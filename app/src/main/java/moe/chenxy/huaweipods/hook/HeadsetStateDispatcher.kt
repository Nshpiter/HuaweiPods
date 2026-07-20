package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
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
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.detectHuaweiDeviceRoute
import moe.chenxy.huaweipods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import java.util.concurrent.ConcurrentHashMap

object HeadsetStateDispatcher : HookContext() {
    private var appRequestReceiverRegistered = false
    private val connectedA2dpAddresses = ConcurrentHashMap.newKeySet<String>()

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
                    connectedA2dpAddresses.add(device.address.uppercase())
                    statusBarManager.setIconVisibility("wireless_headset", true)
                    HuaweiHfpController.connectPod(context, device)
                } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                    connectedA2dpAddresses.remove(device.address.uppercase())
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
                        if (isHuaweiPod(device) && isDeviceConnected(device)) {
                            HuaweiHfpController.connectPod(context, device)
                        } else if (isHuaweiPod(device)) {
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
                    HuaweiPodsAction.ACTION_DISCONNECT_POD_REQUEST -> {
                        val device = receivedIntent.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        Log.d("HuaweiPods", "disconnect request from app device=${device.name}/${device.address}")
                        if (isHuaweiPod(device)) {
                            HuaweiHfpController.disconnectedPod(context, device)
                        } else {
                            notifyRejectedDevice(context, device, state = "disconnected", operation = "disconnect")
                        }
                    }
                }
            }
        }, IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_REFRESH_STATUS)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_DISCONNECT_POD_REQUEST)
        }, Context.RECEIVER_EXPORTED)
        appRequestReceiverRegistered = true
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
        val name = device.name ?: device.alias
        return detectHuaweiDeviceRoute(name) == HuaweiDeviceRoute.HUAWEI_FREEBUDS3
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
}
