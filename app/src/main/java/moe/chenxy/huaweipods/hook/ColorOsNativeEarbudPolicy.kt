package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiWearDetectionController
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams

internal const val COLOR_OS_WEAR_NOT_WEARING = 1
internal val COLOR_OS_LOW_BATTERY_TIERS = listOf(20, 10)

/**
 * 佩戴状态：充电中的耳机在盒内；其余在线耳机按耳机上报的整体入耳状态区分佩戴 / 未佩戴，
 * 尚未收到上报时视为佩戴（系统据此隐藏盒电量）。华为协议不区分左右耳是否入耳。
 */
internal fun colorOsEarbudWearStatus(pod: PodParams?, inEar: Boolean? = null): Int = when {
    pod == null || !pod.isConnected -> COLOR_OS_WEAR_UNKNOWN
    pod.isCharging -> COLOR_OS_WEAR_IN_CASE
    inEar == false -> COLOR_OS_WEAR_NOT_WEARING
    else -> COLOR_OS_WEAR_WEARING
}

/** “我的设备”降噪切换项按机型能力列出，顺序与系统 AirPods 页面一致。 */
internal fun colorOsNoiseControlOptions(route: HuaweiDeviceRoute): List<NoiseControlMode> {
    if (!route.supportsAnc) return emptyList()
    return buildList {
        add(NoiseControlMode.OFF)
        add(NoiseControlMode.NOISE_CANCELLATION)
        if (route.supportsTransparency) add(NoiseControlMode.TRANSPARENCY)
    }
}

/** “我的设备”入口下方的开关类快捷控制。 */
internal enum class ColorOsQuickToggle { LOW_LATENCY, WEAR_DETECTION }

/** 只列出该机型已有抓包验证写入协议的开关。 */
internal fun colorOsQuickToggles(route: HuaweiDeviceRoute): List<ColorOsQuickToggle> = buildList {
    if (route.supportsLowLatencyControl) add(ColorOsQuickToggle.LOW_LATENCY)
    if (HuaweiWearDetectionController.supports(route)) add(ColorOsQuickToggle.WEAR_DETECTION)
}

/** 开关文字带上当前状态；`enabled == null` 表示尚未从耳机读到状态。 */
internal fun colorOsQuickToggleLabel(
    toggle: ColorOsQuickToggle,
    enabled: Boolean?,
    chinese: Boolean,
): String {
    val name = when (toggle) {
        ColorOsQuickToggle.LOW_LATENCY -> if (chinese) "低时延" else "Low latency"
        ColorOsQuickToggle.WEAR_DETECTION -> if (chinese) "佩戴检测" else "Wear detection"
    }
    val state = when (enabled) {
        true -> if (chinese) "已开启" else "On"
        false -> if (chinese) "已关闭" else "Off"
        null -> if (chinese) "读取中" else "…"
    }
    return "$name · $state"
}

/**
 * 低电量提醒分 20% / 10% 两档：在线且未充电的耳机首次降到某一档时各提醒一次；全部回到 20% 以上
 * 或开始充电后重新布防。电量在档位边界小幅回弹不会重复提醒。
 */
internal class ColorOsLowBatteryTracker(
    private val tiers: List<Int> = COLOR_OS_LOW_BATTERY_TIERS,
) {
    private val notifiedTiers = mutableMapOf<String, Int>()

    @Synchronized
    fun shouldNotify(address: String, battery: BatteryParams): Boolean {
        val lowest = lowEarbuds(battery).minOfOrNull { it.second }
        if (lowest == null) {
            notifiedTiers.remove(address)
            return false
        }
        val tier = tiers.count { lowest <= it }
        if (tier <= (notifiedTiers[address] ?: 0)) return false
        notifiedTiers[address] = tier
        return true
    }

    fun lowEarbuds(battery: BatteryParams): List<Pair<Boolean, Int>> {
        val threshold = tiers.maxOrNull() ?: return emptyList()
        return listOfNotNull(
            battery.left?.let { true to it },
            battery.right?.let { false to it },
        ).filter { (_, pod) -> pod.isConnected && !pod.isCharging && pod.battery in 1..threshold }
            .map { (isLeft, pod) -> isLeft to pod.battery }
    }

    @Synchronized
    fun reset(address: String) {
        notifiedTiers.remove(address)
    }

    @Synchronized
    fun clear() {
        notifiedTiers.clear()
    }
}
