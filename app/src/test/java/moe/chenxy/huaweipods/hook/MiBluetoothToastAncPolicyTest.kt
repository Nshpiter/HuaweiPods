package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiBluetoothToastAncPolicyTest {
    @Test
    fun `notification click target is isolated by bluetooth address`() {
        val first = headsetNotificationIntentIdentity("AA:BB:CC:DD:EE:01")
        val second = headsetNotificationIntentIdentity("AA:BB:CC:DD:EE:02")

        assertTrue(first != second)
    }

    @Test
    fun `notification exposes ANC only for verified ANC earbuds`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertTrue(route.name, shouldOfferNotificationAncAction(route))
        }
    }

    @Test
    fun `notification never exposes ANC for clips eyewear or unknown devices`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
            HuaweiDeviceRoute.UNSUPPORTED,
        ).forEach { route ->
            assertFalse(route.name, shouldOfferNotificationAncAction(route))
        }
    }
}
