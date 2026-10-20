package moe.chenxy.huaweipods.ui

enum class ModuleHealth { CHECKING, ONLINE, RELOAD_REQUIRED, TIMEOUT }

internal fun moduleHealth(
    now: Long, started: Long, lastSeen: Long, observedBuild: String?, expectedBuild: String,
): ModuleHealth = when {
    lastSeen > 0 && now - lastSeen in 0..75_000 && !observedBuild.isNullOrBlank() ->
        if (observedBuild == expectedBuild) ModuleHealth.ONLINE else ModuleHealth.RELOAD_REQUIRED
    now - started in 0..75_000 -> ModuleHealth.CHECKING
    else -> ModuleHealth.TIMEOUT
}
