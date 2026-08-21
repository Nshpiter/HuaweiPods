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
            ),
        )
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREECLIP,
                configured = true,
            ),
        )
        assertFalse(
            MiLinkLowLatencyQuickCardPolicy.isAvailable(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                configured = false,
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
            ),
        )
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.HIDDEN_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                configured = false,
                enabled = true,
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
            ),
        )
        assertEquals(
            MiLinkLowLatencyQuickCardPolicy.ENABLED_STATE,
            MiLinkLowLatencyQuickCardPolicy.hostState(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                configured = true,
                enabled = true,
            ),
        )
    }
}
