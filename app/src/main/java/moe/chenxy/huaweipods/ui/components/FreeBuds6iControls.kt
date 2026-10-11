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
import top.yukonga.miuix.kmp.basic.TextButton

private data class FreeBuds6iPreset(val id: Int, val labelRes: Int)

private val freeBuds6iPresets = listOf(
    FreeBuds6iPreset(0x01, R.string.freebuds5_sound_effect_default),
    FreeBuds6iPreset(0x02, R.string.freebuds5_sound_effect_bass),
    FreeBuds6iPreset(0x03, R.string.freebuds5_sound_effect_treble),
    FreeBuds6iPreset(0x09, R.string.freebuds5_sound_effect_clear_voice),
)

/** FreeBuds 6i / SE 4 ANC 官方音效与自定义均衡器。 */
@Composable
fun FreeBuds6iControls(
    address: String,
    route: HuaweiDeviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var equalizerState by remember(address, route) { mutableStateOf<HuaweiEqualizerState?>(null) }
    var reading by remember(address, route) { mutableStateOf(true) }
    var refreshGeneration by remember(address, route) { mutableStateOf(0) }

    fun refreshState() {
        refreshGeneration++
    }

    DisposableEffect(address, route, context, refreshGeneration) {
        var disposed = false
        reading = true
        val device = context.freeBuds6iBluetoothDevice(address)
        if (device != null) {
            HuaweiEqualizerController.requestState(
                context = context,
                device = device,
                route = route,
            ) { state ->
                if (!disposed) {
                    equalizerState = state?.takeIf { it.supported }
                    reading = false
                }
            }
        } else {
            reading = false
        }
        onDispose { disposed = true }
    }

    val selectedPreset = freeBuds6iPresets.firstOrNull { it.id == equalizerState?.selectedId }
    val customSummary = when {
        reading -> stringResource(R.string.freebuds7i_state_unknown)
        equalizerState == null -> stringResource(R.string.huawei_equalizer_read_failed)
        equalizerState?.isCustom == true -> equalizerState?.selectedName
            ?: stringResource(R.string.freebuds7i_custom_equalizer)
        selectedPreset == null -> stringResource(R.string.huawei_equalizer_other_preset)
        else -> null
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
                        route = route,
                        presetId = preset.id,
                    ) { success ->
                        if (success) {
                            refreshState()
                        }
                        complete(success)
                    }
                }
            },
        )
        if (!reading && equalizerState == null) {
            TextButton(text = stringResource(R.string.huawei_equalizer_retry), onClick = ::refreshState)
        }
        HuaweiEqualizerPreference(
            address = address,
            route = route,
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
