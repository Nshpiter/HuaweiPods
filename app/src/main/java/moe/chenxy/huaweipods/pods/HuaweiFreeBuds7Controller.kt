package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/** 000174/00, Smart Audio 2.0.7.321, real TX/RX capture on 2026-10-06. */
object HuaweiFreeBuds7Controller {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7

    @SuppressLint("MissingPermission")
    private fun accepts(context: Context, device: BluetoothDevice): Boolean =
        DeviceRoutePrefs.resolve(
            context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE),
            device.address,
            device.name ?: device.alias,
        ) == route

    fun request(
        context: Context,
        device: BluetoothDevice,
        setting: FreeBuds7Setting,
        complete: (Boolean?) -> Unit,
    ) {
        requestPacket(context, device, setting.query(), { setting.parse(it) != null }) { complete(setting.parse(it)) }
    }

    fun set(
        context: Context,
        device: BluetoothDevice,
        setting: FreeBuds7Setting,
        enabled: Boolean,
        complete: (Boolean) -> Unit,
    ) {
        writeAndVerify(context, device, setting.packet(enabled), setting.query(), { setting.parse(it) == enabled }, complete)
    }

    fun requestSpatial(
        context: Context,
        device: BluetoothDevice,
        complete: (FreeClip2SpatialAudioMode?) -> Unit,
    ) {
        requestPacket(context, device, spatialQuery(), { HuaweiFreeClip2Controller.parseSpatialAudioState(it) != null }) {
            complete(HuaweiFreeClip2Controller.parseSpatialAudioState(it)?.mode)
        }
    }

    fun setSpatial(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        complete: (Boolean) -> Unit,
    ) {
        writeAndVerify(
            context, device, mode.packet(), spatialQuery(),
            matches = { HuaweiFreeClip2Controller.parseSpatialAudioState(it)?.mode == mode },
            complete = complete,
        )
    }

    fun setEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeBuds7SoundEffect,
        complete: (Boolean) -> Unit,
    ) {
        writeAndVerify(
            context, device, effect.packet(), freeBuds7Hex("5A0005002B4A02008C46"),
            matches = { HuaweiEqualizerCodec.parseState(it)?.selectedId == effect.protocolValue },
            complete = complete,
        )
    }

    fun setCustomEqualizer(
        context: Context,
        device: BluetoothDevice,
        gains: List<Int>,
        name: String,
        complete: (Boolean) -> Unit,
    ) {
        if (gains.any { it !in -30..30 }) return complete(false)
        val packet = HuaweiEqualizerCodec.buildCustomPacket(gains, name, operationValue = 1)
            ?: return complete(false)
        writeAndVerify(
            context, device, packet, HuaweiEqualizerCodec.stateQueryPacket(),
            matches = {
                val state = HuaweiEqualizerCodec.parseState(it)
                state?.selectedGains == gains && state.selectedId == 0x64
            },
            complete = complete,
        )
    }

    fun requestTripleTap(
        context: Context,
        device: BluetoothDevice,
        complete: (HuaweiTapState?) -> Unit,
    ) {
        requestPacket(context, device, tripleQuery(), { HuaweiRfcommResponseParser.parseTripleTapState(it, route) != null }) {
            complete(HuaweiRfcommResponseParser.parseTripleTapState(it, route))
        }
    }

    fun setTripleTap(
        context: Context,
        device: BluetoothDevice,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
        complete: (Boolean) -> Unit,
    ) {
        val packet = HuaweiGestureController.buildTripleTapPacket(route, side, action) ?: return complete(false)
        writeAndVerify(
            context, device, packet, tripleQuery(),
            matches = {
                val state = HuaweiRfcommResponseParser.parseTripleTapState(it, route)
                (if (side == HuaweiGestureSide.LEFT) state?.left else state?.right) == action
            },
            complete = complete,
        )
    }

    private fun tripleQuery() = freeBuds7Hex("5A0007000126010002002512")

    private fun spatialQuery() = freeBuds7Hex("5A000A002BB4010118020003009B3F")

    private fun requestPacket(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        responseComplete: ((ByteArray) -> Boolean)? = null,
        complete: (ByteArray) -> Unit,
    ) {
        if (!accepts(context, device)) return complete(byteArrayOf())
        var delivered = false
        val deliver: (ByteArray) -> Unit = { bytes ->
            if (!delivered) {
                delivered = true
                complete(bytes)
            }
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context, device, route, packet,
            description = "freebuds7 state",
            responseWindowMs = 1_500L,
            responseComplete = responseComplete,
            onComplete = { if (!it) deliver(byteArrayOf()) },
            onResponse = deliver,
        )
    }

    private fun writeAndVerify(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        query: ByteArray,
        matches: (ByteArray) -> Boolean,
        complete: (Boolean) -> Unit,
    ) {
        if (!accepts(context, device)) return complete(false)
        // Keep the RFCOMM connection open until the setting's reply arrives, including after
        // the initial handshake. ACKs alone never satisfy this predicate.
        requestPacket(context, device, packet + query, matches) { response ->
            if (matches(response)) complete(true)
            else requestPacket(context, device, query, matches) { complete(matches(it)) }
        }
    }
}

