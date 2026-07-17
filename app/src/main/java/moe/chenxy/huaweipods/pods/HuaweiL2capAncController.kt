package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log as AndroidLog
import moe.chenxy.huaweipods.hook.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.ExperimentalStdlibApi
import kotlin.text.HexFormat

@SuppressLint("MissingPermission")
object HuaweiL2capAncController {
    private const val TAG = "HuaweiPods-HuaweiAnc"
    private const val RFCOMM_CONNECT_TIMEOUT_MS = 3_000L
    private const val RFCOMM_CONNECT_RETRY_DELAY_MS = 350L
    private const val RFCOMM_CONNECT_ATTEMPTS = 2
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ANC_ON_PACKET = huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x04, 0x01, 0x01, 0x01, 0x78, 0x00)
    private val ANC_OFF_PACKET = huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x04, 0x01, 0x01, 0x00, 0x68, 0x21)
    private val ANC_LEVEL_PACKETS = arrayOf(
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x00, 0x27, 0x13),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x01, 0x37, 0x32),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x02, 0x07, 0x51),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x03, 0x17, 0x70),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x04, 0x67, 0x97),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x05, 0x77, 0xB6),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x06, 0x47, 0xD5),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x07, 0x57, 0xF4),
        huaweiPacket(0x5A, 0x00, 0x06, 0x00, 0x2B, 0x08, 0x01, 0x01, 0x08, 0xA6, 0x1B),
    )

    private val executor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var deviceAddress: String? = null
    private var socketLabel: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setAncEnabled(context: Context, device: BluetoothDevice, enabled: Boolean) {
        val packet = if (enabled) ANC_ON_PACKET else ANC_OFF_PACKET
        enqueueWrite(context, device, packet, "enabled=$enabled")
    }

    fun setAncLevel(context: Context, device: BluetoothDevice, level: Int) {
        val safeLevel = level.coerceIn(0, ANC_LEVEL_PACKETS.lastIndex)
        enqueueWrite(context, device, ANC_LEVEL_PACKETS[safeLevel], "level=$safeLevel")
    }

    fun sendRawPacket(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        enqueueWrite(context, device, packet, "raw $description", keepSocket, onComplete)
    }

    fun sendRawPacketOnce(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        sendRawPacket(context, device, packet, description, keepSocket = false, onComplete)
    }

    private fun enqueueWrite(
        context: Context,
        device: BluetoothDevice,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext ?: context
        logInfo(appContext, "Huawei ANC enqueue $description keepSocket=$keepSocket device=${device.address}")
        runCatching {
            executor.execute {
                runCatching {
                    logInfo(appContext, "Huawei ANC worker started $description keepSocket=$keepSocket device=${device.address}")
                    val currentSocket = ensureSocket(device)
                    currentSocket.outputStream.write(packet)
                    currentSocket.outputStream.flush()
                    val hex = packet.toHexString()
                    RfcommLog.d(appContext, "RFCOMM/TX", "$description $hex")
                    logInfo(
                        appContext,
                        "Huawei ANC RFCOMM write finished $description keepSocket=$keepSocket socket=$socketLabel packet=$hex device=${device.address}"
                    )
                }.onFailure {
                    closeSocket()
                    logError(appContext, "Huawei ANC send failed $description device=${device.address}", it)
                    notifyComplete(onComplete, false)
                }.onSuccess {
                    if (!keepSocket) closeSocket()
                    notifyComplete(onComplete, true)
                }
            }
        }.onFailure {
            logError(appContext, "Huawei ANC enqueue failed $description device=${device.address}", it)
            notifyComplete(onComplete, false)
        }
    }

    fun disconnect(device: BluetoothDevice? = null) {
        if (device != null && deviceAddress != device.address) return
        executor.execute { closeSocket() }
    }

    private fun ensureSocket(device: BluetoothDevice): BluetoothSocket {
        val currentSocket = socket
        if (currentSocket != null && deviceAddress == device.address) {
            Log.w(TAG, "Huawei ANC reusing RFCOMM socket label=$socketLabel device=${device.address}")
            return currentSocket
        }

        closeSocket()
        var lastFailure: Throwable? = null
        for (candidate in socketCandidates(device)) {
            repeat(RFCOMM_CONNECT_ATTEMPTS) { attempt ->
                Log.w(
                    TAG,
                    "Huawei ANC connecting RFCOMM label=${candidate.label} attempt=${attempt + 1} device=${device.address}"
                )
                runCatching {
                    val newSocket = connectSocketWithTimeout(candidate.create(), candidate.label)
                    socket = newSocket
                    deviceAddress = device.address
                    socketLabel = candidate.label
                    Log.w(TAG, "Huawei ANC RFCOMM connected label=${candidate.label} device=${device.address}")
                    return newSocket
                }.onFailure {
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

    private fun connectSocketWithTimeout(socket: BluetoothSocket, label: String): BluetoothSocket {
        val connected = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread({
            runCatching {
                socket.connect()
                connected.set(true)
            }.onFailure { failure.set(it) }
        }, "HuaweiAnc-rfcomm-connect")
        thread.start()
        thread.join(RFCOMM_CONNECT_TIMEOUT_MS)

        if (connected.get()) return socket
        failure.get()?.let {
            runCatching { socket.close() }
                .onFailure { closeError -> Log.w(TAG, "Huawei ANC RFCOMM failure close failed label=$label", closeError) }
            throw it
        }
        runCatching { socket.close() }
            .onFailure { Log.w(TAG, "Huawei ANC RFCOMM timeout close failed label=$label", it) }
        throw SocketTimeoutException("Huawei ANC RFCOMM connect timed out after ${RFCOMM_CONNECT_TIMEOUT_MS}ms label=$label")
    }

    private fun closeSocket() {
        val oldSocket = socket
        socket = null
        deviceAddress = null
        socketLabel = null
        runCatching { oldSocket?.close() }
            .onFailure { Log.w(TAG, "Huawei ANC socket close failed", it) }
    }

    private data class SocketCandidate(
        val label: String,
        val create: () -> BluetoothSocket,
    )

    private fun huaweiPacket(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

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

    private fun notifyComplete(callback: ((Boolean) -> Unit)?, success: Boolean) {
        callback ?: return
        mainHandler.post { callback(success) }
    }
}
