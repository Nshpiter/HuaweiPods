package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class FreeBuds4ePreset(val id: Int, val labelRes: Int)

private val freeBuds4ePresets = listOf(
    FreeBuds4ePreset(1, R.string.freebuds5_sound_effect_default),
    FreeBuds4ePreset(2, R.string.freebuds5_sound_effect_bass),
    FreeBuds4ePreset(3, R.string.freebuds5_sound_effect_treble),
)

/** FreeBuds 4E controls backed only by packets present in the 2026-08-15 guided capture. */
@Composable
fun FreeBuds4eControls(address: String) {
    val context = LocalContext.current
    var selectedId by remember(address) { mutableStateOf<Int?>(null) }
    var showDialog by remember(address) { mutableStateOf(false) }
    var pending by remember(address) { mutableStateOf(false) }

    DisposableEffect(address, context) {
        val device = context.bluetoothDevice(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        HuaweiEqualizerController.requestState(
            context,
            device,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        ) { state ->
            if (!disposed) selectedId = state?.selectedId?.takeIf { id ->
                freeBuds4ePresets.any { it.id == id }
            }
        }
        onDispose { disposed = true }
    }

    val selected = freeBuds4ePresets.firstOrNull { it.id == selectedId }
    val title = stringResource(R.string.freebuds5_sound_effect)
    val summary = selected?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.freebuds5_state_unknown)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Button) { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
        Text(
            summary,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }

    OverlayDialog(
        title = title,
        summary = summary,
        show = showDialog,
        onDismissRequest = { if (!pending) showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            freeBuds4ePresets.forEach { preset ->
                val isSelected = preset.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !pending, role = Role.RadioButton) {
                            if (isSelected) {
                                showDialog = false
                            } else {
                                val device = context.bluetoothDevice(address)
                                if (device == null) {
                                    Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                pending = true
                                HuaweiEqualizerController.setBuiltInPreset(
                                    context,
                                    device,
                                    HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
                                    preset.id,
                                ) { success ->
                                    pending = false
                                    if (success) selectedId = preset.id
                                    showDialog = false
                                    if (!success) {
                                        Toast.makeText(
                                            context,
                                            R.string.connect_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(preset.labelRes),
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.headline1,
                    )
                    Checkbox(
                        state = ToggleableState(isSelected),
                        enabled = !pending,
                        onClick = null,
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun Context.bluetoothDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)
