package moe.chenxy.huaweipods.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.config.AppLifecyclePrefs
import moe.chenxy.huaweipods.config.LaunchDecision
import moe.chenxy.huaweipods.ui.pages.OnboardingPage
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun App(
    initialLaunchDecision: LaunchDecision = LaunchDecision(
        showOnboarding = false,
        showUpdated = false,
    ),
    navigateToEarphoneDetail: MutableState<Boolean> = mutableStateOf(false),
    targetDeviceAddress: MutableState<String?> = mutableStateOf(null),
    targetDeviceName: MutableState<String?> = mutableStateOf(null),
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
    val lifecyclePrefs = remember(context) { AppLifecyclePrefs(context) }
    val colorSchemeMode = when (themeMode.value) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val backStack = remember { mutableStateListOf<Screen>(Screen.Main) }
    var selectedTab by remember { mutableStateOf(MainTab.Module) }
    var showOnboarding by remember { mutableStateOf(initialLaunchDecision.showOnboarding) }
    var onboardingIsReplay by remember { mutableStateOf(false) }
    var showUpdatedDialog by remember { mutableStateOf(initialLaunchDecision.showUpdated) }
    var xposedService by remember { mutableStateOf(HuaweiPodsApp.xposedService) }

    LaunchedEffect(navigateToEarphoneDetail.value) {
        if (navigateToEarphoneDetail.value) {
            if (showOnboarding) {
                showOnboarding = false
                onboardingIsReplay = false
            }
            if (backStack.size > 1) {
                backStack.clear()
                backStack.add(Screen.Main)
            }
        }
    }

    DisposableEffect(Unit) {
        val listener: (io.github.libxposed.service.XposedService?) -> Unit = {
            xposedService = it
        }
        HuaweiPodsApp.addServiceListener(listener)
        onDispose { HuaweiPodsApp.removeServiceListener(listener) }
    }

    fun finishOnboarding() {
        lifecyclePrefs.setOnboardingCompleted(true)
        showOnboarding = false
        onboardingIsReplay = false
    }

    fun closeReplayedOnboarding() {
        showOnboarding = false
        onboardingIsReplay = false
    }

    fun acknowledgeUpdatedVersion() {
        lifecyclePrefs.recordCurrentVersion(
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            versionName = BuildConfig.VERSION_NAME,
        )
        showUpdatedDialog = false
    }

    BackHandler(enabled = showOnboarding) {
        if (onboardingIsReplay) {
            closeReplayedOnboarding()
        } else {
            (context as? Activity)?.finish()
        }
    }

    AppLocale.Provider(language = appLanguage.value) {
        AppTheme(colorSchemeMode = colorSchemeMode, accentMode = accentMode.value) {
            if (showOnboarding) {
                OnboardingPage(
                    xposedService = xposedService,
                    isReplay = onboardingIsReplay,
                    onFinish = if (onboardingIsReplay) ::closeReplayedOnboarding else ::finishOnboarding,
                    onSkip = if (onboardingIsReplay) ::closeReplayedOnboarding else ::finishOnboarding,
                )
            } else {
                MainUI(
                    backStack = backStack,
                    navigateToEarphoneDetail = navigateToEarphoneDetail,
                    targetDeviceAddress = targetDeviceAddress,
                    targetDeviceName = targetDeviceName,
                    selectedTab = selectedTab,
                    onSelectedTabChange = { selectedTab = it },
                    showUpdatedDialogOnLaunch = showUpdatedDialog,
                    onUpdatedDialogHandled = ::acknowledgeUpdatedVersion,
                    onOpenOnboarding = {
                        onboardingIsReplay = true
                        showOnboarding = true
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    accentMode = accentMode,
                    onAccentModeChange = onAccentModeChange,
                    floatingBottomBar = floatingBottomBar,
                    onFloatingBottomBarChange = onFloatingBottomBarChange,
                    blurBottomBar = blurBottomBar,
                    onBlurBottomBarChange = onBlurBottomBarChange,
                    appLanguage = appLanguage,
                    onAppLanguageChange = onAppLanguageChange,
                )
            }
        }
    }
}
