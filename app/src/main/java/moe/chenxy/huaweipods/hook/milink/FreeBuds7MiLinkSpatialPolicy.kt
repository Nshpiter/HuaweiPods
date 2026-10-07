package moe.chenxy.huaweipods.hook.milink

import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode

/** MiLink 17.2.6: native card 1=fixed, 2=head tracking; Huawei wire values are reversed. */
internal object FreeBuds7MiLinkSpatialPolicy {
    fun fromDisplay(value: Int): FreeClip2SpatialAudioMode? = when (value) {
        0 -> FreeClip2SpatialAudioMode.OFF
        1 -> FreeClip2SpatialAudioMode.FIXED
        2 -> FreeClip2SpatialAudioMode.HEAD_TRACKING
        else -> null
    }

    fun display(mode: FreeClip2SpatialAudioMode?): Int = when (mode) {
        FreeClip2SpatialAudioMode.OFF -> 0
        FreeClip2SpatialAudioMode.FIXED -> 1
        FreeClip2SpatialAudioMode.HEAD_TRACKING -> 2
        null -> -1
    }

    fun runtime(mode: FreeClip2SpatialAudioMode?): Int = when (mode) {
        FreeClip2SpatialAudioMode.HEAD_TRACKING -> 11
        else -> display(mode)
    }
}

/** Pending writes only suppress duplicate requests; device readback is authoritative. */
internal class FreeBuds7MiLinkSpatialState {
    var mode: FreeClip2SpatialAudioMode? = null
        private set
    private var pending: FreeClip2SpatialAudioMode? = null
    private var pendingAt = 0L

    fun request(mode: FreeClip2SpatialAudioMode, now: Long): Boolean {
        if (mode == pending && now - pendingAt < 5_000L) return false
        pending = mode
        pendingAt = now
        return true
    }

    fun confirm(mode: FreeClip2SpatialAudioMode) {
        // A failed write can read back the original mode before the pending timeout.
        // Accept it immediately so the card recovers and the user can retry.
        pending = null
        this.mode = mode
    }

    fun clear() {
        mode = null
        pending = null
        pendingAt = 0L
    }
}
