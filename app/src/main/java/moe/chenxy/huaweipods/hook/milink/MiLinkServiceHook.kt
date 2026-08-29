package moe.chenxy.huaweipods.hook.milink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.config.PodImageChangeNotifier
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.hook.HuaweiAncSubModeSelectorView
import moe.chenxy.huaweipods.hook.FreeClip2AudioPendingGate
import moe.chenxy.huaweipods.hook.FreeClip2AudioUiState
import moe.chenxy.huaweipods.hook.HookContext
import moe.chenxy.huaweipods.hook.HuaweiFreeClip2AudioControlsView
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.hook.callMethod
import moe.chenxy.huaweipods.hook.getObjectField
import moe.chenxy.huaweipods.hook.huaweiFreeClip2AudioLabels
import moe.chenxy.huaweipods.hook.setObjectField
import moe.chenxy.huaweipods.hook.shouldDispatchFreeClip2AudioSelection
import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiAncState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerPreset
import moe.chenxy.huaweipods.pods.HuaweiEqualizerPresetTransport
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.SmartAudioFreeClip2BridgePolicy
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.pods.ancLevelOptions
import moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast
import moe.chenxy.huaweipods.pods.defaultTransparencySubMode
import moe.chenxy.huaweipods.pods.detectHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.pods.huaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported
import moe.chenxy.huaweipods.pods.normalizeHuaweiAncSubMode
import moe.chenxy.huaweipods.pods.resolveHuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.readHuaweiEqualizerCustomPresets
import moe.chenxy.huaweipods.pods.supportsAnc
import moe.chenxy.huaweipods.pods.supportsAncStateReadback
import moe.chenxy.huaweipods.pods.supportsAncSubMode
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsLowLatencyControl
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.pods.transparencySubModes
import moe.chenxy.huaweipods.pods.usesReportedEarbudAvailability
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.normalizedEarbudAvailability
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import moe.chenxy.huaweipods.utils.ModuleResourceResolver
import moe.chenxy.huaweipods.utils.PodImageLoader
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal data class MiLinkAncSelection(
    val status: Int,
    val subMode: Int? = null,
)

/**
 * 融合中心写入 ANC 后会先收到一次写入前已经在途的旧回读。
 * 等待目标状态确认期间拒绝旧值，超时后重新以耳机回读为准。
 */
internal class MiLinkAncPendingGate(
    private val timeoutMs: Long = 5_000L,
) {
    private var pending: MiLinkAncSelection? = null
    private var pendingSinceMs = 0L

    fun tryBegin(selection: MiLinkAncSelection, nowMs: Long): Boolean {
        expire(nowMs)
        if (pending?.matches(selection) == true) return false
        pending = selection
        pendingSinceMs = nowMs
        return true
    }

    fun shouldAcceptConfirmation(selection: MiLinkAncSelection, nowMs: Long): Boolean {
        val current = pending ?: return true
        if (nowMs - pendingSinceMs !in 0 until timeoutMs) {
            clear()
            return true
        }
        if (!current.matches(selection)) return false
        clear()
        return true
    }

    fun hasPending(nowMs: Long): Boolean {
        expire(nowMs)
        return pending != null
    }

    fun clear() {
        pending = null
        pendingSinceMs = 0L
    }

    internal fun current(): MiLinkAncSelection? = pending

    private fun expire(nowMs: Long) {
        if (pending != null && nowMs - pendingSinceMs !in 0 until timeoutMs) clear()
    }

    private fun MiLinkAncSelection.matches(other: MiLinkAncSelection): Boolean =
        status == other.status &&
            (subMode == null || other.subMode == null || subMode == other.subMode)
}

internal fun shouldPresentAsMiLinkAudioGlasses(route: HuaweiDeviceRoute): Boolean = when (route) {
    HuaweiDeviceRoute.HUAWEI_EYEWEAR,
    HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
    -> true
    else -> false
}

internal fun shouldPollVisibleMiLinkAnc(
    route: HuaweiDeviceRoute,
    visibleDetailCount: Int,
): Boolean = route.supportsAncStateReadback && visibleDetailCount > 0

/**
 * HyperOS 4 的原生卡片只有主动调用刷新方法才会绘制初始选中态。legacy 卡片需要
 * 回放点击，构造阶段执行会误触发两态按钮的自定义监听，因此只初始化有原生刷新入口的卡片。
 */
internal fun shouldPrimeMiLinkAncCard(
    route: HuaweiDeviceRoute,
    hostSpec: MiLinkAncHostSpec,
    reason: String,
): Boolean = route.supportsAnc &&
    hostSpec.refreshMethodNames != null &&
    (reason == "constructor" || reason == "constructor-post")

internal data class MiLinkAncHostRefreshDecision(
    val hostState: Int,
    val guardAsUiOnly: Boolean,
)

/**
 * 融合中心会晚于卡片构造和用户点击再次送入自己的 ANC 缓存。
 * HyperOS 4 的 M(int) 只负责重画卡片，真正点击由 setAncStateBlock 独立处理；
 * 因此当前 ANC 卡片必须始终以模块状态重画，不能让宿主旧值反向改写耳机。
 */
internal fun miLinkAncHostRefreshDecision(
    cardRoute: HuaweiDeviceRoute,
    activeRoute: HuaweiDeviceRoute,
    currentHuaweiStatus: Int,
    hostSpec: MiLinkAncHostSpec,
    incomingHostState: Int,
): MiLinkAncHostRefreshDecision = if (cardRoute.supportsAnc && cardRoute == activeRoute) {
    MiLinkAncHostRefreshDecision(
        hostState = miLinkHostAncStateFor(cardRoute, currentHuaweiStatus, hostSpec),
        guardAsUiOnly = true,
    )
} else {
    MiLinkAncHostRefreshDecision(
        hostState = incomingHostState,
        guardAsUiOnly = false,
    )
}

private data class HiddenCapabilityView(
    val parent: ViewGroup,
    val index: Int,
    val width: Int,
    val height: Int,
    val visibility: Int,
    val isEnabled: Boolean,
    val isClickable: Boolean,
)

internal data class MiLinkAncHostSpec(
    val adapterName: String,
    val cardClassName: String,
    val titleIdNames: List<String>,
    val selectCardIdName: String?,
    val heightMethodName: String,
    val recomputeHeightWhenHidden: Boolean,
    val displayValueOrder: MiLinkAncValueOrder,
    val refreshMethodNames: Set<String>? = null,
)

internal enum class MiLinkAncValueOrder {
    OFF_NOISE_TRANSPARENCY,
    NOISE_TRANSPARENCY_OFF,
}

internal val miLinkAncHostSpecs = listOf(
    MiLinkAncHostSpec(
        adapterName = "legacy",
        cardClassName = "com.miui.circulateplus.world.headset.j",
        titleIdNames = listOf("anc_card_title"),
        selectCardIdName = null,
        heightMethodName = "B",
        recomputeHeightWhenHidden = true,
        displayValueOrder = MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
    ),
    MiLinkAncHostSpec(
        adapterName = "hyperos4-v18",
        cardClassName = "com.miui.circulateplus.world.headset.r",
        titleIdNames = listOf("anc_card_text", "anc_card_title"),
        selectCardIdName = "anc_select_card",
        heightMethodName = "W",
        // OS4 的总高度同时覆盖设备信息、ANC、空间音频等区域。FreeClip 2 会用自定义音效卡
        // 替代被隐藏的 ANC 区域，不能再让宿主扣掉这段高度，否则顶部名称/电量会被裁掉。
        recomputeHeightWhenHidden = false,
        // HyperOS 4 v18 的 HeadsetInfo/M(int) 显示域：0=降噪、1=通透、2=关闭。
        // AncBatteryController 的命令域仍是 0=关闭、1=降噪、2=通透，不能共用此顺序。
        displayValueOrder = MiLinkAncValueOrder.NOISE_TRANSPARENCY_OFF,
        // r 还包含测量、点击和动画回调，只在 M(int) 真正刷新 ANC 状态时处理。
        refreshMethodNames = setOf("M"),
    ),
)

internal fun compatibleMiLinkAncHostSpecs(
    hasCompatibleConstructor: (String) -> Boolean,
): List<MiLinkAncHostSpec> = miLinkAncHostSpecs.filter { spec ->
    hasCompatibleConstructor(spec.cardClassName)
}

internal data class MiLinkAudioEffectHostSpec(
    val adapterName: String,
    val sectionClassName: String,
    val renderMethodName: String,
    val valueOrder: MiLinkSpatialAudioValueOrder,
    val titleIdName: String? = null,
    val selectCardIdName: String? = null,
    val soundEffectSlotIdName: String? = null,
)

internal enum class MiLinkSpatialAudioValueOrder {
    HEAD_TRACKING_FIRST,
    FIXED_FIRST,
}

internal val miLinkAudioEffectHostSpecs = listOf(
    MiLinkAudioEffectHostSpec(
        adapterName = "legacy",
        sectionClassName = "com.miui.circulateplus.world.headset.w0",
        renderMethodName = "o",
        valueOrder = MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST,
    ),
    MiLinkAudioEffectHostSpec(
        adapterName = "hyperos4-v18",
        sectionClassName = "com.miui.circulateplus.world.headset.h1",
        renderMethodName = "w",
        valueOrder = MiLinkSpatialAudioValueOrder.FIXED_FIRST,
        titleIdName = "mi_audio_effect_card_text",
        selectCardIdName = "mi_audio_effect_select_card",
        // 复用无 ANC 机型空出的原生槽位，避免在空间音频区块后追加 View 撑破宿主高度。
        soundEffectSlotIdName = "anc_select_card",
    ),
)

internal fun selectMiLinkAudioEffectHostSpec(
    hasCompatibleConstructor: (String) -> Boolean,
): MiLinkAudioEffectHostSpec? = miLinkAudioEffectHostSpecs.firstOrNull { spec ->
    hasCompatibleConstructor(spec.sectionClassName)
}

private data class AncCardBinding(
    val detail: WeakReference<Any>,
    val hostSpec: MiLinkAncHostSpec,
    var route: HuaweiDeviceRoute = HuaweiDeviceRoute.UNSUPPORTED,
    var resolvedAddress: String? = null,
    var resolvedName: String? = null,
    var resolvedFakeDeviceId: String? = null,
    var resolvedActiveRoute: HuaweiDeviceRoute = HuaweiDeviceRoute.UNSUPPORTED,
    var routeResolved: Boolean = false,
    var clearView: WeakReference<View>? = null,
    var capabilityContainer: WeakReference<View>? = null,
    var missingViewLogged: Boolean = false,
    var renderedHostAncState: Int? = null,
    var pendingHostAncState: Int? = null,
)

private data class MiAudioEffectBinding(
    val detail: WeakReference<Any>,
    val hostSpec: MiLinkAudioEffectHostSpec,
)

private data class FreeClip2OptionOrderState(
    val rootsInOriginalOrder: List<WeakReference<View>>,
    val originalIndices: List<Int>,
)

private data class FreeClip2OptionLayout(
    val parent: ViewGroup,
    val off: View,
    val fixed: View,
    val headTracking: View,
    val indices: List<Int>,
)

private data class FreeClip2SectionPlacement(
    val parent: ViewGroup,
    val audioHeadingRoot: View?,
    val cardAnchor: View,
)

private data class MiLinkHeadsetIconViewState(
    val originalDrawable: Drawable?,
    val originalScaleType: ImageView.ScaleType,
    val originalAdjustViewBounds: Boolean,
    var requestedAddress: String? = null,
    var requestedRoute: HuaweiDeviceRoute = HuaweiDeviceRoute.UNSUPPORTED,
    var requestedKey: String? = null,
)

private data class MiLinkHeadsetIconBitmapCache(
    val address: String,
    val route: HuaweiDeviceRoute,
    val key: String,
    val bitmap: Bitmap,
)

private data class MiLinkHeadsetIconRequest(
    val address: String,
    val route: HuaweiDeviceRoute,
    val key: String,
)

private data class MiLinkVolumeProgressBinding(
    val root: WeakReference<View>,
    val route: HuaweiDeviceRoute,
)

private data class MiLinkBoundClickState(
    val listener: View.OnClickListener?,
    val clickable: Boolean,
)

/**
 * Bluetooth profile 代理是系统持有的异步回调，热重载前必须等它完成解绑。
 * [active] 只决定回调是否还能执行业务；无论是否失效，收到代理后都要立即关闭。
 */
private class MiLinkProfileProxyRequest(
    val adapter: BluetoothAdapter,
    val profile: Int,
    val generation: Int,
) {
    val callbackLock = Any()
    val active = AtomicBoolean(true)
    val completed = AtomicBoolean(false)
    val completion = CountDownLatch(1)
    val proxyClosed = AtomicBoolean(false)

    @Volatile
    var proxy: BluetoothProfile? = null
}

/** 将 Huawei 的 1/2/3 状态映射为融合设备中心的 0/1/2。无 ANC 的机型不参与接管。 */
internal fun miLinkAncModeFor(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    valueOrder: MiLinkAncValueOrder = MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
): Int? {
    if (!route.supportsAnc) return null
    val mode = when (huaweiStatus) {
        2, 5, 6, 7, 8 -> NoiseControlMode.NOISE_CANCELLATION
        3 -> NoiseControlMode.TRANSPARENCY.takeIf { route.supportsTransparency }
            ?: NoiseControlMode.OFF
        else -> NoiseControlMode.OFF
    }
    return when (valueOrder) {
        MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY -> when (mode) {
            NoiseControlMode.OFF -> 0
            NoiseControlMode.NOISE_CANCELLATION -> 1
            NoiseControlMode.TRANSPARENCY -> 2
            NoiseControlMode.UNKNOWN -> 0
        }
        MiLinkAncValueOrder.NOISE_TRANSPARENCY_OFF -> when (mode) {
            NoiseControlMode.NOISE_CANCELLATION -> 0
            NoiseControlMode.TRANSPARENCY -> 1
            NoiseControlMode.OFF -> 2
            NoiseControlMode.UNKNOWN -> 2
        }
    }
}

internal fun miLinkAncModeFor(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    hostSpec: MiLinkAncHostSpec,
): Int? = miLinkAncModeFor(route, huaweiStatus, hostSpec.displayValueOrder)

/**
 * 小米的虚拟耳机模板始终读取一个 ANC 状态，即使当前机型没有 ANC。
 * 无 ANC 机型只向宿主返回“关闭”，写操作仍由能力检查拒绝。
 */
internal fun miLinkHostAncStateFor(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    valueOrder: MiLinkAncValueOrder = MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
): Int = miLinkAncModeFor(route, huaweiStatus, valueOrder)
    ?: when (valueOrder) {
        MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY -> 0
        MiLinkAncValueOrder.NOISE_TRANSPARENCY_OFF -> 2
    }

internal fun miLinkHostAncStateFor(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    hostSpec: MiLinkAncHostSpec,
): Int = miLinkHostAncStateFor(route, huaweiStatus, hostSpec.displayValueOrder)

/** legacy 卡片没有公开刷新方法，只能按当前模式定位其原生按钮。 */
internal fun miLinkAncModeLabels(hostState: Int): Set<String> = when (hostState) {
    0 -> setOf("关闭", "off")
    1 -> setOf("降噪", "noise cancellation")
    2 -> setOf("通透", "环境声", "transparency", "ambient sound")
    else -> emptySet()
}

/**
 * 旧版融合中心沿用耳机 AAM 的 0=关闭、1=头部跟踪、2=固定；HyperOS 4
 * 卡片改为 0=关闭、1=固定、2=头部跟踪。不同宿主版本还可能附加 20/30 偏移。
 */
internal fun freeClip2SpatialModeForMiLinkAudioEffect(
    value: Int,
    valueOrder: MiLinkSpatialAudioValueOrder = MiLinkSpatialAudioValueOrder.FIXED_FIRST,
): FreeClip2SpatialAudioMode? {
    val normalized = when (value) {
        in 20..22 -> value - 20
        in 30..32 -> value - 30
        else -> value
    }
    return when (normalized) {
        0 -> FreeClip2SpatialAudioMode.OFF
        1 -> when (valueOrder) {
            MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST ->
                FreeClip2SpatialAudioMode.HEAD_TRACKING
            MiLinkSpatialAudioValueOrder.FIXED_FIRST -> FreeClip2SpatialAudioMode.FIXED
        }
        2 -> when (valueOrder) {
            MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST -> FreeClip2SpatialAudioMode.FIXED
            MiLinkSpatialAudioValueOrder.FIXED_FIRST ->
                FreeClip2SpatialAudioMode.HEAD_TRACKING
        }
        else -> null
    }
}

/** 将模块状态转换为融合设备中心自己的显示/回调枚举。 */
internal fun miLinkAudioEffectForFreeClip2SpatialMode(
    mode: FreeClip2SpatialAudioMode,
    valueOrder: MiLinkSpatialAudioValueOrder = MiLinkSpatialAudioValueOrder.FIXED_FIRST,
): Int =
    when (mode) {
        FreeClip2SpatialAudioMode.OFF -> 0
        FreeClip2SpatialAudioMode.FIXED -> when (valueOrder) {
            MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST -> 2
            MiLinkSpatialAudioValueOrder.FIXED_FIRST -> 1
        }
        FreeClip2SpatialAudioMode.HEAD_TRACKING -> when (valueOrder) {
            MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST -> 1
            MiLinkSpatialAudioValueOrder.FIXED_FIRST -> 2
        }
    }

internal fun freeClip2SpatialModeForMiLinkAudioEffect(
    value: Int,
    hostSpec: MiLinkAudioEffectHostSpec,
): FreeClip2SpatialAudioMode? =
    freeClip2SpatialModeForMiLinkAudioEffect(value, hostSpec.valueOrder)

internal fun miLinkAudioEffectForFreeClip2SpatialMode(
    mode: FreeClip2SpatialAudioMode,
    hostSpec: MiLinkAudioEffectHostSpec,
): Int = miLinkAudioEffectForFreeClip2SpatialMode(mode, hostSpec.valueOrder)

/** 旧版详情是固定高度卡片；FreeClip 2 会复用被隐藏 ANC 区域的高度展示音效。 */
internal fun shouldReserveLegacyMiLinkAncHeight(
    route: HuaweiDeviceRoute,
    hostSpec: MiLinkAncHostSpec,
): Boolean = route == HuaweiDeviceRoute.HUAWEI_FREECLIP2 &&
    hostSpec.adapterName == "legacy"

/** 只接受当前机型确实支持的融合设备中心状态。 */
internal fun huaweiAncStatusForMiLink(
    route: HuaweiDeviceRoute,
    miLinkMode: Int,
    valueOrder: MiLinkAncValueOrder = MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
): Int? {
    if (!route.supportsAnc) return null
    val mode = when (valueOrder) {
        MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY -> when (miLinkMode) {
            0 -> NoiseControlMode.OFF
            1 -> NoiseControlMode.NOISE_CANCELLATION
            2 -> NoiseControlMode.TRANSPARENCY
            else -> null
        }
        MiLinkAncValueOrder.NOISE_TRANSPARENCY_OFF -> when (miLinkMode) {
            0 -> NoiseControlMode.NOISE_CANCELLATION
            1 -> NoiseControlMode.TRANSPARENCY
            2 -> NoiseControlMode.OFF
            else -> null
        }
    }
    return when (mode) {
        NoiseControlMode.OFF -> NoiseControlMode.OFF.broadcastStatus
        NoiseControlMode.NOISE_CANCELLATION -> NoiseControlMode.NOISE_CANCELLATION.broadcastStatus
        NoiseControlMode.TRANSPARENCY ->
            NoiseControlMode.TRANSPARENCY.broadcastStatus.takeIf { route.supportsTransparency }
        else -> null
    }
}

internal fun huaweiAncStatusForMiLink(
    route: HuaweiDeviceRoute,
    miLinkMode: Int,
    hostSpec: MiLinkAncHostSpec,
): Int? = huaweiAncStatusForMiLink(route, miLinkMode, hostSpec.displayValueOrder)

/** 两态 ANC 机型必须摘除宿主通透按钮，避免异步绑定再次把它设为可见。 */
internal fun shouldDetachMiLinkTransparency(route: HuaweiDeviceRoute): Boolean =
    route.supportsAnc && !route.supportsTransparency

/**
 * HyperOS 4 的两态 ANC 卡仍按三态索引回调，移除“通透”后两个可见按钮可能都会落到索引 0。
 * 两态机型改为按按钮自身文案决定华为协议状态，不再依赖宿主子 View 的当前位置。
 */
internal fun miLinkTwoStateAncStatusForLabel(label: String?): Int? =
    when (label?.trim()?.lowercase()) {
        "降噪", "noise cancellation", "noise reduction" ->
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus
        "关闭", "off" -> NoiseControlMode.OFF.broadcastStatus
        else -> null
    }

/**
 * 宿主可能在设备切换时复用同一组 ANC 按钮。两态机型安装的文案监听若仍存在，
 * 在三态 ANC 机型上也应继续处理“降噪 / 关闭”，不能让按钮变成无响应。
 */
internal fun miLinkBoundAncStatusForRoute(
    route: HuaweiDeviceRoute,
    label: String?,
): Int? = miLinkTwoStateAncStatusForLabel(label).takeIf { route.supportsAnc }

internal fun shouldRemoveMiLinkCapabilityView(
    detachWhenHidden: Boolean,
    parentAvailable: Boolean,
    stillInParent: Boolean,
): Boolean = detachWhenHidden && parentAvailable && stillInParent

internal inline fun <T> withMiLinkAncUiSync(
    depth: AtomicInteger,
    block: () -> T,
): T {
    depth.incrementAndGet()
    return try {
        block()
    } finally {
        depth.decrementAndGet()
    }
}

internal fun shouldReapplyMiLinkHeadsetIcon(
    requestedKey: String?,
    cachedKey: String?,
    alreadyApplied: Boolean,
): Boolean = !requestedKey.isNullOrBlank() &&
    requestedKey == cachedKey &&
    !alreadyApplied

/** 原位替换宿主槽位时必须保留固定高度；追加独立卡片才允许按内容测量。 */
internal fun miLinkSoundEffectCardHeight(
    sourceHeight: Int,
    replacesHostSlot: Boolean,
): Int = if (replacesHostSlot) sourceHeight else ViewGroup.LayoutParams.WRAP_CONTENT

/** 私有卡片拿不到 MAC 时，只允许唯一 ANC 卡片继承当前活动耳机身份。 */
internal fun shouldUseActiveMiLinkAncCardFallback(
    activeRoute: HuaweiDeviceRoute,
    activeAddress: String?,
    sessionConfirmed: Boolean,
    candidateAddressCount: Int,
    liveAncCardCount: Int,
): Boolean = activeRoute.supportsAnc &&
    !activeAddress.isNullOrBlank() &&
    sessionConfirmed &&
    candidateAddressCount == 0 &&
    liveAncCardCount == 1

/** 图片只影响展示；当前会话唯一时允许提前使用活动机型，避免宿主默认图闪过一帧。 */
internal fun shouldUseActiveMiLinkIconFallback(
    activeRoute: HuaweiDeviceRoute,
    activeAddress: String?,
    sessionConfirmed: Boolean,
    liveHeadsetDetailCount: Int,
): Boolean = activeRoute.isSupported &&
    !activeAddress.isNullOrBlank() &&
    sessionConfirmed &&
    liveHeadsetDetailCount == 1

/** 宿主刷新已绑定的详情时优先采用明确身份，冷启动阶段才退回唯一活动会话。 */
internal fun immediateMiLinkHeadsetIconRoute(
    strictRoute: HuaweiDeviceRoute,
    activeRoute: HuaweiDeviceRoute,
    activeAddress: String?,
    sessionConfirmed: Boolean,
    liveHeadsetDetailCount: Int,
): HuaweiDeviceRoute = refreshedMiLinkHeadsetIconRoute(
    strictRoute = strictRoute,
    labelRoute = HuaweiDeviceRoute.UNSUPPORTED,
    activeRoute = activeRoute,
    activeAddress = activeAddress,
    sessionConfirmed = sessionConfirmed,
    liveHeadsetDetailCount = liveHeadsetDetailCount,
)

/**
 * 宿主投递到下一帧的刷新可能暂时拿不到详情绑定和标题，此时继续沿用唯一已确认会话，
 * 避免把刚写入的机型图还原成宿主默认图。
 */
internal fun refreshedMiLinkHeadsetIconRoute(
    strictRoute: HuaweiDeviceRoute,
    labelRoute: HuaweiDeviceRoute,
    activeRoute: HuaweiDeviceRoute,
    activeAddress: String?,
    sessionConfirmed: Boolean,
    liveHeadsetDetailCount: Int,
): HuaweiDeviceRoute = strictRoute.takeIf(HuaweiDeviceRoute::isSupported)
    ?: labelRoute.takeIf(HuaweiDeviceRoute::isSupported)
    ?: activeRoute.takeIf {
        shouldUseActiveMiLinkIconFallback(
            activeRoute = activeRoute,
            activeAddress = activeAddress,
            sessionConfirmed = sessionConfirmed,
            liveHeadsetDetailCount = liveHeadsetDetailCount,
        )
    }
    ?: HuaweiDeviceRoute.UNSUPPORTED

/**
 * 双设备被另一台终端占用时，融合中心会暂时清掉活动会话/MAC，但详情页标题仍保留机型。
 * 标题只能用于无 ANC 机型的展示裁剪，绝不能据此启用会向耳机下发命令的 ANC 控件。
 */
internal fun noAncMiLinkPresentationRoute(labels: Iterable<CharSequence?>): HuaweiDeviceRoute {
    val routes = labels
        .map { label -> detectHuaweiDeviceRoute(label?.toString()) }
        .filter(HuaweiDeviceRoute::isSupported)
        .distinct()
    return routes.singleOrNull()
        ?.takeIf { route -> !route.supportsAnc }
        ?: HuaweiDeviceRoute.UNSUPPORTED
}

/** 与耳机控制器共用子模式校验；Pro 5 的通透子模式由融合设备中心保留。 */
internal fun normalizeMiLinkAncSubMode(
    route: HuaweiDeviceRoute,
    huaweiStatus: Int,
    requestedSubMode: Int?,
    storedSubMode: Int?,
): Int? {
    val mode = NoiseControlMode.fromBroadcastStatus(huaweiStatus)
    if (!route.supportsAnc || mode == NoiseControlMode.UNKNOWN) return null
    if (mode == NoiseControlMode.TRANSPARENCY && !route.supportsTransparency) return null
    if (
        mode == NoiseControlMode.NOISE_CANCELLATION &&
        route.supportsDiscreteAncLevels &&
        requestedSubMode != null &&
        !route.supportsAncSubMode(requestedSubMode)
    ) {
        return null
    }
    return normalizeHuaweiAncSubMode(
        route = route,
        mode = mode,
        requestedSubMode = requestedSubMode,
        previousState = HuaweiAncState(mode, storedSubMode),
    )
}

internal fun miLinkVolumePercentForProgress(
    progress: Int,
    minimum: Int,
    maximum: Int,
): Int? {
    if (maximum <= minimum) return null
    val bounded = progress.coerceIn(minimum, maximum)
    return ((bounded - minimum).toDouble() * 100.0 / (maximum - minimum))
        .roundToInt()
        .coerceIn(0, 100)
}

