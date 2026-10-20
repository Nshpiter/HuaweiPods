package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
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
import moe.chenxy.huaweipods.broadcast.HuaweiPodsBroadcastTrustPolicy
import moe.chenxy.huaweipods.utils.safeDisplayName
import moe.chenxy.huaweipods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import moe.chenxy.huaweipods.coloros.queryColorOsLiveAlertAudioProfiles

object HeadsetStateDispatcher : HookContext() {
    private const val ROUTE_PROBE_WATCHDOG_MS = 5_500L
    private const val HEADSET_ICON_WATCHDOG_MS = 2_500L

    private var appRequestReceiverRegistered = false
    @Volatile
    private var a2dpService: Any? = null
    private var appRequestReceiverContext: Context? = null
    private var appRequestReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingHostCallbacks = ConcurrentHashMap<Runnable, Handler>()
    @Volatile
    private var acceptingCallbacks = false
    /** ColorOS 蓝牙 UID 没有 STATUS_BAR 权限；首次被拒后停止重试，避免保活任务刷日志。 */
    @Volatile
    private var statusBarIconUnavailable = false
    private val connectedA2dpAddresses = ConcurrentHashMap.newKeySet<String>()
    private val connectedHuaweiA2dpAddresses = ConcurrentHashMap.newKeySet<String>()
    private val audioProbes = AudioSessionProbeGuard()
    private val activeRouteProbe = AtomicReference<HuaweiDeviceRouteProbeSession?>(null)
    private val lastRouteProbeStartedAtMs = ConcurrentHashMap<String, Long>()
    @Volatile
    private var iconWatchdogContext: Context? = null
    @Volatile
    private var iconWatchdogRunning = false
    private val iconWatchdogTick = object : Runnable {
        override fun run() {
            val context = iconWatchdogContext
            if (!iconWatchdogRunning || context == null || connectedHuaweiA2dpAddresses.isEmpty()) return
            setHeadsetIcon(context, visible = true, reason = "watchdog")
            if (iconWatchdogRunning) {
                mainHandler.postDelayed(this, HEADSET_ICON_WATCHDOG_MS)
            }
        }
    }

    override fun onHook() {
        acceptingCallbacks = true
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()?.let(::registerAppRequestReceiver)
        runCatching {
            val serviceClass = findClass("com.android.bluetooth.a2dp.A2dpService")
            serviceClass.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                hookConstructorAfter(constructor) {
                    val service = instance ?: return@hookConstructorAfter
                    val context = service as? Context
                        ?: args.firstOrNull { it is Context } as? Context
                        ?: return@hookConstructorAfter
                    a2dpService = service
                    registerAppRequestReceiver(context)
                    scheduleConnectedHuaweiPodsRestore(context, service)
                }
            }
        }.onFailure {
            Log.e("HuaweiPods", "A2dpService startup restore hook failed", it)
        }

