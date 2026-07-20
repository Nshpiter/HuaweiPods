package moe.chenxy.huaweipods.pods

enum class HuaweiDeviceRoute {
    HUAWEI_FREEBUDS3,
    UNSUPPORTED,
}

fun detectHuaweiDeviceRoute(deviceName: String?): HuaweiDeviceRoute {
    return when (deviceName?.let(::normalizeDeviceName).orEmpty()) {
        "huaweifreebuds3", "freebuds3" -> HuaweiDeviceRoute.HUAWEI_FREEBUDS3
        else -> HuaweiDeviceRoute.UNSUPPORTED
    }
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}
