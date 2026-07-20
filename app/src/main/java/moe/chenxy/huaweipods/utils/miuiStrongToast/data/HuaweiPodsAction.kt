package moe.chenxy.huaweipods.utils.miuiStrongToast.data

import android.content.IntentFilter

object HuaweiPodsAction {
    private const val PREFIX = "chen.action.huaweipods"

    const val ACTION_SHOW_PODS_UI = PREFIX + ".show_pods_ui"
    const val ACTION_SEND_STRONG_TOAST = PREFIX + ".sendstrongtoast"
    const val ACTION_UPDATE_PODS_NOTIFICATION = PREFIX + ".updatepodsnotification"
    const val ACTION_CANCEL_PODS_NOTIFICATION = PREFIX + ".cancelpodsnotification"

    const val ACTION_PODS_UI_INIT = PREFIX + ".ui_init"
    const val ACTION_PODS_UI_CLOSED = PREFIX + ".ui_closed"
    const val ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE = PREFIX + ".module_bluetooth_service_alive"
    const val ACTION_PODS_CONNECTED = PREFIX + ".pods_connected"
    const val ACTION_PODS_DISCONNECTED = PREFIX + ".pods_disconnected"
    const val ACTION_CONNECT_POD_REQUEST = PREFIX + ".connect_pod_request"
    const val ACTION_DISCONNECT_POD_REQUEST = PREFIX + ".disconnect_pod_request"
    const val ACTION_PODS_CONNECTION_STATE_CHANGED = PREFIX + ".pods_connection_state_changed"
    const val ACTION_PODS_BATTERY_CHANGED = PREFIX + ".pods_battery_changed"
    const val ACTION_ANC_SELECT = PREFIX + ".anc_select"
    const val ACTION_PODS_ANC_CHANGED = PREFIX + ".pods_anc_select"
    const val ACTION_HUAWEI_ANC_LEVEL_SET = PREFIX + ".huawei_anc_level_set"
    const val ACTION_HUAWEI_ANC_LEVEL_CHANGED = PREFIX + ".huawei_anc_level_changed"
    const val ACTION_GET_PODS_MAC = PREFIX + ".get_pods_mac"
    const val ACTION_PODS_MAC_RECEIVED = PREFIX + ".get_pods_mac"
    const val ACTION_REFRESH_STATUS = PREFIX + ".refresh_status"
    const val ACTION_CYCLE_ANC = PREFIX + ".cycle_anc"
    const val ACTION_RFCOMM_LOG = PREFIX + ".rfcomm_log"
    const val ACTION_HUAWEI_LEGACY_DEBUG_SEND = PREFIX + ".huawei_legacy_debug_send"
    const val ACTION_HUAWEI_GESTURE_SET = PREFIX + ".huawei_gesture_set"
    const val ACTION_CONFIG_CHANGED = PREFIX + ".config_changed"

    fun canonical(action: String?): String? = action

    fun matches(action: String?, expected: String): Boolean = canonical(action) == expected
}

fun IntentFilter.addHuaweiPodsAction(action: String): IntentFilter = apply {
    addAction(action)
}

fun IntentFilter.addHuaweiPodsActions(vararg actions: String): IntentFilter = apply {
    actions.forEach { addHuaweiPodsAction(it) }
}
