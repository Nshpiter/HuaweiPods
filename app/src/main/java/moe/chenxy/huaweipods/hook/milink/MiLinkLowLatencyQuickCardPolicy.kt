package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl

internal object MiLinkLowLatencyQuickCardPolicy {
    const val HIDDEN_STATE = -1
    const val DISABLED_STATE = 0
    const val ENABLED_STATE = 103

    /**
     * 低时延快捷卡复用了旧版融合中心的“查找耳机”槽位。
     * HyperOS 4 的槽位回调语义尚未验证，未知宿主也不能假定兼容。
     */
    fun isHostSupported(hostAdapterName: String?): Boolean =
        hostAdapterName == "legacy"

    fun isAvailable(
        route: HuaweiDeviceRoute,
        configured: Boolean,
        hostAdapterName: String?,
    ): Boolean = configured &&
        route.supportsLowLatencyControl &&
        isHostSupported(hostAdapterName)

    fun hostState(
        route: HuaweiDeviceRoute,
        configured: Boolean,
        enabled: Boolean,
        hostAdapterName: String?,
    ): Int = when {
        !isAvailable(route, configured, hostAdapterName) -> HIDDEN_STATE
        enabled -> ENABLED_STATE
        else -> DISABLED_STATE
    }

    /** 该宿主入口传入的是点击前的查找耳机状态，复用为开关时应切换当前模块状态。 */
    fun toggledEnabled(currentEnabled: Boolean): Boolean = !currentEnabled

    /**
     * 旧宿主退出详情会 setFindRing(..., 0)，真正点击“停止”也会传 0。
     * 必须在 HeadsetServiceController 提交异步任务之前识别调用来源，不能仅按参数过滤。
     */
    fun isDetailCleanup(requestedState: Int, callerStack: Array<StackTraceElement>): Boolean =
        requestedState == 0 && callerStack.any {
            it.className == "com.miui.circulateplus.world.headset.HeadSetsDetail" &&
                it.methodName == "onDetachedFromWindow"
        }
}
