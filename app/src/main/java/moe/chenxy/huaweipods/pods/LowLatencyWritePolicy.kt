package moe.chenxy.huaweipods.pods

/** 手动操作优先于排队的自动开启，旧操作的回调不能覆盖新意图。 */
internal class LowLatencyWritePolicy {
    private var revision = 0L
    private var pending = false

    @Synchronized
    fun beginManual(): Long {
        pending = true
        return ++revision
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = revision == token

    @Synchronized
    fun finishManual(token: Long): Boolean {
        if (revision != token) return false
        pending = false
        return true
    }

    @Synchronized
    fun autoToken(): Long? = revision.takeUnless { pending }

    @Synchronized
    fun canAutoWrite(token: Long): Boolean = !pending && revision == token

    @Synchronized
    fun resetSession() {
        revision++
        pending = false
    }
}
