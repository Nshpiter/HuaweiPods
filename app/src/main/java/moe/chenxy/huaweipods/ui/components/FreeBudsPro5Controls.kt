package moe.chenxy.huaweipods.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.pods.FreeBudsPro5BooleanFeature
import moe.chenxy.huaweipods.pods.FreeBudsPro5EarTipMaterial
import moe.chenxy.huaweipods.pods.FreeBudsPro5SettingsState
import moe.chenxy.huaweipods.pods.FreeBudsPro5SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.HuaweiFreeBudsPro5Controller
import moe.chenxy.huaweipods.pods.mergeFreeBudsPro5SettingsState

/** Controls backed by the FreeBuds Pro 5 00016D/17 guided capture. */
@Composable
fun FreeBudsPro5Controls(address: String) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val prefix = remember(address) { freeBudsPro5PreferencePrefix(address) }
    var state by remember(address) {
        mutableStateOf(
            FreeBudsPro5SettingsState(
                adaptiveVolume = prefs.nullablePro5Boolean(prefix + "adaptive_volume"),
                headMotionControl = prefs.nullablePro5Boolean(prefix + "head_motion"),
                voiceControl = prefs.nullablePro5Boolean(prefix + "voice_control"),
                spatialAudioMode = prefs.getString(prefix + "spatial", null)
                    ?.let(FreeClip2SpatialAudioMode::fromExtraValue),
                highQualityAudio = prefs.nullablePro5Boolean(prefix + "high_quality"),
                dualDevice = prefs.nullablePro5Boolean(prefix + "dual_device"),
                casePromptSound = prefs.nullablePro5Boolean(prefix + "case_prompt_sound"),
                earTipMaterial = prefs.getString(prefix + "ear_tip", null)
                    ?.let(FreeBudsPro5EarTipMaterial::fromExtraValue),
            ),
        )
    }

    FreeBudsPro5ReadbackEffect(address) { update ->
        state = mergeFreeBudsPro5SettingsState(state, update)
        persistFreeBudsPro5State(prefs, prefix, update)
    }

    fun setBoolean(
        feature: FreeBudsPro5BooleanFeature,
        enabled: Boolean,
        stateUpdate: FreeBudsPro5SettingsState,
        preferenceKey: String,
        complete: (Boolean) -> Unit,
    ) {
        context.setFreeBudsPro5Boolean(address, feature, enabled) { success ->
            if (success) {
                state = mergeFreeBudsPro5SettingsState(state, stateUpdate)
                prefs.edit().putBoolean(prefix + preferenceKey, enabled).apply()
            }
            complete(success)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FreeBuds7iSectionTitle(R.string.freebuds_pro5_smart_features)
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds_pro5_adaptive_volume,
            value = state.adaptiveVolume,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.ADAPTIVE_VOLUME,
                    enabled,
                    FreeBudsPro5SettingsState(adaptiveVolume = enabled),
                    "adaptive_volume",
                    complete,
                )
            },
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds7i_head_motion,
            value = state.headMotionControl,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.HEAD_MOTION_CONTROL,
                    enabled,
                    FreeBudsPro5SettingsState(headMotionControl = enabled),
                    "head_motion",
                    complete,
                )
            },
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds_pro5_voice_control,
            value = state.voiceControl,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.VOICE_CONTROL,
                    enabled,
                    FreeBudsPro5SettingsState(voiceControl = enabled),
                    "voice_control",
                    complete,
                )
            },
        )

        FreeBuds7iSectionTitle(R.string.freebuds7i_spatial_audio)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds7i_spatial_audio),
            selected = state.spatialAudioMode,
            values = FreeClip2SpatialAudioMode.entries,
            label = { stringResource(it.pro5SpatialLabelRes()) },
            onSelected = { mode, complete ->
                context.setFreeBudsPro5SpatialMode(address, mode) { success ->
                    if (success) {
                        state = mergeFreeBudsPro5SettingsState(
                            state,
                            FreeBudsPro5SettingsState(spatialAudioMode = mode),
                        )
                        prefs.edit().putString(prefix + "spatial", mode.extraValue).apply()
                    }
                    complete(success)
                }
            },
        )

        FreeBuds7iSectionTitle(R.string.freebuds7i_sound_and_connection)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds_pro5_sound_effect),
            selected = state.equalizer?.selectedId?.let(FreeBudsPro5SoundEffect::fromProtocolValue),
            values = FreeBudsPro5SoundEffect.entries,
            label = { stringResource(it.pro5SoundEffectLabelRes()) },
            onSelected = { effect, complete ->
                val device = context.freeBudsPro5Device(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiFreeBudsPro5Controller.setSoundEffect(
                        context = context,
                        device = device,
                        effect = effect,
                    ) { success ->
                        if (success) {
                            val previous = state.equalizer
                            state = mergeFreeBudsPro5SettingsState(
                                state,
                                FreeBudsPro5SettingsState(
                                    equalizer = previous?.copy(selectedId = effect.protocolValue)
                                        ?: HuaweiEqualizerState(
                                            supported = true,
                                            selectedId = effect.protocolValue,
                                            builtInIds = FreeBudsPro5SoundEffect.entries.map {
                                                it.protocolValue
                                            },
                                            bandCount = 10,
                                            selectedName = null,
                                            selectedGains = null,
                                            customPresets = emptyList(),
                                        ),
                                ),
                            )
                        }
                        complete(success)
                    }
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            readback = state.equalizer,
            requestOnMount = false,
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds7i_high_quality_audio,
            value = state.highQualityAudio,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.HIGH_QUALITY_AUDIO,
                    enabled,
                    FreeBudsPro5SettingsState(highQualityAudio = enabled),
                    "high_quality",
                    complete,
                )
            },
        )
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds_pro5_dual_device,
            value = state.dualDevice,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.DUAL_DEVICE,
                    enabled,
                    FreeBudsPro5SettingsState(dualDevice = enabled),
                    "dual_device",
                    complete,
                )
            },
        )

        FreeBuds7iSectionTitle(R.string.freebuds_pro5_case_and_fit)
        FreeBuds7iFeatureToggle(
            titleRes = R.string.freebuds_pro5_case_prompt_sound,
            value = state.casePromptSound,
            onChange = { enabled, complete ->
                setBoolean(
                    FreeBudsPro5BooleanFeature.CASE_PROMPT_SOUND,
                    enabled,
                    FreeBudsPro5SettingsState(casePromptSound = enabled),
                    "case_prompt_sound",
                    complete,
                )
            },
        )
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds_pro5_ear_tip_material),
            selected = state.earTipMaterial,
            values = FreeBudsPro5EarTipMaterial.entries,
            label = { stringResource(it.pro5EarTipLabelRes()) },
            onSelected = { material, complete ->
                context.setFreeBudsPro5EarTip(address, material) { success ->
                    if (success) {
                        state = mergeFreeBudsPro5SettingsState(
                            state,
                            FreeBudsPro5SettingsState(earTipMaterial = material),
                        )
                        prefs.edit().putString(prefix + "ear_tip", material.extraValue).apply()
                    }
                    complete(success)
                }
            },
        )
    }
}

