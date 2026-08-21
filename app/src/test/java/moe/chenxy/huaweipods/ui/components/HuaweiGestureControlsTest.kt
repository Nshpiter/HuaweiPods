package moe.chenxy.huaweipods.ui.components

import moe.chenxy.huaweipods.pods.FreeBudsPro3LongPressAction
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiGestureKind
import moe.chenxy.huaweipods.pods.HuaweiGestureSide
import moe.chenxy.huaweipods.pods.HuaweiSwipeAction
import moe.chenxy.huaweipods.pods.HuaweiTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiGestureControlsTest {
    @Test
    fun `layout exposes only verified controls for each route`() {
        val freeBuds6i = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I)
        assertEquals(
            listOf(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureKind.TRIPLE_TAP),
            freeBuds6i.tapKinds,
        )
        assertTrue(freeBuds6i.hasSwipe)
        assertTrue(freeBuds6i.hasModernLongPressControls)
        assertFalse(freeBuds6i.hasModernSwipeVolumeToggle)
        assertTrue(freeBuds6i.hasWearDetection)

        val freeBuds4e = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS4E)
        assertEquals(listOf(HuaweiGestureKind.DOUBLE_TAP), freeBuds4e.tapKinds)
        assertFalse(freeBuds4e.hasSwipe)
        assertTrue(freeBuds4e.hasFixedSwipeVolume)
        assertTrue(freeBuds4e.hasModernLongPressControls)
        assertFalse(freeBuds4e.hasModernSwipeVolumeToggle)
        assertTrue(freeBuds4e.hasWearDetection)

        val freeBuds5i = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS5I)
        assertEquals(listOf(HuaweiGestureKind.DOUBLE_TAP), freeBuds5i.tapKinds)
        assertFalse(freeBuds5i.hasSwipe)
        assertFalse(freeBuds5i.hasModernLongPressControls)
        assertFalse(freeBuds5i.hasModernSwipeVolumeToggle)
        assertFalse(freeBuds5i.hasWearDetection)

        val freeClip2 = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREECLIP2)
        assertEquals(
            listOf(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureKind.TRIPLE_TAP),
            freeClip2.tapKinds,
        )
        assertTrue(freeClip2.hasSwipe)

        val eyewear2 = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_EYEWEAR2)
        assertEquals(listOf(HuaweiGestureKind.DOUBLE_TAP), eyewear2.tapKinds)
        assertTrue(eyewear2.hasSwipe)

        val pro3 = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3)
        assertTrue(pro3.hasModernLongPressControls)
        assertTrue(pro3.hasModernSwipeVolumeToggle)
        assertTrue(pro3.hasWearDetection)

        val freeBuds7i = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I)
        assertEquals(
            listOf(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureKind.TRIPLE_TAP),
            freeBuds7i.tapKinds,
        )
        assertTrue(freeBuds7i.hasModernLongPressControls)
        assertTrue(freeBuds7i.hasModernSwipeVolumeToggle)

        val freeArc = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEARC)
        assertEquals(
            listOf(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureKind.TRIPLE_TAP),
            freeArc.tapKinds,
        )
        assertTrue(freeArc.hasSwipe)
        assertTrue(freeArc.hasModernLongPressControls)
        assertFalse(freeArc.hasModernSwipeVolumeToggle)
        assertFalse(freeArc.hasWearDetection)
    }

    @Test
    fun `FreeBuds 3 and unsupported gesture routes stay hidden`() {
        val layout = huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS3)
        assertFalse(layout.isVisible)
        assertFalse(huaweiGestureControlLayout(HuaweiDeviceRoute.HUAWEI_FREEBUDS5).isVisible)
    }

    @Test
    fun `preference namespace separates address and route`() {
        val firstAddress = gesturePreferencePrefix(
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            "aa:bb:cc:dd:ee:ff",
        )
        val secondAddress = gesturePreferencePrefix(
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            "11:22:33:44:55:66",
        )
        val otherRoute = gesturePreferencePrefix(
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
            "aa:bb:cc:dd:ee:ff",
        )

        assertNotEquals(firstAddress, secondAddress)
        assertNotEquals(firstAddress, otherRoute)
        assertTrue(firstAddress.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `FreeClip 2 readback maps only route-verified actions`() {
        val state = freeClip2GestureReadback(
            doubleLeft = "play_pause",
            doubleRight = "spatial_audio",
            tripleLeft = "voice_assistant",
            tripleRight = "play_next",
            swipeLeft = "volume_control",
            swipeRight = "track_control",
        )

        assertEquals(
            HuaweiTapAction.PLAY_PAUSE,
            state.tapActions[HuaweiTapSlot(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureSide.LEFT)],
        )
        assertEquals(
            HuaweiTapAction.SPATIAL_AUDIO,
            state.tapActions[HuaweiTapSlot(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureSide.RIGHT)],
        )
        assertFalse(
            state.tapActions.containsKey(
                HuaweiTapSlot(HuaweiGestureKind.TRIPLE_TAP, HuaweiGestureSide.LEFT),
            ),
        )
        assertEquals(
            HuaweiTapAction.PLAY_NEXT,
            state.tapActions[HuaweiTapSlot(HuaweiGestureKind.TRIPLE_TAP, HuaweiGestureSide.RIGHT)],
        )
        assertEquals(HuaweiSwipeAction.VOLUME_CONTROL, state.swipeActions[HuaweiGestureSide.LEFT])
        assertFalse(state.swipeActions.containsKey(HuaweiGestureSide.RIGHT))
    }

    @Test
    fun `partial FreeClip 2 confirmations merge without clearing previous state`() {
        val doubleTap = freeClip2GestureReadback(doubleLeft = "play_pause")
        val swipe = freeClip2GestureReadback(swipeRight = "none")

        val merged = doubleTap.mergedWith(swipe)

        assertEquals(
            HuaweiTapAction.PLAY_PAUSE,
            merged.tapActions[HuaweiTapSlot(HuaweiGestureKind.DOUBLE_TAP, HuaweiGestureSide.LEFT)],
        )
        assertEquals(HuaweiSwipeAction.NONE, merged.swipeActions[HuaweiGestureSide.RIGHT])
    }

    @Test
    fun `FreeBuds 4E readback accepts only its verified long press actions`() {
        val state = huaweiGestureReadback(
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            longPressLeft = "noise_control",
            longPressRight = "song_recognition",
        )

        assertEquals(
            FreeBudsPro3LongPressAction.NOISE_CONTROL,
            state.longPressActions[HuaweiGestureSide.LEFT],
        )
        assertEquals(
            FreeBudsPro3LongPressAction.SONG_RECOGNITION,
            state.longPressActions[HuaweiGestureSide.RIGHT],
        )

        val rejected = huaweiGestureReadback(
            route = HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            longPressLeft = "voice_assistant",
        )
        assertFalse(rejected.longPressActions.containsKey(HuaweiGestureSide.LEFT))
    }

    @Test
    fun `FreeArc readback rejects unsupported noise control and swipe none`() {
        val state = huaweiGestureReadback(
            route = HuaweiDeviceRoute.HUAWEI_FREEARC,
            longPressLeft = "voice_assistant",
            longPressRight = "noise_control",
            swipeLeft = "track_control",
            swipeRight = "none",
        )

        assertEquals(
            FreeBudsPro3LongPressAction.VOICE_ASSISTANT,
            state.longPressActions[HuaweiGestureSide.LEFT],
        )
        assertFalse(state.longPressActions.containsKey(HuaweiGestureSide.RIGHT))
        assertEquals(HuaweiSwipeAction.TRACK_CONTROL, state.swipeActions[HuaweiGestureSide.LEFT])
        assertFalse(state.swipeActions.containsKey(HuaweiGestureSide.RIGHT))
    }
}
