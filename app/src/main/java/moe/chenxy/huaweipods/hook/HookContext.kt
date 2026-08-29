package moe.chenxy.huaweipods.hook

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.config.ConfigManager

abstract class HookContext {
    lateinit var module: XposedModule
    lateinit var appClassLoader: ClassLoader
    lateinit var prefs: SharedPreferences
    lateinit var packageName: String

    private lateinit var owner: HookEntry
    private lateinit var hookIds: StableHookIdGenerator
    private lateinit var stateNamespace: String
    private val closed = AtomicBoolean(false)
    private val closeActionsLock = Any()
    private val closeActions = mutableListOf<() -> Unit>()

    abstract fun onHook()

    /**
     * 释放当前 Hook 自己注册的接收器、回调和后台任务。
     *
     * 该方法只会调用一次。具体 Hook 可以按需覆盖；通用的偏好监听器由 [HookEntry]
     * 统一释放，避免每个 Hook 重复管理。
     */
    protected open fun onClose() = Unit

    /**
     * 热重载关闭前的无副作用检查。需要恢复宿主 View 或等待线程退出的 Hook 可以覆盖，
     * 但不得在这里真正注销/停止资源。
     */
    protected open fun onCanClose(): Boolean = true

    /** 只允许写入 Framework Bundle 支持的基本类型、String 及其数组。 */
    protected open fun onSaveHotReloadState(outState: Bundle) = Unit

    /** Hook 和 Receiver 安装完成后恢复当前宿主会话。 */
    protected open fun onRestoreHotReloadState(savedState: Bundle) = Unit

    internal fun canClose(): Boolean = !closed.get() && runCatching { onCanClose() }.getOrDefault(false)

    internal fun attach(
        owner: HookEntry,
        namespace: String,
        classLoader: ClassLoader,
        hostPackageName: String,
        remotePrefs: SharedPreferences,
    ) {
        check(!closed.get()) { "Closed HookContext cannot be attached again" }
        this.owner = owner
        module = owner
        appClassLoader = classLoader
        packageName = hostPackageName
        prefs = remotePrefs
        stateNamespace = namespace
        hookIds = StableHookIdGenerator(namespace)
    }

    internal fun saveHotReloadState(parent: Bundle) {
        val state = Bundle()
        onSaveHotReloadState(state)
        if (!state.isEmpty) parent.putBundle(stateNamespace, state)
    }

    internal fun restoreHotReloadState(parent: Bundle) {
        parent.getBundle(stateNamespace)?.let(::onRestoreHotReloadState)
    }

    /** 注册一个随当前 Hook 生命周期反向释放的动作。 */
    protected fun registerCloseAction(action: () -> Unit) {
        val runImmediately = synchronized(closeActionsLock) {
            if (closed.get()) {
                true
            } else {
                closeActions += action
                false
            }
        }
        if (runImmediately) action()
    }

    /**
     * 幂等关闭 Hook。关闭后仍留在旧句柄中的拦截器会透明调用原方法，直到
     * API 102 用新拦截器替换句柄或将其卸载。
     */
    internal fun close(): List<Throwable> {
        if (!closed.compareAndSet(false, true)) return emptyList()

        val failures = mutableListOf<Throwable>()
        runCatching { onClose() }.exceptionOrNull()?.let(failures::add)
        val actions = synchronized(closeActionsLock) {
            closeActions.asReversed().toList().also { closeActions.clear() }
        }
        actions.forEach { action ->
            runCatching(action).exceptionOrNull()?.let(failures::add)
        }
        return failures
    }

    internal fun isClosedForTest(): Boolean = closed.get()

    /** 当前宿主 Application；热重载新代不会重放 onPackageLoaded，可用于恢复 Receiver/UI。 */
    protected fun currentApplicationOrNull(): Application? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.getOrNull()

    /** 在宿主主线程同步执行 View 清理；超时返回 false，动作异常会在调用线程重新抛出。 */
    protected fun runOnMainThreadBlocking(
        timeoutMs: Long = DEFAULT_MAIN_THREAD_TIMEOUT_MS,
        action: () -> Unit,
    ): Boolean {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return true
        }

        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        val posted = Handler(Looper.getMainLooper()).post {
            try {
                action()
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                latch.countDown()
            }
        }
        if (!posted || !latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return false
        failure?.let { throw it }
        return true
    }

    fun fakeDeviceId(): String = ConfigManager.fakeDeviceId()

