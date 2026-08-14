package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Bundle
import android.os.Binder
import android.os.Parcel
import android.os.SystemClock
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiBatteryParser
import moe.chenxy.huaweipods.pods.HuaweiHfpController
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.resolveHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.pods.usesReportedEarbudAvailability
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability
import moe.chenxy.huaweipods.utils.SystemApisUtils.cancelAsUser
import org.json.JSONObject

@SuppressLint("MissingPermission")
class BluetoothUpstreamHeadsetHook : HookContext() {
    private data class RegisteredHuaweiCallback(
        val callback: Any,
        val address: String,
    )

    private data class CallbackCaller(
        val uid: Int,
        val pid: Int,
    )

    private data class PendingHuaweiCallback(
        val address: String,
        val createdAtMs: Long,
    )

    private val TAG = "HuaweiPods-Upstream"
    private val DESCRIPTOR = "com.android.bluetooth.ble.app.IMiuiHeadsetService"
    private val knownHuaweiAddresses = linkedSetOf<String>()
    private val callbacks = ConcurrentHashMap<IBinder, RegisteredHuaweiCallback>()
    private val pendingHuaweiCallbacks = ConcurrentHashMap<CallbackCaller, PendingHuaweiCallback>()
    private val pendingHuaweiCallbackTtlMs = 3_000L
    private val handler = Handler(Looper.getMainLooper())
    private val hookedBinderClasses = linkedSetOf<String>()
    @Volatile
    private var lastHuaweiDevice: BluetoothDevice? = null
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentBattery: BatteryParams? = null
    private var currentAnc = 1
    private var currentAncSubMode: Int? = null
    @Volatile
    private var currentAddress: String? = null
    @Volatile
    private var currentName: String? = null
    @Volatile
    private var currentRoute: HuaweiDeviceRoute? = null

    override fun onHook() {
        hookHeadsetServiceBinder()
        hookMiuiHeadsetBinder()
        hookNotificationBatteryUpstream()
        hookHuaweiHfpBattery()
    }

    private fun hookNotificationBatteryUpstream() {
        val notificationApiClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotificationApi")
        if (notificationApiClass != null) {
            runCatching {
                hookBefore(
                    notificationApiClass.method(
                        "showNewConnectedToast",
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        BluetoothDevice::class.java,
                        String::class.java
                    )
                ) {
                    val device = args[4] as? BluetoothDevice
                    if (!isHuaweiPod(device)) return@hookBefore
                    val battery = effectiveBattery() ?: return@hookBefore
                    val leftBattery = displayBattery(battery.left) ?: (args[1] as? Int ?: 0)
                    val rightBattery = displayBattery(battery.right) ?: (args[2] as? Int ?: 0)
                    val wearState = displayWearState(battery, args[3] as? Int ?: 1)
                    val notification = currentMiuiBluetoothNotification() ?: return@hookBefore
                    result = null
                    callMethod(
                        notification,
                        "showConnectedToast",
                        args[0] as? Int ?: 2,
                        leftBattery,
                        rightBattery,
                        wearState,
                        device,
                        args[5] as? String
                    )
                    Log.d(TAG, "showNewConnectedToast patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState oldLeft=${args[1]} oldRight=${args[2]} oldWear=${args[3]}")
                }
                Log.d(TAG, "MiuiBluetoothNotificationApi.showNewConnectedToast hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotificationApi.showNewConnectedToast skipped", it) }
        }

        val notificationClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
        val requestClass = findClassOrNull("com.android.bluetooth.ble.app.C4705R2")
        if (notificationClass != null) {
            runCatching {
                hookBefore(notificationClass.method("invokeStatusBar", Context::class.java, String::class.java, Bundle::class.java)) {
                    val bundle = args[2] as? Bundle
                    if (shouldInterceptHeadsetWearIsland(bundle)) {
                        when (ConfigManager.islandMode()) {
                            ConfigManager.ISLAND_MODE_NONE, ConfigManager.ISLAND_MODE_MODULE -> {
                                result = null
                                Log.d(TAG, "invokeStatusBar swallowed headset_wear_notification island mode=${ConfigManager.islandMode()}")
                                return@hookBefore
                            }
                        }
                    }
                    patchHeadsetWearIslandBundle(bundle)
                    Log.d(TAG, "invokeStatusBar upstream action=${args[1]} bundle=$bundle focus=${bundle?.getString("miui.focus.param")}")
                }
                Log.d(TAG, "MiuiBluetoothNotification.invokeStatusBar debug hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.invokeStatusBar skipped", it) }
        }
        if (notificationClass != null && requestClass != null) {
            runCatching {
                hookAfter(notificationClass.method("updateParameters", requestClass)) {
                    val request = args[0] ?: return@hookAfter
                    val device = getObjectField(request, "f18110e") as? BluetoothDevice
                    if (!isHuaweiPod(device)) return@hookAfter
                    val battery = effectiveBattery() ?: return@hookAfter
                    val leftBattery = displayBattery(battery.left)
                    val rightBattery = displayBattery(battery.right)
                    val wearState = displayWearState(battery, getObjectField(request, "f18109d") as? Int ?: 1)
                    leftBattery?.let { setObjectField(request, "f18107b", it) }
                    rightBattery?.let { setObjectField(request, "f18108c", it) }
                    setObjectField(request, "f18109d", wearState)
                    Log.d(TAG, "updateParameters patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState")
                }
                Log.d(TAG, "MiuiBluetoothNotification.updateParameters hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.updateParameters skipped", it) }
        }
    }

