package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiAncLevelProfileTest {
    @Test
    fun `FreeBuds 4E exposes only light and balanced ANC levels`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS4E
        val expected = listOf(
            HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
            HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
        )

        assertEquals(expected, route.ancLevelOptions)
        assertEquals(0x01, route.defaultAncSubMode)
        expected.forEach { option ->
            assertEquals(option.protocolValue, route.ancSubModeForMiuiLevel(option.miuiValue))
            assertEquals(option.miuiValue, route.miuiLevelForAncSubMode(option.protocolValue))
        }
        assertFalse(route.supportsAncSubMode(0xFF))
        assertFalse(route.supportsAncSubMode(0x03))
        assertNull(route.ancSubModeForMiuiLevel(0x02))
        assertNull(route.miuiLevelForAncSubMode(0xFF))
    }

    @Test
    fun `FreeBuds 5 exposes the three captured ANC levels without deep mode`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            listOf(
                HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
                HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
                HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
            ),
            route.ancLevelOptions,
        )
        assertEquals(0x03, route.defaultAncSubMode)
        assertTrue(route.supportsAncSubMode(0x03))
        assertTrue(route.supportsAncSubMode(0x01))
        assertTrue(route.supportsAncSubMode(0x00))
        assertFalse(route.supportsAncSubMode(0x02))
        assertNull(route.ancSubModeForMiuiLevel(0x02))
        assertNull(route.miuiLevelForAncSubMode(0x02))
    }

    @Test
    fun `FreeBuds 5 rejects unsupported ANC readback states`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03),
            route.validateAncState(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x03)),
        )
        assertNull(route.validateAncState(HuaweiAncState(NoiseControlMode.NOISE_CANCELLATION, 0x02)))
        assertNull(route.validateAncState(HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0xFF)))
    }

    @Test
    fun `FreeBuds 6i maps official dynamic light balanced and deep values from capture`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
        val expected = listOf(
            HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
            HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
            HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
            HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x02, miuiValue = 0x02),
        )

        assertEquals(expected, route.ancLevelOptions)
        assertEquals(0x03, route.defaultAncSubMode)
        expected.forEach { option ->
            assertEquals(option.protocolValue, route.ancSubModeForMiuiLevel(option.miuiValue))
            assertEquals(option.miuiValue, route.miuiLevelForAncSubMode(option.protocolValue))
        }
    }

    @Test
    fun `FreeBuds 5i maps all four captured ANC levels`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5I
        val expected = listOf(
            HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x03, miuiValue = 0x03),
            HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x01, miuiValue = 0x01),
            HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x00, miuiValue = 0x00),
            HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x02, miuiValue = 0x02),
        )

        assertEquals(expected, route.ancLevelOptions)
        assertEquals(0x03, route.defaultAncSubMode)
        expected.forEach { option ->
            assertEquals(option.protocolValue, route.ancSubModeForMiuiLevel(option.miuiValue))
            assertEquals(option.miuiValue, route.miuiLevelForAncSubMode(option.protocolValue))
        }
        assertEquals(
            HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02),
            route.validateAncState(HuaweiAncState(NoiseControlMode.TRANSPARENCY, 0x02)),
        )
    }

    @Test
    fun `Pro 3 and 7i retain their existing four-level mapping`() {
        val expected = listOf(
            HuaweiAncLevelOption(HuaweiAncLevel.ADAPTIVE, protocolValue = 0x01, miuiValue = 0x03),
            HuaweiAncLevelOption(HuaweiAncLevel.LIGHT, protocolValue = 0x00, miuiValue = 0x01),
            HuaweiAncLevelOption(HuaweiAncLevel.BALANCED, protocolValue = 0x02, miuiValue = 0x00),
            HuaweiAncLevelOption(HuaweiAncLevel.DEEP, protocolValue = 0x03, miuiValue = 0x02),
        )

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertEquals(route.name, expected, route.ancLevelOptions)
            assertEquals(route.name, 0x01, route.defaultAncSubMode)
            expected.forEach { option ->
                assertEquals(option.protocolValue, route.ancSubModeForMiuiLevel(option.miuiValue))
                assertEquals(option.miuiValue, route.miuiLevelForAncSubMode(option.protocolValue))
            }
        }
    }
}
