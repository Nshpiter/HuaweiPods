package moe.chenxy.huaweipods.pods

import org.junit.Assert.*
import org.junit.Test

class HuaweiFreeBuds7ControllerTest {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7

    @Test fun identityDoesNotCollideWith7i() {
        assertEquals(route, detectHuaweiDeviceRoute("HUAWEI FreeBuds 7"))
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, detectHuaweiDeviceRoute("HUAWEI FreeBuds 7i"))
        assertEquals(route, HuaweiDeviceInfoRoutePolicy.routeForModelId("000174"))
        assertFalse(HuaweiDeviceInfoRoutePolicy.isCompatible(route, "000163"))
        assertEquals(route, decodeHuaweiDeviceRouteFromBroadcast(encodeHuaweiDeviceRouteForBroadcast(route)))
        assertTrue(route.supportsRfcommBattery)
        assertTrue(HuaweiWearDetectionController.supports(route))
    }

    @Test fun ancUsesThreeCapturedLevelsAndPlainTransparency() {
        assertEquals(listOf(3, 1, 0), route.ancLevelOptions.map { it.protocolValue })
        assertFalse(route.supportsAncSubMode(2))
        assertEquals(setOf(2), route.transparencySubModes)
        listOf(
            "5A000A002B2A0102010102010564A6" to HuaweiAncLevel.LIGHT,
            "5A000A002B2A01020001020105CEF7" to HuaweiAncLevel.BALANCED,
            "5A000A002B2A010203010201052025" to HuaweiAncLevel.ADAPTIVE,
        ).forEach { (hex, level) ->
            val state = requireNotNull(route.validateAncState(requireNotNull(HuaweiRfcommResponseParser.parseAncState(freeBuds7Hex(hex)))))
            assertEquals(level, route.ancLevelOptionForProtocolValue(requireNotNull(state.subMode))?.level)
        }
        assertEquals(NoiseControlMode.TRANSPARENCY,
            HuaweiRfcommResponseParser.parseAncState(freeBuds7Hex("5A000A002B2A0102020202010511A8"))?.mode)
    }

    @Test fun spatialUsesFixed2AndHeadTracking1() {
        assertArrayEquals(freeBuds7Hex("5A0009002BB401011802010240AF"), FreeClip2SpatialAudioMode.FIXED.packet())
        assertArrayEquals(freeBuds7Hex("5A0009002BB401011802010170CC"), FreeClip2SpatialAudioMode.HEAD_TRACKING.packet())
        assertEquals(FreeClip2SpatialAudioMode.FIXED,
            HuaweiFreeClip2Controller.parseSpatialAudioState(freeBuds7Hex("5A000C002BB40101180201020301003A42"))?.mode)
    }

    @Test fun capturedSettingsReadbacksAreNotInferredFromAcknowledgements() {
        val fixtures = listOf(
            Triple(FreeBuds7Setting.ADAPTIVE_VOLUME, "5A0009002BB401010202010103C0", true),
            Triple(FreeBuds7Setting.ADAPTIVE_VOLUME, "5A0009002BB401010202010013E1", false),
            Triple(FreeBuds7Setting.HEAD_MOTION, "5A000F002BB401010B020101030101040102C55B", true),
            Triple(FreeBuds7Setting.HEAD_MOTION, "5A000F002BB401010B0201000301010401027D3A", false),
            Triple(FreeBuds7Setting.DROP_REMINDER, "5A0009002BB4010107020101BF85", true),
            Triple(FreeBuds7Setting.SINGLE_EAR_ANC, "5A0009002BB401010502010042CC", false),
            Triple(FreeBuds7Setting.AUTO_SWITCH, "5A0006002B2F0101015151", true),
            Triple(FreeBuds7Setting.HIGH_QUALITY, "5A0009002BA3010101020101B623", true),
            Triple(FreeBuds7Setting.HIGH_QUALITY, "5A0009002BA3010101020100A602", false),
            Triple(FreeBuds7Setting.LOW_LATENCY, "5A0006002B6C020100ED60", false),
            Triple(FreeBuds7Setting.CASE_SOUND, "5A0009002BB1020101030101186B", true),
            Triple(FreeBuds7Setting.CASE_SOUND, "5A0009002BB10201000301016EDF", false),
            Triple(FreeBuds7Setting.DOUBLE_TAP, "5A001A000120010101020101030501070200FF040100050100060200FF74D7", true),
            Triple(FreeBuds7Setting.SWIPE_VOLUME, "5A000E002B1F01010002010003030001FF24DF", true),
            Triple(FreeBuds7Setting.PINCH_PLAY, "5A0017002B9301010002010203010204010205060001020304FF3C67", true),
            Triple(FreeBuds7Setting.PINCH_PLAY, "5A0017002B930101000201020301FF0401FF05060001020304FF4D25", false),
            Triple(FreeBuds7Setting.PINCH_NEXT, "5A0017002B9301010102010203010404010405060001020304FF9DE0", true),
            Triple(FreeBuds7Setting.PINCH_PREVIOUS, "5A0017002B9301010202010203010304010305060001020304FF2472", true),
        )
        fixtures.forEach { (setting, hex, expected) ->
            val frame = freeBuds7Hex(hex)
            assertEquals(setting.name, expected, setting.parse(frame))
            assertNull(setting.parse(frame.copyOf(frame.size - 1)))
            val corrupt = frame.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertNull(setting.parse(corrupt))
            assertEquals(expected, setting.parse(byteArrayOf(0x11, 0x22) + corrupt + frame))
        }
        FreeBuds7Setting.entries.forEach {
            assertNull(it.parse(freeBuds7Hex("5A0009002B497F04000186A0B3C6")))
        }
        assertNull(FreeBuds7Setting.PINCH_PLAY.parse(freeBuds7Hex("5A0017002B9301010002010103010004010005060001020304FF1FCF")))
        assertNull(FreeBuds7Setting.LOW_LATENCY.parse(freeBuds7Hex("5A0006002B6C030100DA50")))
    }

    @Test fun settersMatchCapturedPacketsAndExcludeUnprovenActions() {
        assertArrayEquals(freeBuds7Hex("5A000F002B920101000201020301FF0401FF47FB"), FreeBuds7Setting.PINCH_PLAY.packet(false))
        assertArrayEquals(freeBuds7Hex("5A000F002B92010101020102030104040104F798"), FreeBuds7Setting.PINCH_NEXT.packet(true))
        assertArrayEquals(freeBuds7Hex("5A0009002B1E0101000202009D9B"), FreeBuds7Setting.SWIPE_VOLUME.packet(true))
        assertArrayEquals(freeBuds7Hex("5A000900011F010101020101FC28"), FreeBuds7Setting.DOUBLE_TAP.packet(true))
        assertEquals(listOf(HuaweiTapAction.PLAY_NEXT, HuaweiTapAction.PLAY_PREVIOUS, HuaweiTapAction.NONE),
            HuaweiTapAction.availableFor(route, HuaweiGestureKind.TRIPLE_TAP))
        assertArrayEquals(freeBuds7Hex("5A00060001250101071726"),
            HuaweiGestureController.buildTripleTapPacket(route, HuaweiGestureSide.LEFT, HuaweiTapAction.PLAY_PREVIOUS))
        assertNull(HuaweiEqualizerCodec.buildBuiltInPresetPacket(route, 0x11))
    }

    @Test fun equalizerUsesCapturedSaveOperationAndCurve() {
        assertEquals(1, HuaweiEqualizerCodec.customWriteOperation(route))
        assertArrayEquals(freeBuds7Hex("5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367"), FreeBuds7SoundEffect.CLASSICAL.packet())
        val state = requireNotNull(HuaweiEqualizerCodec.parseState(freeBuds7Hex("5A0017002B4A01010102010503070205090D0E0F1004010508003E7A")))
        assertEquals(5, state.selectedId)
        assertEquals(listOf(2,5,9,13,14,15,16), state.builtInIds)
    }

    @Test fun liveHandshakeAndWriteAcknowledgementDoNotMaskReadback() {
        val handshake = freeBuds7Hex("5A00030001063EBD")
        val ack = freeBuds7Hex("5A0009002B107F04000186A0729D")
        assertNull(FreeBuds7Setting.WEAR_DETECTION.parse(handshake + ack))
        assertEquals(false, FreeBuds7Setting.WEAR_DETECTION.parse(handshake + ack + freeBuds7Hex("5A0006002B11010100CFC3")))
        assertEquals(true, FreeBuds7Setting.WEAR_DETECTION.parse(handshake + ack + freeBuds7Hex("5A0006002B11010101DFE2")))
        val right = requireNotNull(HuaweiRfcommResponseParser.parseTripleTapState(handshake + freeBuds7Hex("5A001100012601010202010703060204050607FF4555"), route))
        assertEquals(HuaweiTapAction.PLAY_NEXT, right.left)
        assertEquals(HuaweiTapAction.PLAY_PREVIOUS, right.right)
        val left = requireNotNull(HuaweiRfcommResponseParser.parseTripleTapState(handshake + freeBuds7Hex("5A001100012601010702010203060204050607FF3C38"), route))
        assertEquals(HuaweiTapAction.PLAY_PREVIOUS, left.left)
        assertEquals(HuaweiTapAction.PLAY_NEXT, left.right)
    }
}
