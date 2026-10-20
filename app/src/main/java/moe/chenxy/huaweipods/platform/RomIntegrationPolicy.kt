package moe.chenxy.huaweipods.platform

enum class RomFamily {
    COLOR_OS,
    GENERIC_ANDROID,
}

/** 本分支只适配 ColorOS；其他 ROM 只保留模块界面与 Android 通用能力。 */
object RomIntegrationPolicy {
    const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
    const val SETTINGS_PACKAGE = "com.android.settings"
    const val OPLUS_MELODY_PACKAGE = "com.oplus.melody"
    const val OPLUS_WIRELESS_SETTINGS_PACKAGE = "com.oplus.wirelesssettings"
    const val HEYTAP_ACCESSORY_PACKAGE = "com.heytap.accessory"
    const val HEYTAP_MY_DEVICES_PACKAGE = "com.heytap.mydevices"

    fun detect(manufacturer: String?, brand: String?): RomFamily {
        val identity = listOf(manufacturer, brand)
            .joinToString(" ")
            .lowercase()
        return if (listOf("oppo", "oneplus", "realme", "oplus").any(identity::contains)) {
            RomFamily.COLOR_OS
        } else {
            RomFamily.GENERIC_ANDROID
        }
    }

    /** 华为协议引擎、连接弹窗、常驻通知与系统电量都运行在系统蓝牙进程中。 */
    fun requiredCoreScopes(family: RomFamily): Set<String> = setOf(BLUETOOTH_PACKAGE)

    fun colorOsHostPackages(): Set<String> = setOf(
        HEYTAP_ACCESSORY_PACKAGE,
        HEYTAP_MY_DEVICES_PACKAGE,
        OPLUS_MELODY_PACKAGE,
        OPLUS_WIRELESS_SETTINGS_PACKAGE,
    )

    fun notificationHostPackage(): String = BLUETOOTH_PACKAGE
}
