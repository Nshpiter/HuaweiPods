package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeClip2AudioUiPolicyTest {
    @Test
    fun `valid broadcast values update only supplied fields`() {
        val current = FreeClip2AudioUiState(
            spatialMode = FreeClip2SpatialAudioMode.FIXED,
            spatialScene = FreeClip2SpatialScene.CINEMA,
            soundEffect = FreeClip2SoundEffect.DEFAULT,
        )

        val updated = current.mergeExtraValues(
            spatialModeValue = FreeClip2SpatialAudioMode.HEAD_TRACKING.extraValue,
            spatialSceneValue = null,
            soundEffectValue = FreeClip2SoundEffect.CLEAR_VOICE.extraValue,
        )

        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, updated.spatialMode)
        assertEquals(FreeClip2SpatialScene.CINEMA, updated.spatialScene)
        assertEquals(FreeClip2SoundEffect.CLEAR_VOICE, updated.soundEffect)
    }

    @Test
    fun `unknown broadcast values do not erase confirmed state`() {
        val current = FreeClip2AudioUiState(
            spatialMode = FreeClip2SpatialAudioMode.FIXED,
            spatialScene = FreeClip2SpatialScene.CONCERT_HALL,
            soundEffect = FreeClip2SoundEffect.SPORT_ENHANCE,
        )

        assertEquals(
            current,
            current.mergeExtraValues("unknown", "", "future_custom"),
        )
    }

    @Test
    fun `valid selection updates local UI state immediately`() {
        val current = FreeClip2AudioUiState(spatialMode = FreeClip2SpatialAudioMode.OFF)

        val selected = current.withSelection("spatial_mode", "head_tracking")

        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, selected?.spatialMode)
        assertNull(current.withSelection("spatial_mode", "unknown"))
    }

    @Test
    fun `official custom EQ is preserved as a read only UI state`() {
        val updated = FreeClip2AudioUiState().mergeExtraValues(
            spatialModeValue = null,
            spatialSceneValue = null,
            soundEffectValue = FreeClip2SoundEffect.CUSTOM.extraValue,
        )

        assertEquals(FreeClip2SoundEffect.CUSTOM, updated.soundEffect)
        assertFalse(updated.soundEffect.isSelectable)
    }

    @Test
    fun `preference prefix is stable per bluetooth address`() {
        assertEquals(
            "freeclip2_audio_AA:BB:CC:DD:EE:FF_",
            freeClip2AudioPreferencePrefix("aa:bb:cc:dd:ee:ff", "HUAWEI FC2"),
        )
        assertEquals(
            "freeclip2_audio_name:HUAWEI FC2_",
            freeClip2AudioPreferencePrefix(null, "HUAWEI FC2"),
        )
        assertNull(freeClip2AudioPreferencePrefix("", ""))
    }

    @Test
    fun `pending gate deduplicates hook reentry until confirmed`() {
        val gate = FreeClip2AudioPendingGate(timeoutMs = 5_000L)

        assertTrue(gate.tryBegin("spatial_mode", "head_tracking", nowMs = 100L))
        assertFalse(gate.tryBegin("spatial_mode", "head_tracking", nowMs = 200L))
        gate.observeConfirmed(
            spatialModeValue = "head_tracking",
            spatialSceneValue = null,
            soundEffectValue = null,
        )
        assertTrue(gate.tryBegin("spatial_mode", "head_tracking", nowMs = 300L))
    }

    @Test
    fun `unrelated partial confirmation keeps pending selection`() {
        val gate = FreeClip2AudioPendingGate()
        gate.tryBegin("sound_effect", "clear_voice", nowMs = 100L)

        gate.observeConfirmed(
            spatialModeValue = "fixed",
            spatialSceneValue = "default",
            soundEffectValue = null,
        )

        assertEquals("sound_effect", gate.current()?.kind)
        gate.observeConfirmed(null, null, "clear_voice")
        assertNull(gate.current())
    }

    @Test
    fun `stale confirmation cannot replace pending spatial selection`() {
        val gate = FreeClip2AudioPendingGate()
        gate.tryBegin("spatial_mode", "head_tracking", nowMs = 100L)

        assertFalse(gate.shouldApplyConfirmed("spatial_mode", "fixed"))
        assertEquals("head_tracking", gate.current()?.value)
        assertTrue(gate.shouldApplyConfirmed("spatial_mode", "head_tracking"))
        assertNull(gate.current())
    }

    @Test
    fun `programmatic MiLink render never dispatches a device selection`() {
        assertTrue(shouldDispatchFreeClip2AudioSelection(internalRenderDepth = 0))
        assertFalse(shouldDispatchFreeClip2AudioSelection(internalRenderDepth = 1))
        assertFalse(shouldDispatchFreeClip2AudioSelection(internalRenderDepth = 2))
    }
}
