package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorOsMyDevicesHookTest {
    @Test
    fun `formats connected FreeClip batteries for Chinese system row`() {
        assertEquals(
            "左 81% · 右 76% · 盒 52%",
            colorOsBatterySummary(81, true, 76, true, 52, true, chinese = true),
        )
    }

    @Test
    fun `omits unavailable components and clamps malformed battery values`() {
        assertEquals(
            "R 100%",
            colorOsBatterySummary(-1, false, 255, true, 0, false, chinese = false),
        )
        assertNull(colorOsBatterySummary(0, false, 0, false, 0, false, chinese = true))
    }
}
