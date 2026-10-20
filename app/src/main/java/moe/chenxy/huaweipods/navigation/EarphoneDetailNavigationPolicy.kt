package moe.chenxy.huaweipods.navigation

import java.util.Locale

/**
 * 耳机详情页直达导航状态。
 *
 * 用于将通知点击触发的“用户希望查看某耳机”意向，与真实蓝牙连接状态彻底解耦。
 * 绝不能在点击通知后直接假定 connected，而必须等待底层真实蓝牙连接确认。
 */
sealed interface EarphoneDetailNavigationState {
    /** 空闲状态：没有待处理的直达请求 */
    data object Idle : EarphoneDetailNavigationState

    /**
     * 正在等待/验证蓝牙底层真实连接状态。
     * @param targetAddress 用户期望直达的耳机 MAC 地址
     * @param targetName 用户期望直达的耳机名称（可选）
     */
    data class Validating(
        val targetAddress: String,
        val targetName: String?,
    ) : EarphoneDetailNavigationState

    /**
     * 已确认目标耳机处于真实连接状态，可以安全展示该耳机的详情页。
     * @param address 确认连接的耳机 MAC 地址
     * @param name 确认连接的耳机名称
     */
    data class Connected(
        val address: String,
        val name: String?,
    ) : EarphoneDetailNavigationState

    /**
     * 目标设备不可用：可能设备已断开，或者底层当前连接的是另一个设备，或者超时未响应。
     */
    data class Unavailable(
        val targetAddress: String?,
        val reason: Reason,
    ) : EarphoneDetailNavigationState {
        enum class Reason {
            DISCONNECTED,
            DEVICE_MISMATCH,
            INVALID_ADDRESS,
            TIMEOUT,
        }
    }
}

/**
 * 状态机转换时产生的 UI 行为指导
 */
enum class NavigationAction {
    NONE,
    SWITCH_TO_EARPHONES_TAB,
    REQUEST_BLUETOOTH_STATUS_REFRESH,
    OPEN_EARPHONES_PICKER,
}

data class NavigationTransition(
    val newState: EarphoneDetailNavigationState,
    val action: NavigationAction,
)

/**
 * 通知直达耳机详情页的纯 Kotlin 状态机。
 */
object EarphoneDetailNavigationPolicy {
    private val macAddressRegex = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    fun isValidMacAddress(address: String?): Boolean =
        !address.isNullOrBlank() && macAddressRegex.matches(address.trim())

    /**
     * 用户点击通知（冷启动或 onNewIntent）请求直达耳机详情。
     */
    fun onRequestNavigation(
        currentState: EarphoneDetailNavigationState,
        targetAddress: String?,
        targetName: String?,
        currentHookConnected: Boolean,
        currentConnectedAddress: String?,
    ): NavigationTransition {
        val trimmedAddress = targetAddress?.trim()

        // Case E: targetAddress 为空时的防御处理
        if (trimmedAddress.isNullOrEmpty()) {
            return if (currentHookConnected && isValidMacAddress(currentConnectedAddress)) {
                NavigationTransition(
                    newState = EarphoneDetailNavigationState.Connected(
                        address = currentConnectedAddress!!.uppercase(Locale.US),
                        name = targetName,
                    ),
                    action = NavigationAction.SWITCH_TO_EARPHONES_TAB,
                )
            } else {
                NavigationTransition(
                    newState = EarphoneDetailNavigationState.Unavailable(
                        targetAddress = null,
                        reason = EarphoneDetailNavigationState.Unavailable.Reason.INVALID_ADDRESS,
                    ),
                    action = NavigationAction.OPEN_EARPHONES_PICKER,
                )
            }
        }

        // Case E: targetAddress 格式非法防御
        if (!isValidMacAddress(trimmedAddress)) {
            return NavigationTransition(
                newState = EarphoneDetailNavigationState.Unavailable(
                    targetAddress = targetAddress,
                    reason = EarphoneDetailNavigationState.Unavailable.Reason.INVALID_ADDRESS,
                ),
                action = NavigationAction.NONE,
            )
        }

        val normalizedTarget = trimmedAddress.uppercase(Locale.US)
        val normalizedCurrent = currentConnectedAddress?.trim()?.takeIf { isValidMacAddress(it) }?.uppercase(Locale.US)

        // Case A: 如果底层当前已经连上，且连上的恰好就是通知中的设备
        if (currentHookConnected && normalizedCurrent == normalizedTarget) {
            return NavigationTransition(
                newState = EarphoneDetailNavigationState.Connected(
                    address = normalizedTarget,
                    name = targetName,
                ),
                action = NavigationAction.SWITCH_TO_EARPHONES_TAB,
            )
        }

        // Case C: 如果底层当前已经连上另一个设备，发生设备不匹配，绝不将当前设备伪造为目标设备
        if (currentHookConnected && normalizedCurrent != null && normalizedCurrent != normalizedTarget) {
            return NavigationTransition(
                newState = EarphoneDetailNavigationState.Unavailable(
                    targetAddress = normalizedTarget,
                    reason = EarphoneDetailNavigationState.Unavailable.Reason.DEVICE_MISMATCH,
                ),
                action = NavigationAction.OPEN_EARPHONES_PICKER,
            )
        }

        // 尚未连上：进入验证中状态，请求底层刷新真实状态，绝不伪造已连接！
        return NavigationTransition(
            newState = EarphoneDetailNavigationState.Validating(
                targetAddress = normalizedTarget,
                targetName = targetName,
            ),
            action = NavigationAction.REQUEST_BLUETOOTH_STATUS_REFRESH,
        )
    }

