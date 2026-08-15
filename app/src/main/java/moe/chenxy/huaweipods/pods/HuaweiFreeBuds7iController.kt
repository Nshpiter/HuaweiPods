package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Locale
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/**
 * FreeBuds 7i settings verified against the guided Smart Audio capture from 2026-08-08.
 *
 * Account, firmware and cloud-only operations are deliberately excluded. Every packet exposed by
 * this controller either appears as a successful write in that capture or is its matching state
 * query.
 */
object HuaweiFreeBuds7iController {
    private val WEAR_DETECTION_QUERY = hex("5A0005002B110100772A")
    private val HEAD_MOTION_QUERY = hex("5A0006002BB401010B289B")
    private val SPATIAL_AUDIO_QUERY = hex("5A000A002BB4010118020003009B3F")
    private val SOUND_EFFECT_QUERY = hex("5A0005002B4A02008C46")
    private val HIGH_QUALITY_AUDIO_QUERY = hex("5A0005002BA30101F794")
    private val DUAL_DEVICE_QUERY = hex("5A0008002B3101000D0100AEEE")
    private val SPATIAL_AUDIO_MODE_PACKETS = mapOf(
        FreeClip2SpatialAudioMode.OFF to hex("5A0009002BB401011802010060ED"),
        FreeClip2SpatialAudioMode.FIXED to hex("5A0009002BB401011802010170CC"),
        FreeClip2SpatialAudioMode.HEAD_TRACKING to hex("5A0009002BB401011802010240AF"),
    )

    fun setBooleanFeature(
        context: Context,
        device: BluetoothDevice,
        feature: FreeBuds7iBooleanFeature,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        packet = feature.packet(enabled),
        description = "freebuds7i ${feature.extraValue} enabled=$enabled",
        onComplete = onComplete,
    )

