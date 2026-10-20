package moe.chenxy.huaweipods.hook

import android.annotation.SuppressLint
import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.PowerManager
import moe.chenxy.huaweipods.utils.ModuleResourceResolver
import moe.chenxy.huaweipods.utils.PodImageLoader
import moe.chenxy.huaweipods.utils.notificationDisplayName
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.NotificationPresentationPolicy
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.config.preferredImagePath
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.displayName
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.platform.RomFamily
import moe.chenxy.huaweipods.platform.RomIntegrationPolicy
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import java.util.concurrent.ConcurrentHashMap
import java.io.File

internal fun shouldOfferNotificationAncAction(route: HuaweiDeviceRoute): Boolean = route.supportsAnc

/** PendingIntent 的 extras 不参与身份比较，必须把设备地址写入 Intent identifier。 */
internal fun headsetNotificationIntentIdentity(address: String): String = "BTHeadset$address"

internal fun shouldAcceptPodsNotificationUpdate(
    disconnectedSinceLastConnect: Boolean,
    deviceConnected: Boolean?,
): Boolean = !disconnectedSinceLastConnect && deviceConnected != false

internal fun shouldAttemptColorOsAutoPopup(
    isColorOsHost: Boolean,
    alreadyShownForConnection: Boolean,
    screenInteractive: Boolean,
    keyguardLocked: Boolean,
): Boolean = isColorOsHost &&
    !alreadyShownForConnection &&
    screenInteractive &&
    !keyguardLocked

/**
 * 华为耳机只在充电盒开盖时上报盒电量：盒电量从“未上报”变为“已上报”即视为开盖。
 * 首次上报（尚无记录）属于连接事件，由连接弹窗处理。
 */
internal fun isColorOsCaseOpened(previousCaseReported: Boolean?, caseReported: Boolean): Boolean =
    previousCaseReported == false && caseReported

internal fun shouldShowColorOsCaseOpenPopup(
    lastCaseOpenPopupAt: Long?,
    now: Long,
    cooldownMs: Long = COLOR_OS_CASE_OPEN_POPUP_COOLDOWN_MS,
): Boolean = lastCaseOpenPopupAt == null || now - lastCaseOpenPopupAt >= cooldownMs

internal const val COLOR_OS_CASE_OPEN_POPUP_COOLDOWN_MS = 15_000L

internal fun shouldDelegateColorOsNotificationToApp(
    isColorOsHost: Boolean,
    appCanPostNotifications: Boolean,
): Boolean = isColorOsHost && appCanPostNotifications

@SuppressLint("MissingPermission")
internal const val COLOR_OS_CONNECTION_CARD_LIVE_WINDOW_MS = 45_000L

/** 连接卡片只在弹出后的短时间内跟随电量刷新；之后视为已关闭，不再向快速设备连接发送更新。 */
internal fun shouldRefreshColorOsConnectionCard(shownAtElapsed: Long, nowElapsed: Long): Boolean =
    nowElapsed - shownAtElapsed in 0..COLOR_OS_CONNECTION_CARD_LIVE_WINDOW_MS

/** 连接卡片的电量文字：左耳 · 右耳 · 耳机盒，充电中的部件带闪电标记。 */
internal fun colorOsPopupBatteryText(battery: BatteryParams, chinese: Boolean): String = buildList {
    fun label(pod: PodParams, name: String) = "$name ${pod.battery.coerceIn(0, 100)}%${if (pod.isCharging) "⚡" else ""}"
    battery.left?.takeIf { it.isConnected }?.let { add(label(it, if (chinese) "左耳" else "L")) }
    battery.right?.takeIf { it.isConnected }?.let { add(label(it, if (chinese) "右耳" else "R")) }
    battery.case?.takeIf { it.isConnected }?.let { add(label(it, if (chinese) "耳机盒" else "Case")) }
}.joinToString(" · ")

private data class ColorOsLiveConnectionCard(
    val shownAtElapsed: Long,
    val deviceName: String,
    val imageFileName: String?,
    val fallbackDrawableName: String?,
    val batteryText: String,
)

private fun isChineseLocale(): Boolean =
    java.util.Locale.getDefault().language == java.util.Locale.CHINESE.language

object MiBluetoothToastHook : HookContext() {

