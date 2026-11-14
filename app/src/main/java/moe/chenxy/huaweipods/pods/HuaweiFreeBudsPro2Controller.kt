package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/** FreeBuds Pro 2 000131/0E: guided TX/RX capture from 2026-10-28. */
object HuaweiFreeBudsPro2Controller {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO2

    fun requestState(context: Context, device: BluetoothDevice, complete: (FreeBudsPro2State) -> Unit) {
        val query = FreeBudsPro2Setting.entries.fold(HuaweiEqualizerCodec.stateQueryPacket()) { bytes, setting ->
            bytes + setting.query()
        }
        request(context, device, query, { FreeBudsPro2State.parse(it).isComplete }) {
            complete(FreeBudsPro2State.parse(it))
        }
    }

    fun set(context: Context, device: BluetoothDevice, setting: FreeBudsPro2Setting,
            enabled: Boolean, complete: (Boolean) -> Unit) =
        writeAndVerify(context, device, setting.packet(enabled), setting.query(),
            { setting.parse(it) == enabled }, complete)

    fun setEffect(context: Context, device: BluetoothDevice, effect: FreeBudsPro2SoundEffect,
                  complete: (Boolean) -> Unit) =
        writeAndVerify(context, device, effect.packet(), HuaweiEqualizerCodec.stateQueryPacket(),
            { HuaweiEqualizerCodec.parseState(it)?.selectedId == effect.protocolValue }, complete)

    fun setCustom(context: Context, device: BluetoothDevice, presetId: Int, name: String,
                  gains: List<Int>, complete: (Boolean) -> Unit) {
        val packet = HuaweiEqualizerCodec.buildCustomPacket(gains, name, 1, presetId)
            ?: return complete(false)
        writeAndVerify(context, device, packet, HuaweiEqualizerCodec.stateQueryPacket(), { bytes ->
            HuaweiEqualizerCodec.parseState(bytes)?.let {
                it.selectedId == presetId && it.selectedName == name.trim() && it.selectedGains == gains
            } == true
        }, complete)
    }

    private fun writeAndVerify(context: Context, device: BluetoothDevice, packet: ByteArray,
                               query: ByteArray, matches: (ByteArray) -> Boolean, complete: (Boolean) -> Unit) {
        request(context, device, packet + query, matches) { bytes ->
            if (matches(bytes)) complete(true)
            else request(context, device, query, matches) { complete(matches(it)) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun request(context: Context, device: BluetoothDevice, packet: ByteArray,
                        matches: (ByteArray) -> Boolean, complete: (ByteArray) -> Unit) {
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        if (DeviceRoutePrefs.resolve(prefs, device.address, device.name ?: device.alias) != route) {
            complete(byteArrayOf())
            return
        }
        var delivered = false
        val deliver: (ByteArray) -> Unit = { bytes ->
            if (!delivered) {
                delivered = true
                complete(bytes)
            }
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context, device, route, packet,
            description = "freebuds-pro2 state",
            responseWindowMs = 1_500L,
            responseComplete = matches,
            onComplete = { if (!it) deliver(byteArrayOf()) },
            onResponse = deliver,
        )
    }
}

/** Only switches with matching query, setter and state frames in this capture. */
enum class FreeBudsPro2Setting(private val codec: FreeBuds7Setting) {
    DUAL_DEVICE(FreeBuds7Setting.AUTO_SWITCH),
    HIGH_QUALITY(FreeBuds7Setting.HIGH_QUALITY),
    PINCH_PLAY(FreeBuds7Setting.PINCH_PLAY),
    PINCH_NEXT(FreeBuds7Setting.PINCH_NEXT),
    PINCH_PREVIOUS(FreeBuds7Setting.PINCH_PREVIOUS),
    SWIPE_VOLUME(FreeBuds7Setting.SWIPE_VOLUME);

    fun query(): ByteArray = codec.query()
    fun packet(enabled: Boolean): ByteArray = codec.packet(enabled)
    fun parse(bytes: ByteArray): Boolean? = codec.parse(bytes)
}

data class FreeBudsPro2State(
    val settings: Map<FreeBudsPro2Setting, Boolean?>,
    val equalizer: HuaweiEqualizerState?,
) {
    val isComplete: Boolean get() = equalizer != null && FreeBudsPro2Setting.entries.all { settings[it] != null }

    companion object {
        fun parse(bytes: ByteArray) = FreeBudsPro2State(
            FreeBudsPro2Setting.entries.associateWith { it.parse(bytes) },
            HuaweiEqualizerCodec.parseState(bytes),
        )
    }
}

/** Names and curves are confirmed by the official ZAA7 EQ configuration. */
enum class FreeBudsPro2SoundEffect(val protocolValue: Int) {
    DEFAULT(5), BASS(2), TREBLE(3), CLEAR_VOICE(9), CLASSICAL(0xC8), HIFI_LIVE(0xC9);

    fun packet(): ByteArray = when (this) {
        CLASSICAL -> freeBuds7Hex("5A001D002B490101C802010A050101030A0F0F0AFB0F190FFB322D0403323030FFFC")
        HIFI_LIVE -> freeBuds7Hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367")
        else -> requireNotNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO2, protocolValue,
        ))
    }
}
