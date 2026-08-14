package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xzakota.hyper.notification.focus.FocusNotification
import moe.chenxy.huaweipods.utils.FocusIslandUtil
import moe.chenxy.huaweipods.utils.ModuleResourceResolver
import moe.chenxy.huaweipods.utils.PodImageLoader
import moe.chenxy.huaweipods.utils.SystemApisUtils
import moe.chenxy.huaweipods.utils.SystemApisUtils.cancelAsUser
import moe.chenxy.huaweipods.utils.SystemApisUtils.notifyAsUser
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.NotificationPresentationPolicy
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R

internal fun shouldOfferNotificationAncAction(route: HuaweiDeviceRoute): Boolean = route.supportsAnc

@SuppressLint("MissingPermission")
object MiBluetoothToastHook : HookContext() {

    // ANC 模式本地缓存，用于在 FreeBuds 3 已验证的关/开状态之间切换。
    private val receiverRegistrationLock = Any()
    @Volatile
    private var receiverRegistered = false

    override fun onHook() {

        fun deleteIntent(context: Context, bluetoothDevice: BluetoothDevice): PendingIntent? {
            val intent = Intent("com.android.bluetooth.headset.notification.cancle")
            intent.putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        @SuppressLint("WrongConstant")
        fun createPodsNotification(bluetoothDevice: BluetoothDevice?, context: Context, batteryParams: BatteryParams) {
            val miheadset_notification_Box = context.resources.getIdentifier("miheadset_notification_Box", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_LeftEar = context.resources.getIdentifier("miheadset_notification_LeftEar", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_RightEar = context.resources.getIdentifier("miheadset_notification_RightEar", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_Disconnect = context.resources.getIdentifier("miheadset_notification_Disconnect", "string", "com.xiaomi.bluetooth")
            val system_notification_accent_color = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
            if (bluetoothDevice == null) {
                Log.e("HuaweiPods", "createPodsNotification: btDevice null")
                return
            }
            try {
                val address: String = bluetoothDevice.address
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                fun detailNotificationEnabled(): Boolean = context.getSharedPreferences(
                    "DeviceIdCached",
                    Context.MODE_MULTI_PROCESS,
                ).getBoolean("detail_notification$address", false)
                if (!detailNotificationEnabled()) {
                    notificationManager.cancelAsUser(
                        "BTHeadset$address",
                        10003,
                        SystemApisUtils.getUserAllUserHandle(),
                    )
                    return
                }
                var alias: String? = bluetoothDevice.alias
                if (alias?.isEmpty() == true) {
                    alias = bluetoothDevice.name
                }
                val deviceName = alias ?: bluetoothDevice.name.orEmpty()
                val moduleContext = ModuleResourceResolver.createModuleContext(context) ?: run {
                    Log.w("HuaweiPods", "skip notification: module context unavailable")
                    return
                }
                if (!ModuleResourceResolver.isCurrentModuleBuild(moduleContext)) {
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancelAsUser(
                            "BTHeadset$address",
                            10003,
                            SystemApisUtils.getUserAllUserHandle(),
                        )
                    Log.w("HuaweiPods", "skip notification: stale Hook build")
                    FocusIslandUtil.cancelBatteryIsland(context)
                    return
                }
                val deviceRoute = DeviceRoutePrefs.resolve(prefs, address, deviceName)
                val offerAncAction = shouldOfferNotificationAncAction(deviceRoute)
                val lockscreenVisibility = if (ConfigManager.lockscreenNotificationEnabled()) {
                    Notification.VISIBILITY_PUBLIC
                } else {
                    Notification.VISIBILITY_SECRET
                }
                val attachOfficialIsland = NotificationPresentationPolicy.attachesOfficialIsland(
                    ConfigManager.islandMode(),
                )

                val caseBattStr = if (batteryParams.case != null && batteryParams.case!!.isConnected)
                    "${context.resources.getString(miheadset_notification_Box)}${batteryParams.case!!.battery}%" +
                            "${if (batteryParams.case!!.isCharging) "⚡ " else " "}\n"
                else ""
                val leftEar = if (batteryParams.left != null && batteryParams.left!!.isConnected)
                    "${context.resources.getString(miheadset_notification_LeftEar)}${batteryParams.left!!.battery}%" +
                        (if (batteryParams.left!!.isCharging) "⚡" else "")
                else ""
                val leftToRight = if (batteryParams.left?.isConnected == true && batteryParams.right?.isConnected == true) " " else ""
                val rightEar = if (batteryParams.right != null && batteryParams.right!!.isConnected)
                    "$leftToRight${context.resources.getString(miheadset_notification_RightEar)}${batteryParams.right!!.battery}%" +
                        (if (batteryParams.right!!.isCharging) "⚡ " else " ")
                else ""

                val contentText: String = caseBattStr + leftEar + rightEar
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        "BTHeadset$address",
                        alias,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        setAllowBubbles(true)
                        setLockscreenVisibility(lockscreenVisibility)
                    }
                )
                val bundle = Bundle()
                bundle.putParcelable("Device", bluetoothDevice)
                val intent = Intent("com.android.bluetooth.headset.notification")
                intent.putExtra("btData", bundle)
                intent.putExtra("disconnect", "1")
                intent.setIdentifier("BTHeadset$address")
                val disconnectAction = Notification.Action(
                    285737079,
                    context.resources.getString(miheadset_notification_Disconnect),
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                val ancLabel = moduleContext.getString(R.string.cycle_anc)
                val ancAction = if (offerAncAction) {
                    val ancCycleIntent = Intent(HuaweiPodsAction.ACTION_CYCLE_ANC).apply {
                        setPackage("com.android.bluetooth")
                        setIdentifier("BTHeadset$address")
                        putExtra("address", address)
                        putExtra("device_name", deviceName)
                        encodeHuaweiDeviceRouteForBroadcast(deviceRoute)?.let {
                            putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                        }
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }
                    val ancCyclePendingIntent = PendingIntent.getBroadcast(
                        context,
                        1,
                        ancCycleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode),
                        ancLabel,
                        ancCyclePendingIntent,
                    ).build()
                } else {
                    null
                }
                val headsetBitmap = PodImageLoader.loadBoxBitmap(context, prefs, address)
                    ?: BitmapFactory.decodeResource(moduleContext.resources, R.drawable.img_box)
                if (headsetBitmap == null) {
                    Log.e("HuaweiPods", "createPodsNotification: headset bitmap null")
                    return
                }
                val headsetIcon = Icon.createWithBitmap(headsetBitmap)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(HuaweiPodsAction.ACTION_SHOW_PODS_UI).apply {
                        setClassName(BuildConfig.APPLICATION_ID, "moe.chenxy.huaweipods.PopupActivity")
                        putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
                        putExtra("bluetoothaddress", bluetoothDevice.address)
                        putExtra("device_name", alias)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val focusExtras = FocusNotification.buildV3 {
                    val logo = createPicture("key_headset", headsetIcon)
                    enableFloat = attachOfficialIsland
                    ticker = alias ?: ""
                    updatable = true
//                    tickerPic = logo

                    iconTextInfo {
                        animIconInfo{
                            type = 0
                            src = logo
                        }
                        title = alias ?: ""
                        content = contentText
                    }

                    if (attachOfficialIsland) {
                        island {
                            islandProperty = 1
                            bigIslandArea {
                                imageTextInfoLeft {
                                    type = 1
                                    picInfo {
                                        type = 1
                                        pic = logo
                                    }
                                }
                                imageTextInfoRight {
                                    type = 2
                                    textInfo {
                                        title = alias ?: ""
                                        content = contentText
                                    }
                                }
                            }
                        }
                    }


                    textButton {
                        ancAction?.let { notificationAction: Notification.Action ->
                            addActionInfo {
                                action = createAction("key_anc_cycle", notificationAction)
                                actionTitle = ancLabel
                            }
                        }
                        addActionInfo {
                            val disconnectLabel = moduleContext.getString(R.string.notification_btn_disconnect)
                            action = createAction("key_disconnect", disconnectAction)
                            actionTitle = disconnectLabel
                        }
                    }
                }
                if (attachOfficialIsland && ConfigManager.lockscreenNotificationEnabled()) {
                    // AOD 息屏显示：左右耳电量拼合后注入 aodTitle。
                    val aodParts = mutableListOf<String>()
                    if (batteryParams.left?.isConnected == true)
                        aodParts.add("L ${batteryParams.left!!.battery}%")
                    if (batteryParams.right?.isConnected == true)
                        aodParts.add("R ${batteryParams.right!!.battery}%")
                    val aodTitle = aodParts.joinToString(" | ")
                    try {
                        val json = org.json.JSONObject(focusExtras.getString("miui.focus.param") ?: "{}")
                        val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                        pv2.put("aodTitle", aodTitle)
                        pv2.put("aodPic", "key_headset")
                        json.put("param_v2", pv2)
                        focusExtras.putString("miui.focus.param", json.toString())
                    } catch (_: Exception) {}
                }
                val notification = Notification.Builder(context, "BTHeadset$address")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setWhen(0L)
                    .setTicker(alias)
                    .setDefaults(-1)
                    .setContentTitle(alias)
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(deleteIntent(context, bluetoothDevice))
                    .setColor(context.getColor(system_notification_accent_color))
                    .apply { ancAction?.let { addAction(it) } }
                    .addAction(disconnectAction)
                    .addExtras(focusExtras)
                    .setVisibility(lockscreenVisibility)
                    .build()
                notificationManager.notifyAsUser(
                    "BTHeadset$address",
                    10003,
                    notification,
                    SystemApisUtils.getUserAllUserHandle()
                )
                if (!detailNotificationEnabled()) {
                    notificationManager.cancelAsUser(
                        "BTHeadset$address",
                        10003,
                        SystemApisUtils.getUserAllUserHandle(),
                    )
                }
            } catch (e: Exception) {
                Log.e("HuaweiPods", "Failed to create Pod Notification", e)
            }
        }

        fun cancelNotification(bluetoothDevice: BluetoothDevice, context: Context) {
            try {
                val address = bluetoothDevice.address
                if (address.isNotEmpty()) {
                    val notificationManager = context.getSystemService("notification") as NotificationManager
                    notificationManager.cancelAsUser("BTHeadset$address", 10003, SystemApisUtils.getUserAllUserHandle())
                }
            } catch (e: Exception) {
                Log.e("HuaweiPods", "Failed to cancel Pod Notification!", e)
            }
        }


        hookConstructorAfter(findConstructorByParamCount("com.android.bluetooth.ble.app.MiuiBluetoothNotification", 2)) {
            val context = (getObjectField(instance, "mContext") as? Context)?.applicationContext
                ?: return@hookConstructorAfter
            synchronized(receiverRegistrationLock) {
                if (receiverRegistered) return@synchronized

                val broadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, receivedIntent: Intent?) {
                        runCatching {
                            val intent = receivedIntent ?: return@runCatching
                            when (HuaweiPodsAction.canonical(intent.action)) {
                                HuaweiPodsAction.ACTION_PODS_UI_INIT -> {
                                    val moduleContext = ModuleResourceResolver.createModuleContext(context)
                                        ?: return@runCatching
                                    if (!ModuleResourceResolver.isCurrentModuleBuild(moduleContext)) {
                                        Log.w("HuaweiPods", "skip ready signal: stale Hook build")
                                        return@runCatching
                                    }
                                    context.sendBroadcast(
                                        Intent(HuaweiPodsAction.ACTION_MODULE_MI_BLUETOOTH_SERVICE_ALIVE).apply {
                                            setPackage(BuildConfig.APPLICATION_ID)
                                            putExtra(
                                                HuaweiPodsAction.EXTRA_MODULE_BUILD_ID,
                                                BuildConfig.MODULE_BUILD_ID,
                                            )
                                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                        },
                                    )
                                }
                                HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                                    ConfigManager.refreshFromPrefs(prefs)
                                    if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_MODULE) {
                                        FocusIslandUtil.cancelBatteryIsland(context)
                                    }
                                }
                                HuaweiPodsAction.ACTION_SEND_STRONG_TOAST -> {
                                    if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_MODULE) {
                                        Log.d("HuaweiPods", "skip module island mode=${ConfigManager.islandMode()}")
                                        return@runCatching
                                    }
                                    val moduleContext = ModuleResourceResolver.createModuleContext(context)
                                        ?: return@runCatching
                                    if (!ModuleResourceResolver.isCurrentModuleBuild(moduleContext)) {
                                        Log.w("HuaweiPods", "skip focus island: stale Hook build")
                                        FocusIslandUtil.cancelBatteryIsland(context)
                                        return@runCatching
                                    }
                                    val batteryParams = intent.getParcelableExtra(
                                        "batteryParams",
                                        BatteryParams::class.java,
                                    ) ?: return@runCatching
                                    val address = intent.getStringExtra("address").orEmpty()
                                    FocusIslandUtil.showBatteryIsland(context, prefs, batteryParams, address)
                                }
                                HuaweiPodsAction.ACTION_UPDATE_PODS_NOTIFICATION -> {
                                    val batteryParams = intent.getParcelableExtra(
                                        "batteryParams",
                                        BatteryParams::class.java,
                                    ) ?: return@runCatching
                                    val device = intent.getParcelableExtra("device", BluetoothDevice::class.java)
                                    createPodsNotification(device, context, batteryParams)
                                }
                                HuaweiPodsAction.ACTION_CANCEL_PODS_NOTIFICATION -> {
                                    intent.getParcelableExtra("device", BluetoothDevice::class.java)
                                        ?.let { cancelNotification(it, context) }
                                }
                            }
                        }.onFailure {
                            Log.e("HuaweiPods", "Bluetooth notification receiver failed safely", it)
                        }
                    }
                }

                val intentFilter = IntentFilter().apply {
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_UI_INIT)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_SEND_STRONG_TOAST)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_UPDATE_PODS_NOTIFICATION)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_CANCEL_PODS_NOTIFICATION)
                }
                runCatching {
                    context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
                }.onSuccess {
                    receiverRegistered = true
                }.onFailure {
                    Log.e("HuaweiPods", "Failed to register Bluetooth notification receiver", it)
                }
            }
        }
    }

}
