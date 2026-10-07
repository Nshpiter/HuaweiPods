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
import moe.chenxy.huaweipods.pods.FreeBuds7Setting
import moe.chenxy.huaweipods.pods.FreeBuds7SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.HuaweiFreeBuds7Controller
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiTapAction
import moe.chenxy.huaweipods.pods.HuaweiTapState

/** FreeBuds 7 values start unknown and become selected only after an earphone readback. */
@Composable
fun FreeBuds7Controls(address: String) {
    val context = LocalContext.current
    val device = remember(address) {
        if (BluetoothAdapter.checkBluetoothAddress(address))
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        else null
    }
    var settings by remember(address) { mutableStateOf<Map<FreeBuds7Setting, Boolean?>>(emptyMap()) }
    var spatial by remember(address) { mutableStateOf<FreeClip2SpatialAudioMode?>(null) }
    var equalizer by remember(address) { mutableStateOf<HuaweiEqualizerState?>(null) }
    var tripleTap by remember(address) { mutableStateOf<HuaweiTapState?>(null) }
    var alive by remember(address) { mutableStateOf(true) }
    fun refreshEqualizer() {
        device?.let { target ->
            HuaweiEqualizerController.requestState(context, target, HuaweiDeviceRoute.HUAWEI_FREEBUDS7) { update ->
                if (alive) equalizer = update
            }
        }
    }
    DisposableEffect(device) {
        alive = true
        device?.let { target ->
            FreeBuds7Setting.entries.forEach { setting ->
                HuaweiFreeBuds7Controller.request(context, target, setting) { value ->
                    if (alive) settings = settings + (setting to value)
                }
            }
            HuaweiFreeBuds7Controller.requestSpatial(context, target) { if (alive) spatial = it }
            HuaweiFreeBuds7Controller.requestTripleTap(context, target) { if (alive) tripleTap = it }
            refreshEqualizer()
        }
        onDispose { alive = false }
    }
    Column {
        FreeBuds7iSectionTitle(R.string.freebuds7i_spatial_audio)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds7i_spatial_audio),
            selected = spatial,
            values = FreeClip2SpatialAudioMode.entries,
            label = { stringResource(when (it) {
                FreeClip2SpatialAudioMode.OFF -> R.string.freebuds7i_spatial_off
                FreeClip2SpatialAudioMode.FIXED -> R.string.freebuds7i_spatial_fixed
                FreeClip2SpatialAudioMode.HEAD_TRACKING -> R.string.freebuds7i_spatial_head_tracking
            }) },
            onSelected = { mode, complete ->
                if (device == null) complete(false)
                else HuaweiFreeBuds7Controller.setSpatial(context, device, mode) { success ->
                    if (alive) {
                        if (success) spatial = mode
                        HuaweiFreeBuds7Controller.request(context, device, FreeBuds7Setting.HIGH_QUALITY) {
                            if (alive) settings = settings + (FreeBuds7Setting.HIGH_QUALITY to it)
                        }
                        complete(success)
                    }
                }
            },
        )
        FreeBuds7iSectionTitle(R.string.freebuds7i_sound_and_connection)
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds_pro5_sound_effect),
            selected = FreeBuds7SoundEffect.entries.firstOrNull { it.protocolValue == equalizer?.selectedId },
            values = FreeBuds7SoundEffect.entries,
            label = { stringResource(it.labelRes()) },
            summaryOverride = if (equalizer?.isCustom == true && equalizer?.selectedId != 0xC9)
                stringResource(R.string.freebuds7i_custom_equalizer) else null,
            onSelected = { effect, complete ->
                if (device == null) complete(false)
                else HuaweiFreeBuds7Controller.setEffect(context, device, effect) { success ->
                    if (alive) {
                        refreshEqualizer()
                        complete(success)
                    }
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7,
            readback = equalizer,
            requestOnMount = false,
            onCustomApplied = { refreshEqualizer() },
        )
        FreeBuds7iSectionTitle(R.string.freebuds_pro5_smart_features)
        FreeBuds7Setting.entries.forEach { setting ->
            FreeBuds7iFeatureToggle(
                titleRes = setting.labelRes(),
                value = settings[setting],
                onChange = { enabled, complete ->
                    if (device == null) complete(false)
                    else HuaweiFreeBuds7Controller.set(context, device, setting, enabled) { success ->
                        if (alive) {
                            settings = settings + (setting to if (success) enabled else null)
                            if (setting == FreeBuds7Setting.HIGH_QUALITY) {
                                HuaweiFreeBuds7Controller.requestSpatial(context, device) { if (alive) spatial = it }
                            }
                            complete(success)
                        }
                    }
                },
            )
        }
        FreeBuds7iSectionTitle(R.string.huawei_gesture_tap_section)
        HuaweiGestureSide.entries.forEach { side ->
            FreeBuds7iChoicePreference(
                title = stringResource(if (side == HuaweiGestureSide.LEFT) R.string.huawei_gesture_left_triple_tap else R.string.huawei_gesture_right_triple_tap),
                selected = if (side == HuaweiGestureSide.LEFT) tripleTap?.left else tripleTap?.right,
                values = listOf(HuaweiTapAction.PLAY_NEXT, HuaweiTapAction.PLAY_PREVIOUS, HuaweiTapAction.NONE),
                label = { stringResource(when (it) {
                    HuaweiTapAction.PLAY_NEXT -> R.string.huawei_gesture_action_next
                    HuaweiTapAction.PLAY_PREVIOUS -> R.string.huawei_gesture_action_previous
                    else -> R.string.huawei_gesture_action_none
                }) },
                onSelected = { action, complete ->
                    if (device == null) complete(false)
                    else HuaweiFreeBuds7Controller.setTripleTap(context, device, side, action) { success ->
                        if (alive) {
                            HuaweiFreeBuds7Controller.requestTripleTap(context, device) { if (alive) tripleTap = it }
                            complete(success)
                        }
                    }
                },
            )
        }
    }
}

