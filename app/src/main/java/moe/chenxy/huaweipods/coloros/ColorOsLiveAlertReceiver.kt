package moe.chenxy.huaweipods.coloros

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.provider.Settings
import androidx.core.app.NotificationCompat
import moe.chenxy.huaweipods.MainActivity
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.displayName
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.broadcast.HuaweiPodsBroadcastTrustPolicy
import moe.chenxy.huaweipods.platform.RomFamily
import moe.chenxy.huaweipods.platform.RomIntegrationPolicy
import moe.chenxy.huaweipods.utils.PodImageLoader
import moe.chenxy.huaweipods.utils.resolveBluetoothDisplayName
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

internal fun colorOsLiveAlertChipText(
    left: Int?,
    right: Int?,
    case: Int?,
): String? = listOfNotNull(left, right).minOrNull()?.let { "$it%" }
    ?: case?.let { "$it%" }

internal fun shouldShowColorOsLiveAlert(
    isColorOs: Boolean,
    hasConnectedBattery: Boolean,
): Boolean = isColorOs && hasConnectedBattery

internal fun shouldAcceptColorOsLiveAlertBatteryUpdate(
    lastDisconnectElapsed: Long,
    eventElapsed: Long,
): Boolean = lastDisconnectElapsed <= 0L || eventElapsed > lastDisconnectElapsed

/** 记录断开时间：早于该时间的电量广播不会再把流体云拉起来。 */
internal fun markColorOsLiveAlertDisconnected(context: Context, elapsedRealtime: Long) {
    context.getSharedPreferences("coloros_live_alert_state", Context.MODE_PRIVATE).edit()
        .putLong("last_disconnect_elapsed", elapsedRealtime)
        .putLong("last_event_elapsed", elapsedRealtime)
        .putInt("event_boot", runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1))
        .remove("current_address")
        .apply()
}

internal fun NotificationCompat.Builder.requestColorOsLiveAlert(chipText: String?) = apply {
    // ColorOS 16 also supports promotion on API 36.0. AndroidX writes
    // the compatible extra without calling the platform's 36.1 setter.
    if (Build.VERSION.SDK_INT >= 36) {
        setRequestPromotedOngoing(true)
        chipText?.let(::setShortCriticalText)
    }
}

