package moe.chenxy.huaweipods.coloros

import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ColorOsLiveAlertCompatibilityTest {
    @Test
    fun android16NotificationRequestsPromotionWithoutRequiringMinorUpdate() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notification = NotificationCompat.Builder(context, "HuaweiPodsLiveAlert")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("FreeClip")
            .setContentText("L 80%  R 90%")
            .setStyle(NotificationCompat.BigTextStyle().bigText("L 80%  R 90%"))
            .setOngoing(true)
            .requestColorOsLiveAlert("80%")
            .build()

        assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals("80%", notification.extras.getCharSequence("android.shortCriticalText"))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.hasPromotableCharacteristics())
    }
}
