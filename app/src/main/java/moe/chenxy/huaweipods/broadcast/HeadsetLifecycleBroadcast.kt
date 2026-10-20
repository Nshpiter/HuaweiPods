package moe.chenxy.huaweipods.broadcast

import android.app.BroadcastOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.platform.RomFamily
import moe.chenxy.huaweipods.platform.RomIntegrationPolicy
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

internal fun isHeadsetLifecycleAction(action: String?): Boolean = action in setOf(
    HuaweiPodsAction.ACTION_PODS_CONNECTED,
    HuaweiPodsAction.ACTION_PODS_DISCONNECTED,
    HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED,
    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED,
)

/** Use the Bluetooth host's own bounded FGS allowance, never a permanent battery exemption. */
internal fun Context.sendHeadsetAppBroadcast(intent: Intent) {
    if (!isHeadsetLifecycleAction(intent.action)) {
        sendIdentitySharingBroadcast(intent)
        return
    }
    if (RomIntegrationPolicy.detect(Build.MANUFACTURER, Build.BRAND) == RomFamily.COLOR_OS) {
        HeadsetLifecycleBinding.deliver(this, intent)
    }
    val options = runCatching {
        Class.forName("com.android.bluetooth.Utils", false, classLoader)
            .getMethod("getTempBroadcastOptions")
            .invoke(null) as BroadcastOptions
    }.getOrElse { BroadcastOptions.makeBasic() }
        .setShareIdentityEnabled(true)
        .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_NONE)
    val lifecycle = Intent(intent).apply {
        component = ComponentName(
            BuildConfig.APPLICATION_ID,
            "moe.chenxy.huaweipods.coloros.ColorOsLiveAlertReceiver",
        )
    }
    try {
        sendBroadcast(lifecycle, null, options.toBundle())
    } catch (_: SecurityException) {
        // Older hosts may lack permission for a temporary allowance. Keep identity validation.
        sendBroadcast(lifecycle, null, BroadcastOptions.makeBasic()
            .setShareIdentityEnabled(true)
            .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_NONE).toBundle())
    }
    // UI receivers still receive the state; the manifest receiver must not run twice.
    sendIdentitySharingBroadcast(Intent(intent).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY))
}
