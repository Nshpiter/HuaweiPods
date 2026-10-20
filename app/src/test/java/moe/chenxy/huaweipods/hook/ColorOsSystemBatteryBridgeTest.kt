package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOsSystemBatteryBridgeTest {
    @Test
    fun `maps earbuds and charging case into the OPLUS battery array`() {
        val battery = BatteryParams(
            left = PodParams(battery = 81, isCharging = true, isConnected = true),
            right = PodParams(battery = 76, isCharging = false, isConnected = true),
            case = PodParams(battery = 52, isCharging = false, isConnected = true),
        )

        assertArrayEquals(
            intArrayOf(81, 76, 52, COLOR_OS_CHARGE_CHARGING, COLOR_OS_CHARGE_NOT_CHARGING, COLOR_OS_CHARGE_NOT_CHARGING),
            colorOsTwsBatteryInfo(battery),
        )
    }

    @Test
    fun `absent components are unknown and marked disconnected`() {
        val battery = BatteryParams(
            left = PodParams(battery = 0, isConnected = false),
            right = PodParams(battery = 140, isConnected = true),
            case = null,
        )

        assertArrayEquals(
            intArrayOf(
                COLOR_OS_BATTERY_UNKNOWN,
                100,
                COLOR_OS_BATTERY_UNKNOWN,
                COLOR_OS_CHARGE_DISCONNECTED,
                COLOR_OS_CHARGE_NOT_CHARGING,
                COLOR_OS_CHARGE_DISCONNECTED,
            ),
            colorOsTwsBatteryInfo(battery),
        )
    }

    @Test
    fun `charging earbuds are reported in the case`() {
        assertEquals(COLOR_OS_WEAR_IN_CASE, colorOsEarbudWearStatus(PodParams(50, isCharging = true, isConnected = true)))
        assertEquals(COLOR_OS_WEAR_WEARING, colorOsEarbudWearStatus(PodParams(50, isCharging = false, isConnected = true)))
        assertEquals(COLOR_OS_WEAR_UNKNOWN, colorOsEarbudWearStatus(PodParams(0, isConnected = false)))
        assertEquals(COLOR_OS_WEAR_UNKNOWN, colorOsEarbudWearStatus(null))
    }

    @Test
    fun `system battery uses the lower connected earbud and ignores the case`() {
        assertEquals(
            64,
            colorOsSystemBatteryLevel(
                BatteryParams(
                    left = PodParams(90, isConnected = true),
                    right = PodParams(64, isConnected = true),
                    case = PodParams(10, isConnected = true),
                ),
            ),
        )
        assertEquals(
            90,
            colorOsSystemBatteryLevel(
                BatteryParams(
                    left = PodParams(90, isConnected = true),
                    right = PodParams(0, isConnected = false),
                ),
            ),
        )
        assertEquals(
            COLOR_OS_BATTERY_UNKNOWN,
            colorOsSystemBatteryLevel(BatteryParams(case = PodParams(40, isConnected = true))),
        )
    }
}
