package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.pods.FreeClip2BooleanFeature
import moe.chenxy.huaweipods.pods.FreeClip2HeadMotionAction
import moe.chenxy.huaweipods.pods.FreeClip2HeadMotionGesture
import moe.chenxy.huaweipods.pods.FreeClip2HeadMotionState
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.pods.HuaweiFreeClip2Controller
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.readHuaweiEqualizerCustomPresets
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
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
    val settings = rememberFreeClip2BooleanSettings(address)

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
        FreeClip2Section(R.string.freeclip2_spatial_audio) {
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
        }
        FreeClip2Section(R.string.freeclip2_sound_effect) {
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
        }

        FreeClip2SettingsGroups(settings)
    }
}

@Composable
internal fun FreeClip2SettingsGroups(settings: FreeClip2BooleanUiState) {
    Column(Modifier.fillMaxWidth()) {
        FreeClip2Section(R.string.freeclip2_smart_features) {
            listOf(
                FreeClip2BooleanFeature.WEAR_DETECTION to R.string.freeclip2_wear_detection,
                FreeClip2BooleanFeature.DROP_REMINDER to R.string.freeclip2_drop_reminder,
                FreeClip2BooleanFeature.ADAPTIVE_VOLUME to R.string.freeclip2_adaptive_volume,
            ).forEach { (feature, title) ->
                BooleanFeaturePreference(title, feature, settings)
            }
            FreeClip2DeviceSettingsControls(settings)
        }

        FreeClip2Section(R.string.freeclip2_sound_and_connection) {
            listOf(
                FreeClip2BooleanFeature.LOW_LATENCY to R.string.freeclip2_low_latency,
                FreeClip2BooleanFeature.DUAL_DEVICE to R.string.freeclip2_dual_device,
                FreeClip2BooleanFeature.CASE_PROMPT_SOUND to R.string.freeclip2_case_prompt_sound,
            ).forEach { (feature, title) ->
                BooleanFeaturePreference(title, feature, settings)
            }
            ArrowPreference(
                title = stringResource(R.string.freeclip2_refresh_settings),
                summary = stringResource(if (settings.reading) R.string.freeclip2_settings_loading else R.string.freeclip2_refresh_summary),
                enabled = !settings.busy,
                onClick = settings.refresh,
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

internal class FreeClip2BooleanUiState {
    var values by mutableStateOf<Map<FreeClip2BooleanFeature, Boolean?>>(emptyMap())
    var head by mutableStateOf<FreeClip2HeadMotionState?>(null)
    var reading by mutableStateOf(false)
    var writing by mutableStateOf(false)
    val busy get() = reading || writing
    var revision = 0
    var active = true
    var refresh: () -> Unit = {}
    var change: (FreeClip2BooleanFeature, Boolean, (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) }
    var changeHead: (FreeClip2HeadMotionGesture, FreeClip2HeadMotionAction, (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) }

    fun finishWrite(ticket: Int, success: Boolean, done: (Boolean) -> Unit) {
        writing = false
        done(success)
        if (active && ticket != revision) refresh()
    }

    fun updateHead(value: FreeClip2HeadMotionState?) {
        head = value
        values = values + (FreeClip2BooleanFeature.HEAD_MOTION_CONTROL to value?.enabled)
    }
}

@Composable
private fun rememberFreeClip2BooleanSettings(address: String): FreeClip2BooleanUiState {
    val context = LocalContext.current
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val device = remember(context, address) { context.freeClip2Device(address) }
    val state = remember(address) { FreeClip2BooleanUiState() }
    state.refresh = {
        if (state.active && !state.busy && device != null) {
            val revision = ++state.revision
            state.reading = true
            state.values = emptyMap()
            state.head = null
            HuaweiFreeClip2Controller.requestBooleanSettings(context, device) { values, head ->
                if (state.active && revision == state.revision) {
                    state.values = values
                    state.head = head
                    state.reading = false
                }
            }
        }
    }
    state.change = { feature, enabled, done ->
        if (!state.active || state.busy || device == null) done(false)
        else {
            val revision = ++state.revision
            state.writing = true
            val complete: (Boolean) -> Unit = { success ->
                // 自动应用策略只由用户写入更新，读取耳机不会覆盖策略。
                val stored = if (success && feature == FreeClip2BooleanFeature.LOW_LATENCY) {
                    LowLatencyPrefs.setDesired(
                        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE),
                        HuaweiPodsApp.xposedService, address, HuaweiDeviceRoute.HUAWEI_FREECLIP2, enabled,
                    )
                } else success
                state.finishWrite(revision, stored, done)
            }
            if (feature == FreeClip2BooleanFeature.HEAD_MOTION_CONTROL) {
                HuaweiFreeClip2Controller.setHeadMotionEnabled(context, device, enabled,
                    onState = { if (state.active && revision == state.revision) state.updateHead(it) },
                    onComplete = complete,
                )
            } else HuaweiFreeClip2Controller.setBooleanFeatureVerified(
                context, device, feature, enabled,
                onState = { actual ->
                    if (state.active && revision == state.revision) state.values = state.values + (feature to actual)
                },
                onComplete = complete,
            )
        }
    }
    state.changeHead = { gesture, action, done ->
        if (!state.active || state.busy || device == null) done(false)
        else {
            val revision = ++state.revision
            state.writing = true
            HuaweiFreeClip2Controller.setHeadMotionAction(context, device, gesture, action,
                onState = { if (state.active && revision == state.revision) state.updateHead(it) },
                onComplete = { state.finishWrite(revision, it, done) },
            )
        }
    }
    DisposableEffect(state, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { state.active = true; state.refresh() }
                Lifecycle.Event.ON_PAUSE -> {
                    state.active = false
                    state.revision++
                    state.reading = false
                }
                else -> Unit
            }
        }
        state.active = lifecycleOwner == null || lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (state.active) state.refresh()
        onDispose {
            state.active = false
            state.revision++
            state.reading = false
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    return state
}

@Composable
private fun BooleanFeaturePreference(
    title: Int, feature: FreeClip2BooleanFeature, state: FreeClip2BooleanUiState, summary: String? = null,
) {
    FeatureToggle(
        titleRes = title,
        value = state.values[feature],
        available = !state.busy,
        summary = when {
            state.reading -> stringResource(R.string.freeclip2_settings_loading)
            state.values[feature] == null -> stringResource(R.string.freeclip2_settings_unknown)
            else -> summary
        },
        onChange = { enabled, done -> state.change(feature, enabled, done) },
    )
}

@Composable
private fun FreeClip2DeviceSettingsControls(settings: FreeClip2BooleanUiState) {
    BooleanFeaturePreference(R.string.freeclip2_head_motion, FreeClip2BooleanFeature.HEAD_MOTION_CONTROL, settings)
    if (settings.head?.enabled == true) {
        FreeClip2HeadMotionGesture.entries.forEach { gesture ->
            EnumPreference(
                title = stringResource(if (gesture == FreeClip2HeadMotionGesture.NOD) R.string.freeclip2_head_nod else R.string.freeclip2_head_shake),
                selected = settings.head?.action(gesture),
                values = FreeClip2HeadMotionAction.entries,
                label = { stringResource(it.labelRes()) },
                available = !settings.busy,
                onSelected = { action, complete ->
                    settings.changeHead(gesture, action, complete)
                },
            )
        }
    }
    BooleanFeaturePreference(
        title = R.string.freeclip2_noisy_volume_boost,
        feature = FreeClip2BooleanFeature.NOISY_VOLUME_BOOST,
        state = settings,
        summary = stringResource(R.string.freeclip2_noisy_volume_hint),
    )
}

@Composable
private fun FreeClip2Section(titleRes: Int, content: @Composable () -> Unit) {
    SmallTitle(text = stringResource(titleRes))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        content()
    }
}

@Composable
private fun FeatureToggle(
    titleRes: Int,
    value: Boolean?,
    available: Boolean = true,
    summary: String? = null,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    SwitchPreference(
        title = stringResource(titleRes),
        summary = if (value == null) summary ?: stringResource(R.string.freeclip2_settings_unknown) else summary,
        checked = value == true,
        enabled = available && value != null,
        onCheckedChange = { target ->
            onChange(target) { success ->
                if (!success) Toast.makeText(context, R.string.freeclip2_write_unconfirmed, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun <T> EnumPreference(
    title: String,
    selected: T?,
    values: List<T>,
    label: @Composable (T) -> String,
    available: Boolean = true,
    onSelected: (T, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    ArrowPreference(
        title = title,
        summary = selected?.let { label(it) } ?: stringResource(R.string.freeclip2_settings_unknown),
        enabled = available && !pending,
        holdDownState = showDialog,
        onClick = { showDialog = true },
    )
    OverlayDialog(
        title = title,
        summary = selected?.let { label(it) }.orEmpty(),
        show = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            values.forEach { value ->
                val isSelected = value == selected
                RadioButtonPreference(
                    title = label(value),
                    selected = isSelected,
                    enabled = available && !pending,
                    onClick = {
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
                    },
                )
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

private fun FreeClip2HeadMotionAction.labelRes(): Int = when (this) {
    FreeClip2HeadMotionAction.NONE -> R.string.freeclip2_head_action_none
    FreeClip2HeadMotionAction.ANSWER_CALL -> R.string.freeclip2_head_action_answer
    FreeClip2HeadMotionAction.REJECT_CALL -> R.string.freeclip2_head_action_reject
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
