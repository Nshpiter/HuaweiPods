package moe.chenxy.huaweipods.coloros

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsLiveAlertConnectionPolicyTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private val otherAddress = "AA:BB:CC:DD:EE:02"

    @Test
    fun `device matching ignores address case`() {
        assertTrue(ColorOsLiveAlertConnectionPolicy.matchesDevice(address, address))
        assertTrue(ColorOsLiveAlertConnectionPolicy.matchesDevice(address, address.lowercase()))
        assertTrue(ColorOsLiveAlertConnectionPolicy.matchesDevice(address.lowercase(), address))
    }

    @Test
    fun `different or missing addresses never identify the current device`() {
        assertFalse(ColorOsLiveAlertConnectionPolicy.matchesDevice(address, otherAddress))
        val missingAddresses = listOf(null, "", " ")
        missingAddresses.forEach { missing ->
            assertFalse(ColorOsLiveAlertConnectionPolicy.matchesDevice(address, missing))
            assertFalse(ColorOsLiveAlertConnectionPolicy.matchesDevice(missing, address))
            missingAddresses.forEach { otherMissing ->
                assertFalse(ColorOsLiveAlertConnectionPolicy.matchesDevice(missing, otherMissing))
            }
        }
    }

    @Test
    fun `only a disconnected event for the current device starts a profile check`() {
        val states = listOf(
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_DISCONNECTING,
            BluetoothAdapter.ERROR,
        )
        states.forEach { state ->
            assertEquals(
                "event state=$state",
                state == BluetoothProfile.STATE_DISCONNECTED,
                ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(address, address, state),
            )
        }
    }

    @Test
    fun `a lowercase address still checks the current device disconnect`() {
        assertTrue(
            ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(
                address,
                address.lowercase(),
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `unrelated and missing device events do not start a profile check`() {
        listOf(null, "", " ", otherAddress).forEach { eventAddress ->
            assertFalse(
                ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(
                    address,
                    eventAddress,
                    BluetoothProfile.STATE_DISCONNECTED,
                ),
            )
        }
        listOf(null, "", " ").forEach { currentAddress ->
            assertFalse(
                ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(
                    currentAddress,
                    address,
                    BluetoothProfile.STATE_DISCONNECTED,
                ),
            )
        }
    }

    @Test
    fun `missing broadcast state using ERROR does not mean disconnected`() {
        assertFalse(
            ColorOsLiveAlertConnectionPolicy.shouldCheckProfileDisconnect(
                address,
                address,
                BluetoothAdapter.ERROR,
            ),
        )
    }

    @Test
    fun `HFP disconnect keeps live alert while A2DP remains connected`() {
        assertFalse(
            ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                address,
                address,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `A2DP disconnect keeps live alert while HFP remains connected`() {
        assertFalse(
            ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                address,
                address,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `only two confirmed disconnected profiles cancel the current live alert`() {
        val states = listOf(
            null,
            BluetoothAdapter.ERROR,
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_DISCONNECTING,
        )
        states.forEach { a2dpState ->
            states.forEach { headsetState ->
                assertEquals(
                    "A2DP=$a2dpState HFP=$headsetState",
                    a2dpState == BluetoothProfile.STATE_DISCONNECTED &&
                        headsetState == BluetoothProfile.STATE_DISCONNECTED,
                    ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                        address,
                        address,
                        a2dpState,
                        headsetState,
                    ),
                )
            }
        }
    }

    @Test
    fun `matching lowercase address allows confirmed full disconnection`() {
        assertTrue(
            ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                address,
                address.lowercase(),
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `switching current device during a profile check cannot cancel the new device`() {
        assertFalse(
            ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                otherAddress,
                address,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `missing addresses cannot cancel an alert even when both profiles are disconnected`() {
        val missingAddresses = listOf(null, "", " ")
        missingAddresses.forEach { missing ->
            assertFalse(
                ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                    address,
                    missing,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTED,
                ),
            )
            assertFalse(
                ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                    missing,
                    address,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTED,
                ),
            )
            assertFalse(
                ColorOsLiveAlertConnectionPolicy.shouldCancelAfterProfileCheck(
                    missing,
                    missing,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTED,
                ),
            )
        }
    }

    @Test
    fun `bluetooth turning off or off explicitly clears the connection`() {
        assertTrue(
            ColorOsLiveAlertConnectionPolicy.isBluetoothTurningOffOrOff(BluetoothAdapter.STATE_TURNING_OFF),
        )
        assertTrue(
            ColorOsLiveAlertConnectionPolicy.isBluetoothTurningOffOrOff(BluetoothAdapter.STATE_OFF),
        )
    }

    @Test
    fun `bluetooth on turning on or missing state does not clear the connection`() {
        listOf(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.ERROR)
            .forEach { state ->
                assertFalse(
                    "adapter state=$state",
                    ColorOsLiveAlertConnectionPolicy.isBluetoothTurningOffOrOff(state),
                )
            }
    }
}
