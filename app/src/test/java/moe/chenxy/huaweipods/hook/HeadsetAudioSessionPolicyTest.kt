package moe.chenxy.huaweipods.hook

import android.bluetooth.BluetoothProfile
import org.junit.Assert.*
import org.junit.Test

class HeadsetAudioSessionPolicyTest {
    private val device = "AA:BB:CC:DD:EE:01"
    private val other = "AA:BB:CC:DD:EE:02"
    private fun decide(a2dp: Int?, hfp: Int?, current: String? = device, address: String = device,
                       profile: Int? = BluetoothProfile.A2DP, state: Int? = 0, restore: Boolean = false) =
        HeadsetAudioSessionPolicy.decide(current, address, profile, state, a2dp, hfp, restore)

    @Test fun hfpDisconnectKeepsMediaSession() { assertEquals(AudioSessionDecision.KEEP, decide(2, 0, profile = BluetoothProfile.HEADSET)) }
    @Test fun mediaDisconnectKeepsCallSessionAndPollingOwner() { assertEquals(AudioSessionDecision.KEEP, decide(0, 2)) }
    @Test fun bothProfilesDisconnectedFinishCurrentSession() { assertEquals(AudioSessionDecision.DISCONNECT, decide(0, 0)) }
    @Test fun unknownOrTransitionStatesNeverEndCurrentSession() {
        listOf(null to null, 0 to null, null to 0, 3 to 0, 1 to 0, -1 to 0).forEach { (a, h) ->
            assertEquals(AudioSessionDecision.KEEP, decide(a, h))
        }
    }
    @Test fun otherDeviceDisconnectDoesNotEndOrStealSession() {
        assertEquals(AudioSessionDecision.IGNORE, decide(0, 0, address = other))
        assertEquals(AudioSessionDecision.IGNORE, decide(0, 2, address = other))
    }
    @Test fun lateConnectedBroadcastCannotRecreateDisconnectedSession() {
        assertEquals(AudioSessionDecision.IGNORE, decide(0, 0, current = null, state = 2))
        assertEquals(AudioSessionDecision.IGNORE, decide(0, 2, current = null, state = 2))
    }
    @Test fun confirmedIncomingHfpConnectionCanStartOrSwitchSession() {
        assertEquals(AudioSessionDecision.CONNECT, decide(0, 2, current = null, profile = 1, state = 2))
        assertEquals(AudioSessionDecision.CONNECT, decide(0, 2, address = other, profile = 1, state = 2))
    }
    @Test fun startupRestoresHfpOnlyButDoesNotReplaceCurrentDevice() {
        assertEquals(AudioSessionDecision.CONNECT, decide(null, 2, current = null, profile = null, state = null, restore = true))
        assertEquals(AudioSessionDecision.CONNECT, decide(0, 2, current = null, profile = null, state = null, restore = true))
        assertEquals(AudioSessionDecision.IGNORE, decide(0, 2, address = other, profile = null, state = null, restore = true))
    }
    @Test fun addressComparisonIgnoresCase() { assertEquals(AudioSessionDecision.DISCONNECT, decide(0, 0, current = device.lowercase())) }
    @Test fun lateProfileQueryCannotApplyAfterNewEventOrBluetoothShutdown() {
        val guard = AudioSessionProbeGuard()
        val first = guard.begin(device)
        val next = guard.begin(device.lowercase())
        assertFalse(guard.accepts(first)); assertTrue(guard.accepts(next))
        val secondDevice = guard.begin(other)
        assertTrue(guard.accepts(next)); assertTrue(guard.accepts(secondDevice))
        guard.reset(); assertFalse(guard.accepts(next)); assertFalse(guard.accepts(secondDevice))
    }
}
