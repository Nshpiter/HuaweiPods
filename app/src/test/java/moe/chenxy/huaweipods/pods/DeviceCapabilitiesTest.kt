package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `safe route only accepts verified FreeBuds 3 names`() {
        val cases = listOf(
            "HUAWEI FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "FreeBuds 5" to HuaweiDeviceRoute.UNSUPPORTED,
            "FreeClip 2" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI FreeClip 2" to HuaweiDeviceRoute.UNSUPPORTED,
            "OPPO Enco" to HuaweiDeviceRoute.UNSUPPORTED,
            "OPPO Enco X3" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI WATCH" to HuaweiDeviceRoute.UNSUPPORTED,
            "HUAWEI WATCH GT" to HuaweiDeviceRoute.UNSUPPORTED,
            "" to HuaweiDeviceRoute.UNSUPPORTED,
            "   " to HuaweiDeviceRoute.UNSUPPORTED,
            "My custom freebuds3 headset" to HuaweiDeviceRoute.UNSUPPORTED,
        )

        cases.forEach { (deviceName, expectedRoute) ->
            assertEquals(deviceName, expectedRoute, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals("null", HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(null))
    }
}
