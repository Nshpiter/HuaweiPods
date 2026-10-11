package moe.chenxy.huaweipods.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/** 展示名称优先使用系统蓝牙别名；机型识别仍使用原始设备名称和地址绑定。 */
internal fun resolveBluetoothDisplayName(alias: String?, name: String?): String =
    alias?.trim()?.takeIf(String::isNotEmpty)
        ?: name?.trim()?.takeIf(String::isNotEmpty)
        ?: "HuaweiPods"

@SuppressLint("MissingPermission")
internal fun BluetoothDevice?.notificationDisplayName(): String = resolveBluetoothDisplayName(
    alias = runCatching { this?.alias }.getOrNull(),
    name = runCatching { this?.name }.getOrNull(),
)
