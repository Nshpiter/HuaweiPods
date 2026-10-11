package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

/** 模块页面复用蓝牙进程的连接，避免与后台电量/降噪查询争抢 SPP 通道。 */
internal object HuaweiSharedControlClient {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, Pending>()
    private var registered = false

    fun usesSharedConnection(context: Context, route: HuaweiDeviceRoute): Boolean =
        context.packageName == BuildConfig.APPLICATION_ID && HuaweiSharedControlPolicy.supports(route)

    fun request(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        operation: String,
        presetId: Int = -1,
        name: String = "",
        gains: List<Int> = emptyList(),
        enabled: Boolean = false,
        complete: (ByteArray?) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        if (!ensureReceiver(appContext)) {
            complete(null)
            return
        }
        val nonce = UUID.randomUUID().toString()
        val timeout = Runnable { finish(nonce, null) }
        pending[nonce] = Pending(device.address, route, complete, timeout)
        handler.postDelayed(timeout, 8_000L)
        val sent = runCatching {
            appContext.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_SHARED_CONTROL_REQUEST).apply {
                setPackage("com.android.bluetooth")
                putExtra("nonce", nonce)
                putExtra("address", device.address)
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, encodeHuaweiDeviceRouteForBroadcast(route))
                putExtra("operation", operation)
                putExtra("preset_id", presetId)
                putExtra("name", name)
                putExtra("gains", gains.toIntArray())
                putExtra("enabled", enabled)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }.isSuccess
        if (!sent) finish(nonce, null)
    }

    @Synchronized
    private fun ensureReceiver(context: Context): Boolean {
        if (registered) return true
        registered = runCatching {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (sentFromPackage != "com.android.bluetooth") return
                    val received = intent ?: return
                    val nonce = received.getStringExtra("nonce") ?: return
                    val request = pending[nonce] ?: return
                    if (!request.address.equals(received.getStringExtra("address"), ignoreCase = true) ||
                        decodeHuaweiDeviceRouteFromBroadcast(
                            received.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
                        ) != request.route
                    ) return
                    finish(nonce, received.getByteArrayExtra("response")?.takeIf { it.size <= 64 * 1024 })
                }
            }, IntentFilter(HuaweiPodsAction.ACTION_SHARED_CONTROL_RESULT), Context.RECEIVER_EXPORTED)
            true
        }.getOrDefault(false)
        return registered
    }

    private fun finish(nonce: String, response: ByteArray?) {
        val request = pending.remove(nonce) ?: return
        handler.removeCallbacks(request.timeout)
        handler.post { request.callback(response) }
    }

    private data class Pending(
        val address: String,
        val route: HuaweiDeviceRoute,
        val callback: (ByteArray?) -> Unit,
        val timeout: Runnable,
    )
}
