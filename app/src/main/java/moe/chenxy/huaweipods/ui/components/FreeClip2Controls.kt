package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import moe.chenxy.huaweipods.pods.FreeClip2BooleanFeature
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.pods.HuaweiFreeClip2Controller
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.readHuaweiEqualizerCustomPresets
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** FreeClip 2-only controls backed by packets verified in the guided capture. */
@Composable
fun FreeClip2Controls(address: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val keyPrefix = remember(address) { "freeclip2_${address.uppercase().ifBlank { "unknown" }}_" }
    var spatialMode by remember(address) {
        mutableStateOf(
            enumPreference(
                prefs.getString(keyPrefix + "spatial_mode", null),
                FreeClip2SpatialAudioMode.OFF,
            ),
        )
    }
    var spatialScene by remember(address) {
        mutableStateOf(
            enumPreference(
                prefs.getString(keyPrefix + "spatial_scene", null),
                FreeClip2SpatialScene.DEFAULT,
            ),
        )
    }
    var soundEffect by remember(address) {
        mutableStateOf(
            enumPreference(
                prefs.getString(keyPrefix + "sound_effect", null),
                FreeClip2SoundEffect.DEFAULT,
            ),
        )
    }
    var equalizer by remember(address) { mutableStateOf<HuaweiEqualizerState?>(null) }

    FreeClip2AudioReadbackEffect(address) { mode, scene, effect, equalizerState ->
        mode?.let {
            spatialMode = it
            prefs.edit().putString(keyPrefix + "spatial_mode", it.name).apply()
        }
        scene?.let {
            spatialScene = it
            prefs.edit().putString(keyPrefix + "spatial_scene", it.name).apply()
        }
        effect?.let {
            soundEffect = it
            prefs.edit().putString(keyPrefix + "sound_effect", it.name).apply()
        }
        equalizerState?.let { equalizer = it }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(R.string.freeclip2_spatial_audio)
        EnumPreference(
            title = stringResource(R.string.freeclip2_spatial_mode),
            selected = spatialMode,
            values = FreeClip2SpatialAudioMode.entries,
            label = { mode -> stringResource(mode.labelRes()) },
            onSelected = { mode, complete ->
                context.setFreeClip2SpatialMode(address, mode) { success ->
                    complete(success)
                }
            },
        )

        if (spatialMode != FreeClip2SpatialAudioMode.OFF) {
            EnumPreference(
                title = stringResource(R.string.freeclip2_spatial_scene),
                selected = spatialScene,
                values = FreeClip2SpatialScene.entries,
                label = { scene -> stringResource(scene.labelRes()) },
                onSelected = { scene, complete ->
                    context.setFreeClip2SpatialScene(address, scene) { success ->
                        complete(success)
                    }
                },
            )
        }
        SectionTitle(R.string.freeclip2_sound_effect)
        EnumPreference(
            title = stringResource(R.string.freeclip2_sound_effect_preset),
            selected = soundEffect,
            values = FreeClip2SoundEffect.selectableEntries,
            label = { effect -> stringResource(effect.labelRes()) },
            onSelected = { effect, complete ->
                context.setFreeClip2SoundEffect(address, effect) { success ->
                    complete(success)
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            readback = equalizer,
            requestOnMount = false,
            editable = true,
        )

        SectionTitle(R.string.freeclip2_smart_features)
        listOf(
            FreeClip2BooleanFeature.WEAR_DETECTION to R.string.freeclip2_wear_detection,
            FreeClip2BooleanFeature.DROP_REMINDER to R.string.freeclip2_drop_reminder,
            FreeClip2BooleanFeature.ADAPTIVE_VOLUME to R.string.freeclip2_adaptive_volume,
            FreeClip2BooleanFeature.HEAD_MOTION_CONTROL to R.string.freeclip2_head_motion,
        ).forEach { (feature, title) ->
            FeatureToggle(
                titleRes = title,
                initialValue = prefs.getBoolean(keyPrefix + feature.extraValue, false),
                onChange = { enabled, complete ->
                    context.setFreeClip2BooleanFeature(address, feature, enabled) { success ->
                        if (success) prefs.edit().putBoolean(keyPrefix + feature.extraValue, enabled).apply()
                        complete(success)
                    }
                },
            )
        }

        SectionTitle(R.string.freeclip2_sound_and_connection)
        listOf(
            FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY to R.string.freeclip2_sound_quality_priority,
            FreeClip2BooleanFeature.LOW_LATENCY to R.string.freeclip2_low_latency,
            FreeClip2BooleanFeature.DUAL_DEVICE to R.string.freeclip2_dual_device,
            FreeClip2BooleanFeature.CASE_PROMPT_SOUND to R.string.freeclip2_case_prompt_sound,
        ).forEach { (feature, title) ->
            val initialValue = if (feature == FreeClip2BooleanFeature.LOW_LATENCY) {
                LowLatencyPrefs.desiredOrNull(
                    prefs,
                    address,
                    HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                ) ?: false
            } else {
                prefs.getBoolean(keyPrefix + feature.extraValue, false)
            }
            FeatureToggle(
                titleRes = title,
                initialValue = initialValue,
                onChange = { enabled, complete ->
                    context.setFreeClip2BooleanFeature(address, feature, enabled) { success ->
                        if (success) {
                            val stored = if (feature == FreeClip2BooleanFeature.LOW_LATENCY) {
                                LowLatencyPrefs.setDesired(
                                    prefs = prefs,
                                    service = HuaweiPodsApp.xposedService,
                                    address = address,
                                    route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                                    enabled = enabled,
                                )
                            } else {
                                prefs.edit().putBoolean(keyPrefix + feature.extraValue, enabled).apply()
                                true
                            }
                            complete(stored)
                        } else {
                            complete(false)
                        }
                    }
                },
            )
        }

        Text(
            text = stringResource(R.string.freeclip2_state_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.headline1,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FeatureToggle(
    titleRes: Int,
    initialValue: Boolean,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember(initialValue) { mutableStateOf(initialValue) }
    var pending by remember { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            val target = !enabled
            pending = true
            onChange(target) { success ->
                pending = false
                if (success) {
                    enabled = target
                } else {
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
        Checkbox(state = ToggleableState(enabled), enabled = !pending, onClick = toggle)
    }
}

@Composable
private fun <T> EnumPreference(
    title: String,
    selected: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Button) { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
        Text(
            label(selected),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
    OverlayDialog(
        title = title,
        summary = label(selected),
        show = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            values.forEach { value ->
                val isSelected = value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !pending, role = Role.RadioButton) {
                            if (isSelected) {
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
                    Checkbox(state = ToggleableState(isSelected), enabled = !pending, onClick = null)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun Context.freeClip2Device(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)

private fun Context.setFreeClip2BooleanFeature(
    address: String,
    feature: FreeClip2BooleanFeature,
    enabled: Boolean,
    complete: (Boolean) -> Unit,
) {
    val device = freeClip2Device(address) ?: return complete(false)
    HuaweiFreeClip2Controller.setBooleanFeature(this, device, feature, enabled, complete)
}

private fun Context.setFreeClip2SpatialMode(
    address: String,
    value: FreeClip2SpatialAudioMode,
    complete: (Boolean) -> Unit,
) {
    complete(sendFreeClip2AudioSetting(address, HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE, value.extraValue))
}

private fun Context.setFreeClip2SpatialScene(
    address: String,
    value: FreeClip2SpatialScene,
    complete: (Boolean) -> Unit,
) {
    complete(sendFreeClip2AudioSetting(address, HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE, value.extraValue))
}

private fun Context.setFreeClip2SoundEffect(
    address: String,
    value: FreeClip2SoundEffect,
    complete: (Boolean) -> Unit,
) {
    complete(sendFreeClip2AudioSetting(address, HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT, value.extraValue))
}

private fun Context.sendFreeClip2AudioSetting(address: String, kind: String, value: String): Boolean {
    if (!BluetoothAdapter.checkBluetoothAddress(address)) return false
    return runCatching {
        (applicationContext ?: this).sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET).apply {
            putExtra("address", address)
            encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_KIND, kind)
            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_VALUE, value)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }.isSuccess
}

@Composable
private fun FreeClip2AudioReadbackEffect(
    address: String,
    onReadback: (
        FreeClip2SpatialAudioMode?,
        FreeClip2SpatialScene?,
        FreeClip2SoundEffect?,
        HuaweiEqualizerState?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val currentOnReadback by rememberUpdatedState(onReadback)
    DisposableEffect(address, context) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return@DisposableEffect onDispose { }
        }
        val receiverContext = context.applicationContext ?: context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                if (receivedIntent.action != HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED) return
                if (!receivedIntent.getStringExtra("address").equals(address, ignoreCase = true)) return
                if (!receivedIntent.getBooleanExtra(
                        HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_CONFIRMED,
                        false,
                    )
                ) {
                    return
                }
                currentOnReadback(
                    FreeClip2SpatialAudioMode.fromExtraValue(
                        receivedIntent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE),
                    ),
                    FreeClip2SpatialScene.fromExtraValue(
                        receivedIntent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE),
                    ),
                    FreeClip2SoundEffect.fromExtraValue(
                        receivedIntent.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT),
                    ),
                    receivedIntent.takeIf {
                        it.hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID)
                    }?.let {
                        val selectedId = it.getIntExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                            -1,
                        )
                        val gains = it.getIntArrayExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS,
                        )?.toList()
                        selectedId.takeIf { id -> id in 0..0xFF }?.let { id ->
                            HuaweiEqualizerState(
                                supported = it.getBooleanExtra(
                                    HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SUPPORTED,
                                    true,
                                ),
                                selectedId = id,
                                builtInIds = emptyList(),
                                bandCount = gains?.size ?: 10,
                                selectedName = it.getStringExtra(
                                    HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME,
                                ),
                                selectedGains = gains,
                                customPresets = it.readHuaweiEqualizerCustomPresets().orEmpty(),
                            )
                        }
                    },
                )
            }
        }
        receiverContext.registerReceiver(
            receiver,
            IntentFilter(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        receiverContext.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH).apply {
            putExtra("address", address)
            encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.HUAWEI_FREECLIP2)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("force", true)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        onDispose {
            runCatching { receiverContext.unregisterReceiver(receiver) }
        }
    }
}

private inline fun <reified T : Enum<T>> enumPreference(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

private fun FreeClip2SpatialAudioMode.labelRes(): Int = when (this) {
    FreeClip2SpatialAudioMode.OFF -> R.string.off
    FreeClip2SpatialAudioMode.FIXED -> R.string.freeclip2_spatial_fixed
    FreeClip2SpatialAudioMode.HEAD_TRACKING -> R.string.freeclip2_spatial_head_tracking
}

private fun FreeClip2SpatialScene.labelRes(): Int = when (this) {
    FreeClip2SpatialScene.DEFAULT -> R.string.freeclip2_spatial_scene_default
    FreeClip2SpatialScene.AUDIO_THEATER -> R.string.freeclip2_spatial_scene_theater
    FreeClip2SpatialScene.CINEMA -> R.string.freeclip2_spatial_scene_cinema
    FreeClip2SpatialScene.CONCERT_HALL -> R.string.freeclip2_spatial_scene_concert
}

private fun FreeClip2SoundEffect.labelRes(): Int = when (this) {
    FreeClip2SoundEffect.DEFAULT -> R.string.freeclip2_sound_effect_default
    FreeClip2SoundEffect.SPORT_ENHANCE -> R.string.freeclip2_sound_effect_sport
    FreeClip2SoundEffect.TREBLE_ENHANCE -> R.string.freeclip2_sound_effect_treble
    FreeClip2SoundEffect.CLEAR_VOICE -> R.string.freeclip2_sound_effect_clear_voice
    FreeClip2SoundEffect.CUSTOM -> R.string.freeclip2_sound_effect_custom
}