    fun setSpatialAudioMode(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context,
        device,
        spatialAudioModePacket(mode),
        "freebuds7i spatial-mode=${mode.extraValue}",
        onComplete,
    )

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeBuds5SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context,
        device,
        effect.packet(),
        "freebuds7i sound-effect=${effect.extraValue}",
        onComplete,
    )

    fun setCustomEqualizer(
        context: Context,
        device: BluetoothDevice,
        gains: List<Int>,
        presetName: String = DEFAULT_CUSTOM_EQ_NAME,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = buildCustomEqualizerPacket(gains, presetName) ?: run {
            onComplete?.invoke(false)
            return
        }
        send(context, device, packet, "freebuds7i custom-equalizer", onComplete)
    }

    fun requestSettingsState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeBuds7iSettingsState) -> Unit,
    ) {
        request(context, device, WEAR_DETECTION_QUERY, "wear-detection-state") { response ->
            HuaweiFreeBuds5Controller.parseWearDetectionState(response)?.let {
                onState(FreeBuds7iSettingsState(wearDetection = it))
            }
        }
        request(context, device, HEAD_MOTION_QUERY, "head-motion-state") { response ->
            parseHeadMotionState(response)?.let {
                onState(FreeBuds7iSettingsState(headMotionControl = it))
            }
        }
        request(context, device, SPATIAL_AUDIO_QUERY, "spatial-audio-state") { response ->
            parseSpatialAudioState(response)?.mode?.let {
                onState(FreeBuds7iSettingsState(spatialAudioMode = it))
            }
        }
        request(context, device, SOUND_EFFECT_QUERY, "sound-effect-state") { response ->
            HuaweiEqualizerCodec.parseState(response)?.let { equalizer ->
                onState(
                    FreeBuds7iSettingsState(
                        soundEffect = FreeBuds5SoundEffect.fromProtocolValue(equalizer.selectedId),
                        equalizer = equalizer,
                    ),
                )
            }
        }
        request(context, device, HIGH_QUALITY_AUDIO_QUERY, "high-quality-audio-state") { response ->
            HuaweiFreeBuds5Controller.parseHighQualityAudioState(response)?.let {
                onState(FreeBuds7iSettingsState(highQualityAudio = it))
            }
        }
    }

    fun requestDualDevices(
        context: Context,
        device: BluetoothDevice,
        onDevices: (List<FreeBuds7iDualDevice>) -> Unit,
    ) = request(
        context = context,
        device = device,
        packet = DUAL_DEVICE_QUERY,
        description = "dual-device-list",
        responseWindowMs = 1_500L,
        onResponse = { onDevices(parseDualDevices(it)) },
    )

    fun removeDualDevice(
        context: Context,
        device: BluetoothDevice,
        address: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = buildRemoveDualDevicePacket(address) ?: run {
            onComplete?.invoke(false)
            return
        }
        send(context, device, packet, "freebuds7i remove-dual-device", onComplete)
    }

    fun wearDetectionQueryPacket(): ByteArray = WEAR_DETECTION_QUERY.copyOf()

    fun headMotionQueryPacket(): ByteArray = HEAD_MOTION_QUERY.copyOf()

    fun spatialAudioQueryPacket(): ByteArray = SPATIAL_AUDIO_QUERY.copyOf()

    fun soundEffectQueryPacket(): ByteArray = SOUND_EFFECT_QUERY.copyOf()

    fun highQualityAudioQueryPacket(): ByteArray = HIGH_QUALITY_AUDIO_QUERY.copyOf()

    fun dualDeviceQueryPacket(): ByteArray = DUAL_DEVICE_QUERY.copyOf()

    /** FreeBuds 7i 抓包确认使用 0=关闭、1=固定、2=头部跟踪。 */
    internal fun spatialAudioModePacket(mode: FreeClip2SpatialAudioMode): ByteArray =
        requireNotNull(SPATIAL_AUDIO_MODE_PACKETS[mode]).copyOf()

    internal fun parseSpatialAudioState(stream: ByteArray): FreeClip2AudioState? =
        HuaweiFreeClip2Controller.parseSpatialAudioState(stream) { value ->
            when (value) {
                0 -> FreeClip2SpatialAudioMode.OFF
                1 -> FreeClip2SpatialAudioMode.FIXED
                2 -> FreeClip2SpatialAudioMode.HEAD_TRACKING
                else -> null
            }
        }

    /** Parses field 0x02 from the verified 2BB4/0x0B feature response. */
    fun parseHeadMotionState(stream: ByteArray): Boolean? {
        var latest: Boolean? = null
        verifiedFrames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0xB4) return@forEach
            val fields = parseFields(frame, 6, frame.size - CHECKSUM_SIZE)
            if (fields[0x01]?.singleOrNull()?.u8() != 0x0B) return@forEach
            latest = when (fields[0x02]?.singleOrNull()?.u8()) {
                0x00 -> false
                0x01 -> true
                else -> null
            }
        }
        return latest
    }

    /**
     * Builds the 10-band user preset seen in the capture. Gain values are tenths of a decibel and
     * are restricted to the verified -6.0 dB .. +6.0 dB range.
     */
    fun buildCustomEqualizerPacket(
        gains: List<Int>,
        presetName: String = DEFAULT_CUSTOM_EQ_NAME,
    ): ByteArray? = HuaweiEqualizerCodec.buildCustomPacket(
        gains = gains,
        presetName = presetName,
        operationValue = requireNotNull(
            HuaweiEqualizerCodec.customWriteOperation(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I),
        ),
    )

    fun parseDualDevices(stream: ByteArray): List<FreeBuds7iDualDevice> {
        val devices = linkedMapOf<String, FreeBuds7iDualDevice>()
        verifiedFrames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0x31) return@forEach
            val fields = parseFields(frame, 6, frame.size - CHECKSUM_SIZE)
            val addressBytes = fields[0x04]?.takeIf { it.size == 6 } ?: return@forEach
            val address = addressBytes.joinToString(":") { byte -> "%02X".format(Locale.ROOT, byte.u8()) }
            val name = fields[0x09]
                ?.toString(StandardCharsets.UTF_8)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: address
            devices[address] = FreeBuds7iDualDevice(
                address = address,
                name = name,
                connected = fields[0x05]?.singleOrNull()?.u8() == 0x01,
            )
        }
        return devices.values.toList()
    }

    fun buildRemoveDualDevicePacket(address: String): ByteArray? {
        if (!MAC_ADDRESS.matches(address)) return null
        val addressBytes = address.split(':').map { part -> part.toIntOrNull(16)?.toByte() ?: return null }
        return framedPacket(
            byteArrayOf(0x2B, 0x31, 0x04, 0x06) + addressBytes.toByteArray(),
        )
    }

    private fun request(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        responseWindowMs: Long = 1_000L,
        onResponse: (ByteArray) -> Unit,
    ) {
        if (!isFreeBuds7iTarget(context, device)) {
            onResponse(byteArrayOf())
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            packet = packet.copyOf(),
            description = "freebuds7i $description",
            responseWindowMs = responseWindowMs,
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
        if (!isFreeBuds7iTarget(context, device)) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            packet = packet.copyOf(),
            description = description,
            onComplete = onComplete,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isFreeBuds7iTarget(context: Context, device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank) ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
    }
}

