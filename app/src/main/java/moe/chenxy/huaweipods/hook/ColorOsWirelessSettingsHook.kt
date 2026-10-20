package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** ColorOS 17 蓝牙设置的默认三方耳机厂商白名单（`HfpAtVendorManager`）。 */
internal val COLOR_OS_SETTINGS_DEFAULT_3RD_VENDORS = listOf(2688, 1356, 301, 2016, 3686)

/** 与蓝牙设置相同的白名单解析：属性为逗号分隔的十六进制厂商 ID，缺省时使用内置列表。 */
internal fun colorOsSettingsThirdPartyVendors(property: String?): List<Int> {
    val raw = property?.trim().orEmpty()
    if (raw.isEmpty()) return COLOR_OS_SETTINGS_DEFAULT_3RD_VENDORS
    return raw.split(',').mapNotNull { token -> token.trim().takeIf(String::isNotEmpty)?.toIntOrNull(16) }
}

/**
 * ColorOS 蓝牙设置（`com.oplus.wirelesssettings`）集成。
 *
 * 蓝牙设置只对厂商 ID 在三方耳机白名单内的设备显示左右耳电量摘要和“耳机设置”入口，并把入口
 * 交给“我的设备”的三方耳机面板。这里仅在设置进程内让华为耳机通过白名单判断（不改动蓝牙栈的
 * 设备属性），电量摘要直接读取模块已写入系统的耳机电量；“耳机设置”则改为打开 HuaweiPods。
 */
@SuppressLint("MissingPermission")
object ColorOsWirelessSettingsHook : HookContext() {
    private const val TAG = "HuaweiPods-ColorOsWirelessSettings"
    private const val BLUETOOTH_NATIVE = "com.oplus.wirelesssettings.api.BluetoothNative"
    private const val HFP_AT_VENDOR_PROPERTY = "persist.bluetooth.3rd_earbuds_hfp_at_vendor"
    private const val MY_DEVICES_DETAIL_PANEL_ACTION = "com.oplus.mydevices.ACTION_DEVICE_DETAILED_PANEL"

    private val allowedVendors: List<Int> by lazy {
        colorOsSettingsThirdPartyVendors(
            runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, HFP_AT_VENDOR_PROPERTY, "") as? String
            }.getOrNull(),
        )
    }

    override fun onHook() {
        runCatching { hookVendorId() }
            .onSuccess { Log.i(TAG, "Bluetooth settings vendor hook installed") }
            .onFailure { Log.w(TAG, "Bluetooth settings vendor hook unavailable", it) }
        runCatching { hookEarphoneEntryLaunch() }
            .onFailure { Log.w(TAG, "Bluetooth settings entry hook unavailable", it) }
    }

    private fun hookVendorId() {
        val method = findMethod(BLUETOOTH_NATIVE, "getDeviceVendorId", BluetoothDevice::class.java)
        hookAfter(method) {
            val vendor = result as? Int ?: return@hookAfter
            if (vendor in allowedVendors) return@hookAfter
            val device = args.firstOrNull() as? BluetoothDevice ?: return@hookAfter
            if (!isHuaweiDevice(device)) return@hookAfter
            allowedVendors.firstOrNull()?.let { result = it }
        }
    }

    private fun hookEarphoneEntryLaunch() {
        val method = Activity::class.java.getDeclaredMethod(
            "startActivityForResult",
            Intent::class.java,
            Int::class.javaPrimitiveType,
            Bundle::class.java,
        )
        hookBefore(method) {
            val intent = args.getOrNull(0) as? Intent ?: return@hookBefore
            if (intent.action != MY_DEVICES_DETAIL_PANEL_ACTION) return@hookBefore
            val address = intent.getStringExtra("device_mac_info") ?: return@hookBefore
            val device = runCatching { BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) }
                .getOrNull() ?: return@hookBefore
            if (!isHuaweiDevice(device)) return@hookBefore
            val popupIntent = Intent(HuaweiPodsAction.ACTION_SHOW_PODS_UI).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                putExtra("bluetoothaddress", device.address)
                putExtra("device_address", device.address)
                putExtra("navigate_page", "earphone_detail")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            Log.i(TAG, "Bluetooth settings earphone entry redirected to HuaweiPods")
            proceedWithArgs(popupIntent, -1, args.getOrNull(2))
        }
    }

    private fun isHuaweiDevice(device: BluetoothDevice): Boolean =
        runCatching { device.huaweiDeviceRoute().isSupported }.getOrDefault(false)
}
