package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import moe.chenxy.huaweipods.pods.HuaweiBatteryParser
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiHfpController
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported

/**
 * 处理华为耳机通过 HFP 私有 AT 指令上报的电量（`AT+HUAWEIBATTERY` 等）。
 *
 * AOSP 不认识这些指令，会回复 ERROR 并让耳机停止上报。这里在 `HeadsetStateMachine.processUnknownAt`
 * 之前接管：FreeBuds 3 需要先应答能力查询，其他型号直接解析电量交给 [HuaweiHfpController]。
 */
@SuppressLint("MissingPermission")
class HuaweiHfpBatteryAtHook : HookContext() {
    private val TAG = "HuaweiPods-HfpBattery"

    override fun onHook() {
        hookHuaweiHfpBattery()
    }

    private fun hookHuaweiHfpBattery() {
        hookHuaweiUnknownAtCommands()
    }

    private fun hookHuaweiUnknownAtCommands() {
        val stateMachineClass = findClassOrNull("com.android.bluetooth.hfp.HeadsetStateMachine") ?: run {
            Log.d(TAG, "Huawei HFP hook skipped: HeadsetStateMachine not found")
            return
        }
        val methods = stateMachineClass.declaredMethods.filter { method ->
            method.name == "processUnknownAt" &&
                method.parameterTypes.contentEquals(
                    arrayOf(String::class.java, BluetoothDevice::class.java),
                )
        }
        if (methods.isEmpty()) {
            Log.w(TAG, "Huawei HFP hook skipped: processUnknownAt(String, BluetoothDevice) not found")
            return
        }
        methods
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    hookBefore(method) {
                        val text = args.filterIsInstance<String>().firstOrNull() ?: return@hookBefore
                        val device = args.filterIsInstance<BluetoothDevice>().firstOrNull()
                            ?: objectField(instance, "mDevice")
                            ?: return@hookBefore
                        val route = device.huaweiDeviceRoute()
                        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
                            handleHuaweiBatteryAt(text, device, instance, "unknown-at:${method.name}")
                            return@hookBefore
                        }

                        when (classifyFreeBuds3BatteryAtCommand(text)) {
                            FreeBuds3BatteryAtCommand.CAPABILITY_QUERY -> {
                                val sent = sendFreeBuds3BatteryCapabilityResponse(
                                    stateMachine = instance,
                                    device = device,
                                )
                                if (sent) {
                                    result = null
                                    Log.i(TAG, "FreeBuds 3 battery capability query accepted address=${device.address}")
                                }
                            }
                            FreeBuds3BatteryAtCommand.BATTERY_REPORT -> {
                                // 分类器已确认载荷合法。即使 UI Context 暂未就绪，也必须阻止
                                // AOSP 对华为私有上报追加 ERROR，否则耳机会停止后续更新。
                                sendAtResponseCode(instance, device, AT_RESPONSE_OK)
                                result = null
                                runCatching {
                                    handleHuaweiBatteryAt(
                                        text,
                                        device,
                                        instance,
                                        "unknown-at:${method.name}",
                                    )
                                }.onFailure {
                                    Log.w(TAG, "FreeBuds 3 battery report dispatch failed", it)
                                }
                            }
                            FreeBuds3BatteryAtCommand.CLOSE -> {
                                sendAtResponseCode(instance, device, AT_RESPONSE_OK)
                                result = null
                                Log.d(TAG, "FreeBuds 3 battery stream close acknowledged address=${device.address}")
                            }
                            FreeBuds3BatteryAtCommand.OTHER -> Unit
                        }
                    }
                    Log.d(TAG, "Huawei HFP processUnknownAt hook installed method=${method.name}")
                }.onFailure {
                    Log.w(TAG, "Huawei HFP processUnknownAt hook skipped method=${method.name}", it)
                }
            }
    }

    private fun handleHuaweiBatteryAt(
        text: String,
        device: BluetoothDevice?,
        source: Any?,
        reason: String
    ): Boolean {
        if (!text.contains("HUAWEIBATTERY", ignoreCase = true)) return false
        val ctx = contextFrom(source) ?: run {
            Log.w(TAG, "Huawei HFP battery skipped: context null reason=$reason text=$text")
            return false
        }
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei HFP battery skipped: device null reason=$reason text=$text")
            return false
        }
        if (!currentDevice.huaweiDeviceRoute().isSupported) return false
        return HuaweiHfpController.handleAtCommand(ctx, currentDevice, text) != null
    }

    private fun findHeadsetNativeInterface(stateMachine: Any): Any? {
        objectField<Any>(stateMachine, "mNativeInterface")
            ?.takeIf { it.javaClass.name.endsWith(".HeadsetNativeInterface") }
            ?.let { return it }
        var type: Class<*>? = stateMachine.javaClass
        while (type != null) {
            type.declaredFields.firstOrNull { field ->
                field.type.name.endsWith(".HeadsetNativeInterface")
            }?.let { field ->
                return runCatching {
                    field.isAccessible = true
                    field.get(stateMachine)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun invokeAtResponseString(
        nativeInterface: Any,
        device: BluetoothDevice,
        response: String,
    ): Boolean {
        var type: Class<*>? = nativeInterface.javaClass
        while (type != null) {
            type.declaredMethods.firstOrNull { method ->
                method.name == "atResponseString" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(BluetoothDevice::class.java, String::class.java),
                    )
            }?.let { method ->
                method.isAccessible = true
                return method.invoke(nativeInterface, device, response) as? Boolean == true
            }
            type = type.superclass
        }
        throw NoSuchMethodException("atResponseString(BluetoothDevice, String)")
    }

    private fun sendFreeBuds3BatteryCapabilityResponse(
        stateMachine: Any?,
        device: BluetoothDevice,
    ): Boolean = runCatching {
        val nativeInterface = stateMachine?.let(::findHeadsetNativeInterface)
            ?: throw NoSuchFieldException("HeadsetNativeInterface")
        val sent = invokeAtResponseString(
            nativeInterface = nativeInterface,
            device = device,
            response = FREEBUDS3_BATTERY_RESPONSE,
        )
        if (sent) {
            runCatching {
                invokeAtResponseCode(nativeInterface, device, AT_RESPONSE_OK)
            }.onFailure {
                // 信息响应已经发出后仍必须吞掉宿主的 ERROR，终止 OK 失败只记录日志。
                Log.w(TAG, "FreeBuds 3 capability terminal OK failed address=${device.address}", it)
            }
        }
        sent
    }.onFailure {
        Log.w(TAG, "FreeBuds 3 battery capability response failed address=${device.address}", it)
    }.getOrDefault(false)

    private fun sendAtResponseCode(
        stateMachine: Any?,
        device: BluetoothDevice,
        responseCode: Int,
    ): Boolean = runCatching {
        val nativeInterface = stateMachine?.let(::findHeadsetNativeInterface)
            ?: throw NoSuchFieldException("HeadsetNativeInterface")
        invokeAtResponseCode(nativeInterface, device, responseCode)
    }.onFailure {
        Log.w(TAG, "Huawei HFP AT response failed code=$responseCode address=${device.address}", it)
    }.getOrDefault(false)

    private fun invokeAtResponseCode(
        nativeInterface: Any,
        device: BluetoothDevice,
        responseCode: Int,
    ): Boolean {
        var type: Class<*>? = nativeInterface.javaClass
        while (type != null) {
            type.declaredMethods.firstOrNull { method ->
                method.name == "atResponseCode" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(
                            BluetoothDevice::class.java,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                        ),
                    )
            }?.let { method ->
                method.isAccessible = true
                return method.invoke(nativeInterface, device, responseCode, 0) as? Boolean == true
            }
            type = type.superclass
        }
        throw NoSuchMethodException("atResponseCode(BluetoothDevice, int, int)")
    }

    private fun contextFrom(source: Any?): Context? {
        return source as? Context
            ?: objectField<Context>(source, "mService")
            ?: objectField<Context>(source, "mHeadsetService")
            ?: objectField<Context>(source, "mAdapterService")
            ?: currentApplicationOrNull()
    }

    private fun findClassOrNull(className: String): Class<*>? {
        return runCatching { findClass(className) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> objectField(instance: Any?, fieldName: String): T? {
        return runCatching { getObjectField(instance, fieldName) as? T }.getOrNull()
    }

    private companion object {
        private const val FREEBUDS3_BATTERY_RESPONSE = "+HUAWEIBATTERY=OK"
        private const val AT_RESPONSE_OK = 1
    }
}

internal enum class FreeBuds3BatteryAtCommand {
    CAPABILITY_QUERY,
    BATTERY_REPORT,
    CLOSE,
    OTHER,
}

internal fun classifyFreeBuds3BatteryAtCommand(text: String): FreeBuds3BatteryAtCommand {
    val normalized = text.trim().uppercase().removePrefix("AT")
    return when {
        normalized == "+HUAWEIBATTERY=?" || normalized == "+HUAWEIBATTERY:?" ->
            FreeBuds3BatteryAtCommand.CAPABILITY_QUERY
        isValidFreeBuds3BatteryClose(normalized) ->
            FreeBuds3BatteryAtCommand.CLOSE
        (
            normalized.startsWith("+HUAWEIBATTERY=") ||
                normalized.startsWith("+HUAWEIBATTERY:") ||
                normalized.startsWith("+UPDATEHUAWEIBATTERY=") ||
                normalized.startsWith("+UPDATEHUAWEIBATTERY:")
            ) && isCompleteFreeBuds3BatteryReport(text) ->
            FreeBuds3BatteryAtCommand.BATTERY_REPORT
        else -> FreeBuds3BatteryAtCommand.OTHER
    }
}

private fun isValidFreeBuds3BatteryClose(normalized: String): Boolean {
    val prefix = when {
        normalized.startsWith("+CLOSEHUAWEIBATTERY=") -> "+CLOSEHUAWEIBATTERY="
        normalized.startsWith("+CLOSEHUAWEIBATTERY:") -> "+CLOSEHUAWEIBATTERY:"
        else -> return false
    }
    return normalized.removePrefix(prefix).trim().toIntOrNull() != null
}

private fun isCompleteFreeBuds3BatteryReport(text: String): Boolean {
    val payload = text.substringAfter('=', missingDelimiterValue = "")
        .ifEmpty { text.substringAfter(':', missingDelimiterValue = "") }
    val numbers = payload.split(',').map { token ->
        token.trim().toIntOrNull() ?: return false
    }
    val pairCount = numbers.firstOrNull() ?: return false
    if (pairCount <= 0 || numbers.size != pairCount * 2 + 1) return false
    return HuaweiBatteryParser.parse(text) != null
}
