package moe.chenxy.huaweipods.broadcast

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/**
 * 广播动作领域划分。
 */
enum class BroadcastActionDomain {
    /** 模块或系统设置等发起对耳机的控制指令 */
    APP_CONTROL,
    /** 蓝牙底层状态与心跳上报广播 */
    BLUETOOTH_STATE,
    /** 华为智慧音频组件相关广播 */
    SMART_AUDIO,
    /** 应用内部配置与图片变更通知 */
    INTERNAL,
    /** Android 系统原生广播（如蓝牙状态、绑定状态） */
    SYSTEM,
    /** 未知或未声明的 Action（一律拦截） */
    UNKNOWN,
}

/**
 * 跨进程广播信任策略。
 *
 * HuaweiPods 需要在 App、Bluetooth 宿主进程 (com.android.bluetooth)、我的设备 (com.heytap.mydevices)、
 * 快速设备连接 (com.heytap.accessory) 和智慧音频 (com.huawei.smartaudio) 之间传递状态与控制指令。
 * 所有跨进程接收端均采用严格 fail-closed 原则按 Action Domain 校验发送方包名。
 */
object HuaweiPodsBroadcastTrustPolicy {
    const val PACKAGE_APP = "moe.chenxy.huaweipods.coloros"
    const val PACKAGE_BLUETOOTH = "com.android.bluetooth"
    const val PACKAGE_SMART_AUDIO = "com.huawei.smartaudio"
    const val PACKAGE_HEYTAP_ACCESSORY = "com.heytap.accessory"
    const val PACKAGE_HEYTAP_MYDEVICES = "com.heytap.mydevices"

    /** 受信任的控制广播发送方（包括应用自身及被 Hook 的 ColorOS 系统组件） */
    private val trustedAppControlSenders = setOf(
        PACKAGE_APP,
        PACKAGE_BLUETOOTH,
        PACKAGE_HEYTAP_ACCESSORY,
        PACKAGE_HEYTAP_MYDEVICES,
    )

    /** 受信任的蓝牙底层状态广播发送方（由系统蓝牙进程发往各 UI 与宿主） */
    private val trustedBluetoothStateSenders = setOf(
        PACKAGE_BLUETOOTH,
    )

    private val myDevicesStateActions = setOf(
        HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED,
        HuaweiPodsAction.ACTION_PODS_ANC_CHANGED,
        HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED,
        HuaweiPodsAction.ACTION_HUAWEI_WEAR_DETECTION_CHANGED,
    )

    /** 判断是否为合法的控制类广播发送者 */
    fun isTrustedAppControlSender(packageName: String?): Boolean =
        packageName != null && packageName in trustedAppControlSenders

    /** 判断是否为合法的蓝牙状态类广播发送者 */
    fun isTrustedBluetoothStateSender(packageName: String?): Boolean =
        packageName != null && packageName in trustedBluetoothStateSenders

    /** Packages must come from PackageManager for the kernel-provided Messenger sendingUid. */
    internal fun shouldAcceptBoundHeadsetEvent(action: String?, uidPackages: Array<String>?): Boolean =
        isHeadsetLifecycleAction(action) && uidPackages?.any(::isTrustedBluetoothStateSender) == true

    /** 我的设备只消费蓝牙进程确认的四类状态，不接受控制动作或其他宿主伪造的状态。 */
    fun shouldAcceptMyDevicesStateBroadcast(action: String?, senderPackage: String?): Boolean =
        HuaweiPodsAction.canonical(action) in myDevicesStateActions &&
            isTrustedBluetoothStateSender(senderPackage)

    /** 判断是否为合法的智慧音频发送者 */
    fun isTrustedSmartAudioSender(packageName: String?): Boolean =
        packageName == PACKAGE_SMART_AUDIO

    /** 判断是否为应用内部变更发送者 */
    fun isTrustedInternalSender(packageName: String?): Boolean =
        packageName == PACKAGE_APP