    private fun hookHeadsetServiceBinder() {
        val serviceClassName = "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService"
        val serviceClass = findClassOrNull(serviceClassName)
        if (serviceClass != null) {
            runCatching {
                hookAfter(serviceClass.method("onBind", Intent::class.java)) {
                    registerStatusReceiver(instance as? Context)
                    val binder = result ?: return@hookAfter
                    installHeadsetBinderHooks(binder.javaClass)
                }
                Log.d(TAG, "BluetoothHeadsetService.onBind hook installed package=$packageName")
            }.onFailure { Log.w(TAG, "hook BluetoothHeadsetService.onBind failed package=$packageName", it) }
            runCatching {
                hookAfter(serviceClass.method("onCreate")) {
                    registerStatusReceiver(instance as? Context)
                }
                Log.d(TAG, "BluetoothHeadsetService.onCreate hook installed package=$packageName")
            }.onFailure { Log.d(TAG, "hook BluetoothHeadsetService.onCreate skipped package=$packageName: ${it.message}") }
        } else {
            Log.d(TAG, "BluetoothHeadsetService class not present package=$packageName")
        }

        listOf(
            "com.android.bluetooth.ble.app.headset.BinderC6776v",
            "com.android.bluetooth.ble.app.headset.v"
        ).forEach { className ->
            findClassOrNull(className)?.let { installHeadsetBinderHooks(it) }
        }
    }

    private fun hookHuaweiHfpBattery() {
        if (packageName != "com.android.bluetooth") return
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
        if (!isHuaweiPod(currentDevice)) return false
        val battery = HuaweiHfpController.handleAtCommand(ctx, currentDevice, text) ?: return false
        currentBattery = battery
        return true
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
        return context
            ?: source as? Context
            ?: objectField<Context>(source, "mService")
            ?: objectField<Context>(source, "mHeadsetService")
            ?: objectField<Context>(source, "mAdapterService")
    }

