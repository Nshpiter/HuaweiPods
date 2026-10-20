package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiDeviceRouteProbePolicyTest {
    private val nonce = "123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun `session requires exact address generation and 128 bit nonce`() {
        val session = requireNotNull(
            HuaweiDeviceRouteProbePolicy.session("aa:bb:cc:dd:ee:ff", 7L, nonce),
        )

        assertEquals("AA:BB:CC:DD:EE:FF", session.address)
        assertTrue(session.matches("aa:bb:cc:dd:ee:ff", 7L, nonce))
        assertFalse(session.matches("11:22:33:44:55:66", 7L, nonce))
        assertFalse(session.matches(session.address, 8L, nonce))
        assertFalse(session.matches(session.address, 7L, "123e4567-e89b-42d3-a456-426614174001"))
        assertNull(HuaweiDeviceRouteProbePolicy.session("not-an-address", 7L, nonce))
        assertNull(HuaweiDeviceRouteProbePolicy.session(session.address, 0L, nonce))
        assertNull(HuaweiDeviceRouteProbePolicy.session(session.address, 7L, "predictable"))
    }

    @Test
    fun `app and bluetooth gates both fail closed`() {
        assertTrue(HuaweiDeviceRouteProbePolicy.mayRequest(true, true, false))
        assertFalse(HuaweiDeviceRouteProbePolicy.mayRequest(false, true, false))
        assertFalse(HuaweiDeviceRouteProbePolicy.mayRequest(true, false, false))
        assertFalse(HuaweiDeviceRouteProbePolicy.mayRequest(true, true, true))
        assertTrue(HuaweiDeviceRouteProbePolicy.shouldOpenConnectedDetails(true, true))
        assertFalse(HuaweiDeviceRouteProbePolicy.shouldOpenConnectedDetails(true, false))
        assertFalse(HuaweiDeviceRouteProbePolicy.shouldOpenConnectedDetails(false, true))

        assertTrue(HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(true, true))
        assertFalse(HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(false, true))
        assertFalse(HuaweiDeviceRouteProbePolicy.mayProbeInBluetoothProcess(true, false))
    }

    @Test
    fun `only verified modern identities resolve`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("000153", "02"),
        )
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("000163", "02"),
        )
        assertNull(HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("000027", "01"))
        assertNull(HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("FFFFFF", "00"))
        assertNull(HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("000153", "2"))
        assertNull(HuaweiDeviceRouteProbePolicy.resolveVerifiedRoute("000153", "0a"))
    }

    @Test
    fun `per address cooldown rejects rapid repeat`() {
        assertTrue(HuaweiDeviceRouteProbePolicy.cooldownAllows(null, 100L))
        assertFalse(HuaweiDeviceRouteProbePolicy.cooldownAllows(100L, 101L))
        assertTrue(
            HuaweiDeviceRouteProbePolicy.cooldownAllows(
                100L,
                100L + HuaweiDeviceRouteProbePolicy.MIN_PROBE_INTERVAL_MS,
            ),
        )
        assertTrue(HuaweiDeviceRouteProbePolicy.cooldownAllows(10_000L, 100L))
    }

    @Test
    fun `cross package senders are exact and fail closed`() {
        assertTrue(
            HuaweiDeviceRouteProbePolicy.isTrustedRequestSender("moe.chenxy.huaweipods.coloros"),
        )
        assertFalse(HuaweiDeviceRouteProbePolicy.isTrustedRequestSender("com.example.attacker"))
        assertFalse(HuaweiDeviceRouteProbePolicy.isTrustedRequestSender(null))
        assertTrue(
            HuaweiDeviceRouteProbePolicy.isTrustedResultSender("com.android.bluetooth"),
        )
        assertFalse(HuaweiDeviceRouteProbePolicy.isTrustedResultSender("com.example.attacker"))
        assertFalse(HuaweiDeviceRouteProbePolicy.isTrustedResultSender(null))
    }
}
