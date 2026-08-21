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
}
