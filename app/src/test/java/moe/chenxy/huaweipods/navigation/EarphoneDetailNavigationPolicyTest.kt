package moe.chenxy.huaweipods.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarphoneDetailNavigationPolicyTest {

    private val validMacA = "11:22:33:44:55:66"
    private val validMacB = "AA:BB:CC:DD:EE:FF"
    private val deviceNameA = "HUAWEI FreeBuds Pro 3"
    private val deviceNameB = "HUAWEI FreeClip"

    // ==========================================
    // Case A: 正常直达
    // ==========================================

    @Test
    fun `Case A - Direct navigation when target device is already connected`() {
        val initial = EarphoneDetailNavigationState.Idle
        val transition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = initial,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = true,
            currentConnectedAddress = validMacA,
        )

        assertEquals(NavigationAction.SWITCH_TO_EARPHONES_TAB, transition.action)
        assertTrue(transition.newState is EarphoneDetailNavigationState.Connected)
        val connectedState = transition.newState as EarphoneDetailNavigationState.Connected
        assertEquals(validMacA, connectedState.address)
        assertEquals(deviceNameA, connectedState.name)
    }

    @Test
    fun `Case A - Validating then connected via incoming bluetooth status confirmation`() {
        val initial = EarphoneDetailNavigationState.Idle
        // 1. 冷启动或尚未连上，点击通知进入 Validating
        val reqTransition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = initial,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = false,
            currentConnectedAddress = null,
        )

        assertEquals(NavigationAction.REQUEST_BLUETOOTH_STATUS_REFRESH, reqTransition.action)
        assertTrue(reqTransition.newState is EarphoneDetailNavigationState.Validating)
        val validatingState = reqTransition.newState as EarphoneDetailNavigationState.Validating
        assertEquals(validMacA, validatingState.targetAddress)

        // 2. 底层蓝牙随后广播真实连接成功，且地址匹配
        val statusTransition = EarphoneDetailNavigationPolicy.onBluetoothStatusUpdated(
            currentState = validatingState,
            isIncomingConnected = true,
            incomingAddress = validMacA.lowercase(), // 测试大小写归一化
            incomingName = deviceNameA,
        )

        assertEquals(NavigationAction.SWITCH_TO_EARPHONES_TAB, statusTransition.action)
        assertTrue(statusTransition.newState is EarphoneDetailNavigationState.Connected)
        val connectedState = statusTransition.newState as EarphoneDetailNavigationState.Connected
        assertEquals(validMacA, connectedState.address)
        assertEquals(deviceNameA, connectedState.name)
    }

    // ==========================================
    // Case B: 已断开但点击了旧通知
    // ==========================================

    @Test
    fun `Case B - Offline headset clicked, bluetooth reports disconnected`() {
        // 1. 用户点击旧通知
        val reqTransition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = false,
            currentConnectedAddress = null,
        )
        val validating = reqTransition.newState as EarphoneDetailNavigationState.Validating

        // 2. 底层蓝牙明确报告未连接 / 已断开
        val statusTransition = EarphoneDetailNavigationPolicy.onBluetoothStatusUpdated(
            currentState = validating,
            isIncomingConnected = false,
            incomingAddress = null,
            incomingName = null,
        )

        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, statusTransition.action)
        assertTrue(statusTransition.newState is EarphoneDetailNavigationState.Unavailable)
        val unavailable = statusTransition.newState as EarphoneDetailNavigationState.Unavailable
        assertEquals(EarphoneDetailNavigationState.Unavailable.Reason.DISCONNECTED, unavailable.reason)
        assertEquals(validMacA, unavailable.targetAddress)
    }

    @Test
    fun `Case B - Offline headset clicked, no response leads to timeout`() {
        // 1. 用户点击旧通知
        val reqTransition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = false,
            currentConnectedAddress = null,
        )
        val validating = reqTransition.newState as EarphoneDetailNavigationState.Validating

        // 2. 底层无任何响应，触发超时
        val timeoutTransition = EarphoneDetailNavigationPolicy.onTimeout(validating)

        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, timeoutTransition.action)
        assertTrue(timeoutTransition.newState is EarphoneDetailNavigationState.Unavailable)
        val unavailable = timeoutTransition.newState as EarphoneDetailNavigationState.Unavailable
        assertEquals(EarphoneDetailNavigationState.Unavailable.Reason.TIMEOUT, unavailable.reason)
    }

    // ==========================================
    // Case C: 通知设备与当前连接设备不匹配
    // ==========================================

    @Test
    fun `Case C - Currently connected to Device B, user clicked old notification for Device A`() {
        val transition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = true,
            currentConnectedAddress = validMacB,
        )

        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, transition.action)
        assertTrue(transition.newState is EarphoneDetailNavigationState.Unavailable)
        val unavailable = transition.newState as EarphoneDetailNavigationState.Unavailable
        assertEquals(EarphoneDetailNavigationState.Unavailable.Reason.DEVICE_MISMATCH, unavailable.reason)
        assertEquals(validMacA, unavailable.targetAddress)
    }

    @Test
    fun `Case C - Validating target A but bluetooth status confirms connection to Device B`() {
        val validating = EarphoneDetailNavigationState.Validating(
            targetAddress = validMacA,
            targetName = deviceNameA,
        )

        val statusTransition = EarphoneDetailNavigationPolicy.onBluetoothStatusUpdated(
            currentState = validating,
            isIncomingConnected = true,
            incomingAddress = validMacB,
            incomingName = deviceNameB,
        )

        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, statusTransition.action)
        assertTrue(statusTransition.newState is EarphoneDetailNavigationState.Unavailable)
        val unavailable = statusTransition.newState as EarphoneDetailNavigationState.Unavailable
        assertEquals(EarphoneDetailNavigationState.Unavailable.Reason.DEVICE_MISMATCH, unavailable.reason)
    }

    // ==========================================
    // Case D: 超时回退
    // ==========================================

    @Test
    fun `Case D - Timeout transition from Validating gracefully degrades`() {
        val validating = EarphoneDetailNavigationState.Validating(validMacA, deviceNameA)
        val transition = EarphoneDetailNavigationPolicy.onTimeout(validating)

        assertTrue(transition.newState is EarphoneDetailNavigationState.Unavailable)
        assertEquals(
            EarphoneDetailNavigationState.Unavailable.Reason.TIMEOUT,
            (transition.newState as EarphoneDetailNavigationState.Unavailable).reason,
        )
        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, transition.action)
    }

    @Test
    fun `Case D - Timeout ignored when state is Idle or Connected`() {
        val idleTransition = EarphoneDetailNavigationPolicy.onTimeout(EarphoneDetailNavigationState.Idle)
        assertEquals(EarphoneDetailNavigationState.Idle, idleTransition.newState)
        assertEquals(NavigationAction.NONE, idleTransition.action)

        val connected = EarphoneDetailNavigationState.Connected(validMacA, deviceNameA)
        val connectedTransition = EarphoneDetailNavigationPolicy.onTimeout(connected)
        assertEquals(connected, connectedTransition.newState)
        assertEquals(NavigationAction.NONE, connectedTransition.action)
    }

    // ==========================================
    // Case E: 异常与边界防护
    // ==========================================

    @Test
    fun `Case E - Null target address with active connected device routes to active device`() {
        val transition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = null,
            targetName = null,
            currentHookConnected = true,
            currentConnectedAddress = validMacA,
        )

        assertEquals(NavigationAction.SWITCH_TO_EARPHONES_TAB, transition.action)
        assertTrue(transition.newState is EarphoneDetailNavigationState.Connected)
        assertEquals(validMacA, (transition.newState as EarphoneDetailNavigationState.Connected).address)
    }

    @Test
    fun `Case E - Blank or null target address when disconnected opens device picker safely`() {
        val nullAddressTransition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = null,
            targetName = null,
            currentHookConnected = false,
            currentConnectedAddress = null,
        )
        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, nullAddressTransition.action)
        assertTrue(nullAddressTransition.newState is EarphoneDetailNavigationState.Unavailable)

        val emptyAddressTransition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = EarphoneDetailNavigationState.Idle,
            targetAddress = "   ",
            targetName = null,
            currentHookConnected = false,
            currentConnectedAddress = null,
        )
        assertEquals(NavigationAction.OPEN_EARPHONES_PICKER, emptyAddressTransition.action)
        assertTrue(emptyAddressTransition.newState is EarphoneDetailNavigationState.Unavailable)
    }

    @Test
    fun `Case E - Malformed MAC address does not crash or corrupt existing state`() {
        val invalidAddresses = listOf("invalid_mac", "11:22:33", "GG:HH:II:JJ:KK:LL", "11:22:33:44:55:66:77")
        for (badAddress in invalidAddresses) {
            assertFalse(EarphoneDetailNavigationPolicy.isValidMacAddress(badAddress))
            val transition = EarphoneDetailNavigationPolicy.onRequestNavigation(
                currentState = EarphoneDetailNavigationState.Idle,
                targetAddress = badAddress,
                targetName = deviceNameA,
                currentHookConnected = true,
                currentConnectedAddress = validMacA,
            )
            assertEquals(NavigationAction.NONE, transition.action)
            assertTrue(transition.newState is EarphoneDetailNavigationState.Unavailable)
            val unavailable = transition.newState as EarphoneDetailNavigationState.Unavailable
            assertEquals(EarphoneDetailNavigationState.Unavailable.Reason.INVALID_ADDRESS, unavailable.reason)
        }
    }

    @Test
    fun `Case E - Repeated request for already connected device is idempotent`() {
        val currentState = EarphoneDetailNavigationState.Connected(validMacA, deviceNameA)
        val transition = EarphoneDetailNavigationPolicy.onRequestNavigation(
            currentState = currentState,
            targetAddress = validMacA,
            targetName = deviceNameA,
            currentHookConnected = true,
            currentConnectedAddress = validMacA,
        )

        assertEquals(NavigationAction.SWITCH_TO_EARPHONES_TAB, transition.action)
        assertTrue(transition.newState is EarphoneDetailNavigationState.Connected)
        assertEquals(validMacA, (transition.newState as EarphoneDetailNavigationState.Connected).address)
    }
}
