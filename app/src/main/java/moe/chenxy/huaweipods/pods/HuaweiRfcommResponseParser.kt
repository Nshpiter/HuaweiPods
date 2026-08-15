package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability

internal object HuaweiRfcommResponseParser {
    private const val HEADER_SIZE = 5
    private const val CHECKSUM_SIZE = 2
    private const val BATTERY_SERVICE = 0x01
    private val BATTERY_COMMANDS = setOf(0x08, 0x27)
    private const val BATTERY_LEVELS = 0x02
    private const val CHARGING_STATES = 0x03
    private const val EARBUD_CONNECTION_STATES = 0x05
    private const val ANC_SERVICE = 0x2B
    private const val ANC_STATE_COMMAND = 0x2A
    private const val ANC_STATE = 0x01
    private const val GESTURE_SERVICE = 0x01
    private const val DOUBLE_TAP_STATE_COMMAND = 0x20
    private const val TRIPLE_TAP_STATE_COMMAND = 0x26
    private const val SWIPE_SERVICE = 0x2B
    private const val SWIPE_STATE_COMMAND = 0x1F
    private const val LONG_PRESS_SERVICE = 0x2B
    private const val LONG_PRESS_STATE_COMMAND = 0x17
    private const val LEFT_GESTURE = 0x01
    private const val RIGHT_GESTURE = 0x02

