package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.smartaudio.OfficialImageCatalogPolicy
import org.junit.Assert.*
import org.junit.Test

/** Official ZA09 compatibility checks; these fixtures are not a FreeBuds 4 device capture. */
class FreeBuds4CompatibilityTest {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS4

    @Test
    fun `FreeBuds 4 identity never resolves to 4E or 4i`() {
        assertEquals(route, detectHuaweiDeviceRoute("HUAWEI FreeBuds 4"))
        assertEquals(route, detectHuaweiDeviceRoute("FreeBuds 4"))
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREEBUDS4E, detectHuaweiDeviceRoute("FreeBuds 4E"))
        assertEquals(HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute("FreeBuds 4i"))
        assertEquals(route, HuaweiDeviceInfoRoutePolicy.routeForModelId("000129"))
        assertEquals("000129", OfficialImageCatalogPolicy.modelIdForRoute(route))
        assertFalse(HuaweiDeviceInfoRoutePolicy.isCompatible(route, "000135"))
        assertFalse(HuaweiDeviceInfoRoutePolicy.isCompatible(HuaweiDeviceRoute.HUAWEI_FREEBUDS4E, "000129"))
        assertEquals(route, decodeHuaweiDeviceRouteFromBroadcast(encodeHuaweiDeviceRouteForBroadcast(route)))
    }

    @Test
    fun `ZA09 basic ANC uses modern packets and excludes transparency adaptive and deep`() {
        assertArrayEquals(hex("5A0007002B0401020000D22D"), HuaweiAncPackets.mode(route, NoiseControlMode.OFF))
        assertArrayEquals(hex("5A0007002B04010201FFFFEC"), HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, 0xFF))
        assertEquals(listOf(1, 0), route.ancLevelOptions.map { it.protocolValue })
        assertNull(HuaweiAncPackets.mode(route, NoiseControlMode.TRANSPARENCY))
        assertNull(HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, 2))
        assertNull(HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, 3))
        assertFalse(route.supportsAncDirectionDial)
        assertFalse(route.supportsLowLatencyControl)
        assertFalse(route.supportsBackgroundBatteryRefresh)
    }

    @Test
    fun `shared EQ gesture and wear paths retain conservative feature boundaries`() {
        assertArrayEquals(hex("5A0006002B490101021F79"), HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, 2))
        assertNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, 9))
        assertTrue(HuaweiEqualizerCodec.supportsStateRead(route))
        assertTrue(HuaweiWearDetectionController.supports(route))
        assertArrayEquals(hex("5A000600011F0201070A34"), HuaweiGestureController.buildDoubleTapPacket(
            route, HuaweiGestureSide.RIGHT, HuaweiTapAction.PLAY_PREVIOUS,
        ))
        assertNull(HuaweiGestureController.buildTripleTapPacket(route, HuaweiGestureSide.LEFT, HuaweiTapAction.PLAY_NEXT))
        assertTrue(HuaweiSwipeAction.availableFor(route).isEmpty())
        assertEquals(3, FreeBudsPro3LongPressAction.NOISE_CONTROL.protocolValue(route))
        assertArrayEquals(HuaweiGestureController.buildGestureStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS4E),
            HuaweiGestureController.buildGestureStateQuery(route))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
