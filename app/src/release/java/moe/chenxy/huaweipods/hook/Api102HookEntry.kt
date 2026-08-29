package moe.chenxy.huaweipods.hook

import android.app.Application
import android.os.Bundle
import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import java.lang.reflect.Executable
import java.util.ArrayDeque

/**
 * API 102 热重载入口。
 *
 * 旧代先关闭非 Xposed 资源并把包名保存为跨 ClassLoader 的 String；新代不会收到
 * onPackageLoaded，因此需要从 ActivityThread.currentApplication() 取得宿主 ClassLoader，
 * 再按稳定 ID 原子替换旧句柄并补装新增 Hook。
 */
class Api102HookEntry : HookEntry() {
    private var replacementHandlesById: MutableMap<String, ArrayDeque<XposedInterface.HookHandle>>? = null
    private var remainingOldHandles: MutableSet<XposedInterface.HookHandle>? = null
    private var installedGenerationHandles: MutableList<XposedInterface.HookHandle>? = null
    private var replacedHookCount = 0
    private var newHookCount = 0

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(
            AndroidLog.INFO,
            TAG,
            "API 102 entry loaded process=${param.processName} " +
                "framework=$frameworkName/$frameworkVersionCode api=$apiVersion",
        )
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val application = currentApplicationOrNull()
        val classLoader = application?.classLoader
        if (application == null || classLoader == null) {
            log(AndroidLog.ERROR, TAG, "API 102 hot reload rejected: current Application unavailable")
            return false
        }
        val loadedPackages = loadedPackageNames()
        val packageName = loadedPackages.singleOrNull() ?: application.packageName
        if (packageName.isBlank() ||
            loadedPackages.size > 1 ||
            application.packageName != packageName
        ) {
            log(
                AndroidLog.ERROR,
                TAG,
                "API 102 hot reload rejected: package mismatch " +
                    "application=${application.packageName} loaded=$loadedPackages",
            )
            return false
        }
        if (!canRestorePackageHooks(packageName, classLoader) || !canCloseLoadedGeneration()) {
            log(AndroidLog.ERROR, TAG, "API 102 hot reload rejected: preflight failed package=$packageName")
            return false
        }

        // Bundle 只能包含基本类型/String；不能携带旧模块类实例。
        val stateSaved = runCatching {
            param.setSavedInstanceState(createHotReloadState(packageName))
        }.onFailure {
            log(AndroidLog.ERROR, TAG, "API 102 hot reload rejected: save state failed", it)
        }.isSuccess
        if (!stateSaved) return false

        // close() 先关闭拦截器入口，再尽力清理全部外部资源。开始关闭后不能退回半关闭的
        // 旧代；即使个别宿主注销 API 报错，也要完成换代并让旧类尽快失去引用。
        val closeFailures = closeLoadedGeneration()
        if (closeFailures.isNotEmpty()) {
            log(
                AndroidLog.ERROR,
                TAG,
                "API 102 hot reload cleanup completed with failures " +
                    "package=$packageName count=${closeFailures.size}",
            )
        }
        log(AndroidLog.INFO, TAG, "API 102 hot reload prepared package=$packageName")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val oldHandles = param.oldHookHandles
        log(
            AndroidLog.INFO,
            TAG,
            "API 102 hot reload callback process=${param.processName} " +
                "oldHooks=${oldHandles.size}",
        )

        val application = currentApplicationOrNull()
        val savedState = param.savedInstanceState as? Bundle
        val packageName = savedState
            ?.getString(HOT_RELOAD_PACKAGE_KEY)
            ?.takeIf(String::isNotBlank)
            ?: application?.packageName
        val classLoader = application?.classLoader
        if (packageName == null || classLoader == null) {
            log(
                AndroidLog.ERROR,
                TAG,
                "API 102 hot reload restore failed: current Application/ClassLoader unavailable",
            )
            unhookHandles(oldHandles, "restore prerequisites unavailable")
            throw IllegalStateException(
                "HuaweiPods API 102 hot reload failed: current Application/ClassLoader unavailable",
            )
        }
        if (application.packageName != packageName ||
            !canRestorePackageHooks(packageName, classLoader)
        ) {
            log(
                AndroidLog.ERROR,
                TAG,
                "API 102 hot reload restore preflight failed " +
                    "application=${application.packageName} saved=$packageName",
            )
            unhookHandles(oldHandles, "restore preflight failed")
            throw IllegalStateException(
                "HuaweiPods API 102 hot reload failed: restore preflight failed",
            )
        }