@SuppressLint("MissingPermission")
object MiLinkServiceHook : HookContext() {
    internal const val TAG = "HuaweiPods-MiLink"
    private const val PREFS_NAME = "huaweipods_milink_state"
    private const val PREF_WINDOWS_HOST_IDS = "windows_host_ids"
    private const val CIRCULATE_STATE_DISCONNECTED = 0
    private const val CIRCULATE_STATE_CONNECTED = 2
    private const val CIRCULATE_STATE_CONNECTING = 3
    private const val CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED = 2011
    private const val CIRCULATE_SERVICE_HEADSET_PRIMARY = 393216
    private const val CIRCULATE_SERVICE_HEADSET_FALLBACK = 524288
    private const val HEADSET_BOND_BONDED = 306
    private const val ANC_SUBMODE_SELECTOR_TAG = "huaweipods_milink_anc_submode"
    private const val FREECLIP2_SOUND_EFFECT_CONTROLS_TAG =
        "huaweipods_milink_freeclip2_sound_effect"
    private const val HUAWEI_EQUALIZER_CONTROLS_TAG =
        "huaweipods_milink_huawei_equalizer"
    private const val PREF_ANC_SUBMODE = "anc_submode"
    private const val PREF_ANC_SUBMODE_LEGACY_6I = "anc_level"
    private const val PREF_ANC_SUBMODE_LEGACY_PRO3 = "huawei_anc_level"
    private const val PREF_FREECLIP2_SPATIAL_MODE = "freeclip2_spatial_mode"
    private const val PREF_FREECLIP2_SPATIAL_SCENE = "freeclip2_spatial_scene"
    private const val PREF_FREECLIP2_SOUND_EFFECT = "freeclip2_sound_effect"
    private const val PREF_FREECLIP2_EQ_SELECTED_ID = "freeclip2_eq_selected_id"
    private const val PREF_FREECLIP2_CUSTOM_EQ_NAME_PREFIX = "freeclip2_custom_eq_name_"
    private const val PREF_FREECLIP2_CUSTOM_EQ_GAINS_PREFIX = "freeclip2_custom_eq_gains_"
    private const val PREF_HUAWEI_EQUALIZER_SELECTED_ID = "huawei_equalizer_selected_id"
    private const val PREF_TRANSPARENCY_SUBMODE = "transparency_submode"
    private const val PREF_DEVICE_ROUTE = "device_route"
    private const val FREECLIP2_AUDIO_REFRESH_MIN_INTERVAL_MS = 750L
    private const val FREECLIP2_CUSTOM_EQ_CONFIRM_DELAY_MS = 750L
    private const val HUAWEI_EQUALIZER_REFRESH_MIN_INTERVAL_MS = 750L
    private const val HUAWEI_ANC_REFRESH_MIN_INTERVAL_MS = 750L
    private const val VISIBLE_ANC_REFRESH_INTERVAL_MS = 2_500L
    private const val MILINK_HEADSET_ICON_MAX_DIMENSION = 512
    private const val HOT_RELOAD_THREAD_DRAIN_TIMEOUT_MS = 2_000L
    private const val HOT_RELOAD_PROFILE_DRAIN_TIMEOUT_MS = 1_500L
    private const val HOT_RELOAD_MAIN_THREAD_TIMEOUT_MS = 3_000L
    private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    private val freeBuds6iBuiltInSoundEffectIds = setOf(0x01, 0x02, 0x03, 0x09)
    private val bluetoothAddressPattern = Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$")
    private val ancIdentityGetterNames = listOf(
        "getAddress",
        "component1",
        "getName",
        "getDeviceName",
        "getAlias",
        "getServiceId",
        "getDeviceId",
        "getBluetoothDevice",
        "getDevice",
        "getHeadsetInfo",
        "getServiceInfo",
        "getIntent",
    )
    private val ancIdentityGetterNameSet = ancIdentityGetterNames.toSet()
    private val ancIdentityGetterMethods = ConcurrentHashMap<Class<*>, List<Method>>()
    private val knownHuaweiRoutes = ConcurrentHashMap<String, HuaweiDeviceRoute>()
    private val knownWindowsHostIds = linkedSetOf<String>()
    private val ancCards = Collections.synchronizedMap(WeakHashMap<Any, AncCardBinding>())
    private val headsetDetails = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val visibleAncRefreshScheduled = AtomicBoolean(false)
    private val visibleAncRefreshRunnable = object : Runnable {
        override fun run() {
            val visibleDetailCount = synchronized(headsetDetails) {
                headsetDetails.keys.count { detail ->
                    detail.isAttachedToWindow &&
                        detail.isShown &&
                        detail.windowVisibility == View.VISIBLE
                }
            }
            val route = currentHuaweiRoute()
            if (!shouldPollVisibleMiLinkAnc(route, visibleDetailCount)) {
                visibleAncRefreshScheduled.set(false)
                return
            }
            requestMiLinkAncState("visible-detail")
            mainHandler.postDelayed(this, VISIBLE_ANC_REFRESH_INTERVAL_MS)
        }
    }
    private val miAudioEffectSections = Collections.synchronizedMap(
        WeakHashMap<Any, MiAudioEffectBinding>(),
    )
    private val hiddenCapabilityViews = Collections.synchronizedMap(
        WeakHashMap<View, HiddenCapabilityView>(),
    )
    private val originalHostModeVisibility = Collections.synchronizedMap(
        WeakHashMap<View, Boolean>(),
    )
    private val freeClip2OriginalLabels = Collections.synchronizedMap(
        WeakHashMap<TextView, CharSequence>(),
    )
    private val freeClip2OriginalOptionOrders = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, FreeClip2OptionOrderState>(),
    )
    private val freeClip2AudioHeadingRoots = Collections.synchronizedMap(
        WeakHashMap<View, Boolean>(),
    )
    private val freeClip2SoundEffectAnchors = Collections.synchronizedMap(
        WeakHashMap<View, Boolean>(),
    )
    private val miLinkHeadsetIconViewStates = Collections.synchronizedMap(
        WeakHashMap<ImageView, MiLinkHeadsetIconViewState>(),
    )
    private val miLinkVolumeLabels = Collections.synchronizedMap(
        WeakHashMap<View, WeakReference<TextView>>(),
    )
    private val miLinkVolumeOriginalLabels = Collections.synchronizedMap(
        WeakHashMap<TextView, CharSequence>(),
    )
    private val miLinkVolumeProgressBindings = Collections.synchronizedMap(
        WeakHashMap<ProgressBar, MiLinkVolumeProgressBinding>(),
    )
    private val miLinkHeadsetIconLoads = ConcurrentHashMap.newKeySet<String>()
    private val miLinkHeadsetIconExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HuaweiPods-MiLinkIcon").apply { isDaemon = true }
    }
    @Volatile
    private var miLinkHeadsetIconBitmapCache: MiLinkHeadsetIconBitmapCache? = null
    @Volatile
    private var miLinkHeadsetIconRequestCache: MiLinkHeadsetIconRequest? = null
    @Volatile
    private var activeMiLinkAncHostSpec: MiLinkAncHostSpec? = null
    private val miLinkHeadsetIconInternalRenderDepth = AtomicInteger(0)
    private val stateLoadLock = Any()
    @Volatile
    private var stateLoaded = false
    internal var context: Context? = null
    private var receiverRegistered = false
    private var statusReceiver: BroadcastReceiver? = null
    internal var currentAddress: String? = null
    private var currentName: String? = null
    private var currentRoute: HuaweiDeviceRoute = HuaweiDeviceRoute.UNSUPPORTED
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1
    private var currentAncSubMode: Int? = null
    private var currentTransparencySubMode: Int? = null
    private var currentFreeClip2SpatialMode = FreeClip2SpatialAudioMode.OFF
    private var currentFreeClip2SpatialScene = FreeClip2SpatialScene.DEFAULT
    private var currentFreeClip2SoundEffect = FreeClip2SoundEffect.DEFAULT
    private var currentFreeClip2EqualizerSelectedId: Int? = null
    private var currentFreeClip2CustomPresets: List<HuaweiEqualizerPreset> = emptyList()
    private var pendingFreeClip2CustomEqualizerId: Int? = null
    private var currentHuaweiEqualizerSelectedId: Int? = null
    private var currentLowLatencyEnabled = false
    private var lowLatencyCardIcon: Drawable? = null
    private val ancPendingGate = MiLinkAncPendingGate()
    private val freeClip2AudioPendingGate = FreeClip2AudioPendingGate()
    private var lastFreeClip2AudioRefreshRequestAt = 0L
    private var lastHuaweiEqualizerRefreshRequestAt = 0L
    private var lastHuaweiAncRefreshRequestAt = 0L
    private val freeClip2AudioInternalRenderDepth = AtomicInteger(0)
    private val ancInternalUiSyncDepth = AtomicInteger(0)
    private var currentSessionConfirmed = false
    internal var lastAncBatteryController: Any? = null
    internal var lastProfileContext: Any? = null
    private var circulationSignalRewriteUntilMs = 0L
    private var circulationUiCompletedUntilMs = 0L
    private var circulationTargetHostId: String? = null
    private var lastHeadsetServiceClient: Any? = null
    private var lastHeadsetDeviceInfo: Any? = null
    private var lastHeadsetServiceInfo: Any? = null
    private val localBluetoothConnectBurstToken = AtomicInteger(0)
    private val runtimeGeneration = AtomicInteger(0)
    private val runtimeLifecycleLock = Any()
    private val managedWorkerThreads = Collections.newSetFromMap(
        IdentityHashMap<Thread, Boolean>(),
    )
    private val profileProxyRequests = ConcurrentHashMap.newKeySet<MiLinkProfileProxyRequest>()
    private val boundAncButtonListeners = Collections.synchronizedMap(
        WeakHashMap<View, MiLinkBoundClickState>(),
    )
    private val pendingViewCallbacks = Collections.synchronizedMap(
        WeakHashMap<View, MutableSet<Runnable>>(),
    )
    @Volatile
    private var acceptingViewCallbacks = false

    override fun onHook() {
        acceptingViewCallbacks = true
        hookMiLinkMediaVolumeChanges()
        hookMiLinkVolumeProgressChanges()
        hookMiLinkHeadsetIconWrites()
        hookContextEntry()
        hookMxBluetoothRuntime()
        hookMiLinkAudioGlassesClassification()
        hookHeadsetRuntimeDisplay()
        hookHeadsetCirculationExperiment()
        hookWindowsHeadsetCirculationCapability()
        hookWindowsHeadsetBondState()
        hookCirculatePlusHeadsetAncCard()
        hookCirculatePlusFreeClip2AudioEffectApi()
        hookCirculatePlusFreeClip2AudioEffectCard()
        hookLowLatencyQuickCard()
        currentApplicationOrNull()?.let(::registerStatusReceiver)
        recreateVisibleMiLinkActivities()
    }

    override fun onCanClose(): Boolean {
        val hasWorkers = synchronized(runtimeLifecycleLock) {
            managedWorkerThreads.any(Thread::isAlive)
        }
        val hasPendingProfiles = profileProxyRequests.any { !it.completed.get() }
        return !hasWorkers &&
            !hasPendingProfiles &&
            miLinkHeadsetIconLoads.isEmpty() &&
            runOnMainThreadBlocking { }
    }

    override fun onSaveHotReloadState(outState: Bundle) {
        outState.putString("address", currentAddress)
        outState.putString("name", currentName)
        outState.putString("route", currentRoute.name)
        outState.putInt("anc", currentAnc)
        currentAncSubMode?.let { outState.putInt("anc_submode", it) }
        currentTransparencySubMode?.let { outState.putInt("transparency_submode", it) }
        outState.putString("spatial_mode", currentFreeClip2SpatialMode.name)
        outState.putString("spatial_scene", currentFreeClip2SpatialScene.name)
        outState.putString("sound_effect", currentFreeClip2SoundEffect.name)
        currentFreeClip2EqualizerSelectedId?.let { outState.putInt("freeclip2_eq", it) }
        currentHuaweiEqualizerSelectedId?.let { outState.putInt("huawei_eq", it) }
        outState.putBoolean("low_latency", currentLowLatencyEnabled)
        outState.putBoolean("session_confirmed", currentSessionConfirmed)
        outState.putStringArrayList(
            "known_routes",
            ArrayList(knownHuaweiRoutes.map { (address, route) -> "$address=${route.name}" }),
        )
    }

    override fun onRestoreHotReloadState(savedState: Bundle) {
        currentAddress = savedState.getString("address")
        currentName = savedState.getString("name")
        currentRoute = enumValueOrDefault(savedState.getString("route"), HuaweiDeviceRoute.UNSUPPORTED)
        currentAnc = savedState.getInt("anc", NoiseControlMode.OFF.broadcastStatus)
        currentAncSubMode = savedState.getInt("anc_submode", -1).takeIf { it >= 0 }
        currentTransparencySubMode = savedState.getInt("transparency_submode", -1)
            .takeIf { it >= 0 }
        currentFreeClip2SpatialMode = enumValueOrDefault(
            savedState.getString("spatial_mode"),
            FreeClip2SpatialAudioMode.OFF,
        )
        currentFreeClip2SpatialScene = enumValueOrDefault(
            savedState.getString("spatial_scene"),
            FreeClip2SpatialScene.DEFAULT,
        )
        currentFreeClip2SoundEffect = enumValueOrDefault(
            savedState.getString("sound_effect"),
            FreeClip2SoundEffect.DEFAULT,
        )
        currentFreeClip2EqualizerSelectedId = savedState.getInt("freeclip2_eq", -1)
            .takeIf { it >= 0 }
        currentHuaweiEqualizerSelectedId = savedState.getInt("huawei_eq", -1)
            .takeIf { it >= 0 }
        currentLowLatencyEnabled = savedState.getBoolean("low_latency", false)
        currentSessionConfirmed = savedState.getBoolean("session_confirmed", false)
        knownHuaweiRoutes.clear()
        savedState.getStringArrayList("known_routes").orEmpty().forEach { encoded ->
            val separator = encoded.indexOf('=')
            if (separator <= 0 || separator == encoded.lastIndex) return@forEach
            val address = encoded.substring(0, separator)
            val route = enumValueOrDefault(
                encoded.substring(separator + 1),
                HuaweiDeviceRoute.UNSUPPORTED,
            )
            if (route.isSupported) knownHuaweiRoutes[address] = route
        }
        requestMiLinkAncState("hot-reload-restored")
        requestFreeClip2AudioState("hot-reload-restored")
        requestHuaweiEqualizerState("hot-reload-restored", force = true)
        refreshLowLatencyQuickCard()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    override fun onClose() {
        val cleanupFailures = mutableListOf<Throwable>()
        acceptingViewCallbacks = false
        runtimeGeneration.incrementAndGet()
        localBluetoothConnectBurstToken.incrementAndGet()
        visibleAncRefreshScheduled.set(false)
        mainHandler.removeCallbacksAndMessages(null)

        deactivateProfileProxyRequests()
        val aliveWorkers = drainManagedWorkerThreads()
        if (aliveWorkers.isNotEmpty()) {
            cleanupFailures += IllegalStateException(
                "MiLink workers did not stop: ${aliveWorkers.joinToString()}",
            )
        }

        miLinkHeadsetIconExecutor.shutdownNow()
        val iconExecutorStopped = runCatching {
            miLinkHeadsetIconExecutor.awaitTermination(
                HOT_RELOAD_THREAD_DRAIN_TIMEOUT_MS,
                TimeUnit.MILLISECONDS,
            )
        }.onFailure(cleanupFailures::add).getOrDefault(false)
        if (!iconExecutorStopped) {
            cleanupFailures += IllegalStateException("MiLink icon executor did not stop")
        }

        // Worker 可能恰好在关闭边界登记了 profile 请求；再失效一次并等待系统解绑回调。
        deactivateProfileProxyRequests()
        val pendingProfiles = drainProfileProxyRequests()
        if (pendingProfiles.isNotEmpty()) {
            cleanupFailures += IllegalStateException(
                "MiLink profile callbacks still registered: ${pendingProfiles.joinToString()}",
            )
        }

        val receiver = statusReceiver
        val receiverContext = context
        if (receiver != null && receiverContext != null) {
            runCatching { receiverContext.unregisterReceiver(receiver) }
                .onFailure { error ->
                    if (error !is IllegalArgumentException) {
                        Log.w(TAG, "Failed to unregister MiLink status receiver", error)
                    }
                }
        }
        statusReceiver = null
        receiverRegistered = false

        runCatching { runOnMainThreadSync(::restoreMiLinkUiForHotReload) }
            .onFailure(cleanupFailures::add)
        miLinkHeadsetIconBitmapCache = null
        miLinkHeadsetIconRequestCache = null
        miLinkHeadsetIconLoads.clear()

        ancCards.clear()
        headsetDetails.clear()
        miAudioEffectSections.clear()
        freeClip2OriginalOptionOrders.clear()
        freeClip2AudioHeadingRoots.clear()
        freeClip2SoundEffectAnchors.clear()
        miLinkVolumeLabels.clear()
        miLinkVolumeProgressBindings.clear()
        knownHuaweiRoutes.clear()
        knownWindowsHostIds.clear()
        ancIdentityGetterMethods.clear()
        lastAncBatteryController = null
        lastProfileContext = null
        lastHeadsetServiceClient = null
        lastHeadsetDeviceInfo = null
        lastHeadsetServiceInfo = null
        lowLatencyCardIcon = null
        context = null
        currentAddress = null
        currentName = null
        currentSessionConfirmed = false

        if (cleanupFailures.isNotEmpty()) {
            throw IllegalStateException(
                "MiLink hot-reload cleanup incomplete (${cleanupFailures.size})",
            ).also { failure -> cleanupFailures.forEach(failure::addSuppressed) }
        }
    }

    private fun restoreMiLinkUiForHotReload() {
        synchronized(pendingViewCallbacks) {
            pendingViewCallbacks.entries.toList().forEach { (view, tasks) ->
                tasks.toList().forEach(view::removeCallbacks)
            }
            pendingViewCallbacks.clear()
        }

        synchronized(ancCards) {
            ancCards.entries.toList().forEach { (card, binding) ->
                runCatching {
                    restoreAncCardViews(card, binding, binding.clearView?.get(), "hot-reload")
                }.onFailure { Log.w(TAG, "MiLink ANC card cleanup failed", it) }
            }
        }

        val roots = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        synchronized(headsetDetails) { roots.addAll(headsetDetails.keys) }
        synchronized(ancCards) {
            ancCards.values.mapNotNull { it.detail.get() as? View }.forEach(roots::add)
        }
        synchronized(miAudioEffectSections) {
            miAudioEffectSections.values.mapNotNull { it.detail.get() as? View }.forEach(roots::add)
        }
        synchronized(miLinkVolumeLabels) { roots.addAll(miLinkVolumeLabels.keys) }
        roots.forEach { root ->
            runCatching { restoreFreeClip2CardPresentation(root) }
            runCatching { restoreHuaweiEqualizerControls(root) }
            runCatching { unbindMiLinkVolumeProgress(root) }
            findTaggedView(root, ANC_SUBMODE_SELECTOR_TAG)?.let { selector ->
                (selector.parent as? ViewGroup)?.removeView(selector)
            }
        }

        synchronized(originalHostModeVisibility) {
            originalHostModeVisibility.entries.toList().forEach { (detail, visible) ->
                runCatching { writeHostModeVisible(detail, visible) }
            }
            originalHostModeVisibility.clear()
        }
        synchronized(hiddenCapabilityViews) {
            hiddenCapabilityViews.keys.toList().forEach(::restoreCapabilityView)
            hiddenCapabilityViews.clear()
        }
        synchronized(freeClip2OriginalLabels) {
            freeClip2OriginalLabels.entries.toList().forEach { (label, original) ->
                label.text = original
            }
            freeClip2OriginalLabels.clear()
        }
        synchronized(miLinkVolumeOriginalLabels) {
            miLinkVolumeOriginalLabels.entries.toList().forEach { (label, original) ->
                label.text = original
            }
            miLinkVolumeOriginalLabels.clear()
        }
        synchronized(miLinkHeadsetIconViewStates) {
            miLinkHeadsetIconViewStates.keys.toList().forEach(::restoreMiLinkHeadsetIcon)
            miLinkHeadsetIconViewStates.clear()
        }
        synchronized(boundAncButtonListeners) {
            boundAncButtonListeners.entries.toList().forEach { (button, state) ->
                button.setOnClickListener(state.listener)
                button.isClickable = state.clickable
            }
            boundAncButtonListeners.clear()
        }
    }

    /** API 102 不重放页面构建回调；重建当前 MiLink Activity 让新代重新绑定控件。 */
    private fun recreateVisibleMiLinkActivities() {
        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread")
                .apply { isAccessible = true }
                .invoke(null) ?: return@runCatching
            val records = getObjectField(activityThread, "mActivities") as? Map<*, *>
                ?: return@runCatching
            records.values.mapNotNull { record -> getObjectField(record, "activity") }
                .filter { activity -> activity.javaClass.name.startsWith("com.milink.") }
                .forEach { activity ->
                    mainHandler.post {
                        runCatching { callMethod(activity, "recreate") }
                            .onFailure { Log.w(TAG, "Unable to recreate MiLink activity", it) }
                    }
                }
        }.onFailure { Log.w(TAG, "Unable to inspect active MiLink activities", it) }
    }

    private fun runOnMainThreadSync(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val completion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val task = Runnable {
            try {
                block()
            } catch (throwable: Throwable) {
                failure.set(throwable)
            } finally {
                completion.countDown()
            }
        }
        check(mainHandler.post(task)) { "Unable to schedule MiLink UI cleanup" }
        val completed = try {
            completion.await(HOT_RELOAD_MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            mainHandler.removeCallbacks(task)
            throw IllegalStateException("Interrupted while waiting for MiLink UI cleanup", interrupted)
        }
        if (!completed) {
            mainHandler.removeCallbacks(task)
            throw IllegalStateException("Timed out waiting for MiLink UI cleanup")
        }
        failure.get()?.let { throw it }
    }

    private fun drainManagedWorkerThreads(): List<String> {
        val workers = synchronized(runtimeLifecycleLock) {
            managedWorkerThreads.toList()
        }
        workers.filter { it !== Thread.currentThread() }.forEach(Thread::interrupt)
        val deadline = SystemClock.elapsedRealtime() + HOT_RELOAD_THREAD_DRAIN_TIMEOUT_MS
        workers.filter { it !== Thread.currentThread() }.forEach { worker ->
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) return@forEach
            try {
                worker.join(remainingMs)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return@forEach
            }
        }
        return workers.filter(Thread::isAlive).map { it.name }
    }

    private fun deactivateProfileProxyRequests() {
        profileProxyRequests.toList().forEach { request ->
            request.active.set(false)
            synchronized(request.callbackLock) {
                closeProfileProxyOnce(request, request.proxy)
            }
        }
    }

    private fun drainProfileProxyRequests(): List<Int> {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val deadline = SystemClock.elapsedRealtime() + HOT_RELOAD_PROFILE_DRAIN_TIMEOUT_MS
            profileProxyRequests.toList().forEach { request ->
                val remainingMs = deadline - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) return@forEach
                try {
                    request.completion.await(remainingMs, TimeUnit.MILLISECONDS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@forEach
                }
            }
        }
        return profileProxyRequests.filterNot { it.completed.get() }.map { it.profile }
    }

    private fun closeProfileProxyOnce(
        request: MiLinkProfileProxyRequest,
        proxy: BluetoothProfile?,
    ) {
        if (proxy == null || !request.proxyClosed.compareAndSet(false, true)) return
        runCatching { request.adapter.closeProfileProxy(request.profile, proxy) }
            .onFailure {
                Log.w(TAG, "MiLink profile proxy close failed profile=${request.profile}", it)
            }
    }

    private fun completeProfileProxyRequest(
        request: MiLinkProfileProxyRequest,
        proxy: BluetoothProfile? = request.proxy,
    ) {
        synchronized(request.callbackLock) {
            request.proxy = proxy
            closeProfileProxyOnce(request, proxy)
            request.active.set(false)
        }
        if (request.completed.compareAndSet(false, true)) {
            profileProxyRequests.remove(request)
            request.completion.countDown()
        }
    }

    private fun startManagedWorker(name: String, block: (Int) -> Unit): Boolean {
        val generation = runtimeGeneration.get()
        lateinit var worker: Thread
        worker = Thread({
            try {
                if (acceptingViewCallbacks && runtimeGeneration.get() == generation) {
                    block(generation)
                }
            } finally {
                synchronized(runtimeLifecycleLock) {
                    managedWorkerThreads.remove(worker)
                }
            }
        }, name).apply { isDaemon = true }
        synchronized(runtimeLifecycleLock) {
            if (!acceptingViewCallbacks || runtimeGeneration.get() != generation) return false
            managedWorkerThreads.add(worker)
            worker.start()
        }
        return true
    }

    private fun isRuntimeGenerationActive(generation: Int): Boolean =
        acceptingViewCallbacks && runtimeGeneration.get() == generation

    private fun postTracked(view: View, block: () -> Unit) {
        if (!acceptingViewCallbacks) return
        lateinit var task: Runnable
        task = Runnable {
            synchronized(pendingViewCallbacks) {
                pendingViewCallbacks[view]?.let { tasks ->
                    tasks.remove(task)
                    if (tasks.isEmpty()) pendingViewCallbacks.remove(view)
                }
            }
            if (acceptingViewCallbacks) block()
        }
        synchronized(pendingViewCallbacks) {
            if (!acceptingViewCallbacks) return
            pendingViewCallbacks.getOrPut(view, ::linkedSetOf).add(task)
        }
        if (!view.post(task)) {
            synchronized(pendingViewCallbacks) {
                pendingViewCallbacks[view]?.remove(task)
            }
        }
    }

    private fun hookContextEntry() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                hookBefore(findMethod(className, "getInstanceForIsMiTWS", Context::class.java)) {
                    registerStatusReceiver(args[0] as? Context)
                }
            }.onFailure { Log.w(TAG, "hook $className.getInstanceForIsMiTWS skipped", it) }
        }
    }

    private fun hookMxBluetoothRuntime() {
        val classes = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        )
        classes.forEach { className ->
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { fakeDeviceId() }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { 1 }
            hookBluetoothDeviceResult(
                className,
                "getAncState",
                requiresCurrentState = true,
            ) { miLinkAncRuntimeState() }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { "0,0" }
            hookBluetoothDeviceResult(className, "isLeAudio") { false }
            hookAncCommand(className, "openAnc", 2, 1)
            hookAncCommand(className, "closeAnc", 1, 0)
            hookAncCommand(className, "openTransparent", 3, 1, requiresTransparency = true)
        }
        classes.forEach { className ->
            hookStringAddressResult(className, "isMiTWS") { true }
            hookStringAddressResult(className, "isSupportAudioSwitch") { miLinkSwitchState() }
            hookStringAddressResult(className, "getRingFindState") { false }
            hookTransparentFeatureMethods(className)
        }
    }

    private fun hookHeadsetRuntimeDisplay() {
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.ProfileContext",
            "getBatteryLevel",
            requiresCurrentState = true,
        ) { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getAncState",
            requiresCurrentState = true,
        ) { miLinkAncRuntimeState() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getBatteryLevelCache",
            requiresCurrentState = true,
        ) { miLinkBatteryLevels() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getHeadsetPropertyBlock",
            requiresCurrentState = true,
        ) { batteryPercentForMiLink() }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getFindRingState",
            requiresCurrentState = true,
        ) { miLinkLowLatencyState() }
        hookStringAddressResult("com.miui.headset.runtime.AncBatteryController", "getSwitchState") { miLinkSwitchState() }
        hookTransparentFeatureMethods("com.miui.headset.runtime.AncBatteryController")
        hookTransparentFeatureMethods("com.miui.headset.runtime.ProfileContext")
        hookTransparentFeatureMethods("com.miui.headset.api.HeadsetInfo")
        hookAncStateBlock()
        hookHeadsetInfoNoArg("getDeviceId") { fakeDeviceId() }
        hookHeadsetInfoNoArg("component3") { fakeDeviceId() }
        hookHeadsetInfoNoArg("getPowers", requiresCurrentState = true) { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("component4", requiresCurrentState = true) { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("getMode", requiresCurrentState = true) { miLinkAncDisplayState() }
        hookHeadsetInfoNoArg("component5", requiresCurrentState = true) { miLinkAncDisplayState() }
        hookHeadsetInfoNoArg("getSwitchState", requiresCurrentState = true) { miLinkSwitchState() }
        hookHeadsetInfoNoArg("component8", requiresCurrentState = true) { miLinkSwitchState() }
        hookHeadsetInfoNoArg("getFindRingState", requiresCurrentState = true) {
            miLinkLowLatencyState()
        }
        hookHeadsetInfoNoArg("component11", requiresCurrentState = true) {
            miLinkLowLatencyState()
        }
    }

    internal fun hookBluetoothDeviceResult(
        className: String,
        methodName: String,
        requiresAnc: Boolean = false,
        requiresCurrentState: Boolean = false,
        result: () -> Any,
    ) {
        runCatching {
            hookAfter(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                val route = routeForDevice(device)
                if (!route.isSupported || requiresAnc && !route.supportsAnc) return@hookAfter
                if (requiresCurrentState && !isCurrentHuaweiDevice(device, route)) return@hookAfter
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                this.result = result()
                if (className == "com.miui.headset.runtime.AncBatteryController" && methodName == "getHeadsetPropertyBlock") {
                    notifyHeadsetPropertyChanged(instance, device, 4)
                }
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    internal fun hookStringAddressResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookAfter
                if (!isHuaweiAddress(address)) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookAncCommand(
        className: String,
        methodName: String,
        huaweiAnc: Int,
        result: Int,
        requiresTransparency: Boolean = false,
    ) {
        runCatching {
            hookBefore(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val route = routeForDevice(device)
                if (!route.isSupported) return@hookBefore
                if (!isCurrentHuaweiDevice(device, route)) return@hookBefore
                if (!route.supportsAnc || requiresTransparency && !route.supportsTransparency) {
                    this.result = 0
                    return@hookBefore
                }
                if (ancInternalUiSyncDepth.get() > 0) {
                    // legacy 卡片回放原生点击只为更新选中样式，不能再次写入耳机。
                    this.result = result
                    return@hookBefore
                }
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                val selection = selectionForStatus(route, huaweiAnc) ?: run {
                    this.result = 0
                    return@hookBefore
                }
                dispatchAncSelection(selection, context, "$methodName-command")
                this.result = result
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName command skipped", it) }
    }

    private fun hookAncStateBlock() {
        runCatching {
            hookBefore(findMethod("com.miui.headset.runtime.AncBatteryController", "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val route = routeForDevice(device)
                if (!route.isSupported) return@hookBefore
                if (!isCurrentHuaweiDevice(device, route)) return@hookBefore
                if (!route.supportsAnc) {
                    this.result = 0
                    return@hookBefore
                }
                if (ancInternalUiSyncDepth.get() > 0) {
                    this.result = miLinkAncRuntimeState()
                    return@hookBefore
                }
                lastAncBatteryController = instance
                captureRuntimeContext(instance)
                val miLinkMode = args[1] as? Int ?: return@hookBefore
                // Runtime 命令域在 HyperOS 4 仍保持 0=关闭、1=降噪、2=通透；
                // 宿主卡片的 M(int) 显示域另由 displayValueOrder 处理。
                val huaweiStatus = huaweiAncStatusForMiLink(route, miLinkMode)
                val selection = huaweiStatus?.let { selectionForStatus(route, it) }
                if (selection == null) {
                    this.result = 0
                    return@hookBefore
                }
                Log.i(
                    TAG,
                    "MiLink ANC runtime command hostState=$miLinkMode " +
                        "huaweiStatus=$huaweiStatus route=$route",
                )
                val instanceContext = runCatching { getObjectField(instance, "context") as? Context }.getOrNull()
                if (instanceContext != null) {
                    context = instanceContext.applicationContext ?: instanceContext
                }
                dispatchAncSelection(selection, instanceContext, "setAncStateBlock")
                this.result = miLinkAncRuntimeState()
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.setAncStateBlock skipped", it) }
    }

    private fun hookLowLatencyQuickCard() {
        val hostAdapterName = activeMiLinkAncHostSpec?.adapterName
        if (!MiLinkLowLatencyQuickCardPolicy.isHostSupported(hostAdapterName)) {
            Log.i(
                TAG,
                "MiLink low-latency quick card disabled for unverified host adapter=$hostAdapterName",
            )
            return
        }
        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.runtime.AncBatteryController",
                    "setFindRing",
                    BluetoothDevice::class.java,
                    Int::class.javaPrimitiveType!!,
                ),
            ) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val route = routeForDevice(device)
                if (!isCurrentHuaweiDevice(device, route) ||
                    !lowLatencyQuickCardEnabled(route)
                ) {
                    return@hookBefore
                }
                lastAncBatteryController = instance
                captureRuntimeContext(instance)
                val enabled = MiLinkLowLatencyQuickCardPolicy.toggledEnabled(
                    currentLowLatencyEnabled,
                )
                currentLowLatencyEnabled = enabled
                sendHuaweiLowLatency(enabled)
                notifyHeadsetPropertyChanged(instance, device, 10)
                this.result = 100
            }
        }.onFailure {
            Log.w(TAG, "hook AncBatteryController.setFindRing low-latency skipped", it)
        }

        val synergyViewClass = listOf(
            "com.miui.circulate.world.sticker.ui.SynergyView",
            "com.miui.circulate.world.sticker.p067ui.SynergyView",
        ).firstNotNullOfOrNull { className ->
            runCatching { findClass(className) }.getOrNull()
        } ?: return
        runCatching {
            val setTitle = synergyViewClass.getDeclaredMethod(
                "setTitle",
                Int::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            hookBefore(setTitle) {
                val view = instance as? View ?: return@hookBefore
                val titleResId = args[0] as? Int ?: return@hookBefore
                if (viewResourceName(view, view.id) != "mi_audio_ringing_view" ||
                    !lowLatencyQuickCardEnabled()
                ) {
                    return@hookBefore
                }
                val sourceName = viewResourceName(view, titleResId)
                if (sourceName != "circulate_headset_control_audio_find_earphone" &&
                    sourceName != "circulate_headset_control_audio_stop_find_earphone"
                ) {
                    return@hookBefore
                }
                if (!ModuleResourceResolver.isCurrentModuleBuild(view.context)) return@hookBefore
                val moduleResources = ModuleResourceResolver.resources(view.context)
                    ?: return@hookBefore
                setSynergyTitle(view, moduleResources.getString(R.string.low_latency_mode))
                setSynergySubtitle(
                    view,
                    moduleResources.getString(
                        if (currentLowLatencyEnabled) {
                            R.string.low_latency_enabled
                        } else {
                            R.string.low_latency_disabled
                        },
                    ),
                )
                setSynergyIcon(view, loadLowLatencyCardIcon(view))
                this.result = null
            }
        }.onFailure {
            Log.w(TAG, "hook SynergyView.setTitle low-latency skipped", it)
        }
    }

    private fun lowLatencyQuickCardEnabled(
        route: HuaweiDeviceRoute = currentHuaweiRoute(),
    ): Boolean = MiLinkLowLatencyQuickCardPolicy.isAvailable(
        route = route,
        configured = ConfigManager.milinkLowLatencyCardEnabled(),
        hostAdapterName = activeMiLinkAncHostSpec?.adapterName,
    )

    private fun miLinkLowLatencyState(): Int {
        loadState()
        return MiLinkLowLatencyQuickCardPolicy.hostState(
            route = currentHuaweiRoute(),
            configured = ConfigManager.milinkLowLatencyCardEnabled(),
            enabled = currentLowLatencyEnabled,
            hostAdapterName = activeMiLinkAncHostSpec?.adapterName,
        )
    }

    private fun sendHuaweiLowLatency(enabled: Boolean) {
        val ctx = context ?: return
        val address = currentAddress ?: return
        val route = currentHuaweiRoute()
        if (!lowLatencyQuickCardEnabled(route)) return
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_SET).apply {
            putExtra("address", address)
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra(HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED, enabled)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    private fun refreshLowLatencyFromPrefs() {
        val address = currentAddress
        val route = currentHuaweiRoute()
        currentLowLatencyEnabled = if (address != null && route.supportsLowLatencyControl) {
            LowLatencyPrefs.desiredForHook(address, route) ?: false
        } else {
            false
        }
    }

    private fun currentBluetoothDevice(): BluetoothDevice? {
        val ctx = context ?: return null
        val address = currentAddress ?: return null
        return runCatching {
            ctx.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
        }.getOrNull()
    }

    private fun refreshLowLatencyQuickCard() {
        notifyHeadsetPropertyChanged(
            lastAncBatteryController,
            currentBluetoothDevice() ?: return,
            10,
        )
    }

    private fun viewResourceName(view: View, id: Int): String? {
        if (id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(id) }.getOrNull()
    }

    private fun setSynergyTitle(view: View, title: CharSequence) {
        val method = view.javaClass.methods.firstOrNull {
            it.name == "setTitle" &&
                it.parameterTypes.contentEquals(arrayOf(CharSequence::class.java))
        }
        if (method != null) {
            runCatching { method.invoke(view, title) }
            return
        }
        (findChildByName(view, "item_title") as? TextView)?.text = title
    }

    private fun setSynergySubtitle(view: View, text: CharSequence) {
        (findChildByName(view, "item_subtitle") as? TextView)?.apply {
            this.text = text
            visibility = View.VISIBLE
        }
    }

    private fun setSynergyIcon(view: View, drawable: Drawable?) {
        drawable ?: return
        (findChildByName(view, "item_icon") as? ImageView)?.setImageDrawable(drawable)
    }

    private fun findChildByName(view: View, name: String): View? {
        val packageName = runCatching { view.resources.getResourcePackageName(view.id) }
            .getOrDefault(view.context.packageName)
        val id = view.resources.getIdentifier(name, "id", packageName)
        return id.takeIf { it != 0 }?.let(view::findViewById)
    }

    private fun loadLowLatencyCardIcon(view: View): Drawable? {
        lowLatencyCardIcon?.let { return it }
        if (!ModuleResourceResolver.isCurrentModuleBuild(view.context)) return null
        return ModuleResourceResolver.resources(view.context)
            ?.getDrawable(R.drawable.ic_low_latency, null)
            ?.also { lowLatencyCardIcon = it }
    }

    internal fun hookHeadsetInfoNoArg(
        methodName: String,
        requiresAnc: Boolean = false,
        requiresCurrentState: Boolean = false,
        result: () -> Any,
    ) {
        runCatching {
            hookAfter(findMethodByParamCount("com.miui.headset.api.HeadsetInfo", methodName, 0)) {
                val route = routeForHeadsetInfo(instance)
                if (!route.isSupported || requiresAnc && !route.supportsAnc) return@hookAfter
                if (requiresCurrentState && !isCurrentHeadsetInfo(instance, route)) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook HeadsetInfo.$methodName skipped", it) }
    }

    private fun hookTransparentFeatureMethods(className: String) {
        runCatching {
            findClass(className).declaredMethods
                .filter { method ->
                    val name = method.name.lowercase()
                    ("transparent" in name || "transparency" in name) &&
                        method.returnType in listOf(
                            Boolean::class.javaPrimitiveType,
                            java.lang.Boolean::class.java,
                            Int::class.javaPrimitiveType,
                            java.lang.Integer::class.java,
                        )
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        hookAfter(method) {
                            val route = routeForMethodTarget(args, instance)
                            if (!route.isSupported) return@hookAfter
                            val supported = route.supportsTransparency
                            val isCapability = isTransparencyCapabilityMethod(method.name)
                            if (!isCapability && !methodTargetMatchesCurrent(args, instance, route)) return@hookAfter
                            val enabled = if (isCapability) {
                                supported
                            } else {
                                supported && currentAnc == NoiseControlMode.TRANSPARENCY.broadcastStatus
                            }
                            this.result = when (method.returnType) {
                                Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> enabled
                                else -> if (enabled) 1 else 0
                            }
                            Log.d(
                                TAG,
                                "MiLink transparency ${method.declaringClass.name}.${method.name} " +
                                    "route=$route result=${this.result}",
                            )
                        }
                    }.onFailure {
                        Log.w(TAG, "hook ${method.declaringClass.name}.${method.name} transparency skipped", it)
                    }
                }
        }.onFailure { Log.w(TAG, "hook $className transparency methods skipped", it) }
    }

    private fun hookCirculatePlusHeadsetAncCard() {
        runCatching {
            val detailClass = findClass("com.miui.circulateplus.world.headset.HeadSetsDetail")
            hookHeadsetDetailPresentation(detailClass)
            val hostSpecs = compatibleMiLinkAncHostSpecs { className ->
                runCatching {
                    findClass(className).getDeclaredConstructor(detailClass)
                }.isSuccess
            }
            if (hostSpecs.isEmpty()) {
                throw NoSuchMethodException("No compatible MiLink ANC card constructor")
            }
            // 只有一个实现时，卡片构造前的 HeadsetInfo 初始读取也必须使用正确枚举顺序。
            activeMiLinkAncHostSpec = hostSpecs.singleOrNull()
            hostSpecs.forEach { hostSpec ->
                hookMiLinkAncHostCard(detailClass, hostSpec)
            }
            Log.i(TAG, "MiLink headset UI adapters=${hostSpecs.joinToString { it.adapterName }}")
        }.onFailure { Log.w(TAG, "hook CirculatePlus headset ANC card skipped", it) }
    }

    /**
     * 新版融合中心可能同时保留旧版和新版卡片类。只 Hook 第一个可加载的类会错过实际创建的
     * HyperOS 4 卡片，导致协议控制生效但三个按钮不重画，因此每个兼容实现都独立绑定。
     */
    private fun hookMiLinkAncHostCard(
        detailClass: Class<*>,
        hostSpec: MiLinkAncHostSpec,
    ) {
        runCatching {
            val cardClass = findClass(hostSpec.cardClassName)
            hookConstructorAfter(cardClass.getDeclaredConstructor(detailClass).apply { isAccessible = true }) {
                val card = result ?: instance ?: return@hookConstructorAfter
                val detail = args[0] ?: return@hookConstructorAfter
                ancCards[card] = AncCardBinding(
                    detail = WeakReference(detail),
                    hostSpec = hostSpec,
                )
                activeMiLinkAncHostSpec = hostSpec
                safelyConfigureAncCard(card, "constructor")
            }
            cardClass.declaredMethods
                .filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterTypes.size <= 1 &&
                        (hostSpec.refreshMethodNames?.let { method.name in it } ?: true)
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        if (hostSpec.refreshMethodNames != null) {
                            hookBefore(method) {
                                val card = instance ?: return@hookBefore
                                val binding = ancCards[card] ?: return@hookBefore
                                val incomingHostState = args.singleOrNull() as? Int
                                    ?: return@hookBefore
                                val decision = runCatching {
                                    loadState()
                                    val cardRoute = resolvedAncCardRoute(
                                        binding = binding,
                                        forceResolve = false,
                                    )
                                    miLinkAncHostRefreshDecision(
                                        cardRoute = cardRoute,
                                        activeRoute = currentHuaweiRoute(),
                                        currentHuaweiStatus = currentAnc,
                                        hostSpec = binding.hostSpec,
                                        incomingHostState = incomingHostState,
                                    )
                                }.getOrElse { error ->
                                    // Hook 的身份/状态解析失败不能阻断融合中心原生刷新。
                                    Log.w(TAG, "MiLink ANC host refresh decision failed", error)
                                    proceedWithArgs(incomingHostState)
                                    return@hookBefore
                                }
                                if (decision.guardAsUiOnly &&
                                    incomingHostState != decision.hostState
                                ) {
                                    Log.i(
                                        TAG,
                                        "MiLink stale ANC host refresh corrected " +
                                            "incoming=$incomingHostState current=${decision.hostState}",
                                    )
                                }
                                if (decision.guardAsUiOnly && ancInternalUiSyncDepth.get() == 0) {
                                    withMiLinkAncUiSync(ancInternalUiSyncDepth) {
                                        proceedWithArgs(decision.hostState)
                                    }
                                } else {
                                    proceedWithArgs(decision.hostState)
                                }
                                safelyConfigureAncCard(
                                    card,
                                    method.name,
                                    schedulePostRefresh = false,
                                )
                            }
                        } else {
                            hookAfter(method) {
                                safelyConfigureAncCard(
                                    instance,
                                    method.name,
                                    schedulePostRefresh = true,
                                )
                            }
                        }
                    }.onFailure { Log.w(TAG, "hook ${cardClass.name}.${method.name} hide transparency skipped", it) }
                }
            if (hostSpec.recomputeHeightWhenHidden) {
                runCatching {
                    val heightMethod = detailClass.getDeclaredMethod(hostSpec.heightMethodName).apply {
                        isAccessible = true
                    }
                    hookBefore(heightMethod) {
                        val detail = instance as? View ?: return@hookBefore
                        loadState()
                        val strictRoute = routeForAncCardDetail(detail)
                        val presentationRoute = strictRoute.takeIf {
                            it.isSupported && !it.supportsAnc
                        } ?: noAncMiLinkPresentationRoute(
                            collectTextViews(detail).map(TextView::getText),
                        )
                        if (!forceHostAncSectionCollapsed(detail, presentationRoute, hostSpec)) {
                            restoreHostAncSectionVisibility(detail, presentationRoute)
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "hook HeadSetsDetail height preflight skipped", it)
                }
            }
        }.onFailure {
            Log.w(TAG, "hook MiLink ANC adapter=${hostSpec.adapterName} skipped", it)
        }
    }

    /** 融合中心拖动音量条时直接使用刚提交的刻度，系统广播负责按键与外部调节。 */
    private fun hookMiLinkMediaVolumeChanges() {
        runCatching {
            val method = AudioManager::class.java.getDeclaredMethod(
                "setStreamVolume",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            hookAfter(method) {
                if (args[0] as? Int != AudioManager.STREAM_MUSIC) return@hookAfter
                val requestedIndex = args[1] as? Int ?: return@hookAfter
                refreshMiLinkVolumeLabels(mediaVolumePercentForIndex(requestedIndex))
            }
        }.onFailure { logOptionalMiLinkHookSkipped("AudioManager media-volume refresh", it) }
    }

    /** 融合中心会把实际音量写入节流到约一秒一次；拖动中的百分比直接跟随本地进度。 */
    private fun hookMiLinkVolumeProgressChanges() {
        runCatching {
            val method = ProgressBar::class.java.declaredMethods.firstOrNull { candidate ->
                candidate.name == "setProgressInternal" &&
                    candidate.parameterTypes.size == 3 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
                ?: throw NoSuchMethodException("ProgressBar.setProgressInternal(int, boolean, boolean)")
            hookAfter(method) {
                val progressView = instance as? ProgressBar ?: return@hookAfter
                if (args.getOrNull(1) as? Boolean != true) return@hookAfter
                val binding = synchronized(miLinkVolumeProgressBindings) {
                    miLinkVolumeProgressBindings[progressView]
                } ?: return@hookAfter
                val root = binding.root.get()
                if (root == null || !isDescendantOf(progressView, root)) {
                    synchronized(miLinkVolumeProgressBindings) {
                        miLinkVolumeProgressBindings.remove(progressView)
                    }
                    return@hookAfter
                }
                val minimum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    progressView.min
                } else {
                    0
                }
                val percent = miLinkVolumePercentForProgress(
                    progress = progressView.progress,
                    minimum = minimum,
                    maximum = progressView.max,
                ) ?: return@hookAfter
                syncMiLinkVolumeLabel(root, binding.route, percent)
            }
        }.onFailure { logOptionalMiLinkHookSkipped("MiLink volume progress refresh", it) }
    }

    /**
     * 融合中心会在详情绑定结束后继续异步写回通用耳机图。这里只观察已经由
     * [syncMiLinkHeadsetIcon] 精确登记的主图 View，避免宿主晚到的写入造成闪回。
     */
    private fun hookMiLinkHeadsetIconWrites() {
        listOf(
            ImageView::class.java.getDeclaredMethod("setImageDrawable", Drawable::class.java),
            ImageView::class.java.getDeclaredMethod("setImageBitmap", Bitmap::class.java),
            ImageView::class.java.getDeclaredMethod("setImageResource", Int::class.javaPrimitiveType!!),
        ).forEach { method ->
            runCatching {
                method.isAccessible = true
                hookAfter(method) {
                    if (miLinkHeadsetIconInternalRenderDepth.get() > 0) return@hookAfter
                    val imageView = instance as? ImageView ?: return@hookAfter
                    val requestedKey = synchronized(miLinkHeadsetIconViewStates) {
                        miLinkHeadsetIconViewStates[imageView]?.requestedKey
                    }
                    val cached = miLinkHeadsetIconBitmapCache ?: return@hookAfter
                    val alreadyApplied = (imageView.drawable as? BitmapDrawable)?.bitmap === cached.bitmap
                    if (!shouldReapplyMiLinkHeadsetIcon(requestedKey, cached.key, alreadyApplied)) {
                        return@hookAfter
                    }
                    applyMiLinkHeadsetIcon(imageView, cached.key, cached.bitmap)
                }
            }.onFailure { error ->
                logOptionalMiLinkHookSkipped("MiLink headset icon write guard ${method.name}", error)
            }
        }
    }

    /** 图片替换不再依赖 ANC 卡片是否存在；无 ANC 机型也能在详情绑定后独立刷新。 */
    private fun hookHeadsetDetailPresentation(detailClass: Class<*>) {
        detailClass.declaredConstructors.forEach { constructor ->
            runCatching {
                constructor.isAccessible = true
                hookConstructorAfter(constructor) {
                    val detail = (result ?: instance) as? View ?: return@hookConstructorAfter
                    rememberAndRefreshHeadsetDetail(detail, "constructor")
                }
            }.onFailure {
                Log.w(TAG, "hook ${detailClass.name} constructor presentation skipped", it)
            }
        }
        val refreshMethods = setOf(
            "onFinishInflate",
            "onAttachedToWindow",
            "setAttachedDeviceInfo",
            "setHeadsetDeviceInfo",
            "setHeadsetInfo",
            "updateState",
            "x",
        )
        detailClass.declaredMethods
            .filter { method -> method.name in refreshMethods && method.returnType == Void.TYPE }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    hookAfter(method) {
                        val detail = instance as? View ?: return@hookAfter
                        rememberAndRefreshHeadsetDetail(detail, method.name)
                    }
                }.onFailure {
                    Log.w(TAG, "hook ${detailClass.name}.${method.name} presentation skipped", it)
                }
            }
    }

    private fun rememberAndRefreshHeadsetDetail(detail: View, reason: String) {
        synchronized(headsetDetails) { headsetDetails[detail] = true }
        loadState()
        val activeRoute = currentHuaweiRoute()
        val liveDetailCount = synchronized(headsetDetails) { headsetDetails.size }
        val immediateRoute = immediateMiLinkHeadsetIconRoute(
            // 详情构造/展开的首帧不能执行深层反射扫描；ANC 卡片稍后会缓存严格身份。
            strictRoute = cachedRouteForHeadsetDetail(detail)
                ?: HuaweiDeviceRoute.UNSUPPORTED,
            activeRoute = activeRoute,
            activeAddress = currentAddress,
            sessionConfirmed = currentSessionConfirmed,
            liveHeadsetDetailCount = liveDetailCount,
        )
        if (immediateRoute.isSupported) {
            // updateState 返回前重新写入已缓存图片，避免宿主通用图标进入下一帧。
            syncMiLinkHeadsetIcon(detail, immediateRoute)
        }
        postTracked(detail) {
            loadState()
            val strictRoute = cachedRouteForHeadsetDetail(detail)
                ?: routeForAncCardDetail(detail)
            val presentationRoute = refreshedMiLinkHeadsetIconRoute(
                strictRoute = strictRoute,
                labelRoute = noAncMiLinkPresentationRoute(
                    collectTextViews(detail).map(TextView::getText),
                ),
                activeRoute = currentHuaweiRoute(),
                activeAddress = currentAddress,
                sessionConfirmed = currentSessionConfirmed,
                liveHeadsetDetailCount = synchronized(headsetDetails) { headsetDetails.size },
            )
            syncMiLinkHeadsetIcon(detail, presentationRoute)
            syncMiLinkVolumeLabel(detail, presentationRoute)
            scheduleVisibleAncRefresh()
            Log.d(TAG, "MiLink headset detail refreshed route=$presentationRoute reason=$reason")
        }
    }

    private fun scheduleVisibleAncRefresh() {
        if (!visibleAncRefreshScheduled.compareAndSet(false, true)) return
        mainHandler.post(visibleAncRefreshRunnable)
    }

    /**
     * 新版融合中心会根据 DeviceInfo.extra 的 isGlasses 选择系统眼镜图标。
     * 模块复用的 TWS deviceId 会被宿主误判为普通耳机，因此只能在已确认的眼镜路由上修正分类。
     */
    private fun hookMiLinkAudioGlassesClassification() {
        runCatching {
            val boundedDeviceClass = findClass("wd.a")
            val callbackMethod = findClass("ce.l").declaredMethods.first { method ->
                method.name == "a" &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(boundedDeviceClass))
            }.apply { isAccessible = true }
            hookAfter(callbackMethod) {
                val boundedDevice = args.singleOrNull() ?: return@hookAfter
                val bluetoothDevice = runCatching {
                    callMethod(boundedDevice, "G") as? BluetoothDevice
                }.getOrNull() ?: return@hookAfter
                val route = routeForDevice(bluetoothDevice)
                if (!shouldPresentAsMiLinkAudioGlasses(route)) return@hookAfter

                val extra = runCatching {
                    val deviceInfo = callMethod(boundedDevice, "e") ?: return@runCatching null
                    callMethod(deviceInfo, "getExtra") as? Bundle
                }.getOrNull() ?: return@hookAfter
                extra.putBoolean("isHeadset", false)
                extra.putBoolean("isGlasses", true)
                Log.d(TAG, "MiLink device classified as audio glasses route=$route")
            }
        }.onFailure { Log.w(TAG, "hook MiLink audio glasses classification skipped", it) }
    }

    /**
     * 优先接管融合设备中心稳定的 HeadsetServiceController API。私有 w0 卡片 Hook 只负责
     * 当前系统版本的即时绘制，协议写入绝不能落到小米耳机实现。
     */
    private fun hookCirculatePlusFreeClip2AudioEffectApi() {
        runCatching {
            val serviceInfoClass = findClass("com.miui.circulate.api.service.CirculateServiceInfo")
            val controllerClass = findClass(
                "com.miui.circulate.api.protocol.headset.HeadsetServiceController",
            )
            val detailClass = findClass("com.miui.circulateplus.world.headset.HeadSetsDetail")
            val hostSpec = selectMiLinkAudioEffectHostSpec { className ->
                runCatching {
                    findClass(className).getDeclaredConstructor(detailClass)
                }.isSuccess
            } ?: throw NoSuchMethodException("No compatible MiLink audio-effect value order")
            hookAfter(
                controllerClass.getDeclaredMethod(
                    "getBluetoothDeviceAudioEffect",
                    serviceInfoClass,
                ).apply { isAccessible = true },
            ) {
                val serviceInfo = args[0] ?: return@hookAfter
                loadState()
                if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
                    !isTargetCirculateHeadset(serviceInfo)
                ) {
                    return@hookAfter
                }
                requestFreeClip2AudioState("controller-api-get")
                result = miLinkAudioEffectForFreeClip2SpatialMode(
                    currentFreeClip2SpatialMode,
                    hostSpec,
                )
            }
            hookBefore(
                controllerClass.getDeclaredMethod(
                    "setAudioEffect",
                    serviceInfoClass,
                    Int::class.javaPrimitiveType!!,
                ).apply { isAccessible = true },
            ) {
                val serviceInfo = args[0] ?: return@hookBefore
                loadState()
                if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2 ||
                    !isTargetCirculateHeadset(serviceInfo)
                ) {
                    return@hookBefore
                }
                val mode = freeClip2SpatialModeForMiLinkAudioEffect(
                    args[1] as? Int ?: -1,
                    hostSpec,
                )
                    ?: run {
                        result = CompletableFuture.completedFuture(208)
                        return@hookBefore
                    }
                requestFreeClip2AudioSelection(mode, "controller-api-selected")
                result = CompletableFuture.completedFuture(100)
            }
        }.onFailure {
            Log.w(TAG, "hook CirculatePlus FreeClip2 audio-effect API skipped", it)
        }
    }

    /**
     * 复用融合设备中心自带的“空间音频”三段式卡片。宿主只负责绘制，所有读写仍由
     * Huawei 蓝牙会话处理，避免误走小米耳机协议。
     */
    private fun hookCirculatePlusFreeClip2AudioEffectCard() {
        runCatching {
            val detailClass = findClass("com.miui.circulateplus.world.headset.HeadSetsDetail")
            val hostSpec = selectMiLinkAudioEffectHostSpec { className ->
                runCatching {
                    findClass(className).getDeclaredConstructor(detailClass)
                }.isSuccess
            } ?: throw NoSuchMethodException("No compatible MiLink audio-effect card constructor")
            val sectionClass = findClass(hostSpec.sectionClassName)
            hookConstructorAfter(sectionClass.getDeclaredConstructor(detailClass).apply { isAccessible = true }) {
                val section = result ?: instance ?: return@hookConstructorAfter
                val detail = args[0] ?: return@hookConstructorAfter
                miAudioEffectSections[section] = MiAudioEffectBinding(
                    detail = WeakReference(detail),
                    hostSpec = hostSpec,
                )
                safelyConfigureFreeClip2AudioEffectSection(section, "constructor")
            }

            hookBefore(
                sectionClass.getDeclaredMethod(
                    hostSpec.renderMethodName,
                    Int::class.javaPrimitiveType!!,
                ).apply {
                    isAccessible = true
                },
            ) {
                val section = instance ?: return@hookBefore
                if (freeClip2RouteForAudioEffectSection(section) != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                    return@hookBefore
                }
                loadState()
                if (freeClip2AudioInternalRenderDepth.get() == 0) {
                    requestFreeClip2AudioState("native-card-get")
                }
                proceedWithArgs(
                    miLinkAudioEffectForFreeClip2SpatialMode(
                        currentFreeClip2SpatialMode,
                        hostSpec,
                    ),
                )
            }

            hookAfter(
                sectionClass.getDeclaredMethod(
                    hostSpec.renderMethodName,
                    Int::class.javaPrimitiveType!!,
                ).apply { isAccessible = true },
            ) {
                val section = instance ?: return@hookAfter
                if (freeClip2RouteForAudioEffectSection(section) ==
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I
                ) {
                    safelyConfigureFreeClip2AudioEffectSection(
                        section,
                        "native-card-rendered",
                        schedulePostRefresh = false,
                    )
                }
            }

            hookBefore(
                sectionClass.getDeclaredMethod(
                    "l",
                    View::class.java,
                    Int::class.javaPrimitiveType!!,
                ).apply { isAccessible = true },
            ) {
                val section = instance ?: return@hookBefore
                if (freeClip2RouteForAudioEffectSection(section) != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                    return@hookBefore
                }
                if (!shouldDispatchFreeClip2AudioSelection(freeClip2AudioInternalRenderDepth.get())) {
                    // The host render method may call section.l(...) while we are only repainting a
                    // confirmed state. Let the host finish that render without turning it into a
                    // second device command.
                    return@hookBefore
                }
                val mode = freeClip2SpatialModeForMiLinkAudioEffect(
                    args[1] as? Int ?: -1,
                    hostSpec,
                )
                    ?: run {
                        result = null
                        return@hookBefore
                }
                requestFreeClip2AudioSelection(mode, "native-card-selected")
                runCatching {
                    renderFreeClip2AudioEffectMode(section, currentFreeClip2SpatialMode)
                }
                    .onFailure { Log.w(TAG, "MiLink FreeClip2 spatial card refresh failed", it) }
                result = null
            }
            Log.i(TAG, "MiLink spatial-audio UI adapter=${hostSpec.adapterName}")
        }.onFailure {
            Log.w(TAG, "hook CirculatePlus FreeClip2 spatial-audio card skipped", it)
        }
    }

    private fun safelyConfigureFreeClip2AudioEffectSection(
        section: Any?,
        reason: String,
        schedulePostRefresh: Boolean = true,
    ) {
        runCatching {
            configureFreeClip2AudioEffectSection(section, reason, schedulePostRefresh)
        }.onFailure {
            Log.w(TAG, "MiLink FreeClip2 spatial card configure failed reason=$reason", it)
        }
    }

    private fun configureFreeClip2AudioEffectSection(
        section: Any?,
        reason: String,
        schedulePostRefresh: Boolean,
    ) {
        section ?: return
        loadState()
        val binding = miAudioEffectSections[section] ?: return
        val detail = binding.detail.get() ?: return
        if (schedulePostRefresh) {
            (detail as? View)?.let { detailView -> postTracked(detailView) {
                safelyConfigureFreeClip2AudioEffectSection(
                    section,
                    "$reason-post",
                    schedulePostRefresh = false,
                )
            } }
        }
        val detailView = detail as? View
        when (routeForAncCardDetail(detail)) {
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> {
                detailView?.let { root ->
                    restoreHuaweiEqualizerControls(root)
                    applyFreeClip2CardPresentation(root, binding.hostSpec)
                    syncFreeClip2SoundEffectControls(root, binding.hostSpec)
                }
                if (reason == "constructor") {
                    requestFreeClip2AudioState("native-card-configure-$reason", force = true)
                }
                renderFreeClip2AudioEffectMode(section, currentFreeClip2SpatialMode)
                Log.d(
                    TAG,
                    "MiLink native FreeClip2 spatial card configured " +
                        "mode=$currentFreeClip2SpatialMode reason=$reason",
                )
            }
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> {
                detailView?.let { root ->
                    restoreFreeClip2CardPresentation(root)
                    syncFreeBuds6iSoundEffectControls(root, binding.hostSpec)
                }
                if (reason == "constructor") {
                    requestHuaweiEqualizerState("native-card-configure-$reason", force = true)
                }
            }
            else -> detailView?.let { root ->
                restoreFreeClip2CardPresentation(root)
                restoreHuaweiEqualizerControls(root)
            }
        }
    }

    private fun freeClip2RouteForAudioEffectSection(section: Any): HuaweiDeviceRoute {
        val detail = miAudioEffectSections[section]?.detail?.get()
            ?: return HuaweiDeviceRoute.UNSUPPORTED
        return routeForAncCardDetail(detail)
    }

    private fun renderFreeClip2AudioEffectMode(
        section: Any,
        mode: FreeClip2SpatialAudioMode,
    ) {
        val hostSpec = miAudioEffectSections[section]?.hostSpec ?: return
        freeClip2AudioInternalRenderDepth.incrementAndGet()
        try {
            callMethod(
                section,
                hostSpec.renderMethodName,
                miLinkAudioEffectForFreeClip2SpatialMode(mode, hostSpec),
            )
        } finally {
            freeClip2AudioInternalRenderDepth.decrementAndGet()
        }
    }

    private fun applyFreeClip2CardPresentation(
        root: View,
        hostSpec: MiLinkAudioEffectHostSpec = audioEffectHostSpecForRoot(root),
    ) {
        val stableSelectCard = hostSpec.selectCardIdName?.let { root.findHostViewByIdName(it) }
        val labels = collectTextViews(stableSelectCard ?: root)
        // OS4 18.1 的构造顺序是“固定 / 头部跟踪 / 关闭”，但状态值仍是
        // 0=关闭、1=固定、2=头部跟踪。仅在固定资源定位到的原生选择卡内部重排，
        // 避免过去整页扫描时误搬音效卡或标题。
        reorderFreeClip2SpatialOptions(labels)
        labels.forEach { textView ->
            val semantic = FreeClip2MiLinkUiPolicy.classify(
                freeClip2OriginalLabels[textView] ?: textView.text,
            ) ?: return@forEach
            val replacement = when (semantic) {
                FreeClip2MiLinkLabel.AUDIO_SETTINGS ->
                    if (stableSelectCard == null) {
                        moduleString(R.string.freeclip2_audio_settings, "音效")
                    } else {
                        return@forEach
                    }
                FreeClip2MiLinkLabel.FIXED ->
                    moduleString(R.string.freeclip2_spatial_fixed, "固定")
                FreeClip2MiLinkLabel.HEAD_TRACKING ->
                    moduleString(R.string.freeclip2_spatial_head_tracking, "头部跟踪")
                FreeClip2MiLinkLabel.OFF -> return@forEach
            }
            if (textView.text.toString() == replacement) return@forEach
            freeClip2OriginalLabels.putIfAbsent(textView, textView.text)
            textView.text = replacement
        }
    }

    /** 宿主原来的 ANC 区域对 FreeClip 2 没有含义，优先用音效选择器原位替换。 */
    private fun syncFreeClip2SoundEffectControls(
        root: View,
        hostSpec: MiLinkAudioEffectHostSpec = audioEffectHostSpecForRoot(root),
    ) {
        syncUnsupportedAncHeading(root, HuaweiDeviceRoute.HUAWEI_FREECLIP2)
        val usesHostHeading = findMiLinkAncHeading(root) != null
        val labels = collectTextViews(root)
        val stableSpatialCard = hostSpec.selectCardIdName?.let { root.findHostViewByIdName(it) }
        hostSpec.titleIdName?.let { root.findHostViewByIdName(it) }?.visibility = View.VISIBLE
        val soundEffectSlot = hostSpec.soundEffectSlotIdName?.let { root.findHostViewByIdName(it) }
        val replacesHostSlot = hostSpec.soundEffectSlotIdName != null
        val placement = if (hostSpec.soundEffectSlotIdName != null) {
            soundEffectSlot ?: return
            val parent = soundEffectSlot.parent as? ViewGroup ?: return
            freeClip2SoundEffectAnchors[soundEffectSlot] = true
            setCapabilityViewVisible(soundEffectSlot, visible = false)
            FreeClip2SectionPlacement(parent, null, soundEffectSlot)
        } else if (stableSpatialCard != null) {
            val parent = stableSpatialCard.parent as? ViewGroup ?: return
            // 只以固定资源定位，不隐藏原生“空间音频”标题，也不向上猜测父卡片。
            FreeClip2SectionPlacement(parent, null, stableSpatialCard)
        } else {
            val optionLayout = findFreeClip2OptionLayout(labels) ?: return
            val audioHeading = labels.firstOrNull { textView ->
                FreeClip2MiLinkUiPolicy.classify(
                    freeClip2OriginalLabels[textView] ?: textView.text,
                ) == FreeClip2MiLinkLabel.AUDIO_SETTINGS
            } ?: return
            findFreeClip2SectionPlacement(audioHeading, optionLayout.fixed) ?: return
        }

        placement.audioHeadingRoot?.let { headingRoot ->
            freeClip2AudioHeadingRoots[headingRoot] = true
            setCapabilityViewVisible(headingRoot, visible = false)
        }

        val existing = findTaggedView(root, FREECLIP2_SOUND_EFFECT_CONTROLS_TAG)
            as? HuaweiFreeClip2AudioControlsView
        val controls = existing ?: HuaweiFreeClip2AudioControlsView(
            context = root.context,
            onSpatialModeSelected = {},
            onSpatialSceneSelected = {},
            onSoundEffectSelected = { effect ->
                requestFreeClip2SoundEffectSelection(effect, "native-sound-effect-selected")
            },
            onCustomSoundEffectSelected = { preset ->
                requestFreeClip2CustomSoundEffectSelection(
                    preset,
                    "native-custom-sound-effect-selected",
                )
            },
        ).apply {
            tag = FREECLIP2_SOUND_EFFECT_CONTROLS_TAG
        }

        if (controls.parent !== placement.parent) {
            (controls.parent as? ViewGroup)?.removeView(controls)
            addFreeClip2SoundEffectControls(placement, controls, replacesHostSlot)
        } else {
            val desiredIndex = placement.parent.indexOfChild(placement.cardAnchor) + 1
            val currentIndex = placement.parent.indexOfChild(controls)
            if (desiredIndex > 0 && currentIndex != desiredIndex) {
                placement.parent.removeView(controls)
                addFreeClip2SoundEffectControls(placement, controls, replacesHostSlot)
            }
        }

        val darkSurface = isDarkSurface(placement.cardAnchor)
        val titleStyle = freeClip2SectionTitleStyle(labels)
        controls.setSectionTitleStyle(titleStyle)
        controls.setHostAccentColor(
            miLinkSpatialAccentColor(
                labels = collectTextViews(stableSpatialCard ?: root),
                preferred = when (currentFreeClip2SpatialMode) {
                    FreeClip2SpatialAudioMode.OFF -> FreeClip2MiLinkLabel.OFF
                    FreeClip2SpatialAudioMode.FIXED -> FreeClip2MiLinkLabel.FIXED
                    FreeClip2SpatialAudioMode.HEAD_TRACKING ->
                        FreeClip2MiLinkLabel.HEAD_TRACKING
                },
            ),
        )
        controls.render(
            spatialMode = currentFreeClip2SpatialMode,
            spatialScene = currentFreeClip2SpatialScene,
            soundEffect = currentFreeClip2SoundEffect,
            labels = huaweiFreeClip2AudioLabels { resId, fallback ->
                moduleString(resId, fallback)
            },
            darkSurface = darkSurface,
            showSpatialMode = false,
            showSpatialScene = false,
            showSoundEffect = true,
            showSoundEffectTitle = !usesHostHeading,
            compact = true,
            customSoundEffects = currentFreeClip2CustomPresets,
            selectedCustomSoundEffectId = currentFreeClip2EqualizerSelectedId,
        )
        matchFreeClip2SoundEffectCardPresentation(
            placement = placement,
            controls = controls,
            presentationSource = stableSpatialCard ?: placement.cardAnchor,
            replacesHostSlot = replacesHostSlot,
        )
        controls.visibility = View.VISIBLE
    }

    /** 6i 保留原生 ANC；有空间音频卡时跟随其后，否则插到完整 ANC 区域与音量之间。 */
    private fun syncFreeBuds6iSoundEffectControls(
        root: View,
        hostSpec: MiLinkAudioEffectHostSpec = audioEffectHostSpecForRoot(root),
    ) {
        val spatialCard = hostSpec.selectCardIdName
            ?.let { root.findHostViewByIdName(it) }
            ?.takeIf { it.isVisibleInHierarchy(root) }
        val placement = spatialCard?.let { card ->
            val parent = card.parent as? ViewGroup ?: return@let null
            FreeClip2SectionPlacement(parent, null, card)
        } ?: root.findHostViewByIdName("anc_select_card")
            ?.let { ancCard -> findMiLinkSectionPlacementBeforeVolume(root, ancCard) }
            ?: return
        val parent = placement.parent
        val existing = findTaggedView(root, HUAWEI_EQUALIZER_CONTROLS_TAG)
            as? HuaweiFreeClip2AudioControlsView
        val controls = existing ?: HuaweiFreeClip2AudioControlsView(
            context = root.context,
            onSpatialModeSelected = {},
            onSpatialSceneSelected = {},
            onSoundEffectSelected = {},
            onBuiltInSoundEffectSelected = { presetId ->
                requestHuaweiEqualizerSelection(presetId, "native-sound-effect-selected")
            },
        ).apply {
            tag = HUAWEI_EQUALIZER_CONTROLS_TAG
        }

        if (controls.parent !== parent) {
            (controls.parent as? ViewGroup)?.removeView(controls)
            addFreeClip2SoundEffectControls(placement, controls)
        } else {
            val desiredIndex = parent.indexOfChild(placement.cardAnchor) + 1
            val currentIndex = parent.indexOfChild(controls)
            if (desiredIndex > 0 && currentIndex != desiredIndex) {
                parent.removeView(controls)
                addFreeClip2SoundEffectControls(placement, controls)
            }
        }

        val labels = collectTextViews(root)
        controls.setSectionTitleStyle(freeClip2SectionTitleStyle(labels))
        controls.setHostAccentColor(
            miLinkSpatialAccentColor(
                collectTextViews(spatialCard ?: placement.cardAnchor),
                preferred = null,
            ),
        )
        controls.renderBuiltInSoundEffects(
            selectedId = currentHuaweiEqualizerSelectedId,
            options = listOf(
                HuaweiFreeClip2AudioControlsView.BuiltInSoundEffectOption(
                    0x01,
                    moduleString(R.string.freebuds5_sound_effect_default, "默认"),
                ),
                HuaweiFreeClip2AudioControlsView.BuiltInSoundEffectOption(
                    0x02,
                    moduleString(R.string.freebuds5_sound_effect_bass, "低音增强"),
                ),
                HuaweiFreeClip2AudioControlsView.BuiltInSoundEffectOption(
                    0x03,
                    moduleString(R.string.freebuds5_sound_effect_treble, "高音增强"),
                ),
                HuaweiFreeClip2AudioControlsView.BuiltInSoundEffectOption(
                    0x09,
                    moduleString(R.string.freebuds5_sound_effect_clear_voice, "清晰人声"),
                ),
            ),
            title = moduleString(R.string.freeclip2_sound_effect, "音效"),
            customTitle = moduleString(R.string.freebuds7i_custom_equalizer, "自定义均衡器"),
            darkSurface = isDarkSurface(spatialCard ?: placement.cardAnchor),
            compact = true,
        )
        matchFreeClip2SoundEffectCardPresentation(placement, controls)
        controls.visibility = View.VISIBLE
    }

    /** 只读取宿主标题样式，不再移动或重绘原生标题，避免与宿主布局动画互相覆盖。 */
    private fun freeClip2SectionTitleStyle(
        labels: List<TextView>,
    ): HuaweiFreeClip2AudioControlsView.SectionTitleStyle? {
        val reference = labels.firstOrNull { textView ->
            FreeClip2MiLinkUiPolicy.isSpatialAudioHeading(
                freeClip2OriginalLabels[textView] ?: textView.text,
            )
        } ?: labels.firstOrNull { textView ->
            FreeClip2MiLinkUiPolicy.isVolumeHeading(textView.text)
        } ?: return null
        return HuaweiFreeClip2AudioControlsView.SectionTitleStyle.capture(reference)
    }

    private fun addFreeClip2SoundEffectControls(
        placement: FreeClip2SectionPlacement,
        controls: HuaweiFreeClip2AudioControlsView,
        replacesHostSlot: Boolean = false,
    ) {
        val params = matchingCardLayoutParams(placement.cardAnchor, replacesHostSlot)
        val index = (placement.parent.indexOfChild(placement.cardAnchor) + 1)
            .coerceIn(0, placement.parent.childCount)
        placement.parent.addView(controls, index, params)
        placement.parent.requestLayout()
    }

    /** 复用被替换宿主卡的外框与间距，避免新增区块改变详情页测量高度。 */
    private fun matchFreeClip2SoundEffectCardPresentation(
        placement: FreeClip2SectionPlacement,
        controls: HuaweiFreeClip2AudioControlsView,
        presentationSource: View = placement.cardAnchor,
        replacesHostSlot: Boolean = false,
    ) {
        controls.layoutParams = matchingCardLayoutParams(placement.cardAnchor, replacesHostSlot)
        controls.background = presentationSource.background
            ?.constantState
            ?.newDrawable()
            ?.mutate()
            ?: controls.background
        controls.minimumHeight = presentationSource.minimumHeight
        controls.elevation = presentationSource.elevation
        controls.gravity = Gravity.CENTER_VERTICAL
        val horizontalPadding = (6f * controls.resources.displayMetrics.density).toInt()
        controls.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        controls.requestLayout()
    }

    private fun miLinkSpatialAccentColor(
        labels: List<TextView>,
        preferred: FreeClip2MiLinkLabel?,
    ): Int? {
        preferred?.let { semantic ->
            labels.firstOrNull { textView ->
                FreeClip2MiLinkUiPolicy.classify(
                    freeClip2OriginalLabels[textView] ?: textView.text,
                ) == semantic
            }?.currentTextColor?.let { return it }
        }
        return labels
            .map(TextView::getCurrentTextColor)
            .distinct()
            .mapNotNull { color ->
                if (Color.alpha(color) == 0) return@mapNotNull null
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                color.takeIf { hsv[0] in 185f..245f && hsv[1] >= 0.35f }
                    ?.let { it to hsv[1] * hsv[2] }
            }
            .maxByOrNull(Pair<Int, Float>::second)
            ?.first
    }

    private fun matchingCardLayoutParams(
        source: View,
        replacesHostSlot: Boolean = false,
    ): ViewGroup.LayoutParams {
        val sourceParams = source.layoutParams
        val targetHeight = miLinkSoundEffectCardHeight(
            sourceHeight = sourceParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            replacesHostSlot = replacesHostSlot,
        )
        return when (sourceParams) {
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(sourceParams).apply {
                height = targetHeight
            }
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(sourceParams).apply {
                height = targetHeight
            }
            null -> ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            else -> ViewGroup.LayoutParams(sourceParams).apply {
                height = targetHeight
            }
        }
    }

    private fun findFreeClip2SectionPlacement(
        audioHeading: View,
        spatialOption: View,
    ): FreeClip2SectionPlacement? {
        var parent = spatialOption.parent as? ViewGroup
        while (parent != null) {
            val headingRoot = directChildUnder(parent, audioHeading)
            val spatialRoot = directChildUnder(parent, spatialOption)
            if (
                headingRoot != null &&
                spatialRoot != null &&
                headingRoot !== spatialRoot &&
                parent.indexOfChild(headingRoot) >= 0 &&
                parent.indexOfChild(spatialRoot) >= 0
            ) {
                return FreeClip2SectionPlacement(parent, headingRoot, spatialRoot)
            }
            parent = parent.parent as? ViewGroup
        }
        return null
    }

    private fun findMiLinkSectionPlacementBeforeVolume(
        root: View,
        anchor: View,
    ): FreeClip2SectionPlacement? {
        val volumeHeading = collectTextViews(root).firstOrNull { textView ->
            FreeClip2MiLinkUiPolicy.isVolumeHeading(textView.text)
        } ?: return null
        var fallback: FreeClip2SectionPlacement? = null
        var parent = anchor.parent as? ViewGroup
        while (parent != null) {
            val anchorRoot = directChildUnder(parent, anchor)
            val volumeRoot = directChildUnder(parent, volumeHeading)
            if (anchorRoot != null && volumeRoot != null && anchorRoot !== volumeRoot) {
                val anchorIndex = parent.indexOfChild(anchorRoot)
                val volumeIndex = parent.indexOfChild(volumeRoot)
                if (anchorIndex >= 0 && volumeIndex > anchorIndex) {
                    val placement = FreeClip2SectionPlacement(parent, null, anchorRoot)
                    if (volumeIndex == anchorIndex + 1) return placement
                    fallback = placement
                }
            }
            parent = parent.parent as? ViewGroup
        }
        return fallback
    }

    private fun View.isVisibleInHierarchy(root: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun audioEffectHostSpecForRoot(root: View): MiLinkAudioEffectHostSpec =
        miLinkAudioEffectHostSpecs.firstOrNull { spec ->
            spec.selectCardIdName?.let { root.findHostViewByIdName(it) } != null
        } ?: miLinkAudioEffectHostSpecs.first()

    private fun restoreFreeClip2CardPresentation(root: View) {
        findTaggedView(root, FREECLIP2_SOUND_EFFECT_CONTROLS_TAG)?.let { controls ->
            (controls.parent as? ViewGroup)?.removeView(controls)
        }
        synchronized(freeClip2SoundEffectAnchors) {
            freeClip2SoundEffectAnchors.keys.toList().forEach { anchor ->
                if (!isDescendantOf(anchor, root)) return@forEach
                restoreCapabilityView(anchor)
                freeClip2SoundEffectAnchors.remove(anchor)
            }
        }
        synchronized(freeClip2AudioHeadingRoots) {
            freeClip2AudioHeadingRoots.keys.toList().forEach { headingRoot ->
                if (!isDescendantOf(headingRoot, root)) return@forEach
                restoreCapabilityView(headingRoot)
                freeClip2AudioHeadingRoots.remove(headingRoot)
            }
        }
        synchronized(freeClip2OriginalOptionOrders) {
            freeClip2OriginalOptionOrders.entries.toList().forEach { (parent, state) ->
                if (!isDescendantOf(parent, root)) return@forEach
                val roots = state.rootsInOriginalOrder.mapNotNull(WeakReference<View>::get)
                if (roots.size == 3 && roots.all { it.parent === parent }) {
                    roots.forEach(parent::removeView)
                    roots.zip(state.originalIndices).forEach { (view, index) ->
                        parent.addView(view, index.coerceIn(0, parent.childCount))
                    }
                    parent.requestLayout()
                }
                freeClip2OriginalOptionOrders.remove(parent)
            }
        }
        synchronized(freeClip2OriginalLabels) {
            freeClip2OriginalLabels.entries.toList().forEach { (textView, original) ->
                if (!isDescendantOf(textView, root)) return@forEach
                textView.text = original
                freeClip2OriginalLabels.remove(textView)
            }
        }
    }

    private fun restoreHuaweiEqualizerControls(root: View) {
        findTaggedView(root, HUAWEI_EQUALIZER_CONTROLS_TAG)?.let { controls ->
            (controls.parent as? ViewGroup)?.removeView(controls)
        }
    }

    private fun reorderFreeClip2SpatialOptions(labels: List<TextView>) {
        val layout = findFreeClip2OptionLayout(labels) ?: return
        if (freeClip2OriginalOptionOrders.containsKey(layout.parent)) return
        val original = listOf(layout.off, layout.fixed, layout.headTracking)
            .sortedBy(layout.parent::indexOfChild)
        val originalIndices = original.map(layout.parent::indexOfChild)
        freeClip2OriginalOptionOrders[layout.parent] = FreeClip2OptionOrderState(
            rootsInOriginalOrder = original.map(::WeakReference),
            originalIndices = originalIndices,
        )
        val firstIndex = originalIndices.min()
        original.forEach(layout.parent::removeView)
        listOf(layout.off, layout.fixed, layout.headTracking).forEachIndexed { offset, view ->
            layout.parent.addView(view, (firstIndex + offset).coerceIn(0, layout.parent.childCount))
        }
        layout.parent.requestLayout()
    }

    private fun findFreeClip2OptionLayout(labels: List<TextView>): FreeClip2OptionLayout? {
        val classified = labels.mapNotNull { textView ->
            FreeClip2MiLinkUiPolicy.classify(
                freeClip2OriginalLabels[textView] ?: textView.text,
            )?.let { it to textView }
        }
        val off = classified.filter { it.first == FreeClip2MiLinkLabel.OFF }.map { it.second }
        val fixed = classified.filter { it.first == FreeClip2MiLinkLabel.FIXED }.map { it.second }
        val head = classified.filter { it.first == FreeClip2MiLinkLabel.HEAD_TRACKING }.map { it.second }
        return sequence {
            fixed.forEach { fixedLabel ->
                head.forEach { headLabel ->
                    off.forEach { offLabel ->
                        optionLayout(offLabel, fixedLabel, headLabel)?.let { yield(it) }
                    }
                }
            }
        }.minByOrNull { it.indices.max() - it.indices.min() }
    }

    private fun optionLayout(
        offLabel: TextView,
        fixedLabel: TextView,
        headLabel: TextView,
    ): FreeClip2OptionLayout? {
        var parent = fixedLabel.parent as? ViewGroup
        while (parent != null) {
            val off = directChildUnder(parent, offLabel)
            val fixed = directChildUnder(parent, fixedLabel)
            val head = directChildUnder(parent, headLabel)
            if (off != null && fixed != null && head != null && setOf(off, fixed, head).size == 3) {
                val indices = listOf(off, fixed, head).map(parent::indexOfChild)
                if (indices.all { it >= 0 } && FreeClip2MiLinkUiPolicy.isSafeConsecutiveOrder(indices)) {
                    return FreeClip2OptionLayout(parent, off, fixed, head, indices)
                }
            }
            parent = parent.parent as? ViewGroup
        }
        return null
    }

    private fun directChildUnder(parent: ViewGroup, descendant: View): View? {
        var current: View = descendant
        while (current.parent is View && current.parent !== parent) {
            current = current.parent as View
        }
        return current.takeIf { it.parent === parent }
    }

    private fun collectTextViews(root: View): List<TextView> {
        val result = mutableListOf<TextView>()
        fun visit(view: View) {
            if (view is TextView) result += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return result
    }

    private fun refreshMiLinkVolumeLabels(volumePercent: Int? = null) {
        val details = synchronized(headsetDetails) { headsetDetails.keys.toList() }
        val activeRoute = currentHuaweiRoute()
        details.forEach { detail ->
            val strictRoute = routeForAncCardDetail(detail)
            val route = strictRoute.takeIf(HuaweiDeviceRoute::isSupported)
                ?: activeRoute.takeIf {
                    currentSessionConfirmed && details.size == 1 && it.isSupported
                }
                ?: HuaweiDeviceRoute.UNSUPPORTED
            if (Looper.myLooper() == Looper.getMainLooper()) {
                syncMiLinkVolumeLabel(detail, route, volumePercent)
            } else {
                postTracked(detail) { syncMiLinkVolumeLabel(detail, route, volumePercent) }
            }
        }
    }

    private fun syncMiLinkVolumeLabel(
        root: View,
        route: HuaweiDeviceRoute,
        volumePercent: Int? = null,
    ) {
        val cached = miLinkVolumeLabels[root]?.get()
            ?.takeIf { isDescendantOf(it, root) }
        val label = cached ?: collectTextViews(root).firstOrNull { textView ->
            FreeClip2MiLinkUiPolicy.isVolumeHeading(
                miLinkVolumeOriginalLabels[textView] ?: textView.text,
            )
        }?.also { miLinkVolumeLabels[root] = WeakReference(it) } ?: return
        val original = miLinkVolumeOriginalLabels.getOrPut(label) { label.text }
        if (!route.isSupported) {
            unbindMiLinkVolumeProgress(root)
            if (label.text != original) label.text = original
            return
        }
        bindMiLinkVolumeProgress(root, label, route)
        val percent = volumePercent ?: currentMediaVolumePercent() ?: return
        val originalText = original.toString().trim()
        val rendered = if (Regex("\\d{1,3}%").containsMatchIn(originalText)) {
            originalText
        } else {
            "$originalText  $percent%"
        }
        if (label.text.toString() != rendered) label.text = rendered
    }

    private fun currentMediaVolumePercent(): Int? {
        val audioManager = context?.getSystemService(AudioManager::class.java) ?: return null
        return runCatching {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            mediaVolumePercentForIndex(current)
        }.getOrNull()
    }

    private fun mediaVolumePercentForIndex(index: Int): Int? {
        val audioManager = context?.getSystemService(AudioManager::class.java) ?: return null
        return runCatching {
            val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            miLinkVolumePercentForProgress(index, 0, maximum)
        }.getOrNull()
    }

    private fun bindMiLinkVolumeProgress(
        root: View,
        label: TextView,
        route: HuaweiDeviceRoute,
    ) {
        val named = listOf(
            "volume_seek_bar",
            "volume_seekbar",
            "headset_volume_seekbar",
            "headset_volume_bar",
            "volume_slider",
        ).asSequence()
            .mapNotNull { root.findHostViewByIdName(it) as? ProgressBar }
            .firstOrNull()
        val candidates = collectProgressBars(root).filter { progressBar ->
            val minimum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                progressBar.min
            } else {
                0
            }
            progressBar.max > minimum
        }
        val interactive = candidates.filter { progressBar ->
            progressBar is SeekBar ||
                progressBar.javaClass.name.contains("seek", ignoreCase = true) ||
                progressBar.javaClass.name.contains("slider", ignoreCase = true)
        }
        val selected = named
            ?: interactive.minByOrNull { viewHierarchyDistance(label, it) }
            ?: candidates.singleOrNull()
            ?: return
        synchronized(miLinkVolumeProgressBindings) {
            miLinkVolumeProgressBindings.entries.removeAll { (_, binding) ->
                val boundRoot = binding.root.get()
                boundRoot == null || boundRoot === root
            }
            miLinkVolumeProgressBindings[selected] = MiLinkVolumeProgressBinding(
                root = WeakReference(root),
                route = route,
            )
        }
    }

    private fun unbindMiLinkVolumeProgress(root: View) {
        synchronized(miLinkVolumeProgressBindings) {
            miLinkVolumeProgressBindings.entries.removeAll { (_, binding) ->
                val boundRoot = binding.root.get()
                boundRoot == null || boundRoot === root
            }
        }
    }

    private fun collectProgressBars(root: View): List<ProgressBar> {
        val result = mutableListOf<ProgressBar>()
        fun visit(view: View) {
            if (view is ProgressBar) result += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return result
    }

    private fun viewHierarchyDistance(first: View, second: View): Int {
        val firstAncestors = IdentityHashMap<View, Int>()
        var current: View? = first
        var distance = 0
        while (current != null) {
            firstAncestors[current] = distance++
            current = current.parent as? View
        }
        current = second
        distance = 0
        while (current != null) {
            firstAncestors[current]?.let { return it + distance }
            distance++
            current = current.parent as? View
        }
        return Int.MAX_VALUE
    }

    /** 无 ANC 的耳机隐藏整条标题；只隐藏按钮行会留下空白“噪声控制”。 */
    private fun syncUnsupportedAncHeading(root: View, route: HuaweiDeviceRoute) {
        val heading = findMiLinkAncHeading(root)
        val headingLabel = when (heading) {
            is TextView -> heading
            null -> null
            else -> collectTextViews(heading).firstOrNull()
        }
        if (route != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            headingLabel?.let { label ->
                freeClip2OriginalLabels.remove(label)?.let { label.text = it }
            }
        }
        if (!route.isSupported) {
            restoreCapabilityView(heading)
            return
        }
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            setCapabilityViewVisible(heading, true)
            headingLabel?.let { label ->
                freeClip2OriginalLabels.putIfAbsent(label, label.text)
                label.text = moduleString(R.string.freeclip2_sound_effect, "音效")
            }
            return
        }
        setCapabilityViewVisible(heading, route.supportsAnc)
    }

    private fun findMiLinkAncHeading(root: View): View? = miLinkAncHostSpecs
            .asSequence()
            .flatMap { spec -> spec.titleIdNames.asSequence() }
            .distinct()
            .mapNotNull { idName -> root.findHostViewByIdName(idName) }
            .firstOrNull()
            ?: collectTextViews(root).firstOrNull { textView ->
                val title = textView.text?.toString()?.trim().orEmpty()
                title.equals("噪声控制", ignoreCase = true) ||
                    title.equals("Noise control", ignoreCase = true)
            }
            ?: synchronized(freeClip2OriginalLabels) {
                freeClip2OriginalLabels.entries.firstOrNull { (textView, original) ->
                    isDescendantOf(textView, root) && original.toString().trim().let { title ->
                        title.equals("噪声控制", ignoreCase = true) ||
                            title.equals("Noise control", ignoreCase = true)
                    }
                }?.key
            }

    /**
     * 旧版 HeadSetsDetail 需要在隐藏 ANC 后重算高度；OS4 的 W() 是整张详情卡总高度，
     * FreeClip 2 又会把自定义音效卡放进原 ANC 预留空间，因此 OS4 只隐藏原生控件，
     * 不改 modeVisible，也不调用 W()。
     */
    private fun syncHostAncSectionHeight(
        detail: View,
        route: HuaweiDeviceRoute,
        hostSpec: MiLinkAncHostSpec,
    ) {
        if (shouldReserveLegacyMiLinkAncHeight(route, hostSpec)) {
            // legacy 的 B() 不会统计后来插入的音效 View。保留原 ANC 固定配额，ANC 按钮行仍由
            // configureAncCardViews() 隐藏，这样新增音效与音量区不会被父卡片裁掉。
            restoreHostAncSectionVisibility(detail, route)
            postTracked(detail) {
                runCatching { recomputeHostDetailHeight(detail) }
                    .onFailure {
                        Log.w(TAG, "MiLink legacy FreeClip2 height reserve failed", it)
                    }
            }
            return
        }
        if (!hostSpec.recomputeHeightWhenHidden) {
            // View 若曾由可重算的旧模板复用，仍要恢复旧值；正常 OS4 路径不会进入此分支。
            if (restoreHostAncSectionVisibility(detail, route)) {
                postTracked(detail) {
                    runCatching { recomputeHostDetailHeight(detail) }
                        .onFailure {
                            Log.w(TAG, "MiLink host detail height restore failed route=$route", it)
                        }
                }
            }
            return
        }
        val collapse = FreeClip2MiLinkUiPolicy.shouldCollapseHostAncSection(
            isSupported = route.isSupported,
            supportsAnc = route.supportsAnc,
        )
        if (collapse) {
            if (!forceHostAncSectionCollapsed(detail, route, hostSpec)) return
        } else {
            if (!restoreHostAncSectionVisibility(detail, route)) return
        }
        postTracked(detail) {
            runCatching { recomputeHostDetailHeight(detail) }
                .onFailure { Log.w(TAG, "MiLink host detail height recompute failed route=$route", it) }
        }
    }

    /** 在宿主每次计算高度前兜底，防止异步数据绑定把 modeVisible 又写回 true。 */
    private fun forceHostAncSectionCollapsed(
        detail: View,
        route: HuaweiDeviceRoute,
        hostSpec: MiLinkAncHostSpec? = null,
    ): Boolean {
        if (hostSpec != null && shouldReserveLegacyMiLinkAncHeight(route, hostSpec)) return false
        if (!FreeClip2MiLinkUiPolicy.shouldCollapseHostAncSection(
                isSupported = route.isSupported,
                supportsAnc = route.supportsAnc,
            )
        ) {
            return false
        }
        val current = runCatching { readHostModeVisible(detail) }
            .getOrNull() ?: return false
        synchronized(originalHostModeVisibility) {
            originalHostModeVisibility.putIfAbsent(detail, current)
        }
        if (!current) return true
        return runCatching {
            writeHostModeVisible(detail, false)
            true
        }.getOrElse {
            Log.w(TAG, "MiLink host ANC visibility update failed route=$route", it)
            false
        }
    }

    /** View 被复用于其他/支持 ANC 的耳机时，在高度计算前完整恢复宿主原值。 */
    private fun restoreHostAncSectionVisibility(
        detail: View,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val original = synchronized(originalHostModeVisibility) {
            originalHostModeVisibility.remove(detail)
        } ?: return false
        return runCatching {
            writeHostModeVisible(detail, original)
            true
        }.getOrElse {
            Log.w(TAG, "MiLink host ANC visibility restore failed route=$route", it)
            false
        }
    }

    private fun readHostModeVisible(detail: View): Boolean =
        runCatching { callMethod(detail, "getModeVisible") as Boolean }
            .getOrElse { getObjectField(detail, "modeVisible") as Boolean }

    private fun writeHostModeVisible(detail: View, visible: Boolean) {
        runCatching { callMethod(detail, "setModeVisible", visible) }
            .getOrElse { setObjectField(detail, "modeVisible", visible) }
    }

    private fun recomputeHostDetailHeight(detail: View) {
        val method = miLinkAncHostSpecs
            .asSequence()
            .mapNotNull { spec ->
                detail.javaClass.declaredMethods.firstOrNull { candidate ->
                    candidate.name == spec.heightMethodName &&
                        candidate.parameterTypes.isEmpty() &&
                        candidate.returnType == Void.TYPE
                }
            }
            .firstOrNull()
            ?: throw NoSuchMethodException("MiLink headset height recompute method")
        method.isAccessible = true
        method.invoke(detail)
    }

    /** 按当前地址异步加载手动图、官方图或机型内置图，替换融合设备中心通用耳机图标。 */
    private fun syncMiLinkHeadsetIcon(root: View, route: HuaweiDeviceRoute) {
        val iconViews = listOf(
            "device_item",
            "circulate_headset_icon",
            "circulate_single_battery_headset_icon",
        )
            .mapNotNull { name -> root.findHostViewByIdName(name) as? ImageView }
            .distinct()
        if (iconViews.isEmpty()) return

        val request = miLinkHeadsetIconRequest(route)
        if (request == null) {
            iconViews.forEach(::restoreMiLinkHeadsetIcon)
            return
        }

        iconViews.forEach { imageView ->
            val state = synchronized(miLinkHeadsetIconViewStates) {
                miLinkHeadsetIconViewStates.getOrPut(imageView) {
                    MiLinkHeadsetIconViewState(
                        originalDrawable = imageView.drawable,
                        originalScaleType = imageView.scaleType,
                        originalAdjustViewBounds = imageView.adjustViewBounds,
                    )
                }
            }
            val identityChanged = state.requestedAddress != null && (
                !state.requestedAddress.equals(request.address, ignoreCase = true) ||
                    state.requestedRoute != request.route
                )
            state.requestedAddress = request.address
            state.requestedRoute = request.route
            state.requestedKey = request.key
            if (identityChanged) {
                imageView.setImageDrawable(state.originalDrawable)
                imageView.scaleType = state.originalScaleType
                imageView.adjustViewBounds = state.originalAdjustViewBounds
            }
        }

        val cached = miLinkHeadsetIconBitmapCache
        cached?.takeIf { it.key == request.key }?.let {
            iconViews.forEach { applyMiLinkHeadsetIcon(it, request.key, cached.bitmap) }
            return
        }
        // 同一台耳机的图片文件发生变化时继续显示旧缓存，避免异步解码期间闪回宿主通用图。
        cached?.takeIf {
            it.address.equals(request.address, ignoreCase = true) && it.route == request.route
        }?.let {
            iconViews.forEach { applyMiLinkHeadsetIcon(it, request.key, cached.bitmap) }
        }
        preloadMiLinkHeadsetIcon(root.context.applicationContext ?: root.context, request)
    }

    private fun miLinkHeadsetIconRequest(
        route: HuaweiDeviceRoute = currentHuaweiRoute(),
    ): MiLinkHeadsetIconRequest? {
        val address = currentAddress?.trim()?.uppercase()
        if (!route.isSupported || address.isNullOrBlank()) return null
        miLinkHeadsetIconRequestCache?.takeIf {
            it.address.equals(address, ignoreCase = true) && it.route == route
        }?.let { return it }
        val imagePreference = runCatching { PodImagePrefs.find(prefs, address) }.getOrNull()
        val key = listOf(
            address,
            route.name,
            imagePreference?.imagePath(PodImageResource.BOX).orEmpty(),
            imagePreference?.cloudImagePath(PodImageResource.BOX).orEmpty(),
        ).joinToString("|")
        return MiLinkHeadsetIconRequest(address, route, key).also {
            miLinkHeadsetIconRequestCache = it
        }
    }

    private fun preloadCurrentMiLinkHeadsetIcon() {
        val hostContext = context ?: return
        val request = miLinkHeadsetIconRequest() ?: return
        preloadMiLinkHeadsetIcon(hostContext, request)
    }

    private fun preloadMiLinkHeadsetIcon(
        hostContext: Context,
        request: MiLinkHeadsetIconRequest,
    ) {
        if (!acceptingViewCallbacks) return
        if (miLinkHeadsetIconBitmapCache?.key == request.key) return
        if (!miLinkHeadsetIconLoads.add(request.key)) return
        val generation = runtimeGeneration.get()
        runCatching { miLinkHeadsetIconExecutor.execute {
            if (!isRuntimeGenerationActive(generation)) {
                miLinkHeadsetIconLoads.remove(request.key)
                return@execute
            }
            val bitmap = try {
                runCatching {
                    PodImageLoader.loadBoxBitmap(
                        context = hostContext,
                        prefs = prefs,
                        address = request.address,
                        verifiedRoute = request.route,
                    )
                        ?.scaledForMiLinkHeadsetIcon()
                }.onFailure {
                    Log.w(TAG, "MiLink headset icon load failed route=${request.route}", it)
                }.getOrNull()
            } finally {
                miLinkHeadsetIconLoads.remove(request.key)
            }
            // 加载失败时保留宿主默认图标，等待下一次真实状态变化再重试，避免空转循环。
            if (bitmap == null || !isRuntimeGenerationActive(generation)) return@execute
            miLinkHeadsetIconBitmapCache = MiLinkHeadsetIconBitmapCache(
                address = request.address,
                route = request.route,
                key = request.key,
                bitmap = bitmap,
            )
            // 预加载可能早于详情页创建；完成后只刷新仍存活的详情 View。
            val roots = synchronized(headsetDetails) { headsetDetails.keys.toList() }
            roots.forEach { detail ->
                postTracked(detail) {
                    syncMiLinkHeadsetIcon(detail, currentHuaweiRoute())
                }
            }
        } }.onFailure {
            miLinkHeadsetIconLoads.remove(request.key)
            if (acceptingViewCallbacks) {
                Log.w(TAG, "MiLink headset icon task rejected route=${request.route}", it)
            }
        }
    }

    private fun applyMiLinkHeadsetIcon(imageView: ImageView, key: String, bitmap: Bitmap) {
        val requestedKey = synchronized(miLinkHeadsetIconViewStates) {
            miLinkHeadsetIconViewStates[imageView]?.requestedKey
        }
        // 即使宿主卡片暂未 attach 也先写入；否则异步解码恰好提前完成时会漏掉本轮替换。
        if (requestedKey != key) return
        val currentBitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
        if (currentBitmap === bitmap &&
            imageView.scaleType == ImageView.ScaleType.FIT_CENTER &&
            imageView.adjustViewBounds
        ) {
            return
        }
        miLinkHeadsetIconInternalRenderDepth.incrementAndGet()
        try {
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true
        } finally {
            miLinkHeadsetIconInternalRenderDepth.decrementAndGet()
        }
        Log.d(TAG, "MiLink headset icon replaced size=${bitmap.width}x${bitmap.height}")
    }

    private fun restoreMiLinkHeadsetIcon(imageView: ImageView) {
        val state = synchronized(miLinkHeadsetIconViewStates) {
            miLinkHeadsetIconViewStates.remove(imageView)
        } ?: return
        imageView.setImageDrawable(state.originalDrawable)
        imageView.scaleType = state.originalScaleType
        imageView.adjustViewBounds = state.originalAdjustViewBounds
    }

    private fun invalidateMiLinkHeadsetIcons() {
        miLinkHeadsetIconBitmapCache = null
        miLinkHeadsetIconRequestCache = null
        synchronized(miLinkHeadsetIconViewStates) {
            miLinkHeadsetIconViewStates.values.forEach { it.requestedKey = null }
        }
        val roots = Collections.newSetFromMap(IdentityHashMap<View, Boolean>()).apply {
            synchronized(headsetDetails) { addAll(headsetDetails.keys) }
            synchronized(ancCards) {
                ancCards.values.mapNotNull { it.detail.get() as? View }.forEach(::add)
            }
        }.toList()
        preloadCurrentMiLinkHeadsetIcon()
        val route = currentHuaweiRoute()
        roots.forEach { root -> syncMiLinkHeadsetIcon(root, route) }
    }

    private fun Bitmap.scaledForMiLinkHeadsetIcon(): Bitmap {
        val longestEdge = maxOf(width, height)
        if (longestEdge <= MILINK_HEADSET_ICON_MAX_DIMENSION) return this
        val scale = MILINK_HEADSET_ICON_MAX_DIMENSION.toFloat() / longestEdge
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun View.findHostViewByIdName(name: String): View? {
        val id = resources.getIdentifier(name, "id", context.packageName)
        return id.takeIf { it != 0 }?.let(::findViewById)
    }

    private fun isDescendantOf(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun safelyConfigureAncCard(
        card: Any?,
        reason: String,
        schedulePostRefresh: Boolean = true,
    ) {
        runCatching {
            configureAncCard(card, reason, schedulePostRefresh)
        }.onFailure {
            Log.w(TAG, "MiLink ANC card configure failed reason=$reason", it)
        }
    }

    private fun configureAncCard(
        card: Any?,
        reason: String,
        schedulePostRefresh: Boolean = true,
    ) {
        if (card == null) return
        loadState()
        val binding = ancCards[card] ?: return
        val route = resolvedAncCardRoute(
            binding = binding,
            forceResolve = reason.endsWith("-post"),
        )
        val detail = binding.detail.get() as? View
        val presentationRoute = if (route.isSupported) {
            route
        } else {
            detail
                ?.let(::collectTextViews)
                ?.map(TextView::getText)
                ?.let(::noAncMiLinkPresentationRoute)
                ?: HuaweiDeviceRoute.UNSUPPORTED
        }
        if (!route.isSupported && presentationRoute.isSupported) {
            Log.d(
                TAG,
                "MiLink no-ANC presentation fallback route=$presentationRoute reason=$reason",
            )
        }
        val nativeStateRefresh = reason in binding.hostSpec.refreshMethodNames.orEmpty()
        val ancStateOnlyRefresh = nativeStateRefresh ||
            reason == "anc-changed" ||
            reason == "anc-level-changed"
        detail?.let {
            if (!ancStateOnlyRefresh) {
                syncMiLinkHeadsetIcon(it, presentationRoute)
                syncUnsupportedAncHeading(it, presentationRoute)
                syncHostAncSectionHeight(it, presentationRoute, binding.hostSpec)
            }
            if (presentationRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
                val existingControls = findTaggedView(it, HUAWEI_EQUALIZER_CONTROLS_TAG)
                if (!ancStateOnlyRefresh || existingControls == null) {
                    syncFreeBuds6iSoundEffectControls(it)
                }
                if (!ancStateOnlyRefresh && currentHuaweiEqualizerSelectedId == null) {
                    requestHuaweiEqualizerState("anc-card-$reason", force = reason == "constructor")
                }
            } else if (!ancStateOnlyRefresh) {
                restoreHuaweiEqualizerControls(it)
            }
        }
        val clearView = resolveAncTransparencyView(card, binding, detail)
            ?: binding.clearView?.get()
        clearView?.let { binding.clearView = WeakReference(it) }
        if (route.isSupported && clearView == null && !binding.missingViewLogged) {
            binding.missingViewLogged = true
            Log.w(
                TAG,
                "MiLink ANC transparency view missing route=$route " +
                    "device=${currentName.orEmpty()}/${currentAddress.orEmpty()} " +
                    "card=${card.javaClass.name}",
            )
        }

        if (schedulePostRefresh) {
            // 两态机型会在下面摘除“通透”View，不能把二次初始化挂在将被摘除的子 View 上。
            (detail ?: clearView)?.let { target ->
                postTracked(target) {
                    safelyConfigureAncCard(card, "$reason-post", schedulePostRefresh = false)
                }
            }
        }

        if (!presentationRoute.isSupported) {
            restoreAncCardViews(card, binding, clearView, reason)
            return
        }
        configureAncCardViews(card, binding, presentationRoute, clearView, reason)
        if (shouldPrimeMiLinkAncCard(route, binding.hostSpec, reason)) {
            renderHostAncCardState(card, binding, "initial-$reason")
            if (reason == "constructor") {
                requestMiLinkAncState("anc-card-constructor")
            }
        }
    }

    private fun resolveAncTransparencyView(
        card: Any,
        binding: AncCardBinding,
        detail: View?,
    ): View? {
        if (binding.hostSpec.adapterName == "legacy") {
            return runCatching { getObjectField(card, "f") as? View }.getOrNull()
        }
        val selectCard = binding.hostSpec.selectCardIdName
            ?.let { idName -> detail?.findHostViewByIdName(idName) as? ViewGroup }
            ?: return null
        val label = collectTextViews(selectCard).firstOrNull { textView ->
            when (textView.text?.toString()?.trim()?.lowercase()) {
                "通透", "环境声", "transparency", "ambient sound" -> true
                else -> false
            }
        } ?: return null
        var candidate: View = label
        while (candidate.parent is View && candidate.parent !== selectCard) {
            candidate = candidate.parent as View
        }
        return candidate.takeIf { it.parent === selectCard }
    }

    private fun resolvedAncCardRoute(
        binding: AncCardBinding,
        forceResolve: Boolean,
    ): HuaweiDeviceRoute {
        val address = currentAddress?.trim()?.uppercase()
        val name = currentName?.trim()
        val fakeDeviceId = fakeDeviceId()
        val activeRoute = currentHuaweiRoute()
        if (
            !forceResolve &&
            binding.routeResolved &&
            binding.resolvedAddress == address &&
            binding.resolvedName == name &&
            binding.resolvedFakeDeviceId == fakeDeviceId &&
            binding.resolvedActiveRoute == activeRoute
        ) {
            return binding.route
        }
        return routeForAncCardDetail(
            detail = binding.detail.get(),
            allowActiveFallback = true,
        ).also { route ->
            binding.route = route
            binding.resolvedAddress = address
            binding.resolvedName = name
            binding.resolvedFakeDeviceId = fakeDeviceId
            binding.resolvedActiveRoute = activeRoute
            binding.routeResolved = true
        }
    }

    private fun configureAncCardViews(
        card: Any,
        binding: AncCardBinding,
        route: HuaweiDeviceRoute,
        clearView: View?,
        reason: String,
    ) {
        if (!route.isSupported) return
        val detail = binding.detail.get() as? View
        val hostSelectCard = binding.hostSpec.selectCardIdName
            ?.let { idName -> detail?.findHostViewByIdName(idName) }
        if (!route.supportsAnc && hostSelectCard != null) {
            binding.capabilityContainer = WeakReference(hostSelectCard)
            setCapabilityViewVisible(hostSelectCard, false)
            if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                detail?.let(::applyFreeClip2CardPresentation)
            }
            Log.d(
                TAG,
                "MiLink ANC card hidden route=$route reason=$reason " +
                    "adapter=${binding.hostSpec.adapterName}",
            )
            return
        }
        val modeRow = capabilityParent(clearView)
        val ancContainer = capabilityParent(modeRow)
        val capabilityContainer: View? = ancContainer ?: modeRow ?: clearView

        if (!route.supportsAnc) {
            if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
                detail?.let(::applyFreeClip2CardPresentation)
            }
            // 只隐藏 ANC 按钮所在行。祖先容器可能同时承载详情页点击/展开入口，不能修改。
            val ancModeRow = modeRow ?: clearView
            ancModeRow?.let { binding.capabilityContainer = WeakReference(it) }
            setCapabilityViewVisible(ancModeRow, false)
            ancContainer?.let { findTaggedView(it, ANC_SUBMODE_SELECTOR_TAG)?.visibility = View.GONE }
            Log.d(TAG, "MiLink ANC card hidden route=$route reason=$reason card=${card.javaClass.name}")
            return
        }

        capabilityContainer?.let { binding.capabilityContainer = WeakReference(it) }
        setCapabilityViewVisible(modeRow, true)
        setCapabilityViewVisible(capabilityContainer, true)
        setCapabilityViewVisible(
            clearView,
            route.supportsTransparency,
            detachWhenHidden = shouldDetachMiLinkTransparency(route),
        )
        if (shouldDetachMiLinkTransparency(route)) {
            (hostSelectCard as? ViewGroup)?.let { selectCard ->
                bindTwoStateAncButtons(selectCard, reason)
            }
        }
        if (modeRow == null || ancContainer == null) return

        val selector = findTaggedView(ancContainer, ANC_SUBMODE_SELECTOR_TAG) as? HuaweiAncSubModeSelectorView
        val supportsCurrentSubMode = when (currentAnc) {
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus -> route.supportsDiscreteAncLevels
            NoiseControlMode.TRANSPARENCY.broadcastStatus -> route.supportsTransparency
            else -> false
        }
        if (!supportsCurrentSubMode) {
            selector?.visibility = View.GONE
            return
        }

        val options = selectorOptions(route, currentAnc)
        if (options.isEmpty()) {
            selector?.visibility = View.GONE
            return
        }
        val selected = selectionForStatus(route, currentAnc)?.subMode ?: options.first().value
        val targetSelector = selector ?: HuaweiAncSubModeSelectorView(ancContainer.context) { subMode ->
            val activeRoute = currentHuaweiRoute()
            val selection = selectionForStatus(activeRoute, currentAnc, subMode)
                ?: return@HuaweiAncSubModeSelectorView
            dispatchAncSelection(selection, ancContainer.context, "submode-selected")
        }.apply {
            tag = ANC_SUBMODE_SELECTOR_TAG
            val insertIndex = (ancContainer.indexOfChild(modeRow) + 1)
                .coerceIn(0, ancContainer.childCount)
            ancContainer.addView(
                this,
                insertIndex,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        targetSelector.render(options, selected, isDarkSurface(ancContainer))
        targetSelector.visibility = View.VISIBLE
        Log.d(TAG, "MiLink ANC submode configured route=$route mode=$currentAnc reason=$reason")
    }

    private fun bindTwoStateAncButtons(selectCard: ViewGroup, reason: String) {
        val boundStatuses = linkedSetOf<Int>()
        collectTextViews(selectCard).forEach { label ->
            val status = miLinkTwoStateAncStatusForLabel(label.text?.toString()) ?: return@forEach
            val button = directChildUnder(selectCard, label) ?: return@forEach
            synchronized(boundAncButtonListeners) {
                boundAncButtonListeners.putIfAbsent(
                    button,
                    MiLinkBoundClickState(
                        listener = currentOnClickListener(button),
                        clickable = button.isClickable,
                    ),
                )
            }
            button.setOnClickListener { clicked ->
                val activeRoute = currentHuaweiRoute()
                val activeStatus = miLinkBoundAncStatusForRoute(
                    activeRoute,
                    label.text?.toString(),
                )
                if (activeStatus == null) {
                    Log.w(
                        TAG,
                        "MiLink bound ANC click ignored for route=$activeRoute status=$status",
                    )
                    return@setOnClickListener
                }
                val selection = selectionForStatus(activeRoute, activeStatus)
                    ?: return@setOnClickListener
                Log.i(
                    TAG,
                    "MiLink bound ANC button handled status=$activeStatus " +
                        "label=${label.text} reason=$reason",
                )
                dispatchAncSelection(selection, clicked.context, "bound-anc-button")
            }
            button.isClickable = true
            boundStatuses += status
        }
        if (
            boundStatuses != setOf(
                NoiseControlMode.OFF.broadcastStatus,
                NoiseControlMode.NOISE_CANCELLATION.broadcastStatus,
            )
        ) {
            Log.w(
                TAG,
                "MiLink two-state ANC buttons incomplete statuses=$boundStatuses reason=$reason",
            )
        }
    }

    private fun currentOnClickListener(view: View): View.OnClickListener? = runCatching {
        val listenerInfo = getObjectField(view, "mListenerInfo") ?: return@runCatching null
        getObjectField(listenerInfo, "mOnClickListener") as? View.OnClickListener
    }.getOrNull()

    private fun selectorOptions(
        route: HuaweiDeviceRoute,
        status: Int,
    ): List<HuaweiAncSubModeSelectorView.Option> = when (status) {
        NoiseControlMode.NOISE_CANCELLATION.broadcastStatus -> route.ancLevelOptions.map { option ->
            val label = when (option.level) {
                HuaweiAncLevel.ADAPTIVE -> if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5) {
                    moduleString(R.string.freebuds_pro5_anc_level_adaptive, "智慧双擎降噪")
                } else {
                    moduleString(R.string.anc_level_adaptive, "智慧动态")
                }
                HuaweiAncLevel.LIGHT -> moduleString(R.string.anc_level_light, "轻度")
                HuaweiAncLevel.BALANCED -> moduleString(R.string.anc_level_balanced, "均衡")
                HuaweiAncLevel.DEEP -> moduleString(R.string.anc_level_deep, "深度")
            }
            HuaweiAncSubModeSelectorView.Option(option.protocolValue, label)
        }
        NoiseControlMode.TRANSPARENCY.broadcastStatus -> transparencyOptions(route)
        else -> emptyList()
    }

    private fun transparencyOptions(
        route: HuaweiDeviceRoute,
    ): List<HuaweiAncSubModeSelectorView.Option> {
        val standard = route.defaultTransparencySubMode ?: return emptyList()
        return buildList {
            if (standard in route.transparencySubModes) {
                add(
                    HuaweiAncSubModeSelectorView.Option(
                        standard,
                        moduleString(R.string.transparency_standard, "普通"),
                    ),
                )
            }
            if (0x01 in route.transparencySubModes && 0x01 != standard) {
                add(
                    HuaweiAncSubModeSelectorView.Option(
                        0x01,
                        moduleString(R.string.transparency_voice, "人声增强"),
                    ),
                )
            }
            if (0x04 in route.transparencySubModes) {
                add(
                    HuaweiAncSubModeSelectorView.Option(
                        0x04,
                        moduleString(R.string.transparency_adaptive, "智慧动态透传"),
                    ),
                )
            }
        }
    }

    private fun moduleString(resId: Int, fallback: String): String {
        val hostContext = context ?: return fallback
        return runCatching {
            if (!ModuleResourceResolver.isCurrentModuleBuild(hostContext)) return fallback
            ModuleResourceResolver.resources(hostContext)?.getString(resId) ?: fallback
        }.getOrDefault(fallback)
    }

    private fun findTaggedView(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTaggedView(view.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    private fun capabilityParent(view: View?): ViewGroup? {
        view ?: return null
        return view.parent as? ViewGroup ?: hiddenCapabilityViews[view]?.parent
    }

    private fun restoreAncCardViews(
        card: Any,
        binding: AncCardBinding,
        clearView: View?,
        reason: String,
    ) {
        val targetClearView = clearView ?: binding.clearView?.get()
        val modeRow = capabilityParent(targetClearView)
        val ancContainer = capabilityParent(modeRow)
        val capabilityContainer = binding.capabilityContainer?.get()
            ?: ancContainer
            ?: modeRow
            ?: targetClearView

        val selectorHost = ancContainer ?: capabilityContainer
        val selectorRemoved = selectorHost
            ?.let { findTaggedView(it, ANC_SUBMODE_SELECTOR_TAG) }
            ?.let { selector ->
                (selector.parent as? ViewGroup)?.removeView(selector)
                true
            } == true
        val clearRestored = restoreCapabilityView(targetClearView) != null
        val containerRestored = if (capabilityContainer !== targetClearView) {
            restoreCapabilityView(capabilityContainer) != null
        } else {
            false
        }
        if (selectorRemoved || clearRestored || containerRestored) {
            Log.d(
                TAG,
                "MiLink ANC card restored route=${binding.route} reason=$reason card=${card.javaClass.name}",
            )
        }
    }

    private fun restoreCapabilityView(view: View?): HiddenCapabilityView? {
        view ?: return null
        val hiddenState = hiddenCapabilityViews.remove(view) ?: return null
        if (view.parent == null) {
            val index = hiddenState.index.coerceIn(0, hiddenState.parent.childCount)
            hiddenState.parent.addView(view, index)
        }
        view.layoutParams = view.layoutParams?.apply {
            width = hiddenState.width
            height = hiddenState.height
        }
        view.visibility = hiddenState.visibility
        view.isEnabled = hiddenState.isEnabled
        view.isClickable = hiddenState.isClickable
        view.requestLayout()
        hiddenState.parent.requestLayout()
        return hiddenState
    }

    private fun setCapabilityViewVisible(
        view: View?,
        visible: Boolean,
        detachWhenHidden: Boolean = false,
    ) {
        view ?: return
        if (visible) {
            val hiddenState = restoreCapabilityView(view)
            val targetEnabled = hiddenState?.isEnabled ?: true
            val targetClickable = hiddenState?.isClickable ?: view.isClickable
            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
            if (view.isEnabled != targetEnabled) view.isEnabled = targetEnabled
            if (view.isClickable != targetClickable) view.isClickable = targetClickable
            return
        }

        val parent = view.parent as? ViewGroup
        if (parent != null && !hiddenCapabilityViews.containsKey(view)) {
            val layoutParams = view.layoutParams
            hiddenCapabilityViews[view] = HiddenCapabilityView(
                parent = parent,
                index = parent.indexOfChild(view).coerceAtLeast(0),
                width = layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                height = layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                visibility = view.visibility,
                isEnabled = view.isEnabled,
                isClickable = view.isClickable,
            )
        }
        if (view.visibility != View.GONE) view.visibility = View.GONE
        if (view.isEnabled) view.isEnabled = false
        if (view.isClickable) view.isClickable = false
        // 已经摘除的 View 再次进入宿主刷新时 parent 为 null；不能把 null===null
        // 当作仍在原父布局中，否则会在第二次刷新时触发 removeView 空指针。
        if (shouldRemoveMiLinkCapabilityView(
                detachWhenHidden = detachWhenHidden,
                parentAvailable = parent != null,
                stillInParent = view.parent === parent,
            )
        ) {
            parent?.removeView(view)
        }
    }

    private fun isDarkSurface(view: View): Boolean {
        var current: View? = view
        repeat(4) {
            val color = when (val background = current?.background) {
                is ColorDrawable -> background.color
                is GradientDrawable -> background.color?.defaultColor
                else -> null
            }
            if (color != null && Color.alpha(color) > 32) {
                val luminance = (
                    Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114
                    ) / 1000
                return luminance < 128
            }
            current = current?.parent as? View
        }
        return true
    }

    private fun routeForMethodTarget(args: List<Any?>, instance: Any?): HuaweiDeviceRoute {
        args.forEach { arg ->
            when (arg) {
                is BluetoothDevice -> routeForDevice(arg).takeIf { it.isSupported }?.let { return it }
                is String -> routeForAddress(arg).takeIf { it.isSupported }?.let { return it }
            }
        }
        return routeForHeadsetInfo(instance).takeIf { it.isSupported } ?: currentHuaweiRoute()
    }

    private fun routeForAncCardDetail(
        detail: Any?,
        allowActiveFallback: Boolean = false,
    ): HuaweiDeviceRoute {
        detail ?: return HuaweiDeviceRoute.UNSUPPORTED
        val activeRoute = currentHuaweiRoute()
        if (!activeRoute.isSupported) return HuaweiDeviceRoute.UNSUPPORTED
        (detail as? View)?.let(::cachedRouteForHeadsetDetail)?.let { return it }

        val activeAddress = currentAddress
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.uppercase()
        val candidates = collectAncCardIdentityCandidates(detail)
        val candidateAddresses = candidates.mapNotNull { candidate ->
            when (candidate) {
                is BluetoothDevice -> runCatching { candidate.address }
                    .getOrNull()
                    ?.trim()
                    ?.uppercase()
                    ?.takeIf(bluetoothAddressPattern::matches)
                is String -> candidate.trim().uppercase().takeIf(bluetoothAddressPattern::matches)
                else -> null
            }
        }.distinct()
        // 通常必须由蓝牙地址证明身份。部分 HyperOS 版本的 ANC 卡片没有暴露 MAC，
        // 此时仅允许“当前会话 + 唯一 ANC 卡片”这一条窄回退；FreeClip2 音效入口不使用回退。
        if (candidateAddresses.isEmpty()) {
            val liveAncCardCount = synchronized(ancCards) {
                val liveDetails = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                ancCards.values.forEach { binding ->
                    binding.detail.get()?.let(liveDetails::add)
                }
                liveDetails.size
            }
            return activeRoute.takeIf {
                allowActiveFallback && shouldUseActiveMiLinkAncCardFallback(
                    activeRoute = activeRoute,
                    activeAddress = activeAddress,
                    sessionConfirmed = currentSessionConfirmed,
                    candidateAddressCount = 0,
                    liveAncCardCount = liveAncCardCount,
                )
            } ?: HuaweiDeviceRoute.UNSUPPORTED
        }
        if (activeAddress != null) {
            return activeRoute.takeIf {
                candidateAddresses.size == 1 && candidateAddresses.single() == activeAddress
            }
                ?: HuaweiDeviceRoute.UNSUPPORTED
        }

        // 极少数恢复场景尚未拿到当前地址时，只接受已建立地址绑定且机型与当前会话一致的卡片。
        return candidateAddresses.singleOrNull()
            ?.let { resolveHuaweiDeviceRoute(it, null) }
            ?.takeIf { it.isSupported && it == activeRoute }
            ?: HuaweiDeviceRoute.UNSUPPORTED
    }

    /** 复用 ANC 卡片已经完成的严格身份解析，避免详情动画期间重复遍历宿主对象图。 */
    private fun cachedRouteForHeadsetDetail(detail: View): HuaweiDeviceRoute? {
        val address = currentAddress?.trim()?.uppercase()
        val name = currentName?.trim()
        val fakeDeviceId = fakeDeviceId()
        val activeRoute = currentHuaweiRoute()
        return synchronized(ancCards) {
            ancCards.values.firstNotNullOfOrNull { binding ->
                binding.route.takeIf {
                    binding.detail.get() === detail &&
                        binding.routeResolved &&
                        binding.resolvedAddress == address &&
                        binding.resolvedName == name &&
                        binding.resolvedFakeDeviceId == fakeDeviceId &&
                        binding.resolvedActiveRoute == activeRoute &&
                        it.isSupported
                }
            }
        }
    }

    private fun collectAncCardIdentityCandidates(root: Any): List<Any> {
        val candidates = mutableListOf<Any>()
        val queue = java.util.ArrayDeque<Pair<Any, Int>>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        queue.add(root to 0)

        while (queue.isNotEmpty() && visited.size < 96) {
            val (value, depth) = queue.removeFirst()
            if (!visited.add(value)) continue
            when (value) {
                is String, is BluetoothDevice -> {
                    candidates += value
                    continue
                }
                is Intent -> {
                    value.extras?.keySet()?.forEach { key ->
                        @Suppress("DEPRECATION")
                        val extra = runCatching { value.extras?.get(key) }.getOrNull()
                        enqueueAncIdentityValue(queue, extra, depth + 1)
                    }
                    value.dataString?.let { candidates += it }
                    continue
                }
                is Iterable<*> -> {
                    value.take(12).forEach { enqueueAncIdentityValue(queue, it, depth + 1) }
                    continue
                }
                is Map<*, *> -> {
                    value.entries.take(12).forEach { entry ->
                        enqueueAncIdentityValue(queue, entry.key, depth + 1)
                        enqueueAncIdentityValue(queue, entry.value, depth + 1)
                    }
                    continue
                }
            }
            if (depth >= 2 || !shouldInspectAncIdentityHolder(value, depth)) continue

            if (shouldInvokeAncIdentityGetters(value)) {
                cachedAncIdentityGetterMethods(value.javaClass).forEach { method ->
                    val nested = runCatching { method.invoke(value) }.getOrNull()
                    enqueueAncIdentityValue(queue, nested, depth + 1)
                }
            }

            var type: Class<*>? = value.javaClass
            while (type != null && type != Any::class.java) {
                type.declaredFields
                    .asSequence()
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .take(24)
                    .forEach { field ->
                        val nested = runCatching {
                            field.isAccessible = true
                            field.get(value)
                        }.getOrNull()
                        enqueueAncIdentityValue(queue, nested, depth + 1)
                    }
                type = type.superclass
            }
        }
        return candidates
    }

    private fun shouldInvokeAncIdentityGetters(value: Any): Boolean {
        if (value is View) return false
        val simpleName = value.javaClass.simpleName.lowercase()
        if (simpleName.endsWith("info") || simpleName.endsWith("device")) return true
        return listOf("controller", "manager", "client", "service").none(simpleName::contains)
    }

    private fun cachedAncIdentityGetterMethods(type: Class<*>): List<Method> =
        ancIdentityGetterMethods.computeIfAbsent(type) {
            buildList {
                var current: Class<*>? = type
                while (current != null && current != Any::class.java) {
                    current.declaredMethods
                        .asSequence()
                        .filter { method ->
                            !Modifier.isStatic(method.modifiers) &&
                                method.parameterTypes.isEmpty() &&
                                method.name in ancIdentityGetterNameSet
                        }
                        .forEach { method ->
                            runCatching {
                                method.isAccessible = true
                                add(method)
                            }
                        }
                    current = current.superclass
                }
            }.distinctBy { method -> method.name to method.returnType }
        }

    private fun shouldInspectAncIdentityHolder(value: Any, depth: Int): Boolean {
        if (depth == 0) return true
        if (value is View || value is Context || value is Number || value is Boolean || value is Class<*>) {
            return false
        }
        return !value.javaClass.isEnum
    }

    private fun enqueueAncIdentityValue(
        queue: java.util.ArrayDeque<Pair<Any, Int>>,
        value: Any?,
        depth: Int,
    ) {
        value ?: return
        queue.add(value to depth)
    }

    private fun isTransparencyCapabilityMethod(methodName: String): Boolean {
        val name = methodName.lowercase()
        return "support" in name ||
            "capability" in name ||
            "available" in name ||
            "hastransparent" in name
    }

    private fun hookHeadsetCirculationExperiment() {
        runCatching {
            val circulateParamClass = findClass("com.miui.circulate.api.bean.CirculateParam")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
                    "circulateService",
                    List::class.java,
                    List::class.java,
                    circulateParamClass
                )
            ) {
                val param = args[2] ?: return@hookBefore
                val serviceInfo = runCatching { getObjectField(param, "circulateServiceInfo") }.getOrNull()
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                lastHeadsetServiceClient = instance
                lastHeadsetServiceInfo = serviceInfo
                cacheHeadsetDeviceInfo(instance, serviceInfo)
                Log.w(
                    TAG,
                    "MiLink circulate experiment circulateService " +
                        "service=${describeCirculateService(serviceInfo)} " +
                        "returnHosts=${describeCollection(args[0])} targetHosts=${describeCollection(args[1])}"
                )

                val wasLocked = isHeadsetCirculationLocked(instance)
                if (wasLocked) {
                    clearHeadsetCirculationLock(instance, "before circulateService")
                }
                proceedWithArgs(args[0], args[1], param)
                clearHeadsetCirculationLock(instance, if (wasLocked) "after retry circulateService" else "after circulateService")
            }
        }.onFailure { Log.w(TAG, "hook HeadsetServiceClient.circulateService experiment skipped", it) }

        runCatching {
            val headsetHostClass = findClass("com.miui.headset.api.HeadsetHost")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient\$3",
                    "onHeadsetHostUpdate",
                    Int::class.javaPrimitiveType!!,
                    headsetHostClass
                )
            ) {
                val type = args[0] as? Int ?: return@hookBefore
                if (type != 3 || !isCurrentHuaweiHeadset()) return@hookBefore
                val hostId = headsetHostId(args[1])
                if (shouldSuppressCirculationActiveLost(hostId)) {
                    Log.w(TAG, "MiLink circulate experiment suppress target ActiveHeadsetLost host=$hostId")
                    this.result = null
                    return@hookBefore
                }
                Log.w(TAG, "MiLink circulate experiment allow ActiveHeadsetLost host=$hostId target=$circulationTargetHostId")
            }
        }.onFailure {
            logOptionalMiLinkHookSkipped(
                "HeadsetServiceClient\$3.onHeadsetHostUpdate experiment",
                it,
            )
        }

        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.runtime.AncBatteryController\$mmaCallback\$1",
                    "onConnectMmaStateChanged",
                    BluetoothDevice::class.java,
                    Boolean::class.javaPrimitiveType!!
                )
            ) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                val connected = args[1] as? Boolean ?: return@hookBefore
                if (connected || !isHuaweiPod(device)) return@hookBefore
                Log.w(TAG, "MiLink circulate experiment suppress MMA disconnect device=${device.name}/${device.address}")
                this.result = null
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.mmaCallback experiment skipped", it) }

        runCatching {
            hookAfter(
                findMethod(
                    "com.miui.headset.runtime.HeadsetLocalServiceImpl\$profileStubAdapter\$1",
                    "connect",
                    Long::class.javaPrimitiveType!!,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
            ) {
                val address = args[3] as? String ?: return@hookAfter
                if (!isHuaweiAddress(address) && !isCurrentHuaweiHeadset()) return@hookAfter
                if (result == 100) return@hookAfter
                val targetHostId = args[2] as? String
                circulationTargetHostId = targetHostId
                circulationSignalRewriteUntilMs = System.currentTimeMillis() + 15_000L
                Log.w(TAG, "MiLink circulate experiment force connect success host=$targetHostId address=$address original=$result")
                this.result = 100
            }
        }.onFailure { Log.w(TAG, "hook HeadsetLocalServiceImpl.profileStubAdapter.connect experiment skipped", it) }

        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.api.RequestInvokeSync",
                    "signal",
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                )
            ) {
                val resultCode = args[1] as? Int ?: return@hookBefore
                if (resultCode != CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED || !isCirculationRewriteActive()) return@hookBefore
                Log.w(TAG, "MiLink circulate experiment rewrite async signal requestId=${args[0]} result=2011->100 target=$circulationTargetHostId")
                circulationSignalRewriteUntilMs = System.currentTimeMillis() + 30_000L
                proceedWithArgs(args[0], 100)
            }
        }.onFailure { Log.w(TAG, "hook RequestInvokeSync.signal experiment skipped", it) }

        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            val serviceInfoClass = findClass("com.miui.circulate.api.service.CirculateServiceInfo")
            hookBefore(
                findMethod(
                    "com.miui.circulate.world.headset.HeadsetContentManager",
                    "g0",
                    Int::class.javaPrimitiveType!!,
                    deviceInfoClass,
                    serviceInfoClass
                )
            ) {
                val state = args[0] as? Int ?: return@hookBefore
                val serviceInfo = args[2] ?: return@hookBefore
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                if (state == CIRCULATE_RESULT_ACTIVE_CHANGED_FAILED) {
                    Log.w(TAG, "MiLink circulate experiment suppress UI rollback state=$state service=$serviceInfo")
                    this.result = null
                    return@hookBefore
                }
                if (state == CIRCULATE_STATE_CONNECTING && markCirculateServiceConnected(serviceInfo, "g0")) {
                    circulationUiCompletedUntilMs = System.currentTimeMillis() + 30_000L
                    Log.w(TAG, "MiLink circulate experiment patch UI target connected state=3->2 service=$serviceInfo")
                    proceedWithArgs(CIRCULATE_STATE_CONNECTED, args[1], serviceInfo)
                }
            }
        }.onFailure { logOptionalMiLinkHookSkipped("HeadsetContentManager.g0 experiment", it) }

        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            hookBefore(
                findMethod(
                    "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
                    "clientConnect",
                    deviceInfoClass,
                    deviceInfoClass
                )
            ) {
                if (!isCurrentHuaweiHeadset()) return@hookBefore
                val targetDevice = args[0]
                val headsetDevice = args[1]
                val ret = proceedWithArgs(targetDevice, headsetDevice) as? Int
                Log.w(
                    TAG,
                    "MiLink circulate experiment clientConnect ret=$ret " +
                        "target=${describeCirculateDevice(targetDevice)} headset=${describeCirculateDevice(headsetDevice)}"
                )
            }
        }.onFailure { logOptionalMiLinkHookSkipped("HeadsetServiceClient.clientConnect experiment", it) }

        runCatching {
            val ballClass = findClass("com.miui.circulate.world.view.ball.l")
            val deviceCardClass = findClass("e8.j")
            val headsetCardClass = findClass("com.miui.circulate.world.headset.ui.g")
            hookBefore(
                findMethod(
                    "com.miui.circulate.world.headset.HeadsetContentManager",
                    "m0",
                    ballClass,
                    deviceCardClass,
                    headsetCardClass
                )
            ) {
                val target = args[1] ?: return@hookBefore
                val headset = args[2] ?: return@hookBefore
                val serviceInfo = runCatching { callMethod(headset, "S") }.getOrNull()
                if (!isTargetCirculateHeadset(serviceInfo)) return@hookBefore
                lastHeadsetServiceInfo = serviceInfo
                val origin = runCatching { callMethod(headset, "P") }.getOrNull()
                if (isWindowsCirculateDevice(target)) {
                    rememberWindowsCirculateDevice(target)
                }
                Log.w(
                    TAG,
                    "MiLink circulate experiment m0 target=${circulateDeviceName(target)} local=${isLocalCirculateDevice(target)} " +
                        "origin=${circulateDeviceName(origin)} originLocal=${isLocalCirculateDevice(origin)} " +
                        "targetDetail=${describeCirculateCard(target)} originDetail=${describeCirculateCard(origin)} " +
                        "service=${describeCirculateService(serviceInfo)}"
                )
                markCirculateServiceConnected(serviceInfo, "m0")
                if (origin == null || !isLocalCirculateDevice(target) || isLocalCirculateDevice(origin)) return@hookBefore
                if (scheduleReturnToLocalViaClient(target, headset, serviceInfo)) {
                    Log.w(
                        TAG,
                        "MiLink circulate experiment scheduled direct return to local origin=${circulateDeviceName(origin)} target=${circulateDeviceName(target)}"
                    )
                    this.result = true
                    return@hookBefore
                }
                val ret = callMethod(instance, "D", origin, target, headset, true) as? Int ?: return@hookBefore
                Log.w(
                    TAG,
                    "MiLink circulate experiment force return to local ret=$ret origin=${circulateDeviceName(origin)} target=${circulateDeviceName(target)}"
                )
                this.result = ret == 0
            }
        }.onFailure { logOptionalMiLinkHookSkipped("HeadsetContentManager.m0 return experiment", it) }
    }

    private fun hookWindowsHeadsetCirculationCapability() {
        runCatching {
            val deviceInfoClass = findClass("com.miui.circulate.api.service.CirculateDeviceInfo")
            deviceInfoClass.declaredMethods
                .filter { method ->
                    method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        hookAfter(method) {
                            val serviceType = args[0] as? Int ?: return@hookAfter
                            if (!shouldPatchWindowsHeadsetService(instance, serviceType)) return@hookAfter
                            val patched = patchedWindowsHeadsetServiceResult(method.returnType, result)
                                ?: return@hookAfter
                            Log.w(
                                TAG,
                                "MiLink PC circulate patch ${method.name} service=$serviceType " +
                                    "original=$result patched=$patched target=${describeCirculateDevice(instance)}"
                            )
                            this.result = patched
                        }
                    }.onFailure {
                        Log.w(TAG, "hook CirculateDeviceInfo.${method.name} PC service skipped", it)
                    }
                }
        }.onFailure { Log.w(TAG, "hook CirculateDeviceInfo PC service capability skipped", it) }
    }

    private fun hookWindowsHeadsetBondState() {
        listOf(
            "com.miui.headset.api.Query",
            "com.miui.headset.api.HeadsetClient\$queryProxyAdapter\$1",
            "com.miui.headset.runtime.RemoteProtocol\$Proxy",
            "com.miui.headset.runtime.QueryLocal",
            "com.miui.headset.runtime.QueryServer",
            "com.miui.headset.runtime.HeadsetRemoteImpl",
            "com.miui.headset.runtime.QueryLocal\$getBondStateWithTargetHost\$1"
        ).forEach { className ->
            runCatching {
                findClass(className).declaredMethods
                    .filterNot { method -> Modifier.isAbstract(method.modifiers) }
                    .filter { method ->
                        method.name == "getBondStateWithTargetHost" || method.name == "invoke"
                    }
                    .filter { method ->
                        method.parameterTypes.size == 2 &&
                            method.parameterTypes[0] == String::class.java &&
                            method.parameterTypes[1] == String::class.java
                    }
                    .forEach { method ->
                        method.isAccessible = true
                        hookAfter(method) {
                            val targetAddress = args[0] as? String
                            val targetHostId = args[1] as? String
                            if (!shouldPatchWindowsBondState(targetAddress, targetHostId)) return@hookAfter
                            if (result == HEADSET_BOND_BONDED) return@hookAfter
                            Log.w(
                                TAG,
                                "MiLink PC circulate patch bond state method=${method.declaringClass.name}.${method.name} " +
                                    "targetAddress=$targetAddress targetHostId=$targetHostId original=$result patched=$HEADSET_BOND_BONDED"
                            )
                            this.result = HEADSET_BOND_BONDED
                        }
                    }
            }.onFailure { logOptionalMiLinkHookSkipped("$className PC bond state", it) }
        }
    }

    private fun logOptionalMiLinkHookSkipped(feature: String, error: Throwable) {
        when (error) {
            is ClassNotFoundException,
            is NoSuchMethodException,
            -> Log.i(TAG, "optional hook unavailable feature=$feature reason=${error.javaClass.simpleName}")
            else -> Log.w(TAG, "hook $feature skipped", error)
        }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        loadState()
        preloadCurrentMiLinkHeadsetIcon()
        val filter = IntentFilter().apply {
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_CONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED)
            addHuaweiPodsAction(HuaweiPodsAction.ACTION_CONFIG_CHANGED)
            addAction(VOLUME_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedIntent = intent ?: return
                when (HuaweiPodsAction.canonical(receivedIntent.action)) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamType = receivedIntent.getIntExtra(
                            EXTRA_VOLUME_STREAM_TYPE,
                            AudioManager.STREAM_MUSIC,
                        )
                        if (streamType == AudioManager.STREAM_MUSIC) {
                            val volumeIndex = receivedIntent.getIntExtra(
                                EXTRA_VOLUME_STREAM_VALUE,
                                -1,
                            )
                            refreshMiLinkVolumeLabels(
                                volumeIndex.takeIf { it >= 0 }
                                    ?.let(::mediaVolumePercentForIndex),
                            )
                        }
                    }
                    HuaweiPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        val configuredRoute = resolveHuaweiDeviceRoute(currentAddress, currentName)
                        if (configuredRoute.isSupported && configuredRoute != currentRoute) {
                            currentRoute = configuredRoute
                            resetAncState(configuredRoute)
                            resetFreeClip2AudioState()
                            resetHuaweiEqualizerState()
                            saveState(context)
                        }
                        refreshAncCards("config-changed")
                        refreshFreeClip2AudioEffectSections("config-changed")
                        requestHuaweiEqualizerState("config-changed")
                        refreshLowLatencyFromPrefs()
                        refreshLowLatencyQuickCard()
                    }
                    HuaweiPodsAction.ACTION_POD_IMAGES_CHANGED -> {
                        val changedAddress = receivedIntent.getStringExtra(
                            PodImageChangeNotifier.EXTRA_ADDRESS,
                        )
                        if (changedAddress == null || changedAddress.equals(currentAddress, ignoreCase = true)) {
                            invalidateMiLinkHeadsetIcons()
                        }
                    }
                    HuaweiPodsAction.ACTION_PODS_CONNECTED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        saveState(context)
                        preloadCurrentMiLinkHeadsetIcon()
                        refreshAncCards("connected")
                        refreshFreeClip2AudioEffectSections("connected")
                        refreshMiLinkVolumeLabels()
                        requestFreeClip2AudioState("connected")
                        requestHuaweiEqualizerState("connected", force = true)
                        refreshLowLatencyFromPrefs()
                        refreshLowLatencyQuickCard()
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> {
                        if (!forgetCurrentDevice(receivedIntent)) return
                        saveState(context)
                        refreshAncCards("disconnected")
                        refreshFreeClip2AudioEffectSections("disconnected")
                        refreshMiLinkVolumeLabels()
                        refreshLowLatencyFromPrefs()
                        refreshLowLatencyQuickCard()
                    }
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        currentBattery = (
                            receivedIntent.batteryStatusFromExtras()
                                ?: receivedIntent.parcelableStatus()
                                ?: currentBattery
                            ).let(::normalizeBatteryAvailabilityForCurrentRoute)
                        saveState(context)
                    }
                    HuaweiPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        val route = currentHuaweiRoute()
                        val status = receivedIntent.getIntExtra("status", currentAnc)
                        val requestedSubMode = receivedIntent.getIntExtra("submode", -1)
                            .takeIf { receivedIntent.hasExtra("submode") && it >= 0 }
                        val confirmedSelection = status
                            .takeIf { it in setOf(1, 2, 3) }
                            ?.let { selectionForStatus(route, it, requestedSubMode) }
                        if (
                            route.supportsAncStateReadback &&
                            confirmedSelection != null &&
                            !ancPendingGate.shouldAcceptConfirmation(
                                confirmedSelection,
                                SystemClock.elapsedRealtime(),
                            )
                        ) {
                            Log.i(
                                TAG,
                                "MiLink stale ANC confirmation deferred selection=$confirmedSelection " +
                                    "pending=${ancPendingGate.current()}",
                            )
                            return
                        }
                        when {
                            !route.supportsAnc -> applyAncSelection(MiLinkAncSelection(1))
                            status == 3 && !route.supportsTransparency -> Unit
                            confirmedSelection != null -> applyAncSelection(confirmedSelection)
                            status in setOf(5, 6, 7, 8) -> currentAnc = status
                        }
                        saveState(context)
                        refreshAncPresentation("anc-changed", refreshDetails = false)
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_ANC_LEVEL_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        val route = currentHuaweiRoute()
                        if (!route.supportsDiscreteAncLevels || currentAnc != 2) return
                        if (ancPendingGate.hasPending(SystemClock.elapsedRealtime())) {
                            Log.d(TAG, "MiLink ANC level deferred while mode confirmation is pending")
                            return
                        }
                        val level = receivedIntent.getIntExtra("level", -1)
                        selectionForStatus(route, currentAnc, level)
                            ?.let(::applyAncSelection)
                            ?: return
                        saveState(context)
                        refreshAncPresentation("anc-level-changed", refreshDetails = false)
                    }
                    HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_CHANGED -> {
                        if (!receivedIntent.getBooleanExtra(
                                HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_CONFIRMED,
                                false,
                            )
                        ) {
                            return
                        }
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
                        val spatialModeValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_MODE,
                        )
                        val spatialSceneValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SPATIAL_SCENE,
                        )
                        val soundEffectValue = receivedIntent.getStringExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_SOUND_EFFECT,
                        )
                        val acceptedSpatialModeValue = spatialModeValue?.takeIf {
                            freeClip2AudioPendingGate.shouldApplyConfirmed(
                                HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE,
                                it,
                            )
                        }
                        val acceptedSpatialSceneValue = spatialSceneValue?.takeIf {
                            freeClip2AudioPendingGate.shouldApplyConfirmed(
                                HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE,
                                it,
                            )
                        }
                        val acceptedSoundEffectValue = soundEffectValue?.takeIf {
                            freeClip2AudioPendingGate.shouldApplyConfirmed(
                                HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT,
                                it,
                            )
                        }
                        FreeClip2SpatialAudioMode.fromExtraValue(
                            acceptedSpatialModeValue,
                        )?.let { currentFreeClip2SpatialMode = it }
                        FreeClip2SpatialScene.fromExtraValue(
                            acceptedSpatialSceneValue,
                        )?.let { currentFreeClip2SpatialScene = it }
                        FreeClip2SoundEffect.fromExtraValue(
                            acceptedSoundEffectValue,
                        )?.let { currentFreeClip2SoundEffect = it }
                        if (receivedIntent.hasExtra(
                                HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                            )
                        ) {
                            currentFreeClip2EqualizerSelectedId = receivedIntent.getIntExtra(
                                HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_SELECTED_ID,
                                -1,
                            ).takeIf { it in 0..0xFF }
                        }
                        receivedIntent.readHuaweiEqualizerCustomPresets()?.let {
                            currentFreeClip2CustomPresets = it
                        }
                        if (pendingFreeClip2CustomEqualizerId ==
                            currentFreeClip2EqualizerSelectedId
                        ) {
                            pendingFreeClip2CustomEqualizerId = null
                        }
                        saveState(context)
                        refreshFreeClip2AudioEffectSections("audio-changed")
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_CHANGED -> {
                        if (!receivedIntent.getBooleanExtra(
                                HuaweiPodsAction.EXTRA_HUAWEI_EQUALIZER_CONFIRMED,
                                false,
                            )
                        ) {
                            return
                        }
                        if (!rememberSupportedDevice(receivedIntent)) return
                        currentSessionConfirmed = true
                        if (currentHuaweiRoute() != HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
                            !receivedIntent.hasExtra(
                                HuaweiPodsAction.EXTRA_HUAWEI_EQUALIZER_SELECTED_ID,
                            )
                        ) {
                            return
                        }
                        currentHuaweiEqualizerSelectedId = receivedIntent.getIntExtra(
                            HuaweiPodsAction.EXTRA_HUAWEI_EQUALIZER_SELECTED_ID,
                            -1,
                        ).takeIf {
                            it in freeBuds6iBuiltInSoundEffectIds || it in 0x64..0x66
                        }
                            ?: return
                        saveState(context)
                        refreshFreeClip2AudioEffectSections("equalizer-changed")
                        refreshHuaweiEqualizerControls()
                    }
                    HuaweiPodsAction.ACTION_HUAWEI_LOW_LATENCY_CHANGED -> {
                        if (!rememberSupportedDevice(receivedIntent) ||
                            !receivedIntent.hasExtra(
                                HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED,
                            )
                        ) {
                            return
                        }
                        currentSessionConfirmed = true
                        currentLowLatencyEnabled = receivedIntent.getBooleanExtra(
                            HuaweiPodsAction.EXTRA_HUAWEI_LOW_LATENCY_ENABLED,
                            currentLowLatencyEnabled,
                        )
                        refreshLowLatencyQuickCard()
                    }
                }
            }
        }
        context?.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        statusReceiver = receiver
        receiverRegistered = true
        context?.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_UI_INIT).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    internal fun isHuaweiPod(device: BluetoothDevice): Boolean {
        return routeForDevice(device).isSupported
    }

    internal fun isHuaweiAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return resolveHuaweiDeviceRoute(address, null).isSupported ||
            normalized == currentAddress?.uppercase() ||
            knownHuaweiRoutes.containsKey(normalized)
    }

    private fun routeForHeadsetInfo(info: Any?): HuaweiDeviceRoute {
        if (info == null) return HuaweiDeviceRoute.UNSUPPORTED
        listOf("getAddress", "component1").forEach { method ->
            val address = runCatching { callMethod(info, method) as? String }.getOrNull()
            if (address != null) {
                routeForAddress(address).takeIf { it.isSupported }?.let { return it }
            }
        }
        return HuaweiDeviceRoute.UNSUPPORTED
    }

    private fun addressForHeadsetInfo(info: Any?): String? {
        if (info == null) return null
        listOf("getAddress", "component1").forEach { method ->
            runCatching { callMethod(info, method) as? String }.getOrNull()
                ?.trim()
                ?.takeIf(bluetoothAddressPattern::matches)
                ?.let { return it }
        }
        return null
    }

    /** AncBatteryController/MxBluetoothSdk 的运行时命令域始终为 0=关、1=降噪、2=通透。 */
    private fun miLinkAncRuntimeState(): Int {
        loadState()
        return miLinkHostAncStateFor(currentHuaweiRoute(), currentAnc)
    }

    /** HeadsetInfo 与原生 ANC 卡片刷新方法使用各宿主版本自己的显示域。 */
    private fun miLinkAncDisplayState(
        hostSpec: MiLinkAncHostSpec? = activeMiLinkAncHostSpec,
    ): Int {
        loadState()
        return miLinkHostAncStateFor(
            currentHuaweiRoute(),
            currentAnc,
            hostSpec?.displayValueOrder ?: MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
        )
    }

    private fun currentHuaweiRoute(): HuaweiDeviceRoute =
        currentRoute.takeIf { it.isSupported }
            ?: resolveHuaweiDeviceRoute(currentAddress, currentName)

    private fun routeForAddress(address: String): HuaweiDeviceRoute {
        if (address.equals(currentAddress, ignoreCase = true) && currentRoute.isSupported) {
            return currentRoute
        }
        knownHuaweiRoutes[address.uppercase()]?.takeIf { it.isSupported }?.let { return it }
        return resolveHuaweiDeviceRoute(address, null)
    }

    private fun routeForDevice(device: BluetoothDevice): HuaweiDeviceRoute {
        val address = runCatching { device.address }.getOrNull()
        var route = device.huaweiDeviceRoute()
        if (!route.isSupported && address.equals(currentAddress, ignoreCase = true) && currentRoute.isSupported) {
            route = currentRoute
        }
        if (!route.isSupported && address != null) {
            route = knownHuaweiRoutes[address.uppercase()] ?: HuaweiDeviceRoute.UNSUPPORTED
        }
        return route
    }

    private fun isCurrentHuaweiDevice(
        device: BluetoothDevice,
        route: HuaweiDeviceRoute = routeForDevice(device),
    ): Boolean {
        loadState()
        val address = runCatching { device.address }.getOrNull()
        return matchesMiLinkStateOwner(currentAddress, currentHuaweiRoute(), address, route)
    }

    private fun isCurrentHeadsetInfo(info: Any?, route: HuaweiDeviceRoute): Boolean {
        loadState()
        return matchesMiLinkStateOwner(
            currentAddress,
            currentHuaweiRoute(),
            addressForHeadsetInfo(info),
            route,
        )
    }

    private fun methodTargetMatchesCurrent(
        args: List<Any?>,
        instance: Any?,
        route: HuaweiDeviceRoute,
    ): Boolean {
        val address = args.firstNotNullOfOrNull { arg ->
            when (arg) {
                is BluetoothDevice -> runCatching { arg.address }.getOrNull()
                is String -> arg.trim().takeIf(bluetoothAddressPattern::matches)
                else -> null
            }
        } ?: addressForHeadsetInfo(instance) ?: return true
        loadState()
        return matchesMiLinkStateOwner(currentAddress, currentHuaweiRoute(), address, route)
    }

    private fun selectionForStatus(
        route: HuaweiDeviceRoute,
        status: Int,
        requestedSubMode: Int? = null,
    ): MiLinkAncSelection? {
        if (!route.supportsAnc || status == 3 && !route.supportsTransparency) return null
        if (status !in setOf(1, 2, 3)) return null
        val storedSubMode = when (status) {
            2 -> currentAncSubMode
            3 -> currentTransparencySubMode
            else -> null
        }
        return MiLinkAncSelection(
            status = status,
            subMode = normalizeMiLinkAncSubMode(route, status, requestedSubMode, storedSubMode),
        )
    }

    private fun applyAncSelection(selection: MiLinkAncSelection) {
        currentAnc = selection.status
        when (selection.status) {
            2 -> currentAncSubMode = selection.subMode
            3 -> currentTransparencySubMode = selection.subMode
        }
    }

    private fun dispatchAncSelection(
        selection: MiLinkAncSelection,
        fallbackContext: Context? = null,
        reason: String,
    ): Boolean {
        val route = currentHuaweiRoute()
        if (route.supportsAncStateReadback &&
            !ancPendingGate.tryBegin(selection, SystemClock.elapsedRealtime())
        ) {
            refreshAncPresentation("$reason-duplicate")
            return false
        }
        applyAncSelection(selection)
        saveState(fallbackContext)
        sendHuaweiAnc(selection, fallbackContext)
        // 有回读的机型只接受蓝牙进程发布的确认值；本地乐观广播会提前清掉待确认状态。
        if (!route.supportsAncStateReadback) {
            sendAncChanged(selection, fallbackContext)
        }
        refreshAncPresentation(reason)
        return true
    }

    private fun refreshAncCards(
        reason: String,
        refreshDetails: Boolean = true,
    ) {
        val cards = synchronized(ancCards) { ancCards.keys.toList() }
        cards.forEach { card ->
            runCatching {
                val binding = ancCards[card]
                if (binding == null || !renderHostAncCardState(card, binding, reason)) {
                    configureAncCard(card, reason)
                }
            }
                .onFailure { Log.w(TAG, "MiLink ANC card refresh failed reason=$reason", it) }
        }
        if (refreshDetails) {
            val details = synchronized(headsetDetails) { headsetDetails.keys.toList() }
            details.forEach { detail -> rememberAndRefreshHeadsetDetail(detail, reason) }
        }
    }

    /**
     * 同时走宿主属性监听和当前卡片重画。前者让融合中心重新读取我们接管的 getAncState()，
     * 后者保证已展开的卡片立即更新；两条路径都只作用于当前耳机，不扫描完整 View 树。
     */
    private fun refreshAncPresentation(
        reason: String,
        refreshDetails: Boolean = true,
    ) {
        currentBluetoothDevice()?.let { device ->
            notifyHeadsetPropertyChanged(lastAncBatteryController, device, 8)
            notifyHeadsetPropertyChanged(lastAncBatteryController, device, 4)
        }
        refreshAncCards(reason, refreshDetails)
    }

    private fun refreshHuaweiEqualizerControls() {
        val details = synchronized(headsetDetails) { headsetDetails.keys.toList() }
        details.forEach { detail ->
            if (routeForAncCardDetail(detail) == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
                syncFreeBuds6iSoundEffectControls(detail)
            }
        }
    }

    /** HyperOS 4 的 M(int) 才会重画原生三态按钮；失败时回退到模块附加区域刷新。 */
    private fun renderHostAncCardState(
        card: Any,
        binding: AncCardBinding,
        reason: String,
    ): Boolean {
        val refreshNames = binding.hostSpec.refreshMethodNames
        if (refreshNames == null) {
            scheduleLegacyAncCardState(card, binding, reason)
            return false
        }
        val method = card.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name in refreshNames &&
                candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
        } ?: return false
        return runCatching {
            method.isAccessible = true
            val hostState = miLinkAncDisplayState(binding.hostSpec)
            // 当前 HyperOS 4 的 M(int) 是纯显示刷新。仍在 UI 同步作用域内调用，
            // 避免宿主小版本改变实现后，把卡片重画误当成一次新的耳机控制命令。
            withMiLinkAncUiSync(ancInternalUiSyncDepth) {
                method.invoke(card, hostState)
            }
            binding.renderedHostAncState = hostState
            Log.d(TAG, "MiLink native ANC card rendered method=${method.name} reason=$reason")
            true
        }.getOrElse { error ->
            Log.w(TAG, "MiLink native ANC card render failed reason=$reason", error)
            false
        }
    }

    /**
     * legacy 卡片没有等价于新版 M(int) 的刷新入口。复用它自身的按钮监听器最稳定：
     * callOnClick() 只执行卡片内部选中态切换，不产生点击音效；控制 API 会被同步深度拦截，
     * 因此不会重复向耳机写指令。
     */
    private fun scheduleLegacyAncCardState(
        card: Any,
        binding: AncCardBinding,
        reason: String,
    ) {
        if (binding.hostSpec.adapterName != "legacy") return
        val hostState = miLinkAncDisplayState(binding.hostSpec)
        if (binding.renderedHostAncState == hostState || binding.pendingHostAncState == hostState) return
        val detail = binding.detail.get() as? View ?: return
        val clearView = resolveAncTransparencyView(card, binding, detail)
            ?: binding.clearView?.get()
            ?: return
        val modeRow = capabilityParent(clearView) ?: return
        val target = findLegacyAncModeButton(modeRow, hostState) ?: run {
            Log.w(TAG, "MiLink legacy ANC button missing state=$hostState reason=$reason")
            return
        }
        binding.pendingHostAncState = hostState
        postTracked(target) {
            if (binding.pendingHostAncState != hostState ||
                miLinkAncDisplayState(binding.hostSpec) != hostState
            ) {
                return@postTracked
            }
            val handled = withMiLinkAncUiSync(ancInternalUiSyncDepth) {
                runCatching { target.callOnClick() }.getOrElse { error ->
                    Log.w(TAG, "MiLink legacy ANC button replay failed state=$hostState reason=$reason", error)
                    false
                }
            }
            binding.pendingHostAncState = null
            if (handled) {
                binding.renderedHostAncState = hostState
                Log.d(TAG, "MiLink legacy ANC card rendered state=$hostState reason=$reason")
            } else {
                Log.w(TAG, "MiLink legacy ANC button had no listener state=$hostState reason=$reason")
            }
        }
    }

    private fun findLegacyAncModeButton(modeRow: ViewGroup, hostState: Int): View? {
        val labels = miLinkAncModeLabels(hostState)
        if (labels.isEmpty()) return null
        val label = collectTextViews(modeRow).firstOrNull { textView ->
            textView.text?.toString()?.trim()?.lowercase() in labels
        } ?: return null
        var candidate: View = label
        var directChild: View = label
        while (candidate !== modeRow) {
            if (candidate.isClickable) return candidate
            directChild = candidate
            candidate = candidate.parent as? View ?: break
        }
        return directChild.takeIf { it.parent === modeRow && it.hasOnClickListeners() }
    }

    private fun refreshFreeClip2AudioEffectSections(reason: String) {
        val sections = synchronized(miAudioEffectSections) {
            miAudioEffectSections.keys.toList()
        }
        sections.forEach { section ->
            safelyConfigureFreeClip2AudioEffectSection(section, reason)
        }
    }

    private fun miLinkBatteryLevels(): List<Int> {
        loadState()
        val left = batteryValue(currentBattery.left)
        val right = batteryValue(currentBattery.right)
        val box = batteryValue(currentBattery.case)
        return listOf(
            box,
            left,
            right,
            chargingValue(currentBattery.case),
            chargingValue(currentBattery.left),
            chargingValue(currentBattery.right)
        )
    }

    private fun batteryPercentForMiLink(): Int {
        loadState()
        val values = listOfNotNull(currentBattery.left, currentBattery.right)
            .filter { it.isConnected }
            .map { it.battery.coerceIn(0, 100) }
        return values.minOrNull() ?: 0
    }

    private fun batteryValue(params: moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams?): Int {
        if (params?.isConnected != true) return -1
        return params.battery.coerceIn(0, 100)
    }

    private fun chargingValue(params: moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams?): Int {
        return if (params?.isConnected == true && params.isCharging) 1 else 0
    }

    private fun sendHuaweiAnc(selection: MiLinkAncSelection, fallbackContext: Context? = null) {
        val route = currentHuaweiRoute()
        if (!route.supportsAnc || selection.status == 3 && !route.supportsTransparency) {
            Log.d(TAG, "sendHuaweiAnc skipped: route=$route selection=$selection")
            return
        }
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "sendHuaweiAnc skipped: context is null selection=$selection")
            return
        }
        Intent(HuaweiPodsAction.ACTION_ANC_SELECT).apply {
            currentAddress?.let { putExtra("address", it) }
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("status", selection.status)
            selection.subMode?.let { putExtra("submode", it) }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun requestFreeClip2AudioSelection(
        mode: FreeClip2SpatialAudioMode,
        source: String,
    ) = requestFreeClip2AudioSelection(
        kind = HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE,
        value = mode.extraValue,
        description = "spatial mode=$mode",
        source = source,
    )

    private fun requestFreeClip2SoundEffectSelection(
        effect: FreeClip2SoundEffect,
        source: String,
    ) {
        if (!effect.isSelectable) {
            Log.d(TAG, "MiLink FreeClip2 read-only sound effect ignored effect=$effect source=$source")
            return
        }
        requestFreeClip2AudioSelection(
            kind = HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT,
            value = effect.extraValue,
            description = "sound effect=$effect",
            source = source,
        )
    }

    private fun requestFreeClip2CustomSoundEffectSelection(
        preset: HuaweiEqualizerPreset,
        source: String,
    ) {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (route != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val payload = HuaweiEqualizerPresetTransport.encode(listOf(preset))
        if (payload.ids.size != 1 || payload.names.size != 1 || payload.gains.size != 10) {
            Log.w(TAG, "MiLink invalid custom sound effect ignored id=${preset.id} source=$source")
            return
        }
        if (pendingFreeClip2CustomEqualizerId == preset.id) {
            Log.d(TAG, "MiLink duplicate custom sound effect ignored id=${preset.id} source=$source")
            return
        }
        pendingFreeClip2CustomEqualizerId = preset.id
        val dispatched = runCatching {
            ctx.sendIdentitySharingBroadcast(
                Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET).apply {
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, UUID.randomUUID().toString())
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_PRESET_ID, payload.ids.single())
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME, payload.names.single())
                    putExtra(
                        HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS,
                        payload.gains.toIntArray(),
                    )
                    setPackage(SmartAudioFreeClip2BridgePolicy.SMART_AUDIO_PACKAGE)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }.onFailure {
            Log.w(TAG, "MiLink custom sound effect send failed id=${preset.id}", it)
        }.isSuccess
        if (!dispatched) {
            pendingFreeClip2CustomEqualizerId = null
            return
        }
        mainHandler.postDelayed({
            if (pendingFreeClip2CustomEqualizerId == preset.id) {
                pendingFreeClip2CustomEqualizerId = null
                requestFreeClip2AudioState("custom-sound-effect-confirm", force = true)
            }
        }, FREECLIP2_CUSTOM_EQ_CONFIRM_DELAY_MS)
        Log.d(TAG, "MiLink custom sound effect sent id=${preset.id} source=$source")
    }

    private fun requestFreeClip2AudioSelection(
        kind: String,
        value: String,
        description: String,
        source: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!freeClip2AudioPendingGate.tryBegin(
                kind,
                value,
                now,
            )
        ) {
            Log.d(TAG, "MiLink FreeClip2 duplicate selection ignored $description source=$source")
            return
        }
        if (!sendFreeClip2AudioSetting(
                kind = kind,
                value = value,
            )
        ) {
            freeClip2AudioPendingGate.clear()
            Log.w(TAG, "MiLink FreeClip2 selection send failed $description source=$source")
            return
        }
        FreeClip2AudioUiState(
            spatialMode = currentFreeClip2SpatialMode,
            spatialScene = currentFreeClip2SpatialScene,
            soundEffect = currentFreeClip2SoundEffect,
        ).withSelection(kind, value)?.let { selected ->
            currentFreeClip2SpatialMode = selected.spatialMode
            currentFreeClip2SpatialScene = selected.spatialScene
            currentFreeClip2SoundEffect = selected.soundEffect
            saveState(context)
        }
        Log.d(TAG, "MiLink FreeClip2 selection pending $description source=$source")
    }

    private fun requestFreeClip2AudioState(reason: String, force: Boolean = false) {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (route != HuaweiDeviceRoute.HUAWEI_FREECLIP2) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFreeClip2AudioRefreshRequestAt < FREECLIP2_AUDIO_REFRESH_MIN_INTERVAL_MS) {
            return
        }
        lastFreeClip2AudioRefreshRequestAt = now
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_REFRESH).apply {
            putExtra("address", address)
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("force", force)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "MiLink FreeClip2 audio readback requested reason=$reason address=$address")
    }

    private fun requestMiLinkAncState(reason: String) {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (!route.supportsAncStateReadback) return
        val now = SystemClock.elapsedRealtime()
        if (
            lastHuaweiAncRefreshRequestAt != 0L &&
            now - lastHuaweiAncRefreshRequestAt in 0 until HUAWEI_ANC_REFRESH_MIN_INTERVAL_MS
        ) {
            return
        }
        lastHuaweiAncRefreshRequestAt = now
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_ANC_REFRESH).apply {
            putExtra("address", address)
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "MiLink ANC readback requested reason=$reason address=$address")
    }

    private fun sendFreeClip2AudioSetting(
        kind: String,
        value: String,
        fallbackContext: Context? = null,
    ): Boolean {
        val route = currentHuaweiRoute()
        if (route != HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            Log.w(TAG, "FreeClip2 audio setting ignored for route=$route kind=$kind value=$value")
            return false
        }
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return false
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "FreeClip2 audio setting skipped: context is null")
            return false
        }
        return runCatching {
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET).apply {
                putExtra("address", address)
                currentName?.let { putExtra("device_name", it) }
                encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_KIND, kind)
                putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_VALUE, value)
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }.isSuccess
    }

    private fun requestHuaweiEqualizerSelection(presetId: Int, source: String) {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS6I ||
            presetId !in freeBuds6iBuiltInSoundEffectIds
        ) {
            Log.w(TAG, "Huawei equalizer selection ignored id=$presetId route=$route source=$source")
            return
        }
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_PRESET_SET).apply {
            putExtra("address", address)
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra(HuaweiPodsAction.EXTRA_HUAWEI_EQUALIZER_SELECTED_ID, presetId)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "Huawei equalizer selection sent id=$presetId source=$source")
    }

    private fun requestHuaweiEqualizerState(reason: String, force: Boolean = false) {
        val route = currentHuaweiRoute()
        val address = currentAddress?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (route != HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) return
        val now = SystemClock.elapsedRealtime()
        if (!force &&
            now - lastHuaweiEqualizerRefreshRequestAt < HUAWEI_EQUALIZER_REFRESH_MIN_INTERVAL_MS
        ) {
            return
        }
        lastHuaweiEqualizerRefreshRequestAt = now
        ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_HUAWEI_EQUALIZER_REFRESH).apply {
            putExtra("address", address)
            currentName?.let { putExtra("device_name", it) }
            encodeHuaweiDeviceRouteForBroadcast(route)?.let {
                putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
            }
            putExtra("force", force)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "Huawei equalizer readback requested reason=$reason address=$address")
    }

    private fun sendAncChanged(selection: MiLinkAncSelection, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: return
        listOf(BuildConfig.APPLICATION_ID, "com.milink.service", "com.android.settings").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(HuaweiPodsAction.ACTION_PODS_ANC_CHANGED).apply {
                currentAddress?.let { putExtra("address", it) }
                currentName?.let { putExtra("device_name", it) }
                encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute())?.let {
                    putExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE, it)
                }
                putExtra("status", selection.status)
                selection.subMode?.let { putExtra("submode", it) }
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    internal fun miLinkSwitchState(): Int {
        loadState()
        return 1
    }

    private fun rememberSupportedDevice(intent: Intent): Boolean {
        val address = intent.getStringExtra("address")
            ?.takeIf(String::isNotBlank)
            ?: currentAddress
        val name = intent.getStringExtra("device_name")
            ?.takeIf(String::isNotBlank)
            ?: currentName
        val route = decodeHuaweiDeviceRouteFromBroadcast(
            intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
        ) ?: resolveHuaweiDeviceRoute(address, name)
        if (!route.isSupported) {
            Log.w(TAG, "ignored unsupported persisted/broadcast device=${name.orEmpty()}/${address.orEmpty()}")
            return false
        }
        val previousRoute = currentHuaweiRoute()
        val identityChanged = when {
            !address.isNullOrBlank() -> !address.equals(currentAddress, ignoreCase = true)
            else -> name != currentName
        }
        currentName = name
        currentAddress = address
        currentRoute = route
        currentAddress?.takeIf { it.isNotBlank() }?.let { knownHuaweiRoutes[it.uppercase()] = route }
        if (identityChanged || previousRoute != route) {
            currentBattery = BatteryParams()
            resetAncState(route)
            resetFreeClip2AudioState()
            resetHuaweiEqualizerState()
            refreshLowLatencyFromPrefs()
        }
        return true
    }

    private fun forgetCurrentDevice(intent: Intent): Boolean {
        loadState()
        val disconnectedAddress = intent.getStringExtra("address")?.takeIf(String::isNotBlank)
        val disconnectedName = intent.getStringExtra("device_name")?.takeIf(String::isNotBlank)
        if (currentAddress.isNullOrBlank() && currentName.isNullOrBlank()) return false
        if (
            disconnectedAddress != null &&
            currentAddress != null &&
            !disconnectedAddress.equals(currentAddress, ignoreCase = true)
        ) {
            return false
        }
        if (
            disconnectedAddress == null &&
            disconnectedName != null &&
            currentName != null &&
            !disconnectedName.equals(currentName, ignoreCase = true)
        ) {
            return false
        }

        currentAddress = null
        currentName = null
        currentRoute = HuaweiDeviceRoute.UNSUPPORTED
        currentSessionConfirmed = false
        currentBattery = BatteryParams()
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncSubMode = null
        currentTransparencySubMode = null
        ancPendingGate.clear()
        currentLowLatencyEnabled = false
        resetFreeClip2AudioState()
        resetHuaweiEqualizerState()
        circulationSignalRewriteUntilMs = 0L
        circulationUiCompletedUntilMs = 0L
        circulationTargetHostId = null
        return true
    }

    private fun resetAncState(route: HuaweiDeviceRoute) {
        ancPendingGate.clear()
        lastHuaweiAncRefreshRequestAt = 0L
        currentAnc = NoiseControlMode.OFF.broadcastStatus
        currentAncSubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus,
            requestedSubMode = null,
            storedSubMode = null,
        )
        currentTransparencySubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.TRANSPARENCY.broadcastStatus,
            requestedSubMode = null,
            storedSubMode = null,
        )
    }

    private fun resetFreeClip2AudioState() {
        currentFreeClip2SpatialMode = FreeClip2SpatialAudioMode.OFF
        currentFreeClip2SpatialScene = FreeClip2SpatialScene.DEFAULT
        currentFreeClip2SoundEffect = FreeClip2SoundEffect.DEFAULT
        currentFreeClip2EqualizerSelectedId = null
        currentFreeClip2CustomPresets = emptyList()
        pendingFreeClip2CustomEqualizerId = null
        freeClip2AudioPendingGate.clear()
        lastFreeClip2AudioRefreshRequestAt = 0L
    }

    private fun resetHuaweiEqualizerState() {
        currentHuaweiEqualizerSelectedId = null
        lastHuaweiEqualizerRefreshRequestAt = 0L
    }

    private fun isCurrentHuaweiHeadset(): Boolean {
        loadState()
        return !currentAddress.isNullOrBlank() &&
            resolveHuaweiDeviceRoute(currentAddress, currentName).isSupported
    }

    private fun isTargetCirculateHeadset(serviceInfo: Any?): Boolean {
        if (serviceInfo == null) return false
        val activeRoute = currentHuaweiRoute()
        if (!activeRoute.isSupported) return false

        val serviceId = circulateIdentityValue(serviceInfo, "serviceId", "getServiceId")
        val deviceId = circulateIdentityValue(serviceInfo, "deviceId", "getDeviceId")
        val addressCandidates = buildSet {
            listOf(serviceId, deviceId).forEach { value ->
                value?.trim()?.uppercase()?.takeIf(bluetoothAddressPattern::matches)?.let(::add)
            }
            listOf(
                "address" to "getAddress",
                "bluetoothAddress" to "getBluetoothAddress",
                "macAddress" to "getMacAddress",
                "deviceAddress" to "getDeviceAddress",
                "headsetId" to "getHeadsetId",
            ).forEach { (fieldName, getterName) ->
                circulateIdentityValue(serviceInfo, fieldName, getterName)
                    ?.trim()
                    ?.uppercase()
                    ?.takeIf(bluetoothAddressPattern::matches)
                    ?.let(::add)
            }
        }
        val activeAddress = currentAddress
            ?.trim()
            ?.uppercase()
            ?.takeIf(bluetoothAddressPattern::matches)
        if (addressCandidates.isNotEmpty()) {
            val soleAddress = addressCandidates.singleOrNull() ?: return false
            if (activeAddress != null) return soleAddress == activeAddress
            return resolveHuaweiDeviceRoute(soleAddress, null) == activeRoute
        }

        val activeName = currentName?.trim()?.takeIf(String::isNotEmpty)
        return listOf(serviceId, deviceId).any { identity ->
            val value = identity?.trim()?.takeIf(String::isNotEmpty) ?: return@any false
            detectHuaweiDeviceRoute(value) == activeRoute ||
                activeName?.equals(value, ignoreCase = true) == true
        }
    }

    private fun circulateIdentityValue(
        serviceInfo: Any,
        fieldName: String,
        getterName: String,
    ): String? {
        return runCatching { getObjectField(serviceInfo, fieldName) as? String }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: runCatching { callMethod(serviceInfo, getterName) as? String }.getOrNull()
                ?.takeIf(String::isNotBlank)
    }

    private fun isCirculationRewriteActive(): Boolean {
        return System.currentTimeMillis() <= circulationSignalRewriteUntilMs && isCurrentHuaweiHeadset()
    }

    private fun isHeadsetCirculationLocked(client: Any?): Boolean {
        return runCatching { (getObjectField(client, "isCirculating") as? AtomicBoolean)?.get() == true }
            .getOrDefault(false)
    }

    private fun clearHeadsetCirculationLock(client: Any?, reason: String) {
        if (client == null || !isCurrentHuaweiHeadset()) return
        val now = System.currentTimeMillis()
        val isRewriteWindow = now <= circulationSignalRewriteUntilMs
        val isUiCompletedWindow = now <= circulationUiCompletedUntilMs
        if (!isRewriteWindow && !isUiCompletedWindow && !isHeadsetCirculationLocked(client)) return

        val startTime = runCatching { getObjectField(client, "circulateStartTime") as? Long }.getOrNull()
        runCatching { (getObjectField(client, "isCirculating") as? AtomicBoolean)?.set(false) }
        runCatching { setObjectField(client, "circulateStartTime", 0L) }
        listOf(
            "mBondingHeadsetDevice",
            "mBondingHeadsetService",
            "mBondingReturnHostDevice",
            "mBondingTargetHostDevice"
        ).forEach { fieldName ->
            runCatching { setObjectField(client, fieldName, null) }
        }
        listOf("connectDeviceList", "disconnectDeviceList").forEach { fieldName ->
            runCatching { (getObjectField(client, fieldName) as? MutableCollection<*>)?.clear() }
        }
        Log.w(
            TAG,
            "MiLink circulate experiment clear stale processing reason=$reason " +
                "startTime=$startTime rewrite=$isRewriteWindow uiCompleted=$isUiCompletedWindow"
        )
    }

    private fun scheduleReturnToLocalViaClient(targetCard: Any?, headsetCard: Any?, serviceInfo: Any?): Boolean {
        val client = lastHeadsetServiceClient ?: return false
        val targetDevice = circulateDeviceInfoFromCard(targetCard) ?: return false
        val headsetDevice = cacheHeadsetDeviceInfo(client, serviceInfo) ?: return false

        clearHeadsetCirculationLock(client, "before local clientConnect")
        circulationSignalRewriteUntilMs = System.currentTimeMillis() + 20_000L
        circulationUiCompletedUntilMs = System.currentTimeMillis() + 30_000L
        circulationTargetHostId = "local_device_id"
        markCirculateServiceConnected(serviceInfo, "schedule-local-clientConnect")
        updateHeadsetAttachedCard(headsetCard, targetCard, "schedule-local-clientConnect")
        playLocalReturnTone("schedule-local-clientConnect")
        startLocalBluetoothConnectBurst("schedule-local-clientConnect")

        startManagedWorker("HuaweiPods-MiLinkReturn") { generation ->
            if (!isRuntimeGenerationActive(generation)) return@startManagedWorker
            val ret = runCatching { callMethod(client, "clientConnect", targetDevice, headsetDevice) as? Int }
                .onFailure { Log.w(TAG, "MiLink circulate experiment async local clientConnect failed", it) }
                .getOrNull()
            if (!isRuntimeGenerationActive(generation)) return@startManagedWorker
            Log.w(TAG, "MiLink circulate experiment async local clientConnect ret=$ret")
            if (ret != null && isCirculateConnectAccepted(ret)) {
                clearHeadsetCirculationLock(client, "after async local clientConnect")
                updateHeadsetAttachedCard(headsetCard, targetCard, "after async local clientConnect")
            }
        }
        return true
    }

    private fun playLocalReturnTone(reason: String) {
        startManagedWorker("HuaweiPods-MiLinkTone") { generation ->
            if (!isRuntimeGenerationActive(generation)) return@startManagedWorker
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 80) }
                .onFailure { Log.w(TAG, "MiLink circulate experiment local return tone init failed reason=$reason", it) }
                .getOrNull() ?: return@startManagedWorker
            try {
                if (isRuntimeGenerationActive(generation)) {
                    runCatching {
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                        Thread.sleep(240)
                    }.onFailure {
                        if (it !is InterruptedException) {
                            Log.w(TAG, "MiLink circulate experiment local return tone failed reason=$reason", it)
                        }
                    }
                }
            } finally {
                runCatching { tone.release() }
            }
        }
    }

    private fun startLocalBluetoothConnectBurst(reason: String) {
        val token = localBluetoothConnectBurstToken.incrementAndGet()
        startManagedWorker("HuaweiPods-MiLinkBtConnect") { generation ->
            var lastAttemptAtMs = 0L
            listOf(0L, 650L, 1_800L).forEach { attemptAtMs ->
                if (!isRuntimeGenerationActive(generation) ||
                    localBluetoothConnectBurstToken.get() != token
                ) {
                    return@startManagedWorker
                }
                val sleepMs = attemptAtMs - lastAttemptAtMs
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@startManagedWorker
                    }
                }
                lastAttemptAtMs = attemptAtMs
                if (!isRuntimeGenerationActive(generation) ||
                    localBluetoothConnectBurstToken.get() != token
                ) {
                    return@startManagedWorker
                }
                connectLocalBluetoothProfilesOnce(reason, generation)
            }
        }
    }

    private fun connectLocalBluetoothProfilesOnce(reason: String, generation: Int) {
        if (!isRuntimeGenerationActive(generation)) return
        val ctx = context ?: return
        val address = currentAddress ?: return
        val adapter = runCatching { ctx.getSystemService(BluetoothManager::class.java).adapter }
            .onFailure { Log.w(TAG, "MiLink circulate experiment local bt connect adapter failed reason=$reason", it) }
            .getOrNull() ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment local bt connect device failed reason=$reason address=$address", it) }
            .getOrNull() ?: return
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            if (!isRuntimeGenerationActive(generation)) return
            val request = MiLinkProfileProxyRequest(adapter, profile, generation)
            lateinit var listener: BluetoothProfile.ServiceListener
            listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(connectedProfile: Int, proxy: BluetoothProfile) {
                    synchronized(request.callbackLock) {
                        request.proxy = proxy
                        try {
                            if (connectedProfile != profile ||
                                !request.active.get() ||
                                !isRuntimeGenerationActive(request.generation)
                            ) {
                                return
                            }
                            val state = runCatching { proxy.getConnectionState(device) }
                                .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
                            if (state == BluetoothProfile.STATE_CONNECTED ||
                                state == BluetoothProfile.STATE_CONNECTING
                            ) {
                                Log.w(
                                    TAG,
                                    "MiLink circulate experiment local bt connect skip " +
                                        "profile=$profile state=$state reason=$reason " +
                                        "device=${device.address}",
                                )
                                return
                            }
                            runCatching {
                                proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                                    .invoke(proxy, device)
                                Log.w(
                                    TAG,
                                    "MiLink circulate experiment local bt connect " +
                                        "profile=$profile reason=$reason device=${device.address}",
                                )
                            }.onFailure {
                                Log.w(
                                    TAG,
                                    "MiLink circulate experiment local bt connect profile failed " +
                                        "profile=$profile reason=$reason",
                                    it,
                                )
                            }
                        } finally {
                            completeProfileProxyRequest(request, proxy)
                        }
                    }
                }

                override fun onServiceDisconnected(disconnectedProfile: Int) {
                    completeProfileProxyRequest(request)
                }
            }
            var accepted = false
            runCatching {
                synchronized(runtimeLifecycleLock) {
                    if (!isRuntimeGenerationActive(generation)) return@synchronized
                    profileProxyRequests.add(request)
                    accepted = adapter.getProfileProxy(ctx, listener, profile)
                }
            }.onFailure {
                Log.w(TAG, "MiLink circulate experiment local bt profile proxy failed profile=$profile reason=$reason", it)
            }
            if (!accepted) {
                completeProfileProxyRequest(request)
                return@forEach
            }
            try {
                request.completion.await(
                    HOT_RELOAD_PROFILE_DRAIN_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                request.active.set(false)
                return
            }
        }
    }

    private fun updateHeadsetAttachedCard(headsetCard: Any?, targetCard: Any?, reason: String) {
        if (headsetCard == null || targetCard == null) return
        val before = runCatching { callMethod(headsetCard, "P") }.getOrNull()
        runCatching { callMethod(headsetCard, "i0", targetCard) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment patch headset origin failed reason=$reason", it) }
            .getOrElse { return }
        val after = runCatching { callMethod(headsetCard, "P") }.getOrNull()
        Log.w(
            TAG,
            "MiLink circulate experiment patch headset origin reason=$reason " +
                "before=${circulateDeviceName(before)} after=${circulateDeviceName(after)}"
        )
    }

    private fun circulateDeviceInfoFromCard(deviceCard: Any?): Any? {
        val data = runCatching { callMethod(deviceCard, "M") }.getOrNull() ?: deviceCard ?: return null
        if (data.javaClass.name == "com.miui.circulate.api.service.CirculateDeviceInfo") return data
        return runCatching { callMethod(data, "b") }.getOrNull()
            ?: runCatching { getObjectField(data, "a") }.getOrNull()
            ?: data.javaClass.declaredMethods
                .firstOrNull {
                    it.parameterTypes.isEmpty() &&
                        it.returnType.name == "com.miui.circulate.api.service.CirculateDeviceInfo"
                }
                ?.let {
                    runCatching {
                        it.isAccessible = true
                        it.invoke(data)
                    }.getOrNull()
                }
    }

    private fun cacheHeadsetDeviceInfo(client: Any?, serviceInfo: Any?): Any? {
        val direct = listOf("circulateBluetoothDevice", "mBondingHeadsetDevice")
            .firstNotNullOfOrNull { fieldName ->
                runCatching { getObjectField(client, fieldName) }.getOrNull()
            }
        if (direct != null) {
            lastHeadsetDeviceInfo = direct
            return direct
        }

        val deviceId = runCatching { getObjectField(serviceInfo, "deviceId") as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return lastHeadsetDeviceInfo
        val manager = runCatching {
            findClass("com.miui.circulate.api.protocol.headset.HeadsetDeviceManager")
                .getDeclaredMethod("get")
                .apply { isAccessible = true }
                .invoke(null)
        }.getOrNull()
        val fromManager = runCatching { callMethod(manager, "getBluetoothDevice", deviceId) }.getOrNull()
        if (fromManager != null) lastHeadsetDeviceInfo = fromManager
        return fromManager ?: lastHeadsetDeviceInfo
    }

    private fun isCirculateConnectAccepted(ret: Int): Boolean {
        return ret == 100 || ret == 301 || ret == 308
    }

    private fun shouldSuppressCirculationActiveLost(hostId: String?): Boolean {
        if (!isCirculationRewriteActive() || hostId == null || hostId == "local_device_id") return false
        val targetHostId = circulationTargetHostId
        return targetHostId == null || targetHostId == hostId
    }

    private fun headsetHostId(host: Any?): String? {
        if (host == null) return null
        listOf("getHostId", "component1").forEach { methodName ->
            runCatching { callMethod(host, methodName) as? String }.getOrNull()?.let { return it }
        }
        return runCatching { getObjectField(host, "hostId") as? String }.getOrNull()
    }

    private fun isLocalCirculateDevice(deviceCard: Any?): Boolean {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull()
        val priority = runCatching { callMethod(deviceData, "getPriority") as? Int }.getOrNull()
        if (priority == -1) return true
        val name = circulateDeviceName(deviceCard)
        return localDeviceNames().any { it.equals(name, ignoreCase = true) }
    }

    private fun shouldPatchWindowsHeadsetService(deviceInfo: Any?, serviceType: Int): Boolean {
        if (!isCurrentHuaweiHeadset()) return false
        if (serviceType != CIRCULATE_SERVICE_HEADSET_PRIMARY && serviceType != CIRCULATE_SERVICE_HEADSET_FALLBACK) {
            return false
        }
        val isWindows = isWindowsCirculateDevice(deviceInfo)
        if (isWindows) rememberWindowsCirculateDevice(deviceInfo)
        return isWindows
    }

    private fun shouldPatchWindowsBondState(targetAddress: String?, targetHostId: String?): Boolean {
        if (!isCurrentHuaweiHeadset()) return false
        if (targetHostId.isNullOrBlank() || targetHostId == "local_device_id") return false
        loadWindowsHostIds()
        if (targetHostId in knownWindowsHostIds) return true
        return targetAddress != null && targetAddress in knownWindowsHostIds
    }

    private fun patchedWindowsHeadsetServiceResult(returnType: Class<*>, original: Any?): Any? {
        if (original != null && original != false && original != 0) return null
        val serviceInfo = lastHeadsetServiceInfo
        return when {
            returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java -> true
            returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java -> 1
            serviceInfo != null && returnType.isInstance(serviceInfo) -> serviceInfo
            returnType == Any::class.java && serviceInfo != null -> serviceInfo
            else -> null
        }
    }

    private fun isWindowsCirculateDevice(deviceInfo: Any?): Boolean {
        if (deviceInfo == null) return false
        val directType = runCatching { callMethod(deviceInfo, "getDeviceType") as? String }.getOrNull()
            ?: runCatching { getObjectField(deviceInfo, "deviceType") as? String }.getOrNull()
        if (directType.equals("Windows", ignoreCase = true)) return true
        val description = runCatching { describeCirculateDevice(deviceInfo) }.getOrNull().orEmpty()
        return "DeviceType=Windows" in description || "devicesType=Windows" in description
    }

    private fun rememberWindowsCirculateDevice(deviceInfo: Any?) {
        if (deviceInfo == null) return
        val before = knownWindowsHostIds.size
        listOf("getHostId", "getDeviceId", "getId").forEach { methodName ->
            runCatching { callMethod(deviceInfo, methodName) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { knownWindowsHostIds.add(it) }
        }
        listOf("hostId", "deviceId", "id").forEach { fieldName ->
            runCatching { getObjectField(deviceInfo, fieldName) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { knownWindowsHostIds.add(it) }
        }
        if (knownWindowsHostIds.size != before) {
            saveWindowsHostIds()
            Log.w(TAG, "MiLink PC circulate remember Windows hosts=$knownWindowsHostIds target=${describeCirculateDevice(deviceInfo)}")
        }
    }

    private fun saveWindowsHostIds() {
        val ctx = context ?: return
        if (knownWindowsHostIds.isEmpty()) return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_WINDOWS_HOST_IDS, knownWindowsHostIds)
                .apply()
        }.onFailure {
            Log.w(TAG, "MiLink PC circulate save Windows hosts failed", it)
        }
    }

    private fun loadWindowsHostIds() {
        val ctx = context ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(PREF_WINDOWS_HOST_IDS, emptySet())
                .orEmpty()
                .filter { it.isNotBlank() }
        }.onSuccess { stored ->
            val before = knownWindowsHostIds.size
            knownWindowsHostIds.addAll(stored)
            if (knownWindowsHostIds.size != before) {
                Log.w(TAG, "MiLink PC circulate loaded Windows hosts=$knownWindowsHostIds")
            }
        }.onFailure {
            Log.w(TAG, "MiLink PC circulate load Windows hosts failed", it)
        }
    }

    private fun circulateDeviceName(deviceCard: Any?): String {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull()
        return runCatching { callMethod(deviceData, "getName") as? String }.getOrNull().orEmpty()
    }

    private fun describeCirculateCard(deviceCard: Any?): String {
        val deviceData = runCatching { callMethod(deviceCard, "M") }.getOrNull() ?: deviceCard
        return describeCirculateDevice(deviceData)
    }

    private fun describeCirculateDevice(deviceInfo: Any?): String {
        if (deviceInfo == null) return "null"
        val methodValues = linkedMapOf<String, Any?>()
        listOf(
            "getName",
            "getDeviceId",
            "getId",
            "getHostId",
            "getDeviceType",
            "getDeviceModel",
            "getPriority",
            "getAddress",
            "getBtMac",
            "getMac"
        ).forEach { methodName ->
            runCatching { callMethod(deviceInfo, methodName) }
                .getOrNull()
                ?.let { methodValues[methodName.removePrefix("get")] = it }
        }
        return buildString {
            append(deviceInfo.javaClass.name)
            if (methodValues.isNotEmpty()) {
                append(methodValues.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" })
            } else {
                append("{")
                append(deviceInfo.toString())
                append("}")
            }
        }
    }

    private fun describeCirculateService(serviceInfo: Any?): String {
        if (serviceInfo == null) return "null"
        val values = linkedMapOf<String, Any?>()
        listOf(
            "serviceId",
            "deviceId",
            "headsetId",
            "connectState",
            "serviceName",
            "hostId",
            "deviceType"
        ).forEach { fieldName ->
            runCatching { getObjectField(serviceInfo, fieldName) }
                .getOrNull()
                ?.let { values[fieldName] = it }
        }
        listOf("getServiceId", "getDeviceId", "getConnectState").forEach { methodName ->
            runCatching { callMethod(serviceInfo, methodName) }
                .getOrNull()
                ?.let { values[methodName.removePrefix("get")] = it }
        }
        runCatching { getObjectField(serviceInfo, "serviceProperties") }
            .getOrNull()
            ?.let { properties ->
                val all = runCatching { callMethod(properties, "getAll") }.getOrNull()
                values["properties"] = all ?: properties
            }
        return serviceInfo.javaClass.name + values.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
    }

    private fun describeCollection(value: Any?): String {
        val collection = value as? Collection<*> ?: return value?.toString().orEmpty()
        return collection.joinToString(prefix = "[", postfix = "]") { describeCirculateDevice(it) }
    }

    private fun localDeviceNames(): Set<String> {
        val settingsName = context?.contentResolver?.let { resolver ->
            runCatching { Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        }.orEmpty()
        return setOf(
            settingsName,
            Build.MODEL.orEmpty(),
            "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}".trim()
        ).filter { it.isNotBlank() }.toSet()
    }

    private fun circulateServiceState(serviceInfo: Any?): Int? {
        return runCatching { getObjectField(serviceInfo, "connectState") as? Int }.getOrNull()
            ?: runCatching { callMethod(serviceInfo, "getConnectState") as? Int }.getOrNull()
    }

    private fun markCirculateServiceConnected(serviceInfo: Any?, reason: String): Boolean {
        if (!isTargetCirculateHeadset(serviceInfo)) return false
        val state = circulateServiceState(serviceInfo)
        if (state != CIRCULATE_STATE_CONNECTING) return false
        runCatching { setObjectField(serviceInfo, "connectState", CIRCULATE_STATE_CONNECTED) }
            .onFailure { Log.w(TAG, "MiLink circulate experiment patch service state failed reason=$reason", it) }
            .getOrElse { return false }
        return true
    }

    internal fun cacheRuntimeOwner(className: String, owner: Any?) {
        when (className) {
            "com.miui.headset.runtime.AncBatteryController" -> lastAncBatteryController = owner
            "com.miui.headset.runtime.ProfileContext" -> lastProfileContext = owner
        }
    }

    internal fun captureRuntimeContext(owner: Any?) {
        val ownerContext = runCatching { getObjectField(owner, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastProfileContext, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastAncBatteryController, "context") as? Context }.getOrNull()
            ?: return
        context = ownerContext.applicationContext ?: ownerContext
    }

    private fun notifyHeadsetPropertyChanged(controller: Any?, device: BluetoothDevice, updateType: Int) {
        val listener = runCatching { getObjectField(controller, "headsetPropertyChangeListener") }.getOrNull() ?: return
        runCatching {
            callMethod(listener, "invoke", device, updateType)
        }.onFailure { }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStatus(): BatteryParams? {
        return runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
    }

    private fun Intent.batteryStatusFromExtras(): BatteryParams? {
        if (!hasExtra("left_connected") && !hasExtra("right_connected") && !hasExtra("case_connected")) return null
        return BatteryParams(
            left = PodParams(
                getIntExtra("left_battery", 0),
                getBooleanExtra("left_charging", false),
                getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                getIntExtra("right_battery", 0),
                getBooleanExtra("right_charging", false),
                getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                getIntExtra("case_battery", 0),
                getBooleanExtra("case_charging", false),
                getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun saveState(ctx: Context?) {
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val editor = prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putString(PREF_DEVICE_ROUTE, encodeHuaweiDeviceRouteForBroadcast(currentHuaweiRoute()))
            .putInt("anc", currentAnc)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
            .putString(PREF_FREECLIP2_SPATIAL_MODE, currentFreeClip2SpatialMode.extraValue)
            .putString(PREF_FREECLIP2_SPATIAL_SCENE, currentFreeClip2SpatialScene.extraValue)
            .putString(PREF_FREECLIP2_SOUND_EFFECT, currentFreeClip2SoundEffect.extraValue)
        currentAncSubMode?.let { editor.putInt(PREF_ANC_SUBMODE, it) }
            ?: editor.remove(PREF_ANC_SUBMODE)
        currentTransparencySubMode?.let { editor.putInt(PREF_TRANSPARENCY_SUBMODE, it) }
            ?: editor.remove(PREF_TRANSPARENCY_SUBMODE)
        currentHuaweiEqualizerSelectedId?.let {
            editor.putInt(PREF_HUAWEI_EQUALIZER_SELECTED_ID, it)
        } ?: editor.remove(PREF_HUAWEI_EQUALIZER_SELECTED_ID)
        currentFreeClip2EqualizerSelectedId?.let {
            editor.putInt(PREF_FREECLIP2_EQ_SELECTED_ID, it)
        } ?: editor.remove(PREF_FREECLIP2_EQ_SELECTED_ID)
        (0x64..0x66).forEach { id ->
            val preset = currentFreeClip2CustomPresets.firstOrNull { it.id == id }
            if (preset == null) {
                editor.remove(PREF_FREECLIP2_CUSTOM_EQ_NAME_PREFIX + id)
                editor.remove(PREF_FREECLIP2_CUSTOM_EQ_GAINS_PREFIX + id)
            } else {
                editor.putString(PREF_FREECLIP2_CUSTOM_EQ_NAME_PREFIX + id, preset.name)
                editor.putString(
                    PREF_FREECLIP2_CUSTOM_EQ_GAINS_PREFIX + id,
                    preset.gains.joinToString(","),
                )
            }
        }
        editor.apply()
    }

    private fun loadState() {
        if (stateLoaded) return
        synchronized(stateLoadLock) {
            if (stateLoaded) return
            loadStateFromPreferences()
            if (context != null) stateLoaded = true
        }
    }

    private fun loadStateFromPreferences() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val hasPersistedIdentity = prefs.contains("address") || prefs.contains("name")
        val persistedAddress = prefs.getString("address", null)
        val persistedName = prefs.getString("name", null)
        val persistedRoute = decodeHuaweiDeviceRouteFromBroadcast(
            prefs.getString(PREF_DEVICE_ROUTE, null),
        ) ?: resolveHuaweiDeviceRoute(persistedAddress, persistedName)
        if (hasPersistedIdentity && !persistedRoute.isSupported) {
            currentAddress = null
            currentName = null
            currentRoute = HuaweiDeviceRoute.UNSUPPORTED
            currentBattery = BatteryParams()
            currentAnc = 1
            currentAncSubMode = null
            currentTransparencySubMode = null
            currentLowLatencyEnabled = false
            resetFreeClip2AudioState()
            resetHuaweiEqualizerState()
            knownHuaweiRoutes.clear()
            prefs.edit()
                .remove("address")
                .remove("name")
                .remove(PREF_DEVICE_ROUTE)
                .remove("anc")
                .remove(PREF_ANC_SUBMODE)
                .remove(PREF_ANC_SUBMODE_LEGACY_6I)
                .remove(PREF_ANC_SUBMODE_LEGACY_PRO3)
                .remove(PREF_TRANSPARENCY_SUBMODE)
                .remove(PREF_FREECLIP2_SPATIAL_MODE)
                .remove(PREF_FREECLIP2_SPATIAL_SCENE)
                .remove(PREF_FREECLIP2_SOUND_EFFECT)
                .remove(PREF_FREECLIP2_EQ_SELECTED_ID)
                .remove(PREF_HUAWEI_EQUALIZER_SELECTED_ID)
                .remove("left_battery")
                .remove("left_charging")
                .remove("left_connected")
                .remove("right_battery")
                .remove("right_charging")
                .remove("right_connected")
                .remove("case_battery")
                .remove("case_charging")
                .remove("case_connected")
                .apply {
                    (0x64..0x66).forEach { id ->
                        remove(PREF_FREECLIP2_CUSTOM_EQ_NAME_PREFIX + id)
                        remove(PREF_FREECLIP2_CUSTOM_EQ_GAINS_PREFIX + id)
                    }
                }
                .apply()
            Log.i(TAG, "removed unsupported legacy headset state name=${persistedName.orEmpty()}")
            return
        }
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentRoute = persistedRoute.takeIf { hasPersistedIdentity && it.isSupported }
            ?: HuaweiDeviceRoute.UNSUPPORTED
        refreshLowLatencyFromPrefs()
        currentAnc = prefs.getInt("anc", currentAnc)
        val route = currentHuaweiRoute()
        val persistedAncSubMode = when {
            prefs.contains(PREF_ANC_SUBMODE) -> prefs.getInt(PREF_ANC_SUBMODE, -1)
            prefs.contains(PREF_ANC_SUBMODE_LEGACY_6I) -> prefs.getInt(PREF_ANC_SUBMODE_LEGACY_6I, -1)
            prefs.contains(PREF_ANC_SUBMODE_LEGACY_PRO3) -> prefs.getInt(PREF_ANC_SUBMODE_LEGACY_PRO3, -1)
            else -> -1
        }.takeIf { it >= 0 }
        val persistedTransparencySubMode = prefs.getInt(PREF_TRANSPARENCY_SUBMODE, -1)
            .takeIf { prefs.contains(PREF_TRANSPARENCY_SUBMODE) && it >= 0 }
        currentAncSubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.NOISE_CANCELLATION.broadcastStatus,
            persistedAncSubMode,
            storedSubMode = null,
        )
        currentTransparencySubMode = normalizeMiLinkAncSubMode(
            route,
            NoiseControlMode.TRANSPARENCY.broadcastStatus,
            persistedTransparencySubMode,
            storedSubMode = null,
        )
        if (!route.supportsAnc || currentAnc == 3 && !route.supportsTransparency) {
            currentAnc = NoiseControlMode.OFF.broadcastStatus
        }
        if (route == HuaweiDeviceRoute.HUAWEI_FREECLIP2) {
            currentFreeClip2SpatialMode = FreeClip2SpatialAudioMode.fromExtraValue(
                prefs.getString(PREF_FREECLIP2_SPATIAL_MODE, null),
            ) ?: FreeClip2SpatialAudioMode.OFF
            currentFreeClip2SpatialScene = FreeClip2SpatialScene.fromExtraValue(
                prefs.getString(PREF_FREECLIP2_SPATIAL_SCENE, null),
            ) ?: FreeClip2SpatialScene.DEFAULT
            currentFreeClip2SoundEffect = FreeClip2SoundEffect.fromExtraValue(
                prefs.getString(PREF_FREECLIP2_SOUND_EFFECT, null),
            ) ?: FreeClip2SoundEffect.DEFAULT
            currentFreeClip2EqualizerSelectedId = prefs.getInt(
                PREF_FREECLIP2_EQ_SELECTED_ID,
                -1,
            ).takeIf { it in 0..0xFF }
            val storedPresets = (0x64..0x66).mapNotNull { id ->
                val name = prefs.getString(PREF_FREECLIP2_CUSTOM_EQ_NAME_PREFIX + id, null)
                    ?: return@mapNotNull null
                val gains = prefs.getString(PREF_FREECLIP2_CUSTOM_EQ_GAINS_PREFIX + id, null)
                    ?.split(',')
                    ?.mapNotNull(String::toIntOrNull)
                    ?: return@mapNotNull null
                HuaweiEqualizerPreset(id, name, gains)
            }
            val storedPayload = HuaweiEqualizerPresetTransport.encode(storedPresets)
            currentFreeClip2CustomPresets = HuaweiEqualizerPresetTransport.decode(
                storedPayload.ids,
                storedPayload.names,
                storedPayload.gains,
            ).orEmpty()
        } else {
            resetFreeClip2AudioState()
        }
        currentHuaweiEqualizerSelectedId = if (route == HuaweiDeviceRoute.HUAWEI_FREEBUDS6I) {
            prefs.getInt(PREF_HUAWEI_EQUALIZER_SELECTED_ID, -1)
                .takeIf { it in freeBuds6iBuiltInSoundEffectIds || it in 0x64..0x66 }
        } else {
            null
        }
        currentAddress?.let { knownHuaweiRoutes[it.uppercase()] = route }
        currentBattery = BatteryParams(
            left = PodParams(
                prefs.getInt("left_battery", currentBattery.left?.battery ?: 0),
                prefs.getBoolean("left_charging", currentBattery.left?.isCharging == true),
                prefs.getBoolean("left_connected", currentBattery.left?.isConnected == true),
                0
            ),
            right = PodParams(
                prefs.getInt("right_battery", currentBattery.right?.battery ?: 0),
                prefs.getBoolean("right_charging", currentBattery.right?.isCharging == true),
                prefs.getBoolean("right_connected", currentBattery.right?.isConnected == true),
                0
            ),
            case = PodParams(
                prefs.getInt("case_battery", currentBattery.case?.battery ?: 0),
                prefs.getBoolean("case_charging", currentBattery.case?.isCharging == true),
                prefs.getBoolean("case_connected", currentBattery.case?.isConnected == true),
                0
            )
        ).let(::normalizeBatteryAvailabilityForCurrentRoute)
    }

    private fun normalizeBatteryAvailabilityForCurrentRoute(status: BatteryParams): BatteryParams =
        if (currentHuaweiRoute().usesReportedEarbudAvailability) {
            status
        } else {
            status.normalizedEarbudAvailability()
        }
}

internal fun matchesMiLinkStateOwner(
    activeAddress: String?,
    activeRoute: HuaweiDeviceRoute,
    requestedAddress: String?,
    requestedRoute: HuaweiDeviceRoute,
): Boolean {
    val active = activeAddress?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val requested = requestedAddress?.trim()?.takeIf(String::isNotEmpty) ?: return false
    return activeRoute.isSupported &&
        requestedRoute.isSupported &&
        activeRoute == requestedRoute &&
        active.equals(requested, ignoreCase = true)
}
