package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl

internal object MiLinkLowLatencyQuickCardPolicy {
    const val HIDDEN_STATE = -1
    const val DISABLED_STATE = 0
    const val ENABLED_STATE = 103

    fun isAvailable(
        route: HuaweiDeviceRoute,
        configured: Boolean,
    ): Boolean = configured && route.supportsLowLatencyControl

    fun hostState(
        route: HuaweiDeviceRoute,
        configured: Boolean,
        enabled: Boolean,
    ): Int = when {
        !isAvailable(route, configured) -> HIDDEN_STATE
        enabled -> ENABLED_STATE
        else -> DISABLED_STATE
    }

    /** 该宿主入口传入的是点击前的查找耳机状态，复用为开关时应切换当前模块状态。 */
    fun toggledEnabled(currentEnabled: Boolean): Boolean = !currentEnabled
}
