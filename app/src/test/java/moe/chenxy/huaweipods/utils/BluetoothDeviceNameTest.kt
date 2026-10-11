package moe.chenxy.huaweipods.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothDeviceNameTest {
    @Test
    fun `custom Bluetooth name preserves Chinese emoji and internal spaces`() {
        assertEquals("我的 🎧  耳机", resolveBluetoothDisplayName(" 我的 🎧  耳机 ", "HUAWEI FreeBuds 6i"))
    }

    @Test
    fun `missing or blank aliases preserve original model name`() {
        for (alias in listOf(null, "", " \t\n")) {
            assertEquals("HUAWEI FreeBuds 6i", resolveBluetoothDisplayName(alias, "HUAWEI FreeBuds 6i"))
        }
    }

    @Test
    fun `unavailable Bluetooth names have a nonempty notification title`() {
        assertEquals("HuaweiPods", resolveBluetoothDisplayName(null, null))
        assertEquals("HuaweiPods", resolveBluetoothDisplayName(" ", "\t"))
    }
}
