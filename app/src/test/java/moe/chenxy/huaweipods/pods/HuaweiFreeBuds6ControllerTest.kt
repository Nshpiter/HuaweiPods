package moe.chenxy.huaweipods.pods

import org.junit.Assert.*
import org.junit.Test

class HuaweiFreeBuds6ControllerTest {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6

    @Test fun identityAndCapabilitiesStaySeparateFrom6i() {
        assertEquals(route, detectHuaweiDeviceRoute("HUAWEI FreeBuds 6"))
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, detectHuaweiDeviceRoute("HUAWEI FreeBuds 6i"))
        assertEquals(route, HuaweiDeviceInfoRoutePolicy.routeForModelId("000155"))
        assertFalse(HuaweiDeviceInfoRoutePolicy.isCompatible(route, "000153"))
        assertEquals(route, decodeHuaweiDeviceRouteFromBroadcast(encodeHuaweiDeviceRouteForBroadcast(route)))
        assertFalse(route.supportsTransparency)
        assertFalse(route.supportsGestureConfiguration)
        assertTrue(route.supportsRfcommBattery)
        assertTrue(route.supportsLowLatencyControl)
        assertTrue(HuaweiWearDetectionController.supports(route))
    }

    @Test fun capturedAncLevelsNeverExposeDeepOrTransparency() {
        val cases = listOf(
            Triple(3, "5A0007002B0401020103D17F", "5A0007002B2A010203015043"),
            Triple(1, "5A0007002B0401020101F13D", "5A0007002B2A010201013621"),
            Triple(0, "5A0007002B0401020100E11C", "5A0007002B2A010200010510"),
        )
        assertEquals(listOf(3, 1, 0), route.ancLevelOptions.map { it.protocolValue })
        cases.forEach { (level, tx, rx) ->
            assertArrayEquals(hex(tx), HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, level))
            val state = route.validateAncState(requireNotNull(HuaweiRfcommResponseParser.parseAncState(hex(rx))))
            assertEquals(NoiseControlMode.NOISE_CANCELLATION, state?.mode)
            assertEquals(level, state?.subMode)
        }
        assertArrayEquals(hex("5A0007002B0401020000D22D"), HuaweiAncPackets.enabled(route, false))
        assertEquals(NoiseControlMode.OFF,
            route.validateAncState(requireNotNull(HuaweiRfcommResponseParser.parseAncState(hex("5A0007002B2A010200001531"))))?.mode)
        assertNull(HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, 2))
        assertNull(HuaweiAncPackets.mode(route, NoiseControlMode.TRANSPARENCY))
    }

    @Test fun switchesReadVerifiedStateAndRejectAckTruncatedAndCorruptFrames() {
        val cases = listOf(
            Triple(FreeBuds6Setting.ADAPTIVE_VOLUME, "5A0009002BB401010202010103C0", true),
            Triple(FreeBuds6Setting.ADAPTIVE_VOLUME, "5A0009002BB401010202010013E1", false),
            Triple(FreeBuds6Setting.DROP_REMINDER, "5A0009002BB4010107020101BF85", true),
            Triple(FreeBuds6Setting.DROP_REMINDER, "5A0009002BB4010107020100AFA4", false),
            Triple(FreeBuds6Setting.WEAR_DETECTION, "5A0006002B11010101DFE2", true),
            Triple(FreeBuds6Setting.CASE_SOUND, "5A0009002BB1020101030101186B", true),
            Triple(FreeBuds6Setting.CASE_SOUND, "5A0009002BB10201000301016EDF", false),
            Triple(FreeBuds6Setting.LOW_LATENCY, "5A0006002B6C020100ED60", false),
        )
        cases.forEach { (setting, value, expected) ->
            val bytes = hex(value)
            assertEquals(setting.name, expected, setting.parse(bytes))
            assertNull(setting.parse(bytes.copyOf(bytes.size - 1)))
            val corrupt = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertNull(setting.parse(corrupt))
            assertEquals(expected, setting.parse(corrupt + bytes))
        }
        FreeBuds6Setting.entries.forEach {
            assertNull(it.parse(hex("5A0009002B107F04000186A0729D")))
            assertNull(it.parse(hex("5A0009002B6C7F04000186A0A497")))
        }
        assertNull(FreeBuds6Setting.LOW_LATENCY.parse(hex("5A0006002B6C030100DA50")))
        assertFalse(FreeBuds6State.parse(byteArrayOf()).isComplete)
        assertFalse(FreeBuds6State.parse(hex(cases.first().second)).isComplete)
    }

    @Test fun settingWritesMatchBothCapturedDirections() {
        val cases = listOf(
            Triple(FreeBuds6Setting.ADAPTIVE_VOLUME, "5A0009002BB401010202010013E1", "5A0009002BB401010202010103C0"),
            Triple(FreeBuds6Setting.DROP_REMINDER, "5A0009002BB4010107020100AFA4", "5A0009002BB4010107020101BF85"),
            Triple(FreeBuds6Setting.WEAR_DETECTION, "5A0006002B10010100B977", "5A0006002B10010101A956"),
            Triple(FreeBuds6Setting.CASE_SOUND, "5A0006002BB101010025B5", "5A0006002BB10101013594"),
            Triple(FreeBuds6Setting.LOW_LATENCY, "5A0006002B6C010100B430", "5A0006002B6C010101A411"),
        )
        cases.forEach { (setting, off, on) ->
            assertArrayEquals(hex(off), setting.packet(false))
            assertArrayEquals(hex(on), setting.packet(true))
        }
    }

    @Test fun equalizerUsesDefaultZeroAndOnlyReportedPresets() {
        assertTrue(HuaweiEqualizerCodec.supportsStateRead(route))
        assertNull(HuaweiEqualizerCodec.customWriteOperation(route))
        assertArrayEquals(hex("5A0006002B490101003F3B"), FreeBuds6SoundEffect.DEFAULT.packet())
        assertArrayEquals(hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367"),
            FreeBuds6SoundEffect.CLASSICAL.packet())
        val state = requireNotNull(HuaweiEqualizerCodec.parseState(hex("5A0016002B4A01010102010B0306000203090B0C0401000800A56D")))
        assertEquals(listOf(0, 2, 3, 9, 11, 12), state.builtInIds)
        assertEquals(11, state.selectedId)
        assertNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, 1))
        assertNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, 0x10))
    }

    private fun hex(value: String) = freeBuds7Hex(value)
}