    /**
     * 蓝牙底层上报连接状态变化（ACTION_PODS_CONNECTED, ACTION_PODS_CONNECTION_STATE_CHANGED, ACTION_PODS_DISCONNECTED）
     */
    fun onBluetoothStatusUpdated(
        currentState: EarphoneDetailNavigationState,
        isIncomingConnected: Boolean,
        incomingAddress: String?,
        incomingName: String?,
    ): NavigationTransition {
        if (currentState !is EarphoneDetailNavigationState.Validating) {
            return NavigationTransition(currentState, NavigationAction.NONE)
        }

        val normalizedIncoming = incomingAddress?.trim()?.uppercase(Locale.US)
        val normalizedTarget = currentState.targetAddress.uppercase(Locale.US)

        if (!isIncomingConnected) {
            // Case B: 底层明确反馈未连接或已断开：不得伪造连接
            return NavigationTransition(
                newState = EarphoneDetailNavigationState.Unavailable(
                    targetAddress = currentState.targetAddress,
                    reason = EarphoneDetailNavigationState.Unavailable.Reason.DISCONNECTED,
                ),
                action = NavigationAction.OPEN_EARPHONES_PICKER,
            )
        }

        // 真实连接成功：检查设备地址是否与通知期望一致
        return if (isValidMacAddress(normalizedIncoming) && normalizedIncoming == normalizedTarget) {
            // Case A: 验证期间目标设备连接成功，安全直达详情页
            NavigationTransition(
                newState = EarphoneDetailNavigationState.Connected(
                    address = normalizedTarget,
                    name = incomingName ?: currentState.targetName,
                ),
                action = NavigationAction.SWITCH_TO_EARPHONES_TAB,
            )
        } else {
            // Case C: 底层当前连接的是另一个设备 B，绝不能把 B 的状态显示成 A
            NavigationTransition(
                newState = EarphoneDetailNavigationState.Unavailable(
                    targetAddress = currentState.targetAddress,
                    reason = EarphoneDetailNavigationState.Unavailable.Reason.DEVICE_MISMATCH,
                ),
                action = NavigationAction.OPEN_EARPHONES_PICKER,
            )
        }
    }

    /**
     * Case D: 验证等待超时（例如超过规定时间仍未收到底层蓝牙响应）
     */
    fun onTimeout(currentState: EarphoneDetailNavigationState): NavigationTransition {
        if (currentState is EarphoneDetailNavigationState.Validating) {
            return NavigationTransition(
                newState = EarphoneDetailNavigationState.Unavailable(
                    targetAddress = currentState.targetAddress,
                    reason = EarphoneDetailNavigationState.Unavailable.Reason.TIMEOUT,
                ),
                action = NavigationAction.OPEN_EARPHONES_PICKER,
            )
        }
        return NavigationTransition(currentState, NavigationAction.NONE)
    }
}
