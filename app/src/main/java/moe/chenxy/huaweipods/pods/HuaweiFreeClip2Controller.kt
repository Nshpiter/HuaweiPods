package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context

/**
 * FreeClip 2 commands verified against the guided Huawei Audio capture from 2026-07-29.
 *
 * This controller intentionally contains only direct device settings. Account-backed features,
 * firmware operations and case sound file transfers are not replayed here.
 */
object HuaweiFreeClip2Controller {
    private val SPATIAL_AUDIO_STATE_QUERY = hex("5A000A002BB4010118020003009B3F")
    private val SOUND_EFFECT_STATE_QUERY = hex("5A0005002B4A02008C46")
    private val HEAD_MOTION_QUERY = hex("5A0006002BB401010B289B")
    private val NOISY_VOLUME_QUERY = hex("5A0005002B8801009182")

    // 2026-07-29 FreeClip 2 抓包同时包含这些查询与状态返回；只复用相同字段的编解码。
    private val booleanReadbackCodecs = linkedMapOf(
        FreeClip2BooleanFeature.WEAR_DETECTION to FreeBuds7Setting.WEAR_DETECTION,
        FreeClip2BooleanFeature.DROP_REMINDER to FreeBuds7Setting.DROP_REMINDER,
        FreeClip2BooleanFeature.ADAPTIVE_VOLUME to FreeBuds7Setting.ADAPTIVE_VOLUME,
        FreeClip2BooleanFeature.LOW_LATENCY to FreeBuds7Setting.LOW_LATENCY,
        FreeClip2BooleanFeature.DUAL_DEVICE to FreeBuds7Setting.AUTO_SWITCH,
        FreeClip2BooleanFeature.CASE_PROMPT_SOUND to FreeBuds7Setting.CASE_SOUND,
    )

    internal fun booleanSettingsQueryPacket(): ByteArray =
        booleanReadbackCodecs.values.fold(byteArrayOf()) { bytes, codec -> bytes + codec.query() } +
            HEAD_MOTION_QUERY + NOISY_VOLUME_QUERY

    internal fun parseBooleanSettings(stream: ByteArray): Map<FreeClip2BooleanFeature, Boolean?> =
        booleanReadbackCodecs.mapValues { (_, codec) -> codec.parse(stream) } + mapOf(
            FreeClip2BooleanFeature.HEAD_MOTION_CONTROL to parseHeadMotionState(stream)?.enabled,
            FreeClip2BooleanFeature.NOISY_VOLUME_BOOST to parseNoisyVolumeState(stream),
        )

    fun requestBooleanSettings(
        context: Context, device: BluetoothDevice,
        onState: (Map<FreeClip2BooleanFeature, Boolean?>, FreeClip2HeadMotionState?) -> Unit,
    ) {
        var delivered = false
        fun deliver(bytes: ByteArray) {
            if (delivered) return
            delivered = true
            onState(parseBooleanSettings(bytes), parseHeadMotionState(bytes))
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context, device, HuaweiDeviceRoute.HUAWEI_FREECLIP2, booleanSettingsQueryPacket(),
            description = "freeclip2 boolean-settings", responseWindowMs = 1_500L,
            responseComplete = { parseBooleanSettings(it).values.all { value -> value != null } },
            onComplete = { if (!it) deliver(byteArrayOf()) }, onResponse = ::deliver,
        )
    }

