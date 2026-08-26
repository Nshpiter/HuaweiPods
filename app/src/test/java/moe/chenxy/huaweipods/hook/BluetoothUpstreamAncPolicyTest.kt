package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothUpstreamAncPolicyTest {
    private val off = HuaweiAncState(NoiseControlMode.OFF)

    @Test
    fun `FreeClip one falls back to generic control center bluetooth UI`() {
        assertFalse(shouldExposeMiuiAdvancedHeadsetUi(HuaweiDeviceRoute.HUAWEI_FREECLIP))
        assertFalse(shouldExposeMiuiAdvancedHeadsetUi(HuaweiDeviceRoute.UNSUPPORTED))

        HuaweiDeviceRoute.entries
            .filterNot { it == HuaweiDeviceRoute.HUAWEI_FREECLIP || it == HuaweiDeviceRoute.UNSUPPORTED }
            .forEach { route -> assertTrue(route.name, shouldExposeMiuiAdvancedHeadsetUi(route)) }
    }

    @Test
    fun `open-ear and eyewear routes reject every ANC command`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertNull(upstreamHuaweiAncStateForMode(route, 1, off))
            assertNull(upstreamHuaweiAncStateForMode(route, 2, off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "0100", off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
            assertEquals("0000", upstreamMiuiAncLevel(route, HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION)))
        }
    }

    @Test
    fun `three-state routes preserve transparency and captured defaults`() {
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS5I, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF),
            upstreamHuaweiAncStateForMode(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, 2, off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x01),
            upstreamHuaweiAncStateForLevel(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "0201", off),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            upstreamHuaweiAncStateForLevel(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "0200", off),
        )
        assertEquals(
            "0200",
            upstreamMiuiAncLevel(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            ),
        )
        assertEquals(
            "0201",
            upstreamMiuiAncLevel(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x01),
            ),
        )
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x04),
            upstreamHuaweiAncStateForLevel(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                "0204",
                off,
            ),
        )
        assertEquals(
            "0204",
            upstreamMiuiAncLevel(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x04),
            ),
        )
        assertNull(
            upstreamHuaweiAncStateForLevel(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                "0202",
                off,
            ),
        )
    }

    @Test
    fun `two-state routes reject transparency without converting it to off`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        ).forEach { route ->
            assertNull(upstreamHuaweiAncStateForMode(route, 2, off))
            assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
            assertEquals(
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION),
                upstreamHuaweiAncStateForLevel(route, "0100", off),
            )
        }
    }

    @Test
    fun `FreeBuds 5i and 6i MIUI levels preserve the captured protocol values`() {
        val cases = mapOf(
            "0103" to 0x03,
            "0101" to 0x01,
            "0100" to 0x00,
            "0102" to 0x02,
        )

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
        ).forEach { route ->
            cases.forEach { (miuiPayload, huaweiSubMode) ->
                val state = upstreamHuaweiAncStateForLevel(route, miuiPayload, off)

                assertEquals(
                    HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, huaweiSubMode),
                    state,
                )
                assertEquals(miuiPayload, upstreamMiuiAncLevel(route, state!!))
            }
            assertNull(upstreamHuaweiAncStateForLevel(route, "0109", off))
        }
    }

    @Test
    fun `Pro 3 and 7i retain their MIUI to Huawei level translation`() {
        val cases = mapOf(
            "0103" to 0x01,
            "0101" to 0x00,
            "0100" to 0x02,
            "0102" to 0x03,
        )

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            cases.forEach { (miuiPayload, huaweiSubMode) ->
                val state = upstreamHuaweiAncStateForLevel(route, miuiPayload, off)

                assertEquals(
                    HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, huaweiSubMode),
                    state,
                )
                assertEquals(miuiPayload, upstreamMiuiAncLevel(route, state!!))
            }
            assertNull(upstreamHuaweiAncStateForLevel(route, "0109", off))
        }
    }

    @Test
    fun `FreeBuds 5 MIUI levels map to its captured three-level protocol`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5
        val cases = mapOf(
            "0103" to 0x03,
            "0101" to 0x01,
            "0100" to 0x00,
        )

        cases.forEach { (miuiPayload, huaweiSubMode) ->
            val state = upstreamHuaweiAncStateForLevel(route, miuiPayload, off)
            assertEquals(
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, huaweiSubMode),
                state,
            )
            assertEquals(miuiPayload, upstreamMiuiAncLevel(route, state!!))
        }
        assertNull(upstreamHuaweiAncStateForLevel(route, "0102", off))
        assertNull(upstreamHuaweiAncStateForLevel(route, "02ff", off))
        assertEquals(
            "0000",
            upstreamMiuiAncLevel(
                route,
                HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02),
            ),
        )
    }
}
