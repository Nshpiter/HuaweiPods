package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioFreeClip2BridgePolicyTest {
    @Test
    fun `accepted official write never falls back to competing RFCOMM connection`() {
        assertFalse(shouldFallbackAfterSmartAudioBridgeTimeout(accepted = true))
        assertTrue(shouldFallbackAfterSmartAudioBridgeTimeout(accepted = false))
    }
    @Test
    fun `official Smart Audio mode order is mapped without swapping fixed and tracking`() {
        assertEquals(0, SmartAudioFreeClip2BridgePolicy.officialModeFor(FreeClip2SpatialAudioMode.OFF))
        assertEquals(2, SmartAudioFreeClip2BridgePolicy.officialModeFor(FreeClip2SpatialAudioMode.FIXED))
        assertEquals(1, SmartAudioFreeClip2BridgePolicy.officialModeFor(FreeClip2SpatialAudioMode.HEAD_TRACKING))
        assertEquals(FreeClip2SpatialAudioMode.OFF, SmartAudioFreeClip2BridgePolicy.modeFromOfficial(0))
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, SmartAudioFreeClip2BridgePolicy.modeFromOfficial(1))
        assertEquals(FreeClip2SpatialAudioMode.FIXED, SmartAudioFreeClip2BridgePolicy.modeFromOfficial(2))
        assertNull(SmartAudioFreeClip2BridgePolicy.modeFromOfficial(3))
        FreeClip2SpatialAudioMode.entries.forEach { mode ->
            assertEquals(mode.protocolValue, SmartAudioFreeClip2BridgePolicy.officialModeFor(mode))
        }
    }

    @Test
    fun `bridge identities fail closed`() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            SmartAudioFreeClip2BridgePolicy.normalizeAddress("aa:bb:cc:dd:ee:ff"),
        )
        assertNull(SmartAudioFreeClip2BridgePolicy.normalizeAddress("not-a-mac"))
        assertTrue(
            SmartAudioFreeClip2BridgePolicy.isTrustedRequestSender("com.android.bluetooth"),
        )
        assertFalse(
            SmartAudioFreeClip2BridgePolicy.isTrustedRequestSender("moe.chenxy.huaweipods"),
        )
        assertTrue(
            SmartAudioFreeClip2BridgePolicy.isTrustedEqualizerRequestSender(
                "moe.chenxy.huaweipods",
            ),
        )
        assertFalse(
            SmartAudioFreeClip2BridgePolicy.isTrustedEqualizerRequestSender(
                "com.android.bluetooth",
            ),
        )
        assertTrue(
            SmartAudioFreeClip2BridgePolicy.isTrustedResultSender("com.huawei.smartaudio"),
        )
        assertFalse(SmartAudioFreeClip2BridgePolicy.isTrustedRequestSender(null))
        assertFalse(SmartAudioFreeClip2BridgePolicy.isTrustedResultSender("com.example.fake"))
    }

    @Test
    fun `official EQ modes preserve supported presets and expose custom modes`() {
        assertEquals(
            FreeClip2SoundEffect.DEFAULT,
            SmartAudioFreeClip2BridgePolicy.soundEffectFromOfficial(1),
        )
        assertEquals(
            FreeClip2SoundEffect.SPORT_ENHANCE,
            SmartAudioFreeClip2BridgePolicy.soundEffectFromOfficial(10),
        )
        assertEquals(
            FreeClip2SoundEffect.CUSTOM,
            SmartAudioFreeClip2BridgePolicy.soundEffectFromOfficial(100),
        )
        assertEquals(
            FreeClip2SoundEffect.CUSTOM,
            SmartAudioFreeClip2BridgePolicy.soundEffectFromOfficial(18),
        )
    }
}
