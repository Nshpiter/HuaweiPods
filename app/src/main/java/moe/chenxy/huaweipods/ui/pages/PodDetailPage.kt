package moe.chenxy.huaweipods.ui.pages

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
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
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.HuaweiGestureAction
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.ui.components.AncSwitch
import moe.chenxy.huaweipods.ui.components.FreeBuds5Controls
import moe.chenxy.huaweipods.ui.components.FreeBuds4eControls
import moe.chenxy.huaweipods.ui.components.FreeBuds6iControls
import moe.chenxy.huaweipods.ui.components.FreeBuds7iControls
import moe.chenxy.huaweipods.ui.components.FreeBudsPro5Controls
import moe.chenxy.huaweipods.ui.components.FreeClip2Controls
import moe.chenxy.huaweipods.ui.components.FreeArcControls
import moe.chenxy.huaweipods.ui.components.HuaweiGestureControls
import moe.chenxy.huaweipods.ui.components.LowLatencyControl
import moe.chenxy.huaweipods.ui.components.PodStatus
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.hasChargingCase
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncDirectionDial
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsGestureConfiguration
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.pods.supportsTransparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GESTURE_TAG = "HuaweiPods-Gesture"
private const val POD_DETAIL_IMAGE_MAX_PIXELS = 1024

@Composable
fun PodDetailPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomContentPadding: Dp = 16.dp,
    podName: String,
    connectedDeviceAddress: String = "",
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    huaweiAncLevel: Int = 0,
    onHuaweiAncLevelChange: (Int) -> Unit = {},
    boxImagePath: String? = null,
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gesturePrefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val deviceRoute = remember(podName, connectedDeviceAddress) {
        DeviceRoutePrefs.resolve(gesturePrefs, connectedDeviceAddress, podName)
    }
    val gestureControlEnabled = remember(podName, connectedDeviceAddress) {
        deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS3 &&
            BluetoothAdapter.checkBluetoothAddress(connectedDeviceAddress)
    }
    val ancLevelChange = onHuaweiAncLevelChange.takeIf {
        deviceRoute.supportsAncDirectionDial ||
            deviceRoute.supportsDiscreteAncLevels ||
            deviceRoute.supportsTransparency
    }
    var leftGestureAction by remember(connectedDeviceAddress) {
        mutableStateOf(readGesturePreference(gesturePrefs, connectedDeviceAddress, HuaweiGestureSide.LEFT))
    }
    var rightGestureAction by remember(connectedDeviceAddress) {
        mutableStateOf(readGesturePreference(gesturePrefs, connectedDeviceAddress, HuaweiGestureSide.RIGHT))
    }

    fun setGestureAction(side: HuaweiGestureSide, action: HuaweiGestureAction) {
        context.sendHuaweiGestureSetCommand(
            connectedDeviceAddress,
            deviceRoute,
            side,
            action,
        ) { success ->
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
                    painter = rememberPodImagePainter(boxImagePath, deviceRoute),
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
                    ancMode = ancMode,
                    onAncModeChange = onAncModeChange,
                    huaweiAncLevel = huaweiAncLevel,
                    onHuaweiAncLevelChange = ancLevelChange,
                    deviceRoute = deviceRoute,
                    connectedDeviceAddress = connectedDeviceAddress,
                    gestureControlEnabled = gestureControlEnabled,
                    leftGestureAction = leftGestureAction,
                    rightGestureAction = rightGestureAction,
                    onGestureActionChange = ::setGestureAction,
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
                painter = rememberPodImagePainter(boxImagePath, deviceRoute),
                contentDescription = "Earphones",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        podControlItems(
            batteryParams = batteryParams,
            ancMode = ancMode,
            onAncModeChange = onAncModeChange,
            huaweiAncLevel = huaweiAncLevel,
            onHuaweiAncLevelChange = ancLevelChange,
            deviceRoute = deviceRoute,
            connectedDeviceAddress = connectedDeviceAddress,
            gestureControlEnabled = gestureControlEnabled,
            leftGestureAction = leftGestureAction,
            rightGestureAction = rightGestureAction,
            onGestureActionChange = ::setGestureAction,
            bottomContentPadding = bottomContentPadding
        )
    }
}
@Composable
private fun rememberPodImagePainter(path: String?, route: HuaweiDeviceRoute): Painter {
    val fallback = painterResource(
        when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> R.drawable.img_freebuds5_box
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> R.drawable.img_freebuds6i_box
        HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> R.drawable.img_freeclip2_box
        HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> R.drawable.img_eyewear2_box
        else -> R.drawable.img_box
        },
    )
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            path?.let(::decodePodDetailImage)
        }
    }
    return bitmap?.let { loaded ->
        remember(loaded) { BitmapPainter(loaded.asImageBitmap()) }
    } ?: fallback
}

private fun decodePodDetailImage(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > POD_DETAIL_IMAGE_MAX_PIXELS ||
        bounds.outHeight / sampleSize > POD_DETAIL_IMAGE_MAX_PIXELS
    ) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}.getOrNull()

private fun LazyListScope.podControlItems(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    huaweiAncLevel: Int,
    onHuaweiAncLevelChange: ((Int) -> Unit)?,
    deviceRoute: HuaweiDeviceRoute,
    connectedDeviceAddress: String,
    gestureControlEnabled: Boolean,
    leftGestureAction: HuaweiGestureAction,
    rightGestureAction: HuaweiGestureAction,
    onGestureActionChange: (HuaweiGestureSide, HuaweiGestureAction) -> Unit,
    bottomContentPadding: Dp
) {
    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            PodStatus(
                batteryParams = batteryParams,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                showCase = !deviceRoute.isSupported || deviceRoute.hasChargingCase
            )
        }
    }

    if (deviceRoute.supportsAnc) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                AncSwitch(
                    ancStatus = ancMode,
                    onAncModeChange = onAncModeChange,
                    huaweiAncLevel = huaweiAncLevel,
                    onHuaweiAncLevelChange = onHuaweiAncLevelChange,
                    deviceRoute = deviceRoute,
                )
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeClip2Controls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS5 ||
        deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS5I
    ) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeBuds5Controls(
                    address = connectedDeviceAddress,
                    route = deviceRoute,
                )
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEARC) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeArcControls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS7I) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeBuds7iControls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeBudsPro5Controls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS4E) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeBuds4eControls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                FreeBuds6iControls(address = connectedDeviceAddress)
            }
        }
    }

    if (deviceRoute.supportsLowLatencyControl &&
        deviceRoute != HuaweiDeviceRoute.HUAWEI_FREEBUDS5 &&
        deviceRoute != HuaweiDeviceRoute.HUAWEI_FREEBUDS5I &&
        deviceRoute != HuaweiDeviceRoute.HUAWEI_FREECLIP2 &&
        deviceRoute != HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
    ) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                LowLatencyControl(
                    address = connectedDeviceAddress,
                    route = deviceRoute,
                )
            }
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

    if (deviceRoute.supportsGestureConfiguration && !gestureControlEnabled) {
        item {
            HuaweiGestureControls(
                route = deviceRoute,
                address = connectedDeviceAddress,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
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
    route: HuaweiDeviceRoute,
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
    HuaweiGestureController.setDoubleTap(this, device, route, side, action, onComplete)
}
