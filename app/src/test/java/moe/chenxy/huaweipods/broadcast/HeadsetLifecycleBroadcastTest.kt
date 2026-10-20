package moe.chenxy.huaweipods.broadcast

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetLifecycleBroadcastTest {
    @Test fun boundEndpointRequiresKernelUidPackagesAndLifecycleAction() {
        val action = HuaweiPodsAction.ACTION_PODS_DISCONNECTED
        assertTrue(HuaweiPodsBroadcastTrustPolicy.shouldAcceptBoundHeadsetEvent(action, arrayOf("com.android.bluetooth")))
        assertFalse(HuaweiPodsBroadcastTrustPolicy.shouldAcceptBoundHeadsetEvent(action, null))
        assertFalse(HuaweiPodsBroadcastTrustPolicy.shouldAcceptBoundHeadsetEvent(action, arrayOf("fake.bluetooth")))
        assertFalse(HuaweiPodsBroadcastTrustPolicy.shouldAcceptBoundHeadsetEvent(
            HuaweiPodsAction.ACTION_REFRESH_STATUS, arrayOf("com.android.bluetooth")))
    }
    @Test fun delayedCleanupCannotReleaseNewerConnection() {
        assertFalse(shouldReleaseHeadsetBinding(5, 4))
        assertTrue(shouldReleaseHeadsetBinding(5, 5))
        assertFalse(shouldReleaseHeadsetBinding(0, 0))
    }
    @Test fun onlyLifecycleEventsCanRequestColdStart() {
        listOf(HuaweiPodsAction.ACTION_PODS_CONNECTED, HuaweiPodsAction.ACTION_PODS_DISCONNECTED,
            HuaweiPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED,
            HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED).forEach { assertTrue(isHeadsetLifecycleAction(it)) }
    }

    @Test fun controlsAndHeartbeatCannotRequestColdStart() {
        listOf(null, "", "fake", HuaweiPodsAction.ACTION_REFRESH_STATUS,
            HuaweiPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE,
            HuaweiPodsAction.ACTION_PODS_ANC_CHANGED).forEach { assertFalse(isHeadsetLifecycleAction(it)) }
    }
}
