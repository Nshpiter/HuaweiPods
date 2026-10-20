package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppConfig(
    val logLevel: Int = ConfigManager.LOG_LEVEL_BASIC,
    val persistentNotificationEnabled: Boolean = true,
    val lockscreenNotificationEnabled: Boolean = true,
    val notificationClickAction: Int = ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP,
    val moreClickAction: Int = ConfigManager.MORE_CLICK_MODULE,
    val lowBatteryReminderEnabled: Boolean = true,
)

object ConfigManager {
    private const val TAG = "HuaweiPods-Config"
    // LSPosed remote preferences and app preferences must use the same name.
    const val PREFS_NAME = "huaweipods_settings"
    const val PREF_KEY_CONFIG_JSON = "config_json"
    const val PREF_KEY_LOG_LEVEL = "log_level"
    const val PREF_KEY_PERSISTENT_NOTIFICATION_ENABLED = "persistent_notification_enabled"
    const val PREF_KEY_LOCKSCREEN_NOTIFICATION_ENABLED = "lockscreen_notification_enabled"
    const val PREF_KEY_NOTIFICATION_CLICK_ACTION = "notification_click_action"
    const val PREF_KEY_MORE_CLICK_ACTION = "more_click_action"
    const val PREF_KEY_LOW_BATTERY_REMINDER_ENABLED = "low_battery_reminder_enabled"
    const val LOG_LEVEL_OFF = 0
    const val LOG_LEVEL_BASIC = 1
    const val LOG_LEVEL_DEBUG = 2
    const val NOTIFICATION_CLICK_MODULE_POPUP = 0
    const val NOTIFICATION_CLICK_SYSTEM_SETTINGS = 1
    const val NOTIFICATION_CLICK_SMART_AUDIO = 2
    const val MORE_CLICK_SMART_AUDIO = 0
    const val MORE_CLICK_SYSTEM_SETTINGS = 1
    const val MORE_CLICK_MODULE = 2

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedConfig: AppConfig = AppConfig()

    fun init(prefs: SharedPreferences) {
        val oldConfig = cachedConfig
        cachedConfig = readConfig(prefs, "init")
        logConfigChange("init", oldConfig, cachedConfig)
    }

    fun refreshFromPrefs(prefs: SharedPreferences): AppConfig {
        val oldConfig = cachedConfig
        return readConfig(prefs, "refreshFromPrefs").also {
            cachedConfig = it
            logConfigChange("refreshFromPrefs", oldConfig, it)
        }
    }

    fun current(): AppConfig = cachedConfig

