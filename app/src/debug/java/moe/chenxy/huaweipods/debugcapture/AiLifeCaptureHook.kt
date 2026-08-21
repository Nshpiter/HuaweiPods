package moe.chenxy.huaweipods.debugcapture

import android.app.Application
import android.app.BroadcastOptions
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.hook.HookContext
import moe.chenxy.huaweipods.hook.Log
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only observer for Huawei Smart Audio Bluetooth traffic.
 *
 * The hooks run after the platform call. Capture failures are isolated from the
 * caller. The explicit broadcast only schedules delivery; all disk work remains
 * in HuaweiPods' receiver process and never runs on the official app's thread.
 */
object AiLifeCaptureHook : HookContext() {
    private const val TAG = "HuaweiPods-SmartAudioCapture"
    private const val MAX_PAYLOAD_BYTES = 4 * 1024
    private const val MAX_SUMMARY_CHARS = 512
    private const val CURRENT_IDENTITY_SETTLE_DELAY_MS = 50L

    private val installed = AtomicBoolean(false)
    private val probeReceiverRegistered = AtomicBoolean(false)
    private val currentIdentityBroadcastScheduled = AtomicBoolean(false)
    private val mainHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }

    @Volatile
    private var probeReceiver: BroadcastReceiver? = null

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var lastObservedResource: SmartAudioObservedResource? = null

    override fun onHook() {
        if (!BuildConfig.DEBUG || !installed.compareAndSet(false, true)) return

        hookGattTraffic()
        hookBluetoothSocketIo()
        hookSmartAudioResourceRequests()
        hookSmartAudioCurrentDeviceIdentityUpdates()
        installProbeBridge()
        Log.i(TAG, "Bluetooth protocol and official resource capture enabled for package=$packageName")
    }

    private fun installProbeBridge() {
        resolveApplicationContext()?.let {
            registerProbeReceiver(it)
            return
        }

        runCatching {
            val attachMethod = Application::class.java.getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
            hookAfter(attachMethod) {
                val context = (instance as? Application)?.applicationContext
                    ?: args.firstOrNull() as? Context
                    ?: return@hookAfter
                registerProbeReceiver(context)
            }
        }.onFailure { Log.w(TAG, "Unable to install capture probe bridge", it) }
    }

    private fun registerProbeReceiver(context: Context) {
        if (!probeReceiverRegistered.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        applicationContext = appContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action == CaptureContract.ACTION_HOOK_PROBE) {
                    val context = receiverContext.applicationContext ?: receiverContext
                    broadcastHookReady(context)
                    broadcastCurrentSmartAudioDeviceIdentity(context)
                    lastObservedResource?.let {
                        broadcastSmartAudioResource(
                            context,
                            it,
                        )
                    }
                }
            }
        }
        runCatching {
            val filter = IntentFilter(CaptureContract.ACTION_HOOK_PROBE)
            // The probe originates from the HuaweiPods debug app and is received
            // inside the hooked Smart Audio process, so this receiver must be exported.
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            probeReceiver = receiver
            broadcastHookReady(appContext)
            broadcastCurrentSmartAudioDeviceIdentity(appContext)
        }.onFailure {
            probeReceiverRegistered.set(false)
            Log.w(TAG, "Unable to register capture probe receiver", it)
        }
    }

    private fun broadcastHookReady(context: Context) {
        val intent = Intent(CaptureContract.ACTION_CAPTURE_EVENT).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(CaptureContract.EXTRA_EVENT_TYPE, CaptureContract.EVENT_TYPE_HOOK_READY)
            putExtra(CaptureContract.EXTRA_SOURCE_PROCESS, sourceProcessName())
            putExtra(CaptureContract.EXTRA_TIMESTAMP_EPOCH_MS, System.currentTimeMillis())
        }
        sendCaptureBroadcast(context, intent)
    }

    /**
     * 智慧音频最终会把设备资源交给这个 OkHttp 入口。这里只观察 URL，
     * 且解析器仅接受已知华为 CDN 的固定路径；请求头和响应正文都不会离开官方进程。
     */
    private fun hookSmartAudioResourceRequests() {
        val okHttpUtilClass = runCatching {
            findClass("com.huawei.audiodevicekit.net.okhttp.OkHttpUtil")
        }.onFailure {
            Log.w(TAG, "Smart Audio resource request class unavailable", it)
        }.getOrNull() ?: return
        val requestMethods = okHttpUtilClass.declaredMethods.filter { method ->
            method.name == "asyncGetRequest" &&
                method.parameterTypes.firstOrNull() == String::class.java
        }
        if (requestMethods.isEmpty()) {
            Log.w(TAG, "Smart Audio resource request methods unavailable")
        }
        requestMethods.forEach { method ->
            runCatching {
                method.isAccessible = true
                hookBefore(method) {
                    val observed = SmartAudioResourceLocator.parseObservedUrl(
                        args.firstOrNull() as? String,
                    )?.copy(observedAtEpochMs = System.currentTimeMillis())
                        ?: return@hookBefore
                    lastObservedResource = preferCurrentResource(lastObservedResource, observed)
                    resolveApplicationContext()?.let { context ->
                        broadcastSmartAudioResource(context, observed)
                    }
                }
            }.onFailure { Log.w(TAG, "Unable to hook Smart Audio resource request", it) }
        }
    }

    private fun preferCurrentResource(
        previous: SmartAudioObservedResource?,
        observed: SmartAudioObservedResource,
    ): SmartAudioObservedResource = when {
        previous == null -> observed
        observed.subModelId != null -> observed
        previous.modelId == observed.modelId && previous.subModelId != null -> previous
        else -> observed
    }

    private fun broadcastSmartAudioResource(
        context: Context,
        resource: SmartAudioObservedResource,
    ) {
        val intent = Intent(CaptureContract.ACTION_CAPTURE_EVENT).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(
                CaptureContract.EXTRA_EVENT_TYPE,
                CaptureContract.EVENT_TYPE_SMART_AUDIO_RESOURCE,
            )
            putExtra(CaptureContract.EXTRA_RESOURCE_ORIGIN, resource.origin.wireName)
            putExtra(CaptureContract.EXTRA_RESOURCE_MODEL_ID, resource.modelId)
            resource.subModelId?.let {
                putExtra(CaptureContract.EXTRA_RESOURCE_SUB_MODEL_ID, it)
            }
            putExtra(CaptureContract.EXTRA_RESOURCE_KIND, resource.kind.name.lowercase())
            putExtra(CaptureContract.EXTRA_SOURCE_PROCESS, sourceProcessName())
            putExtra(CaptureContract.EXTRA_TIMESTAMP_EPOCH_MS, resource.observedAtEpochMs)
        }
        sendCaptureBroadcast(context, intent)
    }

    /**
     * 当前设备总线可能在最初的 Hook 探针之后才填入身份，尤其是旧协议机型或
     * 资源已命中缓存时。只观察 modelId、subModelId 和完整 DeviceInfo 写入口，
     * 并在短暂合并窗口后重读完整快照；不从单个参数拼接身份。MAC 写入口不单独
     * 触发，避免设备切换期间把上一副耳机的身份错误绑定到新地址。
     */
    private fun hookSmartAudioCurrentDeviceIdentityUpdates() {
        val adapterClass = runCatching {
            findClass("com.huawei.audiodevicekit.devicerouter.DefaultCurrentDeviceBusAdapter")
        }.onFailure {
            Log.w(TAG, "Smart Audio current-device adapter unavailable", it)
        }.getOrNull() ?: return
        val updateMethods = adapterClass.declaredMethods.filter { method ->
            isSmartAudioCurrentDeviceIdentityMutation(
                methodName = method.name,
                parameterTypeNames = method.parameterTypes.map { it.name },
            )
        }
        if (updateMethods.isEmpty()) {
            Log.w(TAG, "Smart Audio current-device identity update methods unavailable")
            return
        }
        updateMethods.forEach { method ->
            runCatching {
                method.isAccessible = true
                hookAfter(method) {
                    scheduleCurrentSmartAudioDeviceIdentityBroadcast()
                }
            }.onFailure {
                Log.w(TAG, "Unable to hook Smart Audio current-device identity update", it)
            }
        }
    }

    private fun scheduleCurrentSmartAudioDeviceIdentityBroadcast() {
        if (!currentIdentityBroadcastScheduled.compareAndSet(false, true)) return
        mainHandler.postDelayed(
            {
                currentIdentityBroadcastScheduled.set(false)
                resolveApplicationContext()?.let(::broadcastCurrentSmartAudioDeviceIdentity)
            },
            CURRENT_IDENTITY_SETTLE_DELAY_MS,
        )
    }

    /**
     * 从智慧音频自己的“当前设备”总线读取资源身份。
     *
     * 这条路径主要覆盖 FreeBuds 3 等旧协议机型，以及资源已经命中官方应用
     * 本地缓存、不会再次经过 OkHttp 的场景。广播仍携带真实蓝牙地址，模块侧
     * 会与用户本次明确选择的耳机地址再次比对，避免串到后台缓存的其他设备。
     */
    private fun broadcastCurrentSmartAudioDeviceIdentity(context: Context) {
        runCatching {
            val pluginClass = findClass("com.huawei.audiodevicekit.kitutils.plugin.Plugin")
            val currentDeviceBusClass = findClass("q7.a")
            val getPlugin = pluginClass.declaredMethods.firstOrNull { method ->
                method.name == "get" &&
                    method.parameterTypes.contentEquals(arrayOf(Class::class.java))
            } ?: return@runCatching
            getPlugin.isAccessible = true
            val currentDeviceBus = getPlugin.invoke(null, currentDeviceBusClass)
                ?: return@runCatching
            val deviceInfo = invokeNoArg(currentDeviceBus, "getDeviceInfo")
            val address = invokeString(currentDeviceBus, "T0")
                ?: deviceInfo?.let { invokeString(it, "c") }
                ?: return@runCatching
            val modelId = invokeString(currentDeviceBus, "c1")
                ?: deviceInfo?.let { invokeString(it, "g") }
            val subModelId = invokeString(currentDeviceBus, "Z0")
                ?: deviceInfo?.let { invokeString(it, "o") }
            val identity = smartAudioCurrentDeviceIdentity(modelId, subModelId)
                ?: return@runCatching

            val intent = Intent(CaptureContract.ACTION_CAPTURE_EVENT).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                putExtra(
                    CaptureContract.EXTRA_EVENT_TYPE,
                    CaptureContract.EVENT_TYPE_SMART_AUDIO_DEVICE_IDENTITY,
                )
                putExtra(CaptureContract.EXTRA_DEVICE_ADDRESS, address)
                putExtra(CaptureContract.EXTRA_RESOURCE_MODEL_ID, identity.modelId)
                putExtra(CaptureContract.EXTRA_RESOURCE_SUB_MODEL_ID, identity.subModelId)
                putExtra(CaptureContract.EXTRA_IDENTITY_SOURCE, "current_device_bus")
                putExtra(CaptureContract.EXTRA_SOURCE_PROCESS, sourceProcessName())
                putExtra(CaptureContract.EXTRA_TIMESTAMP_EPOCH_MS, System.currentTimeMillis())
            }
            sendCaptureBroadcast(context, intent)
        }.onFailure {
            // 智慧音频内部类在不同版本可能变化；失败时保留 DeviceInfo/URL 路径。
            Log.d(TAG, "Current Smart Audio device identity unavailable: ${it.javaClass.simpleName}")
        }
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? = runCatching {
        target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.isEmpty()
        }?.invoke(target)
    }.getOrNull()

    private fun invokeString(target: Any, methodName: String): String? =
        (invokeNoArg(target, methodName) as? String)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun hookGattTraffic() {
        val gattClass = runCatching { findClass("android.bluetooth.BluetoothGatt") }
            .onFailure { Log.w(TAG, "BluetoothGatt class unavailable", it) }
            .getOrNull() ?: return

        val characteristicWrites = gattClass.declaredMethods
            .filter { it.name == "writeCharacteristic" && it.isSupportedCharacteristicWrite() }
        characteristicWrites
            .preferModernOverload(modernParameterCount = 3)
            .forEach(::hookCharacteristicWrite)

        val descriptorWrites = gattClass.declaredMethods
            .filter { it.name == "writeDescriptor" && it.isSupportedDescriptorWrite() }
        descriptorWrites
            .preferModernOverload(modernParameterCount = 2)
            .forEach(::hookDescriptorWrite)

        gattClass.declaredMethods
            .filter { it.isSupportedGattRead() }
            .forEach(::hookGattRead)

        gattClass.declaredMethods
            .filter { it.isSupportedNotificationToggle() }
            .forEach(::hookNotificationToggle)

        hookGattInboundCallbacks()
    }

    private fun hookCharacteristicWrite(method: Method) {
        runCatching {
            method.isAccessible = true
            hookAfter(method) {
                runCatching {
                    val characteristic = args.firstOrNull() as? BluetoothGattCharacteristic
                        ?: return@runCatching
                    val rawValue = when (args.size) {
                        1 -> characteristic.value
                        3 -> args.getOrNull(1) as? ByteArray
                        else -> null
                    } ?: return@runCatching
                    val value = rawValue.copyPayload()
                    val apiVariant = if (args.size == 1) "legacy" else "value"
                    val writeType = if (args.size == 3) {
                        (args.getOrNull(2) as? Number)?.toInt()
                    } else {
                        characteristic.writeType
                    }
                    val address = (instance as? BluetoothGatt).maskedDeviceAddress()
                    val serviceUuid = runCatching { characteristic.service?.uuid?.toString() }.getOrNull()
                    val characteristicUuid = characteristic.uuid?.toString()

                    enqueueEvent(
                        CaptureEvent(
                            direction = "tx",
                            channel = "gatt_characteristic",
                            operation = "BluetoothGatt.writeCharacteristic.$apiVariant",
                            payload = value,
                            summary = buildSummary(
                                "device" to address,
                                "service" to serviceUuid,
                                "characteristic" to characteristicUuid,
                                "write_type" to writeType,
                                "result" to result.describeResult(),
                                "bytes" to rawValue.size,
                            ),
                            originalPayloadSize = rawValue.size,
                            deviceAddress = address,
                        ),
                    )
                }.onFailure { Log.w(TAG, "Unable to capture characteristic write", it) }
            }
        }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
    }

    private fun hookDescriptorWrite(method: Method) {
        runCatching {
            method.isAccessible = true
            hookAfter(method) {
                runCatching {
                    val descriptor = args.firstOrNull() as? BluetoothGattDescriptor
                        ?: return@runCatching
                    val rawValue = when (args.size) {
                        1 -> descriptor.value
                        2 -> args.getOrNull(1) as? ByteArray
                        else -> null
                    } ?: return@runCatching
                    val value = rawValue.copyPayload()
                    val apiVariant = if (args.size == 1) "legacy" else "value"
                    val characteristic = runCatching { descriptor.characteristic }.getOrNull()
                    val address = (instance as? BluetoothGatt).maskedDeviceAddress()
                    val serviceUuid = runCatching { characteristic?.service?.uuid?.toString() }.getOrNull()

                    enqueueEvent(
                        CaptureEvent(
                            direction = "tx",
                            channel = "gatt_descriptor",
                            operation = "BluetoothGatt.writeDescriptor.$apiVariant",
                            payload = value,
                            summary = buildSummary(
                                "device" to address,
                                "service" to serviceUuid,
                                "characteristic" to characteristic?.uuid?.toString(),
                                "descriptor" to descriptor.uuid?.toString(),
                                "result" to result.describeResult(),
                                "bytes" to rawValue.size,
                            ),
                            originalPayloadSize = rawValue.size,
                            deviceAddress = address,
                        ),
                    )
                }.onFailure { Log.w(TAG, "Unable to capture descriptor write", it) }
            }
        }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
    }

    private fun hookGattRead(method: Method) {
        runCatching {
            method.isAccessible = true
            hookAfter(method) {
                runCatching {
                    val target = args.firstOrNull() ?: return@runCatching
                    val gatt = instance as? BluetoothGatt
                    val address = gatt.maskedDeviceAddress()
                    val identity = when (target) {
                        is BluetoothGattCharacteristic -> GattIdentity(
                            serviceUuid = runCatching { target.service?.uuid?.toString() }.getOrNull(),
                            characteristicUuid = target.uuid?.toString(),
                        )

                        is BluetoothGattDescriptor -> {
                            val characteristic = runCatching { target.characteristic }.getOrNull()
                            GattIdentity(
                                serviceUuid = runCatching { characteristic?.service?.uuid?.toString() }.getOrNull(),
                                characteristicUuid = characteristic?.uuid?.toString(),
                                descriptorUuid = target.uuid?.toString(),
                            )
                        }

                        else -> return@runCatching
                    }
                    val channel = if (target is BluetoothGattDescriptor) {
                        "gatt_descriptor"
                    } else {
                        "gatt_characteristic"
                    }

                    enqueueEvent(
                        CaptureEvent(
                            direction = "tx",
                            channel = channel,
                            operation = "BluetoothGatt.${method.name}",
                            payload = ByteArray(0),
                            summary = buildSummary(
                                "device" to address,
                                "service" to identity.serviceUuid,
                                "characteristic" to identity.characteristicUuid,
                                "descriptor" to identity.descriptorUuid,
                                "result" to result.describeResult(),
                            ),
                            deviceAddress = address,
                        ),
                    )
                }.onFailure { Log.w(TAG, "Unable to capture ${method.name}", it) }
            }
        }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
    }

    private fun hookNotificationToggle(method: Method) {
        runCatching {
            method.isAccessible = true
            hookAfter(method) {
                runCatching {
                    val characteristic = args.firstOrNull() as? BluetoothGattCharacteristic
                        ?: return@runCatching
                    val enabled = args.getOrNull(1) as? Boolean ?: return@runCatching
                    val address = (instance as? BluetoothGatt).maskedDeviceAddress()

                    enqueueEvent(
                        CaptureEvent(
                            direction = "tx",
                            channel = "gatt_characteristic",
                            operation = "BluetoothGatt.setCharacteristicNotification",
                            payload = ByteArray(0),
                            summary = buildSummary(
                                "device" to address,
                                "service" to runCatching {
                                    characteristic.service?.uuid?.toString()
                                }.getOrNull(),
                                "characteristic" to characteristic.uuid?.toString(),
                                "enabled" to enabled,
                                "result" to result.describeResult(),
                            ),
                            deviceAddress = address,
                        ),
                    )
                }.onFailure { Log.w(TAG, "Unable to capture notification toggle", it) }
            }
        }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
    }

    /**
     * Android 15/16 receives GATT values through BluetoothGatt's framework
     * callback before dispatching them to the official application's callback.
     * Observing this point keeps the app's callback object and control flow intact.
     */
    private fun hookGattInboundCallbacks() {
        val callbackClass = runCatching {
            findClass("android.bluetooth.BluetoothGatt\$1")
        }.onFailure {
            Log.w(TAG, "BluetoothGatt framework callback unavailable", it)
        }.getOrNull() ?: return

        val methods = callbackClass.declaredMethods.filter { method ->
            method.name in setOf("onNotify", "onCharacteristicRead", "onDescriptorRead") &&
                method.parameterTypes.any { it == ByteArray::class.java }
        }
        if (methods.isEmpty()) {
            Log.w(TAG, "BluetoothGatt inbound callback methods unavailable")
        }
        methods.forEach(::hookGattInboundCallback)
    }

    private fun hookGattInboundCallback(method: Method) {
        runCatching {
            method.isAccessible = true
            hookAfter(method) {
                runCatching {
                    val rawValue = args.filterIsInstance<ByteArray>().lastOrNull()
                        ?: return@runCatching
                    val numbers = args.filterIsInstance<Number>().map { it.toInt() }
                    val isNotification = method.name == "onNotify"
                    val status = if (isNotification) null else numbers.getOrNull(0)
                    val handle = numbers.getOrNull(if (isNotification) 0 else 1)
                    val gatt = instance.findOuterGatt()
                    val identity = when (method.name) {
                        "onDescriptorRead" -> gatt.resolveGattIdentity(handle, descriptor = true)
                        else -> gatt.resolveGattIdentity(handle, descriptor = false)
                    }
                    val address = (args.firstOrNull { it is String } as? String)
                        .maskBluetoothAddress()
                        ?: gatt.maskedDeviceAddress()

                    enqueueEvent(
                        CaptureEvent(
                            direction = "rx",
                            channel = if (method.name == "onDescriptorRead") {
                                "gatt_descriptor"
                            } else {
                                "gatt_characteristic"
                            },
                            operation = "BluetoothGatt.callback.${method.name}",
                            payload = rawValue.copyPayload(),
                            originalPayloadSize = rawValue.size,
                            summary = buildSummary(
                                "device" to address,
                                "service" to identity.serviceUuid,
                                "characteristic" to identity.characteristicUuid,
                                "descriptor" to identity.descriptorUuid,
                                "handle" to handle,
                                "status" to status,
                                "bytes" to rawValue.size,
                            ),
                            deviceAddress = address,
                        ),
                    )
                }.onFailure { Log.w(TAG, "Unable to capture ${method.name}", it) }
            }
        }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
    }

    /**
     * BluetoothInputStream/BluetoothOutputStream delegate to these package-private
     * methods. Hooking here observes only BluetoothSocket traffic and does not
     * replace the streams returned to the official application.
     */
    private fun hookBluetoothSocketIo() {
        val socketClass = runCatching { findClass("android.bluetooth.BluetoothSocket") }
            .onFailure { Log.w(TAG, "BluetoothSocket class unavailable", it) }
            .getOrNull() ?: return

        val ioMethods = socketClass.declaredMethods.filter { method ->
            method.name in setOf("read", "write") &&
                method.parameterTypes.contentEquals(
                    arrayOf(ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                )
        }
        if (ioMethods.none { it.name == "read" }) {
            Log.w(TAG, "BluetoothSocket.read(byte[], int, int) unavailable")
        }
        if (ioMethods.none { it.name == "write" }) {
            Log.w(TAG, "BluetoothSocket.write(byte[], int, int) unavailable")
        }

        ioMethods.forEach { method ->
            runCatching {
                method.isAccessible = true
                hookAfter(method) {
                    runCatching {
                        val buffer = args.getOrNull(0) as? ByteArray ?: return@runCatching
                        val offset = (args.getOrNull(1) as? Number)?.toInt() ?: return@runCatching
                        val requestedLength = (args.getOrNull(2) as? Number)?.toInt()
                            ?: return@runCatching
                        val actualLength = if (method.name == "read") {
                            (result as? Number)?.toInt() ?: return@runCatching
                        } else {
                            requestedLength
                        }
                        if (actualLength <= 0) return@runCatching

                        val payload = buffer.copySafeRange(offset, actualLength)
                        if (payload.isEmpty()) return@runCatching
                        val socket = instance as? BluetoothSocket
                        val address = socket.maskedDeviceAddress()
                        val socketType = socket.connectionTypeOrNull()
                        val direction = if (method.name == "read") "rx" else "tx"

                        enqueueEvent(
                            CaptureEvent(
                                direction = direction,
                                channel = socketType.toChannelName(),
                                operation = "BluetoothSocket.${method.name}",
                                payload = payload,
                                originalPayloadSize = actualLength,
                                summary = buildSummary(
                                    "device" to address,
                                    "socket_type" to socketType,
                                    "bytes" to actualLength,
                                ),
                                deviceAddress = address,
                            ),
                        )
                    }.onFailure { Log.w(TAG, "Unable to capture BluetoothSocket.${method.name}", it) }
                }
            }.onFailure { Log.w(TAG, "Unable to hook ${method.toGenericString()}", it) }
        }
    }

    private fun enqueueEvent(event: CaptureEvent) {
        runCatching { broadcastEvent(event) }
            .onFailure { Log.w(TAG, "Unable to broadcast capture event", it) }
    }

    private fun broadcastEvent(event: CaptureEvent) {
        val context = resolveApplicationContext() ?: return
        val capturedPayload = if (event.payload.size > MAX_PAYLOAD_BYTES) {
            event.payload.copyOf(MAX_PAYLOAD_BYTES)
        } else {
            event.payload
        }
        val wasTruncated = event.originalPayloadSize > capturedPayload.size
        val summary = buildString {
            append(event.summary)
            if (wasTruncated) {
                if (isNotEmpty()) append(' ')
                append("captured_bytes=")
                append(capturedPayload.size)
                append(" truncated=true")
            }
        }.take(MAX_SUMMARY_CHARS)

        val intent = Intent(CaptureContract.ACTION_CAPTURE_EVENT).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(CaptureContract.EXTRA_EVENT_TYPE, "hook_event")
            putExtra(CaptureContract.EXTRA_DIRECTION, event.direction)
            putExtra(CaptureContract.EXTRA_CHANNEL, event.channel)
            putExtra(CaptureContract.EXTRA_OPERATION, event.operation)
            putExtra(CaptureContract.EXTRA_PAYLOAD_HEX, capturedPayload.toHex())
            putExtra(CaptureContract.EXTRA_SUMMARY, summary)
            putExtra(CaptureContract.EXTRA_SOURCE_PROCESS, sourceProcessName())
            event.deviceAddress?.let {
                putExtra(CaptureContract.EXTRA_DEVICE_ADDRESS, it)
            }
            putExtra(CaptureContract.EXTRA_TIMESTAMP_EPOCH_MS, event.timestampEpochMs)
        }
        sendCaptureBroadcast(context, intent)
    }

    private fun sendCaptureBroadcast(context: Context, intent: Intent) {
        val options = BroadcastOptions.makeBasic()
            .setShareIdentityEnabled(true)
            .toBundle()
        context.sendBroadcast(intent, null, options)
    }

    private fun resolveApplicationContext(): Context? {
        applicationContext?.let { return it }
        val current = runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
                .invoke(null) as? Application
        }.getOrNull() ?: return null
        return (current.applicationContext ?: current).also { applicationContext = it }
    }

    private fun sourceProcessName(): String =
        runCatching { Application.getProcessName() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: packageName

    private fun Method.isSupportedCharacteristicWrite(): Boolean {
        val types = parameterTypes
        return (types.size == 1 && types[0] == BluetoothGattCharacteristic::class.java) ||
            (types.size == 3 &&
                types[0] == BluetoothGattCharacteristic::class.java &&
                types[1] == ByteArray::class.java &&
                types[2] == Int::class.javaPrimitiveType)
    }

    private fun Method.isSupportedDescriptorWrite(): Boolean {
        val types = parameterTypes
        return (types.size == 1 && types[0] == BluetoothGattDescriptor::class.java) ||
            (types.size == 2 &&
                types[0] == BluetoothGattDescriptor::class.java &&
                types[1] == ByteArray::class.java)
    }

    private fun List<Method>.preferModernOverload(modernParameterCount: Int): List<Method> {
        val modern = filter { it.parameterCount == modernParameterCount }
        return modern.ifEmpty { this }
    }

    private fun Method.isSupportedGattRead(): Boolean {
        val types = parameterTypes
        return when (name) {
            "readCharacteristic" -> types.contentEquals(
                arrayOf(BluetoothGattCharacteristic::class.java),
            )

            "readDescriptor" -> types.contentEquals(
                arrayOf(BluetoothGattDescriptor::class.java),
            )

            else -> false
        }
    }

    private fun Method.isSupportedNotificationToggle(): Boolean =
        name == "setCharacteristicNotification" && parameterTypes.contentEquals(
            arrayOf(
                BluetoothGattCharacteristic::class.java,
                Boolean::class.javaPrimitiveType,
            ),
        )

    private fun Any?.findOuterGatt(): BluetoothGatt? {
        val target = this ?: return null
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            val field = currentClass.declaredFields.firstOrNull {
                BluetoothGatt::class.java.isAssignableFrom(it.type)
            }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target) as? BluetoothGatt
                }.getOrNull()
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun BluetoothGatt?.resolveGattIdentity(
        handle: Int?,
        descriptor: Boolean,
    ): GattIdentity {
        val gatt = this ?: return GattIdentity()
        val attributeHandle = handle ?: return GattIdentity()
        return if (descriptor) {
            val target = gatt.findGattAttribute("getDescriptorById", attributeHandle)
                as? BluetoothGattDescriptor
                ?: return GattIdentity()
            val characteristic = runCatching { target.characteristic }.getOrNull()
            GattIdentity(
                serviceUuid = runCatching { characteristic?.service?.uuid?.toString() }.getOrNull(),
                characteristicUuid = characteristic?.uuid?.toString(),
                descriptorUuid = target.uuid?.toString(),
            )
        } else {
            val target = gatt.findGattAttribute("getCharacteristicById", attributeHandle)
                as? BluetoothGattCharacteristic
                ?: return GattIdentity()
            GattIdentity(
                serviceUuid = runCatching { target.service?.uuid?.toString() }.getOrNull(),
                characteristicUuid = target.uuid?.toString(),
            )
        }
    }

    private fun BluetoothGatt.findGattAttribute(methodName: String, handle: Int): Any? {
        val method = javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.any { it == Int::class.javaPrimitiveType }
        } ?: return null
        val invocationArguments = arrayOfNulls<Any>(method.parameterCount)
        method.parameterTypes.forEachIndexed { index, type ->
            invocationArguments[index] = when {
                type == Int::class.javaPrimitiveType -> handle
                type.name == "android.bluetooth.BluetoothDevice" -> device
                else -> return null
            }
        }
        return runCatching {
            method.isAccessible = true
            method.invoke(this, *invocationArguments)
        }.getOrNull()
    }

    private fun ByteArray.copySafeRange(offset: Int, length: Int): ByteArray {
        if (offset < 0 || length <= 0 || offset >= size) return ByteArray(0)
        val capturedLength = length.coerceAtMost(MAX_PAYLOAD_BYTES)
        val endExclusive = (offset.toLong() + capturedLength.toLong())
            .coerceAtMost(size.toLong())
            .toInt()
        return copyOfRange(offset, endExclusive)
    }

    private fun ByteArray.copyPayload(): ByteArray =
        copyOf(size.coerceAtMost(MAX_PAYLOAD_BYTES))

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) append("%02X".format(byte.toInt() and 0xFF))
    }

    private fun String?.maskBluetoothAddress(): String? {
        val parts = this?.split(':') ?: return null
        if (parts.size != 6 || parts.any { it.length != 2 }) return null
        return "**:**:**:**:${parts[4].uppercase()}:${parts[5].uppercase()}"
    }

    private fun BluetoothGatt?.maskedDeviceAddress(): String? =
        runCatching { this?.device?.address.maskBluetoothAddress() }.getOrNull()

    private fun BluetoothSocket?.maskedDeviceAddress(): String? =
        runCatching { this?.remoteDevice?.address.maskBluetoothAddress() }.getOrNull()

    private fun BluetoothSocket?.connectionTypeOrNull(): Int? {
        this ?: return null
        return runCatching {
            javaClass.methods.firstOrNull {
                it.name == "getConnectionType" && it.parameterCount == 0
            }?.invoke(this) as? Number
        }.getOrNull()?.toInt()
    }

    private fun Int?.toChannelName(): String = when (this) {
        1 -> "rfcomm"
        2 -> "sco"
        3, 4 -> "l2cap"
        else -> "bluetooth_socket"
    }

    private fun Any?.describeResult(): String = when (this) {
        null -> "void"
        is Boolean -> "accepted=$this"
        is Number -> "status=$this"
        else -> toString().take(64)
    }

    private fun buildSummary(vararg values: Pair<String, Any?>): String =
        values.asSequence()
            .filter { it.second != null }
            .joinToString(" ") { (key, value) -> "$key=$value" }
            .take(MAX_SUMMARY_CHARS)

    private data class CaptureEvent(
        val direction: String,
        val channel: String,
        val operation: String,
        val payload: ByteArray,
        val originalPayloadSize: Int = payload.size,
        val summary: String,
        val deviceAddress: String?,
        val timestampEpochMs: Long = System.currentTimeMillis(),
    )

    private data class GattIdentity(
        val serviceUuid: String? = null,
        val characteristicUuid: String? = null,
        val descriptorUuid: String? = null,
    )

}
