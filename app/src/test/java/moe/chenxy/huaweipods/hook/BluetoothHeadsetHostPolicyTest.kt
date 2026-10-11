package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothHeadsetHostPolicyTest {
    @Test
    fun `OS2 requires both captured superclass and support signature`() {
        val os2 = resolveBluetoothHeadsetHostSpec("com.android.bluetooth.ble.app.K0", true)
        assertEquals("hyperos2-android14", os2.name)
        assertEquals("s1", os2.methodName("changeAncMode"))
        assertEquals("H0", os2.refreshMethodName)
        assertEquals("com.android.bluetooth.ble.app.J0", os2.callbackClassName)

        listOf(null, "android.os.Binder", "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub")
            .forEach { parent ->
                assertEquals("modern", resolveBluetoothHeadsetHostSpec(parent, true).name)
            }
        assertEquals("modern", resolveBluetoothHeadsetHostSpec("com.android.bluetooth.ble.app.K0", false).name)
    }

    @Test
    fun `modern host keeps original method and callback contracts`() {
        val modern = resolveBluetoothHeadsetHostSpec("com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub", false)
        listOf("checkSupport", "register", "changeAncMode", "changeAncLevel", "getDeviceInfo")
            .forEach { assertEquals(it, modern.methodName(it)) }
        assertEquals("refreshStatus", modern.refreshMethodName)
        assertEquals("com.android.bluetooth.ble.app.IMiuiHeadsetCallback", modern.callbackClassName)
    }
}
