package moe.chenxy.huaweipods.utils.miuiStrongToast.data

import android.content.IntentFilter

object HuaweiPodsAction {
    private const val PREFIX = "chen.action.huaweipods"

    const val EXTRA_DEVICE_ROUTE = "device_route"
    const val EXTRA_FREECLIP2_AUDIO_KIND = "freeclip2_audio_kind"
    const val EXTRA_FREECLIP2_AUDIO_VALUE = "freeclip2_audio_value"
    const val EXTRA_FREECLIP2_SPATIAL_MODE = "spatial_mode"
    const val EXTRA_FREECLIP2_SPATIAL_SCENE = "spatial_scene"
    const val EXTRA_FREECLIP2_SOUND_EFFECT = "sound_effect"
    const val EXTRA_FREECLIP2_AUDIO_CONFIRMED = "freeclip2_audio_confirmed"
    const val EXTRA_HUAWEI_EQUALIZER_SELECTED_ID = "huawei_equalizer_selected_id"
    const val EXTRA_HUAWEI_EQUALIZER_CONFIRMED = "huawei_equalizer_confirmed"
    const val EXTRA_HUAWEI_LOW_LATENCY_ENABLED = "huawei_low_latency_enabled"
    const val EXTRA_HUAWEI_LOW_LATENCY_WRITE_SUCCESS = "huawei_low_latency_write_success"
    const val EXTRA_FREECLIP2_BRIDGE_NONCE = "freeclip2_bridge_nonce"
    const val EXTRA_FREECLIP2_BRIDGE_ADDRESS = "freeclip2_bridge_address"
    const val EXTRA_FREECLIP2_BRIDGE_MODE = "freeclip2_bridge_mode"
    const val EXTRA_FREECLIP2_BRIDGE_ACCEPTED = "freeclip2_bridge_accepted"
    const val EXTRA_FREECLIP2_BRIDGE_CONFIRMED_MODE = "freeclip2_bridge_confirmed_mode"
    const val EXTRA_FREECLIP2_BRIDGE_OFFICIAL_MODE = "freeclip2_bridge_official_mode"
    const val EXTRA_FREECLIP2_BRIDGE_OFFICIAL_EFFECT = "freeclip2_bridge_official_effect"
    const val EXTRA_FREECLIP2_BRIDGE_EQ_SUPPORTED = "freeclip2_bridge_eq_supported"
    const val EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID = "freeclip2_bridge_eq_selected_id"
    const val EXTRA_FREECLIP2_BRIDGE_EQ_NAME = "freeclip2_bridge_eq_name"
    const val EXTRA_FREECLIP2_BRIDGE_EQ_GAINS = "freeclip2_bridge_eq_gains"
    const val EXTRA_FREECLIP2_BRIDGE_EQ_PRESET_ID = "freeclip2_bridge_eq_preset_id"
    const val EXTRA_MODULE_BUILD_ID = "module_build_id"
    const val EXTRA_RESTORE_NOTIFICATION = "restore_notification"
    const val EXTRA_ROUTE_PROBE_ADDRESS = "route_probe_address"
    const val EXTRA_ROUTE_PROBE_GENERATION = "route_probe_generation"
    const val EXTRA_ROUTE_PROBE_NONCE = "route_probe_nonce"
    const val EXTRA_ROUTE_PROBE_MODEL_ID = "route_probe_model_id"
    const val EXTRA_ROUTE_PROBE_SUB_MODEL_ID = "route_probe_sub_model_id"

    const val FREECLIP2_AUDIO_KIND_SPATIAL_MODE = "spatial_mode"
    const val FREECLIP2_AUDIO_KIND_SPATIAL_SCENE = "spatial_scene"
    const val FREECLIP2_AUDIO_KIND_SOUND_EFFECT = "sound_effect"

    const val ACTION_SHOW_PODS_UI = PREFIX + ".show_pods_ui"
    const val ACTION_SEND_STRONG_TOAST = PREFIX + ".sendstrongtoast"
    const val ACTION_UPDATE_PODS_NOTIFICATION = PREFIX + ".updatepodsnotification"
    const val ACTION_CANCEL_PODS_NOTIFICATION = PREFIX + ".cancelpodsnotification"

    const val ACTION_PODS_UI_INIT = PREFIX + ".ui_init"
    const val ACTION_PODS_UI_CLOSED = PREFIX + ".ui_closed"
    const val ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE = PREFIX + ".module_bluetooth_service_alive"
    const val ACTION_MODULE_MI_BLUETOOTH_SERVICE_ALIVE =
        PREFIX + ".module_mi_bluetooth_service_alive"
    const val ACTION_PODS_CONNECTED = PREFIX + ".pods_connected"
    const val ACTION_PODS_DISCONNECTED = PREFIX + ".pods_disconnected"
    const val ACTION_CONNECT_POD_REQUEST = PREFIX + ".connect_pod_request"
    const val ACTION_DEVICE_ROUTE_PROBE_REQUEST = PREFIX + ".device_route_probe_request"
    const val ACTION_DEVICE_ROUTE_PROBE_RESULT = PREFIX + ".device_route_probe_result"
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
    const val ACTION_HUAWEI_GESTURE_REFRESH = PREFIX + ".huawei_gesture_refresh"
    const val ACTION_HUAWEI_GESTURE_CHANGED = PREFIX + ".huawei_gesture_changed"
    const val ACTION_FREECLIP2_AUDIO_SET = PREFIX + ".freeclip2_audio_set"
    const val ACTION_FREECLIP2_AUDIO_REFRESH = PREFIX + ".freeclip2_audio_refresh"
    const val ACTION_FREECLIP2_AUDIO_CHANGED = PREFIX + ".freeclip2_audio_changed"
    const val ACTION_HUAWEI_EQUALIZER_PRESET_SET = PREFIX + ".huawei_equalizer_preset_set"
    const val ACTION_HUAWEI_EQUALIZER_REFRESH = PREFIX + ".huawei_equalizer_refresh"
    const val ACTION_HUAWEI_EQUALIZER_CHANGED = PREFIX + ".huawei_equalizer_changed"
    const val ACTION_HUAWEI_LOW_LATENCY_SET = PREFIX + ".huawei_low_latency_set"
    const val ACTION_HUAWEI_LOW_LATENCY_CHANGED = PREFIX + ".huawei_low_latency_changed"
    const val ACTION_SMART_AUDIO_FREECLIP2_SET = PREFIX + ".smart_audio_freeclip2_set"
    const val ACTION_SMART_AUDIO_FREECLIP2_RESULT = PREFIX + ".smart_audio_freeclip2_result"
    const val ACTION_SMART_AUDIO_FREECLIP2_STATE = PREFIX + ".smart_audio_freeclip2_state"
    const val ACTION_SMART_AUDIO_FREECLIP2_QUERY = PREFIX + ".smart_audio_freeclip2_query"
    const val ACTION_SMART_AUDIO_FREECLIP2_QUERY_RESULT =
        PREFIX + ".smart_audio_freeclip2_query_result"
    const val ACTION_SMART_AUDIO_FREECLIP2_EQ_SET = PREFIX + ".smart_audio_freeclip2_eq_set"
    const val ACTION_SMART_AUDIO_FREECLIP2_EQ_RESULT = PREFIX + ".smart_audio_freeclip2_eq_result"
    const val ACTION_SMART_AUDIO_IMAGE_PROVIDER_READY =
        PREFIX + ".smart_audio_image_provider_ready"
    const val ACTION_POD_IMAGES_CHANGED = PREFIX + ".pod_images_changed"
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
