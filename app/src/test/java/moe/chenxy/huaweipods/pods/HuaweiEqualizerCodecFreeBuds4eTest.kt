package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiEqualizerCodecFreeBuds4eTest {
    @Test
    fun `builds only the three captured FreeBuds 4E presets`() {
        assertPacket("5A0006002B490101012F1A", 1)
        assertPacket("5A0006002B490101021F79", 2)
        assertPacket("5A0006002B490101030F58", 3)
        assertNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
                9,
            ),
        )
    }

    @Test
    fun `does not reuse FreeBuds 4E preset writes for an unverified route`() {
        assertNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                1,
            ),
        )
    }

    private fun assertPacket(expected: String, presetId: Int) {
        assertArrayEquals(
            expected.hex(),
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
                presetId,
            ),
        )
    }

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
