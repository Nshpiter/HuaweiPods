package moe.chenxy.huaweipods.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigClickActionPolicyTest {
    @Test
    fun `notification action accepts the Smart Audio target`() {
        assertEquals(
            ConfigManager.NOTIFICATION_CLICK_SMART_AUDIO,
            with(ConfigManager) {
                ConfigManager.NOTIFICATION_CLICK_SMART_AUDIO.normalizedNotificationClickAction()
            },
        )
        assertEquals(
            ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP,
            with(ConfigManager) { 99.normalizedNotificationClickAction() },
        )
    }

    @Test
    fun `more action accepts every declared target and rejects unknown values`() {
        listOf(
            ConfigManager.MORE_CLICK_SMART_AUDIO,
            ConfigManager.MORE_CLICK_SYSTEM_SETTINGS,
            ConfigManager.MORE_CLICK_MODULE,
        ).forEach { action ->
            assertEquals(
                action,
                with(ConfigManager) { action.normalizedMoreClickAction() },
            )
        }
        assertEquals(
            ConfigManager.MORE_CLICK_MODULE,
            with(ConfigManager) { (-1).normalizedMoreClickAction() },
        )
    }
}
