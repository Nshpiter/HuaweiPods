package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.resolveBoundOrNamedRoute

object HuaweiDeviceRouteResolver {
    @Volatile
    private var prefs: SharedPreferences? = null
    private val sessionBindings = ConcurrentHashMap<String, HuaweiDeviceRoute>()

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        sessionBindings.clear()
    }

    fun refreshBindings() {
        sessionBindings.clear()
    }

    fun resolve(address: String?, deviceName: String?): HuaweiDeviceRoute {
        val normalizedAddress = normalizeAddress(address)
        if (prefs?.let { DeviceRoutePrefs.isAutomatic(it, normalizedAddress) } == true) {
            normalizedAddress?.let(sessionBindings::remove)
            return resolveBoundOrNamedRoute(null, deviceName)
        }
        val persistedRoute = prefs?.let { DeviceRoutePrefs.find(it, normalizedAddress) }
        if (persistedRoute != null) {
            val route = resolveBoundOrNamedRoute(persistedRoute, deviceName)
            if (route.isSupported && normalizedAddress != null) {
                sessionBindings[normalizedAddress] = route
            } else if (normalizedAddress != null) {
                sessionBindings.remove(normalizedAddress)
            }
            return route
        }

        val sessionRoute = normalizedAddress
            ?.let(sessionBindings::get)
            ?.takeIf(::isHuaweiDeviceRouteEnabled)
        val route = resolveBoundOrNamedRoute(sessionRoute, deviceName)
        if (route.isSupported && normalizedAddress != null) {
            sessionBindings[normalizedAddress] = route
        } else if (normalizedAddress != null) {
            sessionBindings.remove(normalizedAddress)
        }
        return route
    }

    private fun normalizeAddress(address: String?): String? {
        return address?.trim()?.takeIf(String::isNotEmpty)?.uppercase()
    }
}

fun resolveHuaweiDeviceRoute(
    address: String?,
    deviceName: String?,
): HuaweiDeviceRoute {
    return HuaweiDeviceRouteResolver.resolve(address, deviceName)
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.huaweiDeviceRoute(): HuaweiDeviceRoute {
    val deviceName = runCatching {
        name?.takeIf(String::isNotBlank)
            ?: alias?.takeIf(String::isNotBlank)
    }.getOrNull()
    val deviceAddress = runCatching { address }.getOrNull()
    return resolveHuaweiDeviceRoute(deviceAddress, deviceName)
}
