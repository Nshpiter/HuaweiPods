package moe.chenxy.huaweipods.config

import android.content.Context
import android.content.Intent
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** 通知模块界面、系统设置与融合设备中心重新读取已同步到远程偏好的耳机图片。 */
object PodImageChangeNotifier {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val MILINK_PACKAGE = "com.milink.service"
    private const val MI_BLUETOOTH_PACKAGE = "com.xiaomi.bluetooth"
    const val EXTRA_ADDRESS = "address"

    fun notify(context: Context, address: String) {
        listOf(
            context.packageName,
            SETTINGS_PACKAGE,
            MILINK_PACKAGE,
            MI_BLUETOOTH_PACKAGE,
        ).distinct().forEach { targetPackage ->
            context.sendBroadcast(
                Intent(HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED)
                    .setPackage(targetPackage)
                    .putExtra(EXTRA_ADDRESS, address)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            )
        }
    }
}
