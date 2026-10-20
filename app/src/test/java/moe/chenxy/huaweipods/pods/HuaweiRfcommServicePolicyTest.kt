package moe.chenxy.huaweipods.pods

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class HuaweiRfcommServicePolicyTest {
    private fun standard(id: Int): UUID = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(id))

    @Test fun audioAndRemoteControlServicesAreNeverHuaweiControlCandidates() {
        listOf(0x1108, 0x110A, 0x110B, 0x110C, 0x110D, 0x110E, 0x110F, 0x1112, 0x111E, 0x111F)
            .forEach { assertFalse(it.toString(16), HuaweiRfcommServicePolicy.isControlCandidate(standard(it))) }
    }

    @Test fun serialPortServiceRemainsAvailable() {
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(standard(0x1101)))
    }

    @Test fun unknownStandardServicesAreNotBlockedByGuessing() {
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(standard(0xFE01)))
    }

    @Test fun vendorServicesWithSimilarAudioPrefixRemainAvailable() {
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(UUID.fromString("0000111e-1234-1000-8000-00805f9b34fb")))
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(UUID.fromString("f000111e-0000-1000-8000-00805f9b34fb")))
    }

    @Test fun filteringPreservesOriginalControlCandidateOrder() {
        val spp = standard(0x1101)
        val vendor = UUID.fromString("0000fe01-1234-1000-8000-00805f9b34fb")
        assertEquals(listOf(spp, vendor), listOf(standard(0x111E), spp, standard(0x110B), vendor)
            .filter(HuaweiRfcommServicePolicy::isControlCandidate))
    }
}
