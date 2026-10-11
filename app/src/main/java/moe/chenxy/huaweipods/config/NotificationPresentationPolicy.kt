package moe.chenxy.huaweipods.config

/** Pure policy shared by preferences and notification producers. */
internal object NotificationPresentationPolicy {
    const val TEMPORARY_ISLAND_TIMEOUT_SECONDS = 8
    // 系统协议默认有效期为一小时；常驻使用半小时一次的静默续期，避免极大超时值溢出。
    const val PERSISTENT_ISLAND_TIMEOUT_SECONDS = 3_600
    const val PERSISTENT_ISLAND_REFRESH_MS = 1_800_000L

    fun shouldKeepBatteryIsland(
        enabled: Boolean,
        effectiveMode: Int,
        persistentNotificationEnabled: Boolean,
        hasConnectedEarBattery: Boolean,
    ): Boolean = enabled && hasConnectedEarBattery && when (effectiveMode) {
        ConfigManager.ISLAND_MODE_MODULE -> true
        ConfigManager.ISLAND_MODE_OFFICIAL -> persistentNotificationEnabled
        else -> false
    }

    fun batteryIslandTimeoutSeconds(persistent: Boolean): Int =
        if (persistent) PERSISTENT_ISLAND_TIMEOUT_SECONDS else TEMPORARY_ISLAND_TIMEOUT_SECONDS

    fun normalizedIslandStyle(value: Int): Int =
        if (value == ConfigManager.ISLAND_MODE_MODULE) {
            ConfigManager.ISLAND_MODE_MODULE
        } else {
            ConfigManager.ISLAND_MODE_OFFICIAL
        }

    fun resolveSuperIslandEnabled(
        explicitValue: Boolean?,
        storedValue: Boolean,
        storedMode: Int,
    ): Boolean = explicitValue ?: if (storedMode == ConfigManager.ISLAND_MODE_NONE) {
        false
    } else {
        storedValue
    }

    fun effectiveIslandMode(enabled: Boolean, style: Int): Int =
        if (enabled) normalizedIslandStyle(style) else ConfigManager.ISLAND_MODE_NONE

    fun attachesOfficialIsland(effectiveMode: Int): Boolean =
        effectiveMode == ConfigManager.ISLAND_MODE_OFFICIAL

    fun shouldPostPersistentNotification(enabled: Boolean): Boolean = enabled
}