    // ANC 模式本地缓存，用于在 FreeBuds 3 已验证的关/开状态之间切换。
    private val receiverRegistrationLock = Any()
    private val activeNotificationAddresses = ConcurrentHashMap.newKeySet<String>()
    private val disconnectedNotificationAddresses = ConcurrentHashMap.newKeySet<String>()
    private val officialPopupShownAddresses = ConcurrentHashMap.newKeySet<String>()
    private val lastCaseReported = ConcurrentHashMap<String, Boolean>()
    private val lastCaseOpenPopupAt = ConcurrentHashMap<String, Long>()
    private val liveConnectionCards = ConcurrentHashMap<String, ColorOsLiveConnectionCard>()
    private val lowBatteryTracker = ColorOsLowBatteryTracker()
    @Volatile
    private var latestBatteryUpdate: Pair<BluetoothDevice, BatteryParams>? = null
    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var colorOsPopupHostReady = false
    private var notificationReceiverContext: Context? = null
    private var notificationReceiver: BroadcastReceiver? = null
    private var colorOsHostReadyReceiver: BroadcastReceiver? = null

    override fun onHook() {
        val isColorOsHost =
            RomIntegrationPolicy.detect(android.os.Build.MANUFACTURER, android.os.Build.BRAND) ==
                RomFamily.COLOR_OS

        fun cancelNotificationForAddress(address: String, context: Context) {
            if (address.isBlank()) return
            val notificationManager = context.getSystemService("notification") as NotificationManager
            notificationManager.cancel("BTHeadset$address", 10003)
            notificationManager.cancel("HuaweiPodsPopup$address", 10004)
            activeNotificationAddresses.remove(address)
        }

        fun clearConnectionPopupState(address: String) {
            officialPopupShownAddresses.remove(address)
            lastCaseReported.remove(address)
            liveConnectionCards.remove(address)
        }

        fun cancelAllPodsNotifications(context: Context) {
            activeNotificationAddresses.toList().forEach { address ->
                runCatching { cancelNotificationForAddress(address, context) }
                    .onFailure { Log.w("HuaweiPods", "Failed to cancel disabled Pod Notification", it) }
            }
            // Hook 进程重启后内存集合为空，但旧通知仍可能留在 SystemUI；按本模块固定 tag/id 补扫。
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.activeNotifications
                    .filter {
                        (it.id == 10003 && it.tag?.startsWith("BTHeadset") == true) ||
                            (it.id == 10004 && it.tag?.startsWith("HuaweiPodsPopup") == true)
                    }
                    .forEach { manager.cancel(it.tag, it.id) }
            }.onFailure {
                Log.w("HuaweiPods", "Failed to scan disabled Pod Notifications", it)
            }
        }

        fun deviceConnectionState(device: BluetoothDevice): Boolean? = runCatching {
            val method = device.javaClass.methods.firstOrNull {
                it.name == "isConnected" && it.parameterCount in 0..1
            } ?: return@runCatching null
            when (method.parameterCount) {
                0 -> method.invoke(device) as? Boolean
                else -> method.invoke(device, BluetoothDevice.TRANSPORT_AUTO) as? Boolean
            }
        }.onFailure {
            Log.w("HuaweiPods", "Unable to verify notification device connection", it)
        }.getOrNull()

