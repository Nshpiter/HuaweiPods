package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/**
 * Shared FreeBuds 5 / 5i settings backed by guided Huawei Audio captures.
 *
 * Noise control and verified gestures remain in their shared controllers. Spatial audio and
 * dual-device commands stay absent until a capture proves their writes and readback.
 */
object HuaweiFreeBuds5Controller {
    private val supportedRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
    )
    private val WEAR_DETECTION_STATE_QUERY = hex("5A0005002B110100772A")
    private val SOUND_EFFECT_STATE_QUERY = hex("5A0005002B4A02008C46")
    private val HIGH_QUALITY_AUDIO_STATE_QUERY = hex("5A0005002BA30101F794")

    fun setWearDetection(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        route = route,
        packet = FreeBuds5BooleanFeature.WEAR_DETECTION.packet(enabled),
        description = "${route.name.lowercase()} wear-detection enabled=$enabled",
        onComplete = onComplete,
    )

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        effect: FreeBuds5SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        route = route,
        packet = effect.packet(),
        description = "${route.name.lowercase()} sound-effect=${effect.extraValue}",
        onComplete = onComplete,
    )

    fun setHighQualityAudio(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = send(
        context = context,
        device = device,
        route = route,
        packet = FreeBuds5BooleanFeature.HIGH_QUALITY_AUDIO.packet(enabled),
        description = "${route.name.lowercase()} high-quality-audio enabled=$enabled",
        onComplete = onComplete,
    )

    fun setLowLatency(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = HuaweiLowLatencyController.setEnabled(
        context,
        device,
        route,
        enabled,
        onComplete,
    )

    fun requestWearDetectionState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (Boolean?) -> Unit,
    ) = request(
        context = context,
        device = device,
        route = route,
        packet = wearDetectionStateQueryPacket(),
        description = "${route.name.lowercase()} wear-detection-state-query",
        parse = ::parseWearDetectionState,
        onState = onState,
    )

    fun requestSoundEffectState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (FreeBuds5SoundEffect?) -> Unit,
    ) = request(
        context = context,
        device = device,
        route = route,
        packet = soundEffectStateQueryPacket(),
        description = "${route.name.lowercase()} sound-effect-state-query",
        parse = ::parseSoundEffectState,
        onState = onState,
    )

    fun requestHighQualityAudioState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (Boolean?) -> Unit,
    ) = request(
        context = context,
        device = device,
        route = route,
        packet = highQualityAudioStateQueryPacket(),
        description = "${route.name.lowercase()} high-quality-audio-state-query",
        parse = ::parseHighQualityAudioState,
        onState = onState,
    )

    fun wearDetectionStateQueryPacket(): ByteArray = WEAR_DETECTION_STATE_QUERY.copyOf()

    fun soundEffectStateQueryPacket(): ByteArray = SOUND_EFFECT_STATE_QUERY.copyOf()

    fun highQualityAudioStateQueryPacket(): ByteArray = HIGH_QUALITY_AUDIO_STATE_QUERY.copyOf()

    /** Parses the 0/1 value from field 0x01 in the latest 2B11 state frame. */
    fun parseWearDetectionState(stream: ByteArray): Boolean? =
        parseLatestBooleanState(stream, service = 0x2B, command = 0x11, field = 0x01)

    /** Parses field 0x02 from the latest captured 2B4A state frame. */
    fun parseSoundEffectState(stream: ByteArray): FreeBuds5SoundEffect? {
        var latest: FreeBuds5SoundEffect? = null
        frames(stream).forEach { frame ->
            if (!frame.isResponse(service = 0x2B, command = 0x4A)) return@forEach
            val value = frame.tlvValue(field = 0x02)?.singleOrNull()?.u8() ?: run {
                latest = null
                return@forEach
            }
            latest = FreeBuds5SoundEffect.fromProtocolValue(value)
        }
        return latest
    }

    /** Parses the 0/1 value from field 0x02 in the latest 2BA3 state frame. */
    fun parseHighQualityAudioState(stream: ByteArray): Boolean? =
        parseLatestBooleanState(stream, service = 0x2B, command = 0xA3, field = 0x02)

    private fun <T> request(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        parse: (ByteArray) -> T?,
        onState: (T?) -> Unit,
    ) {
        if (!isExpectedTarget(context, device, route)) {
            onState(null)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet.copyOf(),
            description = description,
            onResponse = { response -> onState(parse(response)) },
        )
    }

    private fun send(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)?,
    ) {
        if (!isExpectedTarget(context, device, route)) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet.copyOf(),
            description = description,
            onComplete = onComplete,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isExpectedTarget(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ): Boolean {
        if (route !in supportedRoutes) return false
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val deviceName = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, deviceName) == route
    }
}

