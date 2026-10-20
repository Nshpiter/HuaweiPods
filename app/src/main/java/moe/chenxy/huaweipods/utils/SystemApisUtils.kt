package moe.chenxy.huaweipods.utils

import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice


object SystemApisUtils {

    val BluetoothDevice.DEVICE_TYPE_UNTETHERED_HEADSET: String
        get() = "Untethered Headset"

    val BluetoothDevice.METADATA_MANUFACTURER_NAME: Int get() = 0
    val BluetoothDevice.METADATA_MODEL_NAME: Int get() = 1
    val BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET: Int get() = 6
    val BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON: Int get() = 7
    val BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON: Int get() = 8
    val BluetoothDevice.METADATA_UNTETHERED_CASE_ICON: Int get() = 9
    val BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY: Int get() = 10
    val BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY: Int get() = 11
    val BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY: Int get() = 12
    val BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING: Int get() = 13
    val BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING: Int get() = 14
    val BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING: Int get() = 15
    val BluetoothDevice.METADATA_DEVICE_TYPE: Int get() = 17
    val BluetoothDevice.METADATA_MAIN_BATTERY: Int get() = 18
    val BluetoothDevice.METADATA_MAIN_CHARGING: Int get() = 19

    const val BATTERY_LEVEL_UNKNOWN: Int = -1

    fun BluetoothDevice.getMetadata(key: Int): ByteArray? {
        return callMethod(this, "getMetadata", key) as ByteArray?
    }

    fun BluetoothDevice.setMetadata(key: Int, value: ByteArray): Boolean {
        return callMethod(this, "setMetadata", key, value) as Boolean
    }

    fun StatusBarManager.setIconVisibility(iconName: String, show: Boolean) {
        callMethod(this, "setIconVisibility", iconName, show)
    }

    private fun callMethod(instance: Any, methodName: String, vararg args: Any?): Any? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
                it.isAccessible = true
                return it.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException(methodName)
    }
}