private fun FreeBuds7Setting.labelRes(): Int = when (this) {
    FreeBuds7Setting.ADAPTIVE_VOLUME -> R.string.freebuds_pro5_adaptive_volume
    FreeBuds7Setting.HEAD_MOTION -> R.string.freebuds7i_head_motion
    FreeBuds7Setting.DROP_REMINDER -> R.string.freeclip2_drop_reminder
    FreeBuds7Setting.SINGLE_EAR_ANC -> R.string.freebuds7_single_ear_anc
    FreeBuds7Setting.AUTO_SWITCH -> R.string.freebuds7_auto_switch
    FreeBuds7Setting.HIGH_QUALITY -> R.string.freebuds7i_high_quality_audio
    FreeBuds7Setting.LOW_LATENCY -> R.string.low_latency_mode
    FreeBuds7Setting.WEAR_DETECTION -> R.string.huawei_gesture_wear_detection
    FreeBuds7Setting.CASE_SOUND -> R.string.freeclip2_case_prompt_sound
    FreeBuds7Setting.DOUBLE_TAP -> R.string.freebuds7_double_tap
    FreeBuds7Setting.SWIPE_VOLUME -> R.string.huawei_gesture_pro3_swipe_volume
    FreeBuds7Setting.PINCH_PLAY -> R.string.huawei_gesture_pro3_media_play_pause
    FreeBuds7Setting.PINCH_NEXT -> R.string.huawei_gesture_pro3_media_next
    FreeBuds7Setting.PINCH_PREVIOUS -> R.string.huawei_gesture_pro3_media_previous
}

private fun FreeBuds7SoundEffect.labelRes(): Int = when (this) {
    FreeBuds7SoundEffect.BALANCED -> R.string.freebuds_pro5_effect_yuezhang_balanced
    FreeBuds7SoundEffect.VOCAL -> R.string.freebuds_pro5_effect_yuezhang_vocal
    FreeBuds7SoundEffect.BASS -> R.string.freebuds_pro5_effect_yuezhang_bass
    FreeBuds7SoundEffect.CLASSICAL -> R.string.freebuds_pro5_effect_yuezhang_classical
    FreeBuds7SoundEffect.MOVIE -> R.string.freebuds_pro5_effect_movie
    FreeBuds7SoundEffect.PODCAST -> R.string.freebuds_pro5_effect_podcast_voice
    FreeBuds7SoundEffect.GAME -> R.string.freebuds_pro5_effect_game
    FreeBuds7SoundEffect.SPORT -> R.string.freebuds_pro5_effect_sport
}
