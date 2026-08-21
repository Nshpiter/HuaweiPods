package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiRfcommResponseParserTest {
    @Test
    fun `parses every captured FreeBuds 5 ANC state response`() {
        val states = listOf(
            "5A0007002B2A010200001531" to HuaweiAncState(NoiseControlMode.OFF),
            "5A0007002B2A010203015043" to
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03),
            "5A0007002B2A010201013621" to
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x01),
            "5A0007002B2A010200010510" to
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x00),
        )

        states.forEach { (frame, expected) ->
            assertEquals(frame, expected, HuaweiRfcommResponseParser.parseAncState(hex(frame)))
        }
    }

    @Test
    fun `parses FreeBuds Pro 3 ANC main mode from captured state responses`() {
        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010200001531")))
        assertEquals(2, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010203015043")))
        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010202025311")))
    }

    @Test
    fun `uses the latest valid ANC state frame from a combined RFCOMM read`() {
        val off = hex("5A0007002B2A010200001531")
        val unrelated = hex("5A0006002B040201003171")
        val noiseCancellation = hex("5A0007002B2A010203015043")

        assertEquals(2, HuaweiRfcommResponseParser.parseAncStatus(off + unrelated + noiseCancellation))
    }

    @Test
    fun `parses FreeBuds Pro 5 ANC and transparency state from captured responses`() {
        val off = hex("5A000A002B2A01020000020105B843")
        val noiseCancellation = hex("5A000A002B2A010203010201052025")
        val transparency = hex("5A000A002B2A0102020202010511A8")

        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(off, distinguishTransparency = true))
        assertEquals(2, HuaweiRfcommResponseParser.parseAncStatus(noiseCancellation, distinguishTransparency = true))
        assertEquals(3, HuaweiRfcommResponseParser.parseAncStatus(transparency, distinguishTransparency = true))
        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(transparency))
    }

    @Test
    fun `parses FreeBuds 5 battery response captured from official app`() {
        val response = hex("5A0014000127010164020364645603030000000402140A075C")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(86, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `finds battery frame inside a combined RFCOMM read`() {
        val prefix = hex("5A0006000A0E010100A26F")
        val batteryFrame = hex("5A0014000127010164020364645603030000000402140A075C")

        val battery = HuaweiRfcommResponseParser.parseBattery(prefix + batteryFrame)
        assertNotNull(battery)

        assertEquals(86, battery?.case?.battery)
    }

    @Test
    fun `uses the latest battery frame from a combined RFCOMM read`() {
        val older = hex("5A001B000127010164020364644C03030000000402140A0502010006010A69E6")
        val newer = hex("5A001B000108010156020300563503030000000402140A0502000006010AEEEC")

        val battery = HuaweiRfcommResponseParser.parseBattery(older + newer)

        assertEquals(0, battery?.left?.battery)
        assertEquals(86, battery?.right?.battery)
        assertEquals(53, battery?.case?.battery)
    }

    @Test
    fun `does not hide nonzero earbuds using unverified in-case side order`() {
        val leftInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000127010164020364644C03030000000402140A0502010006010A69E6"),
        )
        val rightInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000127010164020364644C03030000000402140A0502000106010AB503"),
        )

        assertEquals(true, leftInCase?.left?.isConnected)
        assertEquals(true, leftInCase?.right?.isConnected)
        assertEquals(true, rightInCase?.left?.isConnected)
        assertEquals(true, rightInCase?.right?.isConnected)
    }

    @Test
    fun `marks a zero percent earbud unavailable`() {
        val response = hex("5A001B000108010156020300563503030000000402140A0502000006010AEEEC")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(0, battery?.left?.battery)
        assertEquals(true, battery?.right?.isConnected)
        assertEquals(false, battery?.left?.isConnected)
        assertEquals(true, battery?.case?.isConnected)
    }

    @Test
    fun `keeps both earbuds visible for equal or missing in-case states`() {
        val bothInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000108010164020364644C03030000000402140A0502010106010AED15"),
        )
        val bothOutOfCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000108010156020357563503030000000402140A0502000006010A25DA"),
        )
        val response = hex("5A0014000127010164020364645603030000000402140A075C")
        val withoutState = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(true, bothInCase?.left?.isConnected)
        assertEquals(true, bothInCase?.right?.isConnected)
        assertEquals(true, bothOutOfCase?.left?.isConnected)
        assertEquals(true, bothOutOfCase?.right?.isConnected)
        assertEquals(true, withoutState?.left?.isConnected)
        assertEquals(true, withoutState?.right?.isConnected)
    }

    @Test
    fun `parses FreeClip battery response captured from official app`() {
        val response = hex("5A0018000108010164020364642603030000000402140A05020101D504")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(38, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds 6i battery response captured from official app`() {
        val response = hex("5A001B000108010164020364646403030000000402140A0502010106010A5607")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(100, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds 5i battery response captured from official app`() {
        val battery = HuaweiRfcommResponseParser.parseBattery(
            hex("5A0014000108010164020364641903030000000402140AD792"),
        )

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(25, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds Pro 3 battery response captured from official app`() {
        val response = hex("5A00180001080101600203606413030301010004020A140502010126EC")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(96, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(19, battery?.case?.battery)
        assertEquals(true, battery?.left?.isCharging)
        assertEquals(true, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds Pro 4 battery response captured from official app`() {
        val response = hex("5A001B00010801015C02035C5C4A030300000004020A140502000006010A6F4E")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(92, battery?.left?.battery)
        assertEquals(92, battery?.right?.battery)
        assertEquals(74, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `FreeBuds 6i ignores transient field 05 instead of applying Pro 5 availability semantics`() {
        val response = hex("5A001B000108010164020364646003030000010402140A0502000106010ABCAB")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(100, battery?.left?.battery)
        assertEquals(true, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(true, battery?.right?.isConnected)
        assertEquals(96, battery?.case?.battery)
    }

    @Test
    fun `FreeBuds 6i keeps zero percent sentinel unavailable despite transient field 05`() {
        val response = hex("5A001B000108010164020300646003030000010402140A0502010106010A0000")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(0, battery?.left?.battery)
        assertEquals(false, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(true, battery?.right?.isConnected)
    }

    @Test
    fun `FreeBuds 6i uses latest battery frame from a combined read`() {
        val stale = hex("5A001B000108010164020364646003030000010402140A0502000106010ABCAB")
        val latest = hex("5A001B00010801016402035F5E6003030000010402140A0502000106010A0000")

        val battery = HuaweiRfcommResponseParser.parseBattery(stale + latest)

        assertEquals(95, battery?.left?.battery)
        assertEquals(94, battery?.right?.battery)
        assertEquals(96, battery?.case?.battery)
    }

    @Test
    fun `parses every verified FreeBuds 6i noise control state`() {
        val states = listOf(
            "5A0007002B2A010200001531" to HuaweiAncState(NoiseControlMode.OFF),
            "5A0007002B2A010201013621" to HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x01),
            "5A0007002B2A010200010510" to HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x00),
            "5A0007002B2A010202016372" to HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02),
            "5A0007002B2A010203015043" to HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03),
            "5A0007002B2A010201020642" to HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x01),
            "5A0007002B2A010202025311" to HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
        )

        states.forEach { (frame, expected) ->
            assertEquals(frame, expected, HuaweiRfcommResponseParser.parseAncState(hex(frame)))
        }
    }

    @Test
    fun `parses FreeBuds 5i ANC and double tap readback`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5I
        val deep = HuaweiRfcommResponseParser.parseAncState(
            hex("5A0007002B2A010202016372"),
        )
        val transparency = HuaweiRfcommResponseParser.parseAncState(
            hex("5A0007002B2A010202025311"),
        )
        val doubleTap = HuaweiRfcommResponseParser.parseDoubleTapState(
            hex("5A0017000120010102020101030501070200FF040100060200FF7698"),
            route,
        )

        assertEquals(
            HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02),
            deep?.let { route.validateAncState(it) },
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            transparency?.let { route.validateAncState(it) },
        )
        assertEquals(HuaweiTapAction.PLAY_NEXT, doubleTap?.left)
        assertEquals(HuaweiTapAction.PLAY_PAUSE, doubleTap?.right)
    }

    @Test
    fun `FreeBuds 6i readback values keep their captured semantic levels`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        val states = mapOf(
            "5A0007002B2A010203015043" to HuaweiAncLevel.ADAPTIVE,
            "5A0007002B2A010201013621" to HuaweiAncLevel.LIGHT,
            "5A0007002B2A010200010510" to HuaweiAncLevel.BALANCED,
            "5A0007002B2A010202016372" to HuaweiAncLevel.DEEP,
        )

        states.forEach { (frame, expectedLevel) ->
            val state = HuaweiRfcommResponseParser.parseAncState(hex(frame))
            assertEquals(
                expectedLevel,
                state?.subMode?.let(route::ancLevelOptionForProtocolValue)?.level,
            )
        }
    }

    @Test
    fun `FreeBuds 6i uses latest noise control frame from a combined read`() {
        val off = hex("5A0007002B2A010200001531")
        val unrelated = hex("5A0006002B040201003171")
        val transparency = hex("5A0007002B2A010202025311")

        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            HuaweiRfcommResponseParser.parseAncState(off + unrelated + transparency),
        )
    }

    @Test
    fun `uses FreeBuds Pro 5 out-of-case state while retaining the raw level`() {
        val response = hex("5A001B0001080101640203646455030300000004020A140502000106010A4D9B")

        val battery = HuaweiRfcommResponseParser.parseBattery(
            response,
            useReportedEarbudAvailability = true,
        )
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(true, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(false, battery?.right?.isConnected)
        assertEquals(85, battery?.case?.battery)
    }

    @Test
    fun `marks both FreeBuds Pro 5 earbuds unavailable when stored in the case`() {
        val response = hex("5A001B0001080101640203646455030300000004020A140502010106010AE7CA")

        val battery = HuaweiRfcommResponseParser.parseBattery(
            response,
            useReportedEarbudAvailability = true,
        )

        assertEquals(100, battery?.left?.battery)
        assertEquals(false, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(false, battery?.right?.isConnected)
    }

    @Test
    fun `keeps a zero-percent FreeBuds Pro 5 earbud available when out of the case`() {
        val response = hex("5A001B0001080101640203006455030300000004020A140502000006010A0000")

        val battery = HuaweiRfcommResponseParser.parseBattery(
            response,
            useReportedEarbudAvailability = true,
        )

        assertEquals(0, battery?.left?.battery)
        assertEquals(true, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(true, battery?.right?.isConnected)
    }

    @Test
    fun `keeps legacy battery behavior unless reported availability is requested`() {
        val response = hex("5A001B0001080101640203646455030300000004020A140502000106010A4D9B")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(100, battery?.left?.battery)
        assertEquals(true, battery?.left?.isConnected)
        assertEquals(100, battery?.right?.battery)
        assertEquals(true, battery?.right?.isConnected)
    }

    @Test
    fun `parses FreeBuds 7i battery response captured from official app`() {
        val response = hex("5A001B000108010164020364641D03030000000402140A0502000106010A31A3")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(29, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses Eyewear temple batteries without exposing the placeholder case`() {
        val response = hex("5A0014000108010144020346440003030000000402140A392E")

        val battery = HuaweiRfcommResponseParser.parseBattery(response, includeCase = false)
        assertNotNull(battery)

        assertEquals(70, battery?.left?.battery)
        assertEquals(68, battery?.right?.battery)
        assertEquals(null, battery?.case)
    }

    @Test
    fun `parses Eyewear 2 temple batteries without exposing the placeholder case`() {
        val response = hex("5A001800010801010802030C0800030300000004020A050502000036DE")

        val battery = HuaweiRfcommResponseParser.parseBattery(response, includeCase = false)
        assertNotNull(battery)

        assertEquals(12, battery?.left?.battery)
        assertEquals(8, battery?.right?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(null, battery?.case)
    }

    @Test
    fun `parses Eyewear 2 temple charging flags from TLV 03`() {
        val response = hex("5A001800010801010802030C0800030301010004020A050502000036DE")

        val battery = HuaweiRfcommResponseParser.parseBattery(response, includeCase = false)
        assertNotNull(battery)

        assertEquals(true, battery?.left?.isCharging)
        assertEquals(true, battery?.right?.isCharging)
        assertEquals(1, battery?.left?.rawStatus)
        assertEquals(1, battery?.right?.rawStatus)
        assertEquals(null, battery?.case)
    }

    @Test
    fun `parses FreeClip 2 double tap state captured from official app`() {
        val response = hex("5A001A000120010102020107030501020700FF040100050100060200FFDC02")

        val state = HuaweiRfcommResponseParser.parseDoubleTapState(
            response,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        )

        assertEquals(HuaweiTapAction.PLAY_NEXT, state?.left)
        assertEquals(HuaweiTapAction.SPATIAL_AUDIO, state?.right)
    }

    @Test
    fun `parses FreeClip 2 triple tap state captured from official app`() {
        val response = hex("5A001100012601010702010203060204050607FF3C38")

        val state = HuaweiRfcommResponseParser.parseTripleTapState(
            response,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        )

        assertEquals(HuaweiTapAction.PLAY_PREVIOUS, state?.left)
        assertEquals(HuaweiTapAction.PLAY_NEXT, state?.right)
    }

    @Test
    fun `parses FreeClip 2 swipe state captured from official app`() {
        val response = hex("5A000E002B1F01010002010003030001FF24DF")

        val state = HuaweiRfcommResponseParser.parseSwipeState(response)

        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state?.left)
        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state?.right)
    }

    @Test
    fun `parses all FreeClip 2 gesture states from one RFCOMM read`() {
        val doubleTap = hex("5A001A000120010101020101030501020700FF040100050100060200FFB2DD")
        val tripleTap = hex("5A001100012601010702010203060204050607FF3C38")
        val swipe = hex("5A000E002B1F0101000201FF03030001FF7060")

        val state = HuaweiRfcommResponseParser.parseGestureState(
            doubleTap + tripleTap + swipe,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
        )

        assertEquals(HuaweiTapAction.PLAY_PAUSE, state.doubleTap?.left)
        assertEquals(HuaweiTapAction.PLAY_PREVIOUS, state.tripleTap?.left)
        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state.swipe?.left)
        assertEquals(HuaweiSwipeAction.NONE, state.swipe?.right)
    }

    @Test
    fun `parses FreeBuds 4E long press state captured from official app`() {
        val response = hex("5A0018002B170101FF0201FF030D000102030405060708090A0E0F5714")

        val state = HuaweiRfcommResponseParser.parseLongPressState(
            response,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        )

        assertEquals(FreeBudsPro3LongPressAction.NONE, state?.left)
        assertEquals(FreeBudsPro3LongPressAction.NONE, state?.right)
    }

    @Test
    fun `parses FreeBuds 4E double tap and long press from one RFCOMM read`() {
        val doubleTap = hex("5A001A000120010101020101030501020700FF040100050100060200FFB2DD")
        val longPress = hex("5A0018002B1701010302010E030D000102030405060708090A0E0FC50C")

        val state = HuaweiRfcommResponseParser.parseGestureState(
            doubleTap + longPress,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
        )

        assertEquals(HuaweiTapAction.PLAY_PAUSE, state.doubleTap?.left)
        assertEquals(HuaweiTapAction.PLAY_PAUSE, state.doubleTap?.right)
        assertEquals(FreeBudsPro3LongPressAction.NOISE_CONTROL, state.longPress?.left)
        assertEquals(FreeBudsPro3LongPressAction.SONG_RECOGNITION, state.longPress?.right)
    }

    @Test
    fun `ignores unverified FreeClip 2 gesture values`() {
        val unverifiedTripleTap = hex("5A001100012601010402010203060204050607FF3C38")
        val unverifiedSwipe = hex("5A000E002B1F01010102010003030001FF24DF")

        assertNull(
            HuaweiRfcommResponseParser.parseTripleTapState(
                unverifiedTripleTap,
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            ),
        )
        assertNull(HuaweiRfcommResponseParser.parseSwipeState(unverifiedSwipe))
    }

    @Test
    fun `parses FreeArc battery and all gesture states from capture`() {
        val battery = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000108010164020364645203030000000402140A0502010106010A1648"),
        )
        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(82, battery?.case?.battery)

        val response = hex(
            "5A0017000120010101020101030501070200FF040100060200FF76EA" +
                "5A000E00012601010702010203030702FF232E" +
                "5A0020002B170101FF0201FF030E000102030405060708090A0E0F140401FF060200FF3C8C" +
                "5A000E002B1F010100020100030300FF011AC0",
        )
        val state = HuaweiRfcommResponseParser.parseGestureState(
            response,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
        )

        assertEquals(HuaweiTapAction.PLAY_PAUSE, state.doubleTap?.left)
        assertEquals(HuaweiTapAction.PLAY_PAUSE, state.doubleTap?.right)
        assertEquals(HuaweiTapAction.PLAY_PREVIOUS, state.tripleTap?.left)
        assertEquals(HuaweiTapAction.PLAY_NEXT, state.tripleTap?.right)
        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state.swipe?.left)
        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state.swipe?.right)
        assertEquals(FreeBudsPro3LongPressAction.NONE, state.longPress?.left)
        assertEquals(FreeBudsPro3LongPressAction.NONE, state.longPress?.right)
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
