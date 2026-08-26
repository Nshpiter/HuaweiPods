package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiWearDetectionControllerTest {
    @Test
    fun `captured models share exact wear detection packets`() {
        val supported = listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        )
        supported.forEach { route ->
            assertArrayEquals(
                route.name,
                hex("5A0005002B110100772A"),
                HuaweiWearDetectionController.stateQueryPacket(route),
            )
            assertArrayEquals(
                route.name,
                hex("5A0006002B10010101A956"),
                HuaweiWearDetectionController.setPacket(route, true),
            )
            assertArrayEquals(
                route.name,
                hex("5A0006002B10010100B977"),
                HuaweiWearDetectionController.setPacket(route, false),
            )
        }
    }

    @Test
    fun `uncaptured routes remain unavailable`() {
        assertNull(
            HuaweiWearDetectionController.stateQueryPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            ),
        )
    }

    @Test
    fun `wear detection parser accepts only boolean state`() {
        assertTrue(
            HuaweiWearDetectionController.parseState(
                hex("5A0006002B11010101DFE2"),
            ) == true,
        )
        assertFalse(
            HuaweiWearDetectionController.parseState(
                hex("5A0006002B11010100CFC3"),
            ) ?: true,
        )
        assertNull(
            HuaweiWearDetectionController.parseState(
                hex("5A0006002B11010102EFA1"),
            ),
        )
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
