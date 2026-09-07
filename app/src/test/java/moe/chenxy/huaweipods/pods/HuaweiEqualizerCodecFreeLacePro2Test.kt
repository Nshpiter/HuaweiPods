package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiEqualizerCodecFreeLacePro2Test {
    @Test
    fun `builds the captured FreeLace Pro 2 built-in presets`() {
        assertPacket("5A0006002B490101012F1A", 0x01)
        assertPacket("5A0006002B490101021F79", 0x02)
        assertPacket("5A0006002B490101030F58", 0x03)
        assertPacket("5A0006002B49010109AE12", 0x09)
    }

    @Test
    fun `rejects a preset outside the captured FreeLace Pro 2 set`() {
        assertNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
                0x0A,
            ),
        )
    }

    @Test
    fun `supports state readback for FreeLace Pro 2`() {
        assertTrue(
            HuaweiEqualizerCodec.supportsStateRead(HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2),
        )
    }

    @Test
    fun `custom write operation matches the captured FreeLace Pro 2 packet`() {
        assertEquals(0x01, HuaweiEqualizerCodec.customWriteOperation(
            HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
        ))
    }

    @Test
    fun `builds the exact captured custom sound-effect packet`() {
        val packet = HuaweiEqualizerCodec.buildCustomPacket(
            gains = listOf(-60, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            presetName = "我的音效 1",
            operationValue = 0x01,
            presetId = 0x64,
        )
        assertArrayEquals(
            "5A0028002B4901016402010A050101030AC4000000000000000000040EE68891E79A84E99FB3E6958820313D52".hex(),
            packet,
        )
    }

    private fun assertPacket(expected: String, presetId: Int) {
        assertArrayEquals(
            expected.hex(),
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
                presetId,
            ),
        )
    }

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}