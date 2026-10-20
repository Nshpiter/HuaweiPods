package moe.chenxy.huaweipods.broadcast

import android.bluetooth.BluetoothProfile
import android.content.Intent

internal fun headsetRecoveryShouldClear(a2dp: Int?, hfp: Int?, adapterOff: Boolean): Boolean =
    adapterOff || (a2dp == BluetoothProfile.STATE_DISCONNECTED && hfp == BluetoothProfile.STATE_DISCONNECTED)

internal fun snapshotHeadsetLifecycleIntent(event: Intent): Intent = Intent(event).apply {
    removeExtra("status")
    setExtrasClassLoader(Intent::class.java.classLoader)
}
