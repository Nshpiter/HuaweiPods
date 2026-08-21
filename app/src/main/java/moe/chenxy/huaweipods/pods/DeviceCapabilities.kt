package moe.chenxy.huaweipods.pods

enum class HuaweiDeviceRoute {
    HUAWEI_FREEBUDS3,
    HUAWEI_FREEBUDS4E,
    HUAWEI_FREEBUDS5,
    HUAWEI_FREEBUDS5I,
    HUAWEI_FREEBUDS6I,
    HUAWEI_FREEBUDS_PRO3,
    HUAWEI_FREEBUDS_PRO4,
    HUAWEI_FREEBUDS_PRO5,
    HUAWEI_FREEBUDS7I,
    HUAWEI_FREECLIP,
    HUAWEI_FREECLIP2,
    HUAWEI_FREEARC,
    HUAWEI_EYEWEAR,
    HUAWEI_EYEWEAR2,
    UNSUPPORTED,
}

data class HuaweiDeviceCapabilities(
    val displayName: String,
    val aliases: Set<String>,
    val supportsAnc: Boolean = false,
    val supportsTransparency: Boolean = false,
    val supportsAncStateReadback: Boolean = false,
    val supportsDiscreteAncLevels: Boolean = false,
    val supportsAncDirectionDial: Boolean = false,
    val supportsRfcommBattery: Boolean = false,
    val supportsBackgroundBatteryRefresh: Boolean = false,
    val supportsGestureConfiguration: Boolean = false,
    val supportsLowLatencyControl: Boolean = false,
    val hasChargingCase: Boolean = false,
    val usesReportedEarbudAvailability: Boolean = false,
)

private val routeCapabilities = linkedMapOf(
    HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 3",
        aliases = setOf("huaweifreebuds3", "freebuds3"),
        supportsAnc = true,
        supportsAncDirectionDial = true,
        supportsGestureConfiguration = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 4E",
        aliases = setOf("huaweifreebuds4e", "freebuds4e"),
        supportsAnc = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS5 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 5",
        aliases = setOf("huaweifreebuds5", "freebuds5"),
        supportsAnc = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 5i",
        aliases = setOf("huaweifreebuds5i", "freebuds5i"),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 6i",
        aliases = setOf("huaweifreebuds6i", "freebuds6i"),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds Pro 3",
        aliases = setOf("huaweifreebudspro3", "freebudspro3"),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds Pro 4",
        aliases = setOf("huaweifreebudspro4", "freebudspro4"),
        supportsAnc = true,
        supportsRfcommBattery = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds Pro 5",
        aliases = setOf("huaweifreebudspro5", "freebudspro5"),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAncStateReadback = true,
        supportsRfcommBattery = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
        usesReportedEarbudAvailability = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeBuds 7i",
        aliases = setOf("huaweifreebuds7i", "freebuds7i"),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAncStateReadback = true,
        supportsDiscreteAncLevels = true,
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREECLIP to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeClip",
        aliases = setOf("huaweifreeclip", "freeclip"),
        supportsRfcommBattery = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREECLIP2 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeClip 2",
        aliases = setOf("huaweifreeclip2", "freeclip2"),
        supportsRfcommBattery = true,
        supportsBackgroundBatteryRefresh = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_FREEARC to HuaweiDeviceCapabilities(
        displayName = "HUAWEI FreeArc",
        aliases = setOf("huaweifreearc", "freearc"),
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        hasChargingCase = true,
    ),
    HuaweiDeviceRoute.HUAWEI_EYEWEAR to HuaweiDeviceCapabilities(
        displayName = "HUAWEI Eyewear",
        aliases = setOf("huaweieyewear"),
        supportsRfcommBattery = true,
    ),
    HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to HuaweiDeviceCapabilities(
        displayName = "HUAWEI Eyewear 2",
        aliases = setOf("huaweieyewear2", "eyewear2"),
        supportsRfcommBattery = true,
        supportsGestureConfiguration = true,
        supportsLowLatencyControl = true,
    ),
)

private val normalizedAliasRoutes: Map<String, HuaweiDeviceRoute> = buildMap {
    routeCapabilities.forEach { (route, capabilities) ->
        capabilities.aliases.forEach { alias -> put(alias, route) }
    }
}

