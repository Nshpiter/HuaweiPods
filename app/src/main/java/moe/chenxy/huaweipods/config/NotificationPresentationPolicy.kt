package moe.chenxy.huaweipods.config

/** Pure policy shared by preferences and notification producers. */
internal object NotificationPresentationPolicy {
    fun shouldPostPersistentNotification(enabled: Boolean): Boolean = enabled
}