/** Separate whitelist: no voice assistant, recording, AI effect or ear-tip commands. */
enum class FreeBuds7Setting(
    private val queryHex: String,
    private val offHex: String,
    private val onHex: String,
    private val featureId: Int? = null,
) {
    ADAPTIVE_VOLUME("5A0008002BB401010202003619", "5A0009002BB401010202010013E1", "5A0009002BB401010202010103C0", 0x02),
    HEAD_MOTION("5A0006002BB401010B289B", "5A0009002BB401010B020100E096", "5A0009002BB401010B020101F0B7", 0x0B),
    DROP_REMINDER("5A0008002BB40101070200DDE9", "5A0009002BB4010107020100AFA4", "5A0009002BB4010107020101BF85", 0x07),
    SINGLE_EAR_ANC("5A0008002BB40101050200B389", "5A0009002BB401010502010042CC", "5A0009002BB401010502010152ED", 0x05),
    AUTO_SWITCH("5A0005002B2F0100A98E", "5A0006002B2E01010037C4", "5A0006002B2E01010127E5"),
    HIGH_QUALITY("5A0005002BA30101F794", "5A0006002BA2010100A5CE", "5A0006002BA2010101B5EF"),
    LOW_LATENCY("5A0005002B6C0200B820", "5A0006002B6C010100B430", "5A0006002B6C010101A411"),
    WEAR_DETECTION("5A0005002B110100772A", "5A0006002B10010100B977", "5A0006002B10010101A956"),
    CASE_SOUND("5A0007002BB1020003007FAB", "5A0006002BB101010025B5", "5A0006002BB10101013594"),
    DOUBLE_TAP("5A000700012001000200E897", "5A000900011F0101FF0201FFCFEE", "5A000900011F010101020101FC28"),
    SWIPE_VOLUME("5A0007002B1F01000200328A", "5A0009002B1E0101FF0202FFC8C8", "5A0009002B1E0101000202009D9B"),
    PINCH_PLAY("", "", ""),
    PINCH_NEXT("", "", ""),
    PINCH_PREVIOUS("", "", "");

    private fun pinch(): FreeBudsPro3GestureToggle? = when (this) {
        PINCH_PLAY -> FreeBudsPro3GestureToggle.MEDIA_PLAY_PAUSE
        PINCH_NEXT -> FreeBudsPro3GestureToggle.MEDIA_NEXT
        PINCH_PREVIOUS -> FreeBudsPro3GestureToggle.MEDIA_PREVIOUS
        else -> null
    }

    fun query(): ByteArray = pinch()?.let {
        freeBuds7Frame(byteArrayOf(0x2B, 0x93.toByte(), 1, 1, it.slot.toByte(), 2, 1, 2))
    } ?: freeBuds7Hex(queryHex)

    fun packet(enabled: Boolean): ByteArray = pinch()?.let {
        HuaweiGestureController.buildFreeBudsPro3GestureTogglePacket(it, enabled)
    } ?: freeBuds7Hex(if (enabled) onHex else offHex)

    fun parse(stream: ByteArray): Boolean? {
        featureId?.let { id ->
            val fields = freeBuds7Fields(stream, 0x2B, 0xB4) { it[1]?.singleOrNull()?.toInt() == id } ?: return null
            return fields[2]?.singleOrNull()?.toInt()?.asBoolean()
        }
        pinch()?.let { gesture ->
            val fields = freeBuds7Fields(stream, 0x2B, 0x93) {
                it[1]?.singleOrNull()?.toInt() == gesture.slot && it[2]?.singleOrNull()?.toInt() == 2
            } ?: return null
            return sharedAction(fields, 3, 4, gesture.enabledValue)
        }
        val fields = when (this) {
            AUTO_SWITCH -> freeBuds7Fields(stream, 0x2B, 0x2F)
            HIGH_QUALITY -> freeBuds7Fields(stream, 0x2B, 0xA3)
            LOW_LATENCY -> freeBuds7Fields(stream, 0x2B, 0x6C)
            CASE_SOUND -> freeBuds7Fields(stream, 0x2B, 0xB1)
            WEAR_DETECTION -> freeBuds7Fields(stream, 0x2B, 0x11)
            DOUBLE_TAP -> freeBuds7Fields(stream, 1, 0x20)
            SWIPE_VOLUME -> freeBuds7Fields(stream, 0x2B, 0x1F)
            else -> null
        } ?: return null
        return when (this) {
            DOUBLE_TAP -> sharedAction(fields, 1, 2, 1)
            SWIPE_VOLUME -> sharedAction(fields, 1, 2, 0)
            HIGH_QUALITY, LOW_LATENCY, CASE_SOUND -> fields[2]?.singleOrNull()?.toInt()?.asBoolean()
            else -> fields[1]?.singleOrNull()?.toInt()?.asBoolean()
        }
    }
}

