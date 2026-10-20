package moe.chenxy.huaweipods.broadcast

import org.junit.Assert.*
import org.junit.Test

class HeadsetDeliveryQueueTest {
    @Test fun recoverySnapshotNeverEvictsUnacknowledgedHeadForNewerCachedStates() {
        val queue = HeadsetDeliveryQueue<String>(2)
        queue.enqueue("critical", HeadsetEventKind.DISCONNECT, 1, "final disconnect")
        val first = queue.protectHead()!!
        repeat(50) { queue.enqueue("other-$it", HeadsetEventKind.BATTERY, (it + 2).toLong(), "battery") }
        val saved = queue.recoverySnapshot()
        assertEquals(2, saved.size)
        assertEquals(first, saved.first())
        assertEquals("final disconnect", saved.first().payload)
    }
    @Test fun appDeathReplaysAppliedConnectionWithoutWaitingForAnotherBatteryPacket() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue("device", HeadsetEventKind.CONNECT, 10, "connected")
        queue.enqueue("device", HeadsetEventKind.BATTERY, 20, "battery")
        val first = queue.protectHead()!!; queue.acknowledge(first.id)
        val second = queue.protectHead()!!; queue.acknowledge(second.id)
        assertEquals(0, queue.size)
        assertTrue(queue.replayLatestState())
        assertEquals(2, queue.size)
        assertTrue(queue.peek()!!.id > second.id)
        assertEquals("connected", queue.peek()!!.payload)
        assertFalse(queue.acknowledge(first.id))
        assertFalse(queue.replayLatestState())
    }
    @Test fun disconnectedCurrentProfilesOverrideSavedConnectionDuringReload() {
        assertTrue(headsetRecoveryShouldClear(0, 0, false))
        assertFalse(headsetRecoveryShouldClear(2, 0, false))
        assertFalse(headsetRecoveryShouldClear(0, 2, false))
    }
    @Test fun unknownProfilesNeverTurnSavedConnectionIntoFalseDisconnect() {
        assertFalse(headsetRecoveryShouldClear(null, 0, false))
        assertFalse(headsetRecoveryShouldClear(0, null, false))
        assertFalse(headsetRecoveryShouldClear(null, null, false))
    }
    @Test fun bluetoothOffClearsReloadedNotificationWithoutProfileProxies() {
        assertTrue(headsetRecoveryShouldClear(null, null, true))
    }
    @Test fun hotReloadTransfersUnacknowledgedDisconnectAndDropsOldReceiptIdentity() {
        val old = HeadsetDeliveryQueue<String>()
        old.enqueue("current", HeadsetEventKind.BATTERY, 10, "battery")
        old.protectHead()
        old.enqueue("current", HeadsetEventKind.DISCONNECT, 20, "disconnect")
        val saved = old.snapshot()
        old.clear()
        assertEquals(2, saved.size)
        val replacement = HeadsetDeliveryQueue<String>()
        saved.forEach { replacement.enqueue(it.address, it.kind, it.elapsed, it.payload) }
        assertEquals(HeadsetEventKind.DISCONNECT, replacement.peek()!!.kind)
        assertEquals("disconnect", replacement.peek()!!.payload)
        assertFalse(HeadsetDeliveryToken("new-generation", 1, 1).matches(HeadsetDeliveryToken("old-generation", 1, 1)))
    }
    @Test fun snapshotDoesNotKeepMutableQueueAndCanResumeAfterClose() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue("device", HeadsetEventKind.CONNECT, 1, "connected")
        val snapshot = queue.snapshot()
        queue.enqueue("device", HeadsetEventKind.DISCONNECT, 2, "disconnected")
        assertEquals("connected", snapshot.single().payload)
        assertEquals("disconnected", queue.peek()!!.payload)
    }
    private val address = "AA:BB:CC:DD:EE:01"
    @Test fun failedIpcRetainsProtectedDisconnectUntilAcknowledged() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.DISCONNECT, 100, "closed")
        val sent = queue.protectHead()!!
        val retry = HeadsetDeliveryRetry()
        repeat(5) { assertNotNull(retry.nextDelay()); assertEquals(sent, queue.peek()) }
        assertNull(retry.nextDelay()); assertEquals(1, queue.size)
        assertFalse(queue.acknowledge(sent.id + 1)); assertTrue(queue.acknowledge(sent.id))
        assertEquals(0, queue.size)
    }
    @Test fun newestBatteryIsCoalescedButInFlightHeadIsNotReplaced() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.BATTERY, 10, "old")
        val head = queue.protectHead()!!
        for (i in 11..100) queue.enqueue(address, HeadsetEventKind.BATTERY, i.toLong(), "battery-$i")
        assertEquals(2, queue.size); assertEquals(head, queue.peek())
        queue.acknowledge(head.id); assertEquals("battery-100", queue.peek()!!.payload)
    }
    @Test fun disconnectReplacesUnsentConnectionAndBattery() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.CONNECT, 10, "connect")
        queue.enqueue(address, HeadsetEventKind.BATTERY, 11, "battery")
        queue.enqueue(address, HeadsetEventKind.DISCONNECT, 12, "disconnect")
        assertEquals(1, queue.size); assertEquals(HeadsetEventKind.DISCONNECT, queue.peek()!!.kind)
    }
    @Test fun lateBatteryAndOutOfOrderConnectionCannotReviveClosedDevice() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.DISCONNECT, 100, "disconnect")
        assertFalse(queue.enqueue(address, HeadsetEventKind.CONNECT, 90, "old connect"))
        assertFalse(queue.enqueue(address, HeadsetEventKind.BATTERY, 110, "late battery"))
        assertTrue(queue.enqueue(address, HeadsetEventKind.CONNECT, 120, "new connect"))
        assertTrue(queue.enqueue(address, HeadsetEventKind.BATTERY, 121, "new battery"))
    }
    @Test fun reconnectQueuedBehindDisconnectSurvivesOldCleanupReceipt() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.DISCONNECT, 10, "disconnect")
        val old = queue.protectHead()!!
        queue.enqueue(address, HeadsetEventKind.CONNECT, 20, "reconnect")
        assertTrue(queue.acknowledge(old.id)); assertEquals(1, queue.size)
        assertEquals("reconnect", queue.peek()!!.payload)
        assertFalse(queue.acknowledge(old.id))
    }
    @Test fun boundedQueuePreservesInFlightAndFinalDisconnectDuringBatteryStorm() {
        val queue = HeadsetDeliveryQueue<String>(4)
        queue.enqueue(address, HeadsetEventKind.BATTERY, 1, "inflight")
        val first = queue.protectHead()!!
        queue.enqueue("other", HeadsetEventKind.DISCONNECT, 2, "terminal")
        repeat(100) { queue.enqueue("device-$it", HeadsetEventKind.BATTERY, (it + 3).toLong(), "battery") }
        assertEquals(4, queue.size); assertEquals(first, queue.peek())
        queue.acknowledge(first.id); assertEquals("terminal", queue.peek()!!.payload)
    }
    @Test fun otherDeviceDisconnectDoesNotDropCurrentBattery() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.BATTERY, 1, "current")
        queue.enqueue("other", HeadsetEventKind.DISCONNECT, 2, "other")
        assertEquals(2, queue.size); assertEquals("current", queue.peek()!!.payload)
    }
    @Test fun retriesBackOffAndSuccessfulDeliveryResetsBudget() {
        val retry = HeadsetDeliveryRetry()
        assertEquals(listOf(250L, 1000L, 2000L, 4000L, 8000L), (1..5).map { retry.nextDelay() })
        repeat(20) { assertNull(retry.nextDelay()) }
        retry.reset(); assertEquals(250L, retry.nextDelay())
    }
    @Test fun oldChannelAndPreviousModuleGenerationCannotAcknowledgeNewConnection() {
        val current = HeadsetDeliveryToken("new-module", 4, 8)
        assertTrue(current.matches(current))
        assertFalse(current.matches(current.copy(client = "old-module")))
        assertFalse(current.matches(current.copy(transport = 3)))
        assertFalse(current.matches(current.copy(event = 7)))
        assertFalse(HeadsetDeliveryToken("", 0, 0).matches(HeadsetDeliveryToken("", 0, 0)))
    }
    @Test fun acceptedIpcIsNotAppliedAndAckLossIsIdempotent() {
        val ledger = HeadsetAppliedLedger()
        assertFalse(ledger.wasApplied("client", 1))
        ledger.record("client", 1)
        assertTrue(ledger.wasApplied("client", 1)); assertFalse(ledger.wasApplied("client", 2))
        ledger.record("client", 2); ledger.record("client", 1)
        assertTrue(ledger.wasApplied("client", 2))
    }
    @Test fun appRestartCanReplayRetainedEventAndNewModuleHasIndependentIds() {
        val before = HeadsetAppliedLedger()
        before.record("old-client", 5)
        assertFalse(before.wasApplied("new-client", 1))
        val restarted = HeadsetAppliedLedger()
        assertFalse(restarted.wasApplied("old-client", 5))
        restarted.record("old-client", 5); assertTrue(restarted.wasApplied("old-client", 5))
    }
    @Test fun queueCloseRemovesOldGenerationState() {
        val queue = HeadsetDeliveryQueue<String>()
        queue.enqueue(address, HeadsetEventKind.DISCONNECT, 500, "old")
        queue.protectHead(); queue.clear()
        assertEquals(0, queue.size)
        assertTrue(queue.enqueue(address, HeadsetEventKind.CONNECT, 1, "after reboot"))
    }
}
