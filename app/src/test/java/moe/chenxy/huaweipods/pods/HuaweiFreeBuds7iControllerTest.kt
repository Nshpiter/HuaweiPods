package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiFreeBuds7iControllerTest {
    @Test
    fun `verified feature packets match the 7i capture`() {
        assertArrayEquals(
            hex("5A0006002B10010100B977"),
            FreeBuds7iBooleanFeature.WEAR_DETECTION.packet(false),
        )
        assertArrayEquals(
            hex("5A0006002B10010101A956"),
            FreeBuds7iBooleanFeature.WEAR_DETECTION.packet(true),
        )
        assertArrayEquals(
            hex("5A0009002BB401010B020100E096"),
            FreeBuds7iBooleanFeature.HEAD_MOTION_CONTROL.packet(false),
        )
        assertArrayEquals(
            hex("5A0009002BB401010B020101F0B7"),
            FreeBuds7iBooleanFeature.HEAD_MOTION_CONTROL.packet(true),
        )
        assertArrayEquals(
            hex("5A0006002BA2010100A5CE"),
            FreeBuds7iBooleanFeature.HIGH_QUALITY_AUDIO.packet(false),
        )
        assertArrayEquals(
            hex("5A0006002BA2010101B5EF"),
            FreeBuds7iBooleanFeature.HIGH_QUALITY_AUDIO.packet(true),
        )
        assertArrayEquals(
            hex("5A0009002BB401011802010240AF"),
            HuaweiFreeBuds7iController.spatialAudioModePacket(
                FreeClip2SpatialAudioMode.HEAD_TRACKING,
            ),
        )
        assertArrayEquals(
            hex("5A0009002BB401011802010170CC"),
            HuaweiFreeBuds7iController.spatialAudioModePacket(
                FreeClip2SpatialAudioMode.FIXED,
            ),
        )
        assertArrayEquals(
            hex("5A0006002B49010109AE12"),
            FreeBuds5SoundEffect.CLEAR_VOICE.packet(),
        )
    }

    @Test
    fun `7i spatial reports keep their captured mode order`() {
        val fixed = HuaweiFreeBuds7iController.parseSpatialAudioState(
            hex("5A000C002BB401011802010103010281DC"),
        )
        val headTracking = HuaweiFreeBuds7iController.parseSpatialAudioState(
            hex("5A000C002BB40101180201020301030A21"),
        )

        assertEquals(FreeClip2SpatialAudioMode.FIXED, fixed?.mode)
        assertEquals(FreeClip2SpatialAudioMode.HEAD_TRACKING, headTracking?.mode)
    }

    @Test
    fun `all 7i state queries match the capture`() {
        assertArrayEquals(
            hex("5A0005002B110100772A"),
            HuaweiFreeBuds7iController.wearDetectionQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0006002BB401010B289B"),
            HuaweiFreeBuds7iController.headMotionQueryPacket(),
        )
        assertArrayEquals(
            hex("5A000A002BB4010118020003009B3F"),
            HuaweiFreeBuds7iController.spatialAudioQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0005002B4A02008C46"),
            HuaweiFreeBuds7iController.soundEffectQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0005002BA30101F794"),
            HuaweiFreeBuds7iController.highQualityAudioQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0008002B3101000D0100AEEE"),
            HuaweiFreeBuds7iController.dualDeviceQueryPacket(),
        )
    }

    @Test
    fun `head motion parser accepts verified values and rejects corrupt frames`() {
        assertFalse(
            HuaweiFreeBuds7iController.parseHeadMotionState(
                hex("5A000F002BB401010B0201000301010401027D3A"),
            )!!,
        )
        assertTrue(
            HuaweiFreeBuds7iController.parseHeadMotionState(
                hex("5A000F002BB401010B020101030101040102C55B"),
            )!!,
        )
        assertNull(
            HuaweiFreeBuds7iController.parseHeadMotionState(
                hex("5A000F002BB401010B020101030101040102C55A"),
            ),
        )
    }

    @Test
    fun `custom equalizer packet matches the captured reset preset`() {
        assertArrayEquals(
            hex(
                "5A0028002B4901016402010A050100030A00000000000000000000" +
                    "040EE68891E79A84E99FB3E695882031DB88",
            ),
            HuaweiFreeBuds7iController.buildCustomEqualizerPacket(
                gains = List(10) { 0 },
                presetName = "我的音效 1",
            ),
        )
        assertNull(HuaweiFreeBuds7iController.buildCustomEqualizerPacket(List(9) { 0 }))
        assertNull(HuaweiFreeBuds7iController.buildCustomEqualizerPacket(List(10) { 61 }))
    }

    @Test
    fun `dual device parser and removal packet match capture`() {
        val stream = hex(
            "5A0029002B310201040301030406AABBCCDDEEFF050101060101070100080101" +
                "090750484F4E452D410A01014729" +
                "5A0029002B310201040301020406112233445566050100060104070100080101" +
                "090750484F4E452D420A01014F90",
        )
        val devices = HuaweiFreeBuds7iController.parseDualDevices(stream)
        assertEquals(2, devices.size)
        assertEquals("AA:BB:CC:DD:EE:FF", devices[0].address)
        assertEquals("PHONE-A", devices[0].name)
        assertTrue(devices[0].connected)
        assertEquals("11:22:33:44:55:66", devices[1].address)
        assertEquals("PHONE-B", devices[1].name)
        assertFalse(devices[1].connected)
        assertArrayEquals(
            hex("5A000B002B3104061122334455660557"),
            HuaweiFreeBuds7iController.buildRemoveDualDevicePacket("11:22:33:44:55:66"),
        )
        assertNull(HuaweiFreeBuds7iController.buildRemoveDualDevicePacket("not-an-address"))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