enum class FreeBuds7SoundEffect(val protocolValue: Int) {
    BALANCED(5), VOCAL(9), BASS(2), CLASSICAL(0xC9), MOVIE(0x0D), PODCAST(0x0F), GAME(0x0E), SPORT(0x10);

    fun packet(): ByteArray = if (this == CLASSICAL) {
        freeBuds7Hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367")
    } else {
        requireNotNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(HuaweiDeviceRoute.HUAWEI_FREEBUDS7, protocolValue))
    }
}

private fun Int.asBoolean(): Boolean? = when (this) {
    0 -> false
    1 -> true
    else -> null
}

private fun sharedAction(fields: Map<Int, ByteArray>, left: Int, right: Int, enabledValue: Int): Boolean? {
    val l = fields[left]?.singleOrNull()?.toInt()?.and(255) ?: return null
    val r = fields[right]?.singleOrNull()?.toInt()?.and(255) ?: return null
    if (l != r) return null
    return when (l) {
        enabledValue -> true
        255 -> false
        else -> null
    }
}

/** Validate framing, CRC and complete TLVs before accepting a setting. Ignore ACKs and other slots. */
internal fun freeBuds7Fields(
    stream: ByteArray,
    service: Int,
    command: Int,
    matches: (Map<Int, ByteArray>) -> Boolean = { true },
): Map<Int, ByteArray>? {
    var latest: Map<Int, ByteArray>? = null
    var offset = 0
    while (offset + 9 <= stream.size) {
        if (stream[offset].toInt() and 255 != 0x5A || stream[offset + 1].toInt() != 0) {
            offset++
            continue
        }
        val size = 5 + (stream[offset + 2].toInt() and 255) + ((stream[offset + 3].toInt() and 255) shl 8)
        if (size < 9 || offset + size > stream.size) {
            offset++
            continue
        }
        val frame = stream.copyOfRange(offset, offset + size)
        val crc = freeBuds7Crc(frame.copyOf(size - 2))
        if ((frame[size - 2].toInt() and 255) != (crc shr 8) || (frame[size - 1].toInt() and 255) != (crc and 255)) {
            offset++
            continue
        }
        offset += size
        if (frame[4].toInt() and 255 != service || frame[5].toInt() and 255 != command) continue
        val fields = linkedMapOf<Int, ByteArray>()
        var pos = 6
        var valid = true
        while (pos < size - 2) {
            if (pos + 2 > size - 2) {
                valid = false
                break
            }
            val type = frame[pos].toInt() and 255
            val length = frame[pos + 1].toInt() and 255
            if (pos + 2 + length > size - 2 || fields.containsKey(type)) {
                valid = false
                break
            }
            fields[type] = frame.copyOfRange(pos + 2, pos + 2 + length)
            pos += 2 + length
        }
        if (valid && matches(fields)) latest = fields
    }
    return latest
}

internal fun freeBuds7Frame(body: ByteArray): ByteArray {
    val length = body.size + 1
    val bytes = byteArrayOf(0x5A, 0, length.toByte(), (length shr 8).toByte()) + body
    val crc = freeBuds7Crc(bytes)
    return bytes + byteArrayOf((crc shr 8).toByte(), crc.toByte())
}

private fun freeBuds7Crc(bytes: ByteArray): Int {
    var crc = 0
    bytes.forEach { byte ->
        crc = crc xor ((byte.toInt() and 255) shl 8)
        repeat(8) { crc = (if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1) and 0xFFFF }
    }
    return crc
}

internal fun freeBuds7Hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
