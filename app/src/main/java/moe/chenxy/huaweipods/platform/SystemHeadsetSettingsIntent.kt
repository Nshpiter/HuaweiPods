package moe.chenxy.huaweipods.platform

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

object SystemHeadsetSettingsIntent {
    fun open(context: Context, device: BluetoothDevice): Boolean {
        val candidates = listOf(aospDetailIntent(device), bluetoothSettingsIntent())

        return candidates.any { intent ->
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
    }

    /**
     * AOSP and ColorOS Settings both retain the standard Bluetooth detail entry.
     * Passing the address in both locations covers Settings implementations that
     * read either the activity extra or the fragment argument bundle.
     */
    private fun aospDetailIntent(device: BluetoothDevice) = Intent().apply {
        setClassName(
            RomIntegrationPolicy.SETTINGS_PACKAGE,
            "com.android.settings.Settings\$BluetoothDeviceDetailActivity",
        )
        putExtra("device_address", device.address)
        putExtra(
            ":settings:show_fragment_args",
            Bundle().apply { putString("device_address", device.address) },
        )
        putExtra(BluetoothDevice.EXTRA_DEVICE, device)
    }

    private fun bluetoothSettingsIntent() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
}
