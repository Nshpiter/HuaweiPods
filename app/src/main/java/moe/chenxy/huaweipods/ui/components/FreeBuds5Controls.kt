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
import androidx.compose.runtime.rememberUpdatedState
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
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.pods.FreeBuds5SettingsState
import moe.chenxy.huaweipods.pods.FreeBuds5SoundEffect
import moe.chenxy.huaweipods.pods.HuaweiFreeBuds5Controller
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.mergeFreeBuds5SettingsState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared FreeBuds 5 / 5i settings backed by their guided captures. */
@Composable
fun FreeBuds5Controls(
    address: String,
    route: HuaweiDeviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val keyPrefix = remember(address, route) {
        val modelPrefix = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS5I) {
            "freebuds5i"
        } else {
            "freebuds5"
        }
        "${modelPrefix}_${address.uppercase().ifBlank { "unknown" }}_"
    }
    var settingsState by remember(address, route) {
        mutableStateOf(
            FreeBuds5SettingsState(
                wearDetection = prefs.nullableBoolean(keyPrefix + "wear_detection"),
                soundEffect = prefs.getString(keyPrefix + "sound_effect", null)
                    ?.let { name -> FreeBuds5SoundEffect.entries.firstOrNull { it.name == name } },
                highQualityAudio = prefs.nullableBoolean(keyPrefix + "high_quality_audio"),
            ),
        )
    }
    var lowLatency by remember(address, route) {
        mutableStateOf(
            LowLatencyPrefs.desiredOrNull(
                prefs,
                address,
                route,
            ),
        )
    }

    FreeBuds5ReadbackEffect(address, route) { update ->
        settingsState = mergeFreeBuds5SettingsState(settingsState, update)
        update.wearDetection?.let {
            prefs.edit().putBoolean(keyPrefix + "wear_detection", it).apply()
        }
        update.soundEffect?.let {
            prefs.edit().putString(keyPrefix + "sound_effect", it.name).apply()
        }
        update.highQualityAudio?.let {
            prefs.edit().putBoolean(keyPrefix + "high_quality_audio", it).apply()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FreeBuds5SectionTitle(R.string.freebuds5_smart_features)
        FreeBuds5FeatureToggle(
            titleRes = R.string.freebuds5_wear_detection,
            value = settingsState.wearDetection,
            onChange = { enabled, complete ->
                context.setFreeBuds5WearDetection(address, route, enabled) { success ->
                    if (success) {
                        settingsState = mergeFreeBuds5SettingsState(
                            settingsState,
                            FreeBuds5SettingsState(wearDetection = enabled),
                        )
                        prefs.edit().putBoolean(keyPrefix + "wear_detection", enabled).apply()
                    }
                    complete(success)
                }
            },
        )

        FreeBuds5SectionTitle(R.string.freebuds5_sound_and_connection)
        FreeBuds5SoundEffectPreference(
            selected = settingsState.soundEffect,
            onSelected = { effect, complete ->
                context.setFreeBuds5SoundEffect(address, route, effect) { success ->
                    if (success) {
                        settingsState = mergeFreeBuds5SettingsState(
                            settingsState,
                            FreeBuds5SettingsState(soundEffect = effect),
                        )
                        prefs.edit().putString(keyPrefix + "sound_effect", effect.name).apply()
                    }
                    complete(success)
                }
            },
        )
        FreeBuds5FeatureToggle(
            titleRes = R.string.freebuds5_high_quality_audio,
            value = settingsState.highQualityAudio,
            onChange = { enabled, complete ->
                context.setFreeBuds5HighQualityAudio(address, route, enabled) { success ->
                    if (success) {
                        settingsState = mergeFreeBuds5SettingsState(
                            settingsState,
                            FreeBuds5SettingsState(highQualityAudio = enabled),
                        )
                        prefs.edit().putBoolean(keyPrefix + "high_quality_audio", enabled).apply()
                    }
                    complete(success)
                }
            },
        )
        FreeBuds5FeatureToggle(
            titleRes = R.string.freebuds5_low_latency,
            value = lowLatency,
            onChange = { enabled, complete ->
                context.setFreeBuds5LowLatency(address, route, enabled) { success ->
                    if (success) {
                        val stored = LowLatencyPrefs.setDesired(
                            prefs = prefs,
                            service = HuaweiPodsApp.xposedService,
                            address = address,
                            route = route,
                            enabled = enabled,
                        )
                        if (stored) lowLatency = enabled
                        complete(stored)
                    } else {
                        complete(false)
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.freebuds5_state_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FreeBuds5SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.headline1,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FreeBuds5FeatureToggle(
    titleRes: Int,
    value: Boolean?,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            val target = value != true
            pending = true
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
        Text(
            text = stringResource(titleRes),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun FreeBuds5SoundEffectPreference(
    selected: FreeBuds5SoundEffect?,
    onSelected: (FreeBuds5SoundEffect, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    val title = stringResource(R.string.freebuds5_sound_effect)
    val summary = selected?.let { stringResource(it.labelRes()) }
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
        onDismissRequest = { showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            FreeBuds5SoundEffect.entries.forEach { effect ->
                val isSelected = effect == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !pending, role = Role.RadioButton) {
                            if (isSelected) {
                                showDialog = false
                            } else {
                                pending = true
                                onSelected(effect) { success ->
                                    pending = false
                                    showDialog = false
                                    if (!success) {
                                        Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(effect.labelRes()),
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.headline1,
                    )
                    Checkbox(state = ToggleableState(isSelected), enabled = !pending, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun FreeBuds5ReadbackEffect(
    address: String,
    route: HuaweiDeviceRoute,
    onReadback: (FreeBuds5SettingsState) -> Unit,
) {
    val context = LocalContext.current
    val currentOnReadback by rememberUpdatedState(onReadback)
    DisposableEffect(address, route, context) {
        val device = context.freeBuds5Device(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        fun publish(update: FreeBuds5SettingsState) {
            if (!disposed) currentOnReadback(update)
        }
        HuaweiFreeBuds5Controller.requestWearDetectionState(context, device, route) { value ->
            value?.let { publish(FreeBuds5SettingsState(wearDetection = it)) }
        }
        HuaweiFreeBuds5Controller.requestSoundEffectState(context, device, route) { value ->
            value?.let { publish(FreeBuds5SettingsState(soundEffect = it)) }
        }
        HuaweiFreeBuds5Controller.requestHighQualityAudioState(context, device, route) { value ->
            value?.let { publish(FreeBuds5SettingsState(highQualityAudio = it)) }
        }
        onDispose { disposed = true }
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeBuds5Device(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)

private fun Context.setFreeBuds5WearDetection(
    address: String,
    route: HuaweiDeviceRoute,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds5Device(address) ?: return complete(false)
    HuaweiFreeBuds5Controller.setWearDetection(this, device, route, enabled, complete)
}

private fun Context.setFreeBuds5SoundEffect(
    address: String,
    route: HuaweiDeviceRoute,
    effect: FreeBuds5SoundEffect,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds5Device(address) ?: return complete(false)
    HuaweiFreeBuds5Controller.setSoundEffect(this, device, route, effect, complete)
}

private fun Context.setFreeBuds5HighQualityAudio(
    address: String,
    route: HuaweiDeviceRoute,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds5Device(address) ?: return complete(false)
    HuaweiFreeBuds5Controller.setHighQualityAudio(this, device, route, enabled, complete)
}

private fun Context.setFreeBuds5LowLatency(
    address: String,
    route: HuaweiDeviceRoute,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds5Device(address) ?: return complete(false)
    HuaweiFreeBuds5Controller.setLowLatency(this, device, route, enabled, complete)
}

private fun FreeBuds5SoundEffect.labelRes(): Int = when (this) {
    FreeBuds5SoundEffect.DEFAULT -> R.string.freebuds5_sound_effect_default
    FreeBuds5SoundEffect.BASS_ENHANCE -> R.string.freebuds5_sound_effect_bass
    FreeBuds5SoundEffect.TREBLE_ENHANCE -> R.string.freebuds5_sound_effect_treble
    FreeBuds5SoundEffect.CLEAR_VOICE -> R.string.freebuds5_sound_effect_clear_voice
}

private fun android.content.SharedPreferences.nullableBoolean(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null
