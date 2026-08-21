package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiDeviceInfoRoutePolicyTest {
    @Test
    fun `verified model ids resolve to their authoritative routes`() {
        val expected = mapOf(
            "000141" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            "000145" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            "000135" to HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            "000153" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            "000149" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            "00016D" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            "000163" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            "000167" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            "00015D" to HuaweiDeviceRoute.HUAWEI_FREEARC,
            "00014F" to HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )

        expected.forEach { (modelId, route) ->
            assertEquals(route, HuaweiDeviceInfoRoutePolicy.routeForModelId(modelId))
            assertEquals(modelId, HuaweiDeviceInfoRoutePolicy.modelIdForRoute(route))
            assertTrue(HuaweiDeviceInfoRoutePolicy.isCompatible(route, modelId))
        }
        assertNull(HuaweiDeviceInfoRoutePolicy.routeForModelId("000999"))
    }

    @Test
    fun `known route conflicts fail closed while unknown pairs remain unguessed`() {
        assertFalse(
            HuaweiDeviceInfoRoutePolicy.isCompatible(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                "000149",
            ),
        )
        assertFalse(
            HuaweiDeviceInfoRoutePolicy.isCompatible(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
                "000149",
            ),
        )
        assertTrue(
            HuaweiDeviceInfoRoutePolicy.isCompatible(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
                "000999",
            ),
        )
    }
}
