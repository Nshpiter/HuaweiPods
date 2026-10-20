package moe.chenxy.huaweipods.pods

import java.util.Locale

internal data class HuaweiDeviceRouteProbeSession(
    val address: String,
    val generation: Long,
    val nonce: String,
) {
    fun matches(
        resultAddress: String?,
        resultGeneration: Long,
        resultNonce: String?,
    ): Boolean =
        generation == resultGeneration &&
            address.equals(resultAddress?.trim(), ignoreCase = true) &&
            nonce == resultNonce
}

/** 用户主动点选未知音频设备时的窄探测策略；不负责后台扫描或名称猜测。 */
internal object HuaweiDeviceRouteProbePolicy {
    const val REQUEST_SENDER_PACKAGE = "moe.chenxy.huaweipods.coloros"
    const val RESULT_SENDER_PACKAGE = "com.android.bluetooth"
    const val MIN_PROBE_INTERVAL_MS = 15_000L

    private val addressRegex = Regex("^[0-9A-F]{2}(?::[0-9A-F]{2}){5}$")
    private val nonceRegex = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    )
    private val subModelIdRegex = Regex("^[0-9A-F]{2}$")

    fun session(
        address: String?,
        generation: Long,
        nonce: String?,
    ): HuaweiDeviceRouteProbeSession? {
        if (generation <= 0L) return null
        val normalizedAddress = address
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf(addressRegex::matches)
            ?: return null
        val confirmedNonce = nonce?.takeIf(nonceRegex::matches) ?: return null
        return HuaweiDeviceRouteProbeSession(normalizedAddress, generation, confirmedNonce)
    }

    /** App 端只允许用户点选的已配对音频设备发起请求。 */
    fun mayRequest(
        bonded: Boolean,
        audioDevice: Boolean,
        requestAlreadyPending: Boolean,
    ): Boolean = bonded && audioDevice && !requestAlreadyPending

    /** 即使系统把设备标为 connected，未知 route 仍需进入点选识别，不能直接打开空详情。 */
    fun shouldOpenConnectedDetails(
        connected: Boolean,
        resolvedRouteSupported: Boolean,
    ): Boolean = connected && resolvedRouteSupported

    /** 蓝牙进程再次收紧到已配对且由系统确认仍连接的设备。 */
    fun mayProbeInBluetoothProcess(
        bonded: Boolean,
        systemConnected: Boolean,
    ): Boolean = bonded && systemConnected

    /** 只接受实包表中已有的现代 modelId；老协议 FreeBuds 3 和未知值返回 null。 */
    fun resolveVerifiedRoute(
        modelId: String?,
        subModelId: String?,
    ): HuaweiDeviceRoute? {
        if (subModelId?.matches(subModelIdRegex) != true) return null
        return modelId?.let { HuaweiDeviceInfoRoutePolicy.routeForModelId(it) }
    }

    fun cooldownAllows(lastStartedAtMs: Long?, nowMs: Long): Boolean =
        lastStartedAtMs == null ||
            nowMs < lastStartedAtMs ||
            nowMs - lastStartedAtMs >= MIN_PROBE_INTERVAL_MS

    fun isTrustedRequestSender(packageName: String?): Boolean =
        packageName == REQUEST_SENDER_PACKAGE

    fun isTrustedResultSender(packageName: String?): Boolean =
        packageName == RESULT_SENDER_PACKAGE
}
