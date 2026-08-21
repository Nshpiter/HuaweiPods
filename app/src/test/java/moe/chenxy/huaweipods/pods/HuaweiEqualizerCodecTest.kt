package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiEqualizerCodecTest {
    @Test
    fun `parses verified FreeBuds 6i custom equalizer state`() {
        val state = HuaweiEqualizerCodec.parseState(hex(FREEBUDS6I_EQ_STATE))

        requireNotNull(state)
        assertTrue(state.supported)
        assertEquals(0x64, state.selectedId)
        assertTrue(state.isCustom)
        assertEquals(listOf(0x01, 0x02, 0x03, 0x09), state.builtInIds)
        assertEquals(10, state.bandCount)
        assertEquals("全频校准", state.selectedName)
        assertEquals(listOf(40, 20, 10, 0, 10, 35, 25, 0, 10, 20), state.selectedGains)
        assertEquals(listOf(0x64, 0x65, 0x66), state.customPresets.map { it.id })
        assertEquals("流行风向", state.customPresets[1].name)
        assertEquals("均衡人声", state.customPresets[2].name)
    }

    @Test
    fun `builds route-specific verified custom packets`() {
        val gains = listOf(40, 20, 10, 0, 10, 35, 25, 0, 10, 20)

        assertArrayEquals(
            hex("5A0026002B4901016402010A050101030A28140A000A2319000A14040CE585A8E9A291E6A0A1E58786A9A9"),
            HuaweiEqualizerCodec.buildCustomPacket(gains, "全频校准", operationValue = 1),
        )
        assertArrayEquals(
            hex("5A0028002B4901016402010A050100030A00000000000000000000040EE68891E79A84E99FB3E695882031DB88"),
            HuaweiEqualizerCodec.buildCustomPacket(List(10) { 0 }, "我的音效 1", operationValue = 0),
        )
    }

    @Test
    fun `builds official FreeBuds 6i sound effect packets reported by state`() {
        val expected = mapOf(
            0x01 to "5A0006002B490101012F1A",
            0x02 to "5A0006002B490101021F79",
            0x03 to "5A0006002B490101030F58",
            0x09 to "5A0006002B49010109AE12",
        )

        expected.forEach { (presetId, packet) ->
            assertArrayEquals(
                hex(packet),
                HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                    presetId,
                ),
            )
        }
        assertNull(
            HuaweiEqualizerCodec.buildBuiltInPresetPacket(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                0x0A,
            ),
        )
    }

    @Test
    fun `rejects malformed or unverified values`() {
        val badCrc = hex(FREEBUDS6I_EQ_STATE).also { it[it.lastIndex] = 0 }
        assertNull(HuaweiEqualizerCodec.parseState(badCrc))
        assertNull(HuaweiEqualizerCodec.buildCustomPacket(List(9) { 0 }, "EQ", 1))
        assertNull(HuaweiEqualizerCodec.buildCustomPacket(List(10) { 61 }, "EQ", 1))
        assertFalse(HuaweiEqualizerCodec.supportsStateRead(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
        assertNull(HuaweiEqualizerCodec.customWriteOperation(HuaweiDeviceRoute.HUAWEI_FREECLIP2))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val FREEBUDS6I_EQ_STATE =
            "5A00A9002B4A01010102016403040102030904010105010A060A28140A000A2319000A14" +
                "0718E585A8E9A291E6A0A1E58786000000000000000000000000086C640A28140A000A" +
                "2319000A14E585A8E9A291E6A0A1E58786000000000000000000000000650A2314EC0A" +
                "0AECEC00323CE6B581E8A18CE9A38EE59091000000000000000000000000660A0A0A0A" +
                "0A00F1F1001414E59D87E8A1A1E4BABAE5A3B0000000000000000000000000CA62"
    }
}
