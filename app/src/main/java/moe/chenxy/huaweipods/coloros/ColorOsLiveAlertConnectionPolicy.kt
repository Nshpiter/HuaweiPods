package moe.chenxy.huaweipods.coloros

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile

/** A Profile disconnect is only a hint; another audio Profile may still be connected. */
internal object ColorOsLiveAlertConnectionPolicy {
    fun matchesDevice(currentAddress: String?, eventAddress: String?): Boolean =
        !currentAddress.isNullOrBlank() && !eventAddress.isNullOrBlank() &&
            currentAddress.equals(eventAddress, ignoreCase = true)

    fun shouldCheckProfileDisconnect(
        currentAddress: String?,
        eventAddress: String?,
        eventState: Int,
    ): Boolean = matchesDevice(currentAddress, eventAddress) &&
        eventState == BluetoothProfile.STATE_DISCONNECTED

    fun shouldCancelAfterProfileCheck(
        currentAddress: String?,
        checkedAddress: String?,
        a2dpState: Int?,
        headsetState: Int?,
    ): Boolean = matchesDevice(currentAddress, checkedAddress) &&
        a2dpState == BluetoothProfile.STATE_DISCONNECTED &&
        headsetState == BluetoothProfile.STATE_DISCONNECTED

    fun isBluetoothTurningOffOrOff(state: Int): Boolean =
        state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF
}
