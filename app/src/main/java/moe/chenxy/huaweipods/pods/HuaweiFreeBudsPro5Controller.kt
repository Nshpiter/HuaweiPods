package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/**
 * FreeBuds Pro 5 00016D/17 controls verified by the guided Smart Audio capture from 2026-08-24.
 *
 * Only direct device settings with a captured write are exposed. Account, firmware, hearing test,
 * recording and find-device operations intentionally remain in the official app.
 */
object HuaweiFreeBudsPro5Controller {
    private val ADAPTIVE_VOLUME_QUERY = hex("5A0008002BB401010202003619")
    private val HEAD_MOTION_QUERY = hex("5A0006002BB401010B289B")
    private val VOICE_CONTROL_QUERY = hex("5A0008002BB401010302000129")
    private val SPATIAL_AUDIO_QUERY = hex("5A000A002BB4010118020003009B3F")
    private val HIGH_QUALITY_AUDIO_QUERY = hex("5A0005002BA30101F794")
    private val DUAL_DEVICE_QUERY = hex("5A0005002B2F0100A98E")
    private val CASE_PROMPT_SOUND_QUERY = hex("5A0006002BB40101108BC1")
    private val EAR_TIP_MATERIAL_QUERY = hex("5A0008002BB40101080200F1D8")

    fun setBooleanFeature(
        context: Context,
        device: BluetoothDevice,
        feature: FreeBudsPro5BooleanFeature,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = feature.packet(enabled),
        description = "freebuds-pro5 ${feature.extraValue} enabled=$enabled",
        onComplete = onComplete,
    )

    fun setSpatialAudioMode(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = mode.packet(),
        description = "freebuds-pro5 spatial-mode=${mode.extraValue}",
        onComplete = onComplete,
    )

