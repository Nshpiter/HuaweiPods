package moe.chenxy.huaweipods.broadcast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.Parcel
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.coloros.ColorOsLiveAlertBridgeService
import moe.chenxy.huaweipods.coloros.queryColorOsLiveAlertAudioProfiles
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

internal fun shouldReleaseHeadsetBinding(lastSent: Long, acknowledged: Long): Boolean =
    lastSent > 0 && lastSent == acknowledged

/** One acknowledged event at a time. Transport failure never deletes the final disconnect. */
internal object HeadsetLifecycleBinding {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val client = UUID.randomUUID().toString()
    private val queue = HeadsetDeliveryQueue<Intent>()
    private val retry = HeadsetDeliveryRetry()
    private var owner: Context? = null
    private var connection: ServiceConnection? = null
    private var remote: IBinder? = null
    private var transport = 0L
    private var awaiting: Long? = null
    private var lastApplied = 0L
    private var retryTask: Runnable? = null
    private var timeoutTask: Runnable? = null
    @Volatile private var closed = false
    private val busy = AtomicBoolean(false)
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), { task -> Thread(task, "HuaweiPods-LiveAlertIPC").apply { isDaemon = true } })
    private val callback by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        val expectedUid = owner?.let { context -> runCatching {
            context.packageManager.getPackageUid(BuildConfig.APPLICATION_ID, 0)
        }.getOrNull() }
        val data = message.data
        val receivedToken = HeadsetDeliveryToken(data.getString("client").orEmpty(), data.getLong("transport"), data.getLong("event"))
        if (!closed && expectedUid != null && message.sendingUid == expectedUid &&
            HeadsetDeliveryToken(client, transport, receivedToken.event).matches(receivedToken)) {
            val id = data.getLong("event")
            when (message.what) {
                ColorOsLiveAlertBridgeService.APPLIED -> if (awaiting == id && queue.acknowledge(id)) {
                    cancelTimeout(); awaiting = null; lastApplied = id; retry.reset()
                    if (data.getBoolean("closed") && queue.size == 0) releaseTransport() else pump()
                }
                ColorOsLiveAlertBridgeService.FAILED -> if (awaiting == id) failed()
                ColorOsLiveAlertBridgeService.CLOSED -> if (data.getBoolean("closed") && shouldReleaseHeadsetBinding(lastApplied, id) && queue.size == 0) releaseTransport()
            }
        }
        true
    }) }

    fun deliver(context: Context, event: Intent) {
        val copy = Intent(event)
        handler.post {
            if (closed) return@post
            owner = context.applicationContext ?: context
            val kind = when (copy.action) {
                HuaweiPodsAction.ACTION_PODS_CONNECTED -> HeadsetEventKind.CONNECT
                HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> HeadsetEventKind.DISCONNECT
                HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> HeadsetEventKind.BATTERY
                else -> when (copy.getStringExtra("state")) {
                    "connected", "connecting" -> HeadsetEventKind.CONNECT
                    "disconnected" -> HeadsetEventKind.DISCONNECT
                    else -> HeadsetEventKind.STATE
                }
            }
            if (queue.enqueue(copy.getStringExtra("address").orEmpty(), kind,
                    copy.getLongExtra(HuaweiPodsAction.EXTRA_EVENT_ELAPSED_REALTIME, SystemClock.elapsedRealtime()), copy)) {
                if (awaiting == null && retryTask == null) retry.reset()
                pump()
            }
        }
    }

    private fun pump() {
        if (closed || awaiting != null || retryTask != null || queue.size == 0) return
        if (remote == null) { if (connection == null) bind(); return }
        if (!busy.compareAndSet(false, true)) { scheduleTimeout(transport, null, 5_000); return }
        val endpoint = remote ?: run { busy.set(false); return }
        val entry = queue.protectHead() ?: run { busy.set(false); return }
        val generation = transport
        val response = callback.binder
        awaiting = entry.id
        scheduleTimeout(generation, entry.id, 5_000)
        val queued = runCatching { worker.execute {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var success = false
            try {
                if (!closed) {
                    data.writeInterfaceToken(ColorOsLiveAlertBridgeService.DESCRIPTOR)
                    data.writeString(client)
                    data.writeLong(generation)
                    data.writeLong(entry.id)
                    data.writeStrongBinder(response)
                    data.writeTypedObject(entry.payload, 0)
                    check(endpoint.transact(ColorOsLiveAlertBridgeService.EVENT, data, reply, 0))
                    reply.readException()
                    success = true // Accepted into the app queue; APPLIED completes delivery.
                }
            } catch (_: Exception) {
                // Retained state and bounded retry handle failed IPC.
            } finally {
                data.recycle(); reply.recycle(); busy.set(false)
            }
            handler.post {
                if (!closed) {
                    if (generation == transport && !success && awaiting == entry.id) failed()
                    else if (awaiting == null) pump()
                }
            }
        } }.isSuccess
        if (!queued) { busy.set(false); failed() }
    }

    private fun bind() {
        val context = owner ?: return
        val generation = ++transport
        val target = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (closed || connection !== this || generation != transport) return
                cancelTimeout(); remote = service; pump()
            }
            override fun onServiceDisconnected(name: ComponentName) { if (connection === this) failed() }
            override fun onBindingDied(name: ComponentName) { if (connection === this) failed() }
            override fun onNullBinding(name: ComponentName) { if (connection === this) failed() }
        }
        connection = target
        val bound = runCatching {
            if (context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).flags and
                ApplicationInfo.FLAG_STOPPED != 0) return@runCatching false
            context.bindService(Intent().setComponent(ComponentName(
            BuildConfig.APPLICATION_ID, "moe.chenxy.huaweipods.coloros.ColorOsLiveAlertBridgeService")),
            target, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (bound) scheduleTimeout(generation, null, 5_000) else failed()
    }

    private fun scheduleTimeout(generation: Long, id: Long?, delay: Long) {
        cancelTimeout()
        timeoutTask = Runnable { if (!closed && generation == transport && awaiting == id) failed() }
            .also { handler.postDelayed(it, delay) }
    }
    private fun cancelTimeout() { timeoutTask?.let(handler::removeCallbacks); timeoutTask = null }

    private fun failed() {
        if (closed || retryTask != null) return
        val serviceDied = remote != null
        releaseTransport()
        if (serviceDied && queue.size == 0) queue.replayLatestState()
        if (queue.size == 0) return
        val delay = retry.nextDelay() ?: return // Retain state, resume only on a fresh event.
        retryTask = Runnable { retryTask = null; pump() }.also { handler.postDelayed(it, delay) }
    }

    private fun releaseTransport() {
        cancelTimeout()
        val context = owner
        val target = connection
        connection = null; remote = null; awaiting = null; transport++
        if (context != null && target != null) runCatching { context.unbindService(target) }
    }

    fun canCloseForHotReload(): Boolean = !busy.get()

    fun saveHotReloadState(state: Bundle) {
        state.putParcelableArrayList("live_alert_pending_events", ArrayList(queue.recoverySnapshot().map { entry ->
            // Primitive battery fields survive; old module Parcelables/loaders do not.
            snapshotHeadsetLifecycleIntent(entry.payload)
        }))
    }

    fun restoreHotReloadState(context: Context, state: Bundle) {
        val events = state.getParcelableArrayList("live_alert_pending_events", Intent::class.java).orEmpty()
            .filter { isHeadsetLifecycleAction(it.action) }.take(16)
        events.groupBy { it.getStringExtra("address").orEmpty() }.forEach { (address, retained) ->
            if (address.isBlank()) return@forEach
            handler.post {
                if (closed) return@post
                queryColorOsLiveAlertAudioProfiles(context, address) { a2dp, hfp ->
                    if (!closed) {
                        val adapterOff = runCatching {
                            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == false
                        }.getOrDefault(false)
                        if (headsetRecoveryShouldClear(a2dp, hfp, adapterOff)) {
                            deliver(context, Intent(HuaweiPodsAction.ACTION_PODS_DISCONNECTED).apply {
                                putExtra("address", address)
                                putExtra(HuaweiPodsAction.EXTRA_EVENT_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
                            })
                        } else retained.forEach { deliver(context, it) }
                    }
                }
            }
        }
    }

    fun closeForHotReload(): Boolean {
        closed = true
        handler.removeCallbacksAndMessages(null)
        if (Looper.myLooper() == Looper.getMainLooper()) { releaseTransport(); queue.clear(); owner = null }
        else handler.post { releaseTransport(); queue.clear(); owner = null }
        worker.shutdownNow()
        return runCatching { worker.awaitTermination(2, TimeUnit.SECONDS) }.getOrDefault(false)
    }
}