    private fun findClassOrNull(className: String): Class<*>? {
        return runCatching { findClass(className) }.getOrNull()
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        notifyRealStatus("config-changed")
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        if (!targetsCurrentHuaweiDevice(receivedIntent)) return
                        pendingHuaweiCallbacks.clear()
                        clearCurrentDeviceStatus()
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentBattery = (
                            receivedIntent.batteryStatusFromExtras()
                                ?: receivedIntent.parcelableStatus()
                                ?: currentBattery
                            )?.let(::normalizeBatteryAvailabilityForCurrentRoute)
                    }
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        val route = currentHuaweiRoute()
                        val mode = NoiseControlMode.fromBroadcastStatus(
                            receivedIntent.getIntExtra("status", currentAnc),
                        )
                        if (route.supportsAnc && (mode != NoiseControlMode.TRANSPARENCY || route.supportsTransparency)) {
                            currentAnc = mode.broadcastStatus
                            currentAncSubMode = receivedIntent.getIntExtra("submode", -1)
                                .takeIf { receivedIntent.hasExtra("submode") && it >= 0 }
                        } else {
                            currentAnc = NoiseControlMode.OFF.broadcastStatus
                            currentAncSubMode = null
                        }
                    }
                }
                Log.d(TAG, "state action=${receivedIntent.action} address=$currentAddress name=$currentName anc=$currentAnc battery=${currentBattery.debugString()}")
                notifyRealStatus("broadcast:${receivedIntent.action}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun installHeadsetBinderHooks(binderClass: Class<*>) {
        val className = binderClass.name
        if (!hookedBinderClasses.add(className)) return
        Log.d(TAG, "BluetoothHeadsetService binder class=$className")

        runCatching {
            hookBefore(binderClass.method("checkSupport", BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isHuaweiPod(device)) {
                    clearPendingHuaweiCallback()
                    return@hookBefore
                }
                val route = currentHuaweiRoute()
                if (!shouldExposeMiuiAdvancedHeadsetUi(route)) {
                    clearPendingHuaweiCallback()
                    Log.d(TAG, "BinderC6776v.checkSupport left native route=$route device=${device.describe()}")
                    return@hookBefore
                }
                lastHuaweiDevice = device
                rememberPendingHuaweiCallback(device?.address)
                result = fakeSupport()
                Log.d(TAG, "BinderC6776v.checkSupport forced device=${device.describe()} support=$result")
            }
            Log.d(TAG, "BinderC6776v.checkSupport hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.checkSupport skipped", it) }

        hookAddressStringResult(binderClass, listOf("getDeviceInfo"), "getDeviceInfo") { fakeSupport() }
        hookAddressStringResult(binderClass, listOf("isSupportAudioSwitch", "mo19775z1", "z1"), "isSupportAudioSwitch") { "1" }
        hookAddressBooleanResult(binderClass, listOf("isMiTWS", "mo19771O0", "O0"), "isMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("checkIsMiTWS", "mo19766B", "B"), "checkIsMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("getRingFindState", "mo19772m0", "m0"), "getRingFindState", false)

        runCatching {
            val method = binderClass.declaredMethods.firstOrNull { candidate ->
                candidate.returnType == String::class.java &&
                    candidate.parameterTypes.contentEquals(
                        arrayOf(
                            Int::class.javaPrimitiveType!!,
                            String::class.java,
                            BluetoothDevice::class.java,
                        ),
                    )
            } ?: return@runCatching
            method.isAccessible = true
            hookBefore(method) {
                val command = args[0] as? Int
                val value = args[1] as? String
                val device = args[2] as? BluetoothDevice
                if (!isHuaweiPod(device)) return@hookBefore
                // 114/115 是通知栏开关；114 的值“1”表示断开设备，继续交给原实现。
                if (command == COMMAND_SET_DETAIL_NOTIFICATION ||
                    command == COMMAND_GET_DETAIL_NOTIFICATION
                ) {
                    detailNotificationResponse(command, value, device)?.let { result = it }
                    return@hookBefore
                }
                lastHuaweiDevice = device
                result = when (command) {
                    102 -> "1"
                    123 -> "4"
                    else -> "1"
                }
                Log.d(TAG, "BinderC6776v.setCommonCommand forced command=$command value=$value device=${device.describe()} result=$result")
                sendRealStatus(device, "setCommonCommand:$command")
            }
            Log.d(TAG, "BinderC6776v.setCommonCommand hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.setCommonCommand skipped", it) }

        hookBinderVoidDevice(binderClass, "connect") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDevice(binderClass, "getDeviceConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDeviceString(binderClass, "getCommonConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderAncMode(binderClass)
        hookBinderAncLevel(binderClass)

        runCatching {
            val callbackClass = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback")
            hookBefore(binderClass.method("register", callbackClass)) {
                val callback = args[0]
                val address = consumePendingHuaweiCallbackAddress()
                if (callback == null || address == null) return@hookBefore
                rememberCallback(callback, address)
                result = null
                Log.d(TAG, "BinderC6776v.register swallowed callback=$callback address=$address")
                requestBluetoothStatus("register")
                sendRealStatus(address, "register")
                sendRealStatusDelayed(address, "register-refresh", 350L)
            }
            hookBefore(binderClass.method("registerCallbackDevice", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isHuaweiPod(device) || callback == null) return@hookBefore
                lastHuaweiDevice = device
                rememberCallback(callback, device)
                result = null
                Log.d(TAG, "BinderC6776v.registerCallbackDevice swallowed callback=$callback device=${device.describe()}")
                requestBluetoothStatus("registerCallbackDevice")
                sendRealStatus(device, "registerCallbackDevice")
                sendRealStatusDelayed(device, "registerCallbackDevice-refresh", 350L)
            }
            hookBefore(binderClass.method("unregister", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isHuaweiPod(device) || callback == null) return@hookBefore
                forgetCallback(callback)
                result = null
                Log.d(TAG, "BinderC6776v.unregister swallowed callback=$callback device=${device.describe()}")
            }
            Log.d(TAG, "BinderC6776v callback hooks installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v callback methods skipped", it) }
    }

    private fun hookBinderVoidDevice(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isHuaweiPod(device)) return@hookBefore
                lastHuaweiDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed device=${device.describe()}")
                requestBluetoothStatus(methodName)
                after(device, methodName)
                sendRealStatusDelayed(device, "$methodName-refresh", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookAddressStringResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: () -> String) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isHuaweiAddress(address)) return@hookBefore
                val route = routeForKnownHuaweiAddress(address)
                if (!shouldExposeMiuiAdvancedHeadsetUi(route)) {
                    Log.d(TAG, "BinderC6776v.$label left native route=$route address=$address method=$methodName")
                    return@hookBefore
                }
                result = forced()
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$result method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookAddressBooleanResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: Boolean) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isHuaweiAddress(address)) return@hookBefore
                val route = routeForKnownHuaweiAddress(address)
                if (!shouldExposeMiuiAdvancedHeadsetUi(route)) {
                    Log.d(TAG, "BinderC6776v.$label left native route=$route address=$address method=$methodName")
                    return@hookBefore
                }
                result = forced
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$forced method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookBinderVoidDeviceString(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java, String::class.java)) {
                val device = args[0] as? BluetoothDevice
                val value = args[1] as? String
                if (!isHuaweiPod(device)) return@hookBefore
                lastHuaweiDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed value=$value device=${device.describe()}")
                requestBluetoothStatus("$methodName:$value")
                after(device, "$methodName:$value")
                sendRealStatusDelayed(device, "$methodName-refresh:$value", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookBinderAncMode(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncMode", Int::class.java, BluetoothDevice::class.java)) {
                val mode = args[0] as? Int
                val device = args[1] as? BluetoothDevice
                if (!isHuaweiPod(device)) return@hookBefore
                lastHuaweiDevice = device
                result = null
                val route = currentHuaweiRoute()
                val selection = mode?.let { upstreamHuaweiAncStateForMode(route, it, currentHuaweiAncState()) }
                Log.d(TAG, "BinderC6776v.changeAncMode swallowed mode=$mode route=$route selection=$selection device=${device.describe()}")
                if (selection != null) {
                    sendHuaweiAnc(route, selection, device)
                } else {
                    sendRealStatus(device, "changeAncMode-invalid:$mode")
                }
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncMode skipped", it) }
    }

    private fun hookBinderAncLevel(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncLevel", String::class.java, BluetoothDevice::class.java)) {
                val level = args[0] as? String
                val device = args[1] as? BluetoothDevice
                if (!isHuaweiPod(device)) return@hookBefore
                lastHuaweiDevice = device
                result = null
                val route = currentHuaweiRoute()
                val selection = level?.let { upstreamHuaweiAncStateForLevel(route, it, currentHuaweiAncState()) }
                Log.d(TAG, "BinderC6776v.changeAncLevel swallowed level=$level route=$route selection=$selection device=${device.describe()}")
                if (selection != null) {
                    sendHuaweiAnc(route, selection, device)
                } else {
                    sendRealStatus(device, "changeAncLevel-invalid:$level")
                }
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncLevel skipped", it) }
    }

    private fun rememberCallback(callback: Any, device: BluetoothDevice?) {
        val address = runCatching { device?.address }.getOrNull()?.takeIf(String::isNotBlank) ?: return
        rememberCallback(callback, address)
    }

    private fun rememberCallback(callback: Any, address: String) {
        (callMethod(callback, "asBinder") as? IBinder)?.let {
            callbacks[it] = RegisteredHuaweiCallback(callback, address)
        }
    }

    private fun forgetCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks.remove(it) }
    }

    private fun firstExistingClass(vararg classNames: String): String? {
        return classNames.firstOrNull { className ->
            runCatching { findClass(className) }.isSuccess
        }
    }

    private fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method {
        return getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }

    private fun hookMiuiHeadsetBinder() {
        val stubClass = firstExistingClass("com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub") ?: run {
            Log.d(TAG, "IMiuiHeadsetService.Stub fallback not found")
            return
        }
        runCatching {
            hookBefore(findMethod(stubClass, "onTransact", Int::class.java, Parcel::class.java, Parcel::class.java, Int::class.java)) {
                val code = args[0] as? Int ?: return@hookBefore
                if (code != TRANSACTION_SET_COMMON_COMMAND) return@hookBefore
                val data = args[1] as? Parcel ?: return@hookBefore
                val reply = args[2] as? Parcel ?: return@hookBefore
                handleTransaction(code, data, reply)?.let { handled ->
                    result = handled
                }
            }
            Log.d(TAG, "IMiuiHeadsetService.Stub.onTransact hooked class=$stubClass")
        }.onFailure { Log.w(TAG, "hook IMiuiHeadsetService.Stub.onTransact skipped", it) }
    }

    private fun handleTransaction(code: Int, data: Parcel, reply: Parcel): Boolean? {
        val originalPosition = data.dataPosition()
        return runCatching {
            data.enforceInterface(DESCRIPTOR)
            when (code) {
                1 -> handleCheckSupport(data, reply)
                2 -> handleRegister(data, reply)
                3 -> handleUnregister(data)
                4 -> handleDeviceVoid("connect", data, reply)
                9 -> handleAncMode(data, reply)
                10 -> handleAncLevel(data, reply)
                11 -> handleAddressString("getDeviceInfo", data, reply, fakeSupport())
                12 -> handleDeviceVoid("getDeviceConfig", data, reply)
                14 -> handleSetCommonCommand(data, reply)
                15 -> handleCommonConfig(data, reply)
                16 -> handleRegisterCallbackDevice(data, reply)
                18 -> handleAddressBoolean("isMiTWS", data, reply, true)
                19 -> handleAddressBoolean("checkIsMiTWS", data, reply, true)
                20 -> handleAddressString("isSupportAudioSwitch", data, reply, "1")
                24 -> handleAddressBoolean("getRingFindState", data, reply, false)
                else -> null
            }
        }.onFailure {
            Log.w(TAG, "onTransact inspect failed code=$code", it)
        }.also {
            data.setDataPosition(originalPosition)
        }.getOrNull()
    }

    private fun handleCheckSupport(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        val route = currentHuaweiRoute()
        val exposeAdvancedUi = isHuawei && shouldExposeMiuiAdvancedHeadsetUi(route)
        Log.d(
            TAG,
            "checkSupport upstream device=${device.describe()} isHuawei=$isHuawei " +
                "route=$route exposeAdvancedUi=$exposeAdvancedUi",
        )
        if (!isHuawei) {
            clearPendingHuaweiCallback()
            return null
        }
        if (!exposeAdvancedUi) {
            clearPendingHuaweiCallback()
            return null
        }
        lastHuaweiDevice = device
        rememberPendingHuaweiCallback(device?.address)
        reply.writeNoException()
        val support = fakeSupport()
        reply.writeString(support)
        Log.d(TAG, "checkSupport upstream forced $support")
        return true
    }

    private fun handleRegister(data: Parcel, reply: Parcel): Boolean? {
        val callback = data.readCallbackBinder()
        val address = consumePendingHuaweiCallbackAddress()
        Log.d(TAG, "register upstream callback=$callback pendingAddress=$address")
        if (callback == null || address == null) return null
        rememberCallback(callback, address)
        reply.writeNoException()
        sendRealStatus(address, "register")
        return true
    }

    private fun handleUnregister(data: Parcel): Boolean? {
        val binder = data.readStrongBinder() ?: return null
        callbacks.remove(binder)
        Log.d(TAG, "unregister upstream callback removed=$binder")
        return null
    }

    private fun handleDeviceVoid(method: String, data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "$method upstream device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei) return null
        lastHuaweiDevice = device
        reply.writeNoException()
        Log.d(TAG, "$method upstream no-op for Huawei")
        sendRealStatus(device, method)
        return true
    }

    private fun handleAncMode(data: Parcel, reply: Parcel): Boolean? {
        val mode = data.readInt()
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "changeAncMode upstream mode=$mode device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei) return null
        lastHuaweiDevice = device
        val route = currentHuaweiRoute()
        val selection = upstreamHuaweiAncStateForMode(route, mode, currentHuaweiAncState())
        if (selection != null) {
            sendHuaweiAnc(route, selection, device)
        } else {
            sendRealStatus(device, "changeAncMode-invalid:$mode")
        }
        reply.writeNoException()
        return true
    }

    private fun handleAncLevel(data: Parcel, reply: Parcel): Boolean? {
        val level = data.readString()
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "changeAncLevel upstream level=$level device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei) return null
        lastHuaweiDevice = device
        val route = currentHuaweiRoute()
        val selection = level?.let { upstreamHuaweiAncStateForLevel(route, it, currentHuaweiAncState()) }
        if (selection != null) {
            sendHuaweiAnc(route, selection, device)
        } else {
            sendRealStatus(device, "changeAncLevel-invalid:$level")
        }
        reply.writeNoException()
        return true
    }

    private fun handleAddressString(method: String, data: Parcel, reply: Parcel, forced: String): Boolean? {
        val address = data.readString()
        val isHuawei = address != null && isHuaweiAddress(address)
        val route = address?.let(::routeForKnownHuaweiAddress) ?: HuaweiDeviceRoute.UNSUPPORTED
        val exposeAdvancedUi = isHuawei && shouldExposeMiuiAdvancedHeadsetUi(route)
        Log.d(
            TAG,
            "$method upstream address=$address isHuawei=$isHuawei route=$route " +
                "exposeAdvancedUi=$exposeAdvancedUi",
        )
        if (!exposeAdvancedUi) return null
        reply.writeNoException()
        reply.writeString(forced)
        Log.d(TAG, "$method upstream forced $forced")
        return true
    }

    private fun handleAddressBoolean(method: String, data: Parcel, reply: Parcel, forced: Boolean): Boolean? {
        val address = data.readString()
        val isHuawei = address != null && isHuaweiAddress(address)
        val route = address?.let(::routeForKnownHuaweiAddress) ?: HuaweiDeviceRoute.UNSUPPORTED
        val exposeAdvancedUi = isHuawei && shouldExposeMiuiAdvancedHeadsetUi(route)
        Log.d(
            TAG,
            "$method upstream address=$address isHuawei=$isHuawei route=$route " +
                "exposeAdvancedUi=$exposeAdvancedUi",
        )
        if (!exposeAdvancedUi) return null
        reply.writeNoException()
        reply.writeInt(if (forced) 1 else 0)
        Log.d(TAG, "$method upstream forced $forced")
        return true
    }

    private fun handleSetCommonCommand(data: Parcel, reply: Parcel): Boolean? {
        val command = data.readInt()
        val value = data.readString()
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "setCommonCommand upstream command=$command value=$value device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei) return null
        lastHuaweiDevice = device
        detailNotificationResponse(command, value, device)?.let { response ->
            reply.writeNoException()
            reply.writeString(response)
            return true
        }
        if (command == COMMAND_SET_DETAIL_NOTIFICATION ||
            command == COMMAND_GET_DETAIL_NOTIFICATION
        ) {
            return null
        }
        reply.writeNoException()
        reply.writeString(
            when (command) {
                102 -> "1"
                123 -> "4"
                else -> "1"
            }
        )
        sendRealStatus(device, "setCommonCommand:$command")
        return true
    }

    private fun detailNotificationResponse(
        command: Int?,
        value: String?,
        device: BluetoothDevice?,
    ): String? {
        if (packageName != "com.xiaomi.bluetooth" || command == null || device == null) return null
        val ctx = context ?: return null
        val address = runCatching { device.address }.getOrNull()?.takeIf(String::isNotBlank)
            ?: return null
        val preferences = ctx.getSharedPreferences("DeviceIdCached", Context.MODE_MULTI_PROCESS)
        val key = "detail_notification$address"
        if (command == COMMAND_SET_DETAIL_NOTIFICATION) {
            val enabled = when (value) {
                "true" -> true
                "false" -> false
                else -> return null
            }
            if (!preferences.edit().putBoolean(key, enabled).commit()) return null
            if (enabled) {
                ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                    putExtra(HuaweiPodsAction.EXTRA_RESTORE_NOTIFICATION, true)
                    setPackage("com.android.bluetooth")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            } else {
                cancelDetailNotification(ctx, address)
            }
            return ""
        }
        if (command == COMMAND_GET_DETAIL_NOTIFICATION) {
            return preferences.getBoolean(key, false).toString()
        }
        return null
    }

    private fun cancelDetailNotification(ctx: Context, address: String) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
            ?.cancelAsUser(
                "BTHeadset$address",
                DETAIL_NOTIFICATION_ID,
                moe.chenxy.huaweipods.utils.SystemApisUtils.getUserAllUserHandle(),
            )
    }

    private fun handleCommonConfig(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val type = data.readString()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "getCommonConfig upstream type=$type device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei) return null
        lastHuaweiDevice = device
        reply.writeNoException()
        sendRealStatus(device, "getCommonConfig:$type")
        return true
    }

    private fun handleRegisterCallbackDevice(data: Parcel, reply: Parcel): Boolean? {
        val callback = data.readCallbackBinder()
        val device = data.readDevice()
        val isHuawei = isHuaweiPod(device)
        Log.d(TAG, "registerCallbackDevice upstream callback=$callback device=${device.describe()} isHuawei=$isHuawei")
        if (!isHuawei || callback == null) return null
        lastHuaweiDevice = device
        rememberCallback(callback, device)
        reply.writeNoException()
        sendRealStatus(device, "registerCallbackDevice")
        return true
    }

    private fun Parcel.readCallbackBinder(): Any? {
        val binder = readStrongBinder() ?: return null
        return runCatching {
            val stub = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback\$Stub")
            stub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.onFailure {
            Log.w(TAG, "read callback binder failed", it)
        }.getOrNull()
    }

    private fun Parcel.readDevice(): BluetoothDevice? {
        return if (readInt() != 0) BluetoothDevice.CREATOR.createFromParcel(this) else null
    }

    private fun isHuaweiPod(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull()
        val route = device.huaweiDeviceRoute()
        val result = route.isSupported ||
            (address != null && isHuaweiAddress(address))
        if (!result) return false
        rememberCurrentHuaweiDevice(address, name, route, device)
        return true
    }

    private fun notifyRealStatus(reason: String) {
        val address = currentAddress?.takeIf(String::isNotBlank)
            ?: lastHuaweiDevice?.let { runCatching { it.address }.getOrNull() }
            ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatus(device: BluetoothDevice?, reason: String) {
        val address = device?.address ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatusDelayed(device: BluetoothDevice?, reason: String, delayMs: Long) {
        val address = device?.address ?: return
        sendRealStatusDelayed(address, reason, delayMs)
    }

    private fun sendRealStatusDelayed(address: String, reason: String, delayMs: Long) {
        handler.postDelayed({ sendRealStatus(address, reason) }, delayMs)
    }

    private fun sendRealStatus(address: String, reason: String) {
        if (!isCurrentStatusAddress(address)) {
            Log.d(TAG, "send real status skipped: stale target reason=$reason target=$address current=$currentAddress")
            return
        }
        val targetCallbacks = callbacks.values.filter {
            it.address.equals(address, ignoreCase = true)
        }
        if (targetCallbacks.isEmpty()) {
            Log.d(TAG, "send real status skipped: no callback reason=$reason address=$address")
            return
        }
        val payload = realRefreshPayload()
        if (!isCurrentStatusAddress(address)) {
            Log.d(TAG, "send real status skipped: identity changed while snapshotting reason=$reason target=$address current=$currentAddress")
            return
        }
        handler.post {
            if (!isCurrentStatusAddress(address)) {
                Log.d(TAG, "send real status skipped: queued target is stale reason=$reason target=$address current=$currentAddress")
                return@post
            }
            targetCallbacks.forEach { registration ->
                val callback = registration.callback
                runCatching {
                    callMethod(callback, "refreshStatus", address, payload)
                    Log.d(TAG, "sent real refreshStatus reason=$reason address=$address payload=$payload callback=$callback")
                }.onFailure {
                    forgetCallback(callback)
                    Log.w(TAG, "send real refreshStatus failed reason=$reason callback=$callback", it)
                }
            }
        }
    }

    private fun realRefreshPayload(): String {
        return miuiRefreshPayload(currentBattery, currentHuaweiRoute(), currentHuaweiAncState())
    }

    private fun isCurrentStatusAddress(address: String): Boolean {
        val activeAddress = currentAddress?.takeIf(String::isNotBlank) ?: return true
        return address.equals(activeAddress, ignoreCase = true)
    }

    private fun effectiveBattery(): BatteryParams? {
        return currentBattery
    }

    private fun miuiRefreshPayload(
        battery: BatteryParams?,
        route: HuaweiDeviceRoute,
        anc: HuaweiAncState,
    ): String {
        val values = MutableList(16) { "" }
        values[0] = miuiBatteryValue(battery?.left)
        values[1] = miuiBatteryValue(battery?.right)
        values[2] = miuiBatteryValue(battery?.case)
        values[7] = upstreamMiuiAncLevel(route, anc)
        values[8] = "true"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun miuiBatteryValue(params: PodParams?): String {
        if (params?.isConnected != true) return "255"
        val value = params.battery.coerceIn(0, 100)
        return (if (params.isCharging) value or 128 else value).toString()
    }

    private fun displayBattery(params: PodParams?): Int? {
        if (params?.isConnected != true) return null
        return params.battery.coerceIn(0, 100)
    }

    private fun displayWearState(battery: BatteryParams, fallback: Int): Int {
        val leftConnected = battery.left?.isConnected == true
        val rightConnected = battery.right?.isConnected == true
        return when {
            leftConnected && rightConnected -> 1
            leftConnected -> 3
            rightConnected -> 2
            fallback != 0 -> fallback
            else -> 1
        }
    }

    private fun currentMiuiBluetoothNotification(): Any? {
        return runCatching {
            findClass("com.android.bluetooth.ble.app.headset.BluetoothHeadsetService")
                .getField("mMiuiBluetoothNotification")
                .apply { isAccessible = true }
                .get(null)
        }.getOrNull()
    }

    private fun patchHeadsetWearIslandBundle(bundle: Bundle?) {
        if (bundle == null) return
        if (!shouldInterceptHeadsetWearIsland(bundle)) return
        if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_OFFICIAL) return
        val battery = effectiveBattery() ?: return
        val leftText = displayBattery(battery.left)?.let { "$it%" }
        val rightText = displayBattery(battery.right)?.let { "$it%" }
        if (leftText == null && rightText == null) return
        patchIslandJson(bundle, "param", leftText, rightText)
        patchIslandJson(bundle, "island_param", leftText, rightText)
        Log.d(TAG, "patched headset_wear_notification island text left=$leftText right=$rightText")
    }

    private fun shouldInterceptHeadsetWearIsland(bundle: Bundle?): Boolean {
        return bundle?.getString("notifyId") == "headset_wear_notification"
    }

    private fun patchIslandJson(bundle: Bundle, key: String, leftText: String?, rightText: String?) {
        val raw = bundle.getString(key) ?: return
        runCatching {
            val json = JSONObject(raw)
            leftText?.let { putTextParams(json.optJSONObject("left"), it) }
            rightText?.let { putTextParams(json.optJSONObject("right"), it) }
            bundle.putString(key, json.toString())
        }.onFailure {
            Log.w(TAG, "patch island json failed key=$key raw=$raw", it)
        }
    }

    private fun putTextParams(area: JSONObject?, text: String) {
        if (area == null) return
        area.put(
            "textParams",
            JSONObject().apply {
                put("text", text)
                put("textColor", -1)
                put("turnAnim", true)
            }
        )
    }

    private fun requestBluetoothStatus(reason: String) {
        runCatching {
            context?.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
            Log.d(TAG, "requested bluetooth status reason=$reason package=$packageName")
        }.onFailure {
            Log.w(TAG, "request bluetooth status failed reason=$reason package=$packageName", it)
        }
    }

    private fun sendHuaweiAnc(route: HuaweiDeviceRoute, state: HuaweiAncState, device: BluetoothDevice?) {
        if (!route.supportsAnc || state.mode == NoiseControlMode.TRANSPARENCY && !route.supportsTransparency) {
            Log.w(TAG, "sendHuaweiAnc skipped: unsupported route=$route state=$state")
            return
        }
        val ctx = context ?: run {
            Log.w(TAG, "sendHuaweiAnc skipped: context is null route=$route state=$state")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", state.mode.broadcastStatus)
            state.subMode?.let { putExtra("submode", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            device?.let {
                putExtra("address", it.address)
                putExtra("device_name", it.name ?: it.alias ?: "")
            }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendHuaweiAnc command sent route=$route state=$state")
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStatus(): BatteryParams? {
        return runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
    }

    private fun Intent.batteryStatusFromExtras(): BatteryParams? {
        if (!hasExtra("left_connected") && !hasExtra("right_connected") && !hasExtra("case_connected")) return null
        return BatteryParams(
            left = PodParams(
                getIntExtra("left_battery", 0),
                getBooleanExtra("left_charging", false),
                getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                getIntExtra("right_battery", 0),
                getBooleanExtra("right_charging", false),
                getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                getIntExtra("case_battery", 0),
                getBooleanExtra("case_charging", false),
                getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun BatteryParams?.debugString(): String {
        if (this == null) return "null"
        return "left=${left?.battery}/${left?.isCharging}/${left?.isConnected} right=${right?.battery}/${right?.isCharging}/${right?.isConnected} case=${case?.battery}/${case?.isCharging}/${case?.isConnected}"
    }

    private fun isHuaweiAddress(address: String): Boolean {
        return resolveHuaweiDeviceRoute(address, null).isSupported ||
            address.uppercase() in knownHuaweiAddresses
    }

    private fun routeForKnownHuaweiAddress(address: String): HuaweiDeviceRoute {
        if (address.equals(currentAddress, ignoreCase = true)) {
            currentRoute?.takeIf { it.isSupported }?.let { return it }
        }
        return resolveHuaweiDeviceRoute(address, null)
    }

    private fun rememberKnownAddress(address: String?) {
        val normalized = address?.uppercase() ?: return
        knownHuaweiAddresses.add(normalized)
    }

    private fun rememberSupportedDevice(intent: Intent): Boolean {
        val address = intent.getStringExtra("address") ?: currentAddress
        val name = intent.getStringExtra("device_name") ?: currentName
        val route = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        ) ?: resolveHuaweiDeviceRoute(address, name)
        if (!route.isSupported) {
            Log.w(TAG, "ignored unsupported state device=${name.orEmpty()}/${address.orEmpty()}")
            return false
        }
        rememberCurrentHuaweiDevice(address, name, route)
        return true
    }

    private fun targetsCurrentHuaweiDevice(intent: Intent): Boolean {
        val activeAddress = currentAddress?.takeIf(String::isNotBlank) ?: return false
        val targetAddress = intent.getStringExtra("address")?.takeIf(String::isNotBlank) ?: return false
        if (!activeAddress.equals(targetAddress, ignoreCase = true)) return false
        val targetRoute = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        ) ?: resolveHuaweiDeviceRoute(targetAddress, intent.getStringExtra("device_name"))
        return targetRoute.isSupported && targetRoute == currentHuaweiRoute()
    }

    private fun consumePendingHuaweiCallbackAddress(): String? {
        val pending = pendingHuaweiCallbacks.remove(callbackCaller()) ?: return null
        if (SystemClock.elapsedRealtime() - pending.createdAtMs > pendingHuaweiCallbackTtlMs) return null
        val address = pending.address.takeIf(String::isNotBlank) ?: return null
        val activeAddress = currentAddress?.takeIf(String::isNotBlank) ?: return null
        return address.takeIf { it.equals(activeAddress, ignoreCase = true) }
    }

    private fun rememberPendingHuaweiCallback(address: String?) {
        val normalizedAddress = address?.takeIf(String::isNotBlank) ?: run {
            clearPendingHuaweiCallback()
            return
        }
        val now = SystemClock.elapsedRealtime()
        pendingHuaweiCallbacks.entries.forEach { (caller, pending) ->
            if (now - pending.createdAtMs > pendingHuaweiCallbackTtlMs) {
                pendingHuaweiCallbacks.remove(caller, pending)
            }
        }
        pendingHuaweiCallbacks[callbackCaller()] = PendingHuaweiCallback(normalizedAddress, now)
    }

    private fun clearPendingHuaweiCallback() {
        pendingHuaweiCallbacks.remove(callbackCaller())
    }

    private fun callbackCaller(): CallbackCaller = CallbackCaller(
        uid = Binder.getCallingUid(),
        pid = Binder.getCallingPid(),
    )

    private fun rememberCurrentHuaweiDevice(
        address: String?,
        name: String?,
        route: HuaweiDeviceRoute,
        device: BluetoothDevice? = null,
    ) {
        val identityChanged = when {
            !address.isNullOrBlank() -> !address.equals(currentAddress, ignoreCase = true)
            else -> name != currentName
        }
        if (identityChanged || currentRoute != route) {
            clearCurrentDeviceStatus()
        }
        if (identityChanged) {
            pendingHuaweiCallbacks.clear()
            lastHuaweiDevice = null
        }
        currentAddress = address
        currentName = name
        currentRoute = route
        if (device != null) {
            lastHuaweiDevice = device
        }
        rememberKnownAddress(address)
    }

    private fun clearCurrentDeviceStatus() {
        currentBattery = null
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncSubMode = null
    }

    private fun normalizeBatteryAvailabilityForCurrentRoute(status: BatteryParams): BatteryParams =
        if (currentHuaweiRoute().usesReportedEarbudAvailability) {
            status
        } else {
            status.normalizedEarbudAvailability()
        }

    private fun currentHuaweiRoute(): HuaweiDeviceRoute =
        currentRoute?.takeIf { it.isSupported }
            ?: lastHuaweiDevice?.huaweiDeviceRoute()?.takeIf { it.isSupported }
            ?: resolveHuaweiDeviceRoute(currentAddress, currentName)

    private fun currentHuaweiAncState(): HuaweiAncState = HuaweiAncState(
        mode = NoiseControlMode.fromBroadcastStatus(currentAnc),
        subMode = currentAncSubMode,
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> objectField(instance: Any?, fieldName: String): T? {
        return runCatching { getObjectField(instance, fieldName) as? T }.getOrNull()
    }

    private companion object {
        private const val TRANSACTION_SET_COMMON_COMMAND = 14
        private const val COMMAND_SET_DETAIL_NOTIFICATION = 114
        private const val COMMAND_GET_DETAIL_NOTIFICATION = 115
        private const val DETAIL_NOTIFICATION_ID = 10003
        private const val FREEBUDS3_BATTERY_RESPONSE = "+HUAWEIBATTERY=OK"
        private const val AT_RESPONSE_OK = 1
    }

    private fun BluetoothDevice?.describe(): String {
        if (this == null) return "null"
        val address = runCatching { this.address }.getOrNull()
        val name = runCatching { this.name }.getOrNull()
        val alias = runCatching { this.alias }.getOrNull()
        return "BluetoothDevice(address=$address,name=$name,alias=$alias)"
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
