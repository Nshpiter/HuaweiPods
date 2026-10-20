package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/** FreeClip 1 settings verified by the guided 2026-08-21 Huawei Audio capture. */
object HuaweiFreeClipController {
    private val adaptiveChannelsQuery = hex("5A0005002B9A0100BC81")
    private val adaptiveChannelsDisabled = hex("5A0006002B990101009738")
    private val adaptiveChannelsEnabled = hex("5A0006002B990101018719")
    private val dropReminderQuery = hex("5A0008002BB40101070200DDE9")
    private val dropReminderDisabled = hex("5A0009002BB4010107020100AFA4")
    private val dropReminderEnabled = hex("5A0009002BB4010107020101BF85")

    fun requestWearDetectionState(
        context: Context,
        device: BluetoothDevice,
        onState: (Boolean?) -> Unit,
    ) = HuaweiWearDetectionController.requestState(
        context,
        device,
        HuaweiDeviceRoute.HUAWEI_FREECLIP,
        onState,
    )

    fun setWearDetection(
        context: Context,
        device: BluetoothDevice,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = HuaweiWearDetectionController.setEnabled(
        context,
        device,
        HuaweiDeviceRoute.HUAWEI_FREECLIP,
        enabled,
        onComplete,
    )

    fun requestAdaptiveChannelsState(
        context: Context,
        device: BluetoothDevice,
        onState: (Boolean?) -> Unit,
    ) = requestBoolean(
        context = context,
        device = device,
        packet = adaptiveChannelsQueryPacket(),
        description = "freeclip adaptive-channels-state",
        parser = ::parseAdaptiveChannelsState,
        onState = onState,
    )

    fun setAdaptiveChannels(
        context: Context,
        device: BluetoothDevice,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setVerifiedBoolean(
        context = context,
        device = device,
        packet = adaptiveChannelsPacket(enabled),
        description = "freeclip adaptive-channels enabled=$enabled",
        expected = enabled,
        parser = ::parseAdaptiveChannelsState,
        onComplete = onComplete,
    )

    fun requestDropReminderState(
        context: Context,
        device: BluetoothDevice,
        onState: (Boolean?) -> Unit,
    ) = requestBoolean(
        context = context,
        device = device,
        packet = dropReminderQueryPacket(),
        description = "freeclip drop-reminder-state",
        parser = ::parseDropReminderState,
        onState = onState,
    )

    fun setDropReminder(
        context: Context,
        device: BluetoothDevice,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) = setVerifiedBoolean(
        context = context,
        device = device,
        packet = dropReminderPacket(enabled),
        description = "freeclip drop-reminder enabled=$enabled",
        expected = enabled,
        parser = ::parseDropReminderState,
        onComplete = onComplete,
    )

    internal fun adaptiveChannelsQueryPacket(): ByteArray = adaptiveChannelsQuery.copyOf()

    internal fun adaptiveChannelsPacket(enabled: Boolean): ByteArray =
        (if (enabled) adaptiveChannelsEnabled else adaptiveChannelsDisabled).copyOf()

    internal fun dropReminderQueryPacket(): ByteArray = dropReminderQuery.copyOf()

    internal fun dropReminderPacket(enabled: Boolean): ByteArray =
        (if (enabled) dropReminderEnabled else dropReminderDisabled).copyOf()

    internal fun parseAdaptiveChannelsState(stream: ByteArray): Boolean? =
        latestBooleanState(stream) { frame ->
            if (
                frame.u8OrNull(4) == 0x2B &&
                frame.u8OrNull(5) == 0x9A &&
                frame.u8OrNull(6) == 0x01 &&
                frame.u8OrNull(7) == 0x01
            ) {
                frame.u8OrNull(8)
            } else {
                null
            }
        }

    internal fun parseDropReminderState(stream: ByteArray): Boolean? =
        latestBooleanState(stream) { frame ->
            if (
                frame.u8OrNull(4) == 0x2B &&
                frame.u8OrNull(5) == 0xB4 &&
                frame.u8OrNull(6) == 0x01 &&
                frame.u8OrNull(7) == 0x01 &&
                frame.u8OrNull(8) == 0x07 &&
                frame.u8OrNull(9) == 0x02 &&
                frame.u8OrNull(10) == 0x01
            ) {
                frame.u8OrNull(11)
            } else {
                null
            }
        }

    private fun requestBoolean(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        parser: (ByteArray) -> Boolean?,
        onState: (Boolean?) -> Unit,
    ) {
        if (!isExpectedTarget(context, device)) {
            onState(null)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP,
            packet = packet,
            description = description,
            responseComplete = { parser(it) != null },
            onResponse = { onState(parser(it)) },
        )
    }

    private fun setVerifiedBoolean(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        expected: Boolean,
        parser: (ByteArray) -> Boolean?,
        onComplete: ((Boolean) -> Unit)?,
    ) {
        if (!isExpectedTarget(context, device)) {
            onComplete?.invoke(false)
            return
        }
        val completed = AtomicBoolean(false)
        fun complete(success: Boolean) {
            if (completed.compareAndSet(false, true)) onComplete?.invoke(success)
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP,
            packet = packet,
            description = description,
            responseComplete = { parser(it) != null },
            onComplete = { writeSucceeded ->
                if (!writeSucceeded) complete(false)
            },
            onResponse = { response -> complete(parser(response) == expected) },
        )
    }

    @SuppressLint("MissingPermission")
    private fun isExpectedTarget(context: Context, device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == HuaweiDeviceRoute.HUAWEI_FREECLIP
    }
}

private fun latestBooleanState(
    stream: ByteArray,
    valueFromFrame: (ByteArray) -> Int?,
): Boolean? {
    var latest: Boolean? = null
    freeClipFrames(stream).forEach { frame ->
        latest = when (valueFromFrame(frame)) {
            0x00 -> false
            0x01 -> true
            else -> latest
        }
    }
    return latest
}

private fun freeClipFrames(stream: ByteArray): Sequence<ByteArray> = sequence {
    var offset = 0
    while (offset + 5 <= stream.size) {
        if (stream[offset].u8() != 0x5A || stream[offset + 1].u8() != 0x00) {
            offset++
            continue
        }
        val payloadLength = stream[offset + 2].u8() or (stream[offset + 3].u8() shl 8)
        val frameSize = 5 + payloadLength
        if (frameSize <= 5 || offset + frameSize > stream.size) {
            offset++
            continue
        }
        yield(stream.copyOfRange(offset, offset + frameSize))
        offset += frameSize
    }
}

private fun ByteArray.u8OrNull(index: Int): Int? = getOrNull(index)?.u8()

private fun Byte.u8(): Int = toInt() and 0xFF

private fun hex(value: String): ByteArray = value.chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()
