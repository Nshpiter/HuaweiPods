package moe.chenxy.huaweipods.coloros

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import moe.chenxy.huaweipods.config.AppConfig
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue

class ColorOsLiveAlertSettingsIntegrationTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = targetContext.getSystemService(NotificationManager::class.java)
    private var ownsTestNotification = false
    private lateinit var context: RecordingContext

    @Before
    fun setUp() {
        assumeTrue(targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)
        // Do not replace a real headset card or modify application preferences on a user's device.
        assumeTrue(activeNotification() == null)
        assumeTrue(manager.getNotificationChannel(CHANNEL_ID) == null)
        context = RecordingContext(targetContext)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Live Alert settings test", NotificationManager.IMPORTANCE_LOW,
        ))
        ownsTestNotification = true
    }

    @After
    fun tearDown() {
        if (!ownsTestNotification) return
        manager.cancel(ColorOsLiveAlertService.NOTIFICATION_ID)
        manager.deleteNotificationChannel(CHANNEL_ID)
    }

    @Test
    fun disablingRemovesExistingCardWithoutAnotherBatteryEvent() {
        postTestNotification()

        applyColorOsLiveAlertSettings(context, AppConfig(persistentNotificationEnabled = false))

        await { activeNotification() == null }
        assertEquals(1, context.stoppedServices.size)
        assertEquals(ColorOsLiveAlertService::class.java.name, context.stoppedServices.single().component?.className)
        assertTrue(context.refreshRequests.isEmpty())
    }

    @Test
    fun lockscreenChangeUpdatesExistingCardAndPreservesChannelAndBatteryContent() {
        postTestNotification()
        val channel = requireNotNull(manager.getNotificationChannel(CHANNEL_ID))

        applyColorOsLiveAlertSettings(context, AppConfig(lockscreenNotificationEnabled = false))

        await { activeNotification()?.visibility == Notification.VISIBILITY_SECRET }
        val updated = activeNotification()!!
        assertEquals("L 80%  R 90%", updated.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals("preserved", updated.extras.getString("test_marker"))
        assertTrue(updated.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(CHANNEL_ID, updated.channelId)
        val unchangedChannel = requireNotNull(manager.getNotificationChannel(CHANNEL_ID))
        assertEquals(channel.importance, unchangedChannel.importance)
        assertEquals(channel.lockscreenVisibility, unchangedChannel.lockscreenVisibility)
        assertTrue(context.stoppedServices.isEmpty())

        applyColorOsLiveAlertSettings(context, AppConfig(lockscreenNotificationEnabled = true))
        await { activeNotification()?.visibility == Notification.VISIBILITY_PUBLIC }
        assertEquals(2, context.refreshRequests.size)
    }

    @Test
    fun enablingRequestsBluetoothRefreshWithoutInventingACachedCard() {
        applyColorOsLiveAlertSettings(context, AppConfig(persistentNotificationEnabled = true))

        assertTrue(activeNotification() == null)
        assertTrue(context.stoppedServices.isEmpty())
        val refresh = context.refreshRequests.single()
        assertEquals(HuaweiPodsAction.ACTION_REFRESH_STATUS, refresh.action)
        assertEquals("com.android.bluetooth", refresh.`package`)
        assertFalse(refresh.flags and Intent.FLAG_RECEIVER_FOREGROUND == 0)
        assertNotNull(context.broadcastOptions)
    }

    private fun postTestNotification() {
        val notification = NotificationCompat.Builder(targetContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("FreeClip settings test")
            .setContentText("L 80%  R 90%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(Bundle().apply { putString("test_marker", "preserved") })
            .build()
        manager.notify(ColorOsLiveAlertService.NOTIFICATION_ID, notification)
        await { activeNotification() != null }
    }

    private fun activeNotification(): Notification? = manager.activeNotifications.firstOrNull {
        it.id == ColorOsLiveAlertService.NOTIFICATION_ID && it.tag == null
    }?.notification

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20L)
        assertTrue("Notification state did not update", condition())
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val stoppedServices = mutableListOf<Intent>()
        val refreshRequests = mutableListOf<Intent>()
        var broadcastOptions: Bundle? = null

        override fun stopService(name: Intent): Boolean {
            stoppedServices += name
            return true
        }

        override fun sendBroadcast(intent: Intent, receiverPermission: String?, options: Bundle?) {
            refreshRequests += intent
            broadcastOptions = options
        }
    }

    private companion object {
        const val CHANNEL_ID = "HuaweiPodsLiveAlertSettingsTest"
    }
}
