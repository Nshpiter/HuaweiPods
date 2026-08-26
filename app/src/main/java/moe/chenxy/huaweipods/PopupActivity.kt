package moe.chenxy.huaweipods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.UNKNOWN_HUAWEI_ANC_SUBMODE
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.defaultAncSubMode
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.hasChargingCase
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.isKnown
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncDirectionDial
import moe.chenxy.huaweipods.pods.supportsAncStateReadback
import moe.chenxy.huaweipods.pods.supportsAncSubMode
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.ui.AppLocale
import moe.chenxy.huaweipods.ui.AppTheme
import moe.chenxy.huaweipods.ui.components.AncSwitch
import moe.chenxy.huaweipods.ui.components.PodStatus
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class PopupActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        AppLocale.rememberDeviceLocale(newBase)
        AppLocale.apply(newBase, newBase.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE).getInt("app_language", AppLocale.SYSTEM))
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val appConfig = ConfigManager.refreshFromPrefs(prefs)
        val bluetoothDevice = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
        DeviceRoutePrefs.syncWithRemote(prefs, HuaweiPodsApp.xposedService)
        LowLatencyPrefs.syncWithRemote(prefs, HuaweiPodsApp.xposedService)
        val popupTarget = bluetoothDevice?.let { resolvePopupDeviceTarget(it, prefs) }
        if (popupTarget == null) {
            openModule()
            finish()
            return
        }
        if (appConfig.notificationClickAction != ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP) {
            openNotificationTarget(appConfig.notificationClickAction, bluetoothDevice)
            finish()
            return
        }

        setContent {
            val colorSchemeMode = when (prefs.getInt("theme_mode", 0)) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            AppTheme(colorSchemeMode = colorSchemeMode, accentMode = prefs.getInt("accent_mode", 0)) {
                PopupContent(
                    target = popupTarget,
                    onMore = {
                        val latestConfig = ConfigManager.refreshFromPrefs(prefs)
                        openMoreTarget(latestConfig.moreClickAction, bluetoothDevice)
                        finish()
                    },
                    onDone = { finish() }
                )
            }
        }
    }

    private fun openNotificationTarget(action: Int, bluetoothDevice: BluetoothDevice?) {
        when (action) {
            ConfigManager.NOTIFICATION_CLICK_SYSTEM_SETTINGS -> openSystemSettings(bluetoothDevice)
            ConfigManager.NOTIFICATION_CLICK_SMART_AUDIO -> openSmartAudioOrModule()
            else -> openModule()
        }
    }

    private fun openMoreTarget(action: Int, bluetoothDevice: BluetoothDevice?) {
        when (action) {
            ConfigManager.MORE_CLICK_SYSTEM_SETTINGS -> openSystemSettings(bluetoothDevice)
            ConfigManager.MORE_CLICK_SMART_AUDIO -> openSmartAudioOrModule()
            else -> openModule()
        }
    }

    private fun openModule() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openSmartAudioOrModule() {
        packageManager.getLaunchIntentForPackage("com.huawei.smartaudio")
            ?.let(::startActivity)
            ?: openModule()
    }

    @SuppressLint("MissingPermission")
    private fun openSystemSettings(bluetoothDevice: BluetoothDevice?) {
        if (bluetoothDevice == null) {
            openModule()
            return
        }
        val intent = Intent().apply {
            setClassName("com.android.settings", "com.android.settings.bluetooth.MiuiHeadsetActivity")
            putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            putExtra("bluetoothaddress", bluetoothDevice.address)
            putExtra("MIUI_HEADSET_SUPPORT", ConfigManager.fakeSupport())
            putExtra("COME_FROM", "MIUI_BLUETOOTH_SETTINGS")
            putExtra("DEVICE_ID", ConfigManager.fakeDeviceId())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure { openModule() }
    }

    private fun Intent.parcelableDevice(key: String): BluetoothDevice? {
        return getParcelableExtra(key, BluetoothDevice::class.java)
    }

    @SuppressLint("MissingPermission")
    private fun resolvePopupDeviceTarget(
        device: BluetoothDevice,
        prefs: android.content.SharedPreferences,
    ): PopupDeviceTarget? {
        val address = runCatching { device.address }.getOrNull()
        val deviceName = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
                ?: ""
        }.getOrDefault("")
        val route = DeviceRoutePrefs.resolve(
            prefs = prefs,
            address = address,
            deviceName = deviceName,
        )
        return popupDeviceTargetOrNull(address, deviceName, route)
    }
}
@Composable
private fun PopupContent(
    target: PopupDeviceTarget,
    onMore: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val terminalClosed = remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val themeMode = remember { prefs.getInt("theme_mode", 0) }
    val systemDark = isSystemInDarkTheme()
    val isDarkMode = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.UNKNOWN) }
    val ancLevel = remember(target.route) {
        mutableStateOf(target.route.defaultAncSubMode ?: UNKNOWN_HUAWEI_ANC_SUBMODE)
    }
    val hasAncLevel = remember { mutableStateOf(false) }
    val transparencySubMode = remember { mutableStateOf(-1) }
    val lowLatencyEnabled = remember(target.address, target.route) {
        mutableStateOf(
            LowLatencyPrefs.desiredOrNull(
                prefs,
                target.address,
                target.route,
            ) ?: false,
        )
    }

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val intent = p1 ?: return
                val identity = PopupBroadcastIdentity(
                    address = intent.getStringExtra("address"),
                    deviceName = intent.getStringExtra("device_name"),
                    route = decodeHuaweiDeviceRouteFromBroadcast(
                        intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
                    ),
                )
                if (!popupBroadcastMatchesTarget(target, identity)) return
                when (HuaweiPodsAction.canonical(intent.action)) {
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        val reportedMode = NoiseControlMode.fromBroadcastStatus(
                            intent.getIntExtra("status", NoiseControlMode.UNKNOWN.broadcastStatus),
                        )
                        ancMode.value = reportedMode
                        intent.getIntExtra("submode", -1)
                            .takeIf { intent.hasExtra("submode") && it >= 0 }
                            ?.let { subMode ->
                                when (reportedMode) {
                                    NoiseControlMode.NOISE_CANCELLATION ->
                                        if (target.route.supportsAncSubMode(subMode)) {
                                            ancLevel.value = subMode
                                            hasAncLevel.value = true
                                        }
                                    NoiseControlMode.TRANSPARENCY ->
                                        if (subMode in popupTransparencySubModes(target.route)) {
                                            transparencySubMode.value = subMode
                                        }
                                    NoiseControlMode.UNKNOWN,
                                    NoiseControlMode.OFF -> Unit
                                }
                            }
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        val level = intent.getIntExtra("level", ancLevel.value)
                        when {
                            target.route.supportsAncDirectionDial -> ancLevel.value = level.coerceIn(0, 8)
                            target.route.supportsDiscreteAncLevels && target.route.supportsAncSubMode(level) -> {
                                ancLevel.value = level
                                hasAncLevel.value = true
                            }
                        }
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        intent.getParcelableExtra("status", BatteryParams::class.java)?.let {
                            batteryParams.value = it
                        }
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED -> {
                        if (intent.hasExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED)) {
                            lowLatencyEnabled.value = intent.getBooleanExtra(
                                HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED,
                                lowLatencyEnabled.value,
                            )
                        }
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!terminalClosed.value && !showDialog.value) showDialog.value = true
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        terminalClosed.value = true
                        showDialog.value = false
                        onDone()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_UI_INIT).apply {
            putPopupTarget(target)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
            putPopupTarget(target)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })

        onDispose {
            try { context.unregisterReceiver(broadcastReceiver) } catch (_: Exception) {}
        }
    }

    // 界面可见时只高频读取 ANC；完整状态仍每 15 秒刷新，避免电量和图片查询挤占耳机通道。
    LaunchedEffect(Unit) {
        delay(500)
        if (!terminalClosed.value && !showDialog.value) showDialog.value = true

        var ancRefreshCount = 0
        while (!terminalClosed.value) {
            delay(2_500)
            if (terminalClosed.value) break
            if (target.route.supportsAncStateReadback) {
                context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_REFRESH).apply {
                    putPopupTarget(target)
                    setPackage("com.android.bluetooth")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            }
            ancRefreshCount += 1
            if (ancRefreshCount % 6 == 0) {
                context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                    putPopupTarget(target)
                    setPackage("com.android.bluetooth")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            }
        }
    }

    fun setAncMode(mode: NoiseControlMode) {
        if (!mode.isKnown()) return
        val route = target.route
        if (!route.supportsAnc) return
        val targetMode = if (mode == NoiseControlMode.TRANSPARENCY && !route.supportsTransparency) {
            NoiseControlMode.OFF
        } else {
            mode
        }
        ancMode.value = targetMode
        Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            putPopupTarget(target)
            putExtra("status", targetMode.broadcastStatus)
            if (route.supportsDiscreteAncLevels || targetMode == NoiseControlMode.TRANSPARENCY && route.supportsTransparency) {
                val subMode = when (targetMode) {
                    NoiseControlMode.NOISE_CANCELLATION ->
                        ancLevel.value.takeIf {
                            hasAncLevel.value && route.supportsAncSubMode(it)
                        }
                    NoiseControlMode.TRANSPARENCY -> {
                        transparencySubMode.value
                            .takeIf { it in popupTransparencySubModes(route) }
                    }
                    NoiseControlMode.UNKNOWN,
                    NoiseControlMode.OFF -> null
                }
                subMode?.let { putExtra("submode", it) }
            }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun setAncLevel(level: Int) {
        val route = target.route
        if (!route.supportsAnc) return
        val safeLevel = when {
            route.supportsAncDirectionDial -> level.coerceIn(0, 8)
            route.supportsDiscreteAncLevels && ancMode.value == NoiseControlMode.NOISE_CANCELLATION ->
                level.takeIf(route::supportsAncSubMode) ?: return
            route.supportsTransparency && ancMode.value == NoiseControlMode.TRANSPARENCY ->
                level.takeIf { it in popupTransparencySubModes(route) } ?: return
            else -> return
        }
        if (ancMode.value == NoiseControlMode.TRANSPARENCY) {
            transparencySubMode.value = safeLevel
        } else {
            ancLevel.value = safeLevel
            hasAncLevel.value = true
        }
        Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET).apply {
            putPopupTarget(target)
            putExtra("level", safeLevel)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun setLowLatency(enabled: Boolean) {
        if (!target.route.supportsLowLatencyControl) return
        lowLatencyEnabled.value = enabled
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_SET).apply {
            putPopupTarget(target)
            putExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED, enabled)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    val deviceRoute = target.route
    LaunchedEffect(deviceRoute) {
        if (ancMode.value == NoiseControlMode.UNKNOWN && !deviceRoute.supportsAncStateReadback) {
            ancMode.value = NoiseControlMode.OFF
        }
    }
    val ancLevelChange = if (
        deviceRoute.supportsAncDirectionDial ||
            deviceRoute.supportsDiscreteAncLevels ||
            deviceRoute.supportsTransparency
    ) {
        ::setAncLevel
    } else {
        null
    }
    val showAnc = deviceRoute.supportsAnc
    val showLowLatency = deviceRoute.supportsLowLatencyControl


    val dialogBgColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF7F7F7)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(containerColor = Color.Transparent) { _ ->
        OverlayDialog(
            title = target.deviceName.ifEmpty { stringResource(R.string.app_name) },
            show = showDialog.value,
            backgroundColor = dialogBgColor,
            onDismissRequest = {
                showDialog.value = false
            },
            onDismissFinished = {
                onDone()
            }
        ) {
            if (isLandscape) {
                LandscapePopupBody(
                    batteryParams = batteryParams.value,
                    ancMode = ancMode.value,
                    ancLevel = if (ancMode.value == NoiseControlMode.TRANSPARENCY) {
                        transparencySubMode.value
                            .takeIf { it in popupTransparencySubModes(deviceRoute) }
                            ?: popupDefaultTransparencySubMode(deviceRoute)
                    } else {
                        ancLevel.value
                    },
                    onAncModeChange = ::setAncMode,
                    onAncLevelChange = ancLevelChange,
                    deviceRoute = deviceRoute,
                    showAnc = showAnc,
                    showLowLatency = showLowLatency,
                    lowLatencyEnabled = lowLatencyEnabled.value,
                    onLowLatencyChange = ::setLowLatency,
                    onMore = onMore,
                    onDone = { showDialog.value = false },
                )
            } else {
                PortraitPopupBody(
                    batteryParams = batteryParams.value,
                    ancMode = ancMode.value,
                    ancLevel = if (ancMode.value == NoiseControlMode.TRANSPARENCY) {
                        transparencySubMode.value
                            .takeIf { it in popupTransparencySubModes(deviceRoute) }
                            ?: popupDefaultTransparencySubMode(deviceRoute)
                    } else {
                        ancLevel.value
                    },
                    onAncModeChange = ::setAncMode,
                    onAncLevelChange = ancLevelChange,
                    deviceRoute = deviceRoute,
                    showAnc = showAnc,
                    showLowLatency = showLowLatency,
                    lowLatencyEnabled = lowLatencyEnabled.value,
                    onLowLatencyChange = ::setLowLatency,
                    onMore = onMore,
                    onDone = { showDialog.value = false },
                )
            }
        }
    }
}

