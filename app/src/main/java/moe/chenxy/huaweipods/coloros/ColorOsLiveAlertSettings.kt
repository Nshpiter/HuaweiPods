package moe.chenxy.huaweipods.coloros

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import moe.chenxy.huaweipods.config.AppConfig
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

internal fun colorOsLiveAlertVisibility(lockscreenEnabled: Boolean): Int =
    if (lockscreenEnabled) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET

/** Apply the settings immediately, including when no new battery report is arriving. */
@SuppressLint("MissingPermission")
internal fun applyColorOsLiveAlertSettings(context: Context, config: AppConfig) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (!config.persistentNotificationEnabled) {
        context.stopService(Intent(context, ColorOsLiveAlertService::class.java))
        manager.cancel(ColorOsLiveAlertService.NOTIFICATION_ID)
        return
    }

    manager.activeNotifications.firstOrNull {
        it.id == ColorOsLiveAlertService.NOTIFICATION_ID && it.tag == null
    }?.let { active ->
        // Retain the channel and its user settings, promotion extras, actions and current battery.
        val notification = active.notification.clone().apply {
            visibility = colorOsLiveAlertVisibility(config.lockscreenNotificationEnabled)
        }
        manager.notify(ColorOsLiveAlertService.NOTIFICATION_ID, notification)
    }
    // The Bluetooth module republishes its current state and requests a real battery reading.
    context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
        setPackage("com.android.bluetooth")
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    })
}
