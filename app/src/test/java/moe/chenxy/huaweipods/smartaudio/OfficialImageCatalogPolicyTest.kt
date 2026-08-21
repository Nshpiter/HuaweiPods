package moe.chenxy.huaweipods.smartaudio

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialImageCatalogPolicyTest {
    @Test
    fun `maps only routes backed by verified official model ids`() {
        val expected = mapOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to "000027",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to "000135",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5 to "000141",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to "000145",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to "000153",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 to "000149",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to "00016D",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to "000163",
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 to "000167",
            HuaweiDeviceRoute.HUAWEI_FREEARC to "00015D",
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to "00014F",
        )

        HuaweiDeviceRoute.entries.forEach { route ->
            assertEquals(expected[route], OfficialImageCatalogPolicy.modelIdForRoute(route))
        }
    }
}
