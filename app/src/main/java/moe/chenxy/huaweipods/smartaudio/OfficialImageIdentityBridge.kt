package moe.chenxy.huaweipods.smartaudio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import moe.chenxy.huaweipods.pods.HuaweiDeviceInfoIdentity
import moe.chenxy.huaweipods.pods.HuaweiDeviceInfoRoutePolicy
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute

internal data class OfficialImageIdentityPublishResult(
    val identityVerified: Boolean,
    val routeBound: Boolean,
    val imageScheduled: Boolean,
) {
    val routeReady: Boolean
        get() = identityVerified && routeBound
}

/** 把蓝牙系统进程确认的 DeviceInfo 身份交给模块进程，禁止在宿主进程写模块缓存。 */
internal object OfficialImageIdentityBridge {
    const val RESULT_IDENTITY_VERIFIED = "identity_verified"
    const val RESULT_ROUTE_BOUND = "route_bound"
    const val RESULT_IMAGE_SCHEDULED = "image_scheduled"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HuaweiPods-image-identity")
    }
    private val inFlight = AtomicInteger(0)

    /** ContentProvider.call 不能保证响应 interrupt；有请求执行时应在预检阶段拒绝热重载。 */
    fun canCloseForHotReload(): Boolean = inFlight.get() == 0

    fun closeForHotReload(): Boolean {
        executor.shutdownNow()
        return runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
            .getOrDefault(false)
    }

    fun publishAsync(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        identity: HuaweiDeviceInfoIdentity,
        callbackHandler: Handler,
        onComplete: (Boolean) -> Unit,
    ) {
        publishResultAsync(context, address, route, identity, callbackHandler) { result ->
            onComplete(result.imageScheduled)
        }
    }

    /** route probe 只依赖严格身份校验和绑定结果，不把图片 JobScheduler 状态当成型号权威。 */
    fun publishVerifiedRouteAsync(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        identity: HuaweiDeviceInfoIdentity,
        callbackHandler: Handler,
        onComplete: (OfficialImageIdentityPublishResult) -> Unit,
    ) {
        publishResultAsync(context, address, route, identity, callbackHandler, onComplete)
    }

    private fun publishResultAsync(
        context: Context,
        address: String,
        route: HuaweiDeviceRoute,
        identity: HuaweiDeviceInfoIdentity,
        callbackHandler: Handler,
        onComplete: (OfficialImageIdentityPublishResult) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        inFlight.incrementAndGet()
        runCatching {
            executor.execute {
                val result = if (!HuaweiDeviceInfoRoutePolicy.isCompatible(route, identity.modelId)) {
                    OfficialImageIdentityPublishResult(false, false, false)
                } else {
                    val extras = Bundle().apply {
                        putString(SmartAudioImageCache.EXTRA_ADDRESS, address)
                        putString(SmartAudioImageCache.EXTRA_MODEL_ID, identity.modelId)
                        putString(SmartAudioImageCache.EXTRA_SUB_MODEL_ID, identity.subModelId)
                    }
                    runCatching {
                        appContext.contentResolver.call(
                            SmartAudioImageCache.providerUri,
                            SmartAudioImageCache.PROVIDER_METHOD_RECORD_IDENTITY,
                            null,
                            extras,
                        )
                    }.getOrNull()?.let { response ->
                        OfficialImageIdentityPublishResult(
                            identityVerified = response.getBoolean(RESULT_IDENTITY_VERIFIED, false),
                            routeBound = response.getBoolean(RESULT_ROUTE_BOUND, false),
                            imageScheduled = response.getBoolean(RESULT_IMAGE_SCHEDULED, false),
                        )
                    } ?: OfficialImageIdentityPublishResult(false, false, false)
                }
                if (!callbackHandler.post {
                        try {
                            onComplete(result)
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    }
                ) {
                    inFlight.decrementAndGet()
                }
            }
        }.onFailure {
            inFlight.decrementAndGet()
            throw it
        }
    }
}
