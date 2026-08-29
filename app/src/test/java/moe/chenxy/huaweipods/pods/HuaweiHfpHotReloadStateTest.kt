package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiHfpHotReloadStateTest {
    @Test
    fun `battery state survives primitive hot reload encoding`() {
        val battery = BatteryParams(
            left = PodParams(85, isCharging = true, isConnected = true, rawStatus = 3),
            right = PodParams(100, isCharging = false, isConnected = true, rawStatus = 1),
            case = PodParams(80, isCharging = false, isConnected = false, rawStatus = 0),
        )

        assertEquals(battery, decodeHotReloadBattery(encodeHotReloadBattery(battery)))
    }

    @Test
    fun `null pods survive encoding and malformed state is rejected`() {
        val battery = BatteryParams(
            left = PodParams(42, isConnected = true),
            right = null,
            case = null,
        )

        assertEquals(battery, decodeHotReloadBattery(encodeHotReloadBattery(battery)))
        assertNull(decodeHotReloadBattery(intArrayOf(1, 2, 3)))
    }
}
