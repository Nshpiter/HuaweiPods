package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.utils.miuiStrongToast.MiuiStrongToastUtil
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction

@SuppressLint("MissingPermission", "StaticFieldLeak")
object HuaweiHfpController {
    private const val TAG = "HuaweiPods-HuaweiHfp"

    private var context: Context? = null
    private var device: BluetoothDevice? = null
    private var receiverRegistered = false
    private var currentBattery: BatteryParams? = null
    private var currentAnc = 1
    private var currentAncLevel = 0
    private var lastDispatchedAncLevel: Int? = null
    private var connectedBroadcastSent = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                HuaweiPodsAction.ACTION_PODS_UI_INIT,
                HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                    sendConnectionState("connected")
                    sendConnected(force = true)
                    currentBattery?.let { sendBattery(it) }
                    sendAnc(currentAnc)
                    sendAncLevel(currentAncLevel)
                }
                HuaweiPodsAction.ACTION_ANC_SELECT -> {
                    setAncMode(receivedIntent.getIntExtra("status", currentAnc))
                }
                HuaweiPodsAction.ACTION_CYCLE_ANC -> {
                    val nextStatus = if (currentAnc == 1) 2 else 1
                    Log.i(TAG, "Huawei ANC cycle current=$currentAnc next=$nextStatus")
                    setAncMode(nextStatus)
                }
                HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET -> {
                    setAncLevel(receivedIntent.getIntExtra("level", currentAncLevel))
                }
                HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND -> {
                    if (BuildConfig.DEBUG) sendLegacyDebugHex(receivedIntent.getStringExtra("hex").orEmpty())
                }
                HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET -> {
                    setGesture(receivedIntent)
                }
            }
        }
    }

    fun connectPod(context: Context, device: BluetoothDevice) {
        ensureSession(context, device)
        sendConnectionState("connecting")
        sendConnected()
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        if (this.device?.address != device.address) return
        MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt(context, device)
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
            putExtra("address", device.address)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_DISCONNECTED) {
            putExtra("address", device.address)
        }
        currentBattery = null
        currentAnc = 1
        currentAncLevel = 0
        lastDispatchedAncLevel = null
        connectedBroadcastSent = false
        this.device = null
        this.context = null
        HuaweiL2capAncController.disconnect(device)
        Log.d(TAG, "Huawei HFP disconnected device=${device.address}")
    }

    fun handleAtCommand(
        context: Context,
        device: BluetoothDevice,
        text: String
    ): BatteryParams? {
        val result = HuaweiBatteryParser.parse(text) ?: return null
        ensureSession(context, device)
        currentBattery = result.battery
        sendConnectionState("connected")
        sendConnected()
        sendBattery(result.battery)
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(context, result.battery, device)
        Log.i(TAG, "Huawei battery parsed device=${device.address} values=${result.values}")
        return result.battery
    }

    fun setAncMode(status: Int) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei ANC skipped: device null status=$status")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei ANC skipped: context null status=$status device=${currentDevice.address}")
            return
        }
        val targetStatus = if (status == 1) 1 else 2
        Log.i(TAG, "Huawei ANC select received rawStatus=$status targetStatus=$targetStatus device=${currentDevice.address}")
        currentAnc = targetStatus
        Log.i(TAG, "Huawei ANC dispatch enabled=${targetStatus != 1} device=${currentDevice.address}")
        HuaweiL2capAncController.setAncEnabled(currentContext, currentDevice, targetStatus != 1)
        Log.i(TAG, "Huawei ANC dispatched enabled=${targetStatus != 1} device=${currentDevice.address}")
        sendAnc(targetStatus)
        Log.i(TAG, "Huawei ANC requested status=$status mapped=$targetStatus device=${currentDevice.address}")
    }

    fun setAncLevel(level: Int) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei ANC level skipped: device null level=$level")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei ANC level skipped: context null level=$level device=${currentDevice.address}")
            return
        }
        val safeLevel = level.coerceIn(0, 8)
        currentAncLevel = safeLevel
        if (lastDispatchedAncLevel == safeLevel) {
            Log.i(TAG, "Huawei ANC level duplicate skipped level=$safeLevel device=${currentDevice.address}")
            sendAncLevel(safeLevel)
            return
        }
        lastDispatchedAncLevel = safeLevel
        Log.i(TAG, "Huawei ANC level dispatch level=$safeLevel device=${currentDevice.address}")
        HuaweiL2capAncController.setAncLevel(currentContext, currentDevice, safeLevel)
        sendAncLevel(safeLevel)
    }

    fun sendLegacyDebugHex(hex: String) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei legacy debug skipped: device null")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei legacy debug skipped: context null device=${currentDevice.address}")
            return
        }
        val packet = parseHex(hex) ?: run {
            Log.w(TAG, "Huawei legacy debug invalid HEX: $hex")
            return
        }
        Log.i(TAG, "Huawei legacy debug send bytes=${packet.size} device=${currentDevice.address}")
        HuaweiL2capAncController.sendRawPacket(currentContext, currentDevice, packet, "debug")
    }

    fun setGesture(intent: Intent) {
        val currentDevice = device ?: run {
            Log.w(TAG, "Huawei gesture skipped: device null")
            return
        }
        val currentContext = context ?: run {
            Log.w(TAG, "Huawei gesture skipped: context null device=${currentDevice.address}")
            return
        }
        val side = HuaweiGestureSide.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_SIDE))
            ?: HuaweiGestureSide.fromProtocolValue(intent.getIntExtra(HuaweiGestureController.EXTRA_SIDE, -1))
            ?: run {
                Log.w(TAG, "Huawei gesture skipped: invalid side device=${currentDevice.address}")
                return
            }
        val action = HuaweiGestureAction.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION))
            ?: HuaweiGestureAction.fromProtocolValue(intent.getIntExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, -1))
            ?: run {
                Log.w(TAG, "Huawei gesture skipped: invalid action device=${currentDevice.address}")
                return
            }
        Log.i(TAG, "Huawei gesture dispatch side=${side.extraValue} action=${action.extraValue} device=${currentDevice.address}")
        HuaweiGestureController.setDoubleTap(currentContext, currentDevice, side, action)
    }

    private fun ensureSession(context: Context, device: BluetoothDevice) {
        this.context = context.applicationContext ?: context
        this.device = device
        registerReceiver()
    }

    private fun registerReceiver() {
        val ctx = context ?: return
        if (receiverRegistered) return
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_ANC_SELECT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CYCLE_ANC)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET)
            if (BuildConfig.DEBUG) {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND)
            }
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET)
        }, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun sendConnected(force: Boolean = false) {
        if (connectedBroadcastSent && !force) return
        val currentDevice = device ?: return
        val deviceName = currentDevice.name ?: currentDevice.alias ?: ""
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTED) {
            putExtra("address", currentDevice.address)
            putExtra("device_name", deviceName)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTED) {
            putExtra("device_name", deviceName)
        }
        connectedBroadcastSent = true
    }

    private fun sendConnectionState(state: String) {
        val currentDevice = device ?: return
        val deviceName = currentDevice.name ?: currentDevice.alias ?: ""
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED) {
            putExtra("address", currentDevice.address)
            putExtra("device_name", deviceName)
            putExtra("state", state)
        }
    }

    private fun sendBattery(battery: BatteryParams) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", battery)
            putBatteryExtras(battery)
        }
    }

    private fun sendAnc(status: Int) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", status)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", status)
        }
    }

    private fun sendAncLevel(level: Int) {
        sendAppBroadcast(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED) {
            putExtra("level", level)
        }
        sendExternalBroadcast(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED) {
            putExtra("level", level)
        }
    }

    private fun sendAppBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = context ?: return
        val currentDevice = device
        Intent(action).apply {
            putExtra("vendor", "huawei")
            currentDevice?.let {
                putExtra("address", it.address)
                putExtra("device_name", it.name ?: it.alias ?: "")
            }
            fill()
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun sendExternalBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = context ?: return
        val currentDevice = device
        listOf("com.milink.service", "com.xiaomi.bluetooth", "com.android.settings").forEach { targetPackage ->
            Intent(action).apply {
                putExtra("vendor", "huawei")
                currentDevice?.let {
                    putExtra("address", it.address)
                    putExtra("device_name", it.name ?: it.alias ?: "")
                }
                fill()
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    private fun Intent.putBatteryExtras(status: BatteryParams) {
        putExtra("left_battery", status.left?.battery ?: 0)
        putExtra("left_charging", status.left?.isCharging ?: false)
        putExtra("left_connected", status.left?.isConnected ?: false)
        putExtra("right_battery", status.right?.battery ?: 0)
        putExtra("right_charging", status.right?.isCharging ?: false)
        putExtra("right_connected", status.right?.isConnected ?: false)
        putExtra("case_battery", status.case?.battery ?: 0)
        putExtra("case_charging", status.case?.isCharging ?: false)
        putExtra("case_connected", status.case?.isConnected ?: false)
    }

    private fun parseHex(hex: String): ByteArray? {
        val normalized = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (normalized.isEmpty() || normalized.length % 2 != 0) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return normalized.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
