package moe.chenxy.huaweipods.pods

import java.nio.charset.StandardCharsets

/**
 * Huawei AAM 0x2B/0x4A equalizer state shared by the verified modern headset captures.
 *
 * State reads are common across the verified models. Custom writes keep the route-specific
 * operation byte separate because FreeBuds 6i and FreeBuds 7i captures use different values.
 */
object HuaweiEqualizerCodec {
    const val BAND_COUNT = 10
    val GAIN_RANGE = -60..60

    private const val MAX_STREAM_BYTES = 64 * 1024
    private const val HEADER_SIZE = 5
    private const val CRC_SIZE = 2
    private const val CUSTOM_RECORD_SIZE = 36
    private const val CUSTOM_NAME_BYTES = 24
    private const val MAX_WRITE_NAME_BYTES = 32
    private val QUERY = hex("5A0005002B4A02008C46")

    fun stateQueryPacket(): ByteArray = QUERY.copyOf()

    fun parseState(stream: ByteArray): HuaweiEqualizerState? {
        if (stream.isEmpty() || stream.size > MAX_STREAM_BYTES) return null
        var latest: HuaweiEqualizerState? = null
        verifiedFrames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0x4A) return@forEach
            val fields = parseUniqueFields(frame, 6, frame.size - CRC_SIZE) ?: return@forEach
            val supported = fields[0x01]?.singleOrNull()?.u8() == 0x01
            val selectedId = fields[0x02]?.singleOrNull()?.u8() ?: return@forEach
            val bandCount = fields[0x05]?.singleOrNull()?.u8()
                ?.takeIf { it in 1..BAND_COUNT }
                ?: fields[0x06]?.size?.takeIf { it in 1..BAND_COUNT }
                ?: BAND_COUNT
            val selectedCurve = fields[0x06]
                ?.takeIf { it.size == bandCount }
                ?.map(Byte::toInt)
            val selectedName = fields[0x07]?.decodePaddedUtf8()
            val customPresets = parseCustomPresets(fields[0x08]) ?: return@forEach
            val selectedCustom = customPresets.singleOrNull { it.id == selectedId }
            latest = HuaweiEqualizerState(
                supported = supported,
                selectedId = selectedId,
                builtInIds = fields[0x03]?.map { it.u8() }.orEmpty(),
                bandCount = bandCount,
                selectedName = selectedCustom?.name ?: selectedName,
                selectedGains = selectedCustom?.gains ?: selectedCurve,
                customPresets = customPresets,
            )
        }
        return latest
    }

    fun buildCustomPacket(
        gains: List<Int>,
        presetName: String,
        operationValue: Int,
        presetId: Int = 0x64,
    ): ByteArray? {
        if (presetId !in 0x64..0x66 || operationValue !in 0..0xFF) return null
        if (gains.size != BAND_COUNT || gains.any { it !in GAIN_RANGE }) return null
        val nameBytes = presetName.trim().toByteArray(StandardCharsets.UTF_8)
        if (nameBytes.isEmpty() || nameBytes.size > MAX_WRITE_NAME_BYTES) return null
        val body = buildList<Byte> {
            add(0x2B)
            add(0x49)
            add(0x01)
            add(0x01)
            add(presetId.toByte())
            add(0x02)
            add(0x01)
            add(BAND_COUNT.toByte())
            add(0x05)
            add(0x01)
            add(operationValue.toByte())
            add(0x03)
            add(BAND_COUNT.toByte())
            gains.forEach { add(it.toByte()) }
            add(0x04)
            add(nameBytes.size.toByte())
            addAll(nameBytes.toList())
        }.toByteArray()
        return frame(body)
    }

    /**
     * Builds the official built-in preset command captured from FreeBuds 4E.
     *
     * 4E only exposed ids 1=default, 2=bass enhance and 3=treble enhance in the guided
     * capture. Do not accept the clear-voice id used by newer models without protocol evidence.
     */
    fun buildBuiltInPresetPacket(route: HuaweiDeviceRoute, presetId: Int): ByteArray? {
        val allowedIds = when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E -> 1..3
            else -> return null
        }
        if (presetId !in allowedIds) return null
        return frame(
            byteArrayOf(
                0x2B,
                0x49,
                0x01,
                0x01,
                presetId.toByte(),
            ),
        )
    }

    fun customWriteOperation(route: HuaweiDeviceRoute): Int? = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> 0x01
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I -> 0x00
        else -> null
    }

    fun supportsStateRead(route: HuaweiDeviceRoute): Boolean = route in setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
    )

    private fun parseCustomPresets(bytes: ByteArray?): List<HuaweiEqualizerPreset>? {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        if (bytes.size % CUSTOM_RECORD_SIZE != 0) return null
        return bytes.asList().chunked(CUSTOM_RECORD_SIZE).map { record ->
            val id = record[0].u8()
            val bands = record[1].u8()
            if (id !in 0x64..0x66 || bands != BAND_COUNT) return null
            val gains = record.subList(2, 2 + BAND_COUNT).map(Byte::toInt)
            if (gains.any { it !in GAIN_RANGE }) return null
            val name = record.subList(12, 12 + CUSTOM_NAME_BYTES)
                .toByteArray()
                .decodePaddedUtf8()
                .orEmpty()
            HuaweiEqualizerPreset(id = id, name = name, gains = gains)
        }
    }

    private fun parseUniqueFields(
        frame: ByteArray,
        start: Int,
        endExclusive: Int,
    ): Map<Int, ByteArray>? {
        val fields = linkedMapOf<Int, ByteArray>()
        var offset = start
        while (offset + 2 <= endExclusive) {
            val type = frame[offset].u8()
            val length = frame[offset + 1].u8()
            val valueStart = offset + 2
            val valueEnd = valueStart + length
            if (valueEnd > endExclusive || fields.containsKey(type)) return null
            fields[type] = frame.copyOfRange(valueStart, valueEnd)
            offset = valueEnd
        }
        return fields.takeIf { offset == endExclusive }
    }

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
            val candidate = stream.copyOfRange(offset, offset + frameSize)
            if (candidate.hasValidCrc()) {
                yield(candidate)
                offset += frameSize
            } else {
                offset++
            }
        }
    }

    private fun frame(body: ByteArray): ByteArray {
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

    private fun ByteArray.decodePaddedUtf8(): String? = copyOfRange(
        0,
        indexOf(0).takeIf { it >= 0 } ?: size,
    ).toString(StandardCharsets.UTF_8).trim().takeIf(String::isNotEmpty)

    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun ByteArray.u8OrNull(index: Int): Int? = getOrNull(index)?.u8()

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

data class HuaweiEqualizerState(
    val supported: Boolean,
    val selectedId: Int,
    val builtInIds: List<Int>,
    val bandCount: Int,
    val selectedName: String?,
    val selectedGains: List<Int>?,
    val customPresets: List<HuaweiEqualizerPreset>,
) {
    val isCustom: Boolean
        get() = selectedId in 0x64..0x66
}

data class HuaweiEqualizerPreset(
    val id: Int,
    val name: String,
    val gains: List<Int>,
)