/** Publishes the Android 16 promoted ongoing notification rendered as ColorOS Fluid Cloud. */
class ColorOsLiveAlertReceiver : BroadcastReceiver() {
    internal var fromBoundBridge = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in TRUSTED_BLUETOOTH_ACTIONS && intent.action !in setOf(
                BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) return
        if (
            intent.action in TRUSTED_BLUETOOTH_ACTIONS &&
            !HuaweiPodsBroadcastTrustPolicy.isTrustedBluetoothStateSender(sentFromPackage)
        ) {
            Log.w(TAG, "Rejected headset status broadcast from an untrusted sender: $sentFromPackage")
            return
        }
        handleTrustedEvent(context, intent)
    }

    /** Only called after the receiver or Binder has authenticated the real Bluetooth sender. */
    internal fun handleTrustedEvent(context: Context, intent: Intent, onComplete: ((Boolean) -> Unit)? = null) {
        var completionPending = false
        var succeeded = true
        try {
        val isColorOs = RomIntegrationPolicy.detect(Build.MANUFACTURER, Build.BRAND) ==
            RomFamily.COLOR_OS
        val manager = context.getSystemService(NotificationManager::class.java)
        val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (statePrefs.getLong(KEY_LAST_DISCONNECT_ELAPSED, 0L) == Long.MAX_VALUE) {
            // Migrate the old sentinel: it could reject every later battery event when
            // ColorOS omitted the matching connected broadcast.
            statePrefs.edit().putLong(KEY_LAST_DISCONNECT_ELAPSED, 0L).apply()
        }
        val eventElapsed = intent.getLongExtra(
            HuaweiPodsAction.EXTRA_EVENT_ELAPSED_REALTIME,
            0L,
        )
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                if (ColorOsLiveAlertConnectionPolicy.isBluetoothTurningOffOrOff(
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR),
                    )
                ) {
                    cancelLiveAlert(context, manager, statePrefs, eventElapsed)
                    Log.d(TAG, "ColorOS Live Alert cancelled on Bluetooth shutdown")
                }
                return
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                )
                val eventAddress = runCatching {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java,
                    )?.address
                }.getOrNull()
                val currentAddress = statePrefs.getString(KEY_CURRENT_ADDRESS, null)
                if (ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(
                        currentAddress, eventAddress, state,
                    )
                ) completionPending = confirmDisconnected(context, manager, statePrefs, eventAddress, eventElapsed, onComplete)
                return
            }
        }
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        val previousBoot = statePrefs.getInt("event_boot", -1).takeIf { it >= 0 }
        if (boot != null && previousBoot != null && boot != previousBoot) {
            statePrefs.edit().putLong(KEY_LAST_DISCONNECT_ELAPSED, 0L).remove(KEY_CURRENT_ADDRESS).apply()
        }
        if (!acceptColorOsLiveAlertEvent(previousBoot, boot, statePrefs.getLong("last_event_elapsed", 0L), eventElapsed)) return
        if (eventElapsed > 0) statePrefs.edit().putLong("last_event_elapsed", eventElapsed)
            .putInt("event_boot", boot ?: -1).apply()
        when (HuaweiPodsAction.canonical(intent.action)) {
            HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                statePrefs.edit()
                    .putLong(KEY_LAST_DISCONNECT_ELAPSED, 0L)
                    .putString(KEY_CURRENT_ADDRESS, intent.getStringExtra("address"))
                    .apply()
                return
            }

            HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                // The module also sends this when only A2DP ends. Verify HFP before clearing.
                completionPending = confirmDisconnected(context, manager, statePrefs, intent.getStringExtra("address"), eventElapsed, onComplete)
                return
            }

            HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED -> {
                if (intent.getStringExtra("state") == "disconnected") {
                    completionPending = confirmDisconnected(context, manager, statePrefs, intent.getStringExtra("address"), eventElapsed, onComplete)
                } else if (intent.getStringExtra("state") == "connected") {
                    statePrefs.edit()
                        .putLong(KEY_LAST_DISCONNECT_ELAPSED, 0L)
                        .putString(KEY_CURRENT_ADDRESS, intent.getStringExtra("address"))
                        .apply()
                }
                return
            }

            HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> Unit
            else -> return
        }
        if (!isColorOs) return
        val lastDisconnectElapsed = statePrefs.getLong(KEY_LAST_DISCONNECT_ELAPSED, 0L)
        if (!shouldAcceptColorOsLiveAlertBatteryUpdate(lastDisconnectElapsed, eventElapsed)) {
            context.stopService(Intent(context, ColorOsLiveAlertService::class.java))
            manager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Ignored stale ColorOS Live Alert battery update")
            return
        }

        ConfigManager.refreshFromPrefs(
            context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE),
        )
        if (!ConfigManager.persistentNotificationEnabled() ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            context.stopService(Intent(context, ColorOsLiveAlertService::class.java))
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val battery = intent.batteryParams()
        intent.getStringExtra("address")?.takeIf(String::isNotBlank)?.let { address ->
            statePrefs.edit().putString(KEY_CURRENT_ADDRESS, address).apply()
        }
        val left = battery.left?.takeIf { it.isConnected }?.battery
        val right = battery.right?.takeIf { it.isConnected }?.battery
        val case = battery.case?.takeIf { it.isConnected }?.battery
        if (!shouldShowColorOsLiveAlert(isColorOs, listOf(left, right, case).any { it != null })) {
            context.stopService(Intent(context, ColorOsLiveAlertService::class.java))
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.coloros_live_alert_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)

        val contentText = buildList {
            left?.let { add("L $it%") }
            right?.let { add("R $it%") }
            case?.let { add("${context.getString(R.string.pod_case)} $it%") }
        }.joinToString("  ")
        val address = intent.getStringExtra("address").orEmpty()
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        // 蓝牙进程已确认机型：用于选择机型内置图，避免 FreeClip 2 等型号显示成一代 FreeClip。
        val route = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        )?.takeIf { it.isSupported }
        val largeIcon = address.takeIf(String::isNotBlank)
            ?.let { PodImageLoader.loadBoxBitmap(context, prefs, it, verifiedRoute = route) }
            ?: BitmapFactory.decodeResource(
                context.resources,
                PodImageLoader.modelFallbackResId(
                    route ?: HuaweiDeviceRoute.UNSUPPORTED,
                    PodImageResource.BOX,
                    R.drawable.img_box,
                ),
            )
        val requestCode = if (address.isNotBlank()) address.hashCode() else 0
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_NAVIGATE_PAGE, MainActivity.PAGE_EARPHONE_DETAIL)
                if (address.isNotBlank()) {
                    putExtra(MainActivity.EXTRA_DEVICE_ADDRESS, address)
                }
                intent.getStringExtra("device_name")?.takeIf(String::isNotBlank)?.let {
                    putExtra(MainActivity.EXTRA_DEVICE_NAME, it)
                }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(
                resolveBluetoothDisplayName(
                    alias = intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_DISPLAY_NAME),
                    name = intent.getStringExtra("device_name"),
                    fallback = route?.displayName ?: context.getString(R.string.coloros_live_alert_fallback_title),
                ),
            )
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(colorOsLiveAlertVisibility(ConfigManager.lockscreenNotificationEnabled()))
            .apply { largeIcon?.let(::setLargeIcon) }
            .apply {
                // 支持低时延的型号在卡片上提供一键切换；状态取模块记录的最近一次写入结果。
                if (route == null || !route.supportsLowLatencyControl || address.isBlank()) return@apply
                val lowLatency = LowLatencyPrefs.desiredOrNull(prefs, address, route) ?: false
                val action = ColorOsLiveAlertActionReceiver.toggleLowLatencyIntent(
                    context,
                    address,
                    route,
                    enabled = !lowLatency,
                ) ?: return@apply
                addAction(
                    0,
                    context.getString(
                        if (lowLatency) R.string.coloros_live_alert_low_latency_off
                        else R.string.coloros_live_alert_low_latency_on,
                    ),
                    action,
                )
                setSubText(
                    context.getString(
                        if (lowLatency) R.string.coloros_live_alert_low_latency_state_on
                        else R.string.coloros_live_alert_low_latency_state_off,
                    ),
                )
            }
            .requestColorOsLiveAlert(colorOsLiveAlertChipText(left, right, case))
            .build()
        if (Build.VERSION.SDK_INT >= 36) {
            Log.d(
                TAG,
                "ColorOS Live Alert eligibility " +
                    "promotable=${notification.hasPromotableCharacteristics()} " +
                    "allowed=${manager.canPostPromotedNotifications()}",
            )
        }
        val serviceIntent = Intent(context, ColorOsLiveAlertService::class.java).putExtra(
            ColorOsLiveAlertService.EXTRA_NOTIFICATION,
            notification,
        ).putExtra(ColorOsLiveAlertService.EXTRA_DEVICE_ADDRESS, address)
        runCatching { context.startForegroundService(serviceIntent) }
            .onFailure {
                manager.notify(NOTIFICATION_ID, notification)
                Log.w(TAG, "Foreground keep-alive unavailable; posted notification directly", it)
            }
        Log.d(TAG, "ColorOS Live Alert posted")
        } catch (error: Exception) {
            succeeded = false
            throw error
        } finally {
            if (!completionPending) onComplete?.invoke(succeeded)
        }
    }

    private fun Intent.batteryParams(): BatteryParams {
        return getParcelableExtra("status", BatteryParams::class.java) ?: BatteryParams(
            left = batteryPod("left"),
            right = batteryPod("right"),
            case = batteryPod("case"),
        )
    }

    private fun Intent.batteryPod(prefix: String) =
        moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams(
            battery = getIntExtra("${prefix}_battery", 0),
            isCharging = getBooleanExtra("${prefix}_charging", false),
            isConnected = getBooleanExtra("${prefix}_connected", false),
        )

    private fun confirmDisconnected(
        context: Context,
        manager: NotificationManager,
        statePrefs: android.content.SharedPreferences,
        eventAddress: String?,
        eventElapsed: Long,
        onComplete: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (!ColorOsLiveAlertConnectionPolicy.matchesDevice(
                statePrefs.getString(KEY_CURRENT_ADDRESS, null), eventAddress,
            )
        ) return false
        val address = eventAddress ?: return false
        if (runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == false }
                .getOrDefault(false)) {
            cancelLiveAlert(context, manager, statePrefs, eventElapsed)
            return false
        }
        val pendingResult = if (fromBoundBridge) null else goAsync()
        queryColorOsLiveAlertAudioProfiles(context, address) { a2dpState, headsetState ->
            var succeeded = true
            try {
                // A new headset may have become current while the Profile proxies were binding.
                if (ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                        statePrefs.getString(KEY_CURRENT_ADDRESS, null), address, a2dpState, headsetState,
                    )
                ) {
                    cancelLiveAlert(context, manager, statePrefs, eventElapsed)
                    Log.d(TAG, "ColorOS Live Alert cancelled: both audio Profiles disconnected")
                }
            } catch (error: Exception) {
                succeeded = false
                Log.w(TAG, "Live Alert disconnect cleanup failed", error)
            } finally {
                pendingResult?.finish()
                onComplete?.invoke(succeeded)
            }
        }
        return true
    }

    private fun cancelLiveAlert(
        context: Context,
        manager: NotificationManager,
        statePrefs: android.content.SharedPreferences,
        eventElapsed: Long,
    ) {
        statePrefs.edit()
            .putLong(
                KEY_LAST_DISCONNECT_ELAPSED,
                eventElapsed.takeIf { it > 0L } ?: SystemClock.elapsedRealtime(),
            )
            .remove(KEY_CURRENT_ADDRESS)
            .apply()
        context.stopService(Intent(context, ColorOsLiveAlertService::class.java))
        manager.cancel(NOTIFICATION_ID)
        markColorOsLiveAlertDisconnected(context, SystemClock.elapsedRealtime())
        ColorOsLiveAlertBridgeService.reportClosed()
    }

    private companion object {
        const val TAG = "HuaweiPods-LiveAlert"
        const val CHANNEL_ID = "HuaweiPodsLiveAlert"
        const val STATE_PREFS = "coloros_live_alert_state"
        const val KEY_LAST_DISCONNECT_ELAPSED = "last_disconnect_elapsed"
        const val KEY_CURRENT_ADDRESS = "current_address"
        const val NOTIFICATION_ID = ColorOsLiveAlertService.NOTIFICATION_ID
        const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
        val TRUSTED_BLUETOOTH_ACTIONS = setOf(
            HuaweiPodsAction.ACTION_PODS_CONNECTED,
            HuaweiPodsAction.ACTION_PODS_DISCONNECTED,
            HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED,
            HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED,
        )
    }
}
