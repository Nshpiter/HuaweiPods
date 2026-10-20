package moe.chenxy.huaweipods.broadcast

import android.content.Intent
import android.os.Parcel
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import org.junit.Assert.*
import org.junit.Test

class HeadsetHotReloadParcelTest {
    @Test fun savedIntentKeepsPrimitiveBatteryButNotModuleParcelable() {
        val original = Intent(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED).apply {
            putExtra("address", "AA:BB:CC:DD:EE:01")
            putExtra("left_battery", 80)
            putExtra("left_connected", true)
            putExtra("device_name", "HUAWEI FreeClip")
            putExtra(HuaweiPodsAction.EXTRA_DEVICE_DISPLAY_NAME, "我的 🎧")
            putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, "HUAWEI_FREECLIP")
            putExtra("state_cached", true)
            putExtra("status", BatteryParams(left = PodParams(80, false, true), right = null, case = null))
        }
        val saved = snapshotHeadsetLifecycleIntent(original)
        assertFalse(saved.hasExtra("status"))
        assertTrue(original.hasExtra("status"))
        assertEquals(80, saved.getIntExtra("left_battery", -1))
        assertTrue(saved.getBooleanExtra("left_connected", false))
        val parcel = Parcel.obtain()
        try {
            parcel.writeTypedObject(saved, 0)
            parcel.setDataPosition(0)
            val restored = parcel.readTypedObject(Intent.CREATOR)!!
            assertEquals(saved.action, restored.action)
            assertEquals("AA:BB:CC:DD:EE:01", restored.getStringExtra("address"))
            assertEquals(80, restored.getIntExtra("left_battery", -1))
            assertEquals("HUAWEI FreeClip", restored.getStringExtra("device_name"))
            assertEquals("我的 🎧", restored.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_DISPLAY_NAME))
            assertEquals("HUAWEI_FREECLIP", restored.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE))
            assertTrue(restored.getBooleanExtra("state_cached", false))
            assertFalse(restored.hasExtra("status"))
        } finally { parcel.recycle() }
    }
}
