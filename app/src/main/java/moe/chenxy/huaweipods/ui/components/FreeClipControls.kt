package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.HuaweiFreeClipController
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class FreeClipPreset(val id: Int, val labelRes: Int)

private val freeClipPresets = listOf(
    FreeClipPreset(0x01, R.string.freebuds5_sound_effect_default),
    FreeClipPreset(0x0A, R.string.freebuds5_sound_effect_bass),
    FreeClipPreset(0x03, R.string.freebuds5_sound_effect_treble),
    FreeClipPreset(0x09, R.string.freebuds5_sound_effect_clear_voice),
)

/** FreeClip 1 controls backed by the guided Huawei Audio capture. */
@Composable
fun FreeClipControls(address: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var equalizerState by remember(address) { mutableStateOf<HuaweiEqualizerState?>(null) }
    var wearDetection by remember(address) { mutableStateOf<Boolean?>(null) }
    var adaptiveChannels by remember(address) { mutableStateOf<Boolean?>(null) }
    var dropReminder by remember(address) { mutableStateOf<Boolean?>(null) }

    DisposableEffect(address, context) {
        val device = context.freeClipBluetoothDevice(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        HuaweiEqualizerController.requestState(
            context = context,
            device = device,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP,
        ) { state ->
            if (!disposed && state != null) equalizerState = state
        }
        HuaweiFreeClipController.requestWearDetectionState(context, device) { state ->
            if (!disposed && state != null) wearDetection = state
        }
        HuaweiFreeClipController.requestAdaptiveChannelsState(context, device) { state ->
            if (!disposed && state != null) adaptiveChannels = state
        }
        HuaweiFreeClipController.requestDropReminderState(context, device) { state ->
            if (!disposed && state != null) dropReminder = state
        }
        onDispose { disposed = true }
    }

    val selectedPreset = freeClipPresets.firstOrNull { it.id == equalizerState?.selectedId }

    Column(modifier = Modifier.fillMaxWidth()) {
        FreeClipSectionTitle(R.string.freebuds5_smart_features)
        FreeClipFeatureToggle(
            titleRes = R.string.freebuds5_wear_detection,
            value = wearDetection,
            onChange = { enabled, complete ->
                val device = context.freeClipBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiFreeClipController.setWearDetection(context, device, enabled) { success ->
                        if (success) wearDetection = enabled
                        complete(success)
                    }
                }
            },
        )
        FreeClipFeatureToggle(
            titleRes = R.string.freeclip_adaptive_channels,
            summaryRes = R.string.freeclip_adaptive_channels_summary,
            value = adaptiveChannels,
            onChange = { enabled, complete ->
                val device = context.freeClipBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiFreeClipController.setAdaptiveChannels(context, device, enabled) { success ->
                        if (success) adaptiveChannels = enabled
                        complete(success)
                    }
                }
            },
        )
        FreeClipFeatureToggle(
            titleRes = R.string.freeclip2_drop_reminder,
            summaryRes = R.string.freeclip_drop_reminder_summary,
            value = dropReminder,
            onChange = { enabled, complete ->
                val device = context.freeClipBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiFreeClipController.setDropReminder(context, device, enabled) { success ->
                        if (success) dropReminder = enabled
                        complete(success)
                    }
                }
            },
        )

        FreeClipSectionTitle(R.string.freebuds5_sound_and_connection)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds5_sound_effect),
            selected = selectedPreset,
            values = freeClipPresets,
            label = { stringResource(it.labelRes) },
            onSelected = { preset, complete ->
                val device = context.freeClipBluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiEqualizerController.setBuiltInPreset(
                        context = context,
                        device = device,
                        route = HuaweiDeviceRoute.HUAWEI_FREECLIP,
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
        Card(modifier = Modifier.fillMaxWidth()) {
            LowLatencyControl(
                address = address,
                route = HuaweiDeviceRoute.HUAWEI_FREECLIP,
            )
        }
    }
}

@Composable
private fun FreeClipSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.headline1,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FreeClipFeatureToggle(
    titleRes: Int,
    value: Boolean?,
    summaryRes: Int? = null,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pending by remember { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            pending = true
            val target = value != true
            onChange(target) { success ->
                pending = false
                if (!success) {
                    Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Switch, onClick = toggle)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            if (summaryRes != null) {
                Text(
                    text = stringResource(summaryRes),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = when (value) {
                true -> ToggleableState.On
                false -> ToggleableState.Off
                null -> ToggleableState.Indeterminate
            },
            enabled = !pending,
            onClick = toggle,
        )
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeClipBluetoothDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)
