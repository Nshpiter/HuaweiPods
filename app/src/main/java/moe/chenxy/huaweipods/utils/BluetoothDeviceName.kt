package moe.chenxy.huaweipods.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/** Upstream 59d5fa8 display policy; do not use aliases for protocol/model identification. */
internal fun resolveBluetoothDisplayName(alias: String?, name: String?, fallback: String = "HuaweiPods"): String =
    alias?.trim()?.takeIf(String::isNotEmpty)
        ?: name?.trim()?.takeIf(String::isNotEmpty)
        ?: fallback

@SuppressLint("MissingPermission")
internal fun BluetoothDevice?.notificationDisplayName(): String = resolveBluetoothDisplayName(
    alias = runCatching { this?.alias }.getOrNull(),
    name = runCatching { this?.name }.getOrNull(),
)

internal fun shouldRefreshBluetoothDisplayName(currentAddress: String?, changedAddress: String?, hasBattery: Boolean): Boolean =
    hasBattery && !currentAddress.isNullOrBlank() && !changedAddress.isNullOrBlank() &&
        currentAddress.equals(changedAddress, ignoreCase = true)
