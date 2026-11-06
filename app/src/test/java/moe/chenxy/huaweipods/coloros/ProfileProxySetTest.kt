package moe.chenxy.huaweipods.coloros

import org.junit.Assert.*
import org.junit.Test

class ProfileProxySetTest {
    private val a2dp = 2
    private val headset = 1

    /** ColorOS 16 Bluetooth process: closeProfileProxy synchronously calls onServiceDisconnected. */
    @Test fun closeAllToleratesSynchronousDisconnectReentry() {
        val closed = mutableListOf<Int>()
        lateinit var set: ProfileProxySet<Any>
        set = ProfileProxySet { profile, _ ->
            closed += profile
            set.remove(profile)
        }
        set.add(a2dp, Any())
        set.add(headset, Any())

        set.closeAll()

        assertEquals(listOf(a2dp, headset), closed)
        assertTrue(set.closed)
        assertNull(set[a2dp])
        assertNull(set[headset])
    }

    @Test fun lateProxyAfterCloseIsClosedNotRetained() {
        val closed = mutableListOf<Int>()
        val set = ProfileProxySet<Any> { profile, _ -> closed += profile }
        set.closeAll()

        assertFalse(set.add(a2dp, Any()))
        assertEquals(listOf(a2dp), closed)
        assertNull(set[a2dp])
    }

    @Test fun replacedProxyIsClosedAndDisconnectClosesOnce() {
        val closed = mutableListOf<Any>()
        val set = ProfileProxySet<Any> { _, proxy -> closed += proxy }
        val first = Any()
        val second = Any()
        set.add(a2dp, first)
        set.add(a2dp, second)
        assertEquals(listOf(first), closed)

        set.remove(a2dp)
        set.remove(a2dp)
        assertEquals(listOf(first, second), closed)
    }

    @Test fun throwingCloseDoesNotEscape() {
        val set = ProfileProxySet<Any> { _, _ -> throw IllegalStateException("binder died") }
        set.add(a2dp, Any())
        set.add(headset, Any())
        set.closeAll()
        assertTrue(set.closed)
        assertNull(set[a2dp])
        assertNull(set[headset])
    }

    @Test fun forgetDropsWithoutClosingAndCloseAllSkipsIt() {
        val closed = mutableListOf<Int>()
        val set = ProfileProxySet<Any> { profile, _ -> closed += profile }
        set.add(a2dp, Any())
        set.add(headset, Any())

        set.forget(a2dp)
        assertNull(set[a2dp])
        assertTrue(closed.isEmpty())

        set.closeAll()
        assertEquals(listOf(headset), closed)
    }
}
