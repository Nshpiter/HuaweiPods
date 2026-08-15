package moe.chenxy.huaweipods.config

/** Pure policy shared by preferences and notification producers. */
internal object NotificationPresentationPolicy {
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
