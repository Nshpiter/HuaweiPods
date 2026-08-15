package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiGestureControllerTest {
    @Test
    fun `FreeBuds 4E gesture packets and queries match capture`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS4E
        val expected = linkedMapOf(
            HuaweiTapAction.PLAY_PAUSE to "5A000600011F01010133A2",
            HuaweiTapAction.PLAY_NEXT to "5A000600011F01010203C1",
            HuaweiTapAction.PLAY_PREVIOUS to "5A000600011F0101075364",
            HuaweiTapAction.VOICE_ASSISTANT to "5A000600011F0101002383",
            HuaweiTapAction.NONE to "5A000600011F0101FF3D73",
        )
        expected.forEach { (action, packet) ->
            assertArrayEquals(
                action.name,
                hex(packet),
                HuaweiGestureController.buildDoubleTapPacket(
                    route,
                    HuaweiGestureSide.LEFT,
                    action,
                ),
            )
        }
        val expectedRight = linkedMapOf(
            HuaweiTapAction.PLAY_PAUSE to "5A000600011F0201016AF2",
            HuaweiTapAction.PLAY_NEXT to "5A000600011F0201025A91",
            HuaweiTapAction.PLAY_PREVIOUS to "5A000600011F0201070A34",
            HuaweiTapAction.VOICE_ASSISTANT to "5A000600011F0201007AD3",
            HuaweiTapAction.NONE to "5A000600011F0201FF6423",
        )
        expectedRight.forEach { (action, packet) ->
            assertArrayEquals(
                action.name,
                hex(packet),
                HuaweiGestureController.buildDoubleTapPacket(
                    route,
                    HuaweiGestureSide.RIGHT,
                    action,
                ),
            )
        }
        assertArrayEquals(
            hex(
                "5A000700012001000200E897" +
                    "5A0007002B170100020030A7",
            ),
            HuaweiGestureController.buildGestureStateQuery(route),
        )
        val longPressPackets = listOf(
            Triple(HuaweiGestureSide.LEFT, FreeBudsPro3LongPressAction.NOISE_CONTROL, "5A0006002B16010103AE8D"),
            Triple(HuaweiGestureSide.LEFT, FreeBudsPro3LongPressAction.SONG_RECOGNITION, "5A0006002B1601010E7F20"),
            Triple(HuaweiGestureSide.LEFT, FreeBudsPro3LongPressAction.NONE, "5A0006002B160101FF801E"),
            Triple(HuaweiGestureSide.RIGHT, FreeBudsPro3LongPressAction.NOISE_CONTROL, "5A0006002B16020103F7DD"),
            Triple(HuaweiGestureSide.RIGHT, FreeBudsPro3LongPressAction.SONG_RECOGNITION, "5A0006002B1602010E2670"),
            Triple(HuaweiGestureSide.RIGHT, FreeBudsPro3LongPressAction.NONE, "5A0006002B160201FFD94E"),
        )
        longPressPackets.forEach { (side, action, packet) ->
            assertArrayEquals(
                "$side/$action",
                hex(packet),
                HuaweiGestureController.buildModernEarbudsLongPressPacket(route, side, action),
            )
        }
        assertNull(
            HuaweiGestureController.buildModernEarbudsLongPressPacket(
                route,
                HuaweiGestureSide.LEFT,
                FreeBudsPro3LongPressAction.VOICE_ASSISTANT,
            ),
        )
        assertNull(
            HuaweiGestureController.buildModernEarbudsLongPressPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                HuaweiGestureSide.LEFT,
                FreeBudsPro3LongPressAction.SONG_RECOGNITION,
            ),
        )
        assertNull(
            HuaweiGestureController.buildTripleTapPacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_NEXT,
            ),
        )
    }

    @Test
    fun `FreeBuds 3 legacy double tap packets stay unchanged`() {
        assertArrayEquals(
            hex("5A000600011F0101046307"),
            HuaweiGestureController.buildDoubleTapPacket(
                HuaweiGestureSide.LEFT,
                HuaweiGestureAction.PLAY_NEXT,
            ),
        )
        HuaweiGestureAction.all.forEach { action ->
            assertTrue(
                HuaweiGestureController.supportsDoubleTapAction(
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                    action,
                ),
            )
        }
    }

    @Test
    fun `FreeBuds 6i tap packets use its route-specific action values`() {
        assertArrayEquals(
            hex("5A000600011F01010203C1"),
            HuaweiGestureController.buildFreeBuds6iTapPacket(
                FreeBuds6iTapGesture.DOUBLE_TAP,
                HuaweiGestureSide.LEFT,
                FreeBuds6iTapAction.NEXT_TRACK,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250201021ED3"),
            HuaweiGestureController.buildFreeBuds6iTapPacket(
                FreeBuds6iTapGesture.TRIPLE_TAP,
                HuaweiGestureSide.RIGHT,
                FreeBuds6iTapAction.NEXT_TRACK,
            ),
        )
        assertNull(
            HuaweiGestureController.buildFreeBuds6iTapPacket(
                FreeBuds6iTapGesture.TRIPLE_TAP,
                HuaweiGestureSide.RIGHT,
                FreeBuds6iTapAction.PLAY_PAUSE,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1601010A3FA4"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.LEFT,
                FreeBudsPro3LongPressAction.NOISE_CONTROL,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1E020101525C"),
            HuaweiGestureController.buildSwipePacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                HuaweiGestureSide.RIGHT,
                HuaweiSwipeAction.TRACK_CONTROL,
            ),
        )
    }

    @Test
    fun `FreeClip 2 tap and swipe packets match capture`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREECLIP2
        assertArrayEquals(
            hex("5A000600011F0201070A34"),
            HuaweiGestureController.buildDoubleTapPacket(
                route,
                HuaweiGestureSide.RIGHT,
                HuaweiTapAction.SPATIAL_AUDIO,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250101FF7931"),
            HuaweiGestureController.buildTripleTapPacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.NONE,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1E0101001B2D"),
            HuaweiGestureController.buildSwipePacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiSwipeAction.VOLUME_CONTROL,
            ),
        )
        assertNull(
            HuaweiGestureController.buildSwipePacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiSwipeAction.TRACK_CONTROL,
            ),
        )
    }

    @Test
    fun `FreeClip 2 gesture state query combines all verified read commands`() {
        assertArrayEquals(
            hex(
                "5A000700012001000200E897" +
                    "5A0007000126010002002512" +
                    "5A0007002B1F01000200328A",
            ),
            HuaweiGestureController.buildGestureStateQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP2),
        )
        assertNull(HuaweiGestureController.buildGestureStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
    }

    @Test
    fun `Eyewear 2 double tap and swipe packets match capture`() {
        val route = HuaweiDeviceRoute.HUAWEI_EYEWEAR2
        assertArrayEquals(
            hex("5A000600011F0201007AD3"),
            HuaweiGestureController.buildDoubleTapPacket(
                route,
                HuaweiGestureSide.RIGHT,
                HuaweiTapAction.VOICE_ASSISTANT,
            ),
        )
        assertArrayEquals(
            hex("5A0009002B1E020201020201DB3C"),
            HuaweiGestureController.buildSwipePacket(
                route,
                HuaweiGestureSide.RIGHT,
                HuaweiSwipeAction.TRACK_CONTROL,
            ),
        )
        assertFalse(
            HuaweiGestureController.supportsDoubleTapAction(
                route,
                HuaweiTapAction.PLAY_NEXT,
            ),
        )
    }

    @Test
    fun `FreeBuds Pro 3 packets match capture`() {
        assertArrayEquals(
            hex("5A0006002B1601010A3FA4"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.LEFT,
                FreeBudsPro3LongPressAction.NOISE_CONTROL,
            ),
        )
        assertArrayEquals(
            hex("5A000F002B92010102020102030103040103679D"),
            HuaweiGestureController.buildFreeBudsPro3GestureTogglePacket(
                FreeBudsPro3GestureToggle.MEDIA_PREVIOUS,
                true,
            ),
        )
        assertArrayEquals(
            hex("5A0009002B1E0101FF0202FFC8C8"),
            HuaweiGestureController.buildFreeBudsPro3SwipeVolumePacket(false),
        )
    }

    @Test
    fun `FreeBuds 7i gestures match captured packets`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS7I
        assertArrayEquals(
            hex("5A000600011F01010133A2"),
            HuaweiGestureController.buildDoubleTapPacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_PAUSE,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250101071726"),
            HuaweiGestureController.buildTripleTapPacket(
                route,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_PREVIOUS,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B160201FFD94E"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.RIGHT,
                FreeBudsPro3LongPressAction.NONE,
            ),
        )
        assertArrayEquals(
            hex("5A0009002B1E0101000202009D9B"),
            HuaweiGestureController.buildFreeBudsPro3SwipeVolumePacket(true),
        )
        assertArrayEquals(
            hex(
                "5A000700012001000200E897" +
                    "5A0007000126010002002512" +
                    "5A0007002B1F01000200328A",
            ),
            HuaweiGestureController.buildGestureStateQuery(route),
        )
    }

    @Test
    fun `FreeBuds 6i captured tap packet matrix remains exact`() {
        val packets = listOf(
            Triple(
                FreeBuds6iTapGesture.DOUBLE_TAP to HuaweiGestureSide.LEFT,
                FreeBuds6iTapAction.NEXT_TRACK,
                "5A000600011F01010203C1",
            ),
            Triple(
                FreeBuds6iTapGesture.DOUBLE_TAP to HuaweiGestureSide.RIGHT,
                FreeBuds6iTapAction.PLAY_PAUSE,
                "5A000600011F0201016AF2",
            ),
            Triple(
                FreeBuds6iTapGesture.TRIPLE_TAP to HuaweiGestureSide.LEFT,
                FreeBuds6iTapAction.PREVIOUS_TRACK,
                "5A00060001250101071726",
            ),
            Triple(
                FreeBuds6iTapGesture.TRIPLE_TAP to HuaweiGestureSide.RIGHT,
                FreeBuds6iTapAction.NEXT_TRACK,
                "5A00060001250201021ED3",
            ),
        )

        packets.forEach { (gestureAndSide, action, packet) ->
            val (gesture, side) = gestureAndSide
            assertArrayEquals(
                packet,
                hex(packet),
                HuaweiGestureController.buildFreeBuds6iTapPacket(gesture, side, action),
            )
        }
    }

    @Test
    fun `FreeClip 2 captured tap and swipe packet matrix remains exact`() {
        assertArrayEquals(
            hex("5A000600011F01010203C1"),
            HuaweiGestureController.buildDoubleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_NEXT,
            ),
        )
        assertArrayEquals(
            hex("5A000600011F0201070A34"),
            HuaweiGestureController.buildDoubleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.RIGHT,
                HuaweiTapAction.SPATIAL_AUDIO,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250101071726"),
            HuaweiGestureController.buildTripleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_PREVIOUS,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250201021ED3"),
            HuaweiGestureController.buildTripleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.RIGHT,
                HuaweiTapAction.PLAY_NEXT,
            ),
        )
        assertArrayEquals(
            hex("5A00060001250101FF7931"),
            HuaweiGestureController.buildTripleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.NONE,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1E0101001B2D"),
            HuaweiGestureController.buildSwipePacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.LEFT,
                HuaweiSwipeAction.VOLUME_CONTROL,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1E020100427D"),
            HuaweiGestureController.buildSwipePacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.RIGHT,
                HuaweiSwipeAction.VOLUME_CONTROL,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B1E0101FF05DD"),
            HuaweiGestureController.buildSwipePacket(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                HuaweiGestureSide.LEFT,
                HuaweiSwipeAction.NONE,
            ),
        )
    }

    @Test
    fun `Eyewear 2 captured double tap and swipe packet matrix remains exact`() {
        val doubleTapPackets = listOf(
            Triple(HuaweiGestureSide.LEFT, HuaweiTapAction.PLAY_PAUSE, "5A000600011F01010133A2"),
            Triple(HuaweiGestureSide.LEFT, HuaweiTapAction.VOICE_ASSISTANT, "5A000600011F0101002383"),
            Triple(HuaweiGestureSide.LEFT, HuaweiTapAction.NONE, "5A000600011F0101FF3D73"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiTapAction.PLAY_PAUSE, "5A000600011F0201016AF2"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiTapAction.VOICE_ASSISTANT, "5A000600011F0201007AD3"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiTapAction.NONE, "5A000600011F0201FF6423"),
        )
        doubleTapPackets.forEach { (side, action, packet) ->
            assertArrayEquals(
                packet,
                hex(packet),
                HuaweiGestureController.buildDoubleTapPacket(
                    HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
                    side,
                    action,
                ),
            )
        }

        val swipePackets = listOf(
            Triple(HuaweiGestureSide.LEFT, HuaweiSwipeAction.VOLUME_CONTROL, "5A0009002B1E0101000101009198"),
            Triple(HuaweiGestureSide.LEFT, HuaweiSwipeAction.TRACK_CONTROL, "5A0009002B1E010101010101F70D"),
            Triple(HuaweiGestureSide.LEFT, HuaweiSwipeAction.NONE, "5A0009002B1E0101FF0101FFC4CB"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiSwipeAction.VOLUME_CONTROL, "5A0009002B1E020200020200BDA9"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiSwipeAction.TRACK_CONTROL, "5A0009002B1E020201020201DB3C"),
            Triple(HuaweiGestureSide.RIGHT, HuaweiSwipeAction.NONE, "5A0009002B1E0202FF0202FFE8FA"),
        )
        swipePackets.forEach { (side, action, packet) ->
            assertArrayEquals(
                packet,
                hex(packet),
                HuaweiGestureController.buildSwipePacket(
                    HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
                    side,
                    action,
                ),
            )
        }
    }

    @Test
    fun `FreeBuds Pro 3 captured gesture packet matrix remains exact`() {
        assertArrayEquals(
            hex("5A0006002B1601010A3FA4"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.LEFT,
                FreeBudsPro3LongPressAction.NOISE_CONTROL,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B160201FFD94E"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.RIGHT,
                FreeBudsPro3LongPressAction.NONE,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B16020100C7BE"),
            HuaweiGestureController.buildFreeBudsPro3LongPressPacket(
                HuaweiGestureSide.RIGHT,
                FreeBudsPro3LongPressAction.VOICE_ASSISTANT,
            ),
        )

        FreeBudsPro3GestureToggle.entries.forEach { gesture ->
            val disabled = HuaweiGestureController.buildFreeBudsPro3GestureTogglePacket(gesture, false)
            assertArrayEquals(
                gesture.name,
                byteArrayOf(0x03, 0x01, 0xFF.toByte()),
                disabled.copyOfRange(12, 15),
            )
        }
        assertArrayEquals(
            hex("5A000F002B92010100020101030100040100CA2A"),
            HuaweiGestureController.buildFreeBudsPro3GestureTogglePacket(
                FreeBudsPro3GestureToggle.CALL_ANSWER_END,
                true,
            ),
        )
        assertArrayEquals(
            hex("5A000F002B92010102020102030103040103679D"),
            HuaweiGestureController.buildFreeBudsPro3GestureTogglePacket(
                FreeBudsPro3GestureToggle.MEDIA_PREVIOUS,
                true,
            ),
        )
        assertArrayEquals(
            hex("5A0009002B1E0101000202009D9B"),
            HuaweiGestureController.buildFreeBudsPro3SwipeVolumePacket(true),
        )
        assertArrayEquals(
            hex("5A0009002B1E0101FF0202FFC8C8"),
            HuaweiGestureController.buildFreeBudsPro3SwipeVolumePacket(false),
        )
    }

    @Test
    fun `routes reject packets belonging to another model`() {
        assertNull(
            HuaweiGestureController.buildTripleTapPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.PLAY_NEXT,
            ),
        )
        assertNull(
            HuaweiGestureController.buildDoubleTapPacket(
                HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
                HuaweiGestureSide.LEFT,
                HuaweiTapAction.NOISE_CANCELLATION,
            ),
        )
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
