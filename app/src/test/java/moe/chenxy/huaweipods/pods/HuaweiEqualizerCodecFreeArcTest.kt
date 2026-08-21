package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiEqualizerCodecFreeArcTest {
    @Test
    fun `parses captured FreeArc equalizer state`() {
        val state = HuaweiEqualizerCodec.parseState(hex(FREEARC_EQUALIZER_STATE))

        requireNotNull(state)
        assertTrue(state.supported)
        assertEquals(0x64, state.selectedId)
        assertEquals(listOf(0x01, 0x0A, 0x02, 0x03, 0x09), state.builtInIds)
        assertEquals(10, state.bandCount)
        assertEquals("我的音效 1", state.selectedName)
        assertEquals(List(10) { 0 }, state.selectedGains)
        assertEquals(listOf(0x64), state.customPresets.map { it.id })
    }

    @Test
    fun `builds captured FreeArc preset and custom packets`() {
        val expectedPresetPackets = mapOf(
            0x01 to "5A0006002B490101012F1A",
            0x0A to "5A0006002B4901010A9E71",
            0x02 to "5A0006002B490101021F79",
            0x03 to "5A0006002B490101030F58",
            0x09 to "5A0006002B49010109AE12",
        )
        expectedPresetPackets.forEach { (presetId, packet) ->
            assertArrayEquals(
                packet,
                hex(packet),
                HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                    HuaweiDeviceRoute.HUAWEI_FREEARC,
                    presetId,
                ),
            )
        }
        assertArrayEquals(
            hex("5A0028002B4901016402010A050101030A00000000000000000000040EE68891E79A84E99FB3E6958820312B6E"),
            HuaweiEqualizerCodec.buildCustomPacket(
                gains = List(10) { 0 },
                presetName = "我的音效 1",
                operationValue = requireNotNull(
                    HuaweiEqualizerCodec.customWriteOperation(HuaweiDeviceRoute.HUAWEI_FREEARC),
                ),
            ),
        )
        assertNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEARC,
                0x04,
            ),
        )
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val FREEARC_EQUALIZER_STATE =
            "5A0062002B4A0101010201640305010A02030904010105010A060A00000000000000000000" +
                "0718E68891E79A84E99FB3E695882031000000000000000000000824640A000000000000" +
                "00000000E68891E79A84E99FB3E695882031000000000000000000001885"
    }
}
