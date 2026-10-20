package moe.chenxy.huaweipods.config

import android.content.Context
import androidx.core.content.edit

data class InstalledAppVersion(
    val versionCode: Long,
    val versionName: String,
)

data class LaunchSnapshot(
    val onboardingCompleted: Boolean,
    val previousVersionCode: Long?,
    val hasExistingInstallation: Boolean,
)

data class LaunchDecision(
    val showOnboarding: Boolean,
    val showUpdated: Boolean,
    val completeOnboardingMigration: Boolean = false,
)

fun decideLaunch(
    snapshot: LaunchSnapshot,
    currentVersionCode: Long,
): LaunchDecision {
    val upgradedFromRecordedVersion = snapshot.previousVersionCode
        ?.let { currentVersionCode > it }
        ?: false
    val upgradedFromLegacyVersion = snapshot.previousVersionCode == null &&
        snapshot.hasExistingInstallation
    val upgraded = upgradedFromRecordedVersion || upgradedFromLegacyVersion

    return LaunchDecision(
        showOnboarding = !snapshot.onboardingCompleted && !upgraded,
        showUpdated = upgraded,
        completeOnboardingMigration = upgradedFromLegacyVersion && !snapshot.onboardingCompleted,
    )
}

fun shouldRunAutomaticUpdateCheck(
    enabled: Boolean,
    lastCheckAtMillis: Long,
    nowMillis: Long,
    intervalMillis: Long = AppLifecyclePrefs.AUTO_CHECK_INTERVAL_MS,
): Boolean {
    if (!enabled) return false
    if (lastCheckAtMillis <= 0L) return true
    if (nowMillis < lastCheckAtMillis) return true
    return nowMillis - lastCheckAtMillis >= intervalMillis.coerceAtLeast(0L)
}

internal fun shouldResetAutomaticUpdateCheckAfterVersionChange(
    previousVersionCode: Long?,
    currentVersionCode: Long,
): Boolean = previousVersionCode != null && currentVersionCode < previousVersionCode

class AppLifecyclePrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
    }

    fun checkUpdatesOnLaunch(): Boolean =
        prefs.getBoolean(KEY_CHECK_UPDATES_ON_LAUNCH, false)

    fun setCheckUpdatesOnLaunch(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CHECK_UPDATES_ON_LAUNCH, enabled) }
    }

    fun lastCheckAtMillis(): Long = prefs.getLong(KEY_LAST_CHECK_AT_MILLIS, 0L)

    fun markUpdateCheck(nowMillis: Long) {
        prefs.edit { putLong(KEY_LAST_CHECK_AT_MILLIS, nowMillis.coerceAtLeast(0L)) }
    }

    fun shouldRunAutomaticCheck(nowMillis: Long = System.currentTimeMillis()): Boolean =
        shouldRunAutomaticUpdateCheck(
            enabled = checkUpdatesOnLaunch(),
            lastCheckAtMillis = lastCheckAtMillis(),
            nowMillis = nowMillis,
        )

    fun lastInstalledVersion(): InstalledAppVersion? {
        if (!prefs.contains(KEY_LAST_VERSION_CODE)) return null
        return InstalledAppVersion(
            versionCode = prefs.getLong(KEY_LAST_VERSION_CODE, 0L),
            versionName = prefs.getString(KEY_LAST_VERSION_NAME, "").orEmpty(),
        )
    }

    fun recordCurrentVersion(versionCode: Long, versionName: String) {
        prefs.edit {
            putLong(KEY_LAST_VERSION_CODE, versionCode)
            putString(KEY_LAST_VERSION_NAME, versionName)
        }
    }

    fun consumeLaunchDecision(
        currentVersionCode: Long,
        currentVersionName: String,
    ): LaunchDecision = consumeLaunchDecision(
        currentVersionCode = currentVersionCode,
        currentVersionName = currentVersionName,
        hasExistingInstallation = detectExistingInstallation(),
    )

    fun consumeLaunchDecision(
        currentVersionCode: Long,
        currentVersionName: String,
        hasExistingInstallation: Boolean,
    ): LaunchDecision {
        val previousVersion = lastInstalledVersion()
        if (
            shouldResetAutomaticUpdateCheckAfterVersionChange(
                previousVersionCode = previousVersion?.versionCode,
                currentVersionCode = currentVersionCode,
            )
        ) {
            prefs.edit { remove(KEY_LAST_CHECK_AT_MILLIS) }
        }
        val decision = decideLaunch(
            snapshot = LaunchSnapshot(
                onboardingCompleted = isOnboardingCompleted(),
                previousVersionCode = previousVersion?.versionCode,
                hasExistingInstallation = hasExistingInstallation,
            ),
            currentVersionCode = currentVersionCode,
        )
        if (decision.completeOnboardingMigration) {
            setOnboardingCompleted(true)
        }
        if (!decision.showUpdated) {
            recordCurrentVersion(currentVersionCode, currentVersionName)
        }
        return decision
    }

    private fun detectExistingInstallation(): Boolean {
        return appContext
            .getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .isNotEmpty()
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L

        private const val PREFS_NAME = "huaweipods_app_lifecycle"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_CHECK_UPDATES_ON_LAUNCH = "check_updates_on_launch"
        private const val KEY_LAST_CHECK_AT_MILLIS = "last_update_check_at_millis"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
        private const val KEY_LAST_VERSION_NAME = "last_version_name"
    }
}