private val broadcastValueByRoute = mapOf(
    HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to "HUAWEI_FREEBUDS3",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to "HUAWEI_FREEBUDS4E",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS5 to "HUAWEI_FREEBUDS5",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to "HUAWEI_FREEBUDS5I",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to "HUAWEI_FREEBUDS6I",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 to "HUAWEI_FREEBUDS_PRO3",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 to "HUAWEI_FREEBUDS_PRO4",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to "HUAWEI_FREEBUDS_PRO5",
    HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to "HUAWEI_FREEBUDS7I",
    HuaweiDeviceRoute.HUAWEI_FREECLIP to "HUAWEI_FREECLIP",
    HuaweiDeviceRoute.HUAWEI_FREECLIP2 to "HUAWEI_FREECLIP2",
    HuaweiDeviceRoute.HUAWEI_FREEARC to "HUAWEI_FREEARC",
    HuaweiDeviceRoute.HUAWEI_EYEWEAR to "HUAWEI_EYEWEAR",
    HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to "HUAWEI_EYEWEAR2",
)

private val routeByBroadcastValue = broadcastValueByRoute.entries.associate { (route, value) ->
    value to route
}

val HuaweiDeviceRoute.capabilities: HuaweiDeviceCapabilities?
    get() = routeCapabilities[this]

val HuaweiDeviceRoute.displayName: String
    get() = capabilities?.displayName ?: "Unsupported"

val HuaweiDeviceRoute.isSupported: Boolean
    get() = capabilities != null

val HuaweiDeviceRoute.supportsAnc: Boolean
    get() = capabilities?.supportsAnc == true

val HuaweiDeviceRoute.supportsTransparency: Boolean
    get() = capabilities?.supportsTransparency == true

val HuaweiDeviceRoute.supportsAncStateReadback: Boolean
    get() = capabilities?.supportsAncStateReadback == true

val HuaweiDeviceRoute.supportsDiscreteAncLevels: Boolean
    get() = capabilities?.supportsDiscreteAncLevels == true

val HuaweiDeviceRoute.supportsAncDirectionDial: Boolean
    get() = capabilities?.supportsAncDirectionDial == true

val HuaweiDeviceRoute.supportsRfcommBattery: Boolean
    get() = capabilities?.supportsRfcommBattery == true

val HuaweiDeviceRoute.supportsBackgroundBatteryRefresh: Boolean
    get() = capabilities?.supportsBackgroundBatteryRefresh == true

val HuaweiDeviceRoute.supportsGestureConfiguration: Boolean
    get() = capabilities?.supportsGestureConfiguration == true

val HuaweiDeviceRoute.supportsLowLatencyControl: Boolean
    get() = capabilities?.supportsLowLatencyControl == true

val HuaweiDeviceRoute.hasChargingCase: Boolean
    get() = capabilities?.hasChargingCase == true

val HuaweiDeviceRoute.usesReportedEarbudAvailability: Boolean
    get() = capabilities?.usesReportedEarbudAvailability == true

fun enabledHuaweiDeviceRoutes(): List<HuaweiDeviceRoute> = routeCapabilities.keys.toList()

fun isHuaweiDeviceRouteEnabled(route: HuaweiDeviceRoute): Boolean = route in routeCapabilities

fun encodeHuaweiDeviceRouteForBroadcast(route: HuaweiDeviceRoute): String? =
    encodeHuaweiDeviceRouteForBroadcast(route, ::isHuaweiDeviceRouteEnabled)

internal fun encodeHuaweiDeviceRouteForBroadcast(
    route: HuaweiDeviceRoute,
    isRouteEnabled: (HuaweiDeviceRoute) -> Boolean,
): String? = broadcastValueByRoute[route]?.takeIf { isRouteEnabled(route) }

fun decodeHuaweiDeviceRouteFromBroadcast(value: String?): HuaweiDeviceRoute? =
    decodeHuaweiDeviceRouteFromBroadcast(value, ::isHuaweiDeviceRouteEnabled)

internal fun decodeHuaweiDeviceRouteFromBroadcast(
    value: String?,
    isRouteEnabled: (HuaweiDeviceRoute) -> Boolean,
): HuaweiDeviceRoute? = value?.let(routeByBroadcastValue::get)?.takeIf(isRouteEnabled)

fun detectHuaweiDeviceRoute(deviceName: String?): HuaweiDeviceRoute =
    detectKnownHuaweiDeviceRoute(deviceName).takeIf(::isHuaweiDeviceRouteEnabled)
        ?: HuaweiDeviceRoute.UNSUPPORTED

fun detectKnownHuaweiDeviceRoute(deviceName: String?): HuaweiDeviceRoute {
    val normalizedName = deviceName?.let(::normalizeDeviceName).orEmpty()
    return normalizedAliasRoutes[normalizedName] ?: HuaweiDeviceRoute.UNSUPPORTED
}

private fun normalizeDeviceName(deviceName: String): String =
    deviceName.lowercase().filter { it.isLetterOrDigit() }