@Composable
private fun PortraitPopupBody(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    ancLevel: Int,
    onAncModeChange: (NoiseControlMode) -> Unit,
    onAncLevelChange: ((Int) -> Unit)?,
    deviceRoute: HuaweiDeviceRoute,
    showAnc: Boolean,
    showLowLatency: Boolean,
    lowLatencyEnabled: Boolean,
    onLowLatencyChange: (Boolean) -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            PodStatus(
                batteryParams = batteryParams,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                showCase = !deviceRoute.isSupported || deviceRoute.hasChargingCase
            )
        }
        if (showAnc) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                AncSwitch(
                    ancStatus = ancMode,
                    onAncModeChange = onAncModeChange,
                    deviceRoute = deviceRoute,
                    huaweiAncLevel = ancLevel,
                    onHuaweiAncLevelChange = onAncLevelChange,
                )
            }
        }
        if (showLowLatency) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.low_latency_mode),
                    summary = stringResource(R.string.low_latency_auto_apply_summary),
                    checked = lowLatencyEnabled,
                    onCheckedChange = onLowLatencyChange,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = stringResource(R.string.more),
                onClick = onMore,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LandscapePopupBody(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    ancLevel: Int,
    onAncModeChange: (NoiseControlMode) -> Unit,
    onAncLevelChange: ((Int) -> Unit)?,
    deviceRoute: HuaweiDeviceRoute,
    showAnc: Boolean,
    showLowLatency: Boolean,
    lowLatencyEnabled: Boolean,
    onLowLatencyChange: (Boolean) -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 560.dp)
            .height(240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                PodStatus(
                    batteryParams = batteryParams,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    compact = true,
                    showCase = !deviceRoute.isSupported || deviceRoute.hasChargingCase
                )
            }
            if (showAnc) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    AncSwitch(
                        ancStatus = ancMode,
                        onAncModeChange = onAncModeChange,
                        deviceRoute = deviceRoute,
                        huaweiAncLevel = ancLevel,
                        onHuaweiAncLevelChange = onAncLevelChange,
                        compact = true,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            if (showLowLatency) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.low_latency_mode),
                        summary = stringResource(R.string.low_latency_auto_apply_summary),
                        checked = lowLatencyEnabled,
                        onCheckedChange = onLowLatencyChange,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            TextButton(
                text = stringResource(R.string.more),
                onClick = onMore,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun popupTransparencySubModes(route: HuaweiDeviceRoute): Set<Int> =
    if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) setOf(0x01, 0x02) else setOf(0x01, 0xFF)

