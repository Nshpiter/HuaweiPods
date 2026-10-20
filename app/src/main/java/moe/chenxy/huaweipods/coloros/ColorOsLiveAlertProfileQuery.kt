package moe.chenxy.huaweipods.coloros

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Bounded, one-shot query for receivers, including the direct-notification fallback without a
 * running foreground service. Missing proxies, permissions and failed queries remain unknown.
 * Call from the main thread and keep the broadcast alive with goAsync() until onResult returns.
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
    val proxies = mutableMapOf<Int, BluetoothProfile>()
    var finished = false
    lateinit var timeout: Runnable

    fun closeProxy(profile: Int, proxy: BluetoothProfile) {
        runCatching { adapter.closeProfileProxy(profile, proxy) }
    }

    fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        // Read both states at completion, not at each bind callback: the first Profile may have
        // reconnected while waiting for the second. Do not substitute the broadcast's old state.
        val a2dp = runCatching { proxies[BluetoothProfile.A2DP]?.getConnectionState(device) }
            .getOrNull()
        val headset = runCatching { proxies[BluetoothProfile.HEADSET]?.getConnectionState(device) }
            .getOrNull()
        try {
            onResult(a2dp, headset)
        } finally {
            proxies.forEach(::closeProxy)
            proxies.clear()
        }
    }

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (finished) {
                // Binding can complete after the timeout; do not retain a late proxy or callback.
                closeProxy(profile, proxy)
                return
            }
            proxies.put(profile, proxy)?.takeIf { it !== proxy }?.let { closeProxy(profile, it) }
            if (proxies.containsKey(BluetoothProfile.A2DP) &&
                proxies.containsKey(BluetoothProfile.HEADSET)
            ) finish()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)?.let { closeProxy(profile, it) }
        }
    }
    // Well below the foreground broadcast execution limit; no service is started just to check.
    timeout = Runnable { finish() }
    handler.postDelayed(timeout, 2_000L)
    var failedRequests = 0
    for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
        if (finished) break
        val requested = runCatching { adapter.getProfileProxy(appContext, listener, profile) }
            .getOrDefault(false)
        // Even when one proxy is unavailable, query the other. Its connected state is
        // sufficient to retain/start a session; unavailable is still unknown, never zero.
        if (!requested && ++failedRequests == 2) finish()
    }
}