    fun parseBattery(
        stream: ByteArray,
        includeCase: Boolean = true,
        useReportedEarbudAvailability: Boolean = false,
    ): BatteryParams? {
        var latestBattery: BatteryParams? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != BATTERY_SERVICE || frame.u8(5) !in BATTERY_COMMANDS) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val levels = fields[BATTERY_LEVELS] ?: return@forEach
            if (levels.size < 2) return@forEach
            val charging = fields[CHARGING_STATES] ?: byteArrayOf()
            val reportedStates = fields[EARBUD_CONNECTION_STATES]
                .takeIf { useReportedEarbudAvailability }
            val parsed = BatteryParams(
                left = levels.podAt(
                    index = 0,
                    charging = charging,
                    reportedConnected = reportedStates?.reportedConnectedAt(0),
                ),
                right = levels.podAt(
                    index = 1,
                    charging = charging,
                    reportedConnected = reportedStates?.reportedConnectedAt(1),
                ),
                case = levels.podAt(
                    index = 2,
                    charging = charging,
                    inferAvailabilityFromBattery = false,
                ).takeIf { includeCase },
            )
            latestBattery = if (reportedStates == null) {
                parsed.normalizedEarbudAvailability()
            } else {
                parsed
            }
        }
        return latestBattery
    }

    fun parseAncState(stream: ByteArray): HuaweiAncState? {
        var latestState: HuaweiAncState? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != ANC_SERVICE || frame.u8(5) != ANC_STATE_COMMAND) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val state = fields[ANC_STATE]?.takeIf { it.size == 2 } ?: return@forEach
            val mode = when (state.u8(1)) {
                0x00 -> NoiseControlMode.OFF
                0x01 -> NoiseControlMode.NOISE_CANCELLATION
                0x02 -> NoiseControlMode.TRANSPARENCY
                else -> return@forEach
            }
            latestState = HuaweiAncState(
                mode = mode,
                subMode = state.u8(0).takeUnless { mode == NoiseControlMode.OFF },
            )
        }
        return latestState
    }

    /** 兼容仍使用广播整数状态的系统 Hook。 */
    fun parseAncStatus(
        stream: ByteArray,
        distinguishTransparency: Boolean = false,
    ): Int? {
        val mode = parseAncState(stream)?.mode ?: return null
        return when {
            mode == NoiseControlMode.TRANSPARENCY && !distinguishTransparency ->
                NoiseControlMode.OFF.broadcastStatus
            else -> mode.broadcastStatus
        }
    }

    fun parseDoubleTapState(
        stream: ByteArray,
        route: HuaweiDeviceRoute,
    ): HuaweiTapState? = parseTapState(
        stream = stream,
        route = route,
        kind = HuaweiGestureKind.DOUBLE_TAP,
        command = DOUBLE_TAP_STATE_COMMAND,
    )

    fun parseTripleTapState(
        stream: ByteArray,
        route: HuaweiDeviceRoute,
    ): HuaweiTapState? = parseTapState(
        stream = stream,
        route = route,
        kind = HuaweiGestureKind.TRIPLE_TAP,
        command = TRIPLE_TAP_STATE_COMMAND,
    )

    fun parseSwipeState(
        stream: ByteArray,
        route: HuaweiDeviceRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
    ): HuaweiSwipeState? {
        var latestState: HuaweiSwipeState? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != SWIPE_SERVICE || frame.u8(5) != SWIPE_STATE_COMMAND) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val leftValue = fields[LEFT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val rightValue = fields[RIGHT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val left = HuaweiSwipeAction.fromProtocolValue(route, leftValue) ?: return@forEach
            val right = HuaweiSwipeAction.fromProtocolValue(route, rightValue) ?: return@forEach
            latestState = HuaweiSwipeState(left = left, right = right)
        }
        return latestState
    }

    fun parseGestureState(
        stream: ByteArray,
        route: HuaweiDeviceRoute,
    ): HuaweiGestureState = HuaweiGestureState(
        doubleTap = parseDoubleTapState(stream, route),
        tripleTap = parseTripleTapState(stream, route),
        swipe = parseSwipeState(stream, route),
        longPress = parseLongPressState(stream, route),
    )

    fun parseLongPressState(
        stream: ByteArray,
        route: HuaweiDeviceRoute,
    ): HuaweiLongPressState? {
        var latestState: HuaweiLongPressState? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != LONG_PRESS_SERVICE || frame.u8(5) != LONG_PRESS_STATE_COMMAND) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val leftValue = fields[LEFT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val rightValue = fields[RIGHT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val left = FreeBudsPro3LongPressAction.fromProtocolValue(route, leftValue) ?: return@forEach
            val right = FreeBudsPro3LongPressAction.fromProtocolValue(route, rightValue) ?: return@forEach
            latestState = HuaweiLongPressState(left = left, right = right)
        }
        return latestState
    }

    private fun parseTapState(
        stream: ByteArray,
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        command: Int,
    ): HuaweiTapState? {
        var latestState: HuaweiTapState? = null
        frames(stream).forEach { frame ->
            if (frame.u8(4) != GESTURE_SERVICE || frame.u8(5) != command) return@forEach
            val fields = parseFields(frame, start = 6, endExclusive = frame.size - CHECKSUM_SIZE)
            val leftValue = fields[LEFT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val rightValue = fields[RIGHT_GESTURE]?.singleOrNull()?.toInt()?.and(0xFF) ?: return@forEach
            val left = HuaweiTapAction.fromProtocolValue(route, kind, leftValue) ?: return@forEach
            val right = HuaweiTapAction.fromProtocolValue(route, kind, rightValue) ?: return@forEach
            latestState = HuaweiTapState(left = left, right = right)
        }
        return latestState
    }

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
            yield(stream.copyOfRange(offset, offset + frameSize))
            offset += frameSize
        }
    }

    private fun parseFields(
        frame: ByteArray,
        start: Int,
        endExclusive: Int,
    ): Map<Int, ByteArray> {
        val fields = linkedMapOf<Int, ByteArray>()
        var offset = start
        while (offset + 2 <= endExclusive) {
            val type = frame.u8(offset)
            val length = frame.u8(offset + 1)
            val valueStart = offset + 2
            val valueEnd = valueStart + length
            if (valueEnd > endExclusive) break
            fields[type] = frame.copyOfRange(valueStart, valueEnd)
            offset = valueEnd
        }
        return fields
    }

    private fun ByteArray.podAt(
        index: Int,
        charging: ByteArray,
        reportedConnected: Boolean? = null,
        inferAvailabilityFromBattery: Boolean = true,
    ): PodParams? {
        val level = getOrNull(index)?.toInt()?.and(0xFF)?.takeIf { it in 0..100 } ?: return null
        val chargingValue = charging.getOrNull(index)?.toInt()?.and(0xFF) ?: 0
        val isConnected = reportedConnected
            ?: if (inferAvailabilityFromBattery) level > 0 else true
        return PodParams(
            battery = level,
            isCharging = isConnected && chargingValue != 0,
            isConnected = isConnected,
            rawStatus = chargingValue,
        )
    }

    /** Pro 5 抓包里的 0x05 字段：0 为已出盒，1 为已收纳入盒。 */
    private fun ByteArray.reportedConnectedAt(index: Int): Boolean? =
        when (getOrNull(index)?.toInt()?.and(0xFF)) {
            0 -> true
            1 -> false
            else -> null
        }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
