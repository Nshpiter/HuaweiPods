package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiLinkAncRoutingTest {
    @Test
    fun `FreeClip2 spatial effect accepts native and offset MiLink states`() {
        assertEquals(
            FreeClip2SpatialAudioMode.OFF,
            freeClip2SpatialModeForMiLinkAudioEffect(0),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            freeClip2SpatialModeForMiLinkAudioEffect(21),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.FIXED,
            freeClip2SpatialModeForMiLinkAudioEffect(32),
        )
        assertNull(freeClip2SpatialModeForMiLinkAudioEffect(3))
    }

    @Test
    fun `FreeClip2 spatial effect round trips MiLink order without swapping fixed and tracking`() {
        FreeClip2SpatialAudioMode.entries.forEach { mode ->
            val hostValue = miLinkAudioEffectForFreeClip2SpatialMode(mode)
            assertEquals(mode, freeClip2SpatialModeForMiLinkAudioEffect(hostValue))
        }
        assertEquals(2, miLinkAudioEffectForFreeClip2SpatialMode(FreeClip2SpatialAudioMode.FIXED))
        assertEquals(1, miLinkAudioEffectForFreeClip2SpatialMode(FreeClip2SpatialAudioMode.HEAD_TRACKING))
    }

    @Test
    fun `clip and eyewear routes never expose ANC`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertNull(miLinkAncModeFor(route, 2))
            assertEquals(-1, miLinkHostAncStateFor(route, 2))
            assertEquals(0, miLinkAncSwitchStateFor(route))
            assertNull(huaweiAncStatusForMiLink(route, 0))
            assertNull(huaweiAncStatusForMiLink(route, 1))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `three-state routes preserve off ANC and transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertEquals(2, miLinkAncModeFor(route, 3))
            assertEquals(1, miLinkAncSwitchStateFor(route))
            assertEquals(1, huaweiAncStatusForMiLink(route, 0))
            assertEquals(2, huaweiAncStatusForMiLink(route, 1))
            assertEquals(3, huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `two-state ANC routes reject transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertEquals(1, miLinkAncSwitchStateFor(route))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `only two-state ANC routes detach the native transparency button`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        ).forEach { route -> assertTrue(shouldDetachMiLinkTransparency(route)) }

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route -> assertFalse(shouldDetachMiLinkTransparency(route)) }
    }

    @Test
    fun `addressless ANC card fallback requires one live card and active ANC route`() {
        assertTrue(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 2,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = false,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
    }

    @Test
    fun `three-state models use their captured submode defaults`() {
        assertEquals(
            0x03,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x02,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0xFF,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x01,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                huaweiStatus = 3,
                requestedSubMode = 0x01,
                storedSubMode = null,
            ),
        )
    }

    @Test
    fun `FreeBuds 5 MiLink routing uses three captured levels and rejects deep`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            0x03,
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        listOf(0x03, 0x01, 0x00).forEach { level ->
            assertEquals(
                level,
                normalizeMiLinkAncSubMode(
                    route,
                    huaweiStatus = 2,
                    requestedSubMode = level,
                    storedSubMode = null,
                ),
            )
        }
        assertNull(
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 2,
                requestedSubMode = 0x02,
                storedSubMode = null,
            ),
        )
        assertNull(
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 3,
                requestedSubMode = 0xFF,
                storedSubMode = null,
            ),
        )
    }

    @Test
    fun `dynamic MiLink state requires the same address and route`() {
        val address = "AA:BB:CC:DD:EE:01"
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

        assertTrue(matchesMiLinkStateOwner(address, route, address.lowercase(), route))
        assertFalse(matchesMiLinkStateOwner(address, route, "AA:BB:CC:DD:EE:02", route))
        assertFalse(
            matchesMiLinkStateOwner(
                address,
                route,
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            ),
        )
        assertFalse(matchesMiLinkStateOwner(null, route, address, route))
        assertFalse(matchesMiLinkStateOwner(address, route, null, route))
    }
}
