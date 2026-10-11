package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

internal object HuaweiSharedControlHost {
    private val handler = Handler(Looper.getMainLooper())
    fun handle(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        intent: Intent,
        stillCurrent: () -> Boolean,
    ) {
        val nonce = intent.getStringExtra("nonce")
        if (!HuaweiSharedControlPolicy.validNonce(nonce)) return
        val operation = intent.getStringExtra("operation").orEmpty()
        val query = HuaweiSharedControlPolicy.query(route, operation) ?: return
        val presetId = intent.getIntExtra("preset_id", -1)
        val enabled = intent.getBooleanExtra("enabled", false)
        val name = intent.getStringExtra("name").orEmpty()
        val gains = intent.getIntArrayExtra("gains")?.toList().orEmpty()
        val replied = AtomicBoolean(false)
        fun reply(response: ByteArray?) {
            if (!replied.compareAndSet(false, true)) return
            context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_SHARED_CONTROL_RESULT).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                putExtra("nonce", nonce)
                putExtra("address", device.address)
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, encodeHuaweiDeviceRouteForBroadcast(route))
                if (stillCurrent() && response != null) putExtra("response", response)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
        fun readState() {
            if (!stillCurrent()) {
                reply(null)
                return
            }
            HuaweiL2capAncController.requestRawPacketOnce(
                context, device, route, query,
                description = "shared-control $operation",
                responseWindowMs = 1_500L,
                keepSocket = true,
                responseComplete = {
                    HuaweiSharedControlPolicy.verifiedResponse(operation, it, presetId, enabled, name, gains)
                },
                onComplete = { if (!it) reply(null) },
                onResponse = {
                    reply(it.takeIf { response ->
                        HuaweiSharedControlPolicy.verifiedResponse(operation, response, presetId, enabled, name, gains)
                    })
                },
            )
        }
        if (operation == HuaweiSharedControlPolicy.EQ_READ || operation == HuaweiSharedControlPolicy.WIND_READ) {
            readState()
        } else {
            val packet = HuaweiSharedControlPolicy.write(route, operation, presetId, name, gains, enabled)
            if (packet == null) {
                reply(null)
                return
            }
            HuaweiL2capAncController.sendRawPacket(
                context, device, route, packet, "shared-control $operation", keepSocket = true,
            ) { success ->
                if (success) handler.postDelayed({ readState() }, 250L) else reply(null)
            }
        }
    }
}
