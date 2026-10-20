package moe.chenxy.huaweipods.coloros

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

/**
 * 流体云卡片上的低时延按钮。通知按钮只能触发本应用内的 PendingIntent，这里再以模块身份把
 * 控制指令转发给蓝牙进程；按钮文字在蓝牙进程回报写入结果、电量通知重建后更新。
 */
class ColorOsLiveAlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_LOW_LATENCY) return
        val address = intent.getStringExtra(EXTRA_ADDRESS)?.takeIf(String::isNotBlank) ?: return
        val routeExtra = intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE)
        val route = decodeHuaweiDeviceRouteFromBroadcast(routeExtra)
            ?.takeIf { it.supportsLowLatencyControl } ?: return
        val enabled = intent.getBooleanExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED, false)
        context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_SET).apply {
            putExtra("address", address)
            putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, routeExtra)
            putExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED, enabled)
            setPackage(BLUETOOTH_PACKAGE)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.i(TAG, "Live Alert low-latency requested enabled=$enabled route=$route")
    }

    companion object {
        private const val TAG = "HuaweiPods-LiveAlert"
        private const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
        private const val ACTION_TOGGLE_LOW_LATENCY =
            "moe.chenxy.huaweipods.coloros.LIVE_ALERT_TOGGLE_LOW_LATENCY"
        private const val EXTRA_ADDRESS = "address"

        /** 切换到 [enabled] 的按钮 PendingIntent；按地址区分，避免多副耳机互相覆盖。 */
        fun toggleLowLatencyIntent(
            context: Context,
            address: String,
            route: HuaweiDeviceRoute,
            enabled: Boolean,
        ): PendingIntent? {
            val routeExtra = encodeHuaweiDeviceRouteForBroadcast(route) ?: return null
            return PendingIntent.getBroadcast(
                context,
                address.hashCode(),
                Intent(context, ColorOsLiveAlertActionReceiver::class.java).apply {
                    action = ACTION_TOGGLE_LOW_LATENCY
                    putExtra(EXTRA_ADDRESS, address)
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, routeExtra)
                    putExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED, enabled)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
