package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiLowLatencyControllerTest {
    @Test
    fun `setter packets match every verified capture`() {
        assertArrayEquals(
            packet("5A0006002B6C010101A411"),
            HuaweiLowLatencyController.packet(true),
        )
        assertArrayEquals(
            packet("5A0006002B6C010100B430"),
            HuaweiLowLatencyController.packet(false),
        )
    }

    @Test
    fun `capability is enabled only for routes with setter capture evidence`() {
        val verified = setOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )
        verified.forEach { route -> assertTrue(route.displayName, route.supportsLowLatencyControl) }
        HuaweiDeviceRoute.entries.filterNot(verified::contains).forEach { route ->
            assertFalse(route.displayName, route.supportsLowLatencyControl)
        }
    }

    private fun packet(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
