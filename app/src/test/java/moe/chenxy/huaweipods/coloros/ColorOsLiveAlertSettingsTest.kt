package moe.chenxy.huaweipods.coloros

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOsLiveAlertSettingsTest {
    @Test
    fun `lockscreen disabled conceals the entire notification`() {
        assertEquals(Notification.VISIBILITY_SECRET, colorOsLiveAlertVisibility(false))
    }

    @Test
    fun `lockscreen enabled permits the notification subject to system channel settings`() {
        assertEquals(Notification.VISIBILITY_PUBLIC, colorOsLiveAlertVisibility(true))
    }
}
