package moe.chenxy.huaweipods.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresentationPolicyTest {
    @Test
    fun `persistent island respects style connection and independent notification switch`() {
        for (enabled in listOf(false, true)) {
            for (connected in listOf(false, true)) {
                for (notification in listOf(false, true)) {
                    for (mode in listOf(0, 1, 2, 99)) {
                        val expected = enabled && connected &&
                            (mode == ConfigManager.ISLAND_MODE_MODULE ||
                                (mode == ConfigManager.ISLAND_MODE_OFFICIAL && notification))
                        assertEquals(expected, NotificationPresentationPolicy.shouldKeepBatteryIsland(
                            enabled, mode, notification, connected,
                        ))
                    }
                }
            }
        }
    }

    @Test
    fun `persistent island is renewed before the system timeout and temporary island stays eight seconds`() {
        assertEquals(8, NotificationPresentationPolicy.batteryIslandTimeoutSeconds(false))
        assertTrue(NotificationPresentationPolicy.PERSISTENT_ISLAND_REFRESH_MS > 0)
        assertTrue(NotificationPresentationPolicy.PERSISTENT_ISLAND_REFRESH_MS <
            NotificationPresentationPolicy.batteryIslandTimeoutSeconds(true) * 1_000L)
    }

    @Test
    fun `legacy island mode migrates into the new master switch`() {
        assertFalse(
            NotificationPresentationPolicy.resolveSuperIslandEnabled(
                explicitValue = null,
                storedValue = true,
                storedMode = ConfigManager.ISLAND_MODE_NONE,
            ),
        )
        assertTrue(
            NotificationPresentationPolicy.resolveSuperIslandEnabled(
                explicitValue = null,
                storedValue = true,
                storedMode = ConfigManager.ISLAND_MODE_OFFICIAL,
            ),
        )
        assertFalse(
            NotificationPresentationPolicy.resolveSuperIslandEnabled(
                explicitValue = false,
                storedValue = true,
                storedMode = ConfigManager.ISLAND_MODE_MODULE,
            ),
        )
    }

    @Test
    fun `master switch gates every island style`() {
        assertEquals(
            ConfigManager.ISLAND_MODE_NONE,
            NotificationPresentationPolicy.effectiveIslandMode(
                enabled = false,
                style = ConfigManager.ISLAND_MODE_MODULE,
            ),
        )
        assertEquals(
            ConfigManager.ISLAND_MODE_MODULE,
            NotificationPresentationPolicy.effectiveIslandMode(
                enabled = true,
                style = ConfigManager.ISLAND_MODE_MODULE,
            ),
        )
    }

    @Test
    fun `persistent notification carries only the official island`() {
        assertTrue(
            NotificationPresentationPolicy.attachesOfficialIsland(
                ConfigManager.ISLAND_MODE_OFFICIAL,
            ),
        )
        assertFalse(
            NotificationPresentationPolicy.attachesOfficialIsland(
                ConfigManager.ISLAND_MODE_MODULE,
            ),
        )
        assertFalse(
            NotificationPresentationPolicy.attachesOfficialIsland(
                ConfigManager.ISLAND_MODE_NONE,
            ),
        )
    }

    @Test
    fun `persistent notification master switch gates notification posting`() {
        assertTrue(NotificationPresentationPolicy.shouldPostPersistentNotification(true))
        assertFalse(NotificationPresentationPolicy.shouldPostPersistentNotification(false))
    }
}
