package moe.chenxy.huaweipods.hook

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import java.lang.reflect.Method
import moe.chenxy.huaweipods.platform.RomFamily
import moe.chenxy.huaweipods.platform.RomIntegrationPolicy
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams

/** OPLUS 三方耳机电量数组：左、右、盒电量，随后是三者的充电状态；-1 表示未知。 */
internal const val COLOR_OS_BATTERY_UNKNOWN = -1
internal const val COLOR_OS_CHARGE_CHARGING = 1
internal const val COLOR_OS_CHARGE_NOT_CHARGING = 2
internal const val COLOR_OS_CHARGE_DISCONNECTED = 4
internal const val COLOR_OS_WEAR_WEARING = 0
internal const val COLOR_OS_WEAR_IN_CASE = 2
internal const val COLOR_OS_WEAR_UNKNOWN = 255

internal fun colorOsTwsBatteryInfo(battery: BatteryParams): IntArray {
    fun level(pod: PodParams?): Int =
        pod?.takeIf { it.isConnected }?.battery?.coerceIn(0, 100) ?: COLOR_OS_BATTERY_UNKNOWN

    fun charge(pod: PodParams?): Int = when {
        pod == null || !pod.isConnected -> COLOR_OS_CHARGE_DISCONNECTED
        pod.isCharging -> COLOR_OS_CHARGE_CHARGING
        else -> COLOR_OS_CHARGE_NOT_CHARGING
    }
    return intArrayOf(
        level(battery.left),
        level(battery.right),
        level(battery.case),
        charge(battery.left),
        charge(battery.right),
        charge(battery.case),
    )
}

/** 状态栏与蓝牙设置使用的单一电量：取在线耳机中较低的一侧，避免高估可用时长。 */
internal fun colorOsSystemBatteryLevel(battery: BatteryParams): Int =
    listOfNotNull(battery.left, battery.right)
        .filter { it.isConnected }
        .minOfOrNull { it.battery.coerceIn(0, 100) }
        ?: COLOR_OS_BATTERY_UNKNOWN

/**
 * 把华为耳机电量写入 ColorOS 自己的耳机电量通道。
 *
 * ColorOS 17 为 AirPods 与三方耳机提供了 `IOplusRemoteDevice`：蓝牙设置、我的设备和快速设备
 * 连接都通过 `OplusBluetoothDevice.getBatteryInfo()` 读取左右耳/盒电量，状态栏与电量小组件则读取
 * Android 标准电量。华为私有协议不会进入这两个通道，这里在蓝牙进程内补齐。
 *
 * 只登记电量，不声明降噪等可控特性：系统界面因此不会尝试通过 OBUDS AT 指令控制华为耳机。
 * 所有厂商接口均通过反射访问，缺失时退回 Android 标准电量。
 */
internal object ColorOsSystemBatteryBridge {
    private const val TAG = "HuaweiPods-ColorOsBattery"
    private const val FEATURE_CACHE = "com.oplus.bluetooth.common.OplusFeatureCache"
    private const val FEATURE_INTERFACE = "com.oplus.bluetooth.common.IOplusCommonFeature"
    private const val REMOTE_DEVICE_INTERFACE =
        "com.oplus.bluetooth.common.interfaces.IOplusRemoteDevice"
    private const val DEFAULT_TRANSPORT_HFP_AT = 1
    private const val ADAPTER_SERVICE = "com.android.bluetooth.btservice.AdapterService"

    @Volatile
    private var remoteDeviceUnavailableLogged = false
    @Volatile
    private var cachedRemoteDevice: Pair<ClassLoader, RemoteDevice>? = null

    private val enabled: Boolean by lazy {
        RomIntegrationPolicy.detect(Build.MANUFACTURER, Build.BRAND) == RomFamily.COLOR_OS
    }

    fun publish(
        context: Context,
        device: BluetoothDevice,
        battery: BatteryParams,
        inEar: Boolean? = null,
    ) {
        if (!enabled) return
        val info = colorOsTwsBatteryInfo(battery)
        val remote = oplusRemoteDevice(context)
        if (remote != null) {
            runCatching {
                remote.call(
                    "setOplusDeviceProtocolTransport",
                    arrayOf(BluetoothDevice::class.java, Int::class.javaPrimitiveType!!),
                    device,
                    remote.transportHfpAt,
                )
                remote.call(
                    "notifyWearStatusChanged",
                    arrayOf(
                        BluetoothDevice::class.java,
                        Int::class.javaPrimitiveType!!,
                        Int::class.javaPrimitiveType!!,
                    ),
                    device,
                    colorOsEarbudWearStatus(battery.left, inEar),
                    colorOsEarbudWearStatus(battery.right, inEar),
                )
                remote.call(
                    "notifyBatteryLevelChanged",
                    arrayOf(BluetoothDevice::class.java, IntArray::class.java),
                    device,
                    info,
                )
            }.onFailure {
                Log.w(TAG, "ColorOS earphone battery channel update failed", it)
            }
        }
        // OPLUS 在右耳缺席时会把 -1 当作最低值写入标准电量；这里再用在线耳机较低值校正。
        val systemLevel = colorOsSystemBatteryLevel(battery)
        if (systemLevel >= 0) setAndroidBatteryLevel(context, device, systemLevel)
        Log.d(TAG, "ColorOS battery published info=${info.joinToString()} system=$systemLevel inEar=$inEar")
    }

