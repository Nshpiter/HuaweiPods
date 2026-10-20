package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsNativeEarbudPolicyTest {
    private val worn = PodParams(battery = 70, isConnected = true)
    private val charging = PodParams(battery = 70, isCharging = true, isConnected = true)

    @Test
    fun `in-ear report separates wearing from out of ear`() {
        assertEquals(COLOR_OS_WEAR_WEARING, colorOsEarbudWearStatus(worn, inEar = true))
        assertEquals(COLOR_OS_WEAR_NOT_WEARING, colorOsEarbudWearStatus(worn, inEar = false))
        assertEquals(COLOR_OS_WEAR_WEARING, colorOsEarbudWearStatus(worn, inEar = null))
    }

    @Test
    fun `charging earbuds stay in case regardless of in-ear report`() {
        assertEquals(COLOR_OS_WEAR_IN_CASE, colorOsEarbudWearStatus(charging, inEar = true))
        assertEquals(COLOR_OS_WEAR_IN_CASE, colorOsEarbudWearStatus(charging, inEar = false))
        assertEquals(COLOR_OS_WEAR_UNKNOWN, colorOsEarbudWearStatus(null, inEar = true))
    }

    @Test
    fun `noise control options follow model capabilities`() {
        assertEquals(
            listOf(NoiseControlMode.OFF, NoiseControlMode.NOISE_CANCELLATION, NoiseControlMode.TRANSPARENCY),
            colorOsNoiseControlOptions(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5),
        )
        assertEquals(
            listOf(NoiseControlMode.OFF, NoiseControlMode.NOISE_CANCELLATION),
            colorOsNoiseControlOptions(HuaweiDeviceRoute.HUAWEI_FREEBUDS3),
        )
        assertTrue(colorOsNoiseControlOptions(HuaweiDeviceRoute.HUAWEI_FREECLIP).isEmpty())
    }

    @Test
    fun `low battery reminder fires once per tier until recovered`() {
        val tracker = ColorOsLowBatteryTracker()
        fun left(level: Int) = BatteryParams(left = PodParams(battery = level, isConnected = true), right = worn)

        assertTrue(tracker.shouldNotify("A", left(18)))
        assertFalse(tracker.shouldNotify("A", left(15)))
        assertFalse(tracker.shouldNotify("A", left(21).copy(right = PodParams(battery = 19, isConnected = true))))
        assertTrue(tracker.shouldNotify("A", left(9)))
        assertFalse(tracker.shouldNotify("A", left(11)))
        assertFalse(tracker.shouldNotify("A", left(8)))
        assertFalse(tracker.shouldNotify("A", left(60)))
        assertTrue(tracker.shouldNotify("A", left(18)))
        assertEquals(listOf(true to 18), tracker.lowEarbuds(left(18)))
    }

    @Test
    fun `dropping straight below ten percent notifies once`() {
        val tracker = ColorOsLowBatteryTracker()
        val battery = BatteryParams(left = PodParams(battery = 7, isConnected = true))

        assertTrue(tracker.shouldNotify("A", battery))
        assertFalse(tracker.shouldNotify("A", battery))
    }

    @Test
    fun `charging or disconnected earbuds never trigger a low battery reminder`() {
        val tracker = ColorOsLowBatteryTracker()
        val battery = BatteryParams(
            left = PodParams(battery = 10, isCharging = true, isConnected = true),
            right = PodParams(battery = 5, isConnected = false),
        )

        assertFalse(tracker.shouldNotify("A", battery))
    }

    @Test
    fun `settings vendor allowlist mirrors the system property format`() {
        assertEquals(COLOR_OS_SETTINGS_DEFAULT_3RD_VENDORS, colorOsSettingsThirdPartyVendors(null))
        assertEquals(COLOR_OS_SETTINGS_DEFAULT_3RD_VENDORS, colorOsSettingsThirdPartyVendors("  "))
        assertEquals(listOf(0x0A80, 0x054C), colorOsSettingsThirdPartyVendors("0A80, 054c,zz"))
    }

    @Test
    fun `quick toggles only list verified controls`() {
        assertEquals(
            listOf(ColorOsQuickToggle.LOW_LATENCY, ColorOsQuickToggle.WEAR_DETECTION),
            colorOsQuickToggles(HuaweiDeviceRoute.HUAWEI_FREECLIP),
        )
        assertTrue(colorOsQuickToggles(HuaweiDeviceRoute.HUAWEI_FREEBUDS3).isEmpty())
        assertTrue(colorOsQuickToggles(HuaweiDeviceRoute.HUAWEI_FREEARC).isEmpty())
    }

    @Test
    fun `quick toggle label shows state or pending readback`() {
        assertEquals("佩戴检测 · 已开启", colorOsQuickToggleLabel(ColorOsQuickToggle.WEAR_DETECTION, true, chinese = true))
        assertEquals("低时延 · 已关闭", colorOsQuickToggleLabel(ColorOsQuickToggle.LOW_LATENCY, false, chinese = true))
        assertEquals("佩戴检测 · 读取中", colorOsQuickToggleLabel(ColorOsQuickToggle.WEAR_DETECTION, null, chinese = true))
        assertEquals("Wear detection · On", colorOsQuickToggleLabel(ColorOsQuickToggle.WEAR_DETECTION, true, chinese = false))
    }

    @Test
    fun `connection card battery text lists connected parts with charging marks`() {
        val battery = BatteryParams(
            left = PodParams(battery = 80, isConnected = true),
            right = PodParams(battery = 78, isCharging = true, isConnected = true),
            case = PodParams(battery = 60, isConnected = true),
        )
        assertEquals("左耳 80% · 右耳 78%⚡ · 耳机盒 60%", colorOsPopupBatteryText(battery, chinese = true))
        assertEquals(
            "L 80%",
            colorOsPopupBatteryText(BatteryParams(left = PodParams(battery = 80, isConnected = true)), chinese = false),
        )
    }

    @Test
    fun `connection card only refreshes shortly after it was shown`() {
        assertTrue(shouldRefreshColorOsConnectionCard(shownAtElapsed = 1_000L, nowElapsed = 20_000L))
        assertFalse(shouldRefreshColorOsConnectionCard(shownAtElapsed = 1_000L, nowElapsed = 1_000L + COLOR_OS_CONNECTION_CARD_LIVE_WINDOW_MS + 1))
        assertFalse(shouldRefreshColorOsConnectionCard(shownAtElapsed = 5_000L, nowElapsed = 1_000L))
    }
}
