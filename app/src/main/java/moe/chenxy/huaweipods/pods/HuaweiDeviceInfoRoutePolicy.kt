package moe.chenxy.huaweipods.pods

/** 仅收录已有实机 DeviceInfo 帧证明的 modelId；未知机型绝不靠名称或默认配色猜测。 */
internal object HuaweiDeviceInfoRoutePolicy {
    private val routeByModelId = mapOf(
        "000141" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        "000145" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
        "000135" to HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        "000153" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        "000149" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        "00016D" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        "000163" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        "000167" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        "00015D" to HuaweiDeviceRoute.HUAWEI_FREEARC,
        // HUAWEI Eyewear 3（Evian，协议产品名仍上报为 HUAWEI Eyewear）。
        "000139" to HuaweiDeviceRoute.HUAWEI_EYEWEAR,
        "00014F" to HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
    )
    private val modelIdByRoute = routeByModelId.entries.associate { (modelId, route) ->
        route to modelId
    }

    fun routeForModelId(modelId: String): HuaweiDeviceRoute? = routeByModelId[modelId]

    fun modelIdForRoute(route: HuaweiDeviceRoute): String? = modelIdByRoute[route]

    fun isCompatible(route: HuaweiDeviceRoute, modelId: String): Boolean {
        val expectedModelId = modelIdByRoute[route]
        if (expectedModelId != null) return expectedModelId == modelId
        val knownRoute = routeByModelId[modelId]
        return knownRoute == null || knownRoute == route
    }
}
