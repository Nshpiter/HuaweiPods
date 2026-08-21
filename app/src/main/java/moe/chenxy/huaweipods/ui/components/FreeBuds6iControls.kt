package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState

private data class FreeBuds6iPreset(val id: Int, val labelRes: Int)

private val freeBuds6iPresets = listOf(
    FreeBuds6iPreset(0x01, R.string.freebuds5_sound_effect_default),
    FreeBuds6iPreset(0x02, R.string.freebuds5_sound_effect_bass),
    FreeBuds6iPreset(0x03, R.string.freebuds5_sound_effect_treble),
    FreeBuds6iPreset(0x09, R.string.freebuds5_sound_effect_clear_voice),
)

/** FreeBuds 6i 官方音效与自定义均衡器，均使用真机状态中声明的预设 ID。 */
@Composable
fun FreeBuds6iControls(address: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var equalizerState by remember(address) { mutableStateOf<HuaweiEqualizerState?>(null) }

    fun refreshState() {
        val device = context.freeBuds6iBluetoothDevice(address) ?: return
        HuaweiEqualizerController.requestState(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        ) { state ->
            if (state != null) equalizerState = state
        }
    }

    DisposableEffect(address, context) {
        var disposed = false
        val device = context.freeBuds6iBluetoothDevice(address)
        if (device != null) {
            HuaweiEqualizerController.requestState(
                context = context,
                device = device,
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            ) { state ->
                if (!disposed && state != null) equalizerState = state
            }
        }
        onDispose { disposed = true }
    }

    val selectedPreset = freeBuds6iPresets.firstOrNull { it.id == equalizerState?.selectedId }
    val customSummary = if (equalizerState?.isCustom == true) {
        stringResource(R.string.freebuds7i_custom_equalizer)
    } else {
        null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds5_sound_effect),
            selected = selectedPreset,
            values = freeBuds6iPresets,
            label = { stringResource(it.labelRes) },
            summaryOverride = customSummary,
            onSelected = { preset, complete ->
                val device = context.freeBuds6iBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiEqualizerController.setBuiltInPreset(
                        context = context,
                        device = device,
                        route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                        presetId = preset.id,
                    ) { success ->
                        if (success) {
                            equalizerState = equalizerState?.copy(
                                selectedId = preset.id,
                                selectedName = null,
                                selectedGains = null,
                            )
                        }
                        complete(success)
                    }
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            readback = equalizerState,
            requestOnMount = false,
            onCustomApplied = { refreshState() },
        )
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeBuds6iBluetoothDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)
