package moe.chenxy.huaweipods.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.AppLifecyclePrefs
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageChangeNotifier
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.UNKNOWN_HUAWEI_ANC_SUBMODE
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.defaultAncSubMode
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.isKnown
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncDirectionDial
import moe.chenxy.huaweipods.pods.supportsAncStateReadback
import moe.chenxy.huaweipods.pods.supportsAncSubMode
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.ui.dialogs.AvailableUpdateDialog
import moe.chenxy.huaweipods.ui.dialogs.UpdatedAppDialog
import moe.chenxy.huaweipods.ui.pages.AboutPage
import moe.chenxy.huaweipods.ui.pages.DocumentationPage
import moe.chenxy.huaweipods.ui.pages.SponsorPage
import moe.chenxy.huaweipods.ui.pages.ThemeSettingsPage
import moe.chenxy.huaweipods.ui.pages.UpdateCheckSummary
import moe.chenxy.huaweipods.update.GitHubRelease
import moe.chenxy.huaweipods.update.GitHubReleaseChecker
import moe.chenxy.huaweipods.update.PendingUpdateStore
import moe.chenxy.huaweipods.update.UpdateCheckFeedbackGate
import moe.chenxy.huaweipods.update.shouldShowAvailableUpdateDialog
import moe.chenxy.huaweipods.update.UpdateCheckResult
import moe.chenxy.huaweipods.utils.RootManager
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import moe.chenxy.huaweipods.BuildConfig

sealed interface Screen : NavKey {
    data object Main : Screen
    data object About : Screen
    data object Theme : Screen
    data object Documentation : Screen
    data object Sponsor : Screen
}

