package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothUpstreamBatteryPolicyTest {
    @Test
    fun `maps the Pro 5 out-of-case side to the Xiaomi wear state`() {
        val battery = BatteryParams(
            left = PodParams(battery = 80, isConnected = true),
            right = PodParams(battery = 70, isConnected = false),
        )

        assertEquals(
            3,
            upstreamHuaweiWearState(
                battery = battery,
                fallback = 1,
                hasReportedAvailability = true,
            ),
        )
    }

    @Test
    fun `reports no worn earbuds after both Pro 5 earbuds return to the case`() {
        val battery = BatteryParams(
            left = PodParams(battery = 80, isConnected = false),
            right = PodParams(battery = 70, isConnected = false),
        )

        assertEquals(
            0,
            upstreamHuaweiWearState(
                battery = battery,
                fallback = 1,
                hasReportedAvailability = true,
            ),
        )
    }

    @Test
    fun `keeps the host fallback for routes without verified availability`() {
        assertEquals(
            2,
            upstreamHuaweiWearState(
                battery = BatteryParams(),
                fallback = 2,
                hasReportedAvailability = false,
            ),
        )
    }
}
