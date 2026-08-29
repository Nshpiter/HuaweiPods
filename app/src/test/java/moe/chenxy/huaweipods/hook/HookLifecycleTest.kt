package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookLifecycleTest {
    @Test
    fun `stable hook ids are reproducible across generations`() {
        val method = Target::class.java.getDeclaredMethod("sample", Int::class.javaPrimitiveType)
        val firstGeneration = StableHookIdGenerator("settings-headset")
        val secondGeneration = StableHookIdGenerator("settings-headset")

        assertEquals(
            firstGeneration.next("before", method),
            secondGeneration.next("before", method),
        )
        assertEquals(
            firstGeneration.next("before", method),
            secondGeneration.next("before", method),
        )
    }

    @Test
    fun `stable hook ids distinguish phase overload and occurrence`() {
        val intMethod = Target::class.java.getDeclaredMethod("sample", Int::class.javaPrimitiveType)
        val stringMethod = Target::class.java.getDeclaredMethod("sample", String::class.java)
        val generator = StableHookIdGenerator("settings-headset")

        val first = generator.next("before", intMethod)
        assertNotEquals(first, generator.next("after", intMethod))
        assertNotEquals(first, generator.next("before", stringMethod))
        assertNotEquals(first, generator.next("before", intMethod))
    }

    @Test
    fun `close is idempotent and cleanup actions run once in reverse order`() {
        val events = mutableListOf<String>()
        val hook = TestHookContext(events)
        hook.addCleanup("first")
        hook.addCleanup("second")

        assertTrue(hook.close().isEmpty())
        assertTrue(hook.close().isEmpty())
        hook.addCleanup("late")

        assertTrue(hook.isClosedForTest())
        assertEquals(listOf("onClose", "second", "first", "late"), events)
    }

    @Test
    fun `close preflight is side effect free and can veto reload`() {
        val hook = VetoHookContext()

        assertEquals(false, hook.canClose())
        assertEquals(1, hook.preflightCount)
        assertEquals(0, hook.closeCount)
        assertEquals(false, hook.isClosedForTest())
    }

    @Test
    fun `close reports failures and still runs every cleanup action`() {
        val events = mutableListOf<String>()
        val hook = FailingHookContext(events)
        hook.addCleanup("first", shouldFail = false)
        hook.addCleanup("second", shouldFail = true)

        val failures = hook.close()

        assertEquals(2, failures.size)
        assertEquals(listOf("onClose", "second", "first"), events)
        assertTrue(hook.isClosedForTest())
    }

    private class TestHookContext(
        private val events: MutableList<String>,
    ) : HookContext() {
        override fun onHook() = Unit

        override fun onClose() {
            events += "onClose"
        }

        fun addCleanup(value: String) {
            registerCloseAction { events += value }
        }
    }

    private class VetoHookContext : HookContext() {
        var preflightCount = 0
        var closeCount = 0

        override fun onHook() = Unit

        override fun onCanClose(): Boolean {
            preflightCount += 1
            return false
        }

        override fun onClose() {
            closeCount += 1
        }
    }

    private class FailingHookContext(
        private val events: MutableList<String>,
    ) : HookContext() {
        override fun onHook() = Unit

        override fun onClose() {
            events += "onClose"
            error("close failed")
        }

        fun addCleanup(value: String, shouldFail: Boolean) {
            registerCloseAction {
                events += value
                if (shouldFail) error("cleanup failed")
            }
        }
    }

    @Suppress("unused")
    private class Target {
        fun sample(value: Int): String = value.toString()

        fun sample(value: String): String = value
    }
}