@Composable
private fun FreeBudsPro5ReadbackEffect(
    address: String,
    onUpdate: (FreeBudsPro5SettingsState) -> Unit,
) {
    val context = LocalContext.current
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    DisposableEffect(context, address) {
        val device = context.freeBudsPro5Device(address)
            ?: return@DisposableEffect onDispose { }
        var disposed = false
        val emit: (FreeBudsPro5SettingsState) -> Unit = { update ->
            if (!disposed) currentOnUpdate(update)
        }
        HuaweiFreeBudsPro5Controller.requestSettingsState(context, device, emit)
        HuaweiEqualizerController.requestState(
            context,
            device,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        ) { equalizer ->
            equalizer?.let { emit(FreeBudsPro5SettingsState(equalizer = it)) }
        }
        onDispose { disposed = true }
    }
}

private fun Context.setFreeBudsPro5Boolean(
    address: String,
    feature: FreeBudsPro5BooleanFeature,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeBudsPro5Device(address) ?: return complete(false)
    HuaweiFreeBudsPro5Controller.setBooleanFeature(this, device, feature, enabled, complete)
}

private fun Context.setFreeBudsPro5SpatialMode(
    address: String,
    mode: FreeClip2SpatialAudioMode,
    complete: (Boolean) -> Unit,
) {
    val device = freeBudsPro5Device(address) ?: return complete(false)
    HuaweiFreeBudsPro5Controller.setSpatialAudioMode(this, device, mode, complete)
}