    fun logLevel(): Int = current().logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG)

    fun persistentNotificationEnabled(): Boolean = current().persistentNotificationEnabled

    fun lockscreenNotificationEnabled(): Boolean = current().lockscreenNotificationEnabled

    fun notificationClickAction(): Int = current().notificationClickAction.normalizedNotificationClickAction()

    fun moreClickAction(): Int = current().moreClickAction.normalizedMoreClickAction()

    fun lowBatteryReminderEnabled(): Boolean = current().lowBatteryReminderEnabled

    fun updateLogLevel(prefs: SharedPreferences, service: XposedService?, logLevel: Int) {
        val config = current().copy(logLevel = logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG))
        save(prefs, service, config)
    }

    fun updateLockscreenNotificationEnabled(
        prefs: SharedPreferences,
        service: XposedService?,
        enabled: Boolean,
    ) {
        save(prefs, service, current().copy(lockscreenNotificationEnabled = enabled))
    }

    fun updatePersistentNotificationEnabled(
        prefs: SharedPreferences,
        service: XposedService?,
        enabled: Boolean,
    ) {
        save(prefs, service, current().copy(persistentNotificationEnabled = enabled))
    }

    fun updateNotificationClickAction(prefs: SharedPreferences, service: XposedService?, action: Int) {
        val config = current().copy(notificationClickAction = action.normalizedNotificationClickAction())
        save(prefs, service, config)
    }

    fun updateLowBatteryReminderEnabled(
        prefs: SharedPreferences,
        service: XposedService?,
        enabled: Boolean,
    ) {
        save(prefs, service, current().copy(lowBatteryReminderEnabled = enabled))
    }

    fun updateMoreClickAction(prefs: SharedPreferences, service: XposedService?, action: Int) {
        val config = current().copy(moreClickAction = action.normalizedMoreClickAction())
        save(prefs, service, config)
    }

    fun save(prefs: SharedPreferences, config: AppConfig) {
        val oldConfig = cachedConfig
        val normalized = config.normalized()
        cachedConfig = normalized
        writePrefs(prefs, normalized)
        logConfigChange("save", oldConfig, normalized)
    }

    fun save(prefs: SharedPreferences, service: XposedService?, config: AppConfig) {
        val oldConfig = cachedConfig
        val normalized = config.normalized()
        cachedConfig = normalized
        writePrefs(prefs, normalized)
        val remotePrefs = runCatching {
            service?.getRemotePreferences(PREFS_NAME)
        }.onFailure {
            Log.w(TAG, "save remote prefs unavailable", it)
        }.getOrNull()
        if (remotePrefs == null) {
            Log.w(TAG, "save remote prefs skipped: LSPosed service is null")
        } else {
            runCatching { writePrefs(remotePrefs, normalized) }
                .onSuccess {
                    Log.d(
                        TAG,
                        "save remote prefs class=${remotePrefs.javaClass.name}",
                    )
                }
                .onFailure { Log.w(TAG, "save remote prefs failed", it) }
        }
        logConfigChange("save", oldConfig, normalized)
    }

    private fun writePrefs(prefs: SharedPreferences, config: AppConfig) {
        prefs.edit()
            .putString(PREF_KEY_CONFIG_JSON, json.encodeToString(AppConfig.serializer(), config))
            .putInt(PREF_KEY_LOG_LEVEL, config.logLevel)
            .putBoolean(PREF_KEY_PERSISTENT_NOTIFICATION_ENABLED, config.persistentNotificationEnabled)
            .putBoolean(PREF_KEY_LOCKSCREEN_NOTIFICATION_ENABLED, config.lockscreenNotificationEnabled)
            .putInt(PREF_KEY_NOTIFICATION_CLICK_ACTION, config.notificationClickAction)
            .putInt(PREF_KEY_MORE_CLICK_ACTION, config.moreClickAction)
            .putBoolean(PREF_KEY_LOW_BATTERY_REMINDER_ENABLED, config.lowBatteryReminderEnabled)
            .commit()
    }

    private fun readConfig(prefs: SharedPreferences, source: String): AppConfig {
        val directLogLevel = prefs.getInt(PREF_KEY_LOG_LEVEL, Int.MIN_VALUE)
        val directPersistentNotificationEnabled = prefs.booleanOrNull(
            PREF_KEY_PERSISTENT_NOTIFICATION_ENABLED,
        )
        val directLockscreenNotificationEnabled = prefs.booleanOrNull(
            PREF_KEY_LOCKSCREEN_NOTIFICATION_ENABLED,
        )
        val directNotificationClickAction = prefs.getInt(PREF_KEY_NOTIFICATION_CLICK_ACTION, Int.MIN_VALUE)
        val directMoreClickAction = prefs.getInt(PREF_KEY_MORE_CLICK_ACTION, Int.MIN_VALUE)
        val directLowBatteryReminderEnabled = prefs.booleanOrNull(PREF_KEY_LOW_BATTERY_REMINDER_ENABLED)
        val raw = prefs.getString(PREF_KEY_CONFIG_JSON, null)
        logPrefsSnapshot(source, prefs, raw)
        val config = raw?.let {
            runCatching { json.decodeFromString(AppConfig.serializer(), it) }.getOrNull()
        } ?: AppConfig()
        return config.copy(
            logLevel = directLogLevel.takeIf { it != Int.MIN_VALUE } ?: config.logLevel,
            persistentNotificationEnabled = directPersistentNotificationEnabled
                ?: config.persistentNotificationEnabled,
            lockscreenNotificationEnabled = directLockscreenNotificationEnabled
                ?: config.lockscreenNotificationEnabled,
            notificationClickAction = directNotificationClickAction.takeIf { it != Int.MIN_VALUE } ?: config.notificationClickAction,
            moreClickAction = directMoreClickAction.takeIf { it != Int.MIN_VALUE } ?: config.moreClickAction,
            lowBatteryReminderEnabled = directLowBatteryReminderEnabled ?: config.lowBatteryReminderEnabled,
        ).normalized()
    }

    private fun AppConfig.normalized(): AppConfig = copy(
        logLevel = logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG),
        notificationClickAction = notificationClickAction.normalizedNotificationClickAction(),
        moreClickAction = moreClickAction.normalizedMoreClickAction(),
    )

    internal fun Int.normalizedNotificationClickAction(): Int = when (this) {
        NOTIFICATION_CLICK_SYSTEM_SETTINGS,
        NOTIFICATION_CLICK_SMART_AUDIO,
        -> this
        else -> NOTIFICATION_CLICK_MODULE_POPUP
    }

    internal fun Int.normalizedMoreClickAction(): Int = when (this) {
        MORE_CLICK_SMART_AUDIO,
        MORE_CLICK_SYSTEM_SETTINGS,
        MORE_CLICK_MODULE,
        -> this
        else -> MORE_CLICK_MODULE
    }

    private fun SharedPreferences.booleanOrNull(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private fun logConfigChange(source: String, oldConfig: AppConfig, newConfig: AppConfig) {
        val changes = changedFields(oldConfig, newConfig)
        if (changes.isEmpty()) {
            Log.d(TAG, "$source config unchanged: $newConfig")
        } else {
            Log.d(TAG, "$source config changed: ${changes.joinToString()}")
        }
    }

    private fun logPrefsSnapshot(source: String, prefs: SharedPreferences, rawConfig: String?) {
        val all = runCatching { prefs.all }.getOrElse { error -> mapOf("<getAllError>" to error.message) }
        Log.d(
            TAG,
            "$source prefs snapshot class=${prefs.javaClass.name} keys=${all.keys.sorted()} " +
                "$PREF_KEY_CONFIG_JSON=$rawConfig all=$all"
        )
    }

    private fun changedFields(oldConfig: AppConfig, newConfig: AppConfig): List<String> {
        return buildList {
            if (oldConfig.logLevel != newConfig.logLevel) {
                add("logLevel=${oldConfig.logLevel}->${newConfig.logLevel}")
            }
            if (oldConfig.persistentNotificationEnabled != newConfig.persistentNotificationEnabled) {
                add(
                    "persistentNotificationEnabled=${oldConfig.persistentNotificationEnabled}->" +
                        newConfig.persistentNotificationEnabled,
                )
            }
            if (oldConfig.lockscreenNotificationEnabled != newConfig.lockscreenNotificationEnabled) {
                add(
                    "lockscreenNotificationEnabled=${oldConfig.lockscreenNotificationEnabled}->" +
                        newConfig.lockscreenNotificationEnabled,
                )
            }
            if (oldConfig.notificationClickAction != newConfig.notificationClickAction) {
                add("notificationClickAction=${oldConfig.notificationClickAction}->${newConfig.notificationClickAction}")
            }
            if (oldConfig.lowBatteryReminderEnabled != newConfig.lowBatteryReminderEnabled) {
                add(
                    "lowBatteryReminderEnabled=${oldConfig.lowBatteryReminderEnabled}->" +
                        newConfig.lowBatteryReminderEnabled,
                )
            }
            if (oldConfig.moreClickAction != newConfig.moreClickAction) {
                add("moreClickAction=${oldConfig.moreClickAction}->${newConfig.moreClickAction}")
            }
        }
    }
}
