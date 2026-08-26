package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** FreeClip 2 在系统宿主界面中展示的、可按设备持久化的音频状态。 */
internal data class FreeClip2AudioUiState(
    val spatialMode: FreeClip2SpatialAudioMode = FreeClip2SpatialAudioMode.OFF,
    val spatialScene: FreeClip2SpatialScene = FreeClip2SpatialScene.DEFAULT,
    val soundEffect: FreeClip2SoundEffect = FreeClip2SoundEffect.DEFAULT,
) {
    fun mergeExtraValues(
        spatialModeValue: String?,
        spatialSceneValue: String?,
        soundEffectValue: String?,
    ): FreeClip2AudioUiState = copy(
        spatialMode = FreeClip2SpatialAudioMode.fromExtraValue(spatialModeValue) ?: spatialMode,
        spatialScene = FreeClip2SpatialScene.fromExtraValue(spatialSceneValue) ?: spatialScene,
        soundEffect = FreeClip2SoundEffect.fromExtraValue(soundEffectValue) ?: soundEffect,
    )

    fun withSelection(kind: String, value: String): FreeClip2AudioUiState? = when (kind) {
        HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE ->
            FreeClip2SpatialAudioMode.fromExtraValue(value)?.let { copy(spatialMode = it) }
        HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE ->
            FreeClip2SpatialScene.fromExtraValue(value)?.let { copy(spatialScene = it) }
        HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT ->
            FreeClip2SoundEffect.fromExtraValue(value)?.let { copy(soundEffect = it) }
        else -> null
    }
}

internal fun freeClip2AudioPreferencePrefix(address: String?, name: String?): String? {
    val identity = address
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.uppercase()
        ?: name?.trim()?.takeIf(String::isNotEmpty)?.let { "name:$it" }
        ?: return null
    return "freeclip2_audio_${identity}_"
}

internal data class FreeClip2AudioSelection(
    val kind: String,
    val value: String,
)

/** Programmatic card rendering may re-enter the host click handler and must never write the device. */
internal fun shouldDispatchFreeClip2AudioSelection(internalRenderDepth: Int): Boolean =
    internalRenderDepth <= 0

/** Deduplicates the two system-host Hook entry points while a device confirmation is pending. */
internal class FreeClip2AudioPendingGate(
    private val timeoutMs: Long = 5_000L,
) {
    private var pending: FreeClip2AudioSelection? = null
    private var pendingSinceMs = 0L

    fun tryBegin(kind: String, value: String, nowMs: Long): Boolean {
        val next = FreeClip2AudioSelection(kind, value)
        if (pending == next && nowMs - pendingSinceMs in 0 until timeoutMs) return false
        pending = next
        pendingSinceMs = nowMs
        return true
    }

    fun observeConfirmed(
        spatialModeValue: String?,
        spatialSceneValue: String?,
        soundEffectValue: String?,
    ) {
        val current = pending ?: return
        val observedValue = when (current.kind) {
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_MODE -> spatialModeValue
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SPATIAL_SCENE -> spatialSceneValue
            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_SOUND_EFFECT -> soundEffectValue
            else -> return clear()
        }
        if (observedValue == current.value) clear()
    }

    /** 写入前已经在途的旧回读不能覆盖刚刚选中的本地状态。 */
    fun shouldApplyConfirmed(kind: String, value: String): Boolean {
        val current = pending ?: return true
        if (current.kind != kind) return true
        if (current.value != value) return false
        clear()
        return true
    }

    fun clear() {
        pending = null
        pendingSinceMs = 0L
    }

    internal fun current(): FreeClip2AudioSelection? = pending
}
