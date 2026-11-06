package moe.chenxy.huaweipods.coloros

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "HuaweiPods-ProfileQuery"

/**
 * Proxies bound by one profile query.
 *
 * Inside the Bluetooth process (`com.android.bluetooth`), `BluetoothAdapter.closeProfileProxy`
 * calls `onServiceDisconnected` synchronously on ColorOS 16, which re-enters [remove] while
 * [closeAll] is still closing. Every close therefore runs on a detached snapshot, so re-entrant
 * callbacks never mutate a map that is being iterated. An uncaught
 * ConcurrentModificationException there crashes the Bluetooth stack in a restart loop.
 */
internal class ProfileProxySet<P : Any>(private val close: (profile: Int, proxy: P) -> Unit) {
    private val proxies = LinkedHashMap<Int, P>()

    var closed = false
        private set

    operator fun get(profile: Int): P? = proxies[profile]

    fun containsAll(vararg profiles: Int): Boolean = profiles.all(proxies::containsKey)

    /** Retains [proxy], or closes it immediately when the query has already finished. */
    fun add(profile: Int, proxy: P): Boolean {
        if (closed) {
            safeClose(profile, proxy)
            return false
        }
        val previous = proxies.put(profile, proxy)
        if (previous != null && previous !== proxy) safeClose(profile, previous)
        return true
    }

    fun remove(profile: Int) {
        proxies.remove(profile)?.let { safeClose(profile, it) }
    }

    /** Drops a proxy whose service went away but keeps its listener registered for rebinding. */
    fun forget(profile: Int) {
        proxies.remove(profile)
    }

    fun closeAll() {
        closed = true
        val snapshot = proxies.entries.map { it.key to it.value }
        proxies.clear()
        snapshot.forEach { (profile, proxy) -> safeClose(profile, proxy) }
    }

    private fun safeClose(profile: Int, proxy: P) {
        runCatching { close(profile, proxy) }
    }
}

/**
 * Bounded, one-shot query for receivers, including the direct-notification fallback without a
 * running foreground service. Missing proxies, permissions and failed queries remain unknown.
 * Call from the main thread and keep the broadcast alive with goAsync() until onResult returns.
 *
 * This also runs inside the hooked Bluetooth process, so no callback may let an exception escape.
 */
@SuppressLint("MissingPermission")
internal fun queryColorOsLiveAlertAudioProfiles(
    context: Context,
    address: String,
    onResult: (a2dpState: Int?, headsetState: Int?) -> Unit,
) {
    val appContext = context.applicationContext ?: context
    val adapter = runCatching { appContext.getSystemService(BluetoothManager::class.java)?.adapter }
        .getOrNull()
    val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
    if (adapter == null || device == null) {
        onResult(null, null)
        return
    }
    val handler = Handler(Looper.getMainLooper())
    val proxies = ProfileProxySet<BluetoothProfile> { profile, proxy ->
        adapter.closeProfileProxy(profile, proxy)
    }
    lateinit var timeout: Runnable

    fun finish() {
        if (proxies.closed) return
        handler.removeCallbacks(timeout)
        // Read both states at completion, not at each bind callback: the first Profile may have
        // reconnected while waiting for the second. Do not substitute the broadcast's old state.
        val a2dp = runCatching { proxies[BluetoothProfile.A2DP]?.getConnectionState(device) }
            .getOrNull()
        val headset = runCatching { proxies[BluetoothProfile.HEADSET]?.getConnectionState(device) }
            .getOrNull()
        // Close before reporting so the result callback can never observe or re-enter a live set.
        proxies.closeAll()
        runCatching { onResult(a2dp, headset) }
            .onFailure { Log.w(TAG, "profile query result callback failed", it) }
    }

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            runCatching {
                // Binding can complete after the timeout; a late proxy is closed, not retained.
                if (proxies.add(profile, proxy) &&
                    proxies.containsAll(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
                ) finish()
            }.onFailure { Log.w(TAG, "profile query connect callback failed", it) }
        }

        override fun onServiceDisconnected(profile: Int) {
            runCatching { proxies.remove(profile) }
                .onFailure { Log.w(TAG, "profile query disconnect callback failed", it) }
        }
    }
    // Well below the foreground broadcast execution limit; no service is started just to check.
    timeout = Runnable { finish() }
    handler.postDelayed(timeout, 2_000L)
    var failedRequests = 0
    for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
        if (proxies.closed) break
        val requested = runCatching { adapter.getProfileProxy(appContext, listener, profile) }
            .getOrDefault(false)
        // Even when one proxy is unavailable, query the other. Its connected state is
        // sufficient to retain/start a session; unavailable is still unknown, never zero.
        if (!requested && ++failedRequests == 2) finish()
    }
}
