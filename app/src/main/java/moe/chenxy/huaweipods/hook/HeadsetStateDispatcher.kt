package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.pods.HuaweiHfpController
import moe.chenxy.huaweipods.pods.HuaweiGestureAction
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiL2capAncController
import moe.chenxy.huaweipods.pods.RfcommController
import moe.chenxy.huaweipods.pods.isHuaweiFreeBudsByName
import moe.chenxy.huaweipods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction

object HeadsetStateDispatcher : HookContext() {
    private var appRequestReceiverRegistered = false

    override fun onHook() {
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
            handler.post {
                val isHuawei = isHuaweiPod(device)
                Log.d("HuaweiPods", "A2DP Connection State: $currState, isHuaweiPod=$isHuawei")
                val context = instance as ContextWrapper
                registerAppRequestReceiver(context)
                if (!isHuawei) return@post

                val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                if (currState == BluetoothHeadset.STATE_CONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", true)
                    HuaweiHfpController.connectPod(context, device)
                } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", false)
                    HuaweiHfpController.disconnectedPod(context, device)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerAppRequestReceiver(context: Context?) {
        if (context == null || appRequestReceiverRegistered) return
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null) return
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    HuaweiPodsAction.ACTION_PODS_UI_INIT,
                    HuaweiPodsAction.ACTION_REFRESH_STATUS -> {
                        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE).apply {
                            setPackage(BuildConfig.APPLICATION_ID)
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        })
                    }
                    HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST -> {
                        val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        Log.d("HuaweiPods", "connect request from app device=${device.name}/${device.address}")
                        if (isHuaweiPod(device)) {
                            HuaweiHfpController.connectPod(context, device)
                        } else {
                            RfcommController.connectPod(context, device, prefs, appRequested = true)
                        }
                    }
                    HuaweiPodsAction.ACTION_DISCONNECT_POD_REQUEST -> {
                        val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        Log.d("HuaweiPods", "disconnect request from app device=${device.name}/${device.address}")
                        if (isHuaweiPod(device)) {
                            HuaweiHfpController.disconnectedPod(context, device)
                        } else {
                            RfcommController.disconnectedPod(context, device)
                        }
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND -> {
                        if (BuildConfig.DEBUG) sendLegacyDebugPacket(context, receivedIntent)
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET -> {
                        sendGesturePacket(context, receivedIntent)
                    }
                }
            }
        }, IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_DISCONNECT_POD_REQUEST)
            if (BuildConfig.DEBUG) {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND)
            }
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET)
        }, Context.RECEIVER_EXPORTED)
        appRequestReceiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun sendLegacyDebugPacket(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address").orEmpty()
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.w("HuaweiPods", "legacy debug skipped: invalid address=$address")
            return
        }
        val packet = parseHex(intent.getStringExtra("hex").orEmpty()) ?: run {
            Log.w("HuaweiPods", "legacy debug skipped: invalid hex")
            return
        }
        val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) ?: run {
            Log.w("HuaweiPods", "legacy debug skipped: bluetooth adapter null")
            return
        }
        Log.i("HuaweiPods", "legacy debug send address=$address bytes=${packet.size}")
        HuaweiL2capAncController.sendRawPacket(context, device, packet, "legacy-debug")
    }

    @SuppressLint("MissingPermission")
    private fun sendGesturePacket(context: Context, intent: Intent) {
        val address = intent.getStringExtra(HuaweiGestureController.EXTRA_ADDRESS).orEmpty()
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.w("HuaweiPods", "gesture skipped: invalid address=$address")
            return
        }
        val side = HuaweiGestureSide.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_SIDE))
            ?: HuaweiGestureSide.fromProtocolValue(intent.getIntExtra(HuaweiGestureController.EXTRA_SIDE, -1))
            ?: run {
                Log.w("HuaweiPods", "gesture skipped: invalid side address=$address")
                return
            }
        val action = HuaweiGestureAction.fromExtra(intent.getStringExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION))
            ?: HuaweiGestureAction.fromProtocolValue(intent.getIntExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, -1))
            ?: run {
                Log.w("HuaweiPods", "gesture skipped: invalid action address=$address")
                return
            }
        val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) ?: run {
            Log.w("HuaweiPods", "gesture skipped: bluetooth adapter null")
            return
        }
        Log.i("HuaweiPods", "gesture send address=$address side=${side.extraValue} action=${action.extraValue}")
        HuaweiGestureController.setDoubleTap(context, device, side, action)
    }

    @SuppressLint("MissingPermission")
    private fun isHuaweiPod(device: BluetoothDevice): Boolean {
        val name = device.name ?: device.alias ?: return false
        return isHuaweiFreeBudsByName(name)
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
