package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `unified build recognizes every integrated model by exact official alias`() {
        val cases = listOf(
            "HUAWEI FreeBuds 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            "FreeBuds 4E" to HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            "FreeBuds 5" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            "HUAWEI FreeBuds 5i" to HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            "HUAWEI FreeBuds 6i" to HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            "FreeBuds Pro 3" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            "HUAWEI FreeBuds Pro 4" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            "FreeBuds Pro 5" to HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            "HUAWEI FreeBuds 7i" to HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            "FreeClip" to HuaweiDeviceRoute.HUAWEI_FREECLIP,
            "HUAWEI FreeClip 2" to HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            "HUAWEI FreeArc" to HuaweiDeviceRoute.HUAWEI_FREEARC,
            "HUAWEI Eyewear" to HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            "Eyewear 2" to HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )

        cases.forEach { (deviceName, expectedRoute) ->
            assertEquals(deviceName, expectedRoute, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals(HuaweiDeviceRoute.entries.size - 1, enabledHuaweiDeviceRoutes().size)
    }

    @Test
    fun `near matches and unrelated bluetooth devices remain unsupported`() {
        listOf(
            "HUAWEI FreeBuds Pro 5i",
            "HUAWEI Eyewear 2 Pro",
            "My custom freebuds3 headset",
            "OPPO Enco X3",
            "HUAWEI WATCH GT",
            "",
            "   ",
        ).forEach { deviceName ->
            assertEquals(deviceName, HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(deviceName))
        }
        assertEquals(HuaweiDeviceRoute.UNSUPPORTED, detectHuaweiDeviceRoute(null))
    }

    @Test
    fun `noise control capabilities follow verified model protocols`() {
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsAnc)
        assertTrue(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsAncDirectionDial)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS3.supportsTransparency)

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertTrue(route.displayName, route.supportsAncStateReadback)
            assertTrue(route.displayName, route.supportsDiscreteAncLevels)
        }
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS5.supportsTransparency)
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route -> assertTrue(route.displayName, route.supportsTransparency) }

        enabledHuaweiDeviceRoutes().forEach { route ->
            assertEquals(
                route.displayName,
                route.supportsDiscreteAncLevels,
                route.ancLevelOptions.isNotEmpty(),
            )
        }
    }

    @Test
    fun `open-ear and eyewear families never expose traditional ANC`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertTrue(route.displayName, route.supportsRfcommBattery)
            assertFalse(route.displayName, route.supportsAnc)
        }
        assertFalse(HuaweiDeviceRoute.HUAWEI_EYEWEAR.hasChargingCase)
        assertFalse(HuaweiDeviceRoute.HUAWEI_EYEWEAR2.hasChargingCase)
    }

    @Test
    fun `only eyewear models omit the charging case`() {
        val routesWithoutChargingCase = setOf(
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )

        enabledHuaweiDeviceRoutes().forEach { route ->
            assertEquals(
                route.name,
                route !in routesWithoutChargingCase,
                route.hasChargingCase,
            )
        }
    }

    @Test
    fun `gesture configuration is only exposed for implemented routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route -> assertTrue(route.displayName, route.supportsGestureConfiguration) }
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREEBUDS5.supportsGestureConfiguration)
        assertFalse(HuaweiDeviceRoute.HUAWEI_FREECLIP.supportsGestureConfiguration)
    }

    @Test
    fun `reported earbud availability is restricted to FreeBuds Pro 5`() {
        HuaweiDeviceRoute.entries.forEach { route ->
            assertEquals(
                route.name,
                route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                route.usesReportedEarbudAvailability,
            )
        }
    }

    @Test
    fun `background battery refresh is restricted to FreeClip 2`() {
        HuaweiDeviceRoute.entries.forEach { route ->
            assertEquals(
                route.name,
                route == HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                route.supportsBackgroundBatteryRefresh,
            )
        }
    }

    @Test
    fun `broadcast route codec uses stable values and round trips every enabled route`() {
        val expectedValues = linkedMapOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to "HUAWEI_FREEBUDS3",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to "HUAWEI_FREEBUDS4E",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5 to "HUAWEI_FREEBUDS5",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to "HUAWEI_FREEBUDS5I",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to "HUAWEI_FREEBUDS6I",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 to "HUAWEI_FREEBUDS_PRO3",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 to "HUAWEI_FREEBUDS_PRO4",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to "HUAWEI_FREEBUDS_PRO5",
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to "HUAWEI_FREEBUDS7I",
            HuaweiDeviceRoute.HUAWEI_FREECLIP to "HUAWEI_FREECLIP",
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 to "HUAWEI_FREECLIP2",
            HuaweiDeviceRoute.HUAWEI_FREEARC to "HUAWEI_FREEARC",
            HuaweiDeviceRoute.HUAWEI_EYEWEAR to "HUAWEI_EYEWEAR",
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to "HUAWEI_EYEWEAR2",
        )

        assertEquals(enabledHuaweiDeviceRoutes(), expectedValues.keys.toList())
        expectedValues.forEach { (route, value) ->
            assertEquals(route.name, value, encodeHuaweiDeviceRouteForBroadcast(route))
            assertEquals(value, route, decodeHuaweiDeviceRouteFromBroadcast(value))
        }
        assertEquals(expectedValues.size, expectedValues.values.toSet().size)
    }

    @Test
    fun `broadcast route codec rejects unsupported null and unknown values`() {
        assertNull(encodeHuaweiDeviceRouteForBroadcast(HuaweiDeviceRoute.UNSUPPORTED))
        listOf(
            null,
            "",
            "UNSUPPORTED",
            "FREEBUDS3",
            "HUAWEI_FREEBUDS_3",
            "huawei_freebuds3",
            " HUAWEI_FREEBUDS3 ",
        ).forEach { value ->
            assertNull(value, decodeHuaweiDeviceRouteFromBroadcast(value))
        }
    }

    @Test
    fun `broadcast route codec rejects a known route disabled by feature gate`() {
        val disabledRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        val featureGate: (HuaweiDeviceRoute) -> Boolean = { route ->
            route != disabledRoute && isHuaweiDeviceRouteEnabled(route)
        }

        assertNull(encodeHuaweiDeviceRouteForBroadcast(disabledRoute, featureGate))
        assertNull(decodeHuaweiDeviceRouteFromBroadcast("HUAWEI_FREEBUDS6I", featureGate))

        val enabledRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3
        assertEquals(
            "HUAWEI_FREEBUDS_PRO3",
            encodeHuaweiDeviceRouteForBroadcast(enabledRoute, featureGate),
        )
        assertEquals(
            enabledRoute,
            decodeHuaweiDeviceRouteFromBroadcast("HUAWEI_FREEBUDS_PRO3", featureGate),
        )
    }
}