data class FreeBuds7iSettingsState(
    val wearDetection: Boolean? = null,
    val headMotionControl: Boolean? = null,
    val spatialAudioMode: FreeClip2SpatialAudioMode? = null,
    val soundEffect: FreeBuds5SoundEffect? = null,
    val equalizer: HuaweiEqualizerState? = null,
    val highQualityAudio: Boolean? = null,
)

fun mergeFreeBuds7iSettingsState(
    current: FreeBuds7iSettingsState,
    update: FreeBuds7iSettingsState,
): FreeBuds7iSettingsState = FreeBuds7iSettingsState(
    wearDetection = update.wearDetection ?: current.wearDetection,
    headMotionControl = update.headMotionControl ?: current.headMotionControl,
    spatialAudioMode = update.spatialAudioMode ?: current.spatialAudioMode,
    soundEffect = update.soundEffect ?: current.soundEffect,
    equalizer = update.equalizer ?: current.equalizer,
    highQualityAudio = update.highQualityAudio ?: current.highQualityAudio,
)

data class FreeBuds7iDualDevice(
    val address: String,
    val name: String,
    val connected: Boolean,
)

enum class FreeBuds7iBooleanFeature(
    val extraValue: String,
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
) {
    WEAR_DETECTION(
        "wear_detection",
        hex("5A0006002B10010100B977"),
        hex("5A0006002B10010101A956"),
    ),
    HEAD_MOTION_CONTROL(
        "head_motion_control",
        hex("5A0009002BB401010B020100E096"),
        hex("5A0009002BB401010B020101F0B7"),
    ),
    HIGH_QUALITY_AUDIO(
        "high_quality_audio",
        hex("5A0006002BA2010100A5CE"),
        hex("5A0006002BA2010101B5EF"),
    );

    fun packet(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()
}

private const val HEADER_SIZE = 5
private const val CHECKSUM_SIZE = 2
private const val DEFAULT_CUSTOM_EQ_NAME = "HuaweiPods EQ"
private val MAC_ADDRESS = Regex("^[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}$")

private fun framedPacket(body: ByteArray): ByteArray {
    val payloadLength = body.size + 1
    require(payloadLength <= 0xFFFF)
    val withoutCrc = byteArrayOf(
        0x5A,
        0x00,
        payloadLength.toByte(),
        (payloadLength shr 8).toByte(),
    ) + body
    val crc = crc16Xmodem(withoutCrc)
    return withoutCrc + byteArrayOf((crc shr 8).toByte(), crc.toByte())
}

private fun verifiedFrames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + HEADER_SIZE <= stream.size) {
        if (stream.u8OrNull(offset) != 0x5A || stream.u8OrNull(offset + 1) != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream.u8OrNull(offset + 2)!! or (stream.u8OrNull(offset + 3)!! shl 8)
        val frameSize = HEADER_SIZE + payloadLength
        if (frameSize <= HEADER_SIZE || offset + frameSize > stream.size) {
            offset++
            continue
        }
        val frame = stream.copyOfRange(offset, offset + frameSize)
        if (frame.hasValidCrc16Xmodem()) {
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
        if (valueEnd > endExclusive) return emptyMap()
        fields[type] = frame.copyOfRange(valueStart, valueEnd)
        offset = valueEnd
    }
    return fields.takeIf { offset == endExclusive }.orEmpty()
}

private fun ByteArray.hasValidCrc16Xmodem(): Boolean {
    if (size < HEADER_SIZE + CHECKSUM_SIZE) return false
    val expected = crc16Xmodem(copyOf(size - CHECKSUM_SIZE))
    return u8OrNull(size - 2) == (expected shr 8) && u8OrNull(size - 1) == (expected and 0xFF)
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
