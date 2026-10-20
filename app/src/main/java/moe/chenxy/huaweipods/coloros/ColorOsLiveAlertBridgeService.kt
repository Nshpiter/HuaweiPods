package moe.chenxy.huaweipods.coloros

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcel
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.broadcast.HeadsetAppliedLedger
import moe.chenxy.huaweipods.broadcast.HeadsetDeliveryToken
import moe.chenxy.huaweipods.broadcast.HuaweiPodsBroadcastTrustPolicy
import moe.chenxy.huaweipods.config.ConfigManager

/** Tiny synchronous wake-up entry; APPLIED is sent after notification/Profile processing. */
class ColorOsLiveAlertBridgeService : Service() {
    private data class Receipt(val callback: Messenger?, val token: HeadsetDeliveryToken)
    private var latest: Receipt? = null
    private val ledger = HeadsetAppliedLedger()
    private val receiver = ColorOsLiveAlertReceiver().apply { fromBoundBridge = true }
    private val handler = Handler(Looper.getMainLooper())
    private val binder = object : Binder() {
        init { attachInterface(null, DESCRIPTOR) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != EVENT) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val packages = packageManager.getPackagesForUid(getCallingUid())
            val token = HeadsetDeliveryToken(data.readString().orEmpty(), data.readLong(), data.readLong())
            val callback = data.readStrongBinder()?.let(::Messenger)
            val event = data.readTypedObject(Intent.CREATOR)
                ?: throw IllegalArgumentException("Missing headset event")
            data.enforceNoDataAvail()
            if (!token.matches(token) ||
                !HuaweiPodsBroadcastTrustPolicy.shouldAcceptBoundHeadsetEvent(event.action, packages)) {
                throw SecurityException("Only the Bluetooth UID may deliver valid headset lifecycle events")
            }
            event.setExtrasClassLoader(javaClass.classLoader)
            check(handler.post {
                val receipt = Receipt(callback, token)
                latest = receipt
                if (ledger.wasApplied(token.client, token.event)) {
                    send(receipt, APPLIED)
                } else {
                    val completed = AtomicBoolean(false)
                    fun complete(success: Boolean) {
                        if (!completed.compareAndSet(false, true)) return
                        if (success) ledger.record(token.client, token.event)
                        send(receipt, if (success) APPLIED else FAILED)
                    }
                    runCatching { receiver.handleTrustedEvent(this@ColorOsLiveAlertBridgeService, event, ::complete) }
                        .onFailure { complete(false) }
                }
            })
            reply?.writeNoException()
            return true
        }
    }

    override fun onCreate() { super.onCreate(); active = this }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onDestroy() {
        if (active === this) active = null
        latest = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun isClosed(): Boolean {
        if (!ConfigManager.refreshFromPrefs(getSharedPreferences(ConfigManager.PREFS_NAME, MODE_PRIVATE)).persistentNotificationEnabled)
            return true
        return getSharedPreferences("coloros_live_alert_state", MODE_PRIVATE).getString("current_address", null).isNullOrBlank() &&
            getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == ColorOsLiveAlertService.NOTIFICATION_ID }
    }

    private fun send(receipt: Receipt, type: Int) {
        runCatching { receipt.callback?.send(Message.obtain().apply {
            what = type
            data = Bundle().apply {
                putString("client", receipt.token.client)
                putLong("transport", receipt.token.transport)
                putLong("event", receipt.token.event)
                putBoolean("closed", isClosed())
            }
        }) }
    }
    companion object {
        const val EVENT = 1
        const val CLOSED = 2
        const val APPLIED = 3
        const val FAILED = 4
        const val DESCRIPTOR = "moe.chenxy.huaweipods.coloros.HeadsetLifecycle.v2"
        private var active: ColorOsLiveAlertBridgeService? = null
        internal fun reportClosed() { active?.let { service -> service.latest?.let { service.send(it, CLOSED) } } }
    }
}
