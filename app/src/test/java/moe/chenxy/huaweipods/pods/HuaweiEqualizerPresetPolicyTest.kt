package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiEqualizerPresetPolicyTest {
    @Test
    fun `allocates only the three official custom preset slots`() {
        val first = HuaweiEqualizerPreset(0x64, "一", List(10) { 0 })
        val third = HuaweiEqualizerPreset(0x66, "三", List(10) { 0 })

        assertEquals(0x65, HuaweiEqualizerPresetPolicy.nextAvailableId(listOf(first, third)))
        assertNull(
            HuaweiEqualizerPresetPolicy.nextAvailableId(
                listOf(first, HuaweiEqualizerPreset(0x65, "二", List(10) { 0 }), third),
            ),
        )
    }

    @Test
    fun `normalizes names by the official UTF-8 byte limit`() {
        assertEquals("我的音效", HuaweiEqualizerPresetPolicy.normalizeName("  我的音效  "))
        assertNull(HuaweiEqualizerPresetPolicy.normalizeName("   "))
        assertEquals("a".repeat(32), HuaweiEqualizerPresetPolicy.normalizeName("a".repeat(32)))
        assertNull(HuaweiEqualizerPresetPolicy.normalizeName("a".repeat(33)))
        assertNull(HuaweiEqualizerPresetPolicy.normalizeName("音".repeat(11)))
    }

    @Test
    fun `upsert replaces only the matching slot and keeps official order`() {
        val old = HuaweiEqualizerPreset(0x64, "旧", List(10) { 0 })
        val other = HuaweiEqualizerPreset(0x66, "其他", List(10) { 20 })
        val replacement = HuaweiEqualizerPreset(0x64, "新", List(10) { 10 })

        assertEquals(
            listOf(replacement, other),
            HuaweiEqualizerPresetPolicy.upsert(listOf(old, other), replacement),
        )
    }
}
