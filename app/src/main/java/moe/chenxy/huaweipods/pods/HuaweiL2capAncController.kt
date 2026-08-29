package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log as AndroidLog
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.ExperimentalStdlibApi
import kotlin.text.HexFormat

@SuppressLint("MissingPermission")
object HuaweiL2capAncController {
    private const val TAG = "HuaweiPods-HuaweiAnc"
    private const val RFCOMM_CONNECT_TIMEOUT_MS = 3_000L
    private const val RFCOMM_CONNECT_RETRY_DELAY_MS = 350L
    private const val RFCOMM_CONNECT_ATTEMPTS = 2
    private const val ROUTE_PROBE_CONNECT_TIMEOUT_MS = 2_000L
    private const val ROUTE_PROBE_RESPONSE_WINDOW_MS = 1_200L
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val executor = Executors.newSingleThreadExecutor()
    private val routeProbeExecutor = Executors.newSingleThreadExecutor()
    private val submissionLock = Any()
    private val transportGeneration = HuaweiRfcommTransportGeneration()
    private var socket: BluetoothSocket? = null
    private var deviceAddress: String? = null
    private var socketLabel: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeConnectSockets = ConcurrentHashMap.newKeySet<BluetoothSocket>()
    private val activeConnectThreads = ConcurrentHashMap.newKeySet<Thread>()
    private val activeOperations = AtomicInteger(0)
    private val closing = AtomicBoolean(false)

    /** 预检必须无副作用；正在执行系统蓝牙 I/O 时让框架回退为进程重启。 */
    fun canCloseForHotReload(): Boolean =
        !closing.get() &&
            activeOperations.get() == 0 &&
            activeConnectThreads.isEmpty() &&
            activeConnectSockets.isEmpty()

    fun closeForHotReload(): Boolean {
        closing.set(true)
        synchronized(submissionLock) {
            transportGeneration.invalidate()
            closeSocket()
            activeConnectSockets.toList().forEach { connectingSocket ->
                runCatching { connectingSocket.close() }
            }
            activeConnectThreads.toList().forEach(Thread::interrupt)
            executor.shutdownNow()
            routeProbeExecutor.shutdownNow()
        }
        mainHandler.removeCallbacksAndMessages(null)
        val executorStopped = runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
            .getOrDefault(false)
        val routeProbeStopped = runCatching {
            routeProbeExecutor.awaitTermination(1, TimeUnit.SECONDS)
        }.getOrDefault(false)
        activeConnectThreads.toList().forEach { thread ->
            runCatching { thread.join(250L) }
        }
        val connectThreadsStopped = activeConnectThreads.none(Thread::isAlive)
        activeConnectThreads.clear()
        activeConnectSockets.clear()
        return executorStopped && routeProbeStopped && connectThreadsStopped
    }