/** Readable device state. Low-latency is omitted because the capture only proves its setter. */
data class FreeBuds5SettingsState(
    val wearDetection: Boolean? = null,
    val soundEffect: FreeBuds5SoundEffect? = null,
    val highQualityAudio: Boolean? = null,
)

fun mergeFreeBuds5SettingsState(
    current: FreeBuds5SettingsState,
    update: FreeBuds5SettingsState,
): FreeBuds5SettingsState = FreeBuds5SettingsState(
    wearDetection = update.wearDetection ?: current.wearDetection,
    soundEffect = update.soundEffect ?: current.soundEffect,
    highQualityAudio = update.highQualityAudio ?: current.highQualityAudio,
)

enum class FreeBuds5BooleanFeature(
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
) {
    WEAR_DETECTION(
        hex("5A0006002B10010100B977"),
        hex("5A0006002B10010101A956"),
    ),
    HIGH_QUALITY_AUDIO(
        hex("5A0006002BA2010100A5CE"),
        hex("5A0006002BA2010101B5EF"),
    ),
    LOW_LATENCY(
        hex("5A0006002B6C010100B430"),
        hex("5A0006002B6C010101A411"),
    );

    fun packet(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()
}

enum class FreeBuds5SoundEffect(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    DEFAULT("default", 0x01, hex("5A0006002B490101012F1A")),
    BASS_ENHANCE("bass_enhance", 0x02, hex("5A0006002B490101021F79")),
    TREBLE_ENHANCE("treble_enhance", 0x03, hex("5A0006002B490101030F58")),
    CLEAR_VOICE("clear_voice", 0x09, hex("5A0006002B49010109AE12"));

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromProtocolValue(value: Int): FreeBuds5SoundEffect? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

private const val HEADER_SIZE = 5
private const val CHECKSUM_SIZE = 2

private fun parseLatestBooleanState(
    stream: ByteArray,
    service: Int,
    command: Int,
    field: Int,
): Boolean? {
    var latest: Boolean? = null
    frames(stream).forEach { frame ->
        if (!frame.isResponse(service, command)) return@forEach
        latest = when (frame.tlvValue(field)?.singleOrNull()?.u8()) {
            0x00 -> false
            0x01 -> true
            else -> null
        }
    }
    return latest
}

private fun frames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + HEADER_SIZE <= stream.size) {
        if (stream[offset].u8() != 0x5A || stream[offset + 1].u8() != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream[offset + 2].u8() or (stream[offset + 3].u8() shl 8)
        val frameSize = HEADER_SIZE + payloadLength
        if (frameSize <= HEADER_SIZE || offset + frameSize > stream.size) {
            offset++
            continue
        }
        yield(stream.copyOfRange(offset, offset + frameSize))
        offset += frameSize
    }
}

private fun ByteArray.isResponse(service: Int, command: Int): Boolean =
    size >= HEADER_SIZE + CHECKSUM_SIZE && getOrNull(4)?.u8() == service && getOrNull(5)?.u8() == command

private fun ByteArray.tlvValue(field: Int): ByteArray? {
    val endExclusive = size - CHECKSUM_SIZE
    var offset = 6
    while (offset + 2 <= endExclusive) {
        val type = this[offset].u8()
        val length = this[offset + 1].u8()
        val valueStart = offset + 2
        val valueEnd = valueStart + length
        if (valueEnd > endExclusive) return null
        if (type == field) return copyOfRange(valueStart, valueEnd)
        offset = valueEnd
    }
    return null
}

private fun Byte.u8(): Int = toInt() and 0xFF

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