        fun deleteIntent(context: Context, bluetoothDevice: BluetoothDevice): PendingIntent? {
            val intent = Intent("com.android.bluetooth.headset.notification.cancle")
            intent.putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            intent.setIdentifier(headsetNotificationIntentIdentity(bluetoothDevice.address))
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun popupPendingIntent(
            context: Context,
            bluetoothDevice: BluetoothDevice,
            deviceName: String,
        ): PendingIntent = PendingIntent.getActivity(
            context,
            bluetoothDevice.address.hashCode(),
            Intent(HuaweiPodsAction.ACTION_SHOW_PODS_UI).apply {
                setClassName(BuildConfig.APPLICATION_ID, "moe.chenxy.huaweipods.PopupActivity")
                setIdentifier(headsetNotificationIntentIdentity(bluetoothDevice.address))
                putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothDevice)
                putExtra("bluetoothaddress", bluetoothDevice.address)
                putExtra("device_name", deviceName)
                putExtra("navigate_page", "earphone_detail")
                putExtra("device_address", bluetoothDevice.address)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        @SuppressLint("WrongConstant")
        fun createPodsNotification(bluetoothDevice: BluetoothDevice?, context: Context, batteryParams: BatteryParams) {
            val system_notification_accent_color = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
            if (bluetoothDevice == null) {
                Log.e("HuaweiPods", "createPodsNotification: btDevice null")
                return
            }
            try {
                val address: String = bluetoothDevice.address
                val notificationIdentity = headsetNotificationIntentIdentity(address)
                if (!NotificationPresentationPolicy.shouldPostPersistentNotification(
                        ConfigManager.persistentNotificationEnabled(),
                    )
                ) {
                    cancelNotificationForAddress(address, context)
                    Log.d("HuaweiPods", "skip persistent notification: disabled")
                    return
                }
                val displayName = bluetoothDevice.notificationDisplayName()
                val deviceName = runCatching { bluetoothDevice.name }.getOrNull().orEmpty()
                if (!ModuleResourceResolver.isCurrentModuleBuild(context)) {
                    cancelNotificationForAddress(address, context)
                    Log.w("HuaweiPods", "skip notification: stale Hook build")
                    return
                }
                val moduleResources = ModuleResourceResolver.resources(context) ?: run {
                    Log.w("HuaweiPods", "skip notification: module resources unavailable")
                    return
                }
                val appCanPostNotifications = context.packageManager.checkPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                    BuildConfig.APPLICATION_ID,
                ) == PackageManager.PERMISSION_GRANTED
                if (shouldDelegateColorOsNotificationToApp(
                        isColorOsHost = isColorOsHost,
                        appCanPostNotifications = appCanPostNotifications,
                    )
                ) {
                    cancelNotificationForAddress(address, context)
                    Log.d("HuaweiPods", "ColorOS persistent notification delegated to Live Alert")
                    return
                }
                val deviceRoute = DeviceRoutePrefs.resolve(prefs, address, deviceName)
                val offerAncAction = shouldOfferNotificationAncAction(deviceRoute)
                val lockscreenVisibility = if (ConfigManager.lockscreenNotificationEnabled()) {
                    Notification.VISIBILITY_PUBLIC
                } else {
                    Notification.VISIBILITY_SECRET
                }

                val caseLabel = moduleResources.getString(R.string.pod_case)
                val leftLabel = moduleResources.getString(R.string.batt_left_pod)
                val rightLabel = moduleResources.getString(R.string.batt_right_pod)
                val caseBattStr = if (batteryParams.case != null && batteryParams.case!!.isConnected)
                    "$caseLabel${batteryParams.case!!.battery}%" +
                            "${if (batteryParams.case!!.isCharging) "⚡ " else " "}\n"
                else ""
                val leftEar = if (batteryParams.left != null && batteryParams.left!!.isConnected)
                    "$leftLabel${batteryParams.left!!.battery}%" +
                        (if (batteryParams.left!!.isCharging) "⚡" else "")
                else ""
                val leftToRight = if (batteryParams.left?.isConnected == true && batteryParams.right?.isConnected == true) " " else ""
                val rightEar = if (batteryParams.right != null && batteryParams.right!!.isConnected)
                    "$leftToRight$rightLabel${batteryParams.right!!.battery}%" +
                        (if (batteryParams.right!!.isCharging) "⚡ " else " ")
                else ""

                val contentText: String = caseBattStr + leftEar + rightEar
                val notificationManager = context.getSystemService("notification") as NotificationManager
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        "BTHeadset$address",
                        displayName,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        setAllowBubbles(true)
                        setLockscreenVisibility(lockscreenVisibility)
                    }
                )
                val ancLabel = moduleResources.getString(R.string.cycle_anc)
                val ancAction = if (offerAncAction) {
                    val ancCycleIntent = Intent(HuaweiPodsAction.ACTION_CYCLE_ANC).apply {
                        setPackage("com.android.bluetooth")
                        setIdentifier(notificationIdentity)
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
                val headsetBitmap = PodImageLoader.loadBoxBitmap(
                    context = context,
                    prefs = prefs,
                    address = address,
                    verifiedRoute = deviceRoute,
                )
                    ?: BitmapFactory.decodeResource(moduleResources, R.drawable.img_box)
                if (headsetBitmap == null) {
                    Log.e("HuaweiPods", "createPodsNotification: headset bitmap null")
                    return
                }
                val headsetIcon = Icon.createWithBitmap(headsetBitmap)
                val pendingIntent = popupPendingIntent(context, bluetoothDevice, deviceName)
                val notification = Notification.Builder(context, "BTHeadset$address")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setWhen(0L)
                    .setTicker(displayName)
                    .setContentTitle(displayName)
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(deleteIntent(context, bluetoothDevice))
                    .setColor(
                        system_notification_accent_color.takeIf { it != 0 }
                            ?.let { context.getColor(it) }
                            ?: Color.rgb(0x33, 0x8A, 0xFF),
                    )
                    .setOngoing(true)
                    .apply { ancAction?.let { addAction(it) } }
                    .setVisibility(lockscreenVisibility)
                    .build()
                notificationManager.notify("BTHeadset$address", 10003, notification)
                activeNotificationAddresses.add(address)
            } catch (e: Exception) {
                Log.e("HuaweiPods", "Failed to create Pod Notification", e)
            }
        }

        @SuppressLint("MissingPermission")
        fun createPodsHeadsUpNotification(
            address: String,
            context: Context,
            batteryParams: BatteryParams,
        ) {
            if (address.isBlank()) return
            val bluetoothDevice = runCatching {
                BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
            }.getOrNull() ?: return
            val deviceName = bluetoothDevice.notificationDisplayName()
            if (!ModuleResourceResolver.isCurrentModuleBuild(context)) return
            val moduleResources = ModuleResourceResolver.resources(context) ?: return

            val batteryText = buildList {
                batteryParams.left?.takeIf { it.isConnected }?.let {
                    add("${moduleResources.getString(R.string.batt_left_pod)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
                }
                batteryParams.right?.takeIf { it.isConnected }?.let {
                    add("${moduleResources.getString(R.string.batt_right_pod)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
                }
                batteryParams.case?.takeIf { it.isConnected }?.let {
                    add("${moduleResources.getString(R.string.pod_case)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
                }
            }.joinToString("  ")
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "HuaweiPodsConnection"
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    moduleResources.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
            val largeIcon = PodImageLoader.loadBoxBitmap(context, prefs, address)
                ?: BitmapFactory.decodeResource(moduleResources, R.drawable.img_box)
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(deviceName)
                .setContentText(batteryText)
                .setStyle(Notification.BigTextStyle().bigText(batteryText))
                .setContentIntent(popupPendingIntent(context, bluetoothDevice, deviceName))
                .setAutoCancel(true)
                .setTimeoutAfter(6_000L)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .apply { largeIcon?.let(::setLargeIcon) }
                .build()
            manager.notify("HuaweiPodsPopup$address", 10004, notification)
            Log.i("HuaweiPods", "ColorOS connection heads-up posted")
        }

        @SuppressLint("MissingPermission")
        fun postLowBatteryReminder(
            context: Context,
            device: BluetoothDevice?,
            lowEarbuds: List<Pair<Boolean, Int>>,
            preview: Boolean,
        ) {
            if (!ModuleResourceResolver.isCurrentModuleBuild(context)) return
            val moduleResources = ModuleResourceResolver.resources(context) ?: return
            val address = device?.address.orEmpty()
            val deviceName = device.notificationDisplayName()
            val levels = lowEarbuds.joinToString("  ") { (isLeft, level) ->
                val label = moduleResources.getString(
                    if (isLeft) R.string.batt_left_pod else R.string.batt_right_pod,
                )
                "$label $level%"
            }
            val text = if (preview) {
                moduleResources.getString(R.string.low_battery_reminder_preview_text, levels)
            } else {
                moduleResources.getString(R.string.low_battery_reminder_text, levels)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "HuaweiPodsLowBattery"
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    moduleResources.getString(R.string.low_battery_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
            val largeIcon = address.takeIf(String::isNotBlank)
                ?.let { PodImageLoader.loadBoxBitmap(context, prefs, it) }
                ?: BitmapFactory.decodeResource(moduleResources, R.drawable.img_box)
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(moduleResources.getString(R.string.low_battery_reminder_title, deviceName))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .apply { device?.let { setContentIntent(popupPendingIntent(context, it, deviceName)) } }
                .setAutoCancel(true)
                .setTimeoutAfter(60_000L)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_HIGH)
                .apply { largeIcon?.let(::setLargeIcon) }
                .build()
            manager.notify("HuaweiPodsLowBattery$address", 10005, notification)
            Log.i("HuaweiPods", "Low battery reminder posted device=$address preview=$preview levels=$levels")
        }

        /**
         * ColorOS 的三方耳机低电量胶囊只对云端白名单厂商开放，这里用一条高优先级通知代替：
         * 在线且未充电的耳机降到 20%、10% 时各提醒一次，充电或电量回升后重新布防。
         */
        fun maybeShowLowBatteryReminder(
            device: BluetoothDevice,
            context: Context,
            batteryParams: BatteryParams,
        ) {
            val address = device.address
            latestBatteryUpdate = device to batteryParams
            if (!ConfigManager.lowBatteryReminderEnabled()) {
                lowBatteryTracker.reset(address)
                return
            }
            if (!lowBatteryTracker.shouldNotify(address, batteryParams)) return
            postLowBatteryReminder(context, device, lowBatteryTracker.lowEarbuds(batteryParams), preview = false)
        }

        /** 设置页“发送测试提醒”：用当前耳机电量（未连接时用示例电量）立即发一条，不影响提醒档位。 */
        fun previewLowBatteryReminder(context: Context) {
            val (device, battery) = latestBatteryUpdate ?: (null to null)
            val levels = listOfNotNull(
                battery?.left?.takeIf { it.isConnected }?.let { true to it.battery },
                battery?.right?.takeIf { it.isConnected }?.let { false to it.battery },
            ).ifEmpty { listOf(true to 15, false to 18) }
            postLowBatteryReminder(context, device, levels, preview = true)
        }

        /** 连接卡片显示期间，电量（含左右耳出入盒导致的充电状态）变化时同步刷新卡片文字。 */
        fun refreshColorOsConnectionCard(address: String, context: Context, batteryParams: BatteryParams) {
            val card = liveConnectionCards[address] ?: return
            val now = android.os.SystemClock.elapsedRealtime()
            if (!shouldRefreshColorOsConnectionCard(card.shownAtElapsed, now)) {
                liveConnectionCards.remove(address, card)
                return
            }
            val batteryText = colorOsPopupBatteryText(batteryParams, isChineseLocale())
            if (batteryText.isBlank() || batteryText == card.batteryText) return
            if (ColorOsAccessoryPopupBridge.updateConnected(
                    context = context,
                    deviceName = card.deviceName,
                    batteryText = batteryText,
                    imageFileName = card.imageFileName,
                    fallbackDrawableName = card.fallbackDrawableName,
                )
            ) {
                liveConnectionCards[address] = card.copy(batteryText = batteryText)
                Log.d("HuaweiPods", "ColorOS connection card battery refreshed")
            }
        }

        fun showColorOsConnectionPopup(
            address: String,
            context: Context,
            batteryParams: BatteryParams,
            caseOpened: Boolean = false,
        ) {
            if (address.isBlank() || !isColorOsHost || address in officialPopupShownAddresses) return
            val bluetoothDevice = runCatching {
                BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
            }.getOrNull() ?: return
            val screenInteractive =
                (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
            val keyguardLocked =
                (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
            if (caseOpened && (!colorOsPopupHostReady || !screenInteractive || keyguardLocked)) {
                // 开盖只补官方卡片；息屏、锁屏或宿主未就绪时不再用横幅打扰。
                officialPopupShownAddresses.add(address)
                Log.d("HuaweiPods", "ColorOS case-open popup skipped host=$colorOsPopupHostReady screen=$screenInteractive locked=$keyguardLocked")
                return
            }
            if (!colorOsPopupHostReady) {
                context.sendBroadcast(
                    Intent(HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_PROBE).apply {
                        setPackage("com.heytap.accessory")
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    },
                )
                createPodsHeadsUpNotification(address, context, batteryParams)
                officialPopupShownAddresses.add(address)
                Log.i("HuaweiPods", "ColorOS popup host not scoped; used heads-up fallback")
                return
            }
            if (!shouldAttemptColorOsAutoPopup(
                    isColorOsHost = isColorOsHost,
                    alreadyShownForConnection = false,
                    screenInteractive = screenInteractive,
                    keyguardLocked = keyguardLocked,
                )
            ) {
                createPodsHeadsUpNotification(address, context, batteryParams)
                officialPopupShownAddresses.add(address)
                return
            }

            val moduleResources = ModuleResourceResolver.resources(context)
            val bluetoothName = runCatching { bluetoothDevice.name }.getOrNull()
            val deviceRoute = DeviceRoutePrefs.resolve(prefs, address, bluetoothName)
            if (!deviceRoute.isSupported) {
                createPodsHeadsUpNotification(address, context, batteryParams)
                officialPopupShownAddresses.add(address)
                return
            }
            val deviceName = bluetoothDevice.notificationDisplayName()
            // 缓存官方图缺失时，快速设备连接进程按该资源名加载机型内置图，而不是固定用 FreeClip 图。
            val fallbackDrawableName = moduleResources?.let { resources ->
                runCatching {
                    resources.getResourceEntryName(
                        PodImageLoader.modelFallbackResId(
                            deviceRoute,
                            PodImageResource.BOX,
                            R.drawable.img_box,
                        ),
                    )
                }.getOrNull()
            }
            val batteryText = colorOsPopupBatteryText(batteryParams, isChineseLocale())
            val imageFileName = runCatching {
                PodImagePrefs.find(prefs, address)
                    ?.preferredImagePath(PodImageResource.BOX)
                    ?.let(::File)
                    ?.name
            }.getOrNull()

            if (ColorOsAccessoryPopupBridge.showConnected(
                    context = context,
                    deviceName = deviceName,
                    batteryText = batteryText,
                    imageFileName = imageFileName,
                    fallbackDrawableName = fallbackDrawableName,
                )
            ) {
                liveConnectionCards[address] = ColorOsLiveConnectionCard(
                    shownAtElapsed = android.os.SystemClock.elapsedRealtime(),
                    deviceName = deviceName,
                    imageFileName = imageFileName,
                    fallbackDrawableName = fallbackDrawableName,
                    batteryText = batteryText,
                )
                Log.i("HuaweiPods", "ColorOS official accessory popup requested route=$deviceRoute")
            } else {
                createPodsHeadsUpNotification(address, context, batteryParams)
            }
            officialPopupShownAddresses.add(address)
        }

        fun cancelNotification(bluetoothDevice: BluetoothDevice, context: Context) {
            try {
                val address = bluetoothDevice.address
                if (address.isNotEmpty()) {
                    cancelNotificationForAddress(address, context)
                }
            } catch (e: Exception) {
                Log.e("HuaweiPods", "Failed to cancel Pod Notification!", e)
            }
        }

        fun registerNotificationReceiver(sourceContext: Context) {
            // Application.attach() 的早期阶段 applicationContext 可能仍为空。
            // 使用传入的 Context 兜底，避免首次启动时漏注册通知更新接收器。
            val context = sourceContext.applicationContext ?: sourceContext
            synchronized(receiverRegistrationLock) {
                if (receiverRegistered) return@synchronized

                val broadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, receivedIntent: Intent?) {
                        runCatching {
                            val intent = receivedIntent ?: return@runCatching
                            when (HuaweiPodsAction.canonical(intent.action)) {
                                HuaweiPodsAction.ACTION_PODS_UI_INIT -> {
                                    if (!ModuleResourceResolver.isCurrentModuleBuild(context)) {
                                        Log.w("HuaweiPods", "skip ready signal: stale Hook build")
                                        return@runCatching
                                    }
                                    context.sendIdentitySharingBroadcast(
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
                                    if (!ConfigManager.persistentNotificationEnabled()) {
                                        cancelAllPodsNotifications(context)
                                    }
                                }
                                HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED -> {
                                    // 请求蓝牙进程用当前电量快照重建通知，换上新图片。
                                    requestCurrentNotificationRestore(
                                        context,
                                        intent.getStringExtra("address"),
                                    )
                                }
                                HuaweiPodsAction.ACTION_PREVIEW_LOW_BATTERY_REMINDER -> {
                                    previewLowBatteryReminder(context)
                                }
                                HuaweiPodsAction.ACTION_UPDATE_PODS_NOTIFICATION -> {
                                    val batteryParams = intent.getParcelableExtra(
                                        "batteryParams",
                                        BatteryParams::class.java,
                                    ) ?: return@runCatching
                                    val device = intent.getParcelableExtra("device", BluetoothDevice::class.java)
                                        ?: return@runCatching
                                    val address = device.address
                                    if (!shouldAcceptPodsNotificationUpdate(
                                            disconnectedSinceLastConnect =
                                                address in disconnectedNotificationAddresses,
                                            deviceConnected = deviceConnectionState(device),
                                        )
                                    ) {
                                        cancelNotificationForAddress(address, context)
                                        Log.i(
                                            "HuaweiPods",
                                            "Dropped stale battery notification after disconnect",
                                        )
                                        return@runCatching
                                    }
                                    createPodsNotification(device, context, batteryParams)
                                    // A rename changes presentation, not battery/case state. Do not
                                    // replay low-battery alerts or reopen the connection popup.
                                    if (intent.getBooleanExtra(HuaweiPodsAction.EXTRA_PRESENTATION_ONLY, false)) return@runCatching
                                    runCatching { maybeShowLowBatteryReminder(device, context, batteryParams) }
                                        .onFailure { Log.w("HuaweiPods", "Low battery reminder failed", it) }
                                    if (isColorOsHost) {
                                        val caseReported = batteryParams.case?.isConnected == true
                                        val caseOpened = isColorOsCaseOpened(
                                            previousCaseReported = lastCaseReported.put(address, caseReported),
                                            caseReported = caseReported,
                                        )
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        if (caseOpened && shouldShowColorOsCaseOpenPopup(lastCaseOpenPopupAt[address], now)) {
                                            // 与 OPPO 耳机一致：连接状态下打开充电盒再次弹出官方卡片。
                                            lastCaseOpenPopupAt[address] = now
                                            officialPopupShownAddresses.remove(address)
                                            Log.i("HuaweiPods", "ColorOS case opened; requesting official popup")
                                        }
                                        if (address in officialPopupShownAddresses) {
                                            runCatching { refreshColorOsConnectionCard(address, context, batteryParams) }
                                                .onFailure { Log.w("HuaweiPods", "Connection card refresh failed", it) }
                                        }
                                        // ColorOS does not consistently emit the separate strong-toast request.
                                        // The first verified battery update is the reliable connected-device event.
                                        showColorOsConnectionPopup(address, context, batteryParams, caseOpened)
                                    }
                                }
                                HuaweiPodsAction.ACTION_CANCEL_PODS_NOTIFICATION -> {
                                    intent.getParcelableExtra("device", BluetoothDevice::class.java)
                                        ?.let {
                                            disconnectedNotificationAddresses.add(it.address)
                                            cancelNotification(it, context)
                                            lowBatteryTracker.reset(it.address)
                                            if (latestBatteryUpdate?.first?.address == it.address) {
                                                latestBatteryUpdate = null
                                            }
                                            runCatching {
                                                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                                    .cancel("HuaweiPodsLowBattery${it.address}", 10005)
                                            }
                                        }
                                }
                                else -> when (intent.action) {
                                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                                        intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice::class.java,
                                        )?.let {
                                            disconnectedNotificationAddresses.remove(it.address)
                                            clearConnectionPopupState(it.address)
                                        }
                                    }
                                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                        intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice::class.java,
                                        )?.let {
                                            disconnectedNotificationAddresses.add(it.address)
                                            clearConnectionPopupState(it.address)
                                            cancelNotification(it, context)
                                            Log.i(
                                                "HuaweiPods",
                                                "ColorOS notification cleared on ACL disconnect",
                                            )
                                        }
                                    }
                                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                                        val device = intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice::class.java,
                                        ) ?: return@runCatching
                                        when (intent.getIntExtra(
                                            BluetoothProfile.EXTRA_STATE,
                                            BluetoothProfile.STATE_DISCONNECTED,
                                        )) {
                                            BluetoothProfile.STATE_CONNECTED ->
                                                disconnectedNotificationAddresses.remove(device.address)
                                            BluetoothProfile.STATE_DISCONNECTED -> {
                                                disconnectedNotificationAddresses.add(device.address)
                                                clearConnectionPopupState(device.address)
                                                cancelNotification(device, context)
                                            }
                                        }
                                    }
                                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                                        if (intent.getIntExtra(
                                                BluetoothAdapter.EXTRA_STATE,
                                                BluetoothAdapter.ERROR,
                                            ) == BluetoothAdapter.STATE_OFF
                                        ) {
                                            officialPopupShownAddresses.clear()
                                            liveConnectionCards.clear()
                                            cancelAllPodsNotifications(context)
                                        }
                                    }
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
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_UPDATE_PODS_NOTIFICATION)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_PREVIEW_LOW_BATTERY_REMINDER)
                    addHuaweiPodsAction(HuaweiPodsAction.ACTION_CANCEL_PODS_NOTIFICATION)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                }
                runCatching {
                    context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
                }.onSuccess {
                    notificationReceiverContext = context
                    notificationReceiver = broadcastReceiver
                    receiverRegistered = true
                    Log.i(
                        "HuaweiPods",
                        "notification receiver registered build=${BuildConfig.MODULE_BUILD_ID} " +
                            "receiver=${System.identityHashCode(broadcastReceiver)}",
                    )
                    // API 102 不会重放连接事件，向蓝牙进程请求用当前状态重建通知。
                    requestCurrentNotificationRestore(context)
                    if (isColorOsHost) {
                        val hostReadyReceiver = object : BroadcastReceiver() {
                            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                                if (intent?.action == HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_READY) {
                                    colorOsPopupHostReady = true
                                    Log.i("HuaweiPods", "ColorOS official popup host ready")
                                }
                            }
                        }
                        runCatching {
                            context.registerReceiver(
                                hostReadyReceiver,
                                IntentFilter(HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_READY),
                                "android.permission.BLUETOOTH_PRIVILEGED",
                                null,
                                Context.RECEIVER_EXPORTED,
                            )
                        }.onSuccess {
                            colorOsHostReadyReceiver = hostReadyReceiver
                            context.sendBroadcast(
                                Intent(HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_PROBE).apply {
                                    setPackage("com.heytap.accessory")
                                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                },
                            )
                        }.onFailure {
                            Log.w("HuaweiPods", "ColorOS popup host receiver unavailable", it)
                        }
                    }
                }.onFailure {
                    Log.e("HuaweiPods", "Failed to register Bluetooth notification receiver", it)
                }
            }
        }