    /**
     * 解析广播 Action 对应的安全域。
     */
    fun resolveDomain(action: String): BroadcastActionDomain {
        val canonical = HuaweiPodsAction.canonical(action) ?: return BroadcastActionDomain.UNKNOWN
        return when (canonical) {
            // 控制类 Action
            HuaweiPodsAction.ACTION_CONNECT_POD_REQUEST,
            HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_REQUEST,
            HuaweiPodsAction.ACTION_PODS_UI_INIT,
            HuaweiPodsAction.ACTION_PODS_UI_CLOSED,
            HuaweiPodsAction.ACTION_REFRESH_STATUS,
            HuaweiPodsAction.ACTION_ANC_SELECT,
            HuaweiPodsAction.ACTION_CYCLE_ANC,
            HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_SET,
            HuaweiPodsAction.ACTION_HUAWEI_ANC_REFRESH,
            HuaweiPodsAction.ACTION_HUAWEI_SPATIAL_SET,
            HuaweiPodsAction.ACTION_HUAWEI_SPATIAL_REFRESH,
            HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_SET,
            HuaweiPodsAction.ACTION_SHARED_CONTROL_REQUEST,
            HuaweiPodsAction.ACTION_HUAWEI_WEAR_DETECTION_SET,
            HuaweiPodsAction.ACTION_HUAWEI_WEAR_DETECTION_REFRESH,
            HuaweiPodsAction.ACTION_HUAWEI_GESTURE_SET,
            HuaweiPodsAction.ACTION_HUAWEI_GESTURE_REFRESH,
            HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET,
            HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH,
            HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_PRESET_SET,
            HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_REFRESH,
            HuaweiPodsAction.ACTION_HUAWEI_LEGACY_DEBUG_SEND,
            HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_PROBE,
            HuaweiPodsAction.ACTION_PREVIEW_LOW_BATTERY_REMINDER,
            HuaweiPodsAction.ACTION_GET_PODS_MAC ->
                BroadcastActionDomain.APP_CONTROL

            // 蓝牙底层状态上报 Action
            HuaweiPodsAction.ACTION_PODS_CONNECTED,
            HuaweiPodsAction.ACTION_PODS_DISCONNECTED,
            HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED,
            HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED,
            HuaweiPodsAction.ACTION_PODS_ANC_CHANGED,
            HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED,
            HuaweiPodsAction.ACTION_HUAWEI_SPATIAL_CHANGED,
            HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED,
            HuaweiPodsAction.ACTION_SHARED_CONTROL_RESULT,
            HuaweiPodsAction.ACTION_HUAWEI_WEAR_DETECTION_CHANGED,
            HuaweiPodsAction.ACTION_HUAWEI_GESTURE_CHANGED,
            HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED,
            HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_CHANGED,
            HuaweiPodsAction.ACTION_DEVICE_ROUTE_PROBE_RESULT,
            HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE,
            HuaweiPodsAction.ACTION_MODULE_MI_BLUETOOTH_SERVICE_ALIVE,
            HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_READY,
            HuaweiPodsAction.ACTION_PODS_MAC_RECEIVED,
            HuaweiPodsAction.ACTION_SHOW_PODS_UI,
            HuaweiPodsAction.ACTION_UPDATE_PODS_NOTIFICATION,
            HuaweiPodsAction.ACTION_CANCEL_PODS_NOTIFICATION ->
                BroadcastActionDomain.BLUETOOTH_STATE

            // 智慧音频组件 Action
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_SET,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_RESULT,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_STATE,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_QUERY,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_QUERY_RESULT,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET,
            HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_RESULT,
            HuaweiPodsAction.ACTION_SMART_AUDIO_IMAGE_PROVIDER_READY ->
                BroadcastActionDomain.SMART_AUDIO

            // 应用内部通知 Action
            HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED,
            HuaweiPodsAction.ACTION_CONFIG_CHANGED ->
                BroadcastActionDomain.INTERNAL

            // Android 系统原生蓝牙广播
            "android.bluetooth.device.action.BOND_STATE_CHANGED",
            "android.bluetooth.adapter.action.STATE_CHANGED",
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" ->
                BroadcastActionDomain.SYSTEM

            else -> {
                if (action.startsWith("android.bluetooth.")) {
                    BroadcastActionDomain.SYSTEM
                } else {
                    BroadcastActionDomain.UNKNOWN
                }
            }
        }
    }

    /**
     * 根据广播 Action 与发送者包名，综合评估是否信任该广播（fail-closed 机制）。
     */
    fun shouldAcceptBroadcast(action: String?, senderPackage: String?): Boolean {
        if (action.isNullOrBlank()) return false
        return when (resolveDomain(action)) {
            BroadcastActionDomain.APP_CONTROL -> isTrustedAppControlSender(senderPackage)
            BroadcastActionDomain.BLUETOOTH_STATE -> isTrustedBluetoothStateSender(senderPackage)
            BroadcastActionDomain.SMART_AUDIO -> isTrustedSmartAudioSender(senderPackage)
            BroadcastActionDomain.INTERNAL -> isTrustedInternalSender(senderPackage)
            BroadcastActionDomain.SYSTEM -> true
            BroadcastActionDomain.UNKNOWN -> false // 彻底 fail-closed，未分类默认拒绝
        }
    }
}
