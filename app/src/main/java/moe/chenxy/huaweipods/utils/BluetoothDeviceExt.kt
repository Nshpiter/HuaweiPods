package moe.chenxy.huaweipods.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/**
 * 统一安全读取 BluetoothDevice 名称扩展函数。
 * 防御 MissingPermission 静态检查与运行时 SecurityException，无名称时安全回退为空字符串。
 */
@SuppressLint("MissingPermission")
fun BluetoothDevice.safeDisplayName(): String {
    return runCatching {
        name?.takeIf(String::isNotBlank)
            ?: alias?.takeIf(String::isNotBlank)
    }.getOrNull().orEmpty()
}
