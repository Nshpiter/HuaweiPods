package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context

object HuaweiGestureController {
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_SIDE = "side"
    const val EXTRA_GESTURE_KIND = "gesture_kind"
    const val EXTRA_GESTURE_ACTION = "gesture_action"
    const val EXTRA_SWIPE_ACTION = "swipe_action"
    const val EXTRA_DOUBLE_LEFT_ACTION = "double_left_action"
    const val EXTRA_DOUBLE_RIGHT_ACTION = "double_right_action"
    const val EXTRA_TRIPLE_LEFT_ACTION = "triple_left_action"
    const val EXTRA_TRIPLE_RIGHT_ACTION = "triple_right_action"
    const val EXTRA_SWIPE_LEFT_ACTION = "swipe_left_action"
    const val EXTRA_SWIPE_RIGHT_ACTION = "swipe_right_action"
    const val EXTRA_LONG_PRESS_LEFT_ACTION = "long_press_left_action"
    const val EXTRA_LONG_PRESS_RIGHT_ACTION = "long_press_right_action"

    private val freeClip2DoubleTapQuery = hex("5A000700012001000200E897")
    private val freeClip2TripleTapQuery = hex("5A0007000126010002002512")
    private val freeClip2SwipeQuery = hex("5A0007002B1F01000200328A")
    private val freeBuds4eLongPressQuery = hex("5A0007002B170100020030A7")
    private val modernLongPressRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREEARC,
    )
    private val modernSwipeVolumeRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
    )
    private val modernPinchRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
    )

    fun supportsTapAction(
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        action: HuaweiTapAction,
    ): Boolean = action in HuaweiTapAction.availableFor(route, kind)

    fun supportsDoubleTapAction(
        route: HuaweiDeviceRoute,
        action: HuaweiTapAction,
    ): Boolean = supportsTapAction(route, HuaweiGestureKind.DOUBLE_TAP, action)

    fun supportsDoubleTapAction(
        route: HuaweiDeviceRoute,
        action: HuaweiGestureAction,
    ): Boolean = supportsDoubleTapAction(route, action.asTapAction())

    fun supportsSwipeAction(
        route: HuaweiDeviceRoute,
        action: HuaweiSwipeAction,
    ): Boolean = action in HuaweiSwipeAction.availableFor(route)

    /** Keeps the original FreeBuds 3 packet API used by existing callers. */
    fun buildDoubleTapPacket(
        side: HuaweiGestureSide,
        action: HuaweiGestureAction,
    ): ByteArray = requireNotNull(
        buildDoubleTapPacket(
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            side = side,
            action = action.asTapAction(),
        ),
    )

    fun buildDoubleTapPacket(
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiGestureAction,
    ): ByteArray? = buildDoubleTapPacket(route, side, action.asTapAction())

    fun buildDoubleTapPacket(
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
    ): ByteArray? = buildTapPacket(route, HuaweiGestureKind.DOUBLE_TAP, side, action)

    fun buildTripleTapPacket(
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
    ): ByteArray? = buildTapPacket(route, HuaweiGestureKind.TRIPLE_TAP, side, action)

    fun buildTapPacket(
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
    ): ByteArray? {
        val command = when (kind) {
            HuaweiGestureKind.DOUBLE_TAP -> 0x1F
            HuaweiGestureKind.TRIPLE_TAP -> 0x25
            HuaweiGestureKind.SWIPE -> return null
        }
        val protocolValue = action.protocolValue(route, kind) ?: return null
        return buildSideActionPacket(
            service = 0x01,
            command = command,
            side = side,
            protocolValue = protocolValue,
        )
    }

    fun buildSwipePacket(
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiSwipeAction,
    ): ByteArray? {
        if (!supportsSwipeAction(route, action)) return null
        return when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            -> buildSideActionPacket(
                service = 0x2B,
                command = 0x1E,
                side = side,
                protocolValue = action.protocolValue,
            )

            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> packetWithCrc(
                byteArrayOf(
                    0x5A,
                    0x00,
                    0x09,
                    0x00,
                    0x2B,
                    0x1E,
                    side.protocolValue.toByte(),
                    side.protocolValue.toByte(),
                    action.protocolValue.toByte(),
                    side.protocolValue.toByte(),
                    side.protocolValue.toByte(),
                    action.protocolValue.toByte(),
                ),
            )

            else -> null
        }
    }

    fun buildFreeBuds6iTapPacket(
        gesture: FreeBuds6iTapGesture,
        side: HuaweiGestureSide,
        action: FreeBuds6iTapAction,
    ): ByteArray? = buildTapPacket(
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        kind = gesture.kind,
        side = side,
        action = action.tapAction,
    )

    fun buildFreeBudsPro3LongPressPacket(
        side: HuaweiGestureSide,
        action: FreeBudsPro3LongPressAction,
    ): ByteArray = buildSideActionPacket(
        service = 0x2B,
        command = 0x16,
        side = side,
        protocolValue = action.protocolValue,
    )

    fun buildModernEarbudsLongPressPacket(
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: FreeBudsPro3LongPressAction,
    ): ByteArray? {
        if (route !in modernLongPressRoutes) return null
        val protocolValue = action.protocolValue(route) ?: return null
        return buildSideActionPacket(
            service = 0x2B,
            command = 0x16,
            side = side,
            protocolValue = protocolValue,
        )
    }

    fun buildFreeBudsPro3GestureTogglePacket(
        gesture: FreeBudsPro3GestureToggle,
        enabled: Boolean,
    ): ByteArray {
        val action = if (enabled) gesture.enabledValue else 0xFF
        return packetWithCrc(
            byteArrayOf(
                0x5A,
                0x00,
                0x0F,
                0x00,
                0x2B,
                0x92.toByte(),
                0x01,
                0x01,
                gesture.slot.toByte(),
                0x02,
                0x01,
                gesture.context.toByte(),
                0x03,
                0x01,
                action.toByte(),
                0x04,
                0x01,
                action.toByte(),
            ),
        )
    }

    fun buildFreeBudsPro3SwipeVolumePacket(enabled: Boolean): ByteArray {
        val action = if (enabled) 0x00 else 0xFF
        return packetWithCrc(
            byteArrayOf(
                0x5A,
                0x00,
                0x09,
                0x00,
                0x2B,
                0x1E,
                0x01,
                0x01,
                action.toByte(),
                0x02,
                0x02,
                action.toByte(),
            ),
        )
    }

    fun setDoubleTap(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiGestureAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setTap(context, device, route, HuaweiGestureKind.DOUBLE_TAP, side, action.asTapAction(), onComplete)

    fun setDoubleTap(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setTap(context, device, route, HuaweiGestureKind.DOUBLE_TAP, side, action, onComplete)

    fun setTap(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
        side: HuaweiGestureSide,
        action: HuaweiTapAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = buildTapPacket(route, kind, side, action) ?: run {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "gesture kind=${kind.extraValue} side=${side.extraValue} action=${action.extraValue}",
            onComplete = onComplete,
        )
    }

    fun setSwipe(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: HuaweiSwipeAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = buildSwipePacket(route, side, action) ?: run {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "gesture kind=${HuaweiGestureKind.SWIPE.extraValue} side=${side.extraValue} action=${action.extraValue}",
            onComplete = onComplete,
        )
    }

    internal fun buildGestureStateQuery(route: HuaweiDeviceRoute): ByteArray? =
        if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS4E) {
            freeClip2DoubleTapQuery + freeBuds4eLongPressQuery
        } else if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS5I) {
            freeClip2DoubleTapQuery
        } else if (route == HuaweiDeviceRoute.HUAWEI_FREEARC) {
            freeClip2DoubleTapQuery + freeClip2TripleTapQuery +
                freeBuds4eLongPressQuery + freeClip2SwipeQuery
        } else if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5) {
            freeClip2TripleTapQuery + freeClip2SwipeQuery
        } else if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
            route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
            route == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
        ) {
            freeClip2DoubleTapQuery + freeClip2TripleTapQuery + freeClip2SwipeQuery
        } else {
            null
        }

    fun requestGestureState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (HuaweiGestureState) -> Unit,
    ) {
        val packet = buildGestureStateQuery(route) ?: return
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "gesture-state",
            responseWindowMs = 1_500L,
            onResponse = { response ->
                HuaweiRfcommResponseParser.parseGestureState(response, route)
                    .takeIf(HuaweiGestureState::hasAnyState)
                    ?.let(onState)
            },
        )
    }

    fun setFreeBuds6iTap(
        context: Context,
        device: BluetoothDevice,
        gesture: FreeBuds6iTapGesture,
        side: HuaweiGestureSide,
        action: FreeBuds6iTapAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (action !in gesture.supportedActions) {
            onComplete?.invoke(false)
            return
        }
        setTap(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            kind = gesture.kind,
            side = side,
            action = action.tapAction,
            onComplete = onComplete,
        )
    }

    fun setFreeBudsPro3LongPress(
        context: Context,
        device: BluetoothDevice,
        side: HuaweiGestureSide,
        action: FreeBudsPro3LongPressAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setModernEarbudsLongPress(
        context = context,
        device = device,
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        side = side,
        action = action,
        onComplete = onComplete,
    )

    fun setModernEarbudsLongPress(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        side: HuaweiGestureSide,
        action: FreeBudsPro3LongPressAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = buildModernEarbudsLongPressPacket(route, side, action)
        if (packet == null) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "modern-earbuds long-press side=${side.extraValue} action=${action.extraValue}",
            onComplete = onComplete,
        )
    }

    fun setFreeBudsPro3GestureToggle(
        context: Context,
        device: BluetoothDevice,
        gesture: FreeBudsPro3GestureToggle,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setModernEarbudsGestureToggle(
        context = context,
        device = device,
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        gesture = gesture,
        enabled = enabled,
        onComplete = onComplete,
    )

    fun setModernEarbudsGestureToggle(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        gesture: FreeBudsPro3GestureToggle,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (route !in modernPinchRoutes) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = buildFreeBudsPro3GestureTogglePacket(gesture, enabled),
            description = "modern-earbuds pinch=${gesture.extraValue} enabled=$enabled",
            onComplete = onComplete,
        )
    }

    fun setFreeBudsPro3SwipeVolume(
        context: Context,
        device: BluetoothDevice,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setModernEarbudsSwipeVolume(
        context = context,
        device = device,
        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        enabled = enabled,
        onComplete = onComplete,
    )

    fun setModernEarbudsSwipeVolume(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        if (route !in modernSwipeVolumeRoutes) {
            onComplete?.invoke(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = buildFreeBudsPro3SwipeVolumePacket(enabled),
            description = "modern-earbuds swipe-volume enabled=$enabled",
            onComplete = onComplete,
        )
    }

    private fun buildSideActionPacket(
        service: Int,
        command: Int,
        side: HuaweiGestureSide,
        protocolValue: Int,
    ): ByteArray = packetWithCrc(
        byteArrayOf(
            0x5A,
            0x00,
            0x06,
            0x00,
            service.toByte(),
            command.toByte(),
            side.protocolValue.toByte(),
            0x01,
            protocolValue.toByte(),
        ),
    )

    private fun packetWithCrc(payload: ByteArray): ByteArray {
        val crc = crc16Xmodem(payload)
        return payload + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun crc16Xmodem(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

enum class HuaweiGestureKind(val extraValue: String) {
    DOUBLE_TAP("double_tap"),
    TRIPLE_TAP("triple_tap"),
    SWIPE("swipe");

    companion object {
        fun fromExtra(value: String?): HuaweiGestureKind? =
            entries.firstOrNull { it.extraValue.equals(value, ignoreCase = true) }
    }
}

enum class HuaweiGestureSide(
    val protocolValue: Int,
    val extraValue: String,
) {
    LEFT(0x01, "left"),
    RIGHT(0x02, "right");

    companion object {
        fun fromExtra(value: String?): HuaweiGestureSide? =
            entries.firstOrNull { it.extraValue.equals(value, ignoreCase = true) }

        fun fromProtocolValue(value: Int): HuaweiGestureSide? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

/**
 * Legacy FreeBuds 3 action type. Its numeric values must not be reused for other routes.
 */
enum class HuaweiGestureAction(
    val protocolValue: Int,
    val extraValue: String,
) {
    PLAY_NEXT(0x04, "play_next"),
    PLAY_PAUSE(0x01, "play_pause"),
    NOISE_CANCELLATION(0x03, "noise_cancellation"),
    VOICE_ASSISTANT(0x00, "voice_assistant"),
    NONE(0xFF, "none");

    companion object {
        val all: List<HuaweiGestureAction> = listOf(
            PLAY_NEXT,
            PLAY_PAUSE,
            VOICE_ASSISTANT,
            NOISE_CANCELLATION,
            NONE,
        )

        fun fromExtra(value: String?): HuaweiGestureAction? =
            entries.firstOrNull { it.extraValue.equals(value, ignoreCase = true) }

        fun fromProtocolValue(value: Int): HuaweiGestureAction? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

/** Route-independent action semantics; [protocolValue] performs the model dispatch. */
enum class HuaweiTapAction(val extraValue: String) {
    PLAY_NEXT("play_next"),
    PLAY_PREVIOUS("play_previous"),
    PLAY_PAUSE("play_pause"),
    NOISE_CANCELLATION("noise_cancellation"),
    SPATIAL_AUDIO("spatial_audio"),
    VOICE_ASSISTANT("voice_assistant"),
    NONE("none");

    companion object {
        private val freeBuds3DoubleTapActions = listOf(
            PLAY_NEXT,
            PLAY_PAUSE,
            VOICE_ASSISTANT,
            NOISE_CANCELLATION,
            NONE,
        )
        private val freeBuds6iDoubleTapActions = listOf(PLAY_PAUSE, PLAY_NEXT)
        private val freeBuds6iTripleTapActions = listOf(PLAY_NEXT, PLAY_PREVIOUS, NONE)
        private val freeClip2DoubleTapActions = listOf(
            PLAY_PAUSE,
            PLAY_NEXT,
            SPATIAL_AUDIO,
            VOICE_ASSISTANT,
            NONE,
        )
        private val freeClip2TripleTapActions = listOf(PLAY_NEXT, PLAY_PREVIOUS, NONE)
        private val freeBuds7iDoubleTapActions = listOf(
            PLAY_PAUSE,
            PLAY_NEXT,
            PLAY_PREVIOUS,
            VOICE_ASSISTANT,
            NONE,
        )
        private val freeBuds7iTripleTapActions = listOf(PLAY_NEXT, PLAY_PREVIOUS, NONE)
        private val freeBuds4eDoubleTapActions = listOf(
            PLAY_PAUSE,
            PLAY_NEXT,
            PLAY_PREVIOUS,
            VOICE_ASSISTANT,
            NONE,
        )
        private val eyewear2DoubleTapActions = listOf(PLAY_PAUSE, VOICE_ASSISTANT, NONE)

        fun availableFor(
            route: HuaweiDeviceRoute,
            kind: HuaweiGestureKind,
        ): List<HuaweiTapAction> = when (route to kind) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to HuaweiGestureKind.DOUBLE_TAP -> freeBuds3DoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to HuaweiGestureKind.DOUBLE_TAP -> freeBuds4eDoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to HuaweiGestureKind.DOUBLE_TAP -> freeBuds7iDoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to HuaweiGestureKind.DOUBLE_TAP -> freeBuds6iDoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to HuaweiGestureKind.TRIPLE_TAP -> freeBuds6iTripleTapActions
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 to HuaweiGestureKind.DOUBLE_TAP -> freeClip2DoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 to HuaweiGestureKind.TRIPLE_TAP -> freeClip2TripleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to HuaweiGestureKind.DOUBLE_TAP -> freeBuds7iDoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to HuaweiGestureKind.TRIPLE_TAP -> freeBuds7iTripleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to HuaweiGestureKind.TRIPLE_TAP -> freeBuds7iTripleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEARC to HuaweiGestureKind.DOUBLE_TAP -> freeBuds7iDoubleTapActions
            HuaweiDeviceRoute.HUAWEI_FREEARC to HuaweiGestureKind.TRIPLE_TAP -> freeBuds7iTripleTapActions
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to HuaweiGestureKind.DOUBLE_TAP -> eyewear2DoubleTapActions
            else -> emptyList()
        }

        fun fromExtra(value: String?): HuaweiTapAction? =
            entries.firstOrNull { it.extraValue.equals(value, ignoreCase = true) }

        fun fromProtocolValue(
            route: HuaweiDeviceRoute,
            kind: HuaweiGestureKind,
            value: Int,
        ): HuaweiTapAction? = availableFor(route, kind).firstOrNull {
            it.protocolValue(route, kind) == value
        }
    }

    fun protocolValue(
        route: HuaweiDeviceRoute,
        kind: HuaweiGestureKind,
    ): Int? {
        if (this !in availableFor(route, kind)) return null
        return when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3 -> when (this) {
                PLAY_NEXT -> 0x04
                PLAY_PAUSE -> 0x01
                NOISE_CANCELLATION -> 0x03
                VOICE_ASSISTANT -> 0x00
                NONE -> 0xFF
                PLAY_PREVIOUS,
                SPATIAL_AUDIO,
                -> null
            }

            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            -> when (this) {
                PLAY_NEXT -> 0x02
                PLAY_PREVIOUS -> 0x07
                SPATIAL_AUDIO -> 0x07
                PLAY_PAUSE -> 0x01
                VOICE_ASSISTANT -> 0x00
                NONE -> 0xFF
                NOISE_CANCELLATION -> null
            }

            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            -> when (this) {
                PLAY_NEXT -> 0x02
                PLAY_PREVIOUS -> 0x07
                PLAY_PAUSE -> 0x01
                VOICE_ASSISTANT -> 0x00
                NONE -> 0xFF
                NOISE_CANCELLATION,
                SPATIAL_AUDIO,
                -> null
            }

            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> when (this) {
                PLAY_PAUSE -> 0x01
                VOICE_ASSISTANT -> 0x00
                NONE -> 0xFF
                else -> null
            }

            else -> null
        }
    }
}

enum class HuaweiSwipeAction(
    val protocolValue: Int,
    val extraValue: String,
) {
    VOLUME_CONTROL(0x00, "volume_control"),
    TRACK_CONTROL(0x01, "track_control"),
    NONE(0xFF, "none");

    companion object {
        val all: List<HuaweiSwipeAction> = entries.toList()

        fun availableFor(route: HuaweiDeviceRoute): List<HuaweiSwipeAction> = when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> all
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> listOf(VOLUME_CONTROL, NONE)
            HuaweiDeviceRoute.HUAWEI_FREEARC -> listOf(VOLUME_CONTROL, TRACK_CONTROL)
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> all
            else -> emptyList()
        }

        fun fromExtra(value: String?): HuaweiSwipeAction? = when {
            value.equals("volume", ignoreCase = true) -> VOLUME_CONTROL
            value.equals("track", ignoreCase = true) -> TRACK_CONTROL
            else -> entries.firstOrNull { it.extraValue.equals(value, ignoreCase = true) }
        }

        fun fromProtocolValue(value: Int): HuaweiSwipeAction? =
            entries.firstOrNull { it.protocolValue == value }

        fun fromProtocolValue(route: HuaweiDeviceRoute, value: Int): HuaweiSwipeAction? =
            availableFor(route).firstOrNull { it.protocolValue == value }
    }
}

data class HuaweiTapState(
    val left: HuaweiTapAction,
    val right: HuaweiTapAction,
)

data class HuaweiSwipeState(
    val left: HuaweiSwipeAction,
    val right: HuaweiSwipeAction,
)

data class HuaweiGestureState(
    val doubleTap: HuaweiTapState? = null,
    val tripleTap: HuaweiTapState? = null,
    val swipe: HuaweiSwipeState? = null,
    val longPress: HuaweiLongPressState? = null,
) {
    val hasAnyState: Boolean
        get() = doubleTap != null || tripleTap != null || swipe != null || longPress != null
}

data class HuaweiLongPressState(
    val left: FreeBudsPro3LongPressAction,
    val right: FreeBudsPro3LongPressAction,
)

enum class FreeBuds6iTapGesture(
    val kind: HuaweiGestureKind,
    val extraValue: String,
    val supportedActions: List<FreeBuds6iTapAction>,
) {
    DOUBLE_TAP(
        kind = HuaweiGestureKind.DOUBLE_TAP,
        extraValue = "double_tap",
        supportedActions = listOf(FreeBuds6iTapAction.PLAY_PAUSE, FreeBuds6iTapAction.NEXT_TRACK),
    ),
    TRIPLE_TAP(
        kind = HuaweiGestureKind.TRIPLE_TAP,
        extraValue = "triple_tap",
        supportedActions = listOf(
            FreeBuds6iTapAction.NEXT_TRACK,
            FreeBuds6iTapAction.PREVIOUS_TRACK,
            FreeBuds6iTapAction.NONE,
        ),
    ),
}

enum class FreeBuds6iTapAction(
    val protocolValue: Int,
    val extraValue: String,
    val tapAction: HuaweiTapAction,
) {
    PLAY_PAUSE(0x01, "play_pause", HuaweiTapAction.PLAY_PAUSE),
    NEXT_TRACK(0x02, "next_track", HuaweiTapAction.PLAY_NEXT),
    PREVIOUS_TRACK(0x07, "previous_track", HuaweiTapAction.PLAY_PREVIOUS),
    NONE(0xFF, "none", HuaweiTapAction.NONE);

    companion object {
        fun fromProtocolValue(value: Int): FreeBuds6iTapAction? =
            entries.firstOrNull { it.protocolValue == value }
    }
}

enum class FreeBudsPro3LongPressAction(
    val protocolValue: Int,
    val extraValue: String,
) {
    VOICE_ASSISTANT(0x00, "voice_assistant"),
    NOISE_CONTROL(0x0A, "noise_control"),
    SONG_RECOGNITION(0x0E, "song_recognition"),
    NONE(0xFF, "none");

    companion object {
        fun fromProtocolValue(value: Int): FreeBudsPro3LongPressAction? =
            entries.firstOrNull { it.protocolValue == value }

        fun availableFor(route: HuaweiDeviceRoute): List<FreeBudsPro3LongPressAction> = when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E -> listOf(NOISE_CONTROL, SONG_RECOGNITION, NONE)
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            -> listOf(VOICE_ASSISTANT, NOISE_CONTROL, NONE)

            HuaweiDeviceRoute.HUAWEI_FREEARC -> listOf(VOICE_ASSISTANT, NONE)

            else -> emptyList()
        }

        fun fromProtocolValue(
            route: HuaweiDeviceRoute,
            value: Int,
        ): FreeBudsPro3LongPressAction? = availableFor(route).firstOrNull {
            it.protocolValue(route) == value
        }
    }

    fun protocolValue(route: HuaweiDeviceRoute): Int? {
        if (this !in availableFor(route)) return null
        return when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E -> when (this) {
                NOISE_CONTROL -> 0x03
                SONG_RECOGNITION -> 0x0E
                NONE -> 0xFF
                VOICE_ASSISTANT -> null
            }

            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            -> protocolValue

            else -> null
        }
    }
}

enum class FreeBudsPro3GestureToggle(
    val slot: Int,
    val context: Int,
    val enabledValue: Int,
    val extraValue: String,
) {
    CALL_ANSWER_END(0x00, 0x01, 0x00, "call_answer_end"),
    CALL_REJECT(0x01, 0x01, 0x01, "call_reject"),
    MEDIA_PLAY_PAUSE(0x00, 0x02, 0x02, "media_play_pause"),
    MEDIA_NEXT(0x01, 0x02, 0x04, "media_next"),
    MEDIA_PREVIOUS(0x02, 0x02, 0x03, "media_previous"),
}

private fun HuaweiGestureAction.asTapAction(): HuaweiTapAction = when (this) {
    HuaweiGestureAction.PLAY_NEXT -> HuaweiTapAction.PLAY_NEXT
    HuaweiGestureAction.PLAY_PAUSE -> HuaweiTapAction.PLAY_PAUSE
    HuaweiGestureAction.NOISE_CANCELLATION -> HuaweiTapAction.NOISE_CANCELLATION
    HuaweiGestureAction.VOICE_ASSISTANT -> HuaweiTapAction.VOICE_ASSISTANT
    HuaweiGestureAction.NONE -> HuaweiTapAction.NONE
}
