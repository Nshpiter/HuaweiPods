package moe.chenxy.huaweipods.pods

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.BuildConfig

/**
 * 低时延写入命令。以下支持范围均来自对应机型抓包中的同一 2B6C/01 开关帧，
 * 未抓到 setter 的型号不会出现在能力表中，也不会接收该命令。
 */
object HuaweiLowLatencyController {
    private val DISABLED_PACKET = decodeLowLatencyHex("5A0006002B6C010100B430")
    private val ENABLED_PACKET = decodeLowLatencyHex("5A0006002B6C010101A411")

    fun setEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val address = runCatching { device.address }.getOrNull()
        if (!route.supportsLowLatencyControl ||
            address == null ||
            !BluetoothAdapter.checkBluetoothAddress(address)
        ) {
            onComplete?.invoke(false)
            return
        }
        if (usesSharedConnection(context.packageName, route)) {
            HuaweiSharedControlClient.request(
                context, device, route, HuaweiSharedControlPolicy.LOW_LATENCY_SET,
                enabled = enabled,
            ) { response ->
                // 这是蓝牙进程的写出确认，不代表耳机协议提供了状态回读。
                onComplete?.invoke(response?.contentEquals(byteArrayOf(1)) == true)
            }
            return
        }
        sendInHost(context, device, route, enabled, onComplete = onComplete)
    }

    internal fun usesSharedConnection(packageName: String, route: HuaweiDeviceRoute): Boolean =
        packageName == BuildConfig.APPLICATION_ID && route.supportsLowLatencyControl

    internal fun sendInHost(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        shouldWrite: () -> Boolean = { true },
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        HuaweiL2capAncController.sendRawPacket(
            context = context,
            device = device,
            route = route,
            packet = packet(enabled),
            description = "${route.name.lowercase()} low-latency enabled=$enabled",
            // SE 4 ANC / 6i 的音效也在宿主复用连接；其他型号仍按原策略在写后释放，
            // 避免长期占用通道，阻塞尚未迁移到宿主的页面控制项。
            keepSocket = HuaweiSharedControlPolicy.supports(route),
            shouldWrite = shouldWrite,
            onComplete = onComplete,
        )
    }

    internal fun packet(enabled: Boolean): ByteArray =
        (if (enabled) ENABLED_PACKET else DISABLED_PACKET).copyOf()
}

private fun decodeLowLatencyHex(value: String): ByteArray =
    value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