    fun setEarTipMaterial(
        context: Context,
        device: BluetoothDevice,
        material: FreeBudsPro5EarTipMaterial,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = material.packet(),
        description = "freebuds-pro5 ear-tip=${material.extraValue}",
        onComplete = onComplete,
    )

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeBudsPro5SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = effect.packet(),
        description = "freebuds-pro5 sound-effect=${effect.name}",
        onComplete = onComplete,
    )

    fun requestSettingsState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeBudsPro5SettingsState) -> Unit,
    ) {
        request(context, device, ADAPTIVE_VOLUME_QUERY, "adaptive-volume-state") { response ->
            parseAamFeatureValue(response, featureId = 0x02)?.let {
                onState(FreeBudsPro5SettingsState(adaptiveVolume = it != 0))
            }
        }
        request(context, device, HEAD_MOTION_QUERY, "head-motion-state") { response ->
            HuaweiFreeBuds7iController.parseHeadMotionState(response)?.let {
                onState(FreeBudsPro5SettingsState(headMotionControl = it))
            }
        }
        request(context, device, VOICE_CONTROL_QUERY, "voice-control-state") { response ->
            parseAamFeatureValue(response, featureId = 0x03)?.let {
                onState(FreeBudsPro5SettingsState(voiceControl = it != 0))
            }
        }
        request(context, device, SPATIAL_AUDIO_QUERY, "spatial-audio-state") { response ->
            HuaweiFreeClip2Controller.parseSpatialAudioState(response)?.mode?.let {
                onState(FreeBudsPro5SettingsState(spatialAudioMode = it))
            }
        }
        request(context, device, HIGH_QUALITY_AUDIO_QUERY, "high-quality-audio-state") { response ->
            HuaweiFreeBuds5Controller.parseHighQualityAudioState(response)?.let {
                onState(FreeBudsPro5SettingsState(highQualityAudio = it))
            }
        }
        request(context, device, DUAL_DEVICE_QUERY, "dual-device-state") { response ->
            parseBooleanField(response, service = 0x2B, command = 0x2F, field = 0x01)?.let {
                onState(FreeBudsPro5SettingsState(dualDevice = it))
            }
        }
        request(context, device, CASE_PROMPT_SOUND_QUERY, "case-prompt-sound-state") { response ->
            parseAamFeatureValue(response, featureId = 0x10)?.let {
                onState(FreeBudsPro5SettingsState(casePromptSound = it != 0))
            }
        }
        request(context, device, EAR_TIP_MATERIAL_QUERY, "ear-tip-material-state") { response ->
            parseAamFeatureValue(response, featureId = 0x08)
                ?.let(FreeBudsPro5EarTipMaterial::fromProtocolValue)
                ?.let { onState(FreeBudsPro5SettingsState(earTipMaterial = it)) }
        }
    }

    internal fun adaptiveVolumeQueryPacket(): ByteArray = ADAPTIVE_VOLUME_QUERY.copyOf()

    internal fun headMotionQueryPacket(): ByteArray = HEAD_MOTION_QUERY.copyOf()

    internal fun voiceControlQueryPacket(): ByteArray = VOICE_CONTROL_QUERY.copyOf()

    internal fun spatialAudioQueryPacket(): ByteArray = SPATIAL_AUDIO_QUERY.copyOf()

    internal fun highQualityAudioQueryPacket(): ByteArray = HIGH_QUALITY_AUDIO_QUERY.copyOf()

    internal fun dualDeviceQueryPacket(): ByteArray = DUAL_DEVICE_QUERY.copyOf()

    internal fun casePromptSoundQueryPacket(): ByteArray = CASE_PROMPT_SOUND_QUERY.copyOf()

    internal fun earTipMaterialQueryPacket(): ByteArray = EAR_TIP_MATERIAL_QUERY.copyOf()

    internal fun parseAamFeatureValue(stream: ByteArray, featureId: Int): Int? {
        var latest: Int? = null
        verifiedFrames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0xB4) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CRC_SIZE)
            if (fields[0x01]?.singleOrNull()?.u8() != featureId) return@forEach
            val value = fields[0x02] ?: return@forEach
            latest = if (featureId == 0x03 || featureId == 0x10) {
                value.firstOrNull()?.u8()
            } else {
                value.lastOrNull()?.u8()
            }
        }
        return latest
    }

    internal fun parseBooleanField(
        stream: ByteArray,
        service: Int,
        command: Int,
        field: Int,
    ): Boolean? {
        var latest: Boolean? = null
        verifiedFrames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != service || frame.u8OrNull(5) != command) return@forEach
            latest = when (
                parseFields(frame, start = 6, endExclusive = frame.size - CRC_SIZE)[field]
                    ?.singleOrNull()
                    ?.u8()
            ) {
                0x00 -> false
                0x01 -> true
                else -> null
            }
        }
        return latest
    }

    private fun request(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        onResponse: (ByteArray) -> Unit,
    ) {
        if (!isFreeBudsPro5Target(context, device)) {
            onResponse(byteArrayOf())
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            packet = packet.copyOf(),
            description = "freebuds-pro5 $description",
            responseWindowMs = 1_200L,
            onResponse = onResponse,
        )
    }

    private fun send(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)?,
    ) {
        if (!isFreeBudsPro5Target(context, device)) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            packet = packet.copyOf(),
            description = description,
            onComplete = onComplete,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isFreeBudsPro5Target(context: Context, device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank) ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5
    }
}

data class FreeBudsPro5SettingsState(
    val adaptiveVolume: Boolean? = null,
    val headMotionControl: Boolean? = null,
    val voiceControl: Boolean? = null,
    val spatialAudioMode: FreeClip2SpatialAudioMode? = null,
    val highQualityAudio: Boolean? = null,
    val dualDevice: Boolean? = null,
    val casePromptSound: Boolean? = null,
    val earTipMaterial: FreeBudsPro5EarTipMaterial? = null,
    val equalizer: HuaweiEqualizerState? = null,
)

fun mergeFreeBudsPro5SettingsState(
    current: FreeBudsPro5SettingsState,
    update: FreeBudsPro5SettingsState,
): FreeBudsPro5SettingsState = FreeBudsPro5SettingsState(
    adaptiveVolume = update.adaptiveVolume ?: current.adaptiveVolume,
    headMotionControl = update.headMotionControl ?: current.headMotionControl,
    voiceControl = update.voiceControl ?: current.voiceControl,
    spatialAudioMode = update.spatialAudioMode ?: current.spatialAudioMode,
    highQualityAudio = update.highQualityAudio ?: current.highQualityAudio,
    dualDevice = update.dualDevice ?: current.dualDevice,
    casePromptSound = update.casePromptSound ?: current.casePromptSound,
    earTipMaterial = update.earTipMaterial ?: current.earTipMaterial,
    equalizer = update.equalizer ?: current.equalizer,
)

