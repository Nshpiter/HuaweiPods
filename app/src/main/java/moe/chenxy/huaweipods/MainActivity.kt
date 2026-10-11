package moe.chenxy.huaweipods

import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import moe.chenxy.huaweipods.config.AppLifecyclePrefs
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.ui.App
import moe.chenxy.huaweipods.ui.AppLocale
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

class MainActivity : ComponentActivity() {
    private val requestedDeviceAddress = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeviceSettingsRequest(intent)
    }

    private fun readDeviceSettingsRequest(intent: Intent?) {
        if (intent?.action != HuaweiPodsAction.ACTION_OPEN_DEVICE_SETTINGS) return
        requestedDeviceAddress.value = intent.getStringExtra("address")
            ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
    }

    override fun attachBaseContext(newBase: Context) {
        AppLocale.rememberDeviceLocale(newBase)
        AppLocale.apply(newBase, newBase.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE).getInt("app_language", AppLocale.SYSTEM))
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readDeviceSettingsRequest(intent)

        val lifecyclePrefs = AppLifecyclePrefs(this)
        val launchDecision = lifecyclePrefs.consumeLaunchDecision(
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            currentVersionName = BuildConfig.VERSION_NAME,
        )

        setContent {
            val prefs = remember { getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
            val themeMode = remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }
            val accentMode = remember { mutableStateOf(prefs.getInt("accent_mode", 0)) }
            val floatingBottomBar = remember { mutableStateOf(prefs.getBoolean("floating_bottom_bar", false)) }
            val blurBottomBar = remember { mutableStateOf(prefs.getBoolean("blur_bottom_bar", false)) }
            val appLanguage = remember { mutableStateOf(prefs.getInt("app_language", AppLocale.SYSTEM)) }
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode.value) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )

                window.isNavigationBarContrastEnforced = false

                onDispose {}
            }

            App(
                initialLaunchDecision = launchDecision,
                requestedDeviceAddress = requestedDeviceAddress.value,
                onDeviceSettingsRequestHandled = { requestedDeviceAddress.value = null },
                themeMode = themeMode,
                onThemeModeChange = {
                    themeMode.value = it
                    prefs.edit().putInt("theme_mode", it).apply()
                },
                accentMode = accentMode,
                onAccentModeChange = {
                    accentMode.value = it
                    prefs.edit().putInt("accent_mode", it).apply()
                },
                floatingBottomBar = floatingBottomBar,
                onFloatingBottomBarChange = {
                    floatingBottomBar.value = it
                    prefs.edit().putBoolean("floating_bottom_bar", it).apply()
                },
                blurBottomBar = blurBottomBar,
                onBlurBottomBarChange = {
                    blurBottomBar.value = it
                    prefs.edit().putBoolean("blur_bottom_bar", it).apply()
                },
                appLanguage = appLanguage,
                onAppLanguageChange = {
                    appLanguage.value = it
                    prefs.edit().putInt("app_language", it).apply()
                },
            )
        }
    }
}
