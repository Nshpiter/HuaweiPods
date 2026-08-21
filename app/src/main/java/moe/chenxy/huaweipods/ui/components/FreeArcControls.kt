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

private data class FreeArcPreset(val id: Int, val labelRes: Int)

private val freeArcPresets = listOf(
    FreeArcPreset(0x01, R.string.freebuds5_sound_effect_default),
    FreeArcPreset(0x0A, R.string.freeclip2_sound_effect_sport),
    FreeArcPreset(0x02, R.string.freebuds5_sound_effect_bass),
    FreeArcPreset(0x03, R.string.freebuds5_sound_effect_treble),
    FreeArcPreset(0x09, R.string.freebuds5_sound_effect_clear_voice),
)

/** FreeArc controls backed only by packets present in the 2026-08-16 guided capture. */
@Composable
fun FreeArcControls(address: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var equalizerState by remember(address) { mutableStateOf<HuaweiEqualizerState?>(null) }

    DisposableEffect(address, context) {
        val device = context.freeArcBluetoothDevice(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        HuaweiEqualizerController.requestState(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREEARC,
        ) { state ->
            if (!disposed) equalizerState = state
        }
        onDispose { disposed = true }
    }

    val selectedPreset = freeArcPresets.firstOrNull { it.id == equalizerState?.selectedId }
    val customSummary = if (equalizerState?.isCustom == true) {
        stringResource(R.string.freebuds7i_custom_equalizer)
    } else {
        null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds5_sound_effect),
            selected = selectedPreset,
            values = freeArcPresets,
            label = { stringResource(it.labelRes) },
            summaryOverride = customSummary,
            onSelected = { preset, complete ->
                val device = context.freeArcBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiEqualizerController.setBuiltInPreset(
                        context = context,
                        device = device,
                        route = HuaweiDeviceRoute.HUAWEI_FREEARC,
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
            route = HuaweiDeviceRoute.HUAWEI_FREEARC,
            readback = equalizerState,
            requestOnMount = false,
            onCustomApplied = { gains ->
                equalizerState = equalizerState?.copy(
                    selectedId = 0x64,
                    selectedName = "HuaweiPods EQ",
                    selectedGains = gains,
                ) ?: HuaweiEqualizerState(
                    supported = true,
                    selectedId = 0x64,
                    builtInIds = freeArcPresets.map(FreeArcPreset::id),
                    bandCount = gains.size,
                    selectedName = "HuaweiPods EQ",
                    selectedGains = gains,
                    customPresets = emptyList(),
                )
            },
        )
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeArcBluetoothDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)