    /** UI 以耳机回读为准；通用 ACK 和本地期望值均不能证明当前开关状态。 */
    fun setBooleanFeatureVerified(
        context: Context, device: BluetoothDevice, feature: FreeClip2BooleanFeature, enabled: Boolean,
        onState: (Boolean?) -> Unit, onComplete: (Boolean) -> Unit,
    ) {
        if (feature == FreeClip2BooleanFeature.NOISY_VOLUME_BOOST) {
            setNoisyVolumeEnabled(context, device, enabled, onState, onComplete)
            return
        }
        val codec = booleanReadbackCodecs[feature] ?: run { onComplete(false); return }
        if (feature == FreeClip2BooleanFeature.LOW_LATENCY) {
            // 保留蓝牙宿主写入，避免恢复 App 与宿主争抢连接的旧问题。
            HuaweiLowLatencyController.setEnabled(context, device, HuaweiDeviceRoute.HUAWEI_FREECLIP2, enabled) { sent ->
                if (!sent) {
                    onComplete(false)
                } else {
                    requestSetting(context, device, codec.query(), codec::parse) { state ->
                        fun finish(actual: Boolean?) {
                            onState(actual)
                            onComplete(actual == enabled)
                        }
                        if (state == enabled) finish(state)
                        else requestSetting(context, device, codec.query(), codec::parse, ::finish)
                    }
                }
            }
        } else {
            writeSetting(context, device, feature.packet(enabled), codec.query(), codec::parse,
                { it == enabled }, onState, onComplete)
        }
    }

    fun requestHeadMotionState(context: Context, device: BluetoothDevice, onState: (FreeClip2HeadMotionState?) -> Unit) =
        requestSetting(context, device, HEAD_MOTION_QUERY, ::parseHeadMotionState, onState)

    fun requestNoisyVolumeState(context: Context, device: BluetoothDevice, onState: (Boolean?) -> Unit) =
        requestSetting(context, device, NOISY_VOLUME_QUERY, ::parseNoisyVolumeState, onState)

    fun setHeadMotionEnabled(
        context: Context, device: BluetoothDevice, enabled: Boolean,
        onState: (FreeClip2HeadMotionState?) -> Unit, onComplete: (Boolean) -> Unit,
    ) = writeSetting(
        context, device, FreeClip2BooleanFeature.HEAD_MOTION_CONTROL.packet(enabled), HEAD_MOTION_QUERY,
        ::parseHeadMotionState, { it.enabled == enabled }, onState, onComplete,
    )

    fun setHeadMotionAction(
        context: Context, device: BluetoothDevice, gesture: FreeClip2HeadMotionGesture,
        action: FreeClip2HeadMotionAction, onState: (FreeClip2HeadMotionState?) -> Unit,
        onComplete: (Boolean) -> Unit,
    ) = writeSetting(
        context, device, headMotionActionPacket(gesture, action), HEAD_MOTION_QUERY,
        ::parseHeadMotionState, { it.action(gesture) == action }, onState, onComplete,
    )

    fun setNoisyVolumeEnabled(
        context: Context, device: BluetoothDevice, enabled: Boolean,
        onState: (Boolean?) -> Unit, onComplete: (Boolean) -> Unit,
    ) = writeSetting(
        context, device, FreeClip2BooleanFeature.NOISY_VOLUME_BOOST.packet(enabled), NOISY_VOLUME_QUERY,
        ::parseNoisyVolumeState, { it == enabled }, onState, onComplete,
    )

    /** 官方 ZAB1/ZAB2 配置：动作 0=无、1=接听、2=拒接；TLV 03=点头、04=摇头。 */
    internal fun headMotionActionPacket(gesture: FreeClip2HeadMotionGesture, action: FreeClip2HeadMotionAction): ByteArray {
        val bytes = byteArrayOf(0x5A, 0, 9, 0, 0x2B, 0xB4.toByte(), 1, 1, 0x0B,
            gesture.field.toByte(), 1, action.protocolValue.toByte())
        return bytes.withCrc16Xmodem()
    }

    internal fun headMotionQueryPacket(): ByteArray = HEAD_MOTION_QUERY.copyOf()
    internal fun noisyVolumeQueryPacket(): ByteArray = NOISY_VOLUME_QUERY.copyOf()

