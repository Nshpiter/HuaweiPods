package moe.chenxy.huaweipods.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Opens ColorOS' real auto-launch list, with app details as a portable fallback. */
fun openColorOsBackgroundSettings(context: Context) {
    val colorOsIntent = Intent("com.oplus.battery.permission.startup.StartupAppListActivity").apply {
        component = ComponentName(
            "com.oplus.battery",
            "com.oplus.startupapp.view.StartupAppListActivity",
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(colorOsIntent) }
        .getOrElse { context.startActivity(fallback) }
}
