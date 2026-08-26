package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiFreeBudsPro5ControllerTest {
    @Test
    fun `settings query packets match the Pro 5 capture`() {
        val expected = listOf(
            "5A0008002BB401010202003619" to HuaweiFreeBudsPro5Controller.adaptiveVolumeQueryPacket(),
            "5A0006002BB401010B289B" to HuaweiFreeBudsPro5Controller.headMotionQueryPacket(),
            "5A0008002BB401010302000129" to HuaweiFreeBudsPro5Controller.voiceControlQueryPacket(),
            "5A000A002BB4010118020003009B3F" to HuaweiFreeBudsPro5Controller.spatialAudioQueryPacket(),
            "5A0005002BA30101F794" to HuaweiFreeBudsPro5Controller.highQualityAudioQueryPacket(),
            "5A0005002B2F0100A98E" to HuaweiFreeBudsPro5Controller.dualDeviceQueryPacket(),
            "5A0006002BB40101108BC1" to HuaweiFreeBudsPro5Controller.casePromptSoundQueryPacket(),
            "5A0008002BB40101080200F1D8" to HuaweiFreeBudsPro5Controller.earTipMaterialQueryPacket(),
        )

        expected.forEach { (packet, actual) -> assertArrayEquals(hex(packet), actual) }
    }

    @Test
    fun `boolean feature writes match the Pro 5 capture`() {
        val expected = mapOf(
            FreeBudsPro5BooleanFeature.ADAPTIVE_VOLUME to
                ("5A0009002BB401010202010013E1" to "5A0009002BB401010202010103C0"),
            FreeBudsPro5BooleanFeature.HEAD_MOTION_CONTROL to
                ("5A0009002BB401010B020100E096" to "5A0009002BB401010B020101F0B7"),
            FreeBudsPro5BooleanFeature.VOICE_CONTROL to
                ("5A0009002BB40101030201006555" to "5A0009002BB40101030201017574"),
            FreeBudsPro5BooleanFeature.HIGH_QUALITY_AUDIO to
                ("5A0006002BA2010100A5CE" to "5A0006002BA2010101B5EF"),
            FreeBudsPro5BooleanFeature.DUAL_DEVICE to
                ("5A0006002B2E01010037C4" to "5A0006002B2E01010127E5"),
            FreeBudsPro5BooleanFeature.CASE_PROMPT_SOUND to
                ("5A0006002BB101010025B5" to "5A0006002BB10101013594"),
        )

        expected.forEach { (feature, packets) ->
            assertArrayEquals(feature.name, hex(packets.first), feature.packet(false))
            assertArrayEquals(feature.name, hex(packets.second), feature.packet(true))
        }
    }

    @Test
    fun `spatial audio and ear tip writes match the Pro 5 capture`() {
        assertArrayEquals(
            hex("5A0009002BB401011802010060ED"),
            FreeClip2SpatialAudioMode.OFF.packet(),
        )
        assertArrayEquals(
            hex("5A0009002BB401011802010240AF"),
            FreeClip2SpatialAudioMode.FIXED.packet(),
        )
        assertArrayEquals(
            hex("5A0009002BB401011802010170CC"),
            FreeClip2SpatialAudioMode.HEAD_TRACKING.packet(),
        )
        assertArrayEquals(
            hex("5A0009002BB40101080201016B6B"),
            FreeBudsPro5EarTipMaterial.SILICONE.packet(),
        )
        assertArrayEquals(
            hex("5A0009002BB40101080201025B08"),
            FreeBudsPro5EarTipMaterial.MEMORY_FOAM.packet(),
        )
    }

    @Test
    fun `sound effect order and packets match the guided Pro 5 capture`() {
        val expected = listOf(
            FreeBudsPro5SoundEffect.YUEZHANG_BALANCED to "5A0006002B490101056F9E",
            FreeBudsPro5SoundEffect.YUEZHANG_VOCAL to "5A0006002B49010109AE12",
            FreeBudsPro5SoundEffect.YUEZHANG_BASS to "5A0006002B490101021F79",
            FreeBudsPro5SoundEffect.YUEZHANG_CLASSICAL to
                "5A001D002B490101C902010A050101030AFB141E0A0000E7F60A000403323031C367",
            FreeBudsPro5SoundEffect.MOVIE to "5A0006002B4901010DEE96",
            FreeBudsPro5SoundEffect.PODCAST_VOICE to "5A0006002B4901010FCED4",
            FreeBudsPro5SoundEffect.GAME to "5A0006002B4901010EDEF5",
            FreeBudsPro5SoundEffect.SPORT to "5A0006002B490101102D0A",
            FreeBudsPro5SoundEffect.AI to "5A0006002B490101113D2B",
        )

        assertEquals(expected.map { it.first }, FreeBudsPro5SoundEffect.entries)
        expected.forEach { (effect, packet) ->
            assertArrayEquals(effect.name, hex(packet), effect.packet())
            assertEquals(effect, FreeBudsPro5SoundEffect.fromProtocolValue(effect.protocolValue))
        }
    }

    @Test
    fun `settings parsers accept captured reports and reject bad crc`() {
        assertEquals(
            1,
            HuaweiFreeBudsPro5Controller.parseAamFeatureValue(
                hex("5A0009002BB401010202010103C0"),
                featureId = 0x02,
            ),
        )
        assertEquals(
            1,
            HuaweiFreeBudsPro5Controller.parseAamFeatureValue(
                hex("5A000D002BB401010302020100030101B3FD"),
                featureId = 0x03,
            ),
        )
        assertEquals(
            0,
            HuaweiFreeBudsPro5Controller.parseAamFeatureValue(
                hex("5A000D002BB40101030202000003010119AC"),
                featureId = 0x03,
            ),
        )
        assertEquals(
            1,
            HuaweiFreeBudsPro5Controller.parseAamFeatureValue(
                hex("5A0014002BB4010110020C01010F000000000000000000DB1D"),
                featureId = 0x10,
            ),
        )
        assertEquals(
            1,
            HuaweiFreeBudsPro5Controller.parseAamFeatureValue(
                hex("5A0009002BB40101080201016B6B"),
                featureId = 0x08,
            ),
        )
        assertTrue(
            HuaweiFreeBudsPro5Controller.parseBooleanField(
                hex("5A0006002B2F0101015151"),
                service = 0x2B,
                command = 0x2F,
                field = 0x01,
            ) == true,
        )

        val corrupted = hex("5A0009002BB401010202010103C0").also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        }
        assertNull(HuaweiFreeBudsPro5Controller.parseAamFeatureValue(corrupted, featureId = 0x02))
        assertNull(
            HuaweiFreeBudsPro5Controller.parseBooleanField(
                hex("5A0006002B2F0101026132"),
                service = 0x2B,
                command = 0x2F,
                field = 0x01,
            ),
        )
    }

    @Test
    fun `partial settings reports merge without clearing previous state`() {
        val current = FreeBudsPro5SettingsState(
            adaptiveVolume = true,
            voiceControl = false,
            dualDevice = true,
            earTipMaterial = FreeBudsPro5EarTipMaterial.SILICONE,
        )
        val merged = mergeFreeBudsPro5SettingsState(
            current,
            FreeBudsPro5SettingsState(
                adaptiveVolume = false,
                spatialAudioMode = FreeClip2SpatialAudioMode.HEAD_TRACKING,
            ),
        )

        assertFalse(merged.adaptiveVolume ?: true)
        assertFalse(merged.voiceControl ?: true)
        assertTrue(merged.dualDevice == true)
        assertEquals(FreeBudsPro5EarTipMaterial.SILICONE, merged.earTipMaterial)
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, merged.spatialAudioMode)
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
