package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context

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
        // 不再强依赖 DeviceRoutePrefs 精确匹配（FreeLace Pro 2 等新模型未必写入 prefs），
        // 与 ANC/电量读取同标准：route 已启用即可放行，真正的发送校验由
        // HuaweiL2capAncController.enqueueWrite 的 isHuaweiDeviceRouteEnabled 兜底。
        return isHuaweiDeviceRouteEnabled(route)
    }
}
