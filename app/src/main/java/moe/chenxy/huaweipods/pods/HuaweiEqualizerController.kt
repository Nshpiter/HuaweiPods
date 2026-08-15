package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/** Direct equalizer transport for models whose 0x2B/0x49 write was captured and verified. */
object HuaweiEqualizerController {
    fun requestState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (HuaweiEqualizerState?) -> Unit,
    ) {
        if (!isTarget(context, device, route) || !HuaweiEqualizerCodec.supportsStateRead(route)) {
            onState(null)
            return
        }
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = HuaweiEqualizerCodec.stateQueryPacket(),
            description = "equalizer-state-query route=$route",
            responseWindowMs = 1_500L,
            responseComplete = { HuaweiEqualizerCodec.parseState(it) != null },
            onResponse = { onState(HuaweiEqualizerCodec.parseState(it)) },
        )
    }

    fun setCustom(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        gains: List<Int>,
        presetName: String,
        onComplete: (Boolean) -> Unit,
    ) {
        val operation = HuaweiEqualizerCodec.customWriteOperation(route)
        val packet = operation?.let {
            HuaweiEqualizerCodec.buildCustomPacket(gains, presetName, operationValue = it)
        }
        if (!isTarget(context, device, route) || packet == null) {
            onComplete(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "custom-equalizer route=$route",
            onComplete = onComplete,
        )
    }

    fun setBuiltInPreset(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        presetId: Int,
        onComplete: (Boolean) -> Unit,
    ) {
        val packet = HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, presetId)
        if (!isTarget(context, device, route) || packet == null) {
            onComplete(false)
            return
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "built-in-equalizer route=$route preset=$presetId",
            onComplete = onComplete,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isTarget(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank) ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == route
    }
}
