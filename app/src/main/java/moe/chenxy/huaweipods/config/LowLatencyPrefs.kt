package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl

/**
 * 低时延模式的期望策略。
 *
 * 耳机协议没有提供已验证的低时延回读，因此这里保存的是“连接后自动重新应用”的用户意图，
 * 不是耳机当前状态。沿用旧版 UI 的键名，升级后无需用户重新开启。
 */
object LowLatencyPrefs {
    private const val FREEBUDS5_PREFIX = "freebuds5_"
    private const val FREECLIP2_PREFIX = "freeclip2_"
    private const val KEY_SUFFIX = "_low_latency"
    private const val AUTO_KEY_PREFIX = "low_latency_auto_v1_"
    private val bluetoothAddressPattern = Regex("^[0-9A-F]{2}(?::[0-9A-F]{2}){5}$")
    private val preferenceKeyPattern = Regex(
        "^(?:(?:freebuds5|freeclip2)_[0-9A-F]{2}(?::[0-9A-F]{2}){5}_low_latency|" +
            "low_latency_auto_v1_[0-9A-F]{12})$",
    )
    private val syncLock = Any()

    @Volatile
    private var hookPreferences: SharedPreferences? = null

    fun attachHookPreferences(prefs: SharedPreferences) {
        hookPreferences = prefs
    }

    fun desiredOrNull(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean? {
        val key = preferenceKey(address, route) ?: return null
        if (prefs.contains(key)) return prefs.getBoolean(key, false)
        val legacyKey = legacyPreferenceKey(address, route) ?: return null
        return if (prefs.contains(legacyKey)) prefs.getBoolean(legacyKey, false) else null
    }

    fun isAutoApplyEnabled(address: String, route: HuaweiDeviceRoute): Boolean =
        desiredForHook(address, route) == true

    fun desiredForHook(address: String, route: HuaweiDeviceRoute): Boolean? =
        hookPreferences?.let { desiredOrNull(it, address, route) }

    fun setDesiredFromHook(
        address: String,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
    ): Boolean {
        val prefs = hookPreferences ?: return false
        return synchronized(syncLock) {
            writeDesired(prefs, address, route, enabled)
        }
    }

    fun setDesired(
        prefs: SharedPreferences,
        service: XposedService?,
        address: String,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
    ): Boolean {
        synchronized(syncLock) {
            if (!writeDesired(prefs, address, route, enabled)) return false
            val key = preferenceKey(address, route) ?: return false
            runCatching {
                service?.getRemotePreferences(ConfigManager.PREFS_NAME)
                    ?.edit()
                    ?.putBoolean(key, enabled)
                    ?.commit()
            }
        }
        return true
    }

    private fun writeDesired(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
    ): Boolean {
        val key = preferenceKey(address, route) ?: return false
        return prefs.edit().putBoolean(key, enabled).commit()
    }

    fun syncWithRemote(prefs: SharedPreferences, service: XposedService?) {
        val remotePrefs = service?.getRemotePreferences(ConfigManager.PREFS_NAME) ?: return
        syncWithRemote(prefs, remotePrefs)
    }

    internal fun syncWithRemote(
        prefs: SharedPreferences,
        remotePrefs: SharedPreferences,
    ) = synchronized(syncLock) {
        val local = lowLatencyValues(prefs)
        val remote = lowLatencyValues(remotePrefs)
        if (local.isNotEmpty()) {
            val editor = remotePrefs.edit()
            local.forEach(editor::putBoolean)
            editor.commit()
        }
        val missingLocal = remote.filterKeys { it !in local }
        if (missingLocal.isNotEmpty()) {
            val editor = prefs.edit()
            missingLocal.forEach(editor::putBoolean)
            editor.commit()
        }
    }

    internal fun preferenceKey(address: String, route: HuaweiDeviceRoute): String? {
        val normalizedAddress = address.trim().uppercase()
            .takeIf(bluetoothAddressPattern::matches)
            ?: return null
        if (!route.supportsLowLatencyControl) return null
        return AUTO_KEY_PREFIX + normalizedAddress.replace(":", "")
    }

    private fun legacyPreferenceKey(address: String, route: HuaweiDeviceRoute): String? {
        val normalizedAddress = address.trim().uppercase()
            .takeIf(bluetoothAddressPattern::matches)
            ?: return null
        val prefix = when (route) {
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> FREEBUDS5_PREFIX
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> FREECLIP2_PREFIX
            else -> return null
        }
        return prefix + normalizedAddress + KEY_SUFFIX
    }

    private fun lowLatencyValues(prefs: SharedPreferences): Map<String, Boolean> =
        prefs.all.mapNotNull { (key, value) ->
            if (value is Boolean && isLowLatencyKey(key)) key to value else null
        }.toMap()

    private fun isLowLatencyKey(key: String): Boolean =
        preferenceKeyPattern.matches(key)
}
