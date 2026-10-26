package moe.chenxy.huaweipods.pods

import org.junit.Assert.*
import org.junit.Test

/** 2026-07-29 FreeClip 2 实际 RX，去除设备身份与其他无关帧。 */
class FreeClip2BooleanReadbackTest {
    private val replies = hex(
        "5A0006002B11010101DFE2" +
            "5A0009002BB4010107020101BF85" +
            "5A000C002BB4010102020101030101758A" +
            "5A0006002B6C020100ED60" +
            "5A0006002B2F0101004170" +
            "5A0009002BB1020101030101186B" +
            "5A000F002BB401010B020101030101040102C55B" +
            "5A0006002B88010100FA2B",
    )

    @Test
    fun `all eight switches use headset replies instead of preference defaults`() {
        assertEquals(mapOf(
            FreeClip2BooleanFeature.WEAR_DETECTION to true,
            FreeClip2BooleanFeature.DROP_REMINDER to true,
            FreeClip2BooleanFeature.ADAPTIVE_VOLUME to true,
            FreeClip2BooleanFeature.LOW_LATENCY to false,
            FreeClip2BooleanFeature.DUAL_DEVICE to false,
            FreeClip2BooleanFeature.CASE_PROMPT_SOUND to true,
            FreeClip2BooleanFeature.HEAD_MOTION_CONTROL to true,
            FreeClip2BooleanFeature.NOISY_VOLUME_BOOST to false,
        ), HuaweiFreeClip2Controller.parseBooleanSettings(replies))
    }

    @Test
    fun `batched query contains only captured reads and no setters`() {
        assertArrayEquals(hex(
            "5A0005002B110100772A" + "5A0008002BB40101070200DDE9" +
                "5A0008002BB401010202003619" + "5A0005002B6C0200B820" +
                "5A0005002B2F0100A98E" + "5A0007002BB1020003007FAB" +
                "5A0006002BB401010B289B" + "5A0005002B8801009182",
        ), HuaweiFreeClip2Controller.booleanSettingsQueryPacket())
    }

    @Test
    fun `missing replies ACKs and capability fields are unknown not off`() {
        val unrelated = hex("5A0009002B6C7F04000186A0A497" + "5A0006002B6C030100DA50" + "5A0006002B88020101B35A")
        assertTrue(HuaweiFreeClip2Controller.parseBooleanSettings(unrelated).values.all { it == null })
        assertTrue(HuaweiFreeClip2Controller.parseBooleanSettings(byteArrayOf()).values.all { it == null })
        val partial = HuaweiFreeClip2Controller.parseBooleanSettings(hex("5A0009002BB10201000301016EDF"))
        assertEquals(false, partial[FreeClip2BooleanFeature.CASE_PROMPT_SOUND])
        assertNull(partial[FreeClip2BooleanFeature.WEAR_DETECTION])
        assertNull(partial[FreeClip2BooleanFeature.LOW_LATENCY])
    }

    @Test
    fun `later official app changes replace earlier true values`() {
        val updates = hex("5A0009002BB4010107020100AFA4" + "5A000C002BB4010102020100030101033E" +
            "5A0009002BB10201000301016EDF" + "5A000F002BB401010B0201000301010401027D3A")
        val values = HuaweiFreeClip2Controller.parseBooleanSettings(replies + updates)
        assertEquals(false, values[FreeClip2BooleanFeature.DROP_REMINDER])
        assertEquals(false, values[FreeClip2BooleanFeature.ADAPTIVE_VOLUME])
        assertEquals(false, values[FreeClip2BooleanFeature.CASE_PROMPT_SOUND])
        assertEquals(false, values[FreeClip2BooleanFeature.HEAD_MOTION_CONTROL])
        assertEquals(true, values[FreeClip2BooleanFeature.WEAR_DETECTION])
    }

    @Test
    fun `corrupt incomplete and invalid boolean fields are not accepted`() {
        val frame = hex("5A0009002BB4010107020101BF85")
        val corrupt = frame.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(HuaweiFreeClip2Controller.parseBooleanSettings(corrupt)[FreeClip2BooleanFeature.DROP_REMINDER])
        assertNull(HuaweiFreeClip2Controller.parseBooleanSettings(frame.copyOf(frame.size - 1))[FreeClip2BooleanFeature.DROP_REMINDER])
        val invalid = freeBuds7Frame(hex("2BB4010107020102"))
        assertNull(HuaweiFreeClip2Controller.parseBooleanSettings(invalid)[FreeClip2BooleanFeature.DROP_REMINDER])
        assertEquals(true, HuaweiFreeClip2Controller.parseBooleanSettings(corrupt + frame)[FreeClip2BooleanFeature.DROP_REMINDER])
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
