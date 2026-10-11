package moe.chenxy.huaweipods.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.pods.FreeBuds6Setting
import moe.chenxy.huaweipods.pods.FreeBuds6SoundEffect
import moe.chenxy.huaweipods.pods.FreeBuds6State
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiFreeBuds6Controller

/** Display confirmed earphone state; unknown or failed reads never become a fake off state. */
@Composable
fun FreeBuds6Controls(address: String) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(ConfigManager.PREFS_NAME, 0) }
    val device = remember(address) {
        if (BluetoothAdapter.checkBluetoothAddress(address))
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        else null
    }
    var state by remember(address) { mutableStateOf<FreeBuds6State?>(null) }
    var alive by remember(address) { mutableStateOf(true) }
    fun refresh() {
        device?.let { target ->
            HuaweiFreeBuds6Controller.requestState(context, target) { if (alive) state = it }
        }
    }
    DisposableEffect(device) {
        alive = true
        refresh()
        onDispose { alive = false }
    }
    Column {
        FreeBuds7iSectionTitle(R.string.freebuds7i_sound_and_connection)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds7i_sound_effect),
            selected = FreeBuds6SoundEffect.entries.firstOrNull { it.protocolValue == state?.equalizer?.selectedId },
            values = FreeBuds6SoundEffect.entries.filter {
                it == FreeBuds6SoundEffect.CLASSICAL || it.protocolValue in state?.equalizer?.builtInIds.orEmpty()
            },
            label = { stringResource(it.labelRes()) },
            summaryOverride = if (state?.equalizer != null &&
                FreeBuds6SoundEffect.entries.none { it.protocolValue == state?.equalizer?.selectedId })
                stringResource(R.string.freebuds6_other_effect) else null,
            onSelected = { effect, complete ->
                if (device == null) complete(false)
                else HuaweiFreeBuds6Controller.setEffect(context, device, effect) { success ->
                    if (alive) {
                        if (success) refresh()
                        complete(success)
                    }
                }
            },
        )
        FreeBuds7iSectionTitle(R.string.freebuds7i_smart_features)
        FreeBuds6Setting.entries.forEach { setting ->
            FreeBuds7iFeatureToggle(setting.labelRes(), state?.settings?.get(setting)) { enabled, complete ->
                if (device == null) complete(false)
                else HuaweiFreeBuds6Controller.set(context, device, setting, enabled) { success ->
                    if (alive) {
                        var applied = success
                        if (success) {
                            val current = state ?: FreeBuds6State(emptyMap(), null)
                            state = current.copy(settings = current.settings + (setting to enabled))
                            if (setting == FreeBuds6Setting.LOW_LATENCY) {
                                applied = LowLatencyPrefs.setDesired(
                                    prefs, HuaweiPodsApp.xposedService, address,
                                    HuaweiDeviceRoute.HUAWEI_FREEBUDS6, enabled,
                                )
                            }
                        }
                        complete(applied)
                    }
                }
            }
        }
    }
}

private fun FreeBuds6Setting.labelRes(): Int = when (this) {
    FreeBuds6Setting.ADAPTIVE_VOLUME -> R.string.freebuds_pro5_adaptive_volume
    FreeBuds6Setting.DROP_REMINDER -> R.string.freeclip2_drop_reminder
    FreeBuds6Setting.WEAR_DETECTION -> R.string.huawei_gesture_wear_detection
    FreeBuds6Setting.CASE_SOUND -> R.string.freeclip2_case_prompt_sound
    FreeBuds6Setting.LOW_LATENCY -> R.string.low_latency_mode
}

private fun FreeBuds6SoundEffect.labelRes(): Int = when (this) {
    FreeBuds6SoundEffect.DEFAULT -> R.string.freebuds7i_sound_effect_default
    FreeBuds6SoundEffect.BASS -> R.string.freebuds7i_sound_effect_bass
    FreeBuds6SoundEffect.TREBLE -> R.string.freebuds7i_sound_effect_treble
    FreeBuds6SoundEffect.CLEAR_VOICE -> R.string.freebuds7i_sound_effect_clear_voice
    FreeBuds6SoundEffect.CLASSICAL -> R.string.freebuds_pro5_effect_yuezhang_classical
}
