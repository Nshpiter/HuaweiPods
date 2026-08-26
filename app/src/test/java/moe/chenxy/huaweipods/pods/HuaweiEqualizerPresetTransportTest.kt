package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiEqualizerPresetTransportTest {
    @Test
    fun `custom presets survive compact transport round trip`() {
        val presets = listOf(
            HuaweiEqualizerPreset(0x66, "通透人声", List(10) { it - 5 }),
            HuaweiEqualizerPreset(0x64, "低频", List(10) { 12 }),
        )

        val payload = HuaweiEqualizerPresetTransport.encode(presets)

        assertEquals(listOf(0x64, 0x66), payload.ids)
        assertEquals(presets.sortedBy(HuaweiEqualizerPreset::id), HuaweiEqualizerPresetTransport.decode(
            payload.ids,
            payload.names,
            payload.gains,
        ))
    }

    @Test
    fun `invalid or incomplete transport fails closed`() {
        assertNull(
            HuaweiEqualizerPresetTransport.decode(
                ids = listOf(0x64),
                names = listOf("我的音效"),
                gains = List(9) { 0 },
            ),
        )
        assertNull(
            HuaweiEqualizerPresetTransport.decode(
                ids = listOf(0x64, 0x64),
                names = listOf("一", "二"),
                gains = List(20) { 0 },
            ),
        )
        assertNull(
            HuaweiEqualizerPresetTransport.decode(
                ids = listOf(0x63),
                names = listOf("越界"),
                gains = List(10) { 0 },
            ),
        )
    }

    @Test
    fun `encoder omits malformed presets without corrupting valid entries`() {
        val payload = HuaweiEqualizerPresetTransport.encode(
            listOf(
                HuaweiEqualizerPreset(0x64, "  已保存  ", List(10) { 0 }),
                HuaweiEqualizerPreset(0x65, "", List(10) { 0 }),
                HuaweiEqualizerPreset(0x66, "坏参数", List(9) { 0 }),
            ),
        )

        assertEquals(listOf(0x64), payload.ids)
        assertEquals(listOf("已保存"), payload.names)
        assertEquals(List(10) { 0 }, payload.gains)
    }
}
