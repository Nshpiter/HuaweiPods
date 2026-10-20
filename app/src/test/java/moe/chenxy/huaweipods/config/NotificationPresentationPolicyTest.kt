package moe.chenxy.huaweipods.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresentationPolicyTest {
    @Test
    fun `persistent notification follows its switch`() {
        assertTrue(NotificationPresentationPolicy.shouldPostPersistentNotification(true))
        assertFalse(NotificationPresentationPolicy.shouldPostPersistentNotification(false))
    }
}