        replacementHandlesById = oldHandles
            .mapNotNull { handle ->
                runCatching { handle.id }
                    .onFailure {
                        log(AndroidLog.WARN, TAG, "read old hook id failed", it)
                    }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let { id -> id to handle }
            }
            .groupByTo(linkedMapOf(), { it.first }, { it.second })
            .mapValuesTo(linkedMapOf()) { (_, handles) -> ArrayDeque(handles) }
        remainingOldHandles = LinkedHashSet(oldHandles)
        installedGenerationHandles = mutableListOf()
        replacedHookCount = 0
        newHookCount = 0

        var restored = false
        try {
            installHooksForPackage(packageName, classLoader, requireComplete = true)
            val stateFailures = savedState
                ?.let(::restoreLoadedGenerationState)
                .orEmpty()
            if (stateFailures.isNotEmpty()) {
                log(
                    AndroidLog.WARN,
                    TAG,
                    "API 102 hot reload restored hooks with partial state " +
                        "package=$packageName failures=${stateFailures.size}",
                )
            }
            restored = true
        } catch (throwable: Throwable) {
            log(
                AndroidLog.ERROR,
                TAG,
                "API 102 hot reload restore failed package=$packageName",
                throwable,
            )
            // 防止半安装的新代继续持有监听器或后台任务。
            val closeFailures = closeLoadedGeneration()
            if (closeFailures.isNotEmpty()) {
                log(
                    AndroidLog.ERROR,
                    TAG,
                    "API 102 rollback cleanup completed with failures " +
                        "package=$packageName count=${closeFailures.size}",
                )
            }
            unhookHandles(installedGenerationHandles.orEmpty(), "rollback new generation")
            throw throwable
        } finally {
            val leftovers = remainingOldHandles?.toList().orEmpty()
            val removedOldHandleCount = unhookHandles(leftovers, "restore completed")
            replacementHandlesById = null
            remainingOldHandles = null
            installedGenerationHandles = null
            log(
                if (restored) AndroidLog.INFO else AndroidLog.ERROR,
                TAG,
                "API 102 hot reload restore package=$packageName restored=$restored " +
                    "replaced=$replacedHookCount new=$newHookCount " +
                    "removed=$removedOldHandleCount failed=${leftovers.size - removedOldHandleCount}",
            )
        }
    }

    internal override fun installHook(
        executable: Executable,
        hookId: String,
        interceptor: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle {
        val hooker = XposedInterface.Hooker { chain -> interceptor(chain) }
        val oldHandle = replacementHandlesById
            ?.get(hookId)
            ?.pollFirst()
        if (oldHandle != null) {
            return runCatching {
                oldHandle.replaceHook(hooker)
            }.onSuccess {
                remainingOldHandles?.remove(oldHandle)
                replacedHookCount += 1
                installedGenerationHandles?.add(it)
            }.onFailure { throwable ->
                log(
                    AndroidLog.WARN,
                    TAG,
                    "replace hook failed id=$hookId; install a fresh handle",
                    throwable,
                )
                if (unhookHandles(listOf(oldHandle), "replace hook failed id=$hookId") == 1) {
                    remainingOldHandles?.remove(oldHandle)
                }
            }.getOrElse {
                newHookCount += 1
                hook(executable).setId(hookId).intercept(hooker).also { handle ->
                    installedGenerationHandles?.add(handle)
                }
            }
        }

        newHookCount += 1
        return hook(executable).setId(hookId).intercept(hooker).also { handle ->
            installedGenerationHandles?.add(handle)
        }
    }

    private fun currentApplicationOrNull(): Application? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.onFailure {
        log(AndroidLog.WARN, TAG, "resolve ActivityThread.currentApplication failed", it)
    }.getOrNull()

    private fun unhookHandles(
        handles: Collection<XposedInterface.HookHandle>,
        reason: String,
    ): Int = handles.count { handle ->
        runCatching {
            handle.unhook()
            true
        }.onFailure {
            log(AndroidLog.WARN, TAG, "unhook handle failed reason=$reason", it)
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "HuaweiPods-Api102"
    }
}
