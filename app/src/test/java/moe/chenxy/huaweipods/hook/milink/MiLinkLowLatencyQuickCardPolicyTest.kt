package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiLinkLowLatencyQuickCardPolicyTest {
    @Test
    fun `card is available only for a verified low-latency route`() {
        assertTrue(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                configured = true,
                hostAdapterName = "legacy",
            ),
        )
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREECLIP,
                configured = true,
                hostAdapterName = "legacy",
            ),
        )
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                configured = false,
                hostAdapterName = "legacy",
            ),
        )
    }

    @Test
    fun `card is hidden on HyperOS 4 and unknown host adapters`() {
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                configured = true,
                hostAdapterName = "hyperos4-v18",
            ),
        )
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                configured = true,
                hostAdapterName = null,
            ),
        )
    }

    @Test
    fun `host state keeps unsupported and disabled cards hidden`() {
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.HIDDEN_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                configured = true,
                enabled = true,
                hostAdapterName = "legacy",
            ),
        )
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.HIDDEN_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                configured = false,
                enabled = true,
                hostAdapterName = "legacy",
            ),
        )
    }

    @Test
    fun `host state mirrors the stored low-latency intent`() {
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.DISABLED_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                configured = true,
                enabled = false,
                hostAdapterName = "legacy",
            ),
        )
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.ENABLED_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                configured = true,
                enabled = true,
                hostAdapterName = "legacy",
            ),
        )
    }

    @Test
    fun `quick card click toggles the module state instead of treating host code as a boolean`() {
        assertTrue(MiLinkLowLatencyQuickCardPolicy.toggledEnabled(currentEnabled = false))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.toggledEnabled(currentEnabled = true))
    }

    @Test
    fun `detail cleanup does not turn off low latency across repeated reconnects`() {
        val cleanupStack = arrayOf(
            frame("com.miui.circulate.api.protocol.headset.HeadsetServiceController", "setFindRing"),
            frame("com.miui.circulateplus.world.headset.HeadSetsDetail", "onDetachedFromWindow"),
            frame("android.view.View", "dispatchDetachedFromWindow"),
        )
        var enabled = true
        repeat(3) {
            if (!MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(0, cleanupStack)) {
                enabled = MiLinkLowLatencyQuickCardPolicy.toggledEnabled(enabled)
            }
            assertTrue("Leaving detail must preserve the next reconnect policy", enabled)
        }
    }

    @Test
    fun `explicit stop click remains usable even though it also sends zero`() {
        val clickStack = arrayOf(
            frame("com.miui.circulate.api.protocol.headset.HeadsetServiceController", "setFindRing"),
            frame("com.miui.circulateplus.world.headset.d1", "c"),
            frame("android.view.View", "performClick"),
        )
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(0, clickStack))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.toggledEnabled(true))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(1, clickStack))
    }

    @Test
    fun `cleanup detection is limited to the exact detail lifecycle and stop command`() {
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(0, emptyArray()))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(0, arrayOf(
            frame("android.view.View", "onDetachedFromWindow"),
        )))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(0, arrayOf(
            frame("com.miui.circulateplus.world.headset.HeadSetsDetail", "onAttachedToWindow"),
        )))
        assertFalse(MiLinkLowLatencyQuickCardPolicy.isDetailCleanup(1, arrayOf(
            frame("com.miui.circulateplus.world.headset.HeadSetsDetail", "onDetachedFromWindow"),
        )))
    }

    private fun frame(className: String, method: String) = StackTraceElement(className, method, null, -1)
}
