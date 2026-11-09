package moe.chenxy.huaweipods.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.FreeBudsPro2Setting
import moe.chenxy.huaweipods.pods.FreeBudsPro2SoundEffect
import moe.chenxy.huaweipods.pods.FreeBudsPro2State
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiFreeBudsPro2Controller
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Read on entry/resume; only matching earphone readback confirms a setting change. */
@Composable
fun FreeBudsPro2Controls(address: String) {
    val context = LocalContext.current
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    val device = remember(address, context) {
        if (BluetoothAdapter.checkBluetoothAddress(address))
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        else null
    }
    var state by remember(address) { mutableStateOf<FreeBudsPro2State?>(null) }
    var alive by remember(address) { mutableStateOf(false) }
    var revision by remember(address) { mutableStateOf(0) }
    var reading by remember(address) { mutableStateOf(false) }
    var pending by remember(address) { mutableStateOf(false) }
    var showEffects by remember(address) { mutableStateOf(false) }

    fun refresh() {
        val target = device ?: return
        if (!alive || pending) return
        val requestRevision = ++revision
        reading = true
        HuaweiFreeBudsPro2Controller.requestState(context, target) {
            if (alive && requestRevision == revision) {
                state = it
                reading = false
            }
        }
    }

    fun change(write: ((Boolean) -> Unit) -> Unit) {
        if (!alive || pending || reading) return
        val requestRevision = ++revision
        pending = true
        write { success ->
            if (alive && requestRevision == revision) {
                pending = false
                showEffects = false
                if (!success) Toast.makeText(context, R.string.freeclip2_write_unconfirmed, Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    DisposableEffect(device, lifecycle) {
        alive = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !reading) refresh()
        }
        lifecycle?.addObserver(observer)
        if (!reading) refresh()
        onDispose {
            alive = false
            revision++
            reading = false
            pending = false
            lifecycle?.removeObserver(observer)
        }
    }

    val equalizer = state?.equalizer
    val selected = FreeBudsPro2SoundEffect.entries.firstOrNull { it.protocolValue == equalizer?.selectedId }
    val available = device != null && !pending && !reading
    Column {
        ArrowPreference(
            title = stringResource(R.string.freebuds5_sound_effect),
            summary = selected?.let { stringResource(it.labelRes()) }
                ?: equalizer?.selectedName
                ?: stringResource(R.string.freeclip2_settings_unknown),
            enabled = available && equalizer?.supported == true,
            holdDownState = showEffects,
            onClick = { showEffects = true },
        )
        OverlayDialog(
            title = stringResource(R.string.freebuds5_sound_effect),
            show = showEffects,
            onDismissRequest = { showEffects = false },
        ) {
            Column {
                FreeBudsPro2SoundEffect.entries.filter {
                    it.protocolValue >= 0xC8 || it.protocolValue in equalizer?.builtInIds.orEmpty()
                }.forEach { effect ->
                    RadioButtonPreference(
                        title = stringResource(effect.labelRes()),
                        selected = effect == selected,
                        enabled = available,
                        onClick = {
                            device?.let { target ->
                                change { complete -> HuaweiFreeBudsPro2Controller.setEffect(context, target, effect, complete) }
                            }
                        },
                    )
                }
            }
        }
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO2,
            readback = equalizer,
            requestOnMount = false,
            editable = available && equalizer?.supported == true,
            onCustomApplied = { refresh() },
        )
        FreeBudsPro2Setting.entries.forEach { setting ->
            val value = state?.settings?.get(setting)
            SwitchPreference(
                title = stringResource(setting.labelRes()),
                summary = if (value == null) stringResource(R.string.freeclip2_settings_unknown) else null,
                checked = value == true,
                enabled = available && value != null,
                onCheckedChange = { enabled ->
                    device?.let { target ->
                        change { complete -> HuaweiFreeBudsPro2Controller.set(context, target, setting, enabled, complete) }
                    }
                },
            )
        }
        ArrowPreference(
            title = stringResource(R.string.freeclip2_refresh_settings),
            enabled = available,
            onClick = { refresh() },
        )
    }
}

private fun FreeBudsPro2SoundEffect.labelRes(): Int = when (this) {
    FreeBudsPro2SoundEffect.DEFAULT -> R.string.freebuds5_sound_effect_default
    FreeBudsPro2SoundEffect.BASS -> R.string.freebuds5_sound_effect_bass
    FreeBudsPro2SoundEffect.TREBLE -> R.string.freebuds5_sound_effect_treble
    FreeBudsPro2SoundEffect.CLEAR_VOICE -> R.string.freebuds5_sound_effect_clear_voice
    FreeBudsPro2SoundEffect.CLASSICAL -> R.string.freebuds_pro2_classical
    FreeBudsPro2SoundEffect.HIFI_LIVE -> R.string.freebuds_pro2_hifi_live
}

private fun FreeBudsPro2Setting.labelRes(): Int = when (this) {
    FreeBudsPro2Setting.DUAL_DEVICE -> R.string.freeclip2_dual_device
    FreeBudsPro2Setting.HIGH_QUALITY -> R.string.freebuds5_high_quality_audio
    FreeBudsPro2Setting.PINCH_PLAY -> R.string.huawei_gesture_pro3_media_play_pause
    FreeBudsPro2Setting.PINCH_NEXT -> R.string.huawei_gesture_pro3_media_next
    FreeBudsPro2Setting.PINCH_PREVIOUS -> R.string.huawei_gesture_pro3_media_previous
    FreeBudsPro2Setting.SWIPE_VOLUME -> R.string.huawei_gesture_pro3_swipe_volume
}
