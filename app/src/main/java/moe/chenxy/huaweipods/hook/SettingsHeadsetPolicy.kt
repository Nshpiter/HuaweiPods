package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.ancLevelOptions
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsGestureConfiguration
import moe.chenxy.huaweipods.pods.supportsTransparency

internal val settingsEarTipFitKeywords = listOf(
    "耳塞贴合度检测",
    "耳塞贴合度测试",
    "耳塞贴合",
    "Ear tip fit test",
    "Earbud fit test",
    "Ear tip fit",
)

internal fun isSettingsEarTipFitText(text: String): Boolean =
    settingsEarTipFitKeywords.any { it.equals(text.trim(), ignoreCase = true) }

/**
 * 小米耳机模板自带的通知开关不会控制 HuaweiPods 创建的通知。华为耳机页面隐藏该假入口，
 * 统一由模块设置中的“耳机常驻通知”开关管理，避免用户看到开关变化但通知行为不变。
 */
internal val settingsNotificationDisplayKeywords = listOf(
    "通知栏显示",
    "耳机连接后在通知栏显示状态信息",
    "Notification display",
    "Show notification",
    "Show in notification",
    "Show in notification shade",
    "Display status information in the notification",
)

internal fun isSettingsNotificationDisplayText(text: String): Boolean =
    settingsNotificationDisplayKeywords.any { it.equals(text.trim(), ignoreCase = true) }

/**
 * 系统蓝牙详情页使用的是小米耳机模板。这里只暴露已经桥接到华为协议的能力，
 * 避免模板把硬件可能具备、但 HuaweiPods 尚未实现的入口显示成可用功能。
 */
internal data class SettingsHeadsetUiPolicy(
    val showAnc: Boolean,
    val showTransparency: Boolean,
    val showGestureConfiguration: Boolean,
    val showEarTipFitTest: Boolean,
)

internal fun settingsHeadsetUiPolicy(route: HuaweiDeviceRoute): SettingsHeadsetUiPolicy {
    val supported = route.isSupported
    return SettingsHeadsetUiPolicy(
        showAnc = supported && route.supportsAnc,
        showTransparency = supported && route.supportsAnc && route.supportsTransparency,
        showGestureConfiguration = supported && route.supportsGestureConfiguration,
        // 物理能力与本模块的协议覆盖分开：尚未桥接测试命令前，所有型号都不展示。
        showEarTipFitTest = false,
    )
}

internal fun shouldUpdateSettingsAncUi(route: HuaweiDeviceRoute): Boolean =
    settingsHeadsetUiPolicy(route).showAnc

internal fun usesCustomSettingsAncSelector(route: HuaweiDeviceRoute): Boolean =
    route.supportsDiscreteAncLevels && route.ancLevelOptions.size != 4

/** 6i 原生二态通透映射已经过真机校正；其他子模式机型使用模块的协议选择器。 */
internal fun usesNativeSettingsTransparencySelector(route: HuaweiDeviceRoute): Boolean =
    route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

/**
 * RecyclerView 的一个 item 可能只是单行设置，也可能承载整组相邻设置。
 * 仅在 item 自身/内部只形成一个交互分支时整项折叠，避免误删同卡片中的其他行。
 */
internal fun shouldCollapseSettingsRecyclerItem(
    itemInteractive: Boolean,
    topLevelInteractiveDescendantCount: Int,
): Boolean = when {
    topLevelInteractiveDescendantCount > 1 -> false
    itemInteractive -> true
    else -> topLevelInteractiveDescendantCount == 1
}

internal data class SettingsRowLayoutState(
    val height: Int?,
    val topMargin: Int?,
    val bottomMargin: Int?,
    val minimumHeight: Int,
)

internal fun SettingsRowLayoutState.collapsed(): SettingsRowLayoutState = copy(
    height = height?.let { 0 },
    topMargin = topMargin?.let { 0 },
    bottomMargin = bottomMargin?.let { 0 },
    minimumHeight = 0,
)
