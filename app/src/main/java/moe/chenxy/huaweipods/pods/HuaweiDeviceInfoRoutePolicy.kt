package moe.chenxy.huaweipods.pods

/** 收录实机 DeviceInfo 或官方产品配置确认的 modelId；未知机型不猜测配色或身份。 */
internal object HuaweiDeviceInfoRoutePolicy {
    private val routeByModelId = mapOf(
        // 官方 000129.json：Hero / ZA09 / HUAWEI FreeBuds 4，尚待本机型抓包复测。
        "000129" to HuaweiDeviceRoute.HUAWEI_FREEBUDS4,
        "000141" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        "000145" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
        "000169" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_SE4_ANC,
        "000135" to HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        "000153" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        "000155" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6,
        "000149" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        "00016D" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        "000163" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        "000174" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7,
        "000167" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        // FreeClip 2 典藏版：2026-10-11 DeviceInfo 与功能写入确认使用同一控制协议。
        "000173" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        "00015D" to HuaweiDeviceRoute.HUAWEI_FREEARC,
        // HUAWEI Eyewear 3（Evian，协议产品名仍上报为 HUAWEI Eyewear）。
        "000139" to HuaweiDeviceRoute.HUAWEI_EYEWEAR,
        "00014F" to HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
    )
    // 同一协议路由允许多个产品身份，目录兜底仍保留首先登记的普通版型号。
    private val modelIdByRoute = routeByModelId.entries.groupBy { it.value }
        .mapValues { (_, identities) -> identities.first().key }

    fun routeForModelId(modelId: String): HuaweiDeviceRoute? = routeByModelId[modelId]

    fun modelIdForRoute(route: HuaweiDeviceRoute): String? = modelIdByRoute[route]

    fun isCompatible(route: HuaweiDeviceRoute, modelId: String): Boolean {
        val knownRoute = routeByModelId[modelId]
        if (knownRoute != null) return knownRoute == route
        return modelIdByRoute[route] == null
    }
}
