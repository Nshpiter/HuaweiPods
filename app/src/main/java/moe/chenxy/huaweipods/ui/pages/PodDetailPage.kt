package moe.chenxy.huaweipods.ui.pages

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.WearStatus
import moe.chenxy.huaweipods.pods.HuaweiGestureAction
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.ui.components.AncSwitch
import moe.chenxy.huaweipods.ui.components.PodStatus
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import moe.chenxy.huaweipods.pods.EqPreset
import moe.chenxy.huaweipods.pods.isHuaweiFreeBudsByName
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GESTURE_TAG = "HuaweiPods-Gesture"

@Composable
fun PodDetailPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomContentPadding: Dp = 16.dp,
    podName: String,
    connectedDeviceAddress: String = "",
    batteryParams: BatteryParams,
    wearStatus: WearStatus = WearStatus(),
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode? = null,
    huaweiAncLevel: Int = 0,
    onHuaweiAncLevelChange: (Int) -> Unit = {},
    transparencyVocalEnhancement: Boolean = false,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit = {},
    gameMode: Boolean = false,
    onGameModeChange: (Boolean) -> Unit = {},
    spatialAudioMode: Int = ConfigManager.SPATIAL_AUDIO_OFF,
    onSpatialAudioModeChange: (Int) -> Unit = {},
    dualDeviceConnection: Boolean = false,
    onDualDeviceConnectionChange: (Boolean) -> Unit = {},
    spatialAudioSupported: Boolean = false,
    spatialSoundSupported: Boolean = false,
    adaptiveModeEnabled: Boolean = true,
    simpleAncMode: Boolean = true,
    eqPreset: Int = -1,
    onEqPresetChange: (Int) -> Unit = {},
    boxImagePath: String? = null,
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gesturePrefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val gestureControlEnabled = remember(podName, connectedDeviceAddress) {
        isHuaweiFreeBudsByName(podName) && BluetoothAdapter.checkBluetoothAddress(connectedDeviceAddress)
    }
    var leftGestureAction by remember(connectedDeviceAddress) {
        mutableStateOf(readGesturePreference(gesturePrefs, connectedDeviceAddress, HuaweiGestureSide.LEFT))
    }
    var rightGestureAction by remember(connectedDeviceAddress) {
        mutableStateOf(readGesturePreference(gesturePrefs, connectedDeviceAddress, HuaweiGestureSide.RIGHT))
    }

    fun setGestureAction(side: HuaweiGestureSide, action: HuaweiGestureAction) {
        context.sendHuaweiGestureSetCommand(connectedDeviceAddress, side, action) { success ->
            if (!success) {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                return@sendHuaweiGestureSetCommand
            }
            if (side == HuaweiGestureSide.LEFT) {
                leftGestureAction = action
            } else {
                rightGestureAction = action
            }
            writeGesturePreference(gesturePrefs, connectedDeviceAddress, side, action)
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = rememberPodImagePainter(boxImagePath),
                    contentDescription = "Earphones",
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 360.dp),
                    contentScale = ContentScale.FillWidth
                )
                Text(
                    text = podName,
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                podControlItems(
                    batteryParams = batteryParams,
                    wearStatus = wearStatus,
                    ancMode = ancMode,
                    onAncModeChange = onAncModeChange,
                    smartAncLevel = smartAncLevel,
                    huaweiAncLevel = huaweiAncLevel,
                    onHuaweiAncLevelChange = onHuaweiAncLevelChange,
                    gestureControlEnabled = gestureControlEnabled,
                    leftGestureAction = leftGestureAction,
                    rightGestureAction = rightGestureAction,
                    onGestureActionChange = ::setGestureAction,
                    transparencyVocalEnhancement = transparencyVocalEnhancement,
                    onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                    gameMode = gameMode,
                    onGameModeChange = onGameModeChange,
                    spatialAudioMode = spatialAudioMode,
                    onSpatialAudioModeChange = onSpatialAudioModeChange,
                    dualDeviceConnection = dualDeviceConnection,
                    onDualDeviceConnectionChange = onDualDeviceConnectionChange,
                    spatialAudioSupported = spatialAudioSupported,
                    spatialSoundSupported = spatialSoundSupported,
                    adaptiveModeEnabled = adaptiveModeEnabled,
                    simpleAncMode = simpleAncMode,
                    eqPreset = eqPreset,
                    onEqPresetChange = onEqPresetChange,
                    bottomContentPadding = bottomContentPadding
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Image(
                painter = rememberPodImagePainter(boxImagePath),
                contentDescription = "Earphones",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        podControlItems(
            batteryParams = batteryParams,
            wearStatus = wearStatus,
            ancMode = ancMode,
            onAncModeChange = onAncModeChange,
            smartAncLevel = smartAncLevel,
            huaweiAncLevel = huaweiAncLevel,
            onHuaweiAncLevelChange = onHuaweiAncLevelChange,
            gestureControlEnabled = gestureControlEnabled,
            leftGestureAction = leftGestureAction,
            rightGestureAction = rightGestureAction,
            onGestureActionChange = ::setGestureAction,
            transparencyVocalEnhancement = transparencyVocalEnhancement,
            onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
            gameMode = gameMode,
            onGameModeChange = onGameModeChange,
            spatialAudioMode = spatialAudioMode,
            onSpatialAudioModeChange = onSpatialAudioModeChange,
            dualDeviceConnection = dualDeviceConnection,
            onDualDeviceConnectionChange = onDualDeviceConnectionChange,
            spatialAudioSupported = spatialAudioSupported,
            spatialSoundSupported = spatialSoundSupported,
            adaptiveModeEnabled = adaptiveModeEnabled,
            simpleAncMode = simpleAncMode,
            eqPreset = eqPreset,
            onEqPresetChange = onEqPresetChange,
            bottomContentPadding = bottomContentPadding
        )
    }
}
@Composable
private fun rememberPodImagePainter(path: String?) = remember(path) {
    path?.let {
        runCatching { BitmapFactory.decodeFile(it) }
            .getOrNull()
            ?.let { bitmap -> BitmapPainter(bitmap.asImageBitmap()) }
    }
} ?: painterResource(R.drawable.img_box)

private fun LazyListScope.podControlItems(
    batteryParams: BatteryParams,
    wearStatus: WearStatus,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode?,
    huaweiAncLevel: Int,
    onHuaweiAncLevelChange: (Int) -> Unit,
    gestureControlEnabled: Boolean,
    leftGestureAction: HuaweiGestureAction,
    rightGestureAction: HuaweiGestureAction,
    onGestureActionChange: (HuaweiGestureSide, HuaweiGestureAction) -> Unit,
    transparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    gameMode: Boolean,
    onGameModeChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
    dualDeviceConnection: Boolean,
    onDualDeviceConnectionChange: (Boolean) -> Unit,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    adaptiveModeEnabled: Boolean,
    simpleAncMode: Boolean,
    eqPreset: Int,
    onEqPresetChange: (Int) -> Unit,
    bottomContentPadding: Dp
) {
    val spatialAudioValues = listOf(
        ConfigManager.SPATIAL_AUDIO_OFF,
        ConfigManager.SPATIAL_AUDIO_FIXED,
        ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING,
    )

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            PodStatus(
                batteryParams = batteryParams,
                wearStatus = wearStatus,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            AncSwitch(
                ancStatus = ancMode,
                onAncModeChange = onAncModeChange,
                smartAncLevel = smartAncLevel,
                huaweiAncLevel = huaweiAncLevel,
                onHuaweiAncLevelChange = onHuaweiAncLevelChange,
                adaptiveModeEnabled = adaptiveModeEnabled,
                simpleMode = simpleAncMode,
                transparencyVocalEnhancement = transparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange
            )
        }
    }

    if (gestureControlEnabled) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                GestureDropdownPreference(
                    title = stringResource(R.string.gesture_left_double_tap),
                    selectedAction = leftGestureAction,
                    onActionChange = { onGestureActionChange(HuaweiGestureSide.LEFT, it) },
                )
                GestureDropdownPreference(
                    title = stringResource(R.string.gesture_right_double_tap),
                    selectedAction = rightGestureAction,
                    onActionChange = { onGestureActionChange(HuaweiGestureSide.RIGHT, it) },
                )
            }
        }
    }

    if (!simpleAncMode) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                SwitchPreference(
                    title = stringResource(R.string.game_mode),
                    summary = stringResource(R.string.game_mode_summary),
                    checked = gameMode,
                    onCheckedChange = onGameModeChange
                )
                if (spatialAudioSupported) {
                    val spatialAudioOptions = listOf(
                        stringResource(R.string.off),
                        stringResource(R.string.spatial_audio_fixed),
                        stringResource(R.string.spatial_audio_head_tracking),
                    )
                    OverlayDropdownPreference(
                        title = stringResource(R.string.spatial_audio),
                        summary = stringResource(R.string.spatial_audio_summary),
                        items = spatialAudioOptions,
                        selectedIndex = spatialAudioValues.indexOf(spatialAudioMode).coerceAtLeast(0),
                        onSelectedIndexChange = { onSpatialAudioModeChange(spatialAudioValues[it]) }
                    )
                }
                if (spatialSoundSupported) {
                    SwitchPreference(
                        title = stringResource(R.string.spatial_sound),
                        summary = stringResource(if (spatialAudioMode != ConfigManager.SPATIAL_AUDIO_OFF) R.string.enabled else R.string.off),
                        checked = spatialAudioMode != ConfigManager.SPATIAL_AUDIO_OFF,
                        onCheckedChange = {
                            onSpatialAudioModeChange(if (it) ConfigManager.SPATIAL_AUDIO_FIXED else ConfigManager.SPATIAL_AUDIO_OFF)
                        }
                    )
                }
                val eqOptions = listOf(
                    stringResource(R.string.eq_preset_authentic),
                    stringResource(R.string.eq_preset_detail),
                    stringResource(R.string.eq_preset_vocal),
                    stringResource(R.string.eq_preset_bass),
                    stringResource(R.string.eq_preset_dynaudio),
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.eq_preset_title),
                    summary = stringResource(R.string.eq_preset_summary),
                    items = eqOptions,
                    selectedIndex = EqPreset.ALL.indexOf(eqPreset).coerceAtLeast(0),
                    onSelectedIndexChange = { onEqPresetChange(EqPreset.ALL[it]) }
                )
                SwitchPreference(
                    title = stringResource(R.string.dual_device_connection),
                    summary = stringResource(if (dualDeviceConnection) R.string.enabled else R.string.off),
                    checked = dualDeviceConnection,
                    onCheckedChange = onDualDeviceConnectionChange
                )
            }
        }
    }
    item {
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(bottomContentPadding))
    }
}
@Composable
private fun GestureDropdownPreference(
    title: String,
    selectedAction: HuaweiGestureAction,
    onActionChange: (HuaweiGestureAction) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val actions = HuaweiGestureAction.all
    val selectedLabel = stringResource(selectedAction.labelRes())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Text(
            text = selectedLabel,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    OverlayDialog(
        title = title,
        summary = selectedLabel,
        show = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions.forEach { action ->
                val label = stringResource(action.labelRes())
                GestureActionRow(
                    label = label,
                    selected = action == selectedAction,
                    onClick = {
                        showDialog = false
                        if (action != selectedAction) {
                            onActionChange(action)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GestureActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = ToggleableState(selected),
            onClick = onClick,
        )
    }
}

private fun HuaweiGestureAction.labelRes(): Int = when (this) {
    HuaweiGestureAction.PLAY_NEXT -> R.string.gesture_action_play_next
    HuaweiGestureAction.PLAY_PAUSE -> R.string.gesture_action_play_pause
    HuaweiGestureAction.VOICE_ASSISTANT -> R.string.gesture_action_voice_assistant
    HuaweiGestureAction.NOISE_CANCELLATION -> R.string.gesture_action_noise_cancellation
    HuaweiGestureAction.NONE -> R.string.gesture_action_none
}

private fun readGesturePreference(
    prefs: SharedPreferences,
    address: String,
    side: HuaweiGestureSide,
): HuaweiGestureAction {
    val defaultAction = defaultGestureAction(side)
    val value = prefs.getInt(gesturePrefKey(address, side), defaultAction.protocolValue)
    return HuaweiGestureAction.fromProtocolValue(value) ?: defaultAction
}

private fun writeGesturePreference(
    prefs: SharedPreferences,
    address: String,
    side: HuaweiGestureSide,
    action: HuaweiGestureAction,
) {
    prefs.edit()
        .putInt(gesturePrefKey(address, side), action.protocolValue)
        .apply()
}

private fun defaultGestureAction(side: HuaweiGestureSide): HuaweiGestureAction = when (side) {
    HuaweiGestureSide.LEFT -> HuaweiGestureAction.NOISE_CANCELLATION
    HuaweiGestureSide.RIGHT -> HuaweiGestureAction.PLAY_PAUSE
}

private fun gesturePrefKey(address: String, side: HuaweiGestureSide): String {
    val normalizedAddress = address.ifBlank { "unknown" }.uppercase()
    return "huawei_gesture_${normalizedAddress}_${side.extraValue}"
}

@SuppressLint("MissingPermission")
private fun Context.sendHuaweiGestureSetCommand(
    address: String,
    side: HuaweiGestureSide,
    action: HuaweiGestureAction,
    onComplete: (Boolean) -> Unit,
) {
    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
        Log.w(GESTURE_TAG, "gesture skipped: invalid address=$address side=${side.extraValue} action=${action.extraValue}")
        onComplete(false)
        return
    }
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: run {
        Log.w(GESTURE_TAG, "gesture skipped: bluetooth adapter null address=$address")
        onComplete(false)
        return
    }
    val device = adapter.getRemoteDevice(address)
    Log.i(GESTURE_TAG, "gesture dispatch address=$address side=${side.extraValue} action=${action.extraValue}")
    HuaweiGestureController.setDoubleTap(this, device, side, action, onComplete)
}
