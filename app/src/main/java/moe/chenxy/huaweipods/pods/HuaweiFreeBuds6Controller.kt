package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/** 000155/02, Smart Audio 2.0.7.321, TX/RX capture on 2026-10-09. */
object HuaweiFreeBuds6Controller {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6

    @SuppressLint("MissingPermission")
    private fun accepts(context: Context, device: BluetoothDevice): Boolean =
        DeviceRoutePrefs.resolve(
            context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE),
            device.address,
            device.name ?: device.alias,
        ) == route

    /** Query the verified switches and EQ in one connection instead of opening one per row. */
    fun requestState(context: Context, device: BluetoothDevice, complete: (FreeBuds6State) -> Unit) {
        val query = FreeBuds6Setting.entries.fold(byteArrayOf()) { bytes, setting -> bytes + setting.query() } +
            HuaweiEqualizerCodec.stateQueryPacket()
        request(context, device, query, { FreeBuds6State.parse(it).isComplete }) {
            complete(FreeBuds6State.parse(it))
        }
    }

    fun set(
        context: Context,
        device: BluetoothDevice,
        setting: FreeBuds6Setting,
        enabled: Boolean,
        complete: (Boolean) -> Unit,
    ) = writeAndVerify(context, device, setting.packet(enabled), setting.query(),
        { setting.parse(it) == enabled }, complete)

    fun setEffect(
        context: Context,
        device: BluetoothDevice,
        effect: FreeBuds6SoundEffect,
        complete: (Boolean) -> Unit,
    ) = writeAndVerify(context, device, effect.packet(), HuaweiEqualizerCodec.stateQueryPacket(),
        { HuaweiEqualizerCodec.parseState(it)?.selectedId == effect.protocolValue }, complete)

    private fun writeAndVerify(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        query: ByteArray,
        matches: (ByteArray) -> Boolean,
        complete: (Boolean) -> Unit,
    ) {
        request(context, device, packet + query, matches) { response ->
            if (matches(response)) complete(true)
            else request(context, device, query, matches) { complete(matches(it)) }
        }
    }

    private fun request(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        matches: (ByteArray) -> Boolean,
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
            description = "freebuds6 state",
            responseWindowMs = 1_500L,
            responseComplete = matches,
            onComplete = { if (!it) deliver(byteArrayOf()) },
            onResponse = deliver,
        )
    }
}

/** Only settings with captured setters; shared codecs do not enable other FreeBuds 7 commands. */
enum class FreeBuds6Setting(private val codec: FreeBuds7Setting) {
    ADAPTIVE_VOLUME(FreeBuds7Setting.ADAPTIVE_VOLUME),
    DROP_REMINDER(FreeBuds7Setting.DROP_REMINDER),
    WEAR_DETECTION(FreeBuds7Setting.WEAR_DETECTION),
    CASE_SOUND(FreeBuds7Setting.CASE_SOUND),
    LOW_LATENCY(FreeBuds7Setting.LOW_LATENCY);

    fun query(): ByteArray = codec.query()
    fun packet(enabled: Boolean): ByteArray = codec.packet(enabled)
    fun parse(bytes: ByteArray): Boolean? = codec.parse(bytes)
}

data class FreeBuds6State(
    val settings: Map<FreeBuds6Setting, Boolean?>,
    val equalizer: HuaweiEqualizerState?,
) {
    val isComplete: Boolean get() = FreeBuds6Setting.entries.all { settings[it] != null } && equalizer != null

    companion object {
        fun parse(bytes: ByteArray) = FreeBuds6State(
            FreeBuds6Setting.entries.associateWith { it.parse(bytes) },
            HuaweiEqualizerCodec.parseState(bytes),
        )
    }
}

/** 0B/0C/C8 names are not present in the capture; do not give them guessed marketing names. */
enum class FreeBuds6SoundEffect(val protocolValue: Int) {
    DEFAULT(0), BASS(2), TREBLE(3), CLEAR_VOICE(9), CLASSICAL(0xC9);

    fun packet(): ByteArray = if (this == CLASSICAL) {
        freeBuds7Hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367")
    } else {
        requireNotNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(HuaweiDeviceRoute.HUAWEI_FREEBUDS6, protocolValue))
    }
}
