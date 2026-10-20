package moe.chenxy.huaweipods.coloros

import org.junit.Assert.*
import org.junit.Test

class ColorOsLiveAlertEventOrderTest {
    @Test fun lateConnectionCannotOverrideNewerDisconnect() {
        assertFalse(acceptColorOsLiveAlertEvent(3, 3, 200, 100))
        assertTrue(acceptColorOsLiveAlertEvent(3, 3, 200, 201))
    }
    @Test fun equalTimestampAllowsIdempotentRetryAcrossTransports() {
        assertTrue(acceptColorOsLiveAlertEvent(3, 3, 200, 200))
    }
    @Test fun elapsedTimeFromPreviousBootDoesNotBlockNewConnection() {
        assertTrue(acceptColorOsLiveAlertEvent(3, 4, 90_000, 100))
    }
    @Test fun missingBootOrLegacyTimestampIsNotMisclassifiedAsStale() {
        assertTrue(acceptColorOsLiveAlertEvent(null, 4, 90_000, 100))
        assertTrue(acceptColorOsLiveAlertEvent(3, null, 90_000, 100))
        assertTrue(acceptColorOsLiveAlertEvent(3, 3, 90_000, 0))
    }
}
