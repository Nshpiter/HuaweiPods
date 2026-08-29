package moe.chenxy.huaweipods.hook

import android.content.SharedPreferences
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Executable
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.LowLatencyPrefs
import moe.chenxy.huaweipods.hook.milink.MiLinkServiceHook
import moe.chenxy.huaweipods.pods.HuaweiDeviceRouteResolver
import moe.chenxy.huaweipods.utils.ModuleResourceResolver

open class HookEntry : XposedModule() {
    private val TAG = "HuaweiPods-HookEntry"
    private val lifecycleLock = Any()
    private val loadedPackages = linkedMapOf<String, ClassLoader>()
    private val loadedHooks = mutableListOf<LoadedHook>()
    private val strictInstall = ThreadLocal.withInitial { false }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        installHooksForPackage(param.packageName, param.defaultClassLoader)
    }

    /**
     * 安装指定宿主包的完整 Hook 集合。
     *
     * API 102 热重载不会再次触发 onPackageLoaded，因此新模块入口会使用保存的包名和
     * ActivityThread.currentApplication() 的 ClassLoader 主动调用这里恢复 Hook。
     */
    internal fun installHooksForPackage(
        packageName: String,
        classLoader: ClassLoader,
        requireComplete: Boolean = false,
    ) {
        ModuleResourceResolver.initialize(
            moduleApplicationInfo = moduleApplicationInfo,
            hotReloadEnabled = apiVersion >= 102,
        )
        synchronized(lifecycleLock) {
            loadedPackages[packageName] = classLoader
        }

        val previousStrict = strictInstall.get()
        strictInstall.set(requireComplete)
        try {
            BuildVariantHooks.installForPackage(this, packageName, classLoader)

            when (packageName) {
                "com.android.bluetooth" -> {
                    loadHook("bluetooth-headset-state", HeadsetStateDispatcher, classLoader, packageName)
                    loadHook("bluetooth-upstream", BluetoothUpstreamHeadsetHook(), classLoader, packageName)
                }
                "com.android.settings" ->
                    loadHook("settings-headset", SettingsHeadsetHook, classLoader, packageName)
                "com.milink.service" ->
                    loadHook("milink-service", MiLinkServiceHook, classLoader, packageName)
                "com.xiaomi.bluetooth" -> {
                    loadHook("mi-bluetooth-toast", MiBluetoothToastHook, classLoader, packageName)
                    loadHook("bluetooth-upstream", BluetoothUpstreamHeadsetHook(), classLoader, packageName)
                }
            }
        } finally {
            strictInstall.set(previousStrict)
        }
    }

    /** 热重载关闭旧代之前，确认新代至少具备重新读取配置并识别宿主包的条件。 */
    internal fun canRestorePackageHooks(packageName: String, classLoader: ClassLoader): Boolean {
        if (!supportsPackage(packageName)) {
            log(android.util.Log.ERROR, TAG, "hot reload preflight rejected unsupported package=$packageName")
            return false
        }
        if (classLoader === ClassLoader.getSystemClassLoader() && packageName != "android") {
            log(android.util.Log.ERROR, TAG, "hot reload preflight rejected system classloader package=$packageName")
            return false
        }
        return runCatching {
            // 实际读取一次，不能只判断 RemotePreferences 代理对象非空。
            getRemotePreferences(ConfigManager.PREFS_NAME)
                .contains(ConfigManager.PREF_KEY_CONFIG_JSON)
            true
        }.onFailure {
            log(android.util.Log.ERROR, TAG, "hot reload preflight remote prefs failed package=$packageName", it)
        }.getOrDefault(false)
    }

    private fun supportsPackage(packageName: String): Boolean =
        packageName == "com.android.bluetooth" ||
            packageName == "com.android.settings" ||
            packageName == "com.milink.service" ||
            packageName == "com.xiaomi.bluetooth" ||
            BuildVariantHooks.supportsPackage(packageName)

    internal fun loadHook(
        namespace: String,
        hook: HookContext,
        classLoader: ClassLoader,
        packageName: String,
    ) {
        Log.module = this
        // API 102 may expose a read-only/temporarily unavailable proxy while the
        // host process is starting.  Do not let that IPC failure crash the host.
        val remotePrefs = runCatching {
            getRemotePreferences(ConfigManager.PREFS_NAME)
        }.onFailure {
            log(android.util.Log.WARN, TAG, "get remote prefs failed for $packageName", it)
        }.getOrNull() ?: if (strictInstall.get() == true) {
            throw IllegalStateException("Remote preferences unavailable for $packageName")
        } else {
            return
        }

        hook.attach(this, namespace, classLoader, packageName, remotePrefs)
        LowLatencyPrefs.attachHookPreferences(remotePrefs)
        HuaweiDeviceRouteResolver.init(remotePrefs)
        Log.d(
            TAG,
            "loadHook package=$packageName namespace=$namespace hook=${hook.javaClass.simpleName}",
        )
        ConfigManager.init(remotePrefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(sharedPreferences)
            }
            if (DeviceRoutePrefs.isBindingKey(key)) {
                HuaweiDeviceRouteResolver.refreshBindings()
            }
        }
        val listenerRegistered = runCatching {
            remotePrefs.registerOnSharedPreferenceChangeListener(configListener)
            true
        }.onFailure {
            log(android.util.Log.WARN, TAG, "register remote prefs listener failed for $packageName", it)
        }.getOrDefault(false)
        val loadedHook = LoadedHook(
            namespace = namespace,
            packageName = packageName,
            classLoader = classLoader,
            context = hook,
            prefs = remotePrefs,
            listener = configListener,
            listenerRegistered = listenerRegistered,
        )
        synchronized(lifecycleLock) {
            loadedHooks += loadedHook
        }
        try {
            hook.onHook()
        } catch (throwable: Throwable) {
            synchronized(lifecycleLock) {
                loadedHooks.remove(loadedHook)
            }
            closeLoadedHook(loadedHook)
            throw throwable
        }
    }

    /** API 101 的安装路径；API 102 变体覆盖此方法并设置稳定 ID/替换旧句柄。 */
    internal open fun installHook(
        executable: Executable,
        hookId: String,
        interceptor: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle = hook(executable).intercept { chain -> interceptor(chain) }

    /** 返回当前进程已装载的宿主包名，不把 ClassLoader 传给热重载的新类加载器。 */
    internal fun loadedPackageNames(): List<String> = synchronized(lifecycleLock) {
        loadedPackages.keys.toList()
    }

    internal fun createHotReloadState(packageName: String): Bundle = Bundle().apply {
        putString(HOT_RELOAD_PACKAGE_KEY, packageName)
        synchronized(lifecycleLock) { loadedHooks.toList() }.forEach { loadedHook ->
            loadedHook.context.saveHotReloadState(this)
        }
    }

    internal fun restoreLoadedGenerationState(savedState: Bundle): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        synchronized(lifecycleLock) { loadedHooks.toList() }.forEach { loadedHook ->
            runCatching {
                loadedHook.context.restoreHotReloadState(savedState)
            }.onFailure { throwable ->
                failures += throwable
                log(
                    android.util.Log.WARN,
                    TAG,
                    "restore hook state failed package=${loadedHook.packageName} " +
                        "namespace=${loadedHook.namespace}",
                    throwable,
                )
            }
        }
        return failures
    }

    /** 无副作用检查全部 HookContext 是否能完整关闭。 */
    internal fun canCloseLoadedGeneration(): Boolean {
        val hooks = synchronized(lifecycleLock) { loadedHooks.toList() }
        return hooks.all { loadedHook ->
            loadedHook.context.canClose().also { canClose ->
                if (!canClose) {
                    log(
                        android.util.Log.ERROR,
                        TAG,
                        "hot reload preflight close rejected package=${loadedHook.packageName} " +
                            "namespace=${loadedHook.namespace}",
                    )
                }
            }
        }
    }

    /**
     * 关闭旧代 HookContext 并注销通用偏好监听器。调用前必须先通过
     * [canCloseLoadedGeneration]；开始关闭后，即使某项清理失败也继续完成其余清理。
     */
    internal fun closeLoadedGeneration(): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        val hooks = synchronized(lifecycleLock) {
            loadedPackages.clear()
            loadedHooks.asReversed().toList().also { loadedHooks.clear() }
        }
        hooks.forEach { loadedHook ->
            failures += closeLoadedHook(loadedHook)
        }
        return failures
    }

    private fun closeLoadedHook(loadedHook: LoadedHook): List<Throwable> {
        val failures = loadedHook.context.close().toMutableList()
        failures.forEach { throwable ->
            log(
                android.util.Log.ERROR,
                TAG,
                "close hook failed package=${loadedHook.packageName} " +
                    "namespace=${loadedHook.namespace}",
                throwable,
            )
        }
        if (loadedHook.listenerRegistered) {
            runCatching {
                loadedHook.prefs.unregisterOnSharedPreferenceChangeListener(loadedHook.listener)
            }.onFailure { throwable ->
                failures += throwable
                log(
                    android.util.Log.ERROR,
                    TAG,
                    "unregister prefs listener failed package=${loadedHook.packageName} " +
                        "namespace=${loadedHook.namespace}",
                    throwable,
                )
            }
        }
        return failures
    }

    private data class LoadedHook(
        val namespace: String,
        val packageName: String,
        val classLoader: ClassLoader,
        val context: HookContext,
        val prefs: SharedPreferences,
        val listener: SharedPreferences.OnSharedPreferenceChangeListener,
        val listenerRegistered: Boolean,
    )

    companion object {
        internal const val HOT_RELOAD_PACKAGE_KEY = "host_package"
    }
}