        val connectionMethod = findA2dpConnectionCallback(
            findClass("com.android.bluetooth.a2dp.A2dpService"), BluetoothDevice::class.java,
        )
        Log.i("HuaweiPods", "A2DP connection callback=${connectionMethod.name}")
        hookAfter(connectionMethod) {
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
                    if (currState == BluetoothHeadset.STATE_CONNECTED) {
                        connectedA2dpAddresses.add(normalizedAddress)
                    } else if (
                        currState == BluetoothHeadset.STATE_DISCONNECTING ||
                        currState == BluetoothHeadset.STATE_DISCONNECTED
                    ) {
                        connectedA2dpAddresses.remove(normalizedAddress)
                    }
                    val isHuawei = isHuaweiPod(device)
                    Log.d("HuaweiPods", "A2DP Connection State: $currState, isHuaweiPod=$isHuawei")
                    val context = instance as ContextWrapper
                    registerAppRequestReceiver(context)
                    if (!isHuawei) return@runCatching

                    postTracked(mainHandler) { checkAudioSession(context, device, BluetoothProfile.A2DP, currState) }
                }.onFailure {
                    Log.e("HuaweiPods", "A2DP state callback failed without interrupting Bluetooth", it)
                }
            }
        }
    }

    override fun onCanClose(): Boolean =
        activeRouteProbe.get() == null &&
        moe.chenxy.huaweipods.broadcast.HeadsetLifecycleBinding.canCloseForHotReload() &&
        HuaweiL2capAncController.canCloseForHotReload() &&
            OfficialImageIdentityBridge.canCloseForHotReload()

    override fun onSaveHotReloadState(outState: Bundle) {
        moe.chenxy.huaweipods.broadcast.HeadsetLifecycleBinding.saveHotReloadState(outState)
        outState.putStringArrayList(
            "connected_a2dp_addresses",
            ArrayList(connectedA2dpAddresses),
        )
        HuaweiHfpController.saveHotReloadState(outState)
    }

    @SuppressLint("MissingPermission")
    override fun onRestoreHotReloadState(savedState: Bundle) {
        val context = appRequestReceiverContext ?: currentApplicationOrNull() ?: return
        moe.chenxy.huaweipods.broadcast.HeadsetLifecycleBinding.restoreHotReloadState(context, savedState)
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
        // Controller 只维护一个 HFP 会话。优先恢复旧代实际持有的设备，避免多设备
        // 遍历时后一个地址覆盖已恢复的电量并使通知恢复任务失效。
        val savedAddress = HuaweiHfpController.hotReloadSessionAddress(savedState)
        if (!savedAddress.isNullOrBlank()) restoredAddresses.add(savedAddress.uppercase())
        val candidates = if (!savedAddress.isNullOrBlank()) listOf(savedAddress) else restoredAddresses.toList()
        candidates.forEach { address ->
            runCatching { adapter.getRemoteDevice(address) }.getOrNull()?.let { device ->
                checkAudioSession(context, device, null, null, restore = true, savedState = savedState)
            }
        }
    }

    override fun onClose() {
        acceptingCallbacks = false
        audioProbes.reset()
        stopIconWatchdog()
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
        a2dpService = null
        connectedA2dpAddresses.clear()
        connectedHuaweiA2dpAddresses.clear()
        activeRouteProbe.set(null)
        lastRouteProbeStartedAtMs.clear()
        HuaweiHfpController.closeForHotReload()
        val liveAlertStopped = moe.chenxy.huaweipods.broadcast.HeadsetLifecycleBinding.closeForHotReload()
        val rfcommStopped = HuaweiL2capAncController.closeForHotReload()
        HuaweiWearDetectionController.closeForHotReload()
        val imageBridgeStopped = OfficialImageIdentityBridge.closeForHotReload()
        check(rfcommStopped && imageBridgeStopped && liveAlertStopped) {
            "Bluetooth hot-reload workers did not stop rfcomm=$rfcommStopped image=$imageBridgeStopped liveAlert=$liveAlertStopped"
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

    private fun startIconWatchdog(context: Context) {
        if (statusBarIconUnavailable) return
        iconWatchdogContext = context
        iconWatchdogRunning = true
        mainHandler.removeCallbacks(iconWatchdogTick)
        mainHandler.postDelayed(iconWatchdogTick, HEADSET_ICON_WATCHDOG_MS)
    }

    private fun stopIconWatchdog() {
        iconWatchdogRunning = false
        iconWatchdogContext = null
        mainHandler.removeCallbacks(iconWatchdogTick)
    }

    /**
     * HyperOS 4 会延迟清除 `wireless_headset` 图标，需要保活；ColorOS 16/17 移除了蓝牙 UID 的
     * STATUS_BAR 权限，并由 SystemUI 自行显示耳机图标。图标只是装饰，权限被拒绝后停止重试，
     * 绝不能中断华为会话建立或通知清理。
     */
    private fun setHeadsetIcon(context: Context, visible: Boolean, reason: String) {
        if (statusBarIconUnavailable) return
        runCatching {
            val statusBarManager = context.getSystemService("statusbar") as? StatusBarManager
                ?: error("statusbar service unavailable")
            statusBarManager.setIconVisibility("wireless_headset", visible)
            Log.d("HuaweiPods", "status bar headset icon visible=$visible reason=$reason")
        }.onFailure {
            if (isVendorPermissionDenied(it)) {
                statusBarIconUnavailable = true
                stopIconWatchdog()
                Log.d("HuaweiPods", "status bar headset icon not permitted; system icon kept")
            } else {
                Log.w(
                    "HuaweiPods",
                    "status bar headset icon update failed visible=$visible reason=$reason",
                    it,
                )
            }
        }
    }

    /**
     * 蓝牙进程重启（如重启作用域）后，已连接的华为设备不会再收到 A2DP 连接回调；
     * A2dpService 构造后延迟查询一次当前连接并恢复会话。任务挂在 [mainHandler] 上，
     * 模块关闭或热重载时随 removeCallbacksAndMessages 一并取消。
     */
    @SuppressLint("MissingPermission")
    private fun scheduleConnectedHuaweiPodsRestore(context: Context, service: Any) {
        Log.i("HuaweiPods", "Scheduled connected Huawei device restore from A2DP service")
        listOf(1_500L, 4_500L).forEach { delayMs ->
            mainHandler.postDelayed(
                {
                    if (acceptingCallbacks) {
                        runCatching { restoreConnectedHuaweiPods(context, service) }
                            .onFailure { Log.w("HuaweiPods", "Connected Huawei device restore failed", it) }
                    }
                },
                delayMs,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun restoreConnectedHuaweiPods(context: Context, service: Any? = a2dpService) {
        val connectedDevices = service?.let { currentService ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (callMethod(currentService, "getConnectedDevices") as? List<*>)
                    ?.filterIsInstance<BluetoothDevice>()
                    .orEmpty()
            }.onFailure {
                Log.w("HuaweiPods", "A2DP connected-device restore query failed", it)
            }.getOrDefault(emptyList())
        }.orEmpty()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val candidates = (connectedDevices + runCatching { adapter.bondedDevices.orEmpty().toList() }
            .getOrDefault(emptyList())).distinctBy { it.address }
        candidates
            .filter(::isHuaweiPod)
            .forEach { device ->
                checkAudioSession(context, device, null, null, restore = true)
            }
    }

    @SuppressLint("MissingPermission")
    private fun checkAudioSession(context: Context, device: BluetoothDevice, profile: Int?, state: Int?,
                                  restore: Boolean = false, savedState: Bundle? = null) {
        if (!acceptingCallbacks || !isHuaweiPod(device)) return
        val address = device.address
        val token = audioProbes.begin(address)
        val sessionGeneration = HuaweiHfpController.audioSessionGeneration()
        queryColorOsLiveAlertAudioProfiles(context, address) { a2dp, hfp ->
            runCatching {
            if (!acceptingCallbacks || !audioProbes.accepts(token) ||
                HuaweiHfpController.audioSessionGeneration() != sessionGeneration) return@queryColorOsLiveAlertAudioProfiles
            val adapterEnabled = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }
                .getOrNull()
            if (savedState != null && moe.chenxy.huaweipods.broadcast.headsetRecoveryShouldClear(a2dp, hfp, adapterEnabled == false)) {
                // A disconnect may race with the save/close boundary. Confirmed current
                // states supersede the saved "connected" snapshot even without an owner.
                moe.chenxy.huaweipods.broadcast.HeadsetLifecycleBinding.deliver(context,
                    Intent(HuaweiPodsAction.ACTION_PODS_DISCONNECTED).apply {
                        putExtra("address", address)
                        putExtra(HuaweiPodsAction.EXTRA_EVENT_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
                    })
            }
            if (adapterEnabled == false) { endAudioSession(context); return@queryColorOsLiveAlertAudioProfiles }
            val decision = HeadsetAudioSessionPolicy.decide(HuaweiHfpController.audioSessionAddress(), address,
                profile, state, a2dp, hfp, restore)
            if (a2dp == BluetoothProfile.STATE_CONNECTED) connectedA2dpAddresses.add(address.uppercase())
            else if (a2dp == BluetoothProfile.STATE_DISCONNECTED) connectedA2dpAddresses.remove(address.uppercase())
            when (decision) {
                AudioSessionDecision.CONNECT, AudioSessionDecision.KEEP -> {
                    if (a2dp == BluetoothProfile.STATE_CONNECTED || hfp == BluetoothProfile.STATE_CONNECTED) {
                        connectedHuaweiA2dpAddresses.add(address.uppercase())
                        setHeadsetIcon(context, true, "audio-profile")
                        startIconWatchdog(context)
                        if (decision == AudioSessionDecision.CONNECT) {
                            HuaweiHfpController.connectPod(context, device)
                            savedState?.let(HuaweiHfpController::restoreHotReloadState)
                        }
                    }
                }
                AudioSessionDecision.DISCONNECT -> {
                    connectedHuaweiA2dpAddresses.remove(address.uppercase())
                    HuaweiHfpController.disconnectedPod(context, device)
                    if (connectedHuaweiA2dpAddresses.isEmpty()) { stopIconWatchdog(); setHeadsetIcon(context, false, "audio-disconnected") }
                }
                AudioSessionDecision.IGNORE -> Unit
            }
            }.onFailure { Log.w("HuaweiPods", "Audio Profile session update failed without interrupting Bluetooth", it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun endAudioSession(context: Context) {
        audioProbes.reset()
        HuaweiHfpController.audioSessionAddress()?.let { address ->
            runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) }
                .getOrNull()?.let { HuaweiHfpController.disconnectedPod(context, it) }
        }
        connectedA2dpAddresses.clear()
        connectedHuaweiA2dpAddresses.clear()
        stopIconWatchdog()
        setHeadsetIcon(context, false, "bluetooth-off")
    }

    @SuppressLint("MissingPermission")
    private fun registerAppRequestReceiver(context: Context?) {
        if (context == null || appRequestReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (context == null) return
                    val receivedIntent = intent ?: return
                    if (receivedIntent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        if (receivedIntent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) in
                            listOf(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF)) runCatching { endAudioSession(context) }
                                .onFailure { Log.w("HuaweiPods", "Bluetooth-off cleanup failed", it) }
                        return
                    }
                    if (receivedIntent.action in listOf(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                        val target = receivedIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                        val profile = if (receivedIntent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                            BluetoothProfile.A2DP else BluetoothProfile.HEADSET
                        runCatching { checkAudioSession(context, target, profile,
                            receivedIntent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) }
                            .onFailure { Log.w("HuaweiPods", "Audio Profile event failed", it) }
                        return
                    }
                    runCatching {
                        val action = HuaweiPodsAction.canonical(receivedIntent.action)
                        if (!HuaweiPodsBroadcastTrustPolicy.isTrustedAppControlSender(sentFromPackage)) {
                            Log.w(
                                "HuaweiPods",
                                "Rejected app request from untrusted sender action=$action sender=${sentFromPackage.orEmpty()}",
                            )
                            return@runCatching
                        }
                        when (action) {
                            HuaweiPodsAction.ACTION_PODS_UI_INIT,
                            HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                                restoreConnectedHuaweiPods(context)
                                context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE).apply {
                                    setPackage(BuildConfig.APPLICATION_ID)
                                    putExtra(HuaweiPodsAction.EXTRA_MODULE_BUILD_ID, BuildConfig.MODULE_BUILD_ID)
                                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                })
                            }
                            HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST -> {
                                val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java)
                                    ?: return@runCatching
                                Log.d("HuaweiPods", "connect request from app device=${device.safeDisplayName()}/${device.address}")
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
                                handleDeviceRouteProbeRequest(context, receivedIntent)
                            }
                        }
                    }.onFailure {
                        Log.e("HuaweiPods", "App request receiver failed without interrupting Bluetooth", it)
                    }
                }
            }
        val registered = runCatching {
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
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
                HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute(it.modelId, it.subModelId)
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
        val deviceName = device.safeDisplayName()
        Log.w(
            "HuaweiPods",
            "rejected device $operation request reason=$reason device=$deviceName/${device.address}",
        )
        context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED).apply {
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