    internal fun parseHeadMotionState(stream: ByteArray): FreeClip2HeadMotionState? {
        var latest: FreeClip2HeadMotionState? = null
        frames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0xB4) return@forEach
            val fields = frame.settingFields() ?: return@forEach
            if (fields[1] != 0x0B) return@forEach
            val enabled = fields[2]?.asBoolean() ?: return@forEach
            val nod = FreeClip2HeadMotionAction.fromProtocolValue(fields[3]) ?: return@forEach
            val shake = FreeClip2HeadMotionAction.fromProtocolValue(fields[4]) ?: return@forEach
            latest = FreeClip2HeadMotionState(enabled, nod, shake)
        }
        return latest
    }

    internal fun parseNoisyVolumeState(stream: ByteArray): Boolean? {
        var latest: Boolean? = null
        frames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0x88) return@forEach
            val fields = frame.settingFields() ?: return@forEach
            fields[1]?.asBoolean()?.let { latest = it }
        }
        return latest
    }

    private fun <T : Any> requestSetting(
        context: Context, device: BluetoothDevice, packet: ByteArray,
        parser: (ByteArray) -> T?, onState: (T?) -> Unit,
    ) {
        var delivered = false
        fun deliver(state: T?) {
            if (!delivered) {
                delivered = true
                onState(state)
            }
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context, device, HuaweiDeviceRoute.HUAWEI_FREECLIP2, packet.copyOf(),
            description = "freeclip2 device-setting", responseWindowMs = 1_500L,
            responseComplete = { parser(it) != null },
            onComplete = { if (!it) deliver(null) }, onResponse = { deliver(parser(it)) },
        )
    }

    private fun <T : Any> writeSetting(
        context: Context, device: BluetoothDevice, packet: ByteArray, query: ByteArray,
        parser: (ByteArray) -> T?, matches: (T) -> Boolean,
        onState: (T?) -> Unit, onComplete: (Boolean) -> Unit,
    ) {
        fun finish(state: T?) {
            onState(state)
            onComplete(state != null && matches(state))
        }
        requestSetting(context, device, packet + query, parser) { state ->
            if (state != null && matches(state)) finish(state)
            else requestSetting(context, device, query, parser, ::finish)
        }
    }

    fun setBooleanFeature(
        context: Context,
        device: BluetoothDevice,
        feature: FreeClip2BooleanFeature,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (feature == FreeClip2BooleanFeature.LOW_LATENCY) {
            HuaweiLowLatencyController.setEnabled(
                context,
                device,
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                enabled,
                onComplete,
            )
            return
        }
        send(
            context = context,
            device = device,
            packet = feature.packet(enabled),
            description = "freeclip2 ${feature.extraValue} enabled=$enabled",
            onComplete = onComplete,
        )
    }

    fun setSpatialAudioMode(
        context: Context,
        device: BluetoothDevice,
        mode: FreeClip2SpatialAudioMode,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = mode.packet(),
        description = "freeclip2 spatial mode=${mode.extraValue}",
        parser = ::parseSpatialAudioState,
        onComplete = onComplete,
        onState = onState,
    )

    fun setSpatialScene(
        context: Context,
        device: BluetoothDevice,
        scene: FreeClip2SpatialScene,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = scene.packet(),
        description = "freeclip2 spatial scene=${scene.extraValue}",
        parser = ::parseSpatialAudioState,
        onComplete = onComplete,
        onState = onState,
    )

    fun setSoundEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeClip2SoundEffect,
        onComplete: ((Boolean) -> Unit)? = null,
        onState: ((FreeClip2AudioState?) -> Unit)? = null,
    ) = transact(
        context = context,
        device = device,
        packet = effect.packet(),
        description = "freeclip2 sound effect=${effect.extraValue}",
        parser = ::parseSoundEffectState,
        onComplete = onComplete,
        onState = onState,
    )

    fun requestSpatialAudioState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeClip2AudioState?) -> Unit,
    ) {
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = spatialAudioStateQueryPacket(),
            description = "freeclip2 spatial-audio-state-query",
            onResponse = { response -> onState(parseSpatialAudioState(response)) },
        )
    }

    fun requestSoundEffectState(
        context: Context,
        device: BluetoothDevice,
        onState: (FreeClip2AudioState?) -> Unit,
    ) {
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = soundEffectStateQueryPacket(),
            description = "freeclip2 sound-effect-state-query",
            onResponse = { response -> onState(parseSoundEffectState(response)) },
        )
    }

    fun spatialAudioStateQueryPacket(): ByteArray = SPATIAL_AUDIO_STATE_QUERY.copyOf()

    fun soundEffectStateQueryPacket(): ByteArray = SOUND_EFFECT_STATE_QUERY.copyOf()

    /**
     * Parses the latest verified spatial-audio frame from a possibly concatenated RFCOMM read.
     * Unknown enum values are rejected instead of being exposed as a misleading UI state.
     */
    fun parseSpatialAudioState(stream: ByteArray): FreeClip2AudioState? =
        parseSpatialAudioState(stream, FreeClip2SpatialAudioMode::fromStateReportValue)

    internal fun parseSpatialAudioState(
        stream: ByteArray,
        modeFromStateReportValue: (Int) -> FreeClip2SpatialAudioMode?,
    ): FreeClip2AudioState? {
        var latest: FreeClip2AudioState? = null
        frames(stream).forEach { frame ->
            val marker = frame.indexOfSequence(SPATIAL_AUDIO_RESPONSE_PREFIX, startIndex = 4)
            if (marker < 0 || marker + SPATIAL_AUDIO_RESPONSE_SIZE > frame.size - CHECKSUM_SIZE) {
                return@forEach
            }
            val mode = modeFromStateReportValue(frame.u8(marker + 7))
                ?: return@forEach
            val scene = FreeClip2SpatialScene.fromProtocolValue(frame.u8(marker + 10))
                ?: return@forEach
            latest = FreeClip2AudioState(mode = mode, scene = scene)
        }
        return latest
    }

    /** Parses the latest verified built-in sound-effect frame from a concatenated RFCOMM read. */
    fun parseSoundEffectState(stream: ByteArray): FreeClip2AudioState? {
        HuaweiEqualizerCodec.parseState(stream)?.let { equalizer ->
            val effect = FreeClip2SoundEffect.fromProtocolValue(equalizer.selectedId)
                ?: FreeClip2SoundEffect.CUSTOM.takeIf { equalizer.isCustom }
            if (effect != null) {
                return FreeClip2AudioState(effect = effect, equalizer = equalizer)
            }
        }
        var latest: FreeClip2AudioState? = null
        frames(stream).forEach { frame ->
            if (frame.u8OrNull(4) != 0x2B || frame.u8OrNull(5) != 0x4A) return@forEach
            val marker = frame.indexOfSequence(SOUND_EFFECT_RESPONSE_FIELD, startIndex = 6)
            if (marker < 0 || marker + SOUND_EFFECT_RESPONSE_SIZE > frame.size - CHECKSUM_SIZE) {
                return@forEach
            }
            val effect = FreeClip2SoundEffect.fromProtocolValue(frame.u8(marker + 2))
                ?: return@forEach
            latest = FreeClip2AudioState(effect = effect)
        }
        return latest
    }

    /** Parses both response types when the transport returns them in the same byte stream. */
    fun parseAudioState(stream: ByteArray): FreeClip2AudioState? =
        mergeFreeClip2AudioState(
            current = parseSpatialAudioState(stream),
            update = parseSoundEffectState(stream),
        )

    private fun send(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)?,
    ) {
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = packet.copyOf(),
            description = description,
            onComplete = onComplete,
        )
    }

    private fun transact(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        parser: (ByteArray) -> FreeClip2AudioState?,
        onComplete: ((Boolean) -> Unit)?,
        onState: ((FreeClip2AudioState?) -> Unit)?,
    ) {
        if (onState == null) {
            send(context, device, packet, description, onComplete)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            packet = packet.copyOf(),
            description = description,
            responseWindowMs = 1_000L,
            responseComplete = { parser(it) != null },
            onComplete = onComplete,
            onResponse = { onState(parser(it)) },
        )
    }
}

