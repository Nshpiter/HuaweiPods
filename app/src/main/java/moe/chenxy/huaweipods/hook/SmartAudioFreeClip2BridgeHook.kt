package moe.chenxy.huaweipods.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.HuaweiEqualizerPreset
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import moe.chenxy.huaweipods.pods.SmartAudioFreeClip2BridgePolicy
import moe.chenxy.huaweipods.pods.putHuaweiEqualizerCustomPresets
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

/**
 * 智慧音频已运行时，复用其按设备地址管理的 AAM 通道设置 FreeClip 2 空间音频。
 *
 * 这里只注册动态 Receiver，不启动服务、不保活智慧音频；智慧音频未运行或内部 API
 * 变化时，蓝牙进程会超时回退到模块自己的单次 RFCOMM 事务。
 */
internal object SmartAudioFreeClip2BridgeHook : HookContext() {
    private const val TAG = "HuaweiPods-SmartAudioSpatial"
    private val installed = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HuaweiPods-smartaudio-spatial").apply { isDaemon = true }
    }
    private val inFlightAsyncResults = AtomicInteger(0)
    @Volatile
    private var eqService: Any? = null
    @Volatile
    private var eqListener: Any? = null
    @Volatile
    private var receiverContext: Context? = null

    override fun onCanClose(): Boolean {
        if (inFlightAsyncResults.get() != 0) return false
        val service = eqService
        val listener = eqListener
        return listener == null || (service != null && findEqUnregisterMethod(service) != null)
    }

    override fun onHook() {
        if (!installed.compareAndSet(false, true)) return
        installOfficialStateObservers()
        resolveApplicationContext()?.let(::registerReceiver)
            ?: hookApplicationAttach()
        Log.i(TAG, "Passive FreeClip 2 spatial/EQ bridge enabled")
    }

    override fun onClose() {
        val cleanupFailures = mutableListOf<Throwable>()
        val context = receiverContext
        if (context != null && receiverRegistered.get()) {
            runCatching { context.unregisterReceiver(requestReceiver) }
                .onFailure { error ->
                    if (error !is IllegalArgumentException) {
                        Log.w(TAG, "Unable to unregister FreeClip 2 spatial bridge", error)
                    }
                }
        }
        receiverContext = null
        receiverRegistered.set(false)

        val service = eqService
        val listener = eqListener
        if (service != null && listener != null) {
            runCatching {
                val unregister = checkNotNull(findEqUnregisterMethod(service)) {
                    "Official EQ listener has no unregister method"
                }
                unregister.isAccessible = true
                if (unregister.parameterTypes.size == 2) {
                    unregister.invoke(service, "HuaweiPodsFreeClip2Bridge", listener)
                } else {
                    unregister.invoke(service, listener)
                }
            }.onFailure {
                cleanupFailures += it
                Log.w(TAG, "Unable to unregister official EQ listener", it)
            }
        }
        eqListener = null
        eqService = null
        executor.shutdownNow()
        val executorStopped = runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
            .onFailure(cleanupFailures::add)
            .getOrDefault(false)
        if (!executorStopped) {
            cleanupFailures += IllegalStateException("Smart Audio bridge executor did not stop")
        }
        installed.set(false)
        if (cleanupFailures.isNotEmpty()) {
            throw IllegalStateException("Smart Audio bridge cleanup incomplete").also { failure ->
                cleanupFailures.forEach(failure::addSuppressed)
            }
        }
    }

    /**
     * 官方 UI 收到耳机回读后会经过这两个稳定的公开方法。这里仅旁路转发已经确认的
     * 状态，不改官方字段，也不主动建立新的耳机连接。
     */
    private fun installOfficialStateObservers() {
        runCatching {
            val widgetClass = findClass(
                "com.huawei.audiodevicekit.spatialaudio.ui.widget.SpatialAudioWidget",
            )
            val method = widgetClass.methods.first { candidate ->
                candidate.name == "setSpatialAudioState" && candidate.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType),
                )
            }.apply { isAccessible = true }
            hookAfter(method) {
                val officialValue = args.firstOrNull() as? Int ?: return@hookAfter
                val mode = SmartAudioFreeClip2BridgePolicy.modeFromOfficial(officialValue)
                    ?: return@hookAfter
                publishOfficialState(mode = mode, officialMode = officialValue)
            }
        }.onFailure { Log.w(TAG, "Unable to observe official spatial state", it) }

        runCatching {
            val cardClass = findClass(
                "com.huawei.audiodevicekit.eqadjust.widget.EqAdjustCardView",
            )
            val method = cardClass.methods.first { candidate ->
                candidate.name == "g3" && candidate.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, List::class.java),
                )
            }.apply { isAccessible = true }
            hookAfter(method) {
                val officialValue = args.firstOrNull() as? Int ?: return@hookAfter
                val equalizer = parseOfficialEqualizerState(
                    supported = true,
                    selectedId = officialValue,
                    values = args.getOrNull(1) as? List<*>,
                )
                publishOfficialState(
                    effect = SmartAudioFreeClip2BridgePolicy.soundEffectFromOfficial(officialValue),
                    officialEffect = officialValue,
                    equalizer = equalizer,
                )
            }
        }.onFailure { Log.w(TAG, "Unable to observe official EQ state", it) }
    }

    private fun publishOfficialState(
        mode: FreeClip2SpatialAudioMode? = null,
        effect: FreeClip2SoundEffect? = null,
        officialMode: Int? = null,
        officialEffect: Int? = null,
        equalizer: HuaweiEqualizerState? = null,
    ) {
        if (mode == null && effect == null && equalizer == null) return
        val appContext = resolveApplicationContext() ?: return
        val address = currentDeviceAddress() ?: return
        runCatching {
            appContext.sendIdentitySharingBroadcast(
                Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_STATE).apply {
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                    officialMode?.let {
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_OFFICIAL_MODE, it)
                    }
                    officialEffect?.let {
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_OFFICIAL_EFFECT, it)
                    }
                    equalizer?.let {
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SUPPORTED, it.supported)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID, it.selectedId)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME, it.selectedName)
                        it.selectedGains?.let { gains ->
                            putExtra(
                                HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS,
                                gains.toIntArray(),
                            )
                        }
                        putHuaweiEqualizerCustomPresets(it.customPresets)
                    }
                    setPackage(SmartAudioFreeClip2BridgePolicy.BLUETOOTH_PACKAGE)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
            Log.i(
                TAG,
                "Official FreeClip 2 state published mode=$mode effect=$effect " +
                    "equalizer=${equalizer?.selectedId}",
            )
        }.onFailure { Log.w(TAG, "Unable to publish official FreeClip 2 state", it) }
    }

    private fun hookApplicationAttach() {
        runCatching {
            val method = Application::class.java.getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
            hookAfter(method) {
                val context = (instance as? Application)?.applicationContext
                    ?: (args.firstOrNull() as? Context)?.applicationContext
                context?.let(::registerReceiver)
            }
        }.onFailure { Log.w(TAG, "Unable to observe Smart Audio application attach", it) }
    }

    private fun registerReceiver(context: Context) {
        if (Application.getProcessName() != SmartAudioFreeClip2BridgePolicy.SMART_AUDIO_PACKAGE) return
        if (!receiverRegistered.compareAndSet(false, true)) return
        runCatching {
            context.registerReceiver(
                requestReceiver,
                IntentFilter().apply {
                    addAction(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_SET)
                    addAction(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_QUERY)
                    addAction(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET)
                },
                Context.RECEIVER_EXPORTED,
            )
            receiverContext = context
        }.onFailure {
            receiverRegistered.set(false)
            Log.w(TAG, "Unable to register FreeClip 2 spatial bridge", it)
        }
    }

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val appContext = context?.applicationContext ?: return
            val request = intent ?: return
            val requesterPackage = sentFromPackage ?: return
            val trustedSender = if (
                request.action == HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET
            ) {
                SmartAudioFreeClip2BridgePolicy.isTrustedEqualizerRequestSender(requesterPackage)
            } else {
                SmartAudioFreeClip2BridgePolicy.isTrustedRequestSender(requesterPackage)
            }
            if (!trustedSender) {
                Log.w(TAG, "Rejected untrusted FreeClip 2 spatial request")
                return
            }
            val nonce = SmartAudioFreeClip2BridgePolicy.normalizeNonce(
                request.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE),
            ) ?: return
            val address = SmartAudioFreeClip2BridgePolicy.normalizeAddress(
                request.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS),
            ) ?: return
            if (request.action == HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_QUERY) {
                handleQueryRequest(appContext, nonce, address)
                return
            }
            if (request.action == HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET) {
                val presetId = request.getIntExtra(
                    HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_PRESET_ID,
                    0x64,
                ).takeIf { it in 0x64..0x66 } ?: return
                val name = request.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME)
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty() && it.toByteArray(StandardCharsets.UTF_8).size <= 32
                    } ?: return
                val gains = request.getIntArrayExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS)
                    ?.toList()
                    ?.takeIf {
                        it.size == HuaweiEqualizerCodec.BAND_COUNT &&
                            it.all { gain -> gain in HuaweiEqualizerCodec.GAIN_RANGE }
                    } ?: return
                handleEqualizerSetRequest(
                    appContext,
                    nonce,
                    address,
                    presetId,
                    name,
                    gains,
                    requesterPackage,
                )
                return
            }
            val mode = FreeClip2SpatialAudioMode.fromProtocolValue(
                request.getIntExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_MODE, -1),
            ) ?: return
            val pendingResult = goAsync()
            executeAsync(pendingResult) {
                val result = runCatching { setSpatialMode(address, mode) }
                    .onFailure { Log.w(TAG, "Official FreeClip 2 spatial write failed", it) }
                    .getOrDefault(BridgeResult(false))
                runCatching {
                    appContext.sendIdentitySharingBroadcast(
                        Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_RESULT).apply {
                            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, nonce)
                            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_MODE, mode.protocolValue)
                            putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ACCEPTED, result.accepted)
                            setPackage(SmartAudioFreeClip2BridgePolicy.BLUETOOTH_PACKAGE)
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        },
                    )
                }.onFailure { Log.w(TAG, "Unable to return FreeClip 2 spatial result", it) }
            }
        }
    }

    private fun handleEqualizerSetRequest(
        context: Context,
        nonce: String,
        address: String,
        presetId: Int,
        name: String,
        gains: List<Int>,
        requesterPackage: String,
    ) {
        val pendingResult = requestReceiver.goAsync()
        executeAsync(pendingResult) {
            val accepted = runCatching {
                setOfficialEqualizer(address, presetId, name, gains)
            }.onFailure { Log.w(TAG, "Official FreeClip 2 equalizer write failed", it) }
                .getOrDefault(false)
            runCatching {
                context.sendIdentitySharingBroadcast(
                    Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_RESULT).apply {
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, nonce)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ACCEPTED, accepted)
                        setPackage(requesterPackage)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    },
                )
            }.onFailure { Log.w(TAG, "Unable to return FreeClip 2 equalizer result", it) }
        }
    }

    private fun setOfficialEqualizer(
        address: String,
        presetId: Int,
        name: String,
        gains: List<Int>,
    ): Boolean {
        if (!address.equals(currentDeviceAddress(), ignoreCase = true)) return false
        val service = resolveEqService() ?: return false
        service.javaClass.methods.first { method ->
            method.name == "setEqAdjust" && method.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType,
                ),
            )
        }.apply { isAccessible = true }.invoke(
            service,
            address,
            presetId,
            name,
            gains.map(Int::toByte).toByteArray(),
            1,
        )
        service.javaClass.methods.first { method ->
            method.name == "getEqAdjust" && method.parameterTypes.size == 2
        }.apply { isAccessible = true }.invoke(service, address, false)
        Log.i(TAG, "Official FreeClip 2 equalizer write dispatched preset=$presetId")
        return true
    }

    private fun handleQueryRequest(
        context: Context,
        nonce: String,
        address: String,
    ) {
        val pendingResult = requestReceiver.goAsync()
        executeAsync(pendingResult) {
            val accepted = runCatching { queryOfficialAudioState(address) }
                .onFailure { Log.w(TAG, "Official FreeClip 2 state query failed", it) }
                .getOrDefault(false)
            runCatching {
                context.sendIdentitySharingBroadcast(
                    Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_QUERY_RESULT).apply {
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, nonce)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                        putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ACCEPTED, accepted)
                        setPackage(SmartAudioFreeClip2BridgePolicy.BLUETOOTH_PACKAGE)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    },
                )
            }.onFailure { Log.w(TAG, "Unable to return FreeClip 2 query result", it) }
        }
    }

    private fun executeAsync(
        pendingResult: BroadcastReceiver.PendingResult,
        block: () -> Unit,
    ) {
        inFlightAsyncResults.incrementAndGet()
        runCatching {
            executor.execute {
                try {
                    block()
                } finally {
                    runCatching(pendingResult::finish)
                    inFlightAsyncResults.decrementAndGet()
                }
            }
        }.onFailure {
            runCatching(pendingResult::finish)
            inFlightAsyncResults.decrementAndGet()
            throw it
        }
    }

    /**
     * 复用智慧音频已经持有的设备会话读取真实状态。这里不会新建 RFCOMM socket，
     * 因而不会把智慧音频自己的连接挤掉。
     */
    private fun queryOfficialAudioState(address: String): Boolean {
        if (!address.equals(currentDeviceAddress(), ignoreCase = true)) return false
        val spatialApiClass = findClass("kl.a")
        val spatialApi = spatialApiClass.declaredMethods.first { method ->
            method.name == "o" && method.parameterTypes.isEmpty()
        }.apply { isAccessible = true }.invoke(null) ?: return false
        spatialApiClass.declaredMethods.first { method ->
            method.name == "n" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }.apply { isAccessible = true }.invoke(spatialApi, address)

        val service = resolveEqService() ?: return true
        service.javaClass.methods.first { method ->
            method.name == "getEqAdjust" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, Boolean::class.javaPrimitiveType),
            )
        }.apply { isAccessible = true }.invoke(service, address, false)
        Log.i(TAG, "Official FreeClip 2 spatial/EQ query dispatched")
        return true
    }

    private fun resolveEqService(): Any? {
        eqService?.let { return it }
        val serviceClass = findClass(
            "com.huawei.audiodevicekit.drouter.core.eqadjust.EqAdjustService",
        )
        val helperClass = findClass("ta.a")
        val service = helperClass.declaredMethods.first { method ->
            method.name == "a" && method.parameterTypes.contentEquals(
                arrayOf(Class::class.java, String::class.java),
            )
        }.apply { isAccessible = true }.invoke(null, serviceClass, "/eqadjust/service/EqAdjustApi")
            ?: return null
        val listenerClass = findClass(
            "com.huawei.audiodevicekit.drouter.core.eqadjust.EqAdjustService\$IEqGetAdjustListener",
        )
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { proxy, method, args ->
            when (method.name) {
                "onEqSupportAndModeResult" -> {
                    val success = args?.getOrNull(0) as? Boolean ?: false
                    val supported = args?.getOrNull(1) as? Boolean ?: false
                    val selectedId = args?.getOrNull(2) as? Int ?: -1
                    val equalizer = if (success) {
                        parseOfficialEqualizerState(
                            supported = supported,
                            selectedId = selectedId,
                            values = args?.getOrNull(3) as? List<*>,
                        )
                    } else {
                        null
                    }
                    equalizer?.let { publishOfficialState(equalizer = it) }
                    null
                }
                "toString" -> "HuaweiPodsEqListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        service.javaClass.methods.first { method ->
            method.name == "registerEqChangeListener" && method.parameterTypes.size == 2
        }.apply { isAccessible = true }.invoke(service, "HuaweiPodsFreeClip2Bridge", listener)
        eqListener = listener
        eqService = service
        return service
    }

    private fun findEqUnregisterMethod(service: Any) = service.javaClass.methods.firstOrNull { method ->
        method.name.equals("unregisterEqChangeListener", ignoreCase = true) &&
            method.parameterTypes.size in 1..2
    }

    private fun parseOfficialEqualizerState(
        supported: Boolean,
        selectedId: Int,
        values: List<*>?,
    ): HuaweiEqualizerState? {
        if (selectedId !in 0..0xFF || values == null) return null
        val presets = values.mapNotNull { wrapper ->
            val bean = wrapper?.let { invokeNoArg(it, "a") } ?: wrapper ?: return@mapNotNull null
            val id = (invokeNoArg(bean, "getEqType") as? Number)?.toInt() ?: return@mapNotNull null
            val gains = (invokeNoArg(bean, "getEqArray") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?.takeIf { it.size == HuaweiEqualizerCodec.BAND_COUNT }
            OfficialEqualizerEntry(
                id = id,
                name = invokeNoArg(bean, "getEqName") as? String,
                gains = gains,
                custom = invokeNoArg(bean, "isCustom") as? Boolean ?: false,
            )
        }
        val selected = presets.singleOrNull { it.id == selectedId }
        return HuaweiEqualizerState(
            supported = supported,
            selectedId = selectedId,
            builtInIds = presets.filterNot(OfficialEqualizerEntry::custom).map { it.id },
            bandCount = selected?.gains?.size ?: HuaweiEqualizerCodec.BAND_COUNT,
            selectedName = selected?.name,
            selectedGains = selected?.gains,
            customPresets = presets.filter(OfficialEqualizerEntry::custom).mapNotNull { item ->
                val gains = item.gains ?: return@mapNotNull null
                HuaweiEqualizerPreset(item.id, item.name.orEmpty(), gains)
            },
        )
    }

    private fun setSpatialMode(
        address: String,
        mode: FreeClip2SpatialAudioMode,
    ): BridgeResult {
        val currentAddress = currentDeviceAddress()
        if (!address.equals(currentAddress, ignoreCase = true)) {
            Log.w(TAG, "FreeClip 2 spatial target is not Smart Audio current device")
            return BridgeResult(false)
        }
        val apiClass = findClass("kl.a")
        val modeClass = findClass("kl.b\$d")
        val api = apiClass.declaredMethods.first { method ->
            method.name == "o" && method.parameterTypes.isEmpty()
        }.apply { isAccessible = true }.invoke(null) ?: return BridgeResult(false)
        val officialMode = modeClass.declaredMethods.first { method ->
            method.name == "j" && method.parameterTypes.contentEquals(
                arrayOf(Int::class.javaPrimitiveType),
            )
        }.apply { isAccessible = true }.invoke(
            null,
            SmartAudioFreeClip2BridgePolicy.officialModeFor(mode),
        ) ?: return BridgeResult(false)

        /*
         * FreeClip 2 使用智慧音频的独立空间音频（INDHVS）通道。官方控件在该分支
         * 调用 O(address, mode)，而不是通用 AAM 的 j3(mode)。后者虽然可能返回 true，
         * 但不会更新 FreeClip 2 的 MBB 状态缓存，也不会触发当前页面的状态回调。
         */
        apiClass.declaredMethods.first { method ->
            method.name == "O" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, modeClass),
            )
        }.apply { isAccessible = true }.invoke(api, address, officialMode)

        // 与官方控件一致，写入后查询一次独立空间音频状态，刷新其 MBB 缓存/监听器。
        runCatching {
            apiClass.declaredMethods.first { method ->
                method.name == "n" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }.apply { isAccessible = true }.invoke(api, address)
        }.onFailure { Log.w(TAG, "Unable to query official FreeClip 2 spatial state", it) }

        Log.i(
            TAG,
            "Official FreeClip 2 independent spatial write dispatched " +
                "mode=$mode; awaiting real state readback",
        )
        // O()/n() are asynchronous. Dispatch success is not device confirmation; the observer
        // above must report the requested mode, otherwise Bluetooth falls back to direct AAM.
        return BridgeResult(true)
    }

    private fun currentDeviceAddress(): String? = runCatching {
        val pluginClass = findClass("com.huawei.audiodevicekit.kitutils.plugin.Plugin")
        val busClass = findClass("q7.a")
        val getPlugin = pluginClass.declaredMethods.first { method ->
            method.name == "get" && method.parameterTypes.contentEquals(arrayOf(Class::class.java))
        }.apply { isAccessible = true }
        val bus = getPlugin.invoke(null, busClass) ?: return@runCatching null
        val direct = invokeNoArg(bus, "T0") as? String
        val info = invokeNoArg(bus, "getDeviceInfo")
        SmartAudioFreeClip2BridgePolicy.normalizeAddress(
            direct ?: info?.let { invokeNoArg(it, "c") as? String },
        )
    }.getOrNull()

    private fun invokeNoArg(target: Any, methodName: String): Any? = target.javaClass.methods
        .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
        ?.apply { isAccessible = true }
        ?.invoke(target)

    private fun resolveApplicationContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()?.applicationContext

    private data class BridgeResult(
        val accepted: Boolean,
    )

    private data class OfficialEqualizerEntry(
        val id: Int,
        val name: String?,
        val gains: List<Int>?,
        val custom: Boolean,
    )
}
