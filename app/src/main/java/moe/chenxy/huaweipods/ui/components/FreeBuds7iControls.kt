package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import moe.chenxy.huaweipods.pods.FreeBuds5SoundEffect
import moe.chenxy.huaweipods.pods.FreeBuds7iBooleanFeature
import moe.chenxy.huaweipods.pods.FreeBuds7iDualDevice
import moe.chenxy.huaweipods.pods.FreeBuds7iSettingsState
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.HuaweiFreeBuds7iController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerPreset
import moe.chenxy.huaweipods.pods.HuaweiEqualizerPresetPolicy
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.mergeFreeBuds7iSettingsState
import moe.chenxy.huaweipods.ui.dialogs.responsiveOverlayDialogModifier
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** FreeBuds 7i controls backed exclusively by the verified 2026-08-08 capture. */
@Composable
fun FreeBuds7iControls(address: String) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val prefix = remember(address) { freeBuds7iPreferencePrefix(address) }
    var state by remember(address) {
        mutableStateOf(
            FreeBuds7iSettingsState(
                wearDetection = prefs.nullableBoolean(prefix + "wear_detection"),
                headMotionControl = prefs.nullableBoolean(prefix + "head_motion"),
                spatialAudioMode = prefs.getString(prefix + "spatial", null)
                    ?.let(FreeClip2SpatialAudioMode::fromExtraValue),
                soundEffect = prefs.getString(prefix + "sound_effect", null)
                    ?.let { saved -> FreeBuds5SoundEffect.entries.firstOrNull { it.name == saved } },
                highQualityAudio = prefs.nullableBoolean(prefix + "high_quality"),
            ),
        )
    }

    FreeBuds7iReadbackEffect(address) { update ->
        state = mergeFreeBuds7iSettingsState(state, update)
        persistFreeBuds7iState(prefs, prefix, update)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FreeBuds7iSectionTitle(R.string.freebuds7i_smart_features)
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds7i_wear_detection,
            value = state.wearDetection,
            onChange = { enabled, complete ->
                context.setFreeBuds7iBoolean(address, FreeBuds7iBooleanFeature.WEAR_DETECTION, enabled) { success ->
                    if (success) {
                        state = mergeFreeBuds7iSettingsState(
                            state,
                            FreeBuds7iSettingsState(wearDetection = enabled),
                        )
                        prefs.edit().putBoolean(prefix + "wear_detection", enabled).apply()
                    }
                    complete(success)
                }
            },
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds7i_head_motion,
            value = state.headMotionControl,
            onChange = { enabled, complete ->
                context.setFreeBuds7iBoolean(address, FreeBuds7iBooleanFeature.HEAD_MOTION_CONTROL, enabled) { success ->
                    if (success) {
                        state = mergeFreeBuds7iSettingsState(
                            state,
                            FreeBuds7iSettingsState(headMotionControl = enabled),
                        )
                        prefs.edit().putBoolean(prefix + "head_motion", enabled).apply()
                    }
                    complete(success)
                }
            },
        )

        FreeBuds7iSectionTitle(R.string.freebuds7i_spatial_audio)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds7i_spatial_audio),
            selected = state.spatialAudioMode,
            values = FreeClip2SpatialAudioMode.entries,
            label = { stringResource(it.freeBuds7iLabelRes()) },
            onSelected = { mode, complete ->
                context.setFreeBuds7iSpatialMode(address, mode) { success ->
                    if (success) {
                        state = mergeFreeBuds7iSettingsState(
                            state,
                            FreeBuds7iSettingsState(spatialAudioMode = mode),
                        )
                        prefs.edit().putString(prefix + "spatial", mode.extraValue).apply()
                    }
                    complete(success)
                }
            },
        )

        FreeBuds7iSectionTitle(R.string.freebuds7i_sound_and_connection)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds7i_sound_effect),
            selected = state.soundEffect,
            values = FreeBuds5SoundEffect.entries,
            label = { stringResource(it.freeBuds7iLabelRes()) },
            onSelected = { effect, complete ->
                context.setFreeBuds7iSoundEffect(address, effect) { success ->
                    if (success) {
                        state = mergeFreeBuds7iSettingsState(
                            state,
                            FreeBuds7iSettingsState(soundEffect = effect),
                        )
                        prefs.edit().putString(prefix + "sound_effect", effect.name).apply()
                    }
                    complete(success)
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            readback = state.equalizer,
            requestOnMount = false,
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds7i_high_quality_audio,
            value = state.highQualityAudio,
            onChange = { enabled, complete ->
                context.setFreeBuds7iBoolean(address, FreeBuds7iBooleanFeature.HIGH_QUALITY_AUDIO, enabled) { success ->
                    if (success) {
                        state = mergeFreeBuds7iSettingsState(
                            state,
                            FreeBuds7iSettingsState(highQualityAudio = enabled),
                        )
                        prefs.edit().putBoolean(prefix + "high_quality", enabled).apply()
                    }
                    complete(success)
                }
            },
        )
        LowLatencyControl(address, moe.chenxy.huaweipods.pods.HuaweiDeviceRoute.HUAWEI_FREEBUDS7I)
        FreeBuds7iDualDevicePreference(address)

        Text(
            text = stringResource(R.string.freebuds7i_state_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FreeBuds7iSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.headline1,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FreeBuds7iFeatureToggle(
    @StringRes titleRes: Int,
    value: Boolean?,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var pending by remember(titleRes) { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            pending = true
            onChange(value != true) { success ->
                pending = false
                if (!success) Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
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
internal fun <T> FreeBuds7iChoicePreference(
    title: String,
    selected: T?,
    values: List<T>,
    label: @Composable (T) -> String,
    summaryOverride: String? = null,
    onSelected: (T, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember(title) { mutableStateOf(false) }
    var pending by remember(title) { mutableStateOf(false) }
    val summary = summaryOverride
        ?: selected?.let { label(it) }
        ?: stringResource(R.string.freebuds7i_state_unknown)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Button) { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
        Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
    }
    OverlayDialog(
        title = title,
        summary = summary,
        show = showDialog,
        onDismissRequest = { if (!pending) showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            values.forEach { value ->
                val selectedValue = value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !pending, role = Role.RadioButton) {
                            if (selectedValue) {
                                showDialog = false
                            } else {
                                pending = true
                                onSelected(value) { success ->
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
                    Text(label(value), modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.headline1)
                    Checkbox(state = ToggleableState(selectedValue), enabled = !pending, onClick = null)
                }
            }
        }
    }
}

@Composable
internal fun HuaweiEqualizerPreference(
    address: String,
    route: HuaweiDeviceRoute,
    readback: HuaweiEqualizerState? = null,
    requestOnMount: Boolean = true,
    editable: Boolean = HuaweiEqualizerCodec.customWriteOperation(route) != null ||
        route == HuaweiDeviceRoute.HUAWEI_FREECLIP2,
    onCustomApplied: ((List<Int>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val prefix = remember(address) { "huawei_eq_${address.uppercase().ifBlank { "unknown" }}_" }
    val stored = remember(address) { readEqualizerGains(prefs, prefix) }
    var gains by remember(address) { mutableStateOf(stored) }
    var editing by remember(address) { mutableStateOf(stored) }
    var currentState by remember(address) { mutableStateOf(readback) }
    var showDialog by remember(address) { mutableStateOf(false) }
    var pending by remember(address) { mutableStateOf(false) }
    var editingPresetId by remember(address) {
        mutableStateOf(HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID)
    }
    var editingPresetName by remember(address) { mutableStateOf("") }
    val defaultPresetNames = (HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID..
        HuaweiEqualizerPresetPolicy.LAST_CUSTOM_ID).associateWith { presetId ->
        stringResource(
            R.string.freeclip2_custom_effect_default_name,
            presetId - HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID + 1,
        )
    }

    fun openPresetEditor(preset: HuaweiEqualizerPreset?) {
        val customPresets = currentState?.customPresets.orEmpty()
        val target = preset ?: currentState?.selectedId
            ?.let { selectedId -> customPresets.singleOrNull { it.id == selectedId } }
        val targetId = target?.id
            ?: HuaweiEqualizerPresetPolicy.nextAvailableId(customPresets)
            ?: customPresets.firstOrNull()?.id
            ?: HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID
        val existing = target ?: customPresets.singleOrNull { it.id == targetId }
        editingPresetId = targetId
        editingPresetName = existing?.name?.takeIf(String::isNotBlank)
            ?: defaultPresetNames.getValue(targetId)
        editing = existing?.gains
            ?.takeIf { it.size == HuaweiEqualizerCodec.BAND_COUNT }
            ?: gains
        showDialog = true
    }

    LaunchedEffect(readback) {
        readback?.let { state ->
            currentState = state
            state.selectedGains?.takeIf { it.size == HuaweiEqualizerCodec.BAND_COUNT }?.let {
                gains = it
                if (!showDialog) editing = it
                prefs.edit().putString(prefix + "equalizer", it.joinToString(",")).apply()
            }
            if (!showDialog) {
                state.customPresets.singleOrNull { it.id == state.selectedId }?.let { selected ->
                    editingPresetId = selected.id
                    editingPresetName = selected.name
                }
            }
        }
    }
    DisposableEffect(address, route, requestOnMount) {
        if (!requestOnMount) return@DisposableEffect onDispose { }
        val device = context.freeBuds7iDevice(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        HuaweiEqualizerController.requestState(context, device, route) { state ->
            if (!disposed && state != null) {
                currentState = state
                state.selectedGains?.takeIf { it.size == HuaweiEqualizerCodec.BAND_COUNT }?.let {
                    gains = it
                    if (!showDialog) editing = it
                    prefs.edit().putString(prefix + "equalizer", it.joinToString(",")).apply()
                }
            }
        }
        onDispose { disposed = true }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = editable && !pending, role = Role.Button) {
                openPresetEditor(null)
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(
                if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                    R.string.freeclip2_custom_effect_title
                } else {
                    R.string.huawei_equalizer_title
                },
            ),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Text(
            currentState?.selectedName?.takeIf(String::isNotBlank)
                ?: stringResource(
                    if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                        R.string.freeclip2_custom_effect_summary
                    } else {
                        R.string.huawei_equalizer_summary
                    },
                ),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
    OverlayDialog(
        title = stringResource(
            if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                R.string.freeclip2_custom_effect_title
            } else {
                R.string.huawei_equalizer_title
            },
        ),
        summary = currentState?.selectedName?.takeIf(String::isNotBlank)
            ?: stringResource(
                if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                    R.string.freeclip2_custom_effect_summary
                } else {
                    R.string.huawei_equalizer_summary
                },
            ),
        show = showDialog,
        onDismissRequest = { if (!pending) showDialog = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(responsiveOverlayDialogModifier())
                .padding(bottom = 8.dp),
        ) {
            if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                val customPresets = currentState?.customPresets.orEmpty()
                if (customPresets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.freeclip2_custom_effect_existing),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    customPresets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !pending, role = Role.RadioButton) {
                                    openPresetEditor(preset)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name.ifBlank {
                                        stringResource(
                                            R.string.freeclip2_custom_effect_default_name,
                                            preset.id - HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID + 1,
                                        )
                                    },
                                    style = MiuixTheme.textStyles.headline1,
                                )
                                Text(
                                    stringResource(R.string.freeclip2_custom_effect_edit_hint),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2,
                                )
                            }
                            Checkbox(
                                state = ToggleableState(preset.id == editingPresetId),
                                enabled = !pending,
                                onClick = null,
                            )
                        }
                    }
                }
                HuaweiEqualizerPresetPolicy.nextAvailableId(customPresets)?.let { nextId ->
                    TextButton(
                        text = stringResource(R.string.freeclip2_custom_effect_add),
                        enabled = !pending,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = {
                            editingPresetId = nextId
                            editingPresetName = defaultPresetNames.getValue(nextId)
                            editing = gains
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.freeclip2_custom_effect_name),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                TextField(
                    value = editingPresetName,
                    onValueChange = { editingPresetName = it },
                    enabled = !pending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.freeclip2_custom_effect_bridge_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            editing.forEachIndexed { index, gain ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.freebuds7i_eq_band, index + 1),
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                    )
                    TextButton(
                        text = "−",
                        enabled = !pending && gain > -60,
                        onClick = { editing = editing.withGain(index, gain - 10) },
                    )
                    Text(
                        text = stringResource(R.string.freebuds7i_eq_gain, gain / 10f),
                        modifier = Modifier.width(72.dp),
                        style = MiuixTheme.textStyles.body1,
                    )
                    TextButton(
                        text = "+",
                        enabled = !pending && gain < 60,
                        onClick = { editing = editing.withGain(index, gain + 10) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.freebuds7i_eq_reset),
                    enabled = !pending,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = { editing = List(10) { 0 } },
                )
                TextButton(
                    text = stringResource(R.string.freebuds7i_eq_apply),
                    enabled = !pending && (
                        route != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
                            HuaweiEqualizerPresetPolicy.normalizeName(editingPresetName) != null
                        ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {
                        pending = true
                        val onComplete: (Boolean) -> Unit = { success ->
                            pending = false
                            if (success) {
                                gains = editing
                                prefs.edit().putString(prefix + "equalizer", editing.joinToString(",")).apply()
                                onCustomApplied?.invoke(editing)
                                showDialog = false
                            } else {
                                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                            val presetName = HuaweiEqualizerPresetPolicy.normalizeName(
                                editingPresetName,
                            ) ?: return@TextButton
                            val selectedId = editingPresetId
                            SmartAudioEqualizerClient.setCustom(
                                context = context,
                                address = address,
                                presetId = selectedId,
                                name = presetName,
                                gains = editing,
                                complete = { success ->
                                    if (success) {
                                        val preset = HuaweiEqualizerPreset(
                                            id = selectedId,
                                            name = presetName,
                                            gains = editing,
                                        )
                                        val previous = currentState
                                        currentState = HuaweiEqualizerState(
                                            supported = previous?.supported ?: true,
                                            selectedId = selectedId,
                                            builtInIds = previous?.builtInIds.orEmpty(),
                                            bandCount = HuaweiEqualizerCodec.BAND_COUNT,
                                            selectedName = presetName,
                                            selectedGains = editing,
                                            customPresets = HuaweiEqualizerPresetPolicy.upsert(
                                                previous?.customPresets.orEmpty(),
                                                preset,
                                            ),
                                        )
                                    }
                                    onComplete(success)
                                },
                            )
                        } else {
                            context.setHuaweiCustomEqualizer(address, route, editing, onComplete)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FreeBuds7iDualDevicePreference(address: String) {
    val context = LocalContext.current
    var showDialog by remember(address) { mutableStateOf(false) }
    var loading by remember(address) { mutableStateOf(false) }
    var devices by remember(address) { mutableStateOf(emptyList<FreeBuds7iDualDevice>()) }
    var removeTarget by remember(address) { mutableStateOf<FreeBuds7iDualDevice?>(null) }

    fun refresh() {
        if (loading) return
        val device = context.freeBuds7iDevice(address) ?: return
        loading = true
        HuaweiFreeBuds7iController.requestDualDevices(context, device) { result ->
            devices = result
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) {
                showDialog = true
                refresh()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.freebuds7i_dual_device),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Text(
            stringResource(R.string.freebuds7i_dual_device_summary),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
    OverlayDialog(
        title = stringResource(R.string.freebuds7i_dual_device),
        summary = stringResource(R.string.freebuds7i_dual_device_summary),
        show = showDialog,
        onDismissRequest = { if (!loading) showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (!loading && devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.freebuds7i_dual_device_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(12.dp),
                )
            }
            devices.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MiuixTheme.textStyles.headline1)
                        Text(
                            text = stringResource(
                                if (item.connected) {
                                    R.string.freebuds7i_dual_device_connected
                                } else {
                                    R.string.freebuds7i_dual_device_saved
                                },
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                    TextButton(
                        text = stringResource(R.string.freebuds7i_dual_device_remove),
                        enabled = !loading,
                        onClick = { removeTarget = item },
                    )
                }
            }
            TextButton(
                text = stringResource(R.string.freebuds7i_dual_device_refresh),
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = ::refresh,
            )
        }
    }
    val target = removeTarget
    OverlayDialog(
        title = stringResource(R.string.freebuds7i_dual_device_remove_title),
        summary = target?.let {
            stringResource(R.string.freebuds7i_dual_device_remove_summary, it.name)
        }.orEmpty(),
        show = target != null,
        onDismissRequest = { if (!loading) removeTarget = null },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                enabled = !loading,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                onClick = { removeTarget = null },
            )
            TextButton(
                text = stringResource(R.string.confirm),
                enabled = target != null && !loading,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                onClick = {
                    val selected = target ?: return@TextButton
                    val headset = context.freeBuds7iDevice(address) ?: return@TextButton
                    loading = true
                    HuaweiFreeBuds7iController.removeDualDevice(context, headset, selected.address) { success ->
                        loading = false
                        removeTarget = null
                        if (success) refresh() else {
                            Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FreeBuds7iReadbackEffect(
    address: String,
    onReadback: (FreeBuds7iSettingsState) -> Unit,
) {
    val context = LocalContext.current
    val currentOnReadback by rememberUpdatedState(onReadback)
    DisposableEffect(address, context) {
        val device = context.freeBuds7iDevice(address) ?: return@DisposableEffect onDispose { }
        var disposed = false
        HuaweiFreeBuds7iController.requestSettingsState(context, device) { update ->
            if (!disposed) currentOnReadback(update)
        }
        onDispose { disposed = true }
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeBuds7iDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)

private fun Context.setFreeBuds7iBoolean(
    address: String,
    feature: FreeBuds7iBooleanFeature,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds7iDevice(address) ?: return complete(false)
    HuaweiFreeBuds7iController.setBooleanFeature(this, device, feature, enabled, complete)
}

private fun Context.setFreeBuds7iSpatialMode(
    address: String,
    mode: FreeClip2SpatialAudioMode,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds7iDevice(address) ?: return complete(false)
    HuaweiFreeBuds7iController.setSpatialAudioMode(this, device, mode, complete)
}

private fun Context.setFreeBuds7iSoundEffect(
    address: String,
    effect: FreeBuds5SoundEffect,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds7iDevice(address) ?: return complete(false)
    HuaweiFreeBuds7iController.setSoundEffect(this, device, effect, complete)
}

private fun Context.setHuaweiCustomEqualizer(
    address: String,
    route: HuaweiDeviceRoute,
    gains: List<Int>,
    complete: (Boolean) -> Unit,
) {
    val device = freeBuds7iDevice(address) ?: return complete(false)
    HuaweiEqualizerController.setCustom(
        context = this,
        device = device,
        route = route,
        gains = gains,
        presetName = "HuaweiPods EQ",
        onComplete = complete,
    )
}

private fun persistFreeBuds7iState(
    prefs: SharedPreferences,
    prefix: String,
    update: FreeBuds7iSettingsState,
) {
    val editor = prefs.edit()
    update.wearDetection?.let { editor.putBoolean(prefix + "wear_detection", it) }
    update.headMotionControl?.let { editor.putBoolean(prefix + "head_motion", it) }
    update.spatialAudioMode?.let { editor.putString(prefix + "spatial", it.extraValue) }
    update.soundEffect?.let { editor.putString(prefix + "sound_effect", it.name) }
    update.highQualityAudio?.let { editor.putBoolean(prefix + "high_quality", it) }
    editor.apply()
}

private fun FreeClip2SpatialAudioMode.freeBuds7iLabelRes(): Int = when (this) {
    FreeClip2SpatialAudioMode.OFF -> R.string.freebuds7i_spatial_off
    FreeClip2SpatialAudioMode.FIXED -> R.string.freebuds7i_spatial_fixed
    FreeClip2SpatialAudioMode.HEAD_TRACKING -> R.string.freebuds7i_spatial_head_tracking
}

private fun FreeBuds5SoundEffect.freeBuds7iLabelRes(): Int = when (this) {
    FreeBuds5SoundEffect.DEFAULT -> R.string.freebuds7i_sound_effect_default
    FreeBuds5SoundEffect.BASS_ENHANCE -> R.string.freebuds7i_sound_effect_bass
    FreeBuds5SoundEffect.TREBLE_ENHANCE -> R.string.freebuds7i_sound_effect_treble
    FreeBuds5SoundEffect.CLEAR_VOICE -> R.string.freebuds7i_sound_effect_clear_voice
}

private fun freeBuds7iPreferencePrefix(address: String): String =
    "freebuds7i_${address.uppercase().ifBlank { "unknown" }}_"

private fun SharedPreferences.nullableBoolean(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null

private fun readEqualizerGains(prefs: SharedPreferences, prefix: String): List<Int> =
    prefs.getString(prefix + "equalizer", null)
        ?.split(',')
        ?.mapNotNull(String::toIntOrNull)
        ?.takeIf { values -> values.size == 10 && values.all { it in -60..60 } }
        ?: List(10) { 0 }

private fun List<Int>.withGain(index: Int, value: Int): List<Int> =
    mapIndexed { currentIndex, current -> if (currentIndex == index) value.coerceIn(-60, 60) else current }
