package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs

/**
 * Shared wear-detection protocol used by the models for which a real capture proves 2B10/2B11.
 *
 * Keeping the protocol here avoids copying the same packets into each model-specific screen. A
 * route is added only after its capture confirms both the setter and the matching state query.
 */
object HuaweiWearDetectionController {
    private val mainHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }
    private val generation = AtomicLong(0L)
    private val pendingCallbacks = ConcurrentHashMap.newKeySet<Runnable>()
    private val supportedRoutes = setOf(
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        HuaweiDeviceRoute.HUAWEI_FREECLIP2,
    )
    private val stateQuery = hex("5A0005002B110100772A")
    private val disabledPacket = hex("5A0006002B10010100B977")
    private val enabledPacket = hex("5A0006002B10010101A956")

    fun supports(route: HuaweiDeviceRoute): Boolean = route in supportedRoutes

    /** 取消旧代所有延迟回读，避免 Runnable 把旧 ClassLoader 留在主线程队列。 */
    fun closeForHotReload() {
        generation.incrementAndGet()
        pendingCallbacks.toList().forEach(mainHandler::removeCallbacks)
        pendingCallbacks.clear()
    }

    fun stateQueryPacket(route: HuaweiDeviceRoute): ByteArray? =
        if (supports(route)) stateQuery.copyOf() else null

    fun setPacket(route: HuaweiDeviceRoute, enabled: Boolean): ByteArray? =
        if (supports(route)) {
            (if (enabled) enabledPacket else disabledPacket).copyOf()
        } else {
            null
        }

    fun parseState(stream: ByteArray): Boolean? =
        HuaweiFreeBuds5Controller.parseWearDetectionState(stream)

    fun setEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = setPacket(route, enabled)
        if (packet == null || !isExpectedTarget(context, device, route)) {
            onComplete?.invoke(false)
            return
        }
        val operationGeneration = generation.get()
        fun verifyWrite(attempt: Int) {
            requestState(context, device, route) { actual ->
                if (generation.get() != operationGeneration) return@requestState
                when {
                    actual == enabled -> onComplete?.invoke(true)
                    attempt >= WRITE_READBACK_ATTEMPTS -> onComplete?.invoke(false)
                    else -> postTracked(operationGeneration, WRITE_READBACK_RETRY_DELAY_MS) {
                        verifyWrite(attempt + 1)
                    }
                }
            }
        }
        HuaweiL2capAncController.sendRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "wear-detection enabled=$enabled",
            onComplete = { writeSucceeded ->
                if (generation.get() != operationGeneration) return@sendRawPacketOnce
                if (!writeSucceeded) {
                    onComplete?.invoke(false)
                    return@sendRawPacketOnce
                }
                if (onComplete == null) return@sendRawPacketOnce
                // 2B10 的通用 ACK 只能证明写入完成，不能证明耳机已经应用。
                // 稍等耳机落盘后用抓包确认的 2B11 回读做最终结果，避免 UI 假成功。
                postTracked(operationGeneration, WRITE_READBACK_DELAY_MS) {
                    verifyWrite(attempt = 1)
                }
            },
        )
    }

    fun requestState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onState: (Boolean?) -> Unit,
    ) {
        val packet = stateQueryPacket(route)
        if (packet == null || !isExpectedTarget(context, device, route)) {
            onState(null)
            return
        }
        val operationGeneration = generation.get()
        HuaweiL2capAncController.requestRawPacketOnce(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "wear-detection-state",
            responseWindowMs = 1_000L,
            onResponse = {
                if (generation.get() == operationGeneration) onState(parseState(it))
            },
        )
    }

    private fun postTracked(expectedGeneration: Long, delayMs: Long, block: () -> Unit) {
        lateinit var callback: Runnable
        callback = Runnable {
            pendingCallbacks.remove(callback)
            if (generation.get() == expectedGeneration) block()
        }
        pendingCallbacks += callback
        if (!mainHandler.postDelayed(callback, delayMs)) {
            pendingCallbacks.remove(callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun isExpectedTarget(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
    ): Boolean {
        if (!supports(route)) return false
        val address = runCatching { device.address }.getOrNull()
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) return false
        val name = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
        }.getOrNull()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceRoutePrefs.resolve(prefs, address, name) == route
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private const val WRITE_READBACK_DELAY_MS = 200L
    private const val WRITE_READBACK_RETRY_DELAY_MS = 300L
    private const val WRITE_READBACK_ATTEMPTS = 2
}
