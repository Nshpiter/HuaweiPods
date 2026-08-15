package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HuaweiFreeClip2ControllerTest {
    @Test
    fun `boolean feature packets match the guided capture`() {
        assertPacket("5A0006002B10010100B977", FreeClip2BooleanFeature.WEAR_DETECTION.packet(false))
        assertPacket("5A0006002B10010101A956", FreeClip2BooleanFeature.WEAR_DETECTION.packet(true))
        assertPacket("5A0009002BB4010107020100AFA4", FreeClip2BooleanFeature.DROP_REMINDER.packet(false))
        assertPacket("5A0009002BB4010107020101BF85", FreeClip2BooleanFeature.DROP_REMINDER.packet(true))
        assertPacket("5A0009002BB401010202010013E1", FreeClip2BooleanFeature.ADAPTIVE_VOLUME.packet(false))
        assertPacket("5A0009002BB401010202010103C0", FreeClip2BooleanFeature.ADAPTIVE_VOLUME.packet(true))
        assertPacket("5A0009002BB401010B020100E096", FreeClip2BooleanFeature.HEAD_MOTION_CONTROL.packet(false))
        assertPacket("5A0009002BB401010B020101F0B7", FreeClip2BooleanFeature.HEAD_MOTION_CONTROL.packet(true))
        assertPacket("5A0006002B870101002EC5", FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY.packet(false))
        assertPacket("5A0006002B870101013EE4", FreeClip2BooleanFeature.SOUND_QUALITY_PRIORITY.packet(true))
        assertPacket("5A0006002B6C010100B430", FreeClip2BooleanFeature.LOW_LATENCY.packet(false))
        assertPacket("5A0006002B6C010101A411", FreeClip2BooleanFeature.LOW_LATENCY.packet(true))
        assertPacket("5A0006002B2E01010037C4", FreeClip2BooleanFeature.DUAL_DEVICE.packet(false))
        assertPacket("5A0006002B2E01010127E5", FreeClip2BooleanFeature.DUAL_DEVICE.packet(true))
        assertPacket("5A0006002BB101010025B5", FreeClip2BooleanFeature.CASE_PROMPT_SOUND.packet(false))
        assertPacket("5A0006002BB10101013594", FreeClip2BooleanFeature.CASE_PROMPT_SOUND.packet(true))
    }

    @Test
    fun `spatial audio modes and scenes match the guided capture`() {
        assertPacket("5A0009002BB401011802010060ED", FreeClip2SpatialAudioMode.OFF.packet())
        assertPacket("5A0009002BB401011802010240AF", FreeClip2SpatialAudioMode.FIXED.packet())
        assertPacket("5A0009002BB401011802010170CC", FreeClip2SpatialAudioMode.HEAD_TRACKING.packet())
        assertPacket("5A0009002BB401011803010057DD", FreeClip2SpatialScene.DEFAULT.packet())
        assertPacket("5A0009002BB401011803010147FC", FreeClip2SpatialScene.AUDIO_THEATER.packet())
        assertPacket("5A0009002BB4010118030102779F", FreeClip2SpatialScene.CINEMA.packet())
        assertPacket("5A0009002BB401011803010367BE", FreeClip2SpatialScene.CONCERT_HALL.packet())
    }

    @Test
    fun `sound effect presets match the guided capture`() {
        assertPacket("5A0006002B490101012F1A", FreeClip2SoundEffect.DEFAULT.packet())
        assertPacket("5A0006002B4901010A9E71", FreeClip2SoundEffect.SPORT_ENHANCE.packet())
        assertPacket("5A0006002B490101030F58", FreeClip2SoundEffect.TREBLE_ENHANCE.packet())
        assertPacket("5A0006002B49010109AE12", FreeClip2SoundEffect.CLEAR_VOICE.packet())
        assertFalse(FreeClip2SoundEffect.CUSTOM.isSelectable)
        assertThrows(IllegalArgumentException::class.java) {
            FreeClip2SoundEffect.CUSTOM.packet()
        }
    }

    @Test
    fun `state query packets match the captured protocol`() {
        assertPacket(
            "5A000A002BB4010118020003009B3F",
            HuaweiFreeClip2Controller.spatialAudioStateQueryPacket(),
        )
        assertPacket(
            "5A0005002B4A02008C46",
            HuaweiFreeClip2Controller.soundEffectStateQueryPacket(),
        )
    }

    @Test
    fun `parses latest spatial state from a concatenated RFCOMM read`() {
        val unrelated = hex("5A0006002B040201003171")
        val older = hex("5A000C002BB4010118020100030100D72A")
        val latest = hex("5A000C002BB40101180201020301030A21")

        val state = HuaweiFreeClip2Controller.parseSpatialAudioState(
            byteArrayOf(0x01, 0x02) + unrelated + older + latest,
        )

        assertEquals(FreeClip2SpatialAudioMode.FIXED, state?.mode)
        assertEquals(FreeClip2SpatialScene.CONCERT_HALL, state?.scene)
        assertNull(state?.effect)
    }

    @Test
    fun `parses latest built-in sound effect from a concatenated RFCOMM read`() {
        val sport = hex("5A0014002B4A01010102010A0304010A0309040101080074C0")
        val clearVoice = hex("5A0014002B4A0101010201090304010A03090401010800715F")

        val state = HuaweiFreeClip2Controller.parseSoundEffectState(sport + clearVoice)

        assertEquals(FreeClip2SoundEffect.CLEAR_VOICE, state?.effect)
        assertNull(state?.mode)
        assertNull(state?.scene)
    }

    @Test
    fun `parses a verified custom equalizer without pretending it is a built in preset`() {
        val state = HuaweiFreeClip2Controller.parseSoundEffectState(
            hex(FREEBUDS6I_CUSTOM_EQ_STATE),
        )

        requireNotNull(state)
        assertEquals(FreeClip2SoundEffect.CUSTOM, state.effect)
        assertEquals(0x64, state.equalizer?.selectedId)
        assertEquals("全频校准", state.equalizer?.selectedName)
        assertEquals(
            listOf(40, 20, 10, 0, 10, 35, 25, 0, 10, 20),
            state.equalizer?.selectedGains,
        )
    }

    @Test
    fun `combines spatial and sound effect state from one RFCOMM read`() {
        val spatial = hex("5A000C002BB401011802010103010281DC")
        val effect = hex("5A0014002B4A0101010201010304010A030904010108006AF7")

        val state = HuaweiFreeClip2Controller.parseAudioState(spatial + effect)

        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, state?.mode)
        assertEquals(FreeClip2SpatialScene.CINEMA, state?.scene)
        assertEquals(FreeClip2SoundEffect.DEFAULT, state?.effect)
    }

    @Test
    fun `rejects unknown or truncated audio state frames`() {
        assertNull(
            HuaweiFreeClip2Controller.parseSpatialAudioState(
                frameWithCrc("5A000C002BB401011802017F030100"),
            ),
        )
        assertNull(
            HuaweiFreeClip2Controller.parseSoundEffectState(
                frameWithCrc("5A0014002B4A01010102017F0304010A03090401010800"),
            ),
        )
        assertNull(
            HuaweiFreeClip2Controller.parseSpatialAudioState(
                hex("5A000C002BB4010118020102"),
            ),
        )
    }

    @Test
    fun `rejects structurally valid state frames with bad CRC`() {
        val spatial = hex("5A000C002BB40101180201020301030A21").also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        }
        val effect = hex("5A0014002B4A0101010201090304010A03090401010800715F").also {
            it[it.lastIndex - 1] = (it[it.lastIndex - 1].toInt() xor 0x01).toByte()
        }

        assertNull(HuaweiFreeClip2Controller.parseSpatialAudioState(spatial))
        assertNull(HuaweiFreeClip2Controller.parseSoundEffectState(effect))
    }

    @Test
    fun `skips bad CRC frame and resynchronizes to following verified frame`() {
        val corrupt = hex("5A000C002BB40101180201010301028100")
        val verified = hex("5A000C002BB40101180201020301030A21")

        val state = HuaweiFreeClip2Controller.parseSpatialAudioState(corrupt + verified)

        assertEquals(FreeClip2SpatialAudioMode.FIXED, state?.mode)
        assertEquals(FreeClip2SpatialScene.CONCERT_HALL, state?.scene)
    }

    @Test
    fun `enum extra values round trip for broadcasts`() {
        FreeClip2SpatialAudioMode.entries.forEach {
            assertEquals(it, FreeClip2SpatialAudioMode.fromExtraValue(it.extraValue))
        }
        FreeClip2SpatialScene.entries.forEach {
            assertEquals(it, FreeClip2SpatialScene.fromExtraValue(it.extraValue))
        }
        FreeClip2SoundEffect.entries.forEach {
            assertEquals(it, FreeClip2SoundEffect.fromExtraValue(it.extraValue))
        }
    }

    @Test
    fun `FreeClip 2 AAM actual modes use the verified official mapping`() {
        assertEquals(2, FreeClip2SpatialAudioMode.FIXED.protocolValue)
        assertEquals(1, FreeClip2SpatialAudioMode.HEAD_TRACKING.protocolValue)
        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            FreeClip2SpatialAudioMode.fromStateReportValue(1),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.FIXED,
            FreeClip2SpatialAudioMode.fromStateReportValue(2),
        )
    }

    @Test
    fun `packets are returned as defensive copies`() {
        val first = FreeClip2SpatialAudioMode.FIXED.packet()
        first[0] = 0
        val second = FreeClip2SpatialAudioMode.FIXED.packet()
        assertFalse(first.contentEquals(second))
        assertPacket("5A0009002BB401011802010240AF", second)
    }

    private fun assertPacket(expected: String, actual: ByteArray) {
        assertArrayEquals(hex(expected), actual)
    }

    private fun frameWithCrc(withoutCrc: String): ByteArray {
        val payload = hex(withoutCrc)
        var crc = 0
        payload.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return payload + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val FREEBUDS6I_CUSTOM_EQ_STATE =
            "5A00A9002B4A01010102016403040102030904010105010A060A28140A000A2319000A14" +
                "0718E585A8E9A291E6A0A1E58786000000000000000000000000086C640A28140A000A" +
                "2319000A14E585A8E9A291E6A0A1E58786000000000000000000000000650A2314EC0A" +
                "0AECEC00323CE6B581E8A18CE9A38EE59091000000000000000000000000660A0A0A0A" +
                "0A00F1F1001414E59D87E8A1A1E4BABAE5A3B0000000000000000000000000CA62"
    }
}
