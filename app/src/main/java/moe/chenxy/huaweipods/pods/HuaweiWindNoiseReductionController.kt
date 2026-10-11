package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothDevice
import android.content.Context

/** FreeBuds SE 4 ANC 防风噪开关，协议来自 000169/02 实机抓包。 */
object HuaweiWindNoiseReductionController {
    private val stateQuery = hex("5A0008002BB401011D0200594B")
    private val disabledPacket = hex("5A0009002BB401011D020100DCA8")
    private val enabledPacket = hex("5A0009002BB401011D020101CC89")

    fun requestState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (Boolean?) -> Unit,
    ) {
        if (!route.supportsWindNoiseReduction) {
            onState(null)
            return
        }
        if (HuaweiSharedControlClient.usesSharedConnection(context, route)) {
            HuaweiSharedControlClient.request(context, device, route, HuaweiSharedControlPolicy.WIND_READ) {
                onState(it?.let(::parseState))
            }
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = queryPacket(),
            description = "wind-noise-state",
            responseWindowMs = 1_000L,
            responseComplete = { parseState(it) != null },
            onComplete = { success -> if (!success) onState(null) },
            onResponse = { onState(parseState(it)) },
        )
    }

    fun setEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onState: (Boolean?) -> Unit,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!route.supportsWindNoiseReduction) {
            onComplete(false)
            return
        }
        if (HuaweiSharedControlClient.usesSharedConnection(context, route)) {
            HuaweiSharedControlClient.request(
                context, device, route, HuaweiSharedControlPolicy.WIND_SET, enabled = enabled,
            ) {
                val state = it?.let(::parseState)
                onState(state)
                onComplete(state == enabled)
            }
            return
        }
        var actualState: Boolean? = null
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = setPacket(enabled),
            description = "wind-noise enabled=$enabled",
            responseWindowMs = 1_000L,
            responseComplete = { parseState(it) != null },
            onResponse = { response ->
                actualState = parseState(response)
                onState(actualState)
            },
            onComplete = { success -> onComplete(success && actualState == enabled) },
        )
    }

    internal fun queryPacket(): ByteArray = stateQuery.copyOf()

    internal fun setPacket(enabled: Boolean): ByteArray =
        (if (enabled) enabledPacket else disabledPacket).copyOf()

    internal fun parseState(stream: ByteArray): Boolean? {
        var latest: Boolean? = null
        var offset = 0
        while (offset <= stream.size - MIN_FRAME_SIZE) {
            if (stream[offset].u8() != FRAME_MAGIC) {
                offset++
                continue
            }
            val payloadSize = (stream[offset + 1].u8() shl 8) or stream[offset + 2].u8()
            val frameSize = payloadSize + FRAME_OVERHEAD
            if (frameSize < MIN_FRAME_SIZE || offset + frameSize > stream.size) {
                offset++
                continue
            }
            val frame = stream.copyOfRange(offset, offset + frameSize)
            if (frame.hasValidCrc() && frame.isWindNoiseStateFrame()) {
                latest = when (frame[STATE_VALUE_INDEX].u8()) {
                    0 -> false
                    1 -> true
                    else -> null
                }
            }
            offset += frameSize
        }
        return latest
    }

    private fun ByteArray.isWindNoiseStateFrame(): Boolean =
        size >= MIN_FRAME_SIZE &&
            this[4].u8() == 0x2B && this[5].u8() == 0xB4 &&
            this[6].u8() == 0x01 && this[7].u8() == 0x01 &&
            this[8].u8() == FEATURE_WIND_NOISE &&
            this[9].u8() == 0x02 && this[10].u8() == 0x01

    private fun ByteArray.hasValidCrc(): Boolean {
        if (size < CRC_SIZE) return false
        val expected = (this[size - 2].u8() shl 8) or this[size - 1].u8()
        return crc16Xmodem(copyOf(size - CRC_SIZE)) == expected
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

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private const val FRAME_MAGIC = 0x5A
    private const val FRAME_OVERHEAD = 5
    private const val CRC_SIZE = 2
    private const val FEATURE_WIND_NOISE = 0x1D
    private const val STATE_VALUE_INDEX = 11
    private const val MIN_FRAME_SIZE = 14
}
