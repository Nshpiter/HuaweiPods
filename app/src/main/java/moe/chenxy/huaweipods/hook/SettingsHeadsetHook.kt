package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.HuaweiGestureAction
import moe.chenxy.huaweipods.pods.HuaweiGestureController
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.detectHuaweiDeviceRoute
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.WeakHashMap

@SuppressLint("MissingPermission")
object SettingsHeadsetHook : HookContext() {
    private const val TAG = "HuaweiPods-Settings"
    private const val PREFS_NAME = "huaweipods_milink_state"
    private const val SETTINGS_REFRESH_INTERVAL_MS = 3_000L
    private const val SETTINGS_FREEBUDS_ANC_OPTIONS = "0100"
    private const val SETTINGS_FREEBUDS_SUPPORT_FLAGS = "000000000000000010000000"
    private const val HUAWEI_ANC_LEVEL_LAST = 8
    private const val HUAWEI_ANC_DIAL_TICKS = 72
    private const val HUAWEI_ANC_TICKS_PER_LEVEL = 8
    private const val HUAWEI_ANC_DIAL_TICK_DEGREES = 5f
    private const val HUAWEI_ANC_DIAL_START_DEGREES = 70f
    private const val SETTINGS_HUAWEI_DIAL_TAG = "huaweipods_settings_anc_level_dial"
    private val transparencyKeywords = listOf("通透", "透明", "Transparency", "Transparent")
    private val earFitKeywords = listOf("耳塞贴合度检测", "贴合度检测", "耳塞贴合", "耳机贴合", "贴合度", "Ear tip fit", "Fit test", "Fit detection")
    private val notificationEntryKeywords = listOf(
        "通知栏显示",
        "耳机连接后在通知栏显示状态信息",
        "Notification display",
        "Show notification",
        "Show in notification",
        "Show in notification shade",
        "Display status information in the notification"
    )
    private val xiaomiOnlyEntryKeywords = listOf(
        "更多设置",
        "更多设定",
        "高级设置",
        "检查更新",
        "固件更新",
        "连接耳机获取版本号",
        "获取版本号",
        "耳机版本",
        "软件版本",
        "版本号",
        "查找耳机",
        "寻找耳机",
        "小爱同学",
        "More settings",
        "Additional settings",
        "Advanced settings",
        "Check for updates",
        "Firmware update",
        "Update firmware",
        "Firmware version",
        "Software version",
        "Find earphones",
        "Find earbuds",
        "Xiao Ai"
    )
    private val ancLevelKeywords = listOf("自适应", "智能", "轻度", "均衡", "深度", "Smart", "Adaptive", "Light", "Medium", "Deep")
    private val ancLevelAnchorKeywords = listOf("轻度", "均衡", "深度", "Light", "Medium", "Deep")
    private val ancModeKeywords = listOf("降噪", "关闭", "Noise", "Off")
    private val knownHuaweiAddresses = linkedSetOf<String>()
    private val batteryViews = WeakHashMap<Any, BluetoothDevice>()
    private val headsetFragments = WeakHashMap<Any, Boolean>()
    private val pruneRoots = WeakHashMap<View, Boolean>()
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentName: String? = null
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1
    private var currentHuaweiAncLevel = 0
    private var proxyCheckSupportCalls = 0
    private var proxySetCommonCommandCalls = 0
    private var proxyGetDeviceConfigCalls = 0
    private var proxyGetCommonConfigCalls = 0
    private var settingsHeaderBitmap: Bitmap? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshLoopStarted = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (headsetFragments.keys.any { isHuaweiFragment(it) }) {
                requestBluetoothStatus("settings-periodic")
                refreshHandler.postDelayed(this, SETTINGS_REFRESH_INTERVAL_MS)
            } else {
                refreshLoopStarted = false
                Log.d(TAG, "settings periodic refresh stopped: no active fragment")
            }
        }
    }

    override fun onHook() {
        hookActivityEntry()
        hookSupportChecks()
        hookServiceProxy()
        hookBatteryView()
        hookFragmentState()
    }

    private fun hookActivityEntry() {
        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetActivity", "onCreate", Bundle::class.java)) {
                val activity = instance as? Context ?: return@hookBefore
                registerStatusReceiver(activity)
                val intent = callMethod(instance, "getIntent") as? Intent ?: return@hookBefore
                val device = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
                Log.d(TAG, "Activity.onCreate before device=${device.describe()} support=${intent.getStringExtra("MIUI_HEADSET_SUPPORT")} comeFrom=${intent.getStringExtra("COME_FROM")} btAddress=${intent.getStringExtra("bluetoothaddress")} known=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuaweiPod(device)) return@hookBefore
                intent.putExtra("MIUI_HEADSET_SUPPORT", settingsSupport())
                intent.putExtra("COME_FROM", intent.getStringExtra("COME_FROM") ?: "MIUI_BLUETOOTH_SETTINGS")
                intent.putExtra("DEVICE_ID", fakeDeviceId())
                Log.d(TAG, "MiuiHeadsetActivity intent patched address=${device?.address}")
            }
            hookActivityStringGetter("getDeviceID") { fakeDeviceId() }
            hookActivityStringGetter("getSupport") { settingsSupport() }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivity skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetActivityPlugin", "onCreate", Bundle::class.java)) {
                val activity = instance as? Context ?: return@hookBefore
                registerStatusReceiver(activity)
                val intent = callMethod(instance, "getIntent") as? Intent ?: return@hookBefore
                val device = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
                Log.d(TAG, "Plugin.onCreate before device=${device.describe()} support=${intent.getStringExtra("MIUI_HEADSET_SUPPORT")} comeFrom=${intent.getStringExtra("COME_FROM")} btAddress=${intent.getStringExtra("bluetoothaddress")} known=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuaweiPod(device)) return@hookBefore
                intent.putExtra("MIUI_HEADSET_SUPPORT", settingsSupport())
                intent.putExtra("DEVICE_ID", fakeDeviceId())
                Log.d(TAG, "MiuiHeadsetActivityPlugin intent patched address=${device?.address}")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivityPlugin skipped", it) }
    }

    private fun hookActivityStringGetter(methodName: String, value: () -> String) {
        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetActivity", methodName, 0)) {
                val device = runCatching { getObjectField(instance, "mDevice") as? BluetoothDevice }.getOrNull()
                Log.d(TAG, "Activity.$methodName old=$result device=${device.describe()} isHuawei=${isHuaweiPod(device)}")
                if (!isHuaweiPod(device)) return@hookAfter
                result = value()
                Log.d(TAG, "Activity.$methodName forced=$result")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetActivity.$methodName skipped", it) }
    }

    private fun hookSupportChecks() {
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "checkSupport") { support ->
            support.startsWith(fakeDeviceId()) || support.contains(fakeDeviceId())
        }
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "isTWS01Headset") { it == fakeDeviceId() }
        hookStringStaticResult("com.android.settings.bluetooth.HeadsetIDConstants", "isK77sHeadset") { false }
        hookBleMmaConnectByContext()
        hookBleMmaConnectByService()
    }

    private fun hookStringStaticResult(className: String, methodName: String, resultForValue: (String) -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val value = args[0] as? String ?: return@hookAfter
                Log.d(TAG, "$className.$methodName value=$value old=$result")
                val deviceId = fakeDeviceId()
                if (value != deviceId && !value.startsWith(deviceId)) return@hookAfter
                result = resultForValue(value)
                Log.d(TAG, "$className.$methodName forced value=$value result=$result")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookBleMmaConnectByContext() {
        runCatching {
            hookAfter(findMethod("com.android.settings.bluetooth.HeadsetIDConstants", "isBleMmaConnect", Context::class.java, BluetoothDevice::class.java, String::class.java)) {
                val device = args[1] as? BluetoothDevice
                val deviceId = args[2] as? String
                Log.d(TAG, "isBleMmaConnect(Context) old=$result device=${device.describe()} deviceId=$deviceId service=${runCatching { callMethod(args[0], "getService") }.getOrNull()}")
                if (deviceId == fakeDeviceId() || isHuaweiPod(device)) {
                    result = true
                    Log.d(TAG, "isBleMmaConnect(Context) forced true")
                }
            }
        }.onFailure { Log.w(TAG, "hook HeadsetIDConstants.isBleMmaConnect(Context) skipped", it) }
    }

    private fun hookBleMmaConnectByService() {
        runCatching {
            val serviceClass = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetService")
            hookAfter(findMethod("com.android.settings.bluetooth.HeadsetIDConstants", "isBleMmaConnect", serviceClass, BluetoothDevice::class.java, String::class.java)) {
                val device = args[1] as? BluetoothDevice
                val deviceId = args[2] as? String
                Log.d(TAG, "isBleMmaConnect(Service) old=$result service=${args[0]} device=${device.describe()} deviceId=$deviceId")
                if (deviceId == fakeDeviceId() || isHuaweiPod(device)) {
                    result = true
                    Log.d(TAG, "isBleMmaConnect(Service) forced true")
                }
            }
        }.onFailure { Log.w(TAG, "hook HeadsetIDConstants.isBleMmaConnect(Service) skipped", it) }
    }

    private fun hookServiceProxy() {
        val proxyClass = "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub\$Proxy"
        hookProxyStringResult(proxyClass, "checkSupport", BluetoothDevice::class.java) { settingsSupport() }
        hookProxyStringArgResult(proxyClass, "getDeviceInfo") { settingsSupport() }
        hookProxyStringArgResult(proxyClass, "isSupportAudioSwitch") { "1" }
        hookProxyStringArgResult(proxyClass, "setCommonCommand", Int::class.java, String::class.java, BluetoothDevice::class.java) { commandArgs ->
            val command = commandArgs[0] as? Int
            if (command == 102) "0" else "1"
        }
        hookProxyVoidDeviceNoop(proxyClass, "connect", BluetoothDevice::class.java)
        hookProxyVoidDeviceNoop(proxyClass, "getDeviceConfig", BluetoothDevice::class.java)
        hookProxyVoidDeviceStringNoop(proxyClass, "getCommonConfig", BluetoothDevice::class.java, String::class.java)
        hookProxyBooleanStringResult(proxyClass, "isMiTWS") { true }
        hookProxyBooleanStringResult(proxyClass, "checkIsMiTWS") { true }
        hookProxyBooleanStringResult(proxyClass, "getRingFindState") { false }
        hookProxyVoidDeviceCommand(proxyClass, "changeAncMode", Int::class.java, BluetoothDevice::class.java) { commandArgs ->
            val miMode = commandArgs[0] as? Int ?: return@hookProxyVoidDeviceCommand null
            huaweiAncFromSettings(miMode)
        }
        hookProxyVoidDeviceCommand(proxyClass, "changeAncLevel", String::class.java, BluetoothDevice::class.java) { commandArgs ->
            val level = commandArgs[0] as? String ?: return@hookProxyVoidDeviceCommand null
            huaweiAncFromLevelCommand(level)
        }
    }

    private fun hookProxyStringResult(className: String, methodName: String, vararg parameterTypes: Class<*>, result: () -> String) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                val isHuawei = isHuaweiPod(device)
                if (methodName == "checkSupport") proxyCheckSupportCalls++
                Log.d(TAG, "$methodName proxy call#${if (methodName == "checkSupport") proxyCheckSupportCalls else -1} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = result()
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=${device?.address}")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyStringArgResult(className: String, methodName: String, vararg parameterTypes: Class<*>, result: (List<Any?>) -> String) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                val address = args.firstOrNull { it is String } as? String
                val isHuawei = isHuaweiPod(device) || (address != null && isHuaweiAddress(address))
                if (methodName == "setCommonCommand") proxySetCommonCommandCalls++
                Log.d(TAG, "$methodName proxy call#${if (methodName == "setCommonCommand") proxySetCommonCommandCalls else -1} args=${args.describeArgs()} device=${device.describe()} addressArg=$address isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = result(args)
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=${device?.address ?: address}")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyBooleanStringResult(className: String, methodName: String, result: () -> Boolean) {
        runCatching {
            hookBefore(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookBefore
                val isHuawei = isHuaweiAddress(address)
                Log.d(TAG, "$methodName proxy string call address=$address isHuawei=$isHuawei oldKnown=$knownHuaweiAddresses current=$currentAddress")
                if (!isHuawei) return@hookBefore
                this.result = result()
                Log.d(TAG, "$methodName proxy forced result=${this.result} address=$address")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceCommand(className: String, methodName: String, vararg parameterTypes: Class<*>, mode: (List<Any?>) -> Int?) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                Log.d(TAG, "$methodName proxy command args=${args.describeArgs()} device=${device.describe()} isHuawei=${isHuaweiPod(device)}")
                if (!isHuaweiPod(device)) return@hookBefore
                val huaweiMode = mode(args) ?: return@hookBefore
                currentAnc = huaweiMode
                sendHuaweiAnc(huaweiMode)
                sendAncChanged(huaweiMode)
                this.result = null
                Log.d(TAG, "$methodName proxy command handled address=${device?.address} huaweiMode=$huaweiMode")
            }
        }.onFailure { Log.w(TAG, "hook proxy $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceNoop(className: String, methodName: String, vararg parameterTypes: Class<*>) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                if (methodName == "getDeviceConfig") proxyGetDeviceConfigCalls++
                val isHuawei = isHuaweiPod(device)
                Log.d(TAG, "$methodName proxy before#${if (methodName == "getDeviceConfig") proxyGetDeviceConfigCalls else -1} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = null
                Log.d(TAG, "$methodName proxy swallowed for virtual Huawei device")
            }
        }.onFailure { Log.w(TAG, "hook proxy noop $methodName skipped", it) }
    }

    private fun hookProxyVoidDeviceStringNoop(className: String, methodName: String, vararg parameterTypes: Class<*>) {
        runCatching {
            hookBefore(findMethod(className, methodName, *parameterTypes)) {
                val device = args.firstOrNull { it is BluetoothDevice } as? BluetoothDevice
                proxyGetCommonConfigCalls++
                val isHuawei = isHuaweiPod(device)
                Log.d(TAG, "$methodName proxy before#$proxyGetCommonConfigCalls args=${args.describeArgs()} device=${device.describe()} isHuawei=$isHuawei")
                if (!isHuawei) return@hookBefore
                this.result = null
                Log.d(TAG, "$methodName proxy swallowed for virtual Huawei device")
            }
        }.onFailure { Log.w(TAG, "hook proxy noop $methodName skipped", it) }
    }

    private fun hookBatteryView() {
        runCatching {
            hookConstructorAfter(findConstructorByParamCount("com.android.settings.bluetooth.tws.MiuiHeadsetBattery", 4)) {
                val device = args[0] as? BluetoothDevice ?: return@hookConstructorAfter
                val ctx = args[1] as? Context
                registerStatusReceiver(ctx)
                Log.d(TAG, "Battery.<init> device=${device.describe()} isHuawei=${isHuaweiPod(device)} ctx=$ctx currentBattery=${settingsBatteryString()}")
                if (!isHuaweiPod(device)) return@hookConstructorAfter
                batteryViews[instance ?: return@hookConstructorAfter] = device
                requestBluetoothStatus("battery-init")
                updateBatteryView(instance)
                Log.d(TAG, "MiuiHeadsetBattery registered address=${device.address}")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetBattery constructor skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.tws.MiuiHeadsetBattery", "onBatteryChanged", String::class.java)) {
                val device = batteryViews[instance]
                Log.d(TAG, "Battery.onBatteryChanged(String) original=${args[0]} mappedDevice=${device.describe()} isHuawei=${isHuaweiPod(device)} forced=${settingsBatteryString()}")
                if (!isHuaweiPod(device)) return@hookBefore
                result = null
                updateBatteryView(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetBattery.onBatteryChanged(String) skipped", it) }
    }

    private fun hookFragmentState() {
        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onCreateView", 3)) {
                registerStatusReceiver(runCatching { getObjectField(instance, "mActivity") as? Context }.getOrNull())
                Log.d(TAG, "Fragment.onCreateView after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                instance?.let { headsetFragments[it] = true }
                schedulePruneFreeBudsUnsupportedViews(result as? View)
                requestBluetoothStatus("fragment-create")
                startPeriodicRefresh()
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onCreateView skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onResume", 0)) {
                Log.d(TAG, "Fragment.onResume after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                schedulePruneFreeBudsUnsupportedViews(fragmentRootView(instance))
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onResume skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetFragment", "onServiceConnected", 0)) {
                Log.d(TAG, "Fragment.onServiceConnected after ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                instance?.let { headsetFragments[it] = true }
                requestBluetoothStatus("service-connected")
                startPeriodicRefresh()
                injectFragmentStatus(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.onServiceConnected skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "refreshStatus", String::class.java, String::class.java)) {
                val key = args[0] as? String
                val data = args[1] as? String
                Log.d(TAG, "Fragment.refreshStatus before key=$key data=$data ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (isHuaweiFragment(instance) && key?.startsWith("MMA_CONNECTION_FAILED") == true) {
                    Log.w(TAG, "Fragment.refreshStatus swallowed MMA failure for virtual Huawei device key=$key")
                    injectFragmentStatus(instance)
                    result = null
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.refreshStatus skipped", it) }

        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "handleConnectMmaFailed", String::class.java)) {
                Log.w(TAG, "Fragment.handleConnectMmaFailed arg=${args[0]} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (isHuaweiFragment(instance)) {
                    injectFragmentStatus(instance)
                    result = null
                    Log.w(TAG, "Fragment.handleConnectMmaFailed swallowed for virtual Huawei device")
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.handleConnectMmaFailed skipped", it) }

        hookFragmentAncCommand("updateAncMode", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!) { commandArgs ->
            huaweiAncFromSettings(commandArgs[0] as? Int ?: 0)
        }
        hookFragmentAncCommand("updateAncLevel", String::class.java, Boolean::class.javaPrimitiveType!!) { commandArgs ->
            val level = commandArgs[0] as? String ?: ""
            huaweiAncFromLevelCommand(level)
        }
        hookFragmentAncUiRender()
        hookKeyConfigFragmentState()
    }

    private fun hookKeyConfigFragmentState() {
        hookNativeKeyConfigPreferenceChange()

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment", "onCreateView", 3)) {
                Log.d(TAG, "MiuiHeadsetKeyConfigFragment.onCreateView after isHuawei=${isHuaweiKeyConfigFragment(instance)}")
                if (!isHuaweiKeyConfigFragment(instance)) return@hookAfter
                configureFreeBudsNativeGesturePage(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onCreateView skipped", it) }

        runCatching {
            hookAfter(findMethodByParamCount("com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment", "onResume", 0)) {
                Log.d(TAG, "MiuiHeadsetKeyConfigFragment.onResume after isHuawei=${isHuaweiKeyConfigFragment(instance)}")
                if (!isHuaweiKeyConfigFragment(instance)) return@hookAfter
                configureFreeBudsNativeGesturePage(instance)
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onResume skipped", it) }
    }

    private fun hookNativeKeyConfigPreferenceChange() {
        runCatching {
            hookBefore(
                findMethod(
                    "com.android.settings.bluetooth.MiuiHeadsetKeyConfigFragment\$2",
                    "onPreferenceChange",
                    findClass("androidx.preference.Preference"),
                    Any::class.java
                )
            ) {
                if (handleNativeGesturePreferenceChange(instance, args.getOrNull(0), args.getOrNull(1))) {
                    result = true
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetKeyConfigFragment.onPreferenceChange skipped", it) }
    }

    private fun handleNativeGesturePreferenceChange(listener: Any?, preference: Any?, newValue: Any?): Boolean {
        val fragment = runCatching { getObjectField(listener, "this$0") }.getOrNull()
        if (!isHuaweiKeyConfigFragment(fragment)) return false
        val key = runCatching { callCompatibleMethod(preference, "getKey") as? String }.getOrNull()
        val side = when (key) {
            "left_double" -> HuaweiGestureSide.LEFT
            "right_double" -> HuaweiGestureSide.RIGHT
            else -> return false
        }
        val action = HuaweiGestureAction.fromExtra(newValue?.toString())
            ?: newValue?.toString()?.toIntOrNull()?.let { HuaweiGestureAction.fromProtocolValue(it) }
            ?: return false
        val targetAddress = gestureDeviceAddress(fragment).orEmpty().ifBlank { currentAddress.orEmpty() }
        val targetContext = runCatching { callCompatibleMethod(preference, "getContext") as? Context }.getOrNull()
            ?: context
            ?: return false

        writeGesturePreference(targetAddress, side, action)
        sendHuaweiGestureFromSettings(targetContext, targetAddress, side, action)
        Log.i(TAG, "native key config gesture handled side=${side.extraValue} action=${action.extraValue} address=$targetAddress")
        return true
    }

    private fun hookFragmentAncUiRender() {
        runCatching {
            hookAfter(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", "updateAncUi", String::class.java, Boolean::class.javaPrimitiveType!!)) {
                Log.d(TAG, "MiuiHeadsetFragment.updateAncUi after args=${args.describeArgs()} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookAfter
                fragmentRootView(instance)?.let { root ->
                    runCatching { pruneFreeBudsUnsupportedViews(root) }
                        .onFailure { Log.w(TAG, "Settings prune after updateAncUi failed", it) }
                }
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.updateAncUi skipped", it) }
    }

    private fun hookFragmentAncCommand(methodName: String, vararg parameterTypes: Class<*>, mode: (List<Any?>) -> Int?) {
        runCatching {
            hookBefore(findMethod("com.android.settings.bluetooth.MiuiHeadsetFragment", methodName, *parameterTypes)) {
                Log.d(TAG, "MiuiHeadsetFragment.$methodName before args=${args.describeArgs()} ${fragmentDebug(instance)} isHuawei=${isHuaweiFragment(instance)}")
                if (!isHuaweiFragment(instance)) return@hookBefore
                val updateDevice = args.getOrNull(1) as? Boolean ?: true
                if (!updateDevice) return@hookBefore
                val huaweiMode = mode(args) ?: return@hookBefore
                currentAnc = huaweiMode
                sendHuaweiAnc(huaweiMode)
                sendAncChanged(huaweiMode)
                runCatching { callMethod(instance, "updateAncUi", settingsAncLevel(), false) }
                injectFragmentStatus(instance)
                result = null
                Log.d(TAG, "MiuiHeadsetFragment.$methodName handled huaweiMode=$huaweiMode")
            }
        }.onFailure { Log.w(TAG, "hook MiuiHeadsetFragment.$methodName skipped", it) }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        loadState()
        val filter = IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentBattery = receivedIntent.batteryStatusFromExtras() ?: receivedIntent.parcelableStatus() ?: currentBattery
                        saveState(context)
                        updateBatteryViews()
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentAnc = receivedIntent.getIntExtra("status", currentAnc)
                        saveState(context)
                        updateFragments()
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentHuaweiAncLevel = receivedIntent.getIntExtra("level", currentHuaweiAncLevel).coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
                        saveState(context)
                        updateFragments()
                    }
                }
                Log.d(TAG, "state action=${receivedIntent.action} address=$currentAddress anc=$currentAnc battery=${settingsBatteryString()}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        requestBluetoothStatus("receiver-register")
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun requestBluetoothStatus(reason: String) {
        val ctx = context ?: return
        listOf(HuaweiPodsAction.ACTION_PODS_UI_INIT, HuaweiPodsAction.ACTION_REFRESH_STATUS).forEach { action ->
            ctx.sendBroadcast(Intent(action).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
        Log.d(TAG, "requested bluetooth status reason=$reason")
    }

    private fun startPeriodicRefresh() {
        if (refreshLoopStarted) return
        refreshLoopStarted = true
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, SETTINGS_REFRESH_INTERVAL_MS)
        Log.d(TAG, "settings periodic refresh started")
    }

    private fun updateBatteryViews() {
        batteryViews.keys.toList().forEach { view ->
            runCatching { updateBatteryView(view) }
                .onFailure { Log.w(TAG, "update battery view failed", it) }
        }
    }

    private fun updateBatteryView(view: Any?) {
        val values = settingsBatteryValues()
        callMethod(view, "onBatteryChanged", values[0], values[1], values[2])
        Log.d(TAG, "Battery.onBatteryChanged(int,int,int) forced=${values.joinToString(",")}")
    }

    private fun updateFragments() {
        headsetFragments.keys.toList().forEach { fragment ->
            if (isHuaweiFragment(fragment)) {
                injectFragmentStatus(fragment)
            }
        }
    }

    private fun injectFragmentStatus(fragment: Any?) {
        runCatching {
            val payload = "${settingsAncMode()}|$SETTINGS_FREEBUDS_ANC_OPTIONS|${settingsBatteryString()}|00"
            Log.d(TAG, "injectFragmentStatus payload=$payload ${fragmentDebug(fragment)}")
            callMethod(fragment, "updateAtUiInfo", payload)
            callMethod(fragment, "updateAncUi", settingsAncLevel(), false)
            schedulePruneFreeBudsUnsupportedViews(fragmentRootView(fragment))
            val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
            val address = device?.address
            if (address != null) {
                val refreshPayload = settingsRefreshPayload()
                Log.d(TAG, "injectFragmentStatus refreshPayload=$refreshPayload address=$address")
                callMethod(fragment, "refreshStatus", address, refreshPayload)
            }
            Log.d(TAG, "fragment status injected anc=$currentAnc battery=${settingsBatteryString()}")
        }.onFailure { Log.w(TAG, "inject fragment status failed", it) }
    }

    private fun isHuaweiFragment(fragment: Any?): Boolean {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val deviceId = runCatching { getObjectField(fragment, "mDeviceId") as? String }.getOrNull()
        val support = runCatching { getObjectField(fragment, "mSupport") as? String }.getOrNull()
        val fakeDeviceId = fakeDeviceId()
        return isHuaweiPod(device) || deviceId == fakeDeviceId || support?.startsWith(fakeDeviceId) == true
    }

    private fun isHuaweiKeyConfigFragment(fragment: Any?): Boolean {
        val args = runCatching { callMethod(fragment, "getArguments") as? Bundle }.getOrNull()
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val argDevice = args?.parcelableDevice("BT_Device")
        val deviceId = runCatching { getObjectField(fragment, "mDeviceId") as? String }.getOrNull()
        val support = runCatching { getObjectField(fragment, "mSupport") as? String }.getOrNull()
            ?: args?.getString("BT_Device_Support")
        val address = runCatching { device?.address }.getOrNull()
            ?: runCatching { argDevice?.address }.getOrNull()
            ?: currentAddress
        val fakeDeviceId = fakeDeviceId()
        return isHuaweiPod(device) ||
            isHuaweiPod(argDevice) ||
            deviceId == fakeDeviceId ||
            support?.startsWith(fakeDeviceId) == true ||
            knownHuaweiAddresses.any { known -> address != null && known.equals(address, ignoreCase = true) } ||
            detectHuaweiDeviceRoute(currentName) == HuaweiDeviceRoute.HUAWEI_FREEBUDS3
    }

    private fun isHuaweiPod(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        if (address != null && isHuaweiAddress(address)) return true
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = detectHuaweiDeviceRoute(name) == HuaweiDeviceRoute.HUAWEI_FREEBUDS3
        if (result && address != null) {
            knownHuaweiAddresses.add(address.uppercase())
            currentAddress = address
            currentName = name
        }
        return result
    }

    private fun BluetoothDevice?.describe(): String {
        if (this == null) return "null"
        val address = runCatching { this.address }.getOrNull()
        val name = runCatching { this.name }.getOrNull()
        val alias = runCatching { this.alias }.getOrNull()
        return "BluetoothDevice(address=$address,name=$name,alias=$alias)"
    }

    private fun List<Any?>.describeArgs(): String {
        return joinToString(prefix = "[", postfix = "]") { arg ->
            when (arg) {
                is BluetoothDevice -> arg.describe()
                else -> arg?.toString() ?: "null"
            }
        }
    }

    private fun fragmentDebug(fragment: Any?): String {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val deviceId = runCatching { getObjectField(fragment, "mDeviceId") as? String }.getOrNull()
        val support = runCatching { getObjectField(fragment, "mSupport") as? String }.getOrNull()
        val service = runCatching { getObjectField(fragment, "mService") }.getOrNull()
        val hfp = runCatching { getObjectField(fragment, "mBluetoothHfp") }.getOrNull()
        val cached = runCatching { getObjectField(fragment, "mCachedDevice") }.getOrNull()
        val supportAnc = runCatching { getObjectField(fragment, "mSupportAnc") }.getOrNull()
        val ancCached = runCatching { getObjectField(fragment, "mAncCached") }.getOrNull()
        val pendingAnc = runCatching { getObjectField(fragment, "mPendingAnc") }.getOrNull()
        val ancPendingStatus = runCatching { getObjectField(fragment, "mAncPendingStatus") }.getOrNull()
        return "fragment(device=${device.describe()},deviceId=$deviceId,support=$support,service=$service,hfp=$hfp,cached=$cached,supportAnc=$supportAnc,ancCached=$ancCached,pendingAnc=$pendingAnc,ancPending=$ancPendingStatus)"
    }

    private fun isHuaweiAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return normalized == currentAddress?.uppercase() || normalized in knownHuaweiAddresses
    }

    private fun rememberSupportedDevice(intent: Intent): Boolean {
        val name = intent.getStringExtra("device_name") ?: currentName
        if (detectHuaweiDeviceRoute(name) != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
            Log.w(TAG, "ignored unsupported persisted/broadcast device name=${name.orEmpty()}")
            return false
        }
        currentName = name
        currentAddress = intent.getStringExtra("address") ?: currentAddress
        currentAddress?.takeIf { it.isNotBlank() }?.let { knownHuaweiAddresses.add(it.uppercase()) }
        return true
    }

    private fun settingsBatteryString(): String {
        return settingsBatteryValues().joinToString(",")
    }

    private fun settingsBatteryValues(): List<Int> {
        loadState()
        return listOf(
            batteryValue(currentBattery.left),
            batteryValue(currentBattery.right),
            batteryValue(currentBattery.case)
        )
    }

    private fun batteryValue(params: PodParams?): Int {
        if (params?.isConnected != true) return 255
        val value = params.battery.coerceIn(0, 100)
        return if (params.isCharging) value or 128 else value
    }

    private fun settingsAncMode(): String {
        loadState()
        return if (currentAnc == 2) "1" else "0"
    }

    private fun settingsAncLevel(): String {
        loadState()
        return if (currentAnc == 2) "0100" else "0000"
    }

    private fun settingsRefreshPayload(): String {
        val battery = settingsBatteryString().split(",")
        val left = battery.getOrNull(0).orEmpty()
        val right = battery.getOrNull(1).orEmpty()
        val box = battery.getOrNull(2).orEmpty()
        val values = MutableList(16) { "" }
        values[0] = left
        values[1] = right
        values[2] = box
        values[7] = settingsAncLevel()
        values[8] = "false"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun huaweiAncFromSettings(mode: Int): Int {
        return when (mode) {
            1 -> 2
            2 -> 1
            else -> 1
        }
    }

    private fun huaweiAncFromLevel(level: String): Int {
        return if (level.startsWith("01")) 2 else 1
    }

    private fun huaweiAncFromLevelCommand(level: String): Int? {
        return huaweiAncFromLevel(level)
    }

    private fun settingsSupport(): String = "${fakeDeviceId()},$SETTINGS_FREEBUDS_SUPPORT_FLAGS"

    private fun fragmentRootView(fragment: Any?): View? {
        return runCatching { callMethod(fragment, "getView") as? View }.getOrNull()
            ?: runCatching { getObjectField(fragment, "mRootView") as? View }.getOrNull()
            ?: runCatching { getObjectField(fragment, "mView") as? View }.getOrNull()
    }

    private fun configureFreeBudsNativeGesturePage(fragment: Any?) {
        val address = gestureDeviceAddress(fragment).orEmpty()
        listOf(
            "left_triple",
            "right_triple",
            "long_press_left_headset",
            "long_press_right_headset"
        ).forEach { key ->
            hideNativePreference(fragment, key)
        }
        configureFreeBudsDoubleTapPreference(fragment, "mDoubleClickLeft", "left_double", address, HuaweiGestureSide.LEFT)
        configureFreeBudsDoubleTapPreference(fragment, "mDoubleClickRight", "right_double", address, HuaweiGestureSide.RIGHT)
        Log.d(TAG, "FreeBuds native gesture page configured address=$address")
    }

    private fun configureFreeBudsDoubleTapPreference(
        fragment: Any?,
        fieldName: String,
        key: String,
        address: String,
        side: HuaweiGestureSide
    ) {
        val preference = runCatching { getObjectField(fragment, fieldName) }.getOrNull()
            ?: nativePreference(fragment, key)
            ?: return
        val context = (runCatching { callCompatibleMethod(preference, "getContext") as? Context }.getOrNull())
            ?: context
            ?: return
        val actions = HuaweiGestureAction.all
        val entries = actions.map { gestureActionLabel(context, it) }.toTypedArray()
        val values = actions.map { it.extraValue }.toTypedArray()
        val selected = readGesturePreference(address, side)
        runCatching {
            callCompatibleMethod(preference, "setEntries", entries)
            callCompatibleMethod(preference, "setEntryValues", values)
            callCompatibleMethod(preference, "setValue", selected.extraValue)
            callCompatibleMethod(preference, "setVisible", true)
            callCompatibleMethod(preference, "setEnabled", true)
        }.onFailure {
            Log.w(TAG, "configure native gesture preference failed key=$key side=${side.extraValue}", it)
        }
    }

    private fun hideNativePreference(fragment: Any?, key: String) {
        val preference = nativePreference(fragment, key) ?: return
        runCatching { callCompatibleMethod(preference, "setVisible", false) }
            .recoverCatching { callCompatibleMethod(preference, "setEnabled", false) }
            .onFailure { Log.w(TAG, "hide native gesture preference failed key=$key", it) }
    }

    private fun nativePreference(fragment: Any?, key: String): Any? {
        return runCatching { callCompatibleMethod(fragment, "findPreference", key) }.getOrNull()
            ?: runCatching { callCompatibleMethod(fragment, "findPreference", key as CharSequence) }.getOrNull()
    }

    private fun callCompatibleMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.size == args.size &&
                    method.parameterTypes.zip(args).all { (type, arg) -> isCompatibleArgument(type, arg) }
            }?.let { method ->
                method.isAccessible = true
                return method.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException("${instance.javaClass.name}.$methodName/${args.size}")
    }

    private fun isCompatibleArgument(type: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !type.isPrimitive
        val target = when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Void.TYPE -> java.lang.Void::class.java
            else -> type
        }
        return target.isInstance(arg)
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun readGesturePreference(address: String, side: HuaweiGestureSide): HuaweiGestureAction {
        val defaultAction = defaultGestureAction(side)
        val value = prefs.getInt(gesturePrefKey(address, side), defaultAction.protocolValue)
        return HuaweiGestureAction.fromProtocolValue(value) ?: defaultAction
    }

    private fun writeGesturePreference(address: String, side: HuaweiGestureSide, action: HuaweiGestureAction) {
        prefs.edit()
            .putInt(gesturePrefKey(address, side), action.protocolValue)
            .apply()
    }

    private fun defaultGestureAction(side: HuaweiGestureSide): HuaweiGestureAction = when (side) {
        HuaweiGestureSide.LEFT -> HuaweiGestureAction.NOISE_CANCELLATION
        HuaweiGestureSide.RIGHT -> HuaweiGestureAction.PLAY_PAUSE
    }

    private fun gesturePrefKey(address: String, side: HuaweiGestureSide): String {
        val normalizedAddress = address.ifBlank { currentAddress ?: "unknown" }.uppercase()
        return "huawei_gesture_${normalizedAddress}_${side.extraValue}"
    }

    private fun gestureActionLabel(context: Context, action: HuaweiGestureAction): String {
        val fallback = when (action) {
            HuaweiGestureAction.PLAY_NEXT -> "播放/下一首"
            HuaweiGestureAction.PLAY_PAUSE -> "播放/暂停"
            HuaweiGestureAction.VOICE_ASSISTANT -> "唤醒语音助手"
            HuaweiGestureAction.NOISE_CANCELLATION -> "开启/关闭主动降噪"
            HuaweiGestureAction.NONE -> "无"
        }
        val resId = when (action) {
            HuaweiGestureAction.PLAY_NEXT -> R.string.gesture_action_play_next
            HuaweiGestureAction.PLAY_PAUSE -> R.string.gesture_action_play_pause
            HuaweiGestureAction.VOICE_ASSISTANT -> R.string.gesture_action_voice_assistant
            HuaweiGestureAction.NOISE_CANCELLATION -> R.string.gesture_action_noise_cancellation
            HuaweiGestureAction.NONE -> R.string.gesture_action_none
        }
        return moduleString(context, resId, fallback)
    }

    private fun moduleString(context: Context, resId: Int, fallback: String): String {
        return runCatching {
            val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            moduleContext.getString(resId)
        }.getOrElse { fallback }
    }

    private fun gestureDeviceAddress(fragment: Any?): String? {
        val device = runCatching { getObjectField(fragment, "mDevice") as? BluetoothDevice }.getOrNull()
        val argDevice = runCatching {
            (callMethod(fragment, "getArguments") as? Bundle)?.parcelableDevice("BT_Device")
        }.getOrNull()
        return runCatching { device?.address }.getOrNull()
            ?: runCatching { argDevice?.address }.getOrNull()
            ?: currentAddress
    }

    private fun sendHuaweiGestureFromSettings(
        context: Context,
        address: String,
        side: HuaweiGestureSide,
        action: HuaweiGestureAction
    ) {
        val targetAddress = address.ifBlank { currentAddress.orEmpty() }
        context.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET).apply {
            putExtra(HuaweiGestureController.EXTRA_ADDRESS, targetAddress)
            putExtra(HuaweiGestureController.EXTRA_SIDE, side.extraValue)
            putExtra(HuaweiGestureController.EXTRA_GESTURE_ACTION, action.extraValue)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.i(TAG, "Settings gesture requested address=$targetAddress side=${side.extraValue} action=${action.extraValue}")
    }

    private fun schedulePruneFreeBudsUnsupportedViews(root: View?) {
        if (root == null) return
        installPruneOnScroll(root)
        listOf(0L, 120L, 360L).forEach { delay ->
            root.postDelayed({
                runCatching { pruneFreeBudsUnsupportedViews(root) }
                    .onFailure { Log.w(TAG, "Settings unsupported row prune failed", it) }
            }, delay)
        }
    }

    private fun installPruneOnScroll(root: View) {
        if (pruneRoots[root] == true) return
        pruneRoots[root] = true
        installPruneOnScroll(root, root)
    }

    private fun installPruneOnScroll(root: View, view: View) {
        if (view.isSystemScrollingContainer()) {
            view.setOnScrollChangeListener { _, _, _, _, _ ->
                root.postDelayed({
                    runCatching { pruneFreeBudsUnsupportedViews(root) }
                        .onFailure { Log.w(TAG, "Settings scroll prune failed", it) }
                }, 80L)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                installPruneOnScroll(root, view.getChildAt(index))
            }
        }
    }

    private fun pruneFreeBudsUnsupportedViews(root: View) {
        replaceSettingsHeaderImage(root)
        hideRowsByText(root, transparencyKeywords)
        hideRowsByText(root, earFitKeywords)
        hideRowsByText(root, notificationEntryKeywords)
        hideRowsByText(root, xiaomiOnlyEntryKeywords)
        replaceHuaweiAncLevelsWithHuaweiDial(root)
    }

    private fun replaceSettingsHeaderImage(root: View) {
        val bitmap = loadSettingsHeaderBitmap(root.context) ?: return
        val candidates = mutableListOf<ImageView>()
        collectHeadsetImageCandidates(root, candidates)
        candidates.forEach { imageView ->
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true
            Log.d(TAG, "Settings headset image replaced view=${imageView.javaClass.name} size=${imageView.width}x${imageView.height}")
        }
        if (candidates.isEmpty()) {
            Log.d(TAG, "Settings headset image candidate not found")
        }
    }

    private fun loadSettingsHeaderBitmap(context: Context): Bitmap? {
        settingsHeaderBitmap?.takeIf { !it.isRecycled }?.let { return it }
        return runCatching {
            val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            BitmapFactory.decodeResource(moduleContext.resources, R.drawable.img_box)
        }.onSuccess { bitmap ->
            settingsHeaderBitmap = bitmap
            Log.d(TAG, "Settings headset image loaded bitmap=${bitmap?.width}x${bitmap?.height}")
        }.onFailure {
            Log.w(TAG, "Settings headset image load failed", it)
        }.getOrNull()
    }

    private fun collectHeadsetImageCandidates(view: View, out: MutableList<ImageView>) {
        if (view is ImageView && view.isLargeSettingsImage()) {
            out.add(view)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectHeadsetImageCandidates(view.getChildAt(index), out)
            }
        }
    }

    private fun ImageView.isLargeSettingsImage(): Boolean {
        if (!isShown) return false
        val minSize = context.dp(88)
        val measuredLarge = width >= minSize && height >= minSize
        val params = layoutParams
        val declaredLarge = params != null && (
            (params.width >= minSize && params.height >= minSize) ||
                (params.width == ViewGroup.LayoutParams.MATCH_PARENT && params.height >= minSize)
            )
        return measuredLarge || declaredLarge
    }

    private fun hideRowsByText(root: View, keywords: List<String>) {
        val matches = mutableListOf<TextView>()
        collectTextMatches(root, keywords, matches)
        matches.forEach { textView ->
            val target = bestHideTarget(root, textView)
            collapseView(target)
            Log.d(TAG, "Settings unsupported row hidden text=${textView.text} target=${target.javaClass.name}")
        }
    }

    private fun replaceHuaweiAncLevelsWithHuaweiDial(root: View) {
        loadState()
        val existingDial = findTaggedView(root, SETTINGS_HUAWEI_DIAL_TAG) as? HuaweiAncLevelDialView
        if (!currentAnc.isSettingsNoiseCancellation()) {
            existingDial?.visibility = View.GONE
            return
        }

        val matches = mutableListOf<TextView>()
        collectTextMatches(root, ancLevelKeywords, matches)

        val anchorMatches = matches.filter { textView ->
            val text = textView.text?.toString().orEmpty()
            ancLevelAnchorKeywords.any { text.contains(it, ignoreCase = true) }
        }
        val anchor = levelContainer(root, anchorMatches.ifEmpty { matches })
            ?: modeButtonContainer(root)
        if (anchor == null) {
            existingDial?.visibility = View.GONE
            Log.d(TAG, "Settings Huawei ANC dial anchor not found matches=${matches.map { it.text }}")
            return
        }

        hideHuaweiAncLevelArea(root, anchor)

        val dial = existingDial ?: createHuaweiAncLevelDial(anchor)
        if (dial != null) {
            dial.setLevel(currentHuaweiAncLevel)
            dial.visibility = View.VISIBLE
        }
    }

    private fun createHuaweiAncLevelDial(anchor: View): HuaweiAncLevelDialView? {
        val parent = anchor.parent as? ViewGroup ?: return null
        if (parent.hasHuaweiAncDialChild()) {
            return parent.findHuaweiAncDialChild()
        }
        val dial = HuaweiAncLevelDialView(anchor.context) { level ->
            currentHuaweiAncLevel = level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
            saveState(anchor.context)
            sendHuaweiAncLevel(currentHuaweiAncLevel)
        }.apply {
            tag = SETTINGS_HUAWEI_DIAL_TAG
            setLevel(currentHuaweiAncLevel)
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            anchor.context.dp(220)
        )
        runCatching {
            val index = parent.indexOfChild(anchor).takeIf { it >= 0 } ?: parent.childCount
            parent.addView(dial, index + 1, params)
            Log.d(TAG, "Settings Huawei ANC dial added parent=${parent.javaClass.name} anchor=${anchor.javaClass.name}")
            return dial
        }.onFailure { Log.w(TAG, "Settings Huawei ANC dial add failed", it) }
        return null
    }

    private fun hideHuaweiAncLevelArea(root: View, anchor: View) {
        val parent = anchor.parent as? ViewGroup
        if (parent == null || parent === root || parent.isSystemScrollingContainer()) {
            hideViewOnly(anchor)
            return
        }

        val parentHasModeButtons = parent.containsAnyText(ancModeKeywords)
        if (!parentHasModeButtons) {
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                if (child.tag != SETTINGS_HUAWEI_DIAL_TAG) {
                    hideViewOnly(child)
                }
            }
            Log.d(TAG, "Settings MIUI ANC level area hidden parent=${parent.javaClass.name} children=${parent.childCount}")
            return
        }

        val anchorIndex = parent.indexOfChild(anchor)
        hideViewOnly(anchor)
        listOf(anchorIndex - 1, anchorIndex + 1).forEach { index ->
            val sibling = parent.getChildAtOrNull(index) ?: return@forEach
            if (sibling.tag == SETTINGS_HUAWEI_DIAL_TAG) return@forEach
            if (sibling.containsAnyText(ancModeKeywords)) return@forEach
            hideViewOnly(sibling)
        }
        Log.d(TAG, "Settings MIUI ANC level nearby views hidden parent=${parent.javaClass.name} anchorIndex=$anchorIndex")
    }

    private fun hideViewOnly(view: View) {
        collapseView(view)
    }

    private fun collapseView(view: View) {
        view.visibility = View.GONE
        view.isEnabled = false
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.minimumHeight = 0
        view.setPadding(0, 0, 0, 0)
        view.layoutParams = view.layoutParams?.apply {
            height = 0
            if (this is ViewGroup.MarginLayoutParams) {
                setMargins(0, 0, 0, 0)
            }
        }
        view.requestLayout()
    }

    private fun ViewGroup.getChildAtOrNull(index: Int): View? {
        return if (index in 0 until childCount) getChildAt(index) else null
    }

    private fun ViewGroup.hasHuaweiAncDialChild(): Boolean = findHuaweiAncDialChild() != null

    private fun ViewGroup.findHuaweiAncDialChild(): HuaweiAncLevelDialView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == SETTINGS_HUAWEI_DIAL_TAG && child is HuaweiAncLevelDialView) {
                return child
            }
        }
        return null
    }

    private fun levelContainer(root: View, matches: List<TextView>): View? {
        if (matches.isEmpty()) return null
        val common = commonAncestor(root, matches)
        if (common != null && common !== root && !common.isSystemScrollingContainer()) {
            return common
        }
        return bestHideTarget(root, matches.first()).takeIf { it !== root && !it.isSystemScrollingContainer() }
    }

    private fun modeButtonContainer(root: View): View? {
        val matches = mutableListOf<TextView>()
        collectTextMatches(root, ancModeKeywords, matches)
        val common = commonAncestor(root, matches)
        if (common != null && common !== root && !common.isSystemScrollingContainer()) {
            return common
        }
        return matches.firstOrNull()?.let { bestHideTarget(root, it) }
            ?.takeIf { it !== root && !it.isSystemScrollingContainer() }
    }

    private fun commonAncestor(root: View, views: List<View>): View? {
        if (views.isEmpty()) return null
        val firstChain = ancestorChain(root, views.first())
        val otherChains = views.drop(1).map { ancestorChain(root, it).toSet() }
        return firstChain.firstOrNull { candidate ->
            candidate !== root && otherChains.all { candidate in it }
        }
    }

    private fun ancestorChain(root: View, view: View): List<View> {
        val result = mutableListOf<View>()
        var current: View? = view
        while (current != null) {
            result.add(current)
            if (current === root) break
            current = current.parent as? View
        }
        return result
    }

    private fun View.isSystemScrollingContainer(): Boolean {
        val className = javaClass.name
        return className.contains("RecyclerView") ||
            className.contains("ScrollView") ||
            className.contains("ListView")
    }

    private fun View.containsAnyText(keywords: List<String>): Boolean {
        if (this is TextView) {
            val text = this.text?.toString().orEmpty()
            val contentDescription = this.contentDescription?.toString().orEmpty()
            if (keywords.any { text.contains(it, ignoreCase = true) || contentDescription.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                if (getChildAt(index).containsAnyText(keywords)) return true
            }
        }
        return false
    }

    private fun findTaggedView(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findTaggedView(view.getChildAt(index), tag)
                if (found != null) return found
            }
        }
        return null
    }

    private fun collectTextMatches(view: View, keywords: List<String>, out: MutableList<TextView>) {
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            val contentDescription = view.contentDescription?.toString().orEmpty()
            if (keywords.any { text.contains(it, ignoreCase = true) || contentDescription.contains(it, ignoreCase = true) }) {
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextMatches(view.getChildAt(index), keywords, out)
            }
        }
    }

    private fun Int.isSettingsNoiseCancellation(): Boolean = this == 2 || this in 5..8

    private fun bestHideTarget(root: View, textView: TextView): View {
        var target: View = textView
        var parent = textView.parent as? ViewGroup
        var depth = 0
        while (parent != null && parent !== root && depth < 4) {
            val parentClass = parent.javaClass.name
            if (
                parentClass.contains("RecyclerView") ||
                parentClass.contains("ScrollView") ||
                parentClass.contains("ListView")
            ) {
                break
            }
            val grandParent = parent.parent as? ViewGroup
            if (parent.isClickable || parent.isFocusable || (grandParent != null && grandParent.childCount > 1)) {
                target = parent
                break
            }
            target = parent
            parent = grandParent
            depth++
        }
        return target
    }

    private fun sendHuaweiAncLevel(level: Int) {
        val ctx = context ?: run {
            Log.w(TAG, "sendHuaweiAncLevel skipped: context is null level=$level")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET).apply {
            putExtra("level", level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST))
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "Huawei ANC level requested from settings level=$level")
    }

    private class HuaweiAncLevelDialView(
        context: Context,
        private val onLevelChange: (Int) -> Unit
    ) : View(context) {
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = context.dp(1).toFloat()
            color = Color.argb(80, 36, 42, 54)
        }
        private val activeTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = context.dp(2).toFloat()
            color = Color.rgb(0, 122, 255)
        }
        private val diskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(12, 36, 42, 54)
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1.5f)
            color = Color.argb(32, 36, 42, 54)
        }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1f)
            color = Color.argb(46, 255, 255, 255)
        }
        private val knobHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(42, 0, 122, 255)
        }
        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(0, 122, 255)
        }
        private var level = 0

        init {
            isClickable = true
            minimumHeight = context.dp(200)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }

        fun setLevel(nextLevel: Int) {
            val safeLevel = nextLevel.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
            if (safeLevel == level) return
            level = safeLevel
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredHeight = context.dp(220)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
            val contentHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
            val centerX = paddingLeft + contentWidth / 2f
            val centerY = paddingTop + contentHeight / 2f
            val radius = min(contentWidth, contentHeight) * 0.32f
            val innerTickRadius = radius + context.dp(8)
            val outerTickRadius = radius + context.dp(19)
            val selectedTick = level.toDialTick()

            canvas.drawCircle(centerX, centerY, radius * 1.08f, diskPaint)
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
            canvas.drawCircle(centerX, centerY, radius * 0.72f, innerRingPaint)

            repeat(HUAWEI_ANC_DIAL_TICKS) { tick ->
                val major = tick % HUAWEI_ANC_TICKS_PER_LEVEL == 0
                val highlighted = circularDistance(tick, selectedTick, HUAWEI_ANC_DIAL_TICKS) <= 2
                val radians = Math.toRadians(tick * HUAWEI_ANC_DIAL_TICK_DEGREES.toDouble())
                val startRadius = if (major) innerTickRadius - context.dp(3) else innerTickRadius
                val startX = centerX + cos(radians).toFloat() * startRadius
                val startY = centerY + sin(radians).toFloat() * startRadius
                val endX = centerX + cos(radians).toFloat() * outerTickRadius
                val endY = centerY + sin(radians).toFloat() * outerTickRadius
                canvas.drawLine(startX, startY, endX, endY, if (highlighted) activeTickPaint else tickPaint)
            }

            val knobRadians = Math.toRadians(level.toDialDegrees().toDouble())
            val knobX = centerX + cos(knobRadians).toFloat() * radius * 0.86f
            val knobY = centerY + sin(knobRadians).toFloat() * radius * 0.86f
            val knobRadius = context.dp(15)
            canvas.drawCircle(knobX, knobY, knobRadius * 1.35f, knobHaloPaint)
            canvas.drawCircle(knobX, knobY, knobRadius.toFloat(), knobPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateLevelFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    updateLevelFromTouch(event.x, event.y)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun updateLevelFromTouch(x: Float, y: Float) {
            val nextLevel = touchToHuaweiAncLevel(x, y, width.toFloat(), height.toFloat())
            if (nextLevel == level) return
            level = nextLevel
            invalidate()
            onLevelChange(nextLevel)
        }
    }

    private fun touchToHuaweiAncLevel(x: Float, y: Float, width: Float, height: Float): Int {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val degrees = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        val normalized = (degrees - HUAWEI_ANC_DIAL_START_DEGREES + 360f) % 360f
        return ((normalized / (360f / (HUAWEI_ANC_LEVEL_LAST + 1))).roundToInt()) % (HUAWEI_ANC_LEVEL_LAST + 1)
    }

    private fun Int.toDialDegrees(): Float = HUAWEI_ANC_DIAL_START_DEGREES + (this * 360f / (HUAWEI_ANC_LEVEL_LAST + 1))

    private fun Int.toDialTick(): Int = ((toDialDegrees() / HUAWEI_ANC_DIAL_TICK_DEGREES).roundToInt()) % HUAWEI_ANC_DIAL_TICKS

    private fun circularDistance(a: Int, b: Int, modulo: Int): Int {
        val distance = abs(a - b)
        return min(distance, modulo - distance)
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sendHuaweiAnc(mode: Int) {
        val ctx = context ?: run {
            Log.w(TAG, "sendHuaweiAnc skipped: context is null mode=$mode")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", mode)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    private fun sendAncChanged(mode: Int) {
        val ctx = context ?: return
        listOf(BuildConfig.APPLICATION_ID, "com.android.settings", "com.milink.service").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED).apply {
                putExtra("status", mode)
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableDevice(key: String): BluetoothDevice? {
        return runCatching { getParcelableExtra(key, BluetoothDevice::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BluetoothDevice>(key) }.getOrNull()
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

    private fun saveState(ctx: Context?) {
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putInt("anc", currentAnc)
            .putInt("huawei_anc_level", currentHuaweiAncLevel)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
            .apply()
    }

    private fun loadState() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val hasPersistedIdentity = prefs.contains("address") || prefs.contains("name")
        val persistedName = prefs.getString("name", null)
        if (hasPersistedIdentity && detectHuaweiDeviceRoute(persistedName) != HuaweiDeviceRoute.HUAWEI_FREEBUDS3) {
            currentAddress = null
            currentName = null
            currentBattery = BatteryParams()
            currentAnc = 1
            currentHuaweiAncLevel = 0
            knownHuaweiAddresses.clear()
            prefs.edit()
                .remove("address")
                .remove("name")
                .remove("anc")
                .remove("huawei_anc_level")
                .remove("left_battery")
                .remove("left_charging")
                .remove("left_connected")
                .remove("right_battery")
                .remove("right_charging")
                .remove("right_connected")
                .remove("case_battery")
                .remove("case_charging")
                .remove("case_connected")
                .apply()
            Log.i(TAG, "removed unsupported legacy headset state name=${persistedName.orEmpty()}")
            return
        }
        val hasSavedBattery = prefs.getBoolean("left_connected", false) ||
            prefs.getBoolean("right_connected", false) ||
            prefs.getBoolean("case_connected", false)
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentAnc = prefs.getInt("anc", currentAnc)
        currentHuaweiAncLevel = prefs.getInt("huawei_anc_level", currentHuaweiAncLevel).coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
        currentAddress?.let { knownHuaweiAddresses.add(it.uppercase()) }
        if (!hasSavedBattery && hasCurrentBattery()) return
        currentBattery = BatteryParams(
            left = PodParams(
                prefs.getInt("left_battery", currentBattery.left?.battery ?: 0),
                prefs.getBoolean("left_charging", currentBattery.left?.isCharging == true),
                prefs.getBoolean("left_connected", currentBattery.left?.isConnected == true),
                0
            ),
            right = PodParams(
                prefs.getInt("right_battery", currentBattery.right?.battery ?: 0),
                prefs.getBoolean("right_charging", currentBattery.right?.isCharging == true),
                prefs.getBoolean("right_connected", currentBattery.right?.isConnected == true),
                0
            ),
            case = PodParams(
                prefs.getInt("case_battery", currentBattery.case?.battery ?: 0),
                prefs.getBoolean("case_charging", currentBattery.case?.isCharging == true),
                prefs.getBoolean("case_connected", currentBattery.case?.isConnected == true),
                0
            )
        )
    }

    private fun hasCurrentBattery(): Boolean {
        return currentBattery.left?.isConnected == true ||
            currentBattery.right?.isConnected == true ||
            currentBattery.case?.isConnected == true
    }

    private fun Bundle.parcelableDevice(key: String): BluetoothDevice? {
        return runCatching { getParcelable(key, BluetoothDevice::class.java) }.getOrNull()
            ?: runCatching { @Suppress("DEPRECATION") getParcelable<BluetoothDevice>(key) }.getOrNull()
    }
}
