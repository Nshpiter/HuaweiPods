package moe.chenxy.huaweipods.hook

import org.junit.Assert.*
import org.junit.Test

class HeadsetRefreshPolicyTest {
    @Test
    fun `retained detail views cannot poll after pause or screen off`() {
        assertTrue(HeadsetRefreshPolicy.shouldQueryDetails(true, true, true, true, true))
        for (inactive in 0..4) {
            val state = BooleanArray(5) { it != inactive }
            assertFalse("inactive condition $inactive", HeadsetRefreshPolicy.shouldQueryDetails(
                state[0], state[1], state[2], state[3], state[4],
            ))
        }
    }

    @Test
    fun `icon repair retains connection burst but avoids continuous fast ticks`() {
        var elapsed = 0L
        var count = 0
        while (true) {
            elapsed += HeadsetRefreshPolicy.iconDelayMs(count)
            if (elapsed > 3_600_000L) break
            count++
        }
        assertEquals(123, count)
        assertEquals(2_500L, HeadsetRefreshPolicy.iconDelayMs(0))
        assertEquals(30_000L, HeadsetRefreshPolicy.iconDelayMs(4))
        assertEquals(30_000L, HeadsetRefreshPolicy.iconDelayMs(Int.MAX_VALUE))
    }
}
