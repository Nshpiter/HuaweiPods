package moe.chenxy.huaweipods.pods

/**
 * 同一 ANC 协议值在不同耳机上可能代表不同语义，所有档位转换必须携带机型路由。
 */
internal data class HuaweiAncLevelOption(
    val level: HuaweiAncLevel,
    val protocolValue: Int,
    val miuiValue: Int,
)

/**
 * FreeBuds 5i / 6i 实机抓包与官方控件实现共同确认：智慧动态=3、轻度=1、均衡=0、深度=2。
 * MIUI 菜单恰好使用相同的四个值，因此这里不能复用 Pro 3/7i 的既有映射。
 */
private val freeBuds6iAncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
    HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x02, miuiValue = 0x02),
)

private val pro3AndFreeBuds7iAncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x01, miuiValue = 0x03),
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x00, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x02, miuiValue = 0x00),
    HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x03, miuiValue = 0x02),
)

private val freeBuds5AncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
)

/** FreeBuds 4E 000135/00 实机确认仅提供轻度=01、均衡=00；FF 不是可选档位。 */
private val freeBuds4eAncOptions = listOf(
    HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
    HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
)

internal val HuaweiDeviceRoute.ancLevelOptions: List<HuaweiAncLevelOption>
    get() = when (this) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> freeBuds5AncOptions
        HuaweiDeviceRoute.HUAWEI_FREEBUDS4E -> freeBuds4eAncOptions
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        -> freeBuds6iAncOptions
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        -> pro3AndFreeBuds7iAncOptions
        else -> emptyList()
    }

internal val HuaweiDeviceRoute.defaultAncSubMode: Int?
    get() = ancLevelOptions.firstOrNull { it.level == HuaweiAncLevel.ADAPTIVE }?.protocolValue
        ?: ancLevelOptions.firstOrNull()?.protocolValue

internal fun HuaweiDeviceRoute.ancLevelOptionForProtocolValue(value: Int): HuaweiAncLevelOption? =
    ancLevelOptions.firstOrNull { it.protocolValue == value }

internal fun HuaweiDeviceRoute.ancSubModeForMiuiLevel(value: Int): Int? =
    ancLevelOptions.firstOrNull { it.miuiValue == value }?.protocolValue

internal fun HuaweiDeviceRoute.miuiLevelForAncSubMode(value: Int): Int? =
    ancLevelOptionForProtocolValue(value)?.miuiValue

internal fun HuaweiDeviceRoute.supportsAncSubMode(value: Int): Boolean =
    ancLevelOptionForProtocolValue(value) != null

/** 丢弃与当前机型能力不一致的回读，避免把其他机型档位或通透状态写入会话。 */
internal fun HuaweiDeviceRoute.validateAncState(state: HuaweiAncState): HuaweiAncState? = when {
    !supportsAnc -> null
    state.mode == NoiseControlMode.OFF -> HuaweiAncState(NoiseControlMode.OFF)
    state.mode == NoiseControlMode.NOISE_CANCELLATION -> when {
        supportsDiscreteAncLevels -> state.takeIf { state.subMode?.let(::supportsAncSubMode) == true }
        else -> HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION)
    }
    state.mode == NoiseControlMode.TRANSPARENCY -> state.takeIf { supportsTransparency }
    else -> null
}
