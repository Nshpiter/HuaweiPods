package moe.chenxy.huaweipods.coloros

/** Monotonic timestamps are comparable only within the same boot. */
internal fun acceptColorOsLiveAlertEvent(storedBoot: Int?, boot: Int?, lastEvent: Long, incoming: Long): Boolean =
    incoming <= 0 || storedBoot == null || boot == null || storedBoot != boot || incoming >= lastEvent
