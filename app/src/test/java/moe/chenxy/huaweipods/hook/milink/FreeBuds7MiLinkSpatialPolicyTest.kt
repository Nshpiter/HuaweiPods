package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import org.junit.Assert.*
import org.junit.Test

class FreeBuds7MiLinkSpatialPolicyTest {
    @Test fun failedWriteReadbackRestoresOriginalModeWithinPendingWindowAndAllowsRetry() {
        val state = FreeBuds7MiLinkSpatialState()
        state.confirm(FreeClip2SpatialAudioMode.FIXED)
        assertTrue(state.request(FreeClip2SpatialAudioMode.HEAD_TRACKING, 1_000L))
        assertFalse(state.request(FreeClip2SpatialAudioMode.HEAD_TRACKING, 1_100L))

        // The write failed: the Bluetooth bridge reads back FIXED before five seconds.
        state.confirm(FreeClip2SpatialAudioMode.FIXED)
        assertEquals(1, FreeBuds7MiLinkSpatialPolicy.display(state.mode))
        assertEquals(1, FreeBuds7MiLinkSpatialPolicy.runtime(state.mode))
        assertTrue(state.request(FreeClip2SpatialAudioMode.HEAD_TRACKING, 1_200L))
        state.confirm(FreeClip2SpatialAudioMode.HEAD_TRACKING)
        assertEquals(2, FreeBuds7MiLinkSpatialPolicy.display(state.mode))
        assertEquals(11, FreeBuds7MiLinkSpatialPolicy.runtime(state.mode))
    }

    @Test fun pendingWriteDoesNotOverrideConfirmedModeAndExpiresForRetry() {
        val state = FreeBuds7MiLinkSpatialState()
        state.confirm(FreeClip2SpatialAudioMode.OFF)
        assertTrue(state.request(FreeClip2SpatialAudioMode.FIXED, 1_000L))
        assertEquals(FreeClip2SpatialAudioMode.OFF, state.mode)
        assertFalse(state.request(FreeClip2SpatialAudioMode.FIXED, 5_999L))
        assertTrue(state.request(FreeClip2SpatialAudioMode.FIXED, 6_000L))
        state.clear()
        assertNull(state.mode)
        assertTrue(state.request(FreeClip2SpatialAudioMode.FIXED, 6_001L))
    }

    @Test fun nativeButtonsDoNotReuseReversedHuaweiWireValues() {
        assertEquals(FreeClip2SpatialAudioMode.FIXED, FreeBuds7MiLinkSpatialPolicy.fromDisplay(1))
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, FreeBuds7MiLinkSpatialPolicy.fromDisplay(2))
        assertEquals(1, FreeBuds7MiLinkSpatialPolicy.display(FreeClip2SpatialAudioMode.FIXED))
        assertEquals(2, FreeBuds7MiLinkSpatialPolicy.display(FreeClip2SpatialAudioMode.HEAD_TRACKING))
        assertEquals(11, FreeBuds7MiLinkSpatialPolicy.runtime(FreeClip2SpatialAudioMode.HEAD_TRACKING))
        assertEquals(0, FreeBuds7MiLinkSpatialPolicy.runtime(FreeClip2SpatialAudioMode.OFF))
    }

    @Test fun unknownOrInvalidStateDoesNotBecomeOff() {
        assertEquals(-1, FreeBuds7MiLinkSpatialPolicy.display(null))
        assertEquals(-1, FreeBuds7MiLinkSpatialPolicy.runtime(null))
        assertNull(FreeBuds7MiLinkSpatialPolicy.fromDisplay(-1))
        assertNull(FreeBuds7MiLinkSpatialPolicy.fromDisplay(11))
    }

    @Test fun nativePercentageSiblingSuppressesDuplicateVolumeText() {
        assertTrue(FreeClip2MiLinkUiPolicy.hasNativeVolumePercentage(listOf("| 23%")))
        assertTrue(FreeClip2MiLinkUiPolicy.hasNativeVolumePercentage(listOf("23%")))
        assertFalse(FreeClip2MiLinkUiPolicy.hasNativeVolumePercentage(listOf("音量", "调节音量")))
        assertFalse(FreeClip2MiLinkUiPolicy.hasNativeVolumePercentage(emptyList()))
    }
}
