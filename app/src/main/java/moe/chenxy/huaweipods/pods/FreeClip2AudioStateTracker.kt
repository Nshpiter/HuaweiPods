package moe.chenxy.huaweipods.pods

/**
 * Keeps FreeClip 2 audio UI state tied to verified device readback.
 *
 * A successful RFCOMM write only means that the packet left the phone. It must not update the
 * confirmed state until a later query reports what the headset actually applied. Query tokens also
 * prevent a response queued before a newer write/query from restoring an obsolete selection.
 */
internal class FreeClip2AudioStateTracker {
    private companion object {
        const val DUPLICATE_WRITE_WINDOW_MS = 5_000L
    }

    class WriteToken internal constructor(
        internal val version: Long,
        internal val update: FreeClip2AudioState,
        internal val startedAtMs: Long,
    )

    class QueryToken internal constructor(
        internal val version: Long,
        internal val mutationVersion: Long,
    )

    private var writeVersion = 0L
    private var queryVersion = 0L
    private var mutationVersion = 0L
    private var pendingWrite: WriteToken? = null

    var confirmedState: FreeClip2AudioState? = null
        private set

    fun reset() {
        writeVersion++
        queryVersion++
        mutationVersion++
        pendingWrite = null
        confirmedState = null
    }

    /** Returns null when the same setting is already awaiting confirmation. */
    fun beginWrite(update: FreeClip2AudioState, nowMs: Long): WriteToken? {
        if (!update.hasExactlyOneField()) return null
        pendingWrite?.takeIf {
            it.update == update && nowMs - it.startedAtMs in 0 until DUPLICATE_WRITE_WINDOW_MS
        }?.let { return null }
        mutationVersion++
        queryVersion++
        return WriteToken(++writeVersion, update, nowMs).also { pendingWrite = it }
    }

    /** Returns false for a completion belonging to an older/replaced write. */
    fun completeWrite(token: WriteToken, success: Boolean): Boolean {
        if (pendingWrite?.version != token.version) return false
        if (!success) pendingWrite = null
        return true
    }

    /** 接受同一次写事务中由耳机 CRC 校验通过的回包，或官方 AAM 成功回读。 */
    fun acceptWriteConfirmation(
        token: WriteToken,
        update: FreeClip2AudioState,
    ): FreeClip2AudioState? {
        val pending = pendingWrite ?: return null
        if (pending.version != token.version || !update.observes(pending.update)) return null
        confirmedState = mergeFreeClip2AudioState(confirmedState, update)
        pendingWrite = null
        return confirmedState
    }

    fun isPending(token: WriteToken): Boolean = pendingWrite?.version == token.version

    /**
     * 新打开的系统宿主可以立即复用最近一次真实回读，避免查询节流期间显示各自的旧缓存。
     * 写入尚待确认时不能重播旧值，否则会让选择器在用户刚点击后跳回去。
     */
    fun stableRefreshSnapshot(): FreeClip2AudioState? =
        confirmedState?.takeIf { pendingWrite == null }

    fun beginQuery(): QueryToken = QueryToken(++queryVersion, mutationVersion)

    /**
     * Applies only a response from the newest query and current mutation generation. A verified
     * value for the pending field resolves the pending write, whether the device accepted or
     * rejected the requested value.
     */
    fun acceptQuery(token: QueryToken, update: FreeClip2AudioState): FreeClip2AudioState? {
        if (token.version != queryVersion || token.mutationVersion != mutationVersion) return null
        confirmedState = mergeFreeClip2AudioState(confirmedState, update)
        pendingWrite?.takeIf { update.observes(it.update) }?.let { pendingWrite = null }
        return confirmedState
    }

    /**
     * 接受智慧音频从官方状态回调中取得的权威值。
     *
     * 外部确认会使已发出的旧查询失效，避免较晚返回的 RFCOMM 缓存把官方刚刚选择的
     * 状态覆盖回去；它只会结束自己实际观测到的待确认字段。
     */
    fun acceptExternalConfirmation(update: FreeClip2AudioState): FreeClip2AudioState? {
        if (!update.hasAnyField()) return null
        mutationVersion++
        queryVersion++
        confirmedState = mergeFreeClip2AudioState(confirmedState, update)
        pendingWrite?.takeIf { update.observes(it.update) }?.let { pendingWrite = null }
        return confirmedState
    }

    internal fun pendingUpdate(): FreeClip2AudioState? = pendingWrite?.update
}

private fun FreeClip2AudioState.hasExactlyOneField(): Boolean =
    listOf(mode, scene, effect, equalizer).count { it != null } == 1

private fun FreeClip2AudioState.hasAnyField(): Boolean =
    mode != null || scene != null || effect != null || equalizer != null

private fun FreeClip2AudioState.observes(pending: FreeClip2AudioState): Boolean =
    (pending.mode != null && mode != null) ||
        (pending.scene != null && scene != null) ||
        (pending.effect != null && effect != null) ||
        (pending.equalizer != null && equalizer != null)
