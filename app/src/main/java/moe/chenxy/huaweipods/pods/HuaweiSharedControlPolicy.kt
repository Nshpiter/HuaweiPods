package moe.chenxy.huaweipods.pods

import java.util.UUID

/** 仅开放已验证的音效和防风噪操作，不允许 App 发送任意蓝牙指令。 */
internal object HuaweiSharedControlPolicy {
    const val EQ_READ = "eq_read"
    const val EQ_PRESET = "eq_preset"
    const val EQ_CUSTOM = "eq_custom"
    const val WIND_READ = "wind_read"
    const val WIND_SET = "wind_set"

    fun supports(route: HuaweiDeviceRoute): Boolean =
        route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
            route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_SE4_ANC

    fun validNonce(value: String?): Boolean = value != null &&
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    fun query(route: HuaweiDeviceRoute, operation: String): ByteArray? {
        if (!supports(route)) return null
        return when (operation) {
            EQ_READ, EQ_PRESET, EQ_CUSTOM -> HuaweiEqualizerCodec.stateQueryPacket()
            WIND_READ, WIND_SET -> HuaweiWindNoiseReductionController.queryPacket()
                .takeIf { route.supportsWindNoiseReduction }
            else -> null
        }
    }

    fun write(
        route: HuaweiDeviceRoute,
        operation: String,
        presetId: Int,
        name: String,
        gains: List<Int>,
        enabled: Boolean,
    ): ByteArray? = when {
        !supports(route) -> null
        operation == EQ_PRESET -> HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, presetId)
        operation == EQ_CUSTOM -> HuaweiEqualizerCodec.customWriteOperation(route)?.let {
            HuaweiEqualizerCodec.buildCustomPacket(gains, name, it)
        }
        operation == WIND_SET && route.supportsWindNoiseReduction ->
            HuaweiWindNoiseReductionController.setPacket(enabled)
        else -> null
    }

    fun verifiedResponse(
        operation: String,
        response: ByteArray,
        presetId: Int,
        enabled: Boolean,
        name: String = "",
        gains: List<Int> = emptyList(),
    ): Boolean = when (operation) {
        EQ_READ, EQ_PRESET, EQ_CUSTOM -> HuaweiEqualizerCodec.parseState(response)?.let {
            it.supported && when (operation) {
                EQ_PRESET -> it.selectedId == presetId
                EQ_CUSTOM -> it.selectedId == 0x64 && it.selectedGains == gains &&
                    it.selectedName == name.trim()
                else -> true
            }
        } == true
        WIND_READ -> HuaweiWindNoiseReductionController.parseState(response) != null
        WIND_SET -> HuaweiWindNoiseReductionController.parseState(response) == enabled
        else -> false
    }
}