        // 接收器注册绑定到宿主 Application 生命周期。
        hookAfter(
            Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
                isAccessible = true
            },
        ) {
            if (Application.getProcessName() != packageName) return@hookAfter
            (args[0] as? Context)?.let(::registerNotificationReceiver)
        }
        // API 102 热重载不会重放 Application.attach()；直接复用当前 Application 恢复接收器。
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
        }.getOrNull()
            ?.takeIf { Application.getProcessName() == packageName }
            ?.let(::registerNotificationReceiver)
    }

    override fun onSaveHotReloadState(outState: Bundle) {
        outState.putStringArrayList(
            "active_notification_addresses",
            ArrayList(activeNotificationAddresses),
        )
    }

    override fun onRestoreHotReloadState(savedState: Bundle) {
        activeNotificationAddresses += savedState
            .getStringArrayList("active_notification_addresses")
            .orEmpty()
    }

    private fun requestCurrentNotificationRestore(context: Context, address: String? = null) {
        context.sendBroadcast(
            Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
                setPackage("com.android.bluetooth")
                putExtra(HuaweiPodsAction.EXTRA_RESTORE_NOTIFICATION, true)
                address?.takeIf(String::isNotBlank)?.let { putExtra("address", it) }
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
        )
    }

    override fun onClose() {
        synchronized(receiverRegistrationLock) {
            val receiver = notificationReceiver
            val receiverContext = notificationReceiverContext
            if (receiver != null && receiverContext != null) {
                runCatching { receiverContext.unregisterReceiver(receiver) }
                    .onFailure { error ->
                        if (error !is IllegalArgumentException) {
                            Log.w("HuaweiPods", "Failed to unregister notification receiver", error)
                        }
                    }
            }
            if (receiver != null) {
                Log.i(
                    "HuaweiPods",
                    "notification receiver closed build=${BuildConfig.MODULE_BUILD_ID} " +
                        "receiver=${System.identityHashCode(receiver)}",
                )
            }
            colorOsHostReadyReceiver?.let { hostReceiver ->
                runCatching { receiverContext?.unregisterReceiver(hostReceiver) }
            }
            colorOsHostReadyReceiver = null
            colorOsPopupHostReady = false
            notificationReceiver = null
            notificationReceiverContext = null
            receiverRegistered = false
            activeNotificationAddresses.clear()
            disconnectedNotificationAddresses.clear()
            officialPopupShownAddresses.clear()
            liveConnectionCards.clear()
            lastCaseReported.clear()
            lastCaseOpenPopupAt.clear()
            lowBatteryTracker.clear()
            latestBatteryUpdate = null
        }
    }

}
