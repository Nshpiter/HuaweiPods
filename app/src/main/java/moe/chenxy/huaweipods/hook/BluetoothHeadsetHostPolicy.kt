package moe.chenxy.huaweipods.hook

internal data class BluetoothHeadsetHostSpec(
    val name: String,
    val callbackClassName: String,
    val refreshMethodName: String,
    val methodNames: Map<String, String> = emptyMap(),
) {
    fun methodName(logicalName: String): String = methodNames[logicalName] ?: logicalName
}

private val modernHeadsetHost = BluetoothHeadsetHostSpec(
    name = "modern",
    callbackClassName = "com.android.bluetooth.ble.app.IMiuiHeadsetCallback",
    refreshMethodName = "refreshStatus",
)

// 依据 OS 2 / Android 14 扩展包的 K0.onTransact 和 m 实现核对，不能仅按参数数量猜混淆方法。
private val os2HeadsetHost = BluetoothHeadsetHostSpec(
    name = "hyperos2-android14",
    callbackClassName = "com.android.bluetooth.ble.app.J0",
    refreshMethodName = "H0",
    methodNames = mapOf(
        "checkSupport" to "K0",
        "getDeviceInfo" to "c1",
        "isMiTWS" to "U",
        "checkIsMiTWS" to "T",
        "setCommonCommand" to "L0",
        "connect" to "G0",
        "getDeviceConfig" to "q",
        "getCommonConfig" to "z",
        "changeAncMode" to "s1",
        "changeAncLevel" to "J0",
        "register" to "g1",
        "registerCallbackDevice" to "A",
        "unregister" to "s",
    ),
)

internal fun resolveBluetoothHeadsetHostSpec(
    binderSuperclassName: String?,
    hasOs2CheckSupport: Boolean,
): BluetoothHeadsetHostSpec =
    if (binderSuperclassName == "com.android.bluetooth.ble.app.K0" && hasOs2CheckSupport) {
        os2HeadsetHost
    } else {
        modernHeadsetHost
    }
