package moe.chenxy.huaweipods.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresentationPolicyTest {
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
