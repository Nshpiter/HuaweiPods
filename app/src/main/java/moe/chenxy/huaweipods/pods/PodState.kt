package moe.chenxy.huaweipods.pods

/**
 * FreeBuds 3 当前已验证的降噪状态。
 *
 * 未经实机验证的模式不应加入这里，也不能据此发送私有协议指令。
 */
enum class NoiseControlMode {
    OFF,
    NOISE_CANCELLATION,
}

fun NoiseControlMode.isNoiseCancellation(): Boolean = this == NoiseControlMode.NOISE_CANCELLATION
