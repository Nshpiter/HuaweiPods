package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiFreeClipControllerTest {
    @Test
    fun `adaptive channel packets match FreeClip capture`() {
        assertArrayEquals(
            hex("5A0005002B9A0100BC81"),
            HuaweiFreeClipController.adaptiveChannelsQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0006002B990101009738"),
            HuaweiFreeClipController.adaptiveChannelsPacket(false),
        )
        assertArrayEquals(
            hex("5A0006002B990101018719"),
            HuaweiFreeClipController.adaptiveChannelsPacket(true),
        )
    }

    @Test
    fun `drop reminder packets match FreeClip capture`() {
        assertArrayEquals(
            hex("5A0008002BB40101070200DDE9"),
            HuaweiFreeClipController.dropReminderQueryPacket(),
        )
        assertArrayEquals(
            hex("5A0009002BB4010107020100AFA4"),
            HuaweiFreeClipController.dropReminderPacket(false),
        )
        assertArrayEquals(
            hex("5A0009002BB4010107020101BF85"),
            HuaweiFreeClipController.dropReminderPacket(true),
        )
    }

    @Test
    fun `parses latest adaptive channel state from a concatenated read`() {
        val stream = hex(
            "5A0006002B9A0101011CC5" +
                "5A0009002B107F04000186A0729D" +
                "5A0006002B9A0101000CE4",
        )

        assertEquals(false, HuaweiFreeClipController.parseAdaptiveChannelsState(stream))
    }

    @Test
    fun `parses latest drop reminder state from a concatenated read`() {
        val stream = hex(
            "5A0009002BB4010107020100AFA4" +
                "5A0006002B88010100FA2B" +
                "5A0009002BB4010107020101BF85",
        )

        assertEquals(true, HuaweiFreeClipController.parseDropReminderState(stream))
    }

    @Test
    fun `rejects unrelated or unknown FreeClip boolean states`() {
        assertNull(
            HuaweiFreeClipController.parseAdaptiveChannelsState(
                hex("5A0006002B9A0101022CA7"),
            ),
        )
        assertNull(
            HuaweiFreeClipController.parseDropReminderState(
                hex("5A0009002BB401010B020101F0B7"),
            ),
        )
        assertTrue(HuaweiEqualizerCodec.supportsStateRead(HuaweiDeviceRoute.HUAWEI_FREECLIP))
        assertNull(HuaweiEqualizerCodec.customWriteOperation(HuaweiDeviceRoute.HUAWEI_FREECLIP))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
