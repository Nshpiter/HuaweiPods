package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context

object HuaweiGestureController {
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_SIDE = "side"
    const val EXTRA_GESTURE_ACTION = "gesture_action"

    fun buildDoubleTapPacket(side: HuaweiGestureSide, action: HuaweiGestureAction): ByteArray {
        val payload = byteArrayOf(
            0x5A.toByte(),
            0x00,
            0x06,
            0x00,
            0x01,
            0x1F,
            side.protocolValue.toByte(),
            0x01,
            action.protocolValue.toByte(),
        )
        val crc = crc16Xmodem(payload)
        return payload + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    fun setDoubleTap(
        context: Context,
        device: BluetoothDevice,
        side: HuaweiGestureSide,
        action: HuaweiGestureAction,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            packet = buildDoubleTapPacket(side, action),
            description = "gesture side=${side.extraValue} action=${action.extraValue}",
            onComplete = onComplete,
        )
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
