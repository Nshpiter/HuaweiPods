package moe.chenxy.huaweipods.config

import android.content.Context
import android.content.Intent
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** 通知模块界面与系统蓝牙进程（ColorOS 通知、弹窗宿主）重新读取已同步到远程偏好的耳机图片。 */
object PodImageChangeNotifier {
    private const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
    const val EXTRA_ADDRESS = "address"

    fun notify(context: Context, address: String) {
        listOf(
            context.packageName,
            BLUETOOTH_PACKAGE,
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