private const val DEVICE_CONNECT_TIMEOUT_MS = 15_000L
private const val GITHUB_REPOSITORY_URL = "https://github.com/Nshpiter/HuaweiPods"
private const val GITHUB_ISSUES_URL = "$GITHUB_REPOSITORY_URL/issues"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainUI(
    backStack: SnapshotStateList<Screen>,
    showUpdatedDialogOnLaunch: Boolean = false,
    onUpdatedDialogHandled: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    accentMode: MutableState<Int> = mutableStateOf(0),
    onAccentModeChange: (Int) -> Unit = {},
    floatingBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    blurBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onBlurBottomBarChange: (Boolean) -> Unit = {},
    appLanguage: MutableState<Int> = mutableStateOf(AppLocale.SYSTEM),
    onAppLanguageChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val qqGroupNumber = stringResource(R.string.qq_group_number)

    val mainTitle = remember { mutableStateOf("") }
    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.UNKNOWN) }
    val huaweiAncLevel = remember { mutableStateOf(UNKNOWN_HUAWEI_ANC_SUBMODE) }
    val hasHuaweiAncLevel = remember { mutableStateOf(false) }
    val huaweiTransparencySubMode = remember { mutableStateOf(-1) }
    val hookConnected = remember { mutableStateOf(false) }
    val tabs = remember { MainTab.entries.toList() }
    var selectedTab by remember { mutableStateOf(MainTab.Module) }
    var hasAppliedDefaultTab by remember { mutableStateOf(false) }
    var bluetoothState by remember { mutableStateOf(readBluetoothState(context)) }
    var xposedService by remember { mutableStateOf(HuaweiPodsApp.xposedService) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showRestartScopeDialog by remember { mutableStateOf(false) }
    var restartRequestedByUpdate by remember { mutableStateOf(false) }
    var restartingScopes by remember { mutableStateOf(false) }
    var connectingDeviceAddress by remember { mutableStateOf<String?>(null) }
    var connectedDeviceAddress by remember { mutableStateOf("") }
    var showConnectErrorDialog by remember { mutableStateOf(false) }
    var hookConnectionState by remember { mutableStateOf("disconnected") }
    var pendingOpenEarphonesAfterPickerLoaded by remember { mutableStateOf(false) }
    var lastBluetoothServiceAliveMs by remember { mutableStateOf(0L) }
    var lastMiBluetoothServiceAliveMs by remember { mutableStateOf(0L) }
    var bluetoothServiceResponsive by remember { mutableStateOf(false) }
    val backgroundColor = appBackground()
    val overlayBottomBar = floatingBottomBar.value || blurBottomBar.value
    val pageBottomContentPadding = if (overlayBottomBar) 104.dp else 28.dp
    val backdrop = if (blurBottomBar.value) {
        rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
    } else {
        null
    }

    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val lifecyclePrefs = remember(context) { AppLifecyclePrefs(context) }
    val appConfig = remember { ConfigManager.refreshFromPrefs(prefs) }
    val milinkLowLatencyCardEnabled = remember {
        mutableStateOf(appConfig.milinkLowLatencyCardEnabled)
    }
    val notificationClickAction = remember { mutableStateOf(appConfig.notificationClickAction) }
    val moreClickAction = remember { mutableStateOf(appConfig.moreClickAction) }
    val desktopIconHidden = remember { mutableStateOf(isLauncherIconHidden(context)) }
    val logLevel = remember { mutableStateOf(appConfig.logLevel) }
    val fakeDeviceId = remember { mutableStateOf(appConfig.fakeDeviceId) }
    val islandMode = remember { mutableStateOf(ConfigManager.islandMode()) }
    val persistentNotificationEnabled = remember {
        mutableStateOf(appConfig.persistentNotificationEnabled)
    }
    val lockscreenNotificationEnabled = remember {
        mutableStateOf(appConfig.lockscreenNotificationEnabled)
    }
    val earphonePrefs = remember { mutableStateOf(PodImagePrefs.load(prefs)) }
    val checkUpdatesOnLaunch = remember {
        mutableStateOf(lifecyclePrefs.checkUpdatesOnLaunch())
    }
    val updatePreviewChangelog = stringResource(R.string.update_preview_changelog)
    val pendingUpdateStore = remember(context) { PendingUpdateStore(context) }
    var checkingForUpdates by remember { mutableStateOf(false) }
    val updateFeedbackGate = remember { UpdateCheckFeedbackGate() }
    var updateCheckSummary by remember { mutableStateOf<UpdateCheckSummary?>(null) }
    var availableUpdate by remember {
        mutableStateOf(
            pendingUpdateStore.restore(
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                allowPreview = BuildConfig.DEBUG,
            ),
        )
    }
    var previewUpdate by remember { mutableStateOf<GitHubRelease?>(null) }

    fun currentDeviceRoute(): HuaweiDeviceRoute = DeviceRoutePrefs.resolve(
        prefs = prefs,
        address = connectedDeviceAddress,
        deviceName = mainTitle.value,
    )

    fun checkForUpdates(manual: Boolean) {
        if (manual) {
            updateFeedbackGate.request()
            updateCheckSummary = null
        }
        if (checkingForUpdates) return
        Log.i(
            "HuaweiPods-Update",
            "Update check started: manual=$manual current=${BuildConfig.VERSION_CODE}-${BuildConfig.VERSION_NAME}",
        )
        checkingForUpdates = true
        coroutineScope.launch {
            try {
                when (
                    val result = GitHubReleaseChecker.check(
                        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        currentVersionName = BuildConfig.VERSION_NAME,
                    )
                ) {
                    is UpdateCheckResult.Available -> {
                        Log.i(
                            "HuaweiPods-Update",
                            "Update available: ${result.release.tag}",
                        )
                        if (updateFeedbackGate.shouldShow(manual)) {
                            updateCheckSummary = UpdateCheckSummary.Available(
                                versionName = result.release.versionName,
                            )
                        }
                        if (!pendingUpdateStore.save(result.release)) {
                            Log.w("HuaweiPods-Update", "Unable to persist pending update ${result.release.tag}")
                        }
                        availableUpdate = result.release
                    }

                    is UpdateCheckResult.UpToDate -> {
                        pendingUpdateStore.clear()
                        availableUpdate = null
                        lifecyclePrefs.markUpdateCheck(System.currentTimeMillis())
                        Log.i(
                            "HuaweiPods-Update",
                            "Already up to date: ${result.latest.tag}",
                        )
                        if (updateFeedbackGate.shouldShow(manual)) {
                            updateCheckSummary = UpdateCheckSummary.UpToDate(
                                versionName = result.latest.versionName,
                            )
                            Toast.makeText(context, R.string.already_latest_version, Toast.LENGTH_SHORT).show()
                        }
                    }

                    is UpdateCheckResult.Failure -> {
                        Log.w(
                            "HuaweiPods-Update",
                            "Update check failed: status=${result.statusCode} ${result.message}",
                        )
                        if (updateFeedbackGate.shouldShow(manual)) {
                            updateCheckSummary = UpdateCheckSummary.Failure
                            Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } finally {
                checkingForUpdates = false
                updateFeedbackGate.reset()
            }
        }
    }

    fun copyQqGroup() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("HuaweiPods QQ", qqGroupNumber))
        Toast.makeText(context, R.string.qq_group_copied, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(showUpdatedDialogOnLaunch) {
        if (!showUpdatedDialogOnLaunch && lifecyclePrefs.shouldRunAutomaticCheck()) {
            checkForUpdates(manual = false)
        }
    }

    val connectedAddressValid = BluetoothAdapter.checkBluetoothAddress(connectedDeviceAddress)
    val canShowDetailPage = hookConnected.value && connectedAddressValid
    val showEarphoneDetail = canShowDetailPage && !showDevicePicker
    val displayBattery = batteryParams.value
    val displayAnc = ancMode.value
    val displayTitle = mainTitle.value.takeIf { it.isNotBlank() && hookConnected.value } ?: mainTitle.value

    LaunchedEffect(xposedService) {
        val service = xposedService ?: return@LaunchedEffect
        DeviceRoutePrefs.syncWithRemote(prefs, service)
        val pendingAddress = connectingDeviceAddress
            ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
            ?: return@LaunchedEffect
        val pendingDevice = context
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.getRemoteDevice(pendingAddress)
            ?: return@LaunchedEffect
        Intent(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST).apply {
            putExtra("device", pendingDevice)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    LaunchedEffect(displayTitle) {
        if (displayTitle.isNotEmpty()) {
            mainTitle.value = displayTitle
        }
    }

    LaunchedEffect(canShowDetailPage) {
        if (!hasAppliedDefaultTab) {
            selectedTab = if (canShowDetailPage) MainTab.Earphones else MainTab.Module
            hasAppliedDefaultTab = true
        }
    }

    LaunchedEffect(hookConnectionState) {
        if (hookConnectionState == "error") {
            connectingDeviceAddress = null
            pendingOpenEarphonesAfterPickerLoaded = false
            showConnectErrorDialog = true
            showDevicePicker = true
        }
    }

    LaunchedEffect(connectingDeviceAddress) {
        val requestedAddress = connectingDeviceAddress ?: return@LaunchedEffect
        delay(DEVICE_CONNECT_TIMEOUT_MS)
        if (connectingDeviceAddress == requestedAddress) {
            hookConnectionState = "error"
        }
    }

    LaunchedEffect(pendingOpenEarphonesAfterPickerLoaded, connectingDeviceAddress, hookConnected.value) {
        if (pendingOpenEarphonesAfterPickerLoaded && connectingDeviceAddress == null && hookConnected.value) {
            withFrameNanos { }
            pendingOpenEarphonesAfterPickerLoaded = false
            showDevicePicker = false
        }
    }

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val intent = p1 ?: return
                when (HuaweiPodsAction.canonical(intent.action)) {
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        connectedDeviceAddress = intent.getStringExtra("address") ?: connectedDeviceAddress
                        val route = currentDeviceRoute()
                        val reportedMode = NoiseControlMode.fromBroadcastStatus(
                            intent.getIntExtra("status", NoiseControlMode.UNKNOWN.broadcastStatus),
                        )
                        ancMode.value = if (
                            reportedMode == NoiseControlMode.UNKNOWN && !route.supportsAncStateReadback
                        ) {
                            NoiseControlMode.OFF
                        } else {
                            reportedMode
                        }
                        intent.getIntExtra("submode", -1)
                            .takeIf { intent.hasExtra("submode") && it >= 0 }
                            ?.let { subMode ->
                                when (reportedMode) {
                                    NoiseControlMode.NOISE_CANCELLATION ->
                                        if (route.supportsAncSubMode(subMode)) {
                                            huaweiAncLevel.value = subMode
                                            hasHuaweiAncLevel.value = true
                                        }
                                    NoiseControlMode.TRANSPARENCY ->
                                        if (subMode in supportedTransparencySubModes(route)) {
                                            huaweiTransparencySubMode.value = subMode
                                        }
                                    NoiseControlMode.UNKNOWN,
                                    NoiseControlMode.OFF -> Unit
                                }
                            }
                    }

                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        val route = currentDeviceRoute()
                        val level = intent.getIntExtra("level", huaweiAncLevel.value)
                        when {
                            route.supportsAncDirectionDial -> huaweiAncLevel.value = level.coerceIn(0, 8)
                            route.supportsDiscreteAncLevels && route.supportsAncSubMode(level) -> {
                                huaweiAncLevel.value = level
                                hasHuaweiAncLevel.value = true
                            }
                        }
                    }

                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        connectedDeviceAddress = intent.getStringExtra("address") ?: connectedDeviceAddress
                        intent.parcelableBatteryStatus()?.let { batteryParams.value = it }
                    }

                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        val deviceName = intent.getStringExtra("device_name")
                        val shouldOpenEarphones = connectingDeviceAddress != null || !hasAppliedDefaultTab
                        val previousAddress = connectedDeviceAddress
                        val previousRoute = currentDeviceRoute()
                        connectedDeviceAddress = resolvedConnectedAddress(
                            intent.getStringExtra("address"),
                            connectingDeviceAddress,
                            connectedDeviceAddress,
                        )
                        connectingDeviceAddress = null
                        mainTitle.value = deviceName ?: ""
                        val route = decodeHuaweiDeviceRouteFromBroadcast(
                            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
                        ) ?: currentDeviceRoute()
                        if (
                            !previousAddress.equals(connectedDeviceAddress, ignoreCase = true) ||
                            previousRoute != route
                        ) {
                            ancMode.value = NoiseControlMode.UNKNOWN
                            huaweiAncLevel.value = route.defaultAncSubMode
                                ?: UNKNOWN_HUAWEI_ANC_SUBMODE
                            hasHuaweiAncLevel.value = false
                            huaweiTransparencySubMode.value = -1
                        }
                        earphonePrefs.value = PodImagePrefs.upsertConnected(
                            prefs = prefs,
                            service = xposedService,
                            address = connectedDeviceAddress,
                            name = deviceName.orEmpty(),
                        )
                        hookConnected.value = true
                        hookConnectionState = "connected"
                        if (shouldOpenEarphones) {
                            if (!hasAppliedDefaultTab) {
                                selectedTab = MainTab.Earphones
                            }
                            hasAppliedDefaultTab = true
                            pendingOpenEarphonesAfterPickerLoaded = true
                        }
                        Log.i("HuaweiPods", "pod connected via hook: $deviceName")
                    }

                    HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED -> {
                        hookConnectionState = intent.getStringExtra("state") ?: hookConnectionState
                        if (hookConnectionState == "disconnected") {
                            val route = currentDeviceRoute()
                            connectedDeviceAddress = ""
                            mainTitle.value = ""
                            batteryParams.value = BatteryParams()
                            ancMode.value = NoiseControlMode.UNKNOWN
                            huaweiAncLevel.value = route.defaultAncSubMode
                                ?: UNKNOWN_HUAWEI_ANC_SUBMODE
                            hasHuaweiAncLevel.value = false
                            huaweiTransparencySubMode.value = -1
                            hookConnected.value = false
                        } else if (hookConnectionState == "connecting") {
                            val incomingAddress = intent.getStringExtra("address")
                            if (!incomingAddress.isNullOrBlank() &&
                                !incomingAddress.equals(connectedDeviceAddress, ignoreCase = true)
                            ) {
                                val route = DeviceRoutePrefs.resolve(
                                    prefs = prefs,
                                    address = incomingAddress,
                                    deviceName = null,
                                )
                                connectedDeviceAddress = ""
                                mainTitle.value = ""
                                batteryParams.value = BatteryParams()
                                ancMode.value = NoiseControlMode.UNKNOWN
                                huaweiAncLevel.value = route.defaultAncSubMode
                                    ?: UNKNOWN_HUAWEI_ANC_SUBMODE
                                hasHuaweiAncLevel.value = false
                                huaweiTransparencySubMode.value = -1
                                hookConnected.value = false
                            }
                        } else if (hookConnected.value) {
                            connectedDeviceAddress = resolvedConnectedAddress(intent.getStringExtra("address"), connectingDeviceAddress, connectedDeviceAddress)
                            intent.getStringExtra("device_name")?.let {
                                mainTitle.value = it
                                earphonePrefs.value = PodImagePrefs.upsertConnected(prefs, xposedService, connectedDeviceAddress, it)
                            }
                        }
                    }

                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        val route = currentDeviceRoute()
                        mainTitle.value = ""
                        connectedDeviceAddress = ""
                        batteryParams.value = BatteryParams()
                        ancMode.value = NoiseControlMode.UNKNOWN
                        huaweiAncLevel.value = route.defaultAncSubMode
                            ?: UNKNOWN_HUAWEI_ANC_SUBMODE
                        hasHuaweiAncLevel.value = false
                        huaweiTransparencySubMode.value = -1
                        hookConnectionState = "disconnected"
                        hookConnected.value = false
                    }

                    HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE -> {
                        if (
                            intent.getStringExtra(HuaweiPodsAction.EXTRA_MODULE_BUILD_ID) ==
                            BuildConfig.MODULE_BUILD_ID
                        ) {
                            lastBluetoothServiceAliveMs = SystemClock.elapsedRealtime()
                            bluetoothServiceResponsive = true
                        }
                    }

                    HuaweiPodsAction.ACTION_MODULE_MI_BLUETOOTH_SERVICE_ALIVE -> {
                        if (
                            intent.getStringExtra(HuaweiPodsAction.EXTRA_MODULE_BUILD_ID) ==
                            BuildConfig.MODULE_BUILD_ID
                        ) {
                            lastMiBluetoothServiceAliveMs = SystemClock.elapsedRealtime()
                        }
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        bluetoothState = readBluetoothState(context)
                    }
                }
            }
        }
    }

    val podImagesChangedReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED) return
                earphonePrefs.value = PodImagePrefs.load(prefs)
            }
        }
    }

    DisposableEffect(Unit) {
        val serviceListener: (io.github.libxposed.service.XposedService?) -> Unit = { service ->
            xposedService = service
        }
        HuaweiPodsApp.addServiceListener(serviceListener)

        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_MODULE_MI_BLUETOOTH_SERVICE_ALIVE)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }, Context.RECEIVER_EXPORTED)
        context.registerReceiver(
            podImagesChangedReceiver,
            IntentFilter(HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )

        sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_PODS_UI_INIT)

        onDispose {
            sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_PODS_UI_CLOSED)
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (_: Exception) {}
            try {
                context.unregisterReceiver(podImagesChangedReceiver)
            } catch (_: Exception) {}
            HuaweiPodsApp.removeServiceListener(serviceListener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_PODS_UI_INIT)
            if (!restartingScopes) {
                sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_REFRESH_STATUS)
            }
            delay(if (hookConnected.value) 10_000L else 30_000L)
        }
    }

    LaunchedEffect(selectedTab, hookConnected.value) {
        sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_PODS_UI_INIT)
        if (!restartingScopes && (selectedTab == MainTab.Module || hookConnected.value)) {
            sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_REFRESH_STATUS)
        }
    }

    LaunchedEffect(lastBluetoothServiceAliveMs) {
        while (true) {
            bluetoothServiceResponsive = lastBluetoothServiceAliveMs > 0L &&
                    SystemClock.elapsedRealtime() - lastBluetoothServiceAliveMs <= 75_000L
            delay(5_000L)
        }
    }

    fun setAncMode(mode: NoiseControlMode) {
        if (!mode.isKnown()) return
        val route = currentDeviceRoute()
        if (!route.supportsAnc) return
        val normalizedMode = if (mode == NoiseControlMode.TRANSPARENCY && !route.supportsTransparency) {
            NoiseControlMode.OFF
        } else {
            mode
        }
        ancMode.value = normalizedMode
        Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("address", connectedDeviceAddress)
            putExtra("device_name", mainTitle.value)
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("status", normalizedMode.broadcastStatus)
            if (route.supportsDiscreteAncLevels || normalizedMode == NoiseControlMode.TRANSPARENCY && route.supportsTransparency) {
                val subMode = when (normalizedMode) {
                    NoiseControlMode.NOISE_CANCELLATION ->
                        huaweiAncLevel.value.takeIf {
                            hasHuaweiAncLevel.value && route.supportsAncSubMode(it)
                        }
                    NoiseControlMode.TRANSPARENCY -> {
                        huaweiTransparencySubMode.value
                            .takeIf { it in supportedTransparencySubModes(route) }
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


    fun setHuaweiAncLevel(level: Int) {
        val route = currentDeviceRoute()
        val safeLevel = when {
            route.supportsAncDirectionDial -> level.coerceIn(0, 8)
            route.supportsDiscreteAncLevels && ancMode.value == NoiseControlMode.NOISE_CANCELLATION ->
                level.takeIf(route::supportsAncSubMode) ?: return
            route.supportsTransparency && ancMode.value == NoiseControlMode.TRANSPARENCY ->
                level.takeIf { it in supportedTransparencySubModes(route) } ?: return
            else -> return
        }
        if (ancMode.value == NoiseControlMode.TRANSPARENCY) {
            huaweiTransparencySubMode.value = safeLevel
        } else {
            huaweiAncLevel.value = safeLevel
            hasHuaweiAncLevel.value = true
        }
        Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET).apply {
            putExtra("address", connectedDeviceAddress)
            putExtra("device_name", mainTitle.value)
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            this.putExtra("level", safeLevel)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun clearPodConnectionState() {
        val route = currentDeviceRoute()
        connectingDeviceAddress = null
        pendingOpenEarphonesAfterPickerLoaded = false
        connectedDeviceAddress = ""
        mainTitle.value = ""
        batteryParams.value = BatteryParams()
        ancMode.value = NoiseControlMode.UNKNOWN
        huaweiAncLevel.value = route.defaultAncSubMode
            ?: UNKNOWN_HUAWEI_ANC_SUBMODE
        hasHuaweiAncLevel.value = false
        huaweiTransparencySubMode.value = -1
        hookConnected.value = false
        hookConnectionState = "disconnected"
        showConnectErrorDialog = false
        showDevicePicker = true
        selectedTab = MainTab.Earphones
    }

    fun onDeviceSelected(device: BluetoothDevice, route: HuaweiDeviceRoute) {
        DeviceRoutePrefs.bind(
            prefs = prefs,
            service = xposedService,
            address = device.address,
            route = route,
        )
        connectingDeviceAddress = device.address
        ancMode.value = NoiseControlMode.UNKNOWN
        huaweiAncLevel.value = route.defaultAncSubMode
            ?: UNKNOWN_HUAWEI_ANC_SUBMODE
        hasHuaweiAncLevel.value = false
        huaweiTransparencySubMode.value = -1
        pendingOpenEarphonesAfterPickerLoaded = false
        showConnectErrorDialog = false
        showDevicePicker = true
        selectedTab = MainTab.Earphones
        hookConnectionState = "connecting"
        Intent(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST).apply {
            putExtra("device", device)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun onConnectedDeviceClick() {
        if (connectedDeviceAddress.isBlank() && mainTitle.value.isBlank()) return
        pendingOpenEarphonesAfterPickerLoaded = false
        hookConnected.value = true
        hookConnectionState = "connected"
        showDevicePicker = false
        selectedTab = MainTab.Earphones
    }

    fun backToDevicePicker() {
        showDevicePicker = true
    }

    fun openBluetoothSettings() {
        val action = if (bluetoothState.enabled) Settings.ACTION_BLUETOOTH_SETTINGS else BluetoothAdapter.ACTION_REQUEST_ENABLE
        Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(this) }
                .onFailure { Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    fun openDevicePicker() {
        showDevicePicker = true
        selectedTab = MainTab.Earphones
    }

    @SuppressLint("MissingPermission")
    fun openSystemHeadsetSettings() {
        val address = connectedDeviceAddress
        if (address.isBlank()) {
            Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val device = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }.getOrNull()
        if (device == null) {
            Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
            return
        }
        Intent().apply {
            setClassName("com.android.settings", "com.android.settings.bluetooth.MiuiHeadsetActivity")
            putExtra("android.bluetooth.device.extra.DEVICE", device)
            putExtra("bluetoothaddress", device.address)
            putExtra("MIUI_HEADSET_SUPPORT", ConfigManager.fakeSupport())
            putExtra("COME_FROM", "MIUI_BLUETOOTH_SETTINGS")
            putExtra("DEVICE_ID", ConfigManager.fakeDeviceId())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(this) }
                .onFailure { Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    fun refreshStatus() {
        if (hookConnected.value) {
            context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    fun savePodImages(
        address: String,
        name: String,
        images: Map<PodImageResource, Uri?>,
        clearedImages: Set<PodImageResource>,
    ) {
        earphonePrefs.value = PodImagePrefs.saveImages(context, prefs, xposedService, address, name, images, clearedImages)
        PodImageChangeNotifier.notify(context, address)
    }

    fun restartScopes(packages: List<String>) {
        if (packages.isEmpty() || restartingScopes) return
        restartingScopes = true
        coroutineScope.launch {
            var commandSucceeded = false
            var success = false
            try {
                commandSucceeded = withContext(Dispatchers.IO) {
                    RootManager.restartPackages(packages)
                }
                val waitForBluetooth = "com.android.bluetooth" in packages
                val observeMiBluetooth = "com.xiaomi.bluetooth" in packages
                success = commandSucceeded

                if (commandSucceeded && waitForBluetooth) {
                    lastBluetoothServiceAliveMs = 0L
                    bluetoothServiceResponsive = false
                }
                if (commandSucceeded && observeMiBluetooth) {
                    // 通知进程可能按需启动，心跳仅用于观察，不阻断核心蓝牙重启。
                    lastMiBluetoothServiceAliveMs = 0L
                }

                if (commandSucceeded && waitForBluetooth) {
                    for (attempt in 0 until 20) {
                        // 探活不请求业务状态，避免重启期间触发失效 Binder 或 HFP 查询。
                        sendBluetoothModuleBroadcast(context, HuaweiPodsAction.ACTION_PODS_UI_INIT)
                        delay(500L)
                        if (lastBluetoothServiceAliveMs > 0L) break
                    }
                    success = lastBluetoothServiceAliveMs > 0L
                }

                if (success && (waitForBluetooth || observeMiBluetooth)) {
                    // 新 Hook 就绪后只恢复一次；重启期间保留界面上最后一份有效状态。
                    context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                        setPackage("com.android.bluetooth")
                        putExtra(HuaweiPodsAction.EXTRA_RESTORE_NOTIFICATION, true)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    })
                }
            } finally {
                restartingScopes = false
                showRestartScopeDialog = false
            }
            Toast.makeText(
                context,
                when {
                    success -> R.string.restart_scope_success
                    commandSucceeded -> R.string.restart_scope_incomplete
                    else -> R.string.restart_scope_failed
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val entryProvider = entryProvider<Screen> {
        entry<Screen.Main> {
            MainTabsScaffold(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                floatingBottomBar = floatingBottomBar.value,
                blurBottomBar = blurBottomBar.value,
                backdrop = backdrop,
                backgroundColor = backgroundColor,
                overlayBottomBar = overlayBottomBar,
                pageBottomContentPadding = pageBottomContentPadding,
                xposedService = xposedService,
                bluetoothServiceResponsive = bluetoothServiceResponsive,
                bluetoothEnabled = bluetoothState.enabled,
                bondedDeviceCount = bluetoothState.bondedCount,
                onBluetoothStatusClick = { openBluetoothSettings() },
                onPairedBluetoothClick = { openDevicePicker() },
                showEarphoneDetail = showEarphoneDetail,
                mainTitle = mainTitle.value,
                displayTitle = displayTitle,
                displayBattery = displayBattery,
                displayAnc = displayAnc,
                onAncModeChange = { setAncMode(it) },
                huaweiAncLevel = if (displayAnc == NoiseControlMode.TRANSPARENCY) {
                    huaweiTransparencySubMode.value
                        .takeIf { it in supportedTransparencySubModes(currentDeviceRoute()) }
                        ?: defaultTransparencySubMode(currentDeviceRoute())
                } else {
                    huaweiAncLevel.value
                },
                onHuaweiAncLevelChange = { setHuaweiAncLevel(it) },
                earphonePrefs = earphonePrefs.value,
                deviceRoute = currentDeviceRoute(),
                connectedDeviceAddress = connectedDeviceAddress,
                connectingDeviceAddress = connectingDeviceAddress,
                showConnectErrorDialog = showConnectErrorDialog,
                onDeviceSelected = { device, route -> onDeviceSelected(device, route) },
                onConnectedDeviceClick = { onConnectedDeviceClick() },
                onDismissConnectError = { showConnectErrorDialog = false },
                desktopIconHidden = desktopIconHidden,
                onDesktopIconHiddenChange = {
                    desktopIconHidden.value = it
                    setLauncherIconHidden(context, it)
                },
                checkUpdatesOnLaunch = checkUpdatesOnLaunch,
                onCheckUpdatesOnLaunchChange = {
                    checkUpdatesOnLaunch.value = it
                    lifecyclePrefs.setCheckUpdatesOnLaunch(it)
                    if (it && lifecyclePrefs.shouldRunAutomaticCheck()) {
                        checkForUpdates(manual = false)
                    }
                },
                logLevel = logLevel,
                onLogLevelChange = {
                    logLevel.value = it
                    ConfigManager.updateLogLevel(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.milink.service")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                islandMode = islandMode,
                onIslandModeChange = {
                    islandMode.value = it
                    ConfigManager.updateIslandMode(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                persistentNotificationEnabled = persistentNotificationEnabled,
                onPersistentNotificationEnabledChange = {
                    persistentNotificationEnabled.value = it
                    ConfigManager.updatePersistentNotificationEnabled(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                lockscreenNotificationEnabled = lockscreenNotificationEnabled,
                onLockscreenNotificationEnabledChange = {
                    lockscreenNotificationEnabled.value = it
                    ConfigManager.updateLockscreenNotificationEnabled(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                appLanguage = appLanguage,
                onAppLanguageChange = {
                    appLanguage.value = it
                    onAppLanguageChange(it)
                },
                milinkLowLatencyCardEnabled = milinkLowLatencyCardEnabled,
                onMilinkLowLatencyCardEnabledChange = {
                    milinkLowLatencyCardEnabled.value = it
                    ConfigManager.updateMilinkLowLatencyCardEnabled(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.milink.service")
                },
                notificationClickAction = notificationClickAction,
                onNotificationClickActionChange = {
                    notificationClickAction.value = it
                    ConfigManager.updateNotificationClickAction(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                moreClickAction = moreClickAction,
                onMoreClickActionChange = {
                    moreClickAction.value = it
                    ConfigManager.updateMoreClickAction(prefs, xposedService, it)
                },
                fakeDeviceId = fakeDeviceId,
                onFakeDeviceIdChange = {
                    fakeDeviceId.value = it
                    ConfigManager.updateFakeDeviceId(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.android.settings")
                    broadcastConfigChanged(context, "com.milink.service")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                onOpenTheme = { backStack.add(Screen.Theme) },
                onOpenAbout = { backStack.add(Screen.About) },
                onOpenDocumentation = { backStack.add(Screen.Documentation) },
                onOpenSponsor = { backStack.add(Screen.Sponsor) },
                showRestartScopeDialog = showRestartScopeDialog,
                restartingScopes = restartingScopes,
                onShowRestartScopeDialog = { showRestartScopeDialog = true },
                onDismissRestartScopeDialog = {
                    showRestartScopeDialog = false
                    restartRequestedByUpdate = false
                },
                onRestartScopes = {
                    if (restartRequestedByUpdate) {
                        onUpdatedDialogHandled()
                        restartRequestedByUpdate = false
                    }
                    restartScopes(it)
                },
                onBackToDevicePicker = { backToDevicePicker() },
                onOpenSystemHeadsetSettings = { openSystemHeadsetSettings() },
                onSavePodImages = { address, name, images, clearedImages ->
                    savePodImages(address, name, images, clearedImages)
                },
            )
        }
        entry<Screen.About> {
            val aboutScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.about),
                        largeTitle = stringResource(R.string.about),
                        scrollBehavior = aboutScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    AboutPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(aboutScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        checkingForUpdates = checkingForUpdates,
                        updateCheckSummary = updateCheckSummary,
                        onCheckForUpdates = { checkForUpdates(manual = true) },
                        onPreviewUpdateDialog = if (BuildConfig.DEBUG) {
                            {
                                val previewRelease = GitHubRelease(
                                    tag = "${BuildConfig.VERSION_CODE + 1}-${BuildConfig.VERSION_NAME}-preview",
                                    versionCode = BuildConfig.VERSION_CODE.toLong() + 1L,
                                    versionName = "${BuildConfig.VERSION_NAME}-preview",
                                    releaseUrl = GitHubReleaseChecker.LATEST_RELEASE_PAGE,
                                    changelog = updatePreviewChangelog,
                                )
                                val persisted = pendingUpdateStore.save(
                                    release = previewRelease,
                                    isPreview = true,
                                )
                                Log.i(
                                    "HuaweiPods-Update",
                                    "Debug update preview requested: persisted=$persisted tag=${previewRelease.tag}",
                                )
                                updateCheckSummary = UpdateCheckSummary.Available(
                                    versionName = previewRelease.versionName,
                                )
                                availableUpdate = previewRelease
                                previewUpdate = previewRelease
                            }
                        } else {
                            null
                        },
                        onOpenGitHub = { openExternalUrl(context, GITHUB_REPOSITORY_URL) },
                        onOpenIssues = { openExternalUrl(context, GITHUB_ISSUES_URL) },
                        onCopyQqGroup = { copyQqGroup() },
                        qqGroupNumber = qqGroupNumber,
                        onOpenOnboarding = onOpenOnboarding,
                    )
                }
            }
        }
        entry<Screen.Theme> {
            val themeScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.theme_title),
                        largeTitle = stringResource(R.string.theme_title),
                        scrollBehavior = themeScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    ThemeSettingsPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(themeScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        accentMode = accentMode,
                        onAccentModeChange = onAccentModeChange,
                        floatingBottomBar = floatingBottomBar,
                        onFloatingBottomBarChange = onFloatingBottomBarChange,
                        blurBottomBar = blurBottomBar,
                        onBlurBottomBarChange = onBlurBottomBarChange,
                    )
                }
            }
        }
        entry<Screen.Documentation> {
            val documentationScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.documentation_title),
                        largeTitle = stringResource(R.string.documentation_title),
                        scrollBehavior = documentationScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    DocumentationPage(
                        onOpenExternalUrl = { openExternalUrl(context, it) },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                    )
                }
            }
        }
        entry<Screen.Sponsor> {
            val sponsorScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.sponsor_title),
                        largeTitle = stringResource(R.string.sponsor_title),
                        scrollBehavior = sponsorScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    SponsorPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(sponsorScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                    )
                }
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            } else {
                (context as? Activity)?.finish()
            }
        }
    )

    val release = previewUpdate ?: availableUpdate
    val showingPreviewUpdate = previewUpdate != null
    val showUpdatedAppDialog = showUpdatedDialogOnLaunch && !restartRequestedByUpdate
    val showAvailableUpdateDialog = shouldShowAvailableUpdateDialog(
        hasAvailableUpdate = release != null,
        showUpdatedAppDialog = showUpdatedAppDialog,
        showRestartScopeDialog = showRestartScopeDialog,
        forcePreview = showingPreviewUpdate,
    )

    LaunchedEffect(showAvailableUpdateDialog, release?.tag) {
        if (showAvailableUpdateDialog && release != null) {
            Log.i("HuaweiPods-Update", "Showing update dialog: ${release.tag}")
        }
    }

    // Keep both update modal call sites stable and let the priority policy expose only one.
    UpdatedAppDialog(
        show = showUpdatedAppDialog && !showAvailableUpdateDialog,
        versionName = BuildConfig.VERSION_NAME,
        onLater = onUpdatedDialogHandled,
        onRestartScope = {
            while (backStack.size > 1) {
                backStack.removeLast()
            }
            restartRequestedByUpdate = true
            showRestartScopeDialog = true
        },
    )

    AvailableUpdateDialog(
        show = showAvailableUpdateDialog,
        currentVersion = BuildConfig.VERSION_NAME,
        latestVersion = release?.versionName.orEmpty(),
        releaseNotes = release?.changelog.orEmpty(),
        onLater = {
            Log.i("HuaweiPods-Update", "Update dialog dismissed: ${release?.tag}")
            if (!showingPreviewUpdate) {
                lifecyclePrefs.markUpdateCheck(System.currentTimeMillis())
            }
            pendingUpdateStore.clear()
            availableUpdate = null
            previewUpdate = null
        },
        onOpenRelease = {
            release?.let {
                Log.i("HuaweiPods-Update", "Opening release page: ${it.tag}")
                if (openExternalUrl(context, it.releaseUrl)) {
                    if (!showingPreviewUpdate) {
                        lifecyclePrefs.markUpdateCheck(System.currentTimeMillis())
                    }
                    pendingUpdateStore.clear()
                    availableUpdate = null
                    previewUpdate = null
                }
            }
        },
    )
}

@Composable
fun appBackground(): Color = MiuixTheme.colorScheme.surface

private fun openExternalUrl(context: Context, url: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrElse {
    Toast.makeText(context, R.string.open_link_failed, Toast.LENGTH_SHORT).show()
    false
}

private data class BluetoothSummary(
    val enabled: Boolean,
    val bondedCount: Int,
)

@SuppressLint("MissingPermission")
private fun readBluetoothState(context: Context): BluetoothSummary {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    return runCatching {
        BluetoothSummary(
            enabled = adapter?.isEnabled == true,
            bondedCount = adapter?.bondedDevices?.size ?: 0,
        )
    }.getOrDefault(BluetoothSummary(enabled = false, bondedCount = 0))
}

private fun resolvedConnectedAddress(vararg candidates: String?): String {
    return candidates.firstOrNull { candidate ->
        !candidate.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(candidate)
    }.orEmpty()
}

private fun sendBluetoothModuleBroadcast(context: Context, action: String) {
    listOf("com.android.bluetooth", "com.xiaomi.bluetooth").forEach { packageName ->
        Intent(action).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }
}

private fun isLauncherIconHidden(context: Context): Boolean {
    val component = ComponentName(BuildConfig.APPLICATION_ID, "moe.chenxy.huaweipods.LauncherActivity")
    val state = context.packageManager.getComponentEnabledSetting(component)
    return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}

private fun setLauncherIconHidden(context: Context, hidden: Boolean) {
    val component = ComponentName(BuildConfig.APPLICATION_ID, "moe.chenxy.huaweipods.LauncherActivity")
    val state = if (hidden) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
    context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
}

private fun broadcastConfigChanged(context: Context, packageName: String) {
    Intent(HuaweiPodsAction.ACTION_CONFIG_CHANGED).apply {
        setPackage(packageName)
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        context.sendBroadcast(this)
    }
}

private fun supportedTransparencySubModes(route: HuaweiDeviceRoute): Set<Int> =
    if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) setOf(0x01, 0x02) else setOf(0x01, 0xFF)

private fun defaultTransparencySubMode(route: HuaweiDeviceRoute): Int =
    if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) 0x02 else 0xFF

@Suppress("DEPRECATION")
private fun Intent.parcelableBatteryStatus(): BatteryParams? =
    runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
        ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
