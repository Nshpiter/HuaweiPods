package moe.chenxy.huaweipods.hook

/** 连接初期保留快速图标修复，稳定后只做低频兜底；熄屏由宿主取消任务。 */
internal object HeadsetRefreshPolicy {
    fun iconDelayMs(completedTicks: Int): Long = if (completedTicks < 4) 2_500L else 30_000L

    fun shouldQueryDetails(
        resumed: Boolean,
        attached: Boolean,
        shown: Boolean,
        windowVisible: Boolean,
        interactive: Boolean,
    ): Boolean = resumed && attached && shown && windowVisible && interactive
}
