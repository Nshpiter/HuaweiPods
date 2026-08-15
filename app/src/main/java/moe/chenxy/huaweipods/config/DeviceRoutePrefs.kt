package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.detectKnownHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isHuaweiDeviceRouteEnabled

object DeviceRoutePrefs {
    private const val PREF_KEY_PREFIX = "device_route_v1_"
    private val bluetoothAddressPattern = Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$")
    private val bindingLock = Any()

    fun find(prefs: SharedPreferences, address: String?): HuaweiDeviceRoute? {
        val key = bindingKey(address) ?: return null
        return prefs.getString(key, null)
            ?.let(::routeFromStorage)
            ?.takeIf(::isHuaweiDeviceRouteEnabled)
    }

    fun resolve(
        prefs: SharedPreferences?,
        address: String?,
        deviceName: String?,
    ): HuaweiDeviceRoute {
        val boundRoute = prefs?.let { find(it, address) }
        return resolveBoundOrNamedRoute(boundRoute, deviceName)
    }

    fun bind(
        prefs: SharedPreferences,
        service: XposedService?,
        address: String,
        route: HuaweiDeviceRoute,
    ) {
        synchronized(bindingLock) {
            bindLocked(prefs, address, route)
            runCatching {
                service
                    ?.getRemotePreferences(ConfigManager.PREFS_NAME)
                    ?.let { bindLocked(it, address, route) }
            }
        }
    }

    fun bind(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean = synchronized(bindingLock) {
        bindLocked(prefs, address, route)
    }

    /**
     * 自动识别只允许填补空绑定或确认同一路由，绝不覆盖用户已经手选的不同路由。
     * 本地与远程写入和手动 bind 共用一把进程锁，封住 watchdog 后迟到 Binder 的竞态。
     */
    fun bindIfAbsent(
        prefs: SharedPreferences,
        service: XposedService?,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean = synchronized(bindingLock) {
        if (!bindIfAbsentLocked(prefs, address, route)) return@synchronized false
        runCatching {
            service
                ?.getRemotePreferences(ConfigManager.PREFS_NAME)
                ?.let { bindIfAbsentLocked(it, address, route) }
        }
        true
    }

    internal fun bindIfAbsent(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean = synchronized(bindingLock) {
        bindIfAbsentLocked(prefs, address, route)
    }

    private fun bindLocked(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val key = bindingKey(address) ?: return false
        if (!isHuaweiDeviceRouteEnabled(route)) return false
        // 注入进程拿到的 LSPosed RemotePreferences 可能是只读实现。
        // 绑定应由模块 App 主动完成，任何误用都不能让系统宿主进程崩溃。
        return runCatching {
            prefs.edit()
                .putString(key, route.storageId())
                .commit()
        }.getOrDefault(false)
    }

    private fun bindIfAbsentLocked(
        prefs: SharedPreferences,
        address: String,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val key = bindingKey(address) ?: return false
        if (!isHuaweiDeviceRouteEnabled(route)) return false
        val existingRoute = prefs.getString(key, null)?.let(::routeFromStorage)
        if (existingRoute != null) return existingRoute == route
        return bindLocked(prefs, address, route)
    }

    fun syncWithRemote(
        prefs: SharedPreferences,
        service: XposedService?,
    ) {
        synchronized(bindingLock) {
            val remotePrefs = service?.getRemotePreferences(ConfigManager.PREFS_NAME)
                ?: return@synchronized
            val localBindings = validBindings(prefs)
            val remoteBindings = validBindings(remotePrefs)

            if (localBindings.isNotEmpty()) {
                val remoteEditor = remotePrefs.edit()
                localBindings.forEach(remoteEditor::putString)
                remoteEditor.commit()
            }
            val missingLocalBindings = remoteBindings.filterKeys { it !in localBindings }
            if (missingLocalBindings.isNotEmpty()) {
                val localEditor = prefs.edit()
                missingLocalBindings.forEach(localEditor::putString)
                localEditor.commit()
            }
        }
    }

    fun isBindingKey(key: String?): Boolean {
        return key?.startsWith(PREF_KEY_PREFIX) == true
    }

    internal fun bindingKey(address: String?): String? {
        val normalized = normalizeAddress(address) ?: return null
        return PREF_KEY_PREFIX + normalized.replace(":", "")
    }

    private fun normalizeAddress(address: String?): String? {
        val normalized = address?.trim()?.uppercase() ?: return null
        return normalized.takeIf(bluetoothAddressPattern::matches)
    }

    private fun HuaweiDeviceRoute.storageId(): String = when (this) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS3 -> "freebuds3"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS4E -> "freebuds4e"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> "freebuds5"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> "freebuds6i"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 -> "freebuds_pro3"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 -> "freebuds_pro4"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 -> "freebuds_pro5"
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I -> "freebuds7i"
        HuaweiDeviceRoute.HUAWEI_FREECLIP -> "freeclip"
        HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> "freeclip2"
        HuaweiDeviceRoute.HUAWEI_EYEWEAR -> "eyewear"
        HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> "eyewear2"
        HuaweiDeviceRoute.UNSUPPORTED -> "unsupported"
    }

    private fun routeFromStorage(value: String): HuaweiDeviceRoute? = when (value) {
        "freebuds3" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS3
        "freebuds4e" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS4E
        "freebuds5" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        "freebuds6i" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        "freebuds_pro3" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3
        "freebuds_pro4" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4
        "freebuds_pro5" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5
        "freebuds7i" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
        "freeclip" -> HuaweiDeviceRoute.HUAWEI_FREECLIP
        "freeclip2" -> HuaweiDeviceRoute.HUAWEI_FREECLIP2
        "eyewear" -> HuaweiDeviceRoute.HUAWEI_EYEWEAR
        "eyewear2" -> HuaweiDeviceRoute.HUAWEI_EYEWEAR2
        else -> null
    }

    private fun validBindings(prefs: SharedPreferences): Map<String, String> {
        return prefs.all.mapNotNull { (key, value) ->
            if (!isBindingKey(key)) return@mapNotNull null
            val route = (value as? String)
                ?.let(::routeFromStorage)
                ?.takeIf(::isHuaweiDeviceRouteEnabled)
                ?: return@mapNotNull null
            key to route.storageId()
        }.toMap()
    }
}

internal fun resolveBoundOrNamedRoute(
    boundRoute: HuaweiDeviceRoute?,
    deviceName: String?,
): HuaweiDeviceRoute {
    val enabledBoundRoute = boundRoute?.takeIf(::isHuaweiDeviceRouteEnabled)
    val knownNamedRoute = detectKnownHuaweiDeviceRoute(deviceName)
    if (
        enabledBoundRoute != null &&
        knownNamedRoute.isHuaweiRoute() &&
        knownNamedRoute != enabledBoundRoute
    ) {
        return HuaweiDeviceRoute.UNSUPPORTED
    }
    val enabledNamedRoute = knownNamedRoute
        .takeIf(::isHuaweiDeviceRouteEnabled)
        ?: HuaweiDeviceRoute.UNSUPPORTED
    return enabledBoundRoute ?: enabledNamedRoute
}

private fun HuaweiDeviceRoute.isHuaweiRoute(): Boolean {
    return this != HuaweiDeviceRoute.UNSUPPORTED
}
