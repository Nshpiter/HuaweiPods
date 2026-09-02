package moe.chenxy.huaweipods.ui.pages

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.enabledHuaweiDeviceRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceModelPickerPolicyTest {
    @Test
    fun `blank query keeps the supported model order`() {
        val routes = enabledHuaweiDeviceRoutes()

        assertEquals(routes, filterDeviceModelRoutes(routes, ""))
        assertEquals(routes, filterDeviceModelRoutes(routes, "   "))
    }

    @Test
    fun `search ignores spaces and case`() {
        val routes = enabledHuaweiDeviceRoutes()

        assertEquals(
            listOf(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5),
            filterDeviceModelRoutes(routes, "pro 5"),
        )
        assertEquals(
            listOf(HuaweiDeviceRoute.HUAWEI_FREECLIP2),
            filterDeviceModelRoutes(routes, "FREECLIP2"),
        )
    }

    @Test
    fun `unmatched query returns an empty list`() {
        assertTrue(
            filterDeviceModelRoutes(enabledHuaweiDeviceRoutes(), "not-a-model").isEmpty(),
        )
    }

    @Test
    fun `compact name removes the common Huawei prefix`() {
        assertEquals(
            "FreeBuds Pro 5",
            compactDeviceModelName(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5),
        )
    }
}