    fun setAncEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = HuaweiAncPackets.enabled(route, enabled) ?: run {
            notifyComplete(onComplete, false)
            return
        }
        enqueueWrite(context, device, route, packet, "enabled=$enabled", onComplete = onComplete)
    }

    fun setAncMode(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        mode: NoiseControlMode,
        subMode: Int? = null,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = HuaweiAncPackets.mode(route, mode, subMode) ?: run {
            notifyComplete(onComplete, false)
            return
        }
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "mode=$mode subMode=${subMode?.toString(16) ?: "default"}",
            onComplete = onComplete,
        )
    }

    fun setAncLevel(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        level: Int,
    ) {
        val packet = HuaweiAncPackets.level(route, level) ?: return
        val safeLevel = level.coerceIn(0, 8)
        enqueueWrite(context, device, route, packet, "level=$safeLevel")
    }

    fun requestBattery(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onBattery: (BatteryParams) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = HuaweiAncPackets.batteryQuery(route) ?: run {
            notifyComplete(onComplete, false)
            return
        }
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "battery-query",
            onComplete = onComplete,
            responseWindowMs = 1_500L,
            onResponse = { response ->
                val battery = HuaweiRfcommResponseParser.parseBattery(
                    response,
                    includeCase = route.hasChargingCase,
                    useReportedEarbudAvailability = route.usesReportedEarbudAvailability,
                )
                logInfo(
                    context.applicationContext ?: context,
                    "Huawei battery response bytes=${response.size} parsed=${battery != null} device=${device.address}",
                )
                battery?.let(onBattery)
            },
        )
    }

    internal fun requestDeviceInfoIdentity(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onResult: (HuaweiDeviceInfoIdentity?) -> Unit,
    ) {
        val packet = HuaweiAncPackets.deviceInfoQuery(route) ?: run {
            onResult(null)
            return
        }
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "device-info-query",
            responseWindowMs = 1_500L,
            responseComplete = { response ->
                HuaweiDeviceInfoIdentityParser.parse(response) != null
            },
            onComplete = { success ->
                if (!success) onResult(null)
            },
            onResponse = { response ->
                val identity = HuaweiDeviceInfoIdentityParser.parse(response)
                logInfo(
                    context.applicationContext ?: context,
                    "Huawei DeviceInfo response bytes=${response.size} " +
                        "parsed=${identity != null} device=${device.address}",
                )
                onResult(identity)
            },
        )
    }

    /**
     * 用户主动点选已连接的未知设备时使用的窄探测入口。它不依赖 route，也不复用常驻传输：
     * 只尝试一次标准 secure SPP UUID，短超时后关闭独立 socket，响应必须通过严格 DeviceInfo 解析。
     */
    internal fun requestDeviceInfoIdentityRouteFree(
        context: Context,
        device: BluetoothDevice,
        onResult: (HuaweiDeviceInfoIdentity?) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        val address = runCatching { device.address }.getOrDefault("")
        runCatching {
            executeTracked(routeProbeExecutor) {
                var probeSocket: BluetoothSocket? = null
                val identity = runCatching {
                    val packet = HuaweiAncPackets.routeFreeDeviceInfoQuery()
                    probeSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    val connectedSocket = connectSocketWithTimeout(
                        socket = probeSocket,
                        label = "route-probe-secure-spp-$SPP_UUID",
                        timeoutMs = ROUTE_PROBE_CONNECT_TIMEOUT_MS,
                    )
                    connectedSocket.outputStream.write(packet)
                    connectedSocket.outputStream.flush()
                    RfcommLog.d(
                        appContext,
                        "RFCOMM/TX",
                        "route-free-device-info ${packet.toHexString()}",
                    )
                    val response = collectSocketResponse(
                        socket = connectedSocket,
                        timeoutMs = ROUTE_PROBE_RESPONSE_WINDOW_MS,
                        responseComplete = { bytes ->
                            HuaweiDeviceInfoIdentityParser.parse(bytes) != null
                        },
                    )
                    HuaweiDeviceInfoIdentityParser.parse(response).also { parsed ->
                        logInfo(
                            appContext,
                            "Route-free DeviceInfo response bytes=${response.size} " +
                                "parsed=${parsed != null} device=$address",
                        )
                    }
                }.onFailure {
                    logError(appContext, "Route-free DeviceInfo probe failed device=$address", it)
                }.getOrNull()
                runCatching { probeSocket?.close() }
                    .onFailure { Log.w(TAG, "Route-free DeviceInfo socket close failed", it) }
                mainHandler.post { onResult(identity) }
            }
        }.onFailure {
            logError(appContext, "Route-free DeviceInfo probe enqueue failed device=$address", it)
            mainHandler.post { onResult(null) }
        }
    }

    internal fun requestAncState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onResult: (HuaweiAncState?) -> Unit,
    ) {
        val packet = HuaweiAncPackets.currentStateQuery(route) ?: run {
            onResult(null)
            return
        }
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "anc-state-query",
            responseWindowMs = 1_500L,
            responseComplete = { response ->
                HuaweiRfcommResponseParser.parseAncState(response)
                    ?.let(route::validateAncState) != null
            },
            onComplete = { success ->
                if (!success) onResult(null)
            },
            onResponse = { response ->
                val state = HuaweiRfcommResponseParser.parseAncState(response)
                    ?.let(route::validateAncState)
                RfcommLog.d(
                    context.applicationContext ?: context,
                    "RFCOMM/RX",
                    "anc-state-query ${response.toHexString()}",
                )
                logInfo(
                    context.applicationContext ?: context,
                    "Huawei ANC state response bytes=${response.size} parsed=$state device=${device.address}",
                )
                onResult(state)
            },
        )
    }

    fun requestRawPacketOnce(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        responseWindowMs: Long = 1_000L,
        responseComplete: ((ByteArray) -> Boolean)? = null,
        onComplete: ((Boolean) -> Unit)? = null,
        onResponse: (ByteArray) -> Unit,
    ) {
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = description,
            keepSocket = false,
            responseWindowMs = responseWindowMs,
            responseComplete = responseComplete,
            onComplete = onComplete,
            onResponse = onResponse,
        )
    }

    fun sendRawPacket(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        enqueueWrite(context, device, route, packet, "raw $description", keepSocket, onComplete)
    }

    fun sendRawPacketOnce(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        sendRawPacket(context, device, route, packet, description, keepSocket = false, onComplete)
    }

    private fun enqueueWrite(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
        responseWindowMs: Long = 0L,
        responseComplete: ((ByteArray) -> Boolean)? = null,
        onResponse: ((ByteArray) -> Unit)? = null,
    ) {
        val requestGeneration = transportGeneration.snapshot()
        val appContext = context.applicationContext ?: context
        if (!isHuaweiDeviceRouteEnabled(route)) {
            logInfo(
                appContext,
                "Huawei write rejected: disabled route=$route address=${device.address}",
            )
            notifyComplete(onComplete, false)
            return
        }
        logInfo(appContext, "Huawei ANC enqueue $description keepSocket=$keepSocket device=${device.address}")
        runCatching {
            synchronized(submissionLock) {
                if (!transportGeneration.isCurrent(requestGeneration)) {
                    logInfo(
                        appContext,
                        "Huawei ANC canceled stale pre-submit request $description device=${device.address}",
                    )
                    notifyComplete(onComplete, false)
                    return@synchronized
                }
                executeTracked(executor) worker@{
                    if (!transportGeneration.isCurrent(requestGeneration)) {
                        logInfo(
                            appContext,
                            "Huawei ANC canceled stale queued request $description device=${device.address}",
                        )
                        notifyComplete(onComplete, false)
                        return@worker
                    }
                    runCatching {
                        logInfo(appContext, "Huawei ANC worker started $description keepSocket=$keepSocket device=${device.address}")
                        val currentSocket = ensureSocket(device, requestGeneration)
                        ensureCurrentTransportGeneration(requestGeneration)
                        currentSocket.outputStream.write(packet)
                        currentSocket.outputStream.flush()
                        ensureCurrentTransportGeneration(requestGeneration)
                        val hex = packet.toHexString()
                        RfcommLog.d(appContext, "RFCOMM/TX", "$description $hex")
                        if (responseWindowMs > 0L && onResponse != null) {
                            val response = collectSocketResponse(
                                currentSocket,
                                responseWindowMs,
                                responseComplete,
                            )
                            ensureCurrentTransportGeneration(requestGeneration)
                            mainHandler.post {
                                if (transportGeneration.isCurrent(requestGeneration)) {
                                    onResponse(response)
                                }
                            }
                        }
                        ensureCurrentTransportGeneration(requestGeneration)
                        logInfo(
                            appContext,
                            "Huawei ANC RFCOMM write finished $description keepSocket=$keepSocket socket=$socketLabel packet=$hex device=${device.address}"
                        )
                    }.onFailure {
                        closeSocket()
                        if (it is CancellationException) {
                            logInfo(
                                appContext,
                                "Huawei ANC canceled stale active request $description device=${device.address}",
                            )
                        } else {
                            logError(appContext, "Huawei ANC send failed $description device=${device.address}", it)
                        }
                        notifyComplete(onComplete, false)
                    }.onSuccess {
                        if (!keepSocket) closeSocket()
                        notifyComplete(onComplete, true, requestGeneration)
                    }
                }
            }
        }.onFailure {
            logError(appContext, "Huawei ANC enqueue failed $description device=${device.address}", it)
            notifyComplete(onComplete, false)
        }
    }

    fun disconnect(device: BluetoothDevice? = null) {
        synchronized(submissionLock) {
            val invalidatedGeneration = transportGeneration.invalidate()
            Log.w(
                TAG,
                "Huawei ANC disconnect queued generation=$invalidatedGeneration device=${device?.address.orEmpty()}",
            )
            executeTracked(executor) { closeSocket() }
        }
    }

    private fun ensureSocket(
        device: BluetoothDevice,
        requestGeneration: Long,
    ): BluetoothSocket {
        ensureCurrentTransportGeneration(requestGeneration)
        val currentSocket = socket
        if (currentSocket != null && deviceAddress == device.address) {
            Log.w(TAG, "Huawei ANC reusing RFCOMM socket label=$socketLabel device=${device.address}")
            return currentSocket
        }

        closeSocket()
        var lastFailure: Throwable? = null
        for (candidate in socketCandidates(device)) {
            repeat(RFCOMM_CONNECT_ATTEMPTS) { attempt ->
                ensureCurrentTransportGeneration(requestGeneration)
                Log.w(
                    TAG,
                    "Huawei ANC connecting RFCOMM label=${candidate.label} attempt=${attempt + 1} device=${device.address}"
                )
                runCatching {
                    val newSocket = connectSocketWithTimeout(candidate.create(), candidate.label)
                    if (!transportGeneration.isCurrent(requestGeneration)) {
                        runCatching { newSocket.close() }
                            .onFailure { Log.w(TAG, "Huawei ANC stale RFCOMM close failed", it) }
                        ensureCurrentTransportGeneration(requestGeneration)
                    }
                    socket = newSocket
                    deviceAddress = device.address
                    socketLabel = candidate.label
                    Log.w(TAG, "Huawei ANC RFCOMM connected label=${candidate.label} device=${device.address}")
                    return newSocket
                }.onFailure {
                    if (it is CancellationException) throw it
                    lastFailure = it
                    Log.w(
                        TAG,
                        "Huawei ANC RFCOMM candidate failed label=${candidate.label} attempt=${attempt + 1} device=${device.address}",
                        it
                    )
                    if (attempt + 1 < RFCOMM_CONNECT_ATTEMPTS) {
                        Thread.sleep(RFCOMM_CONNECT_RETRY_DELAY_MS)
                    }
                }
            }
        }
        throw lastFailure ?: IOException("No Huawei ANC RFCOMM candidate succeeded")
    }

    private fun ensureCurrentTransportGeneration(requestGeneration: Long) {
        if (!transportGeneration.isCurrent(requestGeneration)) {
            throw CancellationException("Stale Huawei RFCOMM transport generation")
        }
    }

    private fun socketCandidates(device: BluetoothDevice): List<SocketCandidate> {
        val candidates = mutableListOf<SocketCandidate>()
        fun add(label: String, create: () -> BluetoothSocket) {
            if (candidates.none { it.label == label }) candidates += SocketCandidate(label, create)
        }

        add("secure-spp-$SPP_UUID") { device.createRfcommSocketToServiceRecord(SPP_UUID) }
        add("insecure-spp-$SPP_UUID") { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }

        device.uuids.orEmpty()
            .mapNotNull { it?.uuid }
            .filter { it != SPP_UUID }
            .forEach { uuid ->
                add("secure-sdp-$uuid") { device.createRfcommSocketToServiceRecord(uuid) }
                add("insecure-sdp-$uuid") { device.createInsecureRfcommSocketToServiceRecord(uuid) }
            }

        (1..8).forEach { channel ->
            add("secure-channel-$channel") { hiddenChannelSocket(device, channel, secure = true) }
            add("insecure-channel-$channel") { hiddenChannelSocket(device, channel, secure = false) }
        }
        return candidates
    }

    private fun hiddenChannelSocket(device: BluetoothDevice, channel: Int, secure: Boolean): BluetoothSocket {
        val methodName = if (secure) "createRfcommSocket" else "createInsecureRfcommSocket"
        val method = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
        return method.invoke(device, channel) as BluetoothSocket
    }

    private fun connectSocketWithTimeout(
        socket: BluetoothSocket,
        label: String,
        timeoutMs: Long = RFCOMM_CONNECT_TIMEOUT_MS,
    ): BluetoothSocket {
        val connected = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread({
            try {
                runCatching {
                    socket.connect()
                    connected.set(true)
                }.onFailure { failure.set(it) }
            } finally {
                activeConnectThreads.remove(Thread.currentThread())
                activeConnectSockets.remove(socket)
            }
        }, "HuaweiAnc-rfcomm-connect")
        activeConnectSockets.add(socket)
        activeConnectThreads.add(thread)
        thread.start()
        try {
            thread.join(timeoutMs)
        } catch (interrupted: InterruptedException) {
            runCatching { socket.close() }
            Thread.currentThread().interrupt()
            throw CancellationException("Huawei RFCOMM connect interrupted").apply {
                initCause(interrupted)
            }
        }

        if (connected.get()) return socket
        failure.get()?.let {
            runCatching { socket.close() }
                .onFailure { closeError -> Log.w(TAG, "Huawei ANC RFCOMM failure close failed label=$label", closeError) }
            throw it
        }
        runCatching { socket.close() }
            .onFailure { Log.w(TAG, "Huawei ANC RFCOMM timeout close failed label=$label", it) }
        throw SocketTimeoutException("Huawei ANC RFCOMM connect timed out after ${timeoutMs}ms label=$label")
    }

    private fun closeSocket() {
        val oldSocket = socket
        socket = null
        deviceAddress = null
        socketLabel = null
        runCatching { oldSocket?.close() }
            .onFailure { Log.w(TAG, "Huawei ANC socket close failed", it) }
    }

    private fun collectSocketResponse(
        socket: BluetoothSocket,
        timeoutMs: Long,
        responseComplete: ((ByteArray) -> Boolean)?,
    ): ByteArray {
        val input = socket.inputStream
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var lastReadAt = 0L
        while (System.nanoTime() < deadline) {
            val available = runCatching { input.available() }.getOrDefault(0)
            if (available > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, available))
                if (read > 0) {
                    output.write(buffer, 0, read)
                    lastReadAt = System.nanoTime()
                    if (responseComplete?.invoke(output.toByteArray()) == true) break
                }
                continue
            }
            if (
                responseComplete == null &&
                lastReadAt > 0L &&
                System.nanoTime() - lastReadAt >= 200_000_000L
            ) break
            Thread.sleep(20L)
        }
        return output.toByteArray()
    }

    private data class SocketCandidate(
        val label: String,
        val create: () -> BluetoothSocket,
    )

    @OptIn(ExperimentalStdlibApi::class)
    private fun ByteArray.toHexString(): String = toHexString(HexFormat.UpperCase)

    private fun logInfo(context: Context, message: String) {
        Log.w(TAG, message)
        AndroidLog.i(TAG, message)
        RfcommLog.i(context, TAG, message)
    }

    private fun logError(context: Context, message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        AndroidLog.e(TAG, message, throwable)
        RfcommLog.e(context, TAG, "$message: ${throwable.message.orEmpty()}")
    }

    private fun notifyComplete(
        callback: ((Boolean) -> Unit)?,
        success: Boolean,
        requestGeneration: Long? = null,
    ) {
        callback ?: return
        mainHandler.post {
            val belongsToCurrentGeneration = requestGeneration == null ||
                transportGeneration.isCurrent(requestGeneration)
            callback(success && belongsToCurrentGeneration)
        }
    }

    private fun executeTracked(executorService: ExecutorService, block: () -> Unit) {
        check(!closing.get()) { "Huawei RFCOMM controller is closing" }
        activeOperations.incrementAndGet()
        runCatching {
            executorService.execute {
                try {
                    block()
                } finally {
                    activeOperations.decrementAndGet()
                }
            }
        }.onFailure {
            activeOperations.decrementAndGet()
            throw it
        }
    }
}

internal class HuaweiRfcommTransportGeneration {
    private val value = AtomicLong(0L)

    fun snapshot(): Long = value.get()

    fun invalidate(): Long = value.incrementAndGet()

    fun isCurrent(snapshot: Long): Boolean = value.get() == snapshot
}