private fun Context.setFreeBudsPro5EarTip(
    address: String,
    material: FreeBudsPro5EarTipMaterial,
    complete: (Boolean) -> Unit,
) {
    val device = freeBudsPro5Device(address) ?: return complete(false)
    HuaweiFreeBudsPro5Controller.setEarTipMaterial(this, device, material, complete)
}

private fun Context.freeBudsPro5Device(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)

private fun persistFreeBudsPro5State(
    prefs: SharedPreferences,
    prefix: String,
    update: FreeBudsPro5SettingsState,
) {
    val editor = prefs.edit()
    update.adaptiveVolume?.let { editor.putBoolean(prefix + "adaptive_volume", it) }
    update.headMotionControl?.let { editor.putBoolean(prefix + "head_motion", it) }
    update.voiceControl?.let { editor.putBoolean(prefix + "voice_control", it) }
    update.spatialAudioMode?.let { editor.putString(prefix + "spatial", it.extraValue) }
    update.highQualityAudio?.let { editor.putBoolean(prefix + "high_quality", it) }
    update.dualDevice?.let { editor.putBoolean(prefix + "dual_device", it) }
    update.casePromptSound?.let { editor.putBoolean(prefix + "case_prompt_sound", it) }
    update.earTipMaterial?.let { editor.putString(prefix + "ear_tip", it.extraValue) }
    editor.apply()
}

private fun SharedPreferences.nullablePro5Boolean(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null

private fun freeBudsPro5PreferencePrefix(address: String): String =
    "freebuds_pro5_${address.uppercase().ifBlank { "unknown" }}_"

private fun FreeClip2SpatialAudioMode.pro5SpatialLabelRes(): Int = when (this) {
    FreeClip2SpatialAudioMode.OFF -> R.string.freebuds7i_spatial_off
    FreeClip2SpatialAudioMode.FIXED -> R.string.freebuds7i_spatial_fixed
    FreeClip2SpatialAudioMode.HEAD_TRACKING -> R.string.freebuds7i_spatial_head_tracking
}

private fun FreeBudsPro5EarTipMaterial.pro5EarTipLabelRes(): Int = when (this) {
    FreeBudsPro5EarTipMaterial.SILICONE -> R.string.freebuds_pro5_ear_tip_silicone
    FreeBudsPro5EarTipMaterial.MEMORY_FOAM -> R.string.freebuds_pro5_ear_tip_memory_foam
}

private fun FreeBudsPro5SoundEffect.pro5SoundEffectLabelRes(): Int = when (this) {
    FreeBudsPro5SoundEffect.YUEZHANG_BALANCED -> R.string.freebuds_pro5_effect_yuezhang_balanced
    FreeBudsPro5SoundEffect.YUEZHANG_VOCAL -> R.string.freebuds_pro5_effect_yuezhang_vocal
    FreeBudsPro5SoundEffect.YUEZHANG_BASS -> R.string.freebuds_pro5_effect_yuezhang_bass
    FreeBudsPro5SoundEffect.YUEZHANG_CLASSICAL -> R.string.freebuds_pro5_effect_yuezhang_classical
    FreeBudsPro5SoundEffect.MOVIE -> R.string.freebuds_pro5_effect_movie
    FreeBudsPro5SoundEffect.PODCAST_VOICE -> R.string.freebuds_pro5_effect_podcast_voice
    FreeBudsPro5SoundEffect.GAME -> R.string.freebuds_pro5_effect_game
    FreeBudsPro5SoundEffect.SPORT -> R.string.freebuds_pro5_effect_sport
    FreeBudsPro5SoundEffect.AI -> R.string.freebuds_pro5_effect_ai
}
