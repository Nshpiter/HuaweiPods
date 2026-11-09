package moe.chenxy.huaweipods.pods

import org.junit.Assert.*
import org.junit.Test

/** Frames from the 2026-10-28 Pro 2 capture, without device identifiers. */
class HuaweiFreeBudsPro2ControllerTest {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO2

    @Test
    fun `identity resolves Pro 2 separately from Pro 3`() {
        assertEquals(route, HuaweiDeviceInfoRoutePolicy.routeForModelId("000131"))
        assertEquals("000131", HuaweiDeviceInfoRoutePolicy.modelIdForRoute(route))
        assertFalse(HuaweiDeviceInfoRoutePolicy.isCompatible(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, "000131"))
        assertEquals(route, detectHuaweiDeviceRoute("HUAWEI FreeBuds Pro 2"))
        assertEquals(HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute("HUAWEI FreeBuds Pro 2+"))
    }

    @Test
    fun `noise levels and transparency use captured Pro 2 values`() {
        val frames = mapOf(
            HuaweiAncLevel.LIGHT to "5A0007002B0401020101F13D",
            HuaweiAncLevel.BALANCED to "5A0007002B0401020100E11C",
            HuaweiAncLevel.DEEP to "5A0007002B0401020102C15E",
            HuaweiAncLevel.ADAPTIVE to "5A0007002B0401020103D17F",
        )
        route.ancLevelOptions.forEach {
            assertArrayEquals(hex(frames.getValue(it.level)), HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, it.protocolValue))
        }
        assertArrayEquals(hex("5A0007002B0401020202940D"), HuaweiAncPackets.mode(route, NoiseControlMode.TRANSPARENCY))
        assertArrayEquals(hex("5A0007002B0401020201A46E"), HuaweiAncPackets.mode(route, NoiseControlMode.TRANSPARENCY, 1))
        assertEquals(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0), HuaweiRfcommResponseParser.parseAncState(hex("5A0007002B2A010200010510")))
    }

    @Test
    fun `equalizer retains both captured custom names and signed gains`() {
        val state = HuaweiEqualizerCodec.parseState(hex(
            "5A0085002B4A01010102016503040203050904010505010A060A000000000000000000000718E68891E79A84E99FB3E695882032000000000000000000000848640A00000005F6F100000000E68891E79A84E99FB3E69588203100000000000000000000650A00000000000000000000E68891E79A84E99FB3E695882032000000000000000000008F7E",
        ))!!
        assertEquals(0x65, state.selectedId)
        assertEquals(listOf(2, 3, 5, 9), state.builtInIds)
        assertEquals(listOf("我的音效 1", "我的音效 2"), state.customPresets.map { it.name })
        assertEquals(listOf(0, 0, 0, 5, -10, -15, 0, 0, 0, 0), state.customPresets.first().gains)
        assertArrayEquals(hex("5A0028002B4901016502010A050101030A00000000000000000000040EE68891E79A84E99FB3E6958820327C38"),
            HuaweiEqualizerCodec.buildCustomPacket(List(10) { 0 }, "我的音效 2", HuaweiEqualizerCodec.customWriteOperation(route)!!, 0x65))
        assertArrayEquals(hex("5A0006002B490101056F9E"), FreeBudsPro2SoundEffect.DEFAULT.packet())
        assertArrayEquals(hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367"), FreeBudsPro2SoundEffect.HIFI_LIVE.packet())
    }

    @Test
    fun `settings parse device state and reject ACK or corrupt frames`() {
        assertEquals(true, FreeBudsPro2Setting.DUAL_DEVICE.parse(hex("5A0006002B2F0101015151")))
        assertEquals(false, FreeBudsPro2Setting.HIGH_QUALITY.parse(hex("5A0009002BA3010101020100A602")))
        assertEquals(true, FreeBudsPro2Setting.HIGH_QUALITY.parse(hex("5A0009002BA3010101020101B623")))
        assertEquals(false, FreeBudsPro2Setting.PINCH_PLAY.parse(hex("5A0017002B930101000201020301FF0401FF05060001020304FF4D25")))
        assertEquals(true, FreeBudsPro2Setting.PINCH_NEXT.parse(hex("5A0017002B9301010102010203010404010405060001020304FF9DE0")))
        assertNull(FreeBudsPro2Setting.PINCH_PLAY.parse(hex("5A0017002B9301010102010203010404010405060001020304FF9DE0")))
        // The captured enable attempt still returned disabled; command ACK is not device state.
        assertEquals(false, FreeBudsPro2Setting.SWIPE_VOLUME.parse(hex("5A0006002B1E030102550F5A000E002B1F0101FF0201FF03030001FFFC5D")))
        val ack = hex("5A0009002B6C7F04000186A0A497")
        assertFalse(FreeBudsPro2State.parse(ack).isComplete)
        assertTrue(FreeBudsPro2State.parse(ack).settings.values.all { it == null })
        assertNull(FreeBudsPro2Setting.DUAL_DEVICE.parse(hex("5A0006002B2F0101015150")))
    }

    @Test
    fun `pinch swipe long press and low latency match recorded writes`() {
        assertArrayEquals(hex("5A000F002B92010100020102030102040102DF82"), FreeBudsPro2Setting.PINCH_PLAY.packet(true))
        assertArrayEquals(hex("5A0009002B1E0101FF0202FFC8C8"), FreeBudsPro2Setting.SWIPE_VOLUME.packet(false))
        assertArrayEquals(hex("5A0006002B1602010E2670"), HuaweiGestureController.buildModernEarbudsLongPressPacket(route, HuaweiGestureSide.RIGHT, FreeBudsPro3LongPressAction.SONG_RECOGNITION))
        assertEquals(FreeBudsPro3LongPressAction.SONG_RECOGNITION,
            HuaweiRfcommResponseParser.parseLongPressState(hex("5A001A002B170101FF02010E030F000102030405060708090A0E0FFFFF59A6"), route)?.right)
        assertTrue(route.supportsLowLatencyControl)
        assertArrayEquals(hex("5A0006002B6C010101A411"), HuaweiLowLatencyController.packet(true))
        assertArrayEquals(hex("5A0006002B10010100B977"), HuaweiWearDetectionController.setPacket(route, false))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
