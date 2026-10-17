package moe.chenxy.huaweipods.pods

import org.junit.Assert.*
import org.junit.Test

class LowLatencyWritePolicyTest {
    @Test
    fun `manual off cancels a queued auto on even after manual completion`() {
        val policy = LowLatencyWritePolicy()
        val autoOn = requireNotNull(policy.autoToken())
        assertTrue(policy.canAutoWrite(autoOn))
        val manualOff = policy.beginManual()
        assertFalse(policy.canAutoWrite(autoOn))
        assertNull(policy.autoToken())
        assertTrue(policy.finishManual(manualOff))
        assertFalse(policy.canAutoWrite(autoOn))
        assertNotNull(policy.autoToken())
    }

    @Test
    fun `superseded command cannot write or complete a newer command`() {
        val policy = LowLatencyWritePolicy()
        val first = policy.beginManual()
        val second = policy.beginManual()
        assertFalse(policy.isCurrent(first))
        assertFalse(policy.finishManual(first))
        assertNull(policy.autoToken())
        assertTrue(policy.isCurrent(second))
        assertTrue(policy.finishManual(second))
    }

    @Test
    fun `disconnect invalidates writes and releases pending state for reconnect`() {
        val policy = LowLatencyWritePolicy()
        val autoOn = requireNotNull(policy.autoToken())
        val manualOff = policy.beginManual()
        policy.resetSession()
        assertFalse(policy.canAutoWrite(autoOn))
        assertFalse(policy.isCurrent(manualOff))
        assertFalse(policy.finishManual(manualOff))
        assertTrue(policy.canAutoWrite(requireNotNull(policy.autoToken())))
    }
}
