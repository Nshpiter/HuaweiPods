package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.pods.HuaweiHfpController
import moe.chenxy.huaweipods.pods.HuaweiDeviceInfoIdentity
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteProbePolicy
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteProbeSession
import moe.chenxy.huaweipods.pods.HuaweiL2capAncController
import moe.chenxy.huaweipods.pods.HuaweiWearDetectionController
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.smartaudio.OfficialImageIdentityBridge
import moe.chenxy.huaweipods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object HeadsetStateDispatcher : HookContext() {
    private const val ROUTE_PROBE_WATCHDOG_MS = 5_500L
    // 状态栏无线耳机 slot 会被系统在连接后固定几秒清掉（瞬时 slot）。
    // 用低频看门狗在连接期间周期性地重新压实图标，间隔调小则恢复更快、
    // 但频繁刷新可能重新引入闪烁；间隔调大则可能出现短暂空缺。默认 2.5s。
    private const val HEADSET_ICON_WATCHDOG_MS = 2_500L

    private var appRequestReceiverRegistered = false
    private var appRequestReceiverContext: Context? = null
    private var appRequestReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingHostCallbacks = ConcurrentHashMap<Runnable, Handler>()
    @Volatile
    private var acceptingCallbacks = false
    private val connectedA2dpAddresses = ConcurrentHashMap.newKeySet<String>()
    private val activeRouteProbe = AtomicReference<HuaweiDeviceRouteProbeSession?>(null)
    private val lastRouteProbeStartedAtMs = ConcurrentHashMap<String, Long>()

    // 图标看门狗：连接期间周期性 re-assert setIconVisibility(true)，断开即停。
    @Volatile
    private var iconWatchdogContext: Context? = null
    @Volatile
    private var iconWatchdogRunning = false
    private val iconWatchdogTick = object : Runnable {
        override fun run() {
            if (!iconWatchdogRunning) return
            val ctx = iconWatchdogContext ?: return
            runCatching {
                (ctx.getSystemService("statusbar") as StatusBarManager)
                    .setIconVisibility("wireless_headset", true)
                Log.d("HuaweiPods", "headset icon watchdog re-assert -> visible")
            }.onFailure {
                Log.e("HuaweiPods", "headset icon watchdog failed", it)
            }
            mainHandler.postDelayed(this, HEADSET_ICON_WATCHDOG_MS)
        }
    }

    override fun onHook() {
        acceptingCallbacks = true
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()?.let(::registerAppRequestReceiver)
        runCatching {
            hookAfter(findMethod("com.android.bluetooth.btservice.AdapterService", "onCreate")) {
                registerAppRequestReceiver(instance as? Context)
            }
        }.onFailure {
            Log.w("HuaweiPods", "AdapterService.onCreate hook skipped", it)
        }

        hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
            val currState = args[2] as Int
            val fromState = args[1] as Int
            val device = args[0] as BluetoothDevice?
            val handler = getObjectField(instance, "mHandler") as Handler
            if (device == null || currState == fromState) {
                return@hookAfter
            }
            postTracked(handler) {
                runCatching {
                    val normalizedAddress = device.address.uppercase()
                    val disconnecting = currState == BluetoothHeadset.STATE_DISCONNECTING ||
                        currState == BluetoothHeadset.STATE_DISCONNECTED
                    // 只有“此前不在已连接集合”的 CONNECTED 才算真正的新连接并触发处理；
                    // 系统周期性重发的 CONNECTED 一律忽略，避免状态栏图标被反复刷新而闪烁。
                    val newConnect = !disconnecting &&
                        connectedA2dpAddresses.add(normalizedAddress)
                    if (disconnecting) connectedA2dpAddresses.remove(normalizedAddress)
                    val isHuawei = isHuaweiPod(device)
                    Log.d(
                        "HuaweiPods",
                        "A2DP Connection State: $currState (new=$newConnect), isHuaweiPod=$isHuawei",
                    )
                    val context = instance as ContextWrapper
                    registerAppRequestReceiver(context)
                    if (!isHuawei) return@runCatching

                    val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                    if (newConnect) {
                        statusBarManager.setIconVisibility("wireless_headset", true)
                        startIconWatchdog(context)
                        HuaweiHfpController.connectPod(context, device)
                    } else if (disconnecting) {
                        stopIconWatchdog()
                        statusBarManager.setIconVisibility("wireless_headset", false)
                        HuaweiHfpController.disconnectedPod(context, device)
                    }
                }.onFailure {
                    Log.e("HuaweiPods", "A2DP state callback failed without interrupting Bluetooth", it)
                }
            }
        }
    }

    private fun startIconWatchdog(context: Context) {
        iconWatchdogContext = context
        iconWatchdogRunning = true
        mainHandler.removeCallbacks(iconWatchdogTick)
        // 不立即 post，首个 CONNECTED 已在回调里直接 setIconVisibility(true)；
        // 看门狗只在首个间隔到时才首次 re-assert。
        mainHandler.postDelayed(iconWatchdogTick, HEADSET_ICON_WATCHDOG_MS)
    }

    private fun stopIconWatchdog() {
        iconWatchdogRunning = false
        iconWatchdogContext = null
        mainHandler.removeCallbacks(iconWatchdogTick)
    }

    override fun onCanClose(): Boolean =
        activeRouteProbe.get() == null &&
        HuaweiL2capAncController.canCloseForHotReload() &&
            OfficialImageIdentityBridge.canCloseForHotReload()

    override fun onSaveHotReloadState(outState: Bundle) {
        outState.putStringArrayList(
            "connected_a2dp_addresses",
            ArrayList(connectedA2dpAddresses),
        )
        HuaweiHfpController.saveHotReloadState(outState)
    }

    @SuppressLint("MissingPermission")
    override fun onRestoreHotReloadState(savedState: Bundle) {
        val context = appRequestReceiverContext ?: currentApplicationOrNull() ?: return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val restoredAddresses = savedState.getStringArrayList("connected_a2dp_addresses")
            .orEmpty()
            .map(String::uppercase)
            .toMutableSet()
        runCatching {
            adapter.bondedDevices.filter { device ->
                isHuaweiPod(device) && isActiveA2dpDevice(device)
            }.mapTo(restoredAddresses) { it.address.uppercase() }
        }
        val connectedDevices = restoredAddresses.mapNotNull { address ->
            runCatching { adapter.getRemoteDevice(address) }.getOrNull()
                ?.takeIf { isHuaweiPod(it) && isDeviceConnected(it) }
                ?.also { connectedA2dpAddresses += address }
        }
        // Controller 只维护一个 HFP 会话。优先恢复旧代实际持有的设备，避免多设备
        // 遍历时后一个地址覆盖已恢复的电量并使通知恢复任务失效。
        val savedAddress = HuaweiHfpController.hotReloadSessionAddress(savedState)
        val sessionDevice = connectedDevices.firstOrNull {
            it.address.equals(savedAddress, ignoreCase = true)
        } ?: connectedDevices.firstOrNull()
        sessionDevice?.let { device ->
            HuaweiHfpController.connectPod(context, device)
            HuaweiHfpController.restoreHotReloadState(savedState)
        }
    }

    override fun onClose() {
        acceptingCallbacks = false
        pendingHostCallbacks.entries.toList().forEach { (task, handler) ->
            handler.removeCallbacks(task)
        }
        pendingHostCallbacks.clear()
        mainHandler.removeCallbacksAndMessages(null)
        val receiver = appRequestReceiver
        val receiverContext = appRequestReceiverContext
        if (receiver != null && receiverContext != null) {
            runCatching { receiverContext.unregisterReceiver(receiver) }
                .onFailure { error ->
                    if (error !is IllegalArgumentException) {
                        Log.w("HuaweiPods", "Failed to unregister app request receiver", error)
                    }
                }
        }
        appRequestReceiver = null
        appRequestReceiverContext = null
        appRequestReceiverRegistered = false
        stopIconWatchdog()
        connectedA2dpAddresses.clear()
        activeRouteProbe.set(null)
        lastRouteProbeStartedAtMs.clear()
        HuaweiHfpController.closeForHotReload()
        val rfcommStopped = HuaweiL2capAncController.closeForHotReload()
        HuaweiWearDetectionController.closeForHotReload()
        val imageBridgeStopped = OfficialImageIdentityBridge.closeForHotReload()
        check(rfcommStopped && imageBridgeStopped) {
            "Bluetooth hot-reload workers did not stop rfcomm=$rfcommStopped image=$imageBridgeStopped"
        }
    }

    private fun postTracked(handler: Handler, block: () -> Unit) {
        lateinit var task: Runnable
        task = Runnable {
            pendingHostCallbacks.remove(task)
            if (acceptingCallbacks) block()
        }
        pendingHostCallbacks[task] = handler
        if (!handler.post(task)) pendingHostCallbacks.remove(task)
    }

    @SuppressLint("MissingPermission")
    private fun registerAppRequestReceiver(context: Context?) {
        if (context == null || appRequestReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (context == null) return
                    val receivedIntent = intent ?: return
                    runCatching {
                        when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                            HuaweiPodsAction.ACTION_PODS_UI_INIT,
                            HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                                context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE).apply {
                                    setPackage(BuildConfig.APPLICATION_ID)
                                    putExtra(HuaweiPodsAction.EXTRA_MODULE_BUILD_ID, BuildConfig.MODULE_BUILD_ID)
                                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                })
                            }
                            HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST -> {
                                val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java)
                                    ?: return@runCatching
                                Log.d("HuaweiPods", "connect request from app device=${device.name}/${device.address}")
                                val supported = isHuaweiPod(device)
                                if (supported && isDeviceConnected(device)) {
                                    HuaweiHfpController.connectPod(context, device)
                                } else if (supported) {
                                    notifyRejectedDevice(
                                        context = context,
                                        device = device,
                                        state = "error",
                                        operation = "connect",
                                        reason = "not_connected",
                                        supported = true,
                                    )
                                } else {
                                    notifyRejectedDevice(context, device, state = "error", operation = "connect")
                                }
                            }
                            HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_REQUEST -> {
                                if (
                                    HuaweiDeviceRouteProbePolicy.isTrustedRequestSender(
                                        sentFromPackage,
                                    )
                                ) {
                                    handleDeviceRouteProbeRequest(context, receivedIntent)
                                } else {
                                    Log.w(
                                        "HuaweiPods",
                                        "Rejected route probe sender=${sentFromPackage.orEmpty()}",
                                    )
                                }
                            }
                        }
                    }.onFailure {
                        Log.e("HuaweiPods", "App request receiver failed without interrupting Bluetooth", it)
                    }
                }
            }
        val registered = runCatching {
            context.registerReceiver(receiver, IntentFilter().apply {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_REQUEST)
            }, Context.RECEIVER_EXPORTED)
        }.onFailure {
            Log.e("HuaweiPods", "Failed to register app request receiver", it)
        }.isSuccess
        if (registered) {
            appRequestReceiver = receiver
            appRequestReceiverContext = context
            appRequestReceiverRegistered = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceRouteProbeRequest(context: Context, intent: Intent) {
        val session = HuaweiDeviceRouteProbePolicy.session(
            address = intent.getStringExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_ADDRESS),
            generation = intent.getLongExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_GENERATION, -1L),
            nonce = intent.getStringExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_NONCE),
        ) ?: return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val device = runCatching { adapter?.getRemoteDevice(session.address) }.getOrNull()
        if (device == null) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        val eligible = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
            bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
            systemConnected = isActiveA2dpDevice(device),
        ) && !isHuaweiPod(device)
        if (!eligible) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        val cooldownAccepted = synchronized(lastRouteProbeStartedAtMs) {
            val allowed = HuaweiDeviceRouteProbePolicy.cooldownAllows(
                lastStartedAtMs = lastRouteProbeStartedAtMs[session.address],
                nowMs = nowMs,
            )
            if (allowed) lastRouteProbeStartedAtMs[session.address] = nowMs
            allowed
        }
        if (!cooldownAccepted) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        if (!activeRouteProbe.compareAndSet(null, session)) {
            sendDeviceRouteProbeResult(context, session, null)
            return
        }
        mainHandler.postDelayed(
            { completeDeviceRouteProbe(context, session, null) },
            ROUTE_PROBE_WATCHDOG_MS,
        )
        Log.d("HuaweiPods", "route-free DeviceInfo probe started device=${session.address}")
        HuaweiL2capAncController.requestDeviceInfoIdentityRouteFree(context, device) { identity ->
            if (activeRouteProbe.get() != session) return@requestDeviceInfoIdentityRouteFree
            val stillEligible = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
                bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }
                    .getOrDefault(false),
                systemConnected = isActiveA2dpDevice(device),
            ) && !isHuaweiPod(device)
            val verifiedRoute = identity?.takeIf { stillEligible }?.let {
                HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute(
                    modelId = it.modelId,
                    subModelId = it.subModelId,
                    deviceName = runCatching { device.name ?: device.alias }.getOrNull(),
                )
            }
            if (identity == null || verifiedRoute == null) {
                completeDeviceRouteProbe(context, session, null)
                return@requestDeviceInfoIdentityRouteFree
            }
            if (activeRouteProbe.get() != session) return@requestDeviceInfoIdentityRouteFree
            // Provider only accepts com.android.bluetooth and repeats strict identity/route
            // validation before binding. The exported result is therefore UX continuation,
            // not the authority that establishes the route.
            OfficialImageIdentityBridge.publishVerifiedRouteAsync(
                context = context,
                address = session.address,
                route = verifiedRoute,
                identity = identity,
                callbackHandler = mainHandler,
            ) { publishResult ->
                if (activeRouteProbe.get() != session) return@publishVerifiedRouteAsync
                val remainsConnected = HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(
                    bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }
                        .getOrDefault(false),
                    systemConnected = isActiveA2dpDevice(device),
                )
                completeDeviceRouteProbe(
                    context = context,
                    session = session,
                    identity = identity.takeIf {
                        publishResult.routeReady && remainsConnected
                    },
                )
            }
        }
    }

    private fun completeDeviceRouteProbe(
        context: Context,
        session: HuaweiDeviceRouteProbeSession,
        identity: HuaweiDeviceInfoIdentity?,
    ) {
        if (!activeRouteProbe.compareAndSet(session, null)) return
        sendDeviceRouteProbeResult(context, session, identity)
    }

    private fun sendDeviceRouteProbeResult(
        context: Context,
        session: HuaweiDeviceRouteProbeSession,
        identity: HuaweiDeviceInfoIdentity?,
    ) {
        val resultIntent = Intent(HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_RESULT).apply {
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_ADDRESS, session.address)
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_GENERATION, session.generation)
            putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_NONCE, session.nonce)
            identity?.let {
                putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_MODEL_ID, it.modelId)
                putExtra(HuaweiPodsAction.EXTRA_ROUTE_PROBE_SUB_MODEL_ID, it.subModelId)
            }
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        context.sendIdentitySharingBroadcast(resultIntent)
    }

    @SuppressLint("MissingPermission")
    private fun notifyRejectedDevice(
        context: Context,
        device: BluetoothDevice,
        state: String,
        operation: String,
        reason: String = "unsupported",
        supported: Boolean = false,
    ) {
        val deviceName = device.name ?: device.alias ?: ""
        Log.w(
            "HuaweiPods",
            "rejected device $operation request reason=$reason device=$deviceName/${device.address}",
        )
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED).apply {
            putExtra("address", device.address)
            putExtra("device_name", deviceName)
            putExtra("state", state)
            putExtra("reason", reason)
            putExtra("supported", supported)
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    @SuppressLint("MissingPermission")
    private fun isHuaweiPod(device: BluetoothDevice): Boolean {
        return runCatching { device.huaweiDeviceRoute().isSupported }
            .onFailure { Log.e("HuaweiPods", "Huawei route resolution failed", it) }
            .getOrDefault(false)
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        if (device.address.uppercase() in connectedA2dpAddresses) return true
        return runCatching {
            val method = device.javaClass.methods.firstOrNull {
                it.name == "isConnected" && it.parameterCount in 0..1
            } ?: return@runCatching false
            when (method.parameterCount) {
                0 -> method.invoke(device) as? Boolean == true
                else -> method.invoke(device, BluetoothDevice.TRANSPORT_AUTO) as? Boolean == true
            }
        }.onFailure {
            Log.w("HuaweiPods", "BluetoothDevice.isConnected unavailable device=${device.address}", it)
        }.getOrDefault(false)
    }

    private fun isActiveA2dpDevice(device: BluetoothDevice): Boolean =
        device.address.uppercase() in connectedA2dpAddresses
}