private fun popupDefaultTransparencySubMode(route: HuaweiDeviceRoute): Int =
    if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) 0x02 else 0xFF

internal data class PopupDeviceTarget(
    val address: String,
    val deviceName: String,
    val route: HuaweiDeviceRoute,
)

internal data class PopupBroadcastIdentity(
    val address: String?,
    val deviceName: String?,
    val route: HuaweiDeviceRoute?,
)

internal fun popupDeviceTargetOrNull(
    address: String?,
    deviceName: String?,
    route: HuaweiDeviceRoute,
): PopupDeviceTarget? {
    val normalizedAddress = address?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!route.isSupported) return null
    return PopupDeviceTarget(
        address = normalizedAddress,
        deviceName = deviceName?.trim().orEmpty(),
        route = route,
    )
}

internal fun popupBroadcastMatchesTarget(
    target: PopupDeviceTarget,
    received: PopupBroadcastIdentity,
): Boolean {
    val receivedAddress = received.address?.trim()?.takeIf(String::isNotEmpty) ?: return false
    return receivedAddress.equals(target.address, ignoreCase = true) &&
        received.route == target.route
}

private fun Intent.putPopupTarget(target: PopupDeviceTarget) {
    putExtra("address", target.address)
    putExtra("device_name", target.deviceName)
    encodeHuaweiDeviceRouteForBroadcast(target.route)?.let {
        putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
    }
}