enum class FreeBudsPro5BooleanFeature(
    val extraValue: String,
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
) {
    ADAPTIVE_VOLUME(
        "adaptive_volume",
        hex("5A0009002BB401010202010013E1"),
        hex("5A0009002BB401010202010103C0"),
    ),
    HEAD_MOTION_CONTROL(
        "head_motion_control",
        hex("5A0009002BB401010B020100E096"),
        hex("5A0009002BB401010B020101F0B7"),
    ),
    VOICE_CONTROL(
        "voice_control",
        hex("5A0009002BB40101030201006555"),
        hex("5A0009002BB40101030201017574"),
    ),
    HIGH_QUALITY_AUDIO(
        "high_quality_audio",
        hex("5A0006002BA2010100A5CE"),
        hex("5A0006002BA2010101B5EF"),
    ),
    DUAL_DEVICE(
        "dual_device",
        hex("5A0006002B2E01010037C4"),
        hex("5A0006002B2E01010127E5"),
    ),
    CASE_PROMPT_SOUND(
        "case_prompt_sound",
        hex("5A0006002BB101010025B5"),
        hex("5A0006002BB10101013594"),
    );

    fun packet(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()
}

enum class FreeBudsPro5EarTipMaterial(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    SILICONE(
        "silicone",
        0x01,
        hex("5A0009002BB40101080201016B6B"),
    ),
    MEMORY_FOAM(
        "memory_foam",
        0x02,
        hex("5A0009002BB40101080201025B08"),
    );

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeBudsPro5EarTipMaterial? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeBudsPro5EarTipMaterial? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

/**
 * Pro 5 sound effects in the order shown by Smart Audio 2.0.6.340.
 *
 * Yuezhang Classical is selected with the captured C9 curve payload instead of the compact
 * built-in-preset packet used by the other eight choices.
 */
enum class FreeBudsPro5SoundEffect(val protocolValue: Int) {
    YUEZHANG_BALANCED(0x05),
    YUEZHANG_VOCAL(0x09),
    YUEZHANG_BASS(0x02),
    YUEZHANG_CLASSICAL(0xC9),
    MOVIE(0x0D),
    PODCAST_VOICE(0x0F),
    GAME(0x0E),
    SPORT(0x10),
    AI(0x11);

    fun packet(): ByteArray = when (this) {
        YUEZHANG_CLASSICAL -> CLASSICAL_PACKET.copyOf()
        else -> requireNotNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                protocolValue,
            ),
        )
    }

    companion object {
        private val CLASSICAL_PACKET = hex(
            "5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367",
        )

        fun fromProtocolValue(value: Int): FreeBudsPro5SoundEffect? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

private const val HEADER_SIZE = 5
private const val CRC_SIZE = 2

private fun verifiedFrames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + HEADER_SIZE <= stream.size) {
        if (stream.u8OrNull(offset) != 0x5A || stream.u8OrNull(offset + 1) != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream.u8OrNull(offset + 2)!! or
            (stream.u8OrNull(offset + 3)!! shl 8)
        val frameSize = HEADER_SIZE + payloadLength
        if (payloadLength < 4 || offset + frameSize > stream.size) {
            offset++
            continue
        }
        val frame = stream.copyOfRange(offset, offset + frameSize)
        if (frame.hasValidCrc()) {
            yield(frame)
            offset += frameSize
        } else {
            offset++
        }
    }
}

private fun parseFields(frame: ByteArray, start: Int, endExclusive: Int): Map<Int, ByteArray> {
    val fields = linkedMapOf<Int, ByteArray>()
    var offset = start
    while (offset + 2 <= endExclusive) {
        val type = frame[offset].u8()
        val length = frame[offset + 1].u8()
        val valueStart = offset + 2
        val valueEnd = valueStart + length
        if (valueEnd > endExclusive || fields.containsKey(type)) return emptyMap()
        fields[type] = frame.copyOfRange(valueStart, valueEnd)
        offset = valueEnd
    }
    return fields.takeIf { offset == endExclusive }.orEmpty()
}

private fun ByteArray.hasValidCrc(): Boolean {
    if (size < HEADER_SIZE + CRC_SIZE) return false
    val crc = crc16Xmodem(copyOf(size - CRC_SIZE))
    return u8OrNull(size - 2) == (crc shr 8) && u8OrNull(size - 1) == (crc and 0xFF)
}

private fun crc16Xmodem(bytes: ByteArray): Int {
    var crc = 0
    bytes.forEach { byte ->
        crc = crc xor (byte.u8() shl 8)
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
            crc = crc and 0xFFFF
        }
    }
    return crc
}

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u8OrNull(index: Int): Int? = getOrNull(index)?.u8()

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