    fun fakeSupport(): String = ConfigManager.fakeSupport()

    fun refreshConfig() {
        ConfigManager.refreshFromPrefs(prefs)
    }

    fun findClass(name: String): Class<*> = Class.forName(name, false, appClassLoader)

    fun findMethod(className: String, methodName: String, vararg parameterTypes: Class<*>): Method =
        findClass(className).getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }

    fun findConstructor(className: String, vararg parameterTypes: Class<*>): Constructor<*> =
        findClass(className).getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }

    fun findMethodByParamCount(className: String, methodName: String, paramCount: Int): Method =
        findClass(className).declaredMethods.first { it.name == methodName && it.parameterTypes.size == paramCount }
            .apply { isAccessible = true }

    fun findConstructorByParamCount(className: String, paramCount: Int): Constructor<*> =
        findClass(className).declaredConstructors.first { it.parameterTypes.size == paramCount }
            .apply { isAccessible = true }

    fun hookAfter(method: Method, block: HookParam.() -> Unit) {
        owner.installHook(method, hookIds.next("after", method)) { chain ->
            val result = chain.proceed()
            if (closed.get()) result else HookParam(chain, result).apply(block).result
        }
    }

    fun hookBefore(method: Method, block: HookParam.() -> Unit) {
        owner.installHook(method, hookIds.next("before", method)) { chain ->
            if (closed.get()) {
                chain.proceed()
            } else {
                val param = HookParam(chain, null).apply(block)
                if (param.hasResult) param.result else chain.proceed()
            }
        }
    }

    fun hookConstructorAfter(constructor: Constructor<*>, block: HookParam.() -> Unit) {
        owner.installHook(constructor, hookIds.next("constructor-after", constructor)) { chain ->
            chain.proceed().also {
                if (!closed.get()) HookParam(chain, it).apply(block)
            }
        }
    }

    private companion object {
        const val DEFAULT_MAIN_THREAD_TIMEOUT_MS = 2_000L
    }
}

/** 为 API 102 生成跨模块版本稳定、且不依赖 ClassLoader 身份的 Hook ID。 */
internal class StableHookIdGenerator(namespace: String) {
    private val normalizedNamespace = namespace.trim().also {
        require(it.isNotEmpty()) { "Hook namespace must not be blank" }
    }
    private val occurrences = mutableMapOf<String, Int>()

    @Synchronized
    fun next(kind: String, executable: Executable): String {
        val signature = buildString {
            append(executable.declaringClass.name)
            append('#')
            append(if (executable is Constructor<*>) "<init>" else executable.name)
            append('(')
            append(executable.parameterTypes.joinToString(",") { it.name })
            append(')')
            if (executable is Method) {
                append(':')
                append(executable.returnType.name)
            }
        }
        val key = "$kind|$signature"
        val occurrence = occurrences.getOrDefault(key, 0)
        occurrences[key] = occurrence + 1
        return "HuaweiPods/$normalizedNamespace/$kind/${key.sha256Prefix()}/$occurrence"
    }
}

private fun String.sha256Prefix(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

object Log {
    @Volatile
    var module: XposedModule? = null

    fun v(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun i(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun d(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun d(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }
}

class HookParam(private val chain: XposedInterface.Chain, initialResult: Any?) {
    val args: List<Any?> = chain.args
    val instance: Any? = chain.thisObject
    var hasResult = false
        private set
    var result: Any? = initialResult
        set(value) {
            hasResult = true
            field = value
        }

    fun proceedWithArgs(vararg newArgs: Any?): Any? {
        return chain.proceed(newArgs.copyOf()).also {
            result = it
        }
    }
}

fun getObjectField(instance: Any?, fieldName: String): Any? {
    if (instance == null) return null
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        runCatching {
            return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
        }
        cls = cls.superclass
    }
    throw NoSuchFieldException(fieldName)
}

fun setObjectField(instance: Any?, fieldName: String, value: Any?) {
    if (instance == null) return
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        runCatching {
            cls.getDeclaredField(fieldName).apply { isAccessible = true }.set(instance, value)
            return
        }
        cls = cls.superclass
    }
    throw NoSuchFieldException(fieldName)
}

fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
    if (instance == null) return null
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
            it.isAccessible = true
            return it.invoke(instance, *args)
        }
        cls = cls.superclass
    }
    throw NoSuchMethodException(methodName)
}
