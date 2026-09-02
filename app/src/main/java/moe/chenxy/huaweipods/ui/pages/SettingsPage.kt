package moe.chenxy.huaweipods.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.ui.AppLocale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    desktopIconHidden: MutableState<Boolean> = mutableStateOf(false),
    onDesktopIconHiddenChange: (Boolean) -> Unit = {},
    checkUpdatesOnLaunch: MutableState<Boolean> = mutableStateOf(true),
    onCheckUpdatesOnLaunchChange: (Boolean) -> Unit = {},
    logLevel: MutableState<Int> = mutableStateOf(ConfigManager.LOG_LEVEL_BASIC),
    onLogLevelChange: (Int) -> Unit = {},
    islandMode: MutableState<Int> = mutableStateOf(ConfigManager.ISLAND_MODE_OFFICIAL),
    onIslandModeChange: (Int) -> Unit = {},
    persistentNotificationEnabled: MutableState<Boolean> = mutableStateOf(true),
    onPersistentNotificationEnabledChange: (Boolean) -> Unit = {},
    lockscreenNotificationEnabled: MutableState<Boolean> = mutableStateOf(true),
    onLockscreenNotificationEnabledChange: (Boolean) -> Unit = {},
    appLanguage: MutableState<Int> = mutableStateOf(AppLocale.SYSTEM),
    onAppLanguageChange: (Int) -> Unit = {},
    milinkLowLatencyCardEnabled: MutableState<Boolean> = mutableStateOf(true),
    onMilinkLowLatencyCardEnabledChange: (Boolean) -> Unit = {},
    notificationClickAction: MutableState<Int> = mutableStateOf(ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP),
    onNotificationClickActionChange: (Int) -> Unit = {},
    moreClickAction: MutableState<Int> = mutableStateOf(ConfigManager.MORE_CLICK_MODULE),
    onMoreClickActionChange: (Int) -> Unit = {},
    fakeDeviceId: MutableState<String> = mutableStateOf(ConfigManager.DEFAULT_FAKE_DEVICE_ID),
    onFakeDeviceIdChange: (String) -> Unit = {},
    onOpenTheme: () -> Unit = {},
) {
    val languageOptions = listOf(
        stringResource(R.string.language_system),
        stringResource(R.string.language_chinese),
        stringResource(R.string.language_english),
    )
    val logLevelValues = listOf(ConfigManager.LOG_LEVEL_OFF, ConfigManager.LOG_LEVEL_BASIC, ConfigManager.LOG_LEVEL_DEBUG)
    val logLevelOptions = listOf(
        stringResource(R.string.log_level_off),
        stringResource(R.string.log_level_basic),
        stringResource(R.string.log_level_debug),
    )
    val islandModeValues = listOf(
        ConfigManager.ISLAND_MODE_NONE,
        ConfigManager.ISLAND_MODE_OFFICIAL,
        ConfigManager.ISLAND_MODE_MODULE,
    )
    val islandModeOptions = listOf(
        stringResource(R.string.island_mode_none),
        stringResource(R.string.island_mode_official),
        stringResource(R.string.island_mode_module),
    )
    val notificationClickActionValues = listOf(
        ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP,
        ConfigManager.NOTIFICATION_CLICK_SMART_AUDIO,
        ConfigManager.NOTIFICATION_CLICK_SYSTEM_SETTINGS,
    )
    val notificationClickActionOptions = listOf(
        stringResource(R.string.notification_click_module_popup),
        stringResource(R.string.click_action_smart_audio),
        stringResource(R.string.click_action_system_settings),
    )
    val moreClickActionValues = listOf(
        ConfigManager.MORE_CLICK_MODULE,
        ConfigManager.MORE_CLICK_SMART_AUDIO,
        ConfigManager.MORE_CLICK_SYSTEM_SETTINGS,
    )
    val moreClickActionOptions = listOf(
        stringResource(R.string.click_action_module),
        stringResource(R.string.click_action_smart_audio),
        stringResource(R.string.click_action_system_settings),
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
    ) {
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.theme_title),
                    summary = stringResource(R.string.theme_color_summary),
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = onOpenTheme,
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.language),
                    summary = stringResource(R.string.language_summary),
                    items = languageOptions,
                    selectedIndex = appLanguage.value.coerceIn(languageOptions.indices),
                    onSelectedIndexChange = { onAppLanguageChange(it) }
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.log_level),
                    summary = stringResource(R.string.log_level_summary),
                    items = logLevelOptions,
                    selectedIndex = logLevelValues.indexOf(logLevel.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onLogLevelChange(logLevelValues[it]) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hide_desktop_icon),
                    summary = stringResource(R.string.hide_desktop_icon_summary),
                    checked = desktopIconHidden.value,
                    onCheckedChange = { onDesktopIconHiddenChange(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.check_updates_on_launch),
                    summary = stringResource(R.string.check_updates_on_launch_summary),
                    checked = checkUpdatesOnLaunch.value,
                    onCheckedChange = { onCheckUpdatesOnLaunchChange(it) },
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.island_mode),
                    summary = stringResource(R.string.island_mode_summary),
                    items = islandModeOptions,
                    selectedIndex = islandModeValues.indexOf(islandMode.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onIslandModeChange(islandModeValues[it]) },
                )
                SwitchPreference(
                    title = stringResource(R.string.persistent_notification),
                    summary = stringResource(R.string.persistent_notification_summary),
                    checked = persistentNotificationEnabled.value,
                    onCheckedChange = onPersistentNotificationEnabledChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.lockscreen_notification),
                    summary = stringResource(R.string.lockscreen_notification_summary),
                    checked = lockscreenNotificationEnabled.value,
                    onCheckedChange = onLockscreenNotificationEnabledChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.milink_low_latency_card),
                    summary = stringResource(R.string.milink_low_latency_card_summary),
                    checked = milinkLowLatencyCardEnabled.value,
                    onCheckedChange = onMilinkLowLatencyCardEnabledChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.notification_click_action),
                    summary = stringResource(R.string.notification_click_action_summary),
                    items = notificationClickActionOptions,
                    selectedIndex = notificationClickActionValues.indexOf(notificationClickAction.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onNotificationClickActionChange(notificationClickActionValues[it]) }
                )
                if (notificationClickAction.value == ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.more_click_action),
                        summary = stringResource(R.string.more_click_action_summary),
                        items = moreClickActionOptions,
                        selectedIndex = moreClickActionValues.indexOf(moreClickAction.value).coerceAtLeast(0),
                        onSelectedIndexChange = { onMoreClickActionChange(moreClickActionValues[it]) }
                    )
                }

            }
        }
    }
}