    fun clear(context: Context, device: BluetoothDevice) {
        if (!enabled) return
        setAndroidBatteryLevel(context, device, COLOR_OS_BATTERY_UNKNOWN)
    }

    private fun setAndroidBatteryLevel(context: Context, device: BluetoothDevice, level: Int) {
        val adapterService = adapterService(context) ?: return
        runCatching {
            adapterService.javaClass.findMethod(
                "setBatteryLevel",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            ).invoke(adapterService, device, level, false)
        }.onFailure {
            Log.w(TAG, "Android battery level update failed level=$level", it)
        }
    }

    /**
     * HuaweiHfpController 的 Context 通常是 A2dpService（ProfileService）；热重载恢复时可能是
     * Application。依次尝试 ProfileService.getAdapterService()、AdapterService 静态实例与
     * OPLUS 实现持有的 AdapterService（Android 17 已移除 AdapterService.getAdapterService()）。
     */
    private fun adapterService(context: Context): Any? {
        if (context.javaClass.name == ADAPTER_SERVICE) return context
        runCatching { context.javaClass.findMethod("getAdapterService").invoke(context) }
            .getOrNull()?.let { return it }
        runCatching {
            Class.forName(ADAPTER_SERVICE, false, context.classLoader)
                .getDeclaredField("sAdapterService")
                .apply { isAccessible = true }
                .get(null)
        }.getOrNull()?.let { return it }
        return oplusRemoteDevice(context)?.instance?.let { remote ->
            runCatching {
                remote.javaClass.getDeclaredField("mAdapterService").apply { isAccessible = true }.get(remote)
            }.getOrNull()
        }
    }

    private class RemoteDevice(val instance: Any, val type: Class<*>, val transportHfpAt: Int) {
        private val methods = OptionalVendorMethods(type)
        private val reported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun call(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?) {
            // ColorOS 16 does not expose all of ColorOS 17's battery extensions. A missing
            // transport/wear method must not prevent the battery or Android fallback update.
            runCatching {
                val method = methods.find(name, parameterTypes)
                if (method == null) {
                    if (reported.add(name)) Log.i(TAG, "ColorOS optional battery method unavailable: $name")
                } else {
                    method.invoke(instance, *args)
                }
            }.onFailure {
                if (reported.add("failed:$name")) Log.w(TAG, "ColorOS battery method failed: $name", it)
            }
        }
    }

    private fun oplusRemoteDevice(context: Context): RemoteDevice? {
        val classLoader = context.classLoader ?: return null
        cachedRemoteDevice?.takeIf { it.first === classLoader }?.let { return it.second }
        // 只缓存成功结果：蓝牙栈尚未完成 OPLUS 扩展注册时，下次上报会重新解析。
        return resolveOplusRemoteDevice(classLoader)?.also {
            cachedRemoteDevice = classLoader to it
        }
    }

    private fun resolveOplusRemoteDevice(classLoader: ClassLoader): RemoteDevice? = runCatching {
        val remoteType = Class.forName(REMOTE_DEVICE_INTERFACE, false, classLoader)
        val featureType = Class.forName(FEATURE_INTERFACE, false, classLoader)
        val default = remoteType.getField("DEFAULT").get(null)
        val instance = Class.forName(FEATURE_CACHE, false, classLoader)
            .getMethod("get", featureType)
            .invoke(null, default)
            ?: return@runCatching null
        val transport = runCatching { remoteType.getField("TRANSPORT_HFP_AT").getInt(null) }
            .getOrDefault(DEFAULT_TRANSPORT_HFP_AT)
        RemoteDevice(instance, remoteType, transport)
    }.onFailure {
        if (!remoteDeviceUnavailableLogged) {
            remoteDeviceUnavailableLogged = true
            Log.i(TAG, "ColorOS earphone battery channel unavailable; using Android battery only")
        }
    }.getOrNull()

    private fun Class<*>.findMethod(name: String, vararg parameterTypes: Class<*>): Method {
        var type: Class<*>? = this
        while (type != null) {
            runCatching { return type.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }
            type = type.superclass
        }
        throw NoSuchMethodException(name)
    }
}
