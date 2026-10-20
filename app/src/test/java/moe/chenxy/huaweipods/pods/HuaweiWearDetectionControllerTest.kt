package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
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

    @Test
    fun `failed readback finishes with unknown state exactly once`() {
        val states = mutableListOf<Boolean?>()
        val readback = WearDetectionStateReadback({ true }, states::add)

        readback.onTransportComplete(false)
        readback.onTransportComplete(false)
        readback.onResponse(true)

        assertEquals(listOf<Boolean?>(null), states)
    }

    @Test
    fun `successful transport waits for a parsed response`() {
        val states = mutableListOf<Boolean?>()
        val readback = WearDetectionStateReadback({ true }, states::add)

        readback.onTransportComplete(true)
        assertTrue(states.isEmpty())
        readback.onResponse(false)
        readback.onTransportComplete(true)

        assertEquals(listOf(false), states)
    }

    @Test
    fun `parsed state is not overwritten by a later transport failure`() {
        val states = mutableListOf<Boolean?>()
        val readback = WearDetectionStateReadback({ true }, states::add)

        readback.onResponse(true)
        readback.onTransportComplete(false)
        readback.onResponse(false)

        assertEquals(listOf(true), states)
    }

    @Test
    fun `empty response finishes with unknown state exactly once`() {
        val states = mutableListOf<Boolean?>()
        val readback = WearDetectionStateReadback({ true }, states::add)

        readback.onResponse(HuaweiWearDetectionController.parseState(byteArrayOf()))
        readback.onTransportComplete(true)
        readback.onTransportComplete(false)

        assertEquals(listOf<Boolean?>(null), states)
    }

    @Test
    fun `hot reload suppresses old readback response and failure callbacks`() {
        var generation = 1L
        val states = mutableListOf<Boolean?>()
        val failedRead = WearDetectionStateReadback({ generation == 1L }, states::add)
        val respondingRead = WearDetectionStateReadback({ generation == 1L }, states::add)

        generation = 2L
        failedRead.onTransportComplete(false)
        respondingRead.onResponse(true)
        respondingRead.onTransportComplete(true)

        assertTrue(states.isEmpty())
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