data class FreeClip2AudioState(
    val mode: FreeClip2SpatialAudioMode? = null,
    val scene: FreeClip2SpatialScene? = null,
    val effect: FreeClip2SoundEffect? = null,
    val equalizer: HuaweiEqualizerState? = null,
)

fun mergeFreeClip2AudioState(
    current: FreeClip2AudioState?,
    update: FreeClip2AudioState?,
): FreeClip2AudioState? {
    if (current == null) return update
    if (update == null) return current
    return FreeClip2AudioState(
        mode = update.mode ?: current.mode,
        scene = update.scene ?: current.scene,
        effect = update.effect ?: current.effect,
        equalizer = update.equalizer ?: current.equalizer,
    )
}

enum class FreeClip2BooleanFeature(
    val extraValue: String,
    private val disabledPacket: ByteArray,
    private val enabledPacket: ByteArray,
) {
    WEAR_DETECTION(
        "wear_detection",
        hex("5A0006002B10010100B977"),
        hex("5A0006002B10010101A956"),
    ),
    DROP_REMINDER(
        "drop_reminder",
        hex("5A0009002BB4010107020100AFA4"),
        hex("5A0009002BB4010107020101BF85"),
    ),
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
    NOISY_VOLUME_BOOST(
        "noisy_volume_boost",
        hex("5A0006002B870101002EC5"),
        hex("5A0006002B870101013EE4"),
    ),
    LOW_LATENCY(
        "low_latency",
        hex("5A0006002B6C010100B430"),
        hex("5A0006002B6C010101A411"),
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

enum class FreeClip2HeadMotionGesture(val field: Int) { NOD(3), SHAKE(4) }

enum class FreeClip2HeadMotionAction(val protocolValue: Int) {
    NONE(0), ANSWER_CALL(1), REJECT_CALL(2);

    companion object {
        fun fromProtocolValue(value: Int?): FreeClip2HeadMotionAction? = entries.firstOrNull { it.protocolValue == value }
    }
}

data class FreeClip2HeadMotionState(
    val enabled: Boolean,
    val nod: FreeClip2HeadMotionAction,
    val shake: FreeClip2HeadMotionAction,
) {
    fun action(gesture: FreeClip2HeadMotionGesture): FreeClip2HeadMotionAction =
        if (gesture == FreeClip2HeadMotionGesture.NOD) nod else shake
}

/**
 * FreeClip 2 耳机 AAM 空间音频协议：0=关闭、1=头部跟踪、2=固定。
 *
 * 该顺序同时由官方 API 和真机实际效果确认。各型号控制器负责对应的协议转换。
 */
enum class FreeClip2SpatialAudioMode(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    OFF("off", 0x00, hex("5A0009002BB401011802010060ED")),
    FIXED("fixed", 0x02, hex("5A0009002BB401011802010240AF")),
    HEAD_TRACKING("head_tracking", 0x01, hex("5A0009002BB401011802010170CC"));

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeClip2SpatialAudioMode? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SpatialAudioMode? =
            entries.firstOrNull { it.protocolValue == value }

        /** FreeClip 2 AAM 状态回报与写命令一致：1=头部跟踪、2=固定。 */
        fun fromStateReportValue(value: Int): FreeClip2SpatialAudioMode? = when (value) {
            0 -> OFF
            1 -> HEAD_TRACKING
            2 -> FIXED
            else -> null
        }
    }
}

enum class FreeClip2SpatialScene(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray,
) {
    DEFAULT("default", 0x00, hex("5A0009002BB401011803010057DD")),
    AUDIO_THEATER("audio_theater", 0x01, hex("5A0009002BB401011803010147FC")),
    CINEMA("cinema", 0x02, hex("5A0009002BB4010118030102779F")),
    CONCERT_HALL("concert_hall", 0x03, hex("5A0009002BB401011803010367BE"));

    fun packet(): ByteArray = packetBytes.copyOf()

    companion object {
        fun fromExtraValue(value: String?): FreeClip2SpatialScene? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SpatialScene? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

enum class FreeClip2SoundEffect(
    val extraValue: String,
    val protocolValue: Int,
    private val packetBytes: ByteArray?,
) {
    DEFAULT("default", 0x01, hex("5A0006002B490101012F1A")),
    SPORT_ENHANCE("sport_enhance", 0x0A, hex("5A0006002B4901010A9E71")),
    TREBLE_ENHANCE("treble_enhance", 0x03, hex("5A0006002B490101030F58")),
    CLEAR_VOICE("clear_voice", 0x09, hex("5A0006002B49010109AE12")),
    /** 官方 App 的自定义或模块尚未提供的音效；只读展示，不能作为写命令。 */
    CUSTOM("custom", -1, null);

    val isSelectable: Boolean
        get() = packetBytes != null

    fun packet(): ByteArray = requireNotNull(packetBytes) {
        "The custom/unsupported sound effect has no writable packet"
    }.copyOf()

    companion object {
        val selectableEntries: List<FreeClip2SoundEffect> = entries.filter { it.isSelectable }

        fun fromExtraValue(value: String?): FreeClip2SoundEffect? =
            entries.firstOrNull { it.extraValue == value }

        fun fromProtocolValue(value: Int): FreeClip2SoundEffect? =
            selectableEntries.firstOrNull { it.protocolValue == value }
    }
}

private const val HEADER_SIZE = 5
private const val CHECKSUM_SIZE = 2
private const val SPATIAL_AUDIO_RESPONSE_SIZE = 11
private const val SOUND_EFFECT_RESPONSE_SIZE = 3
private val SPATIAL_AUDIO_RESPONSE_PREFIX = hex("2BB40101180201")
private val SOUND_EFFECT_RESPONSE_FIELD = hex("0201")

private fun frames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + HEADER_SIZE <= stream.size) {
        if (stream.u8(offset) != 0x5A || stream.u8(offset + 1) != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream.u8(offset + 2) or (stream.u8(offset + 3) shl 8)
        val frameSize = HEADER_SIZE + payloadLength
        if (frameSize <= HEADER_SIZE || offset + frameSize > stream.size) {
            offset++
            continue
        }
        val frame = stream.copyOfRange(offset, offset + frameSize)
        if (!frame.hasValidCrc16Xmodem()) {
            // Advance one byte so a following valid frame can still be recovered when the corrupt
            // length field overlaps it.
            offset++
            continue
        }
        yield(frame)
        offset += frameSize
    }
}

/** Huawei RFCOMM frames append CRC16/XMODEM as high byte followed by low byte. */
private fun ByteArray.hasValidCrc16Xmodem(): Boolean {
    if (size < HEADER_SIZE + CHECKSUM_SIZE) return false
    val crc = copyOf(size - CHECKSUM_SIZE).crc16Xmodem()
    return u8(size - 2) == (crc shr 8) && u8(size - 1) == (crc and 0xFF)
}

private fun ByteArray.withCrc16Xmodem(): ByteArray {
    val crc = crc16Xmodem()
    return this + byteArrayOf((crc shr 8).toByte(), crc.toByte())
}

private fun ByteArray.crc16Xmodem(): Int {
    var crc = 0
    for (index in indices) {
        crc = crc xor (u8(index) shl 8)
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) {
                (crc shl 1) xor 0x1021
            } else {
                crc shl 1
            }
            crc = crc and 0xFFFF
        }
    }
    return crc
}

/** Single-byte setting fields; reject malformed or duplicate TLVs, including ACKs. */
private fun ByteArray.settingFields(): Map<Int, Int>? {
    val fields = mutableMapOf<Int, Int>()
    val end = size - CHECKSUM_SIZE
    var offset = 6
    while (offset < end) {
        if (offset + 3 > end || u8(offset + 1) != 1) return null
        if (fields.put(u8(offset), u8(offset + 2)) != null) return null
        offset += 3
    }
    return fields
}

private fun Int.asBoolean(): Boolean? = when (this) { 0 -> false; 1 -> true; else -> null }

private fun ByteArray.indexOfSequence(needle: ByteArray, startIndex: Int): Int {
    if (needle.isEmpty()) return startIndex.coerceIn(0, size)
    val lastStart = size - needle.size
    for (index in startIndex.coerceAtLeast(0)..lastStart) {
        if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) return index
    }
    return -1
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u8OrNull(index: Int): Int? = getOrNull(index)?.toInt()?.and(0xFF)

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
