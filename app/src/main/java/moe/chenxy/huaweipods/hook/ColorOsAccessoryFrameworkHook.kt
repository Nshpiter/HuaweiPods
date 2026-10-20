package moe.chenxy.huaweipods.hook

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.utils.ModuleResourceResolver
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import java.util.WeakHashMap

internal fun shouldSuppressColorOsPopupClose(
    isHuaweiPodsCard: Boolean,
    isUserClickInProgress: Boolean,
    automaticCloseAlreadySuppressed: Boolean,
): Boolean = isHuaweiPodsCard &&
    !isUserClickInProgress &&
    !automaticCloseAlreadySuppressed

/** Keeps HuaweiPods' explicitly marked headset card visible in ColorOS Quick Connect. */
object ColorOsAccessoryFrameworkHook : HookContext() {
    private const val DIALOG_ACTIVITY =
        "com.oplus.pantaconnect.appcore.devicediscovery.ui.DialogActivity"
    private const val USER_CLOSE_WINDOW_MS = 3_000L

    /**
     * 弹窗数据的 protobuf 外层类：ColorOS 17（快速设备连接 17.6.x）保留了原名，
     * ColorOS 16.1（17.4.x）被混淆为 `fd.c`。
     */
    private val POPUP_BASE_VIEW_DATA_CLASSES = listOf(
        "com.oplus.pantaconnect.data.BaseViewData",
        "fd.c",
    )
    private val automaticCloseSuppressed = WeakHashMap<Any, Boolean>()
    private val lastUserTouchElapsed = WeakHashMap<Any, Long>()
    private val userClickDepth = ThreadLocal<Int>()
    private val registrationLock = Any()
    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var handshakeReceiver: BroadcastReceiver? = null
    @Volatile
    private var accessoryContext: Context? = null
    @Volatile
    private var popupImageFileName: String? = null
    @Volatile
    private var popupFallbackDrawableName: String? = null

    override fun onHook() {
        hookPopupImageResources()
        hookUserClicks()
        runCatching {
            val activityClass = findClass(DIALOG_ACTIVITY)
            runCatching {
                val onCreate = activityClass.getDeclaredMethod("onCreate", Bundle::class.java).apply {
                    isAccessible = true
                }
                hookBefore(onCreate) {
                    val activity = instance ?: return@hookBefore
                    val card = readPopupCard(activity) ?: return@hookBefore
                    if (isHuaweiPodsCard(card)) {
                        popupImageFileName =
                            ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(card.packageName)
                        popupFallbackDrawableName =
                            ColorOsAccessoryPopupBridge.popupFallbackDrawableFromMarker(card.packageName)
                    }
                }
            }.onFailure {
                Log.w("HuaweiPods", "ColorOS Quick Connect image marker hook unavailable", it)
            }
            runCatching {
                val dispatchTouchEvent = activityClass.getMethod(
                    "dispatchTouchEvent",
                    MotionEvent::class.java,
                ).apply { isAccessible = true }
                hookBefore(dispatchTouchEvent) {
                    val activity = instance ?: return@hookBefore
                    val event = args.firstOrNull() as? MotionEvent ?: return@hookBefore
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        synchronized(lastUserTouchElapsed) {
                            lastUserTouchElapsed[activity] = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }.onFailure {
                Log.w("HuaweiPods", "ColorOS Quick Connect touch tracking unavailable", it)
            }
            val finishDialog = findPopupFinishMethod(activityClass)
            hookBefore(finishDialog) {
                val activity = args.firstOrNull() ?: return@hookBefore
                if (!isHuaweiPodsCard(readPopupCard(activity))) return@hookBefore
                val now = SystemClock.elapsedRealtime()
                val lastTouch = synchronized(lastUserTouchElapsed) {
                    lastUserTouchElapsed[activity]
                }
                val touchAge = lastTouch?.let { now - it }
                val isRecentUserTouch = touchAge != null && touchAge in 0..USER_CLOSE_WINDOW_MS
                val isUserClickInProgress = (userClickDepth.get() ?: 0) > 0 || isRecentUserTouch
                val wasAutomaticCloseAlreadySuppressed = synchronized(automaticCloseSuppressed) {
                    automaticCloseSuppressed[activity] == true
                }
                if (shouldSuppressColorOsPopupClose(
                        isHuaweiPodsCard = true,
                        isUserClickInProgress = isUserClickInProgress,
                        automaticCloseAlreadySuppressed = wasAutomaticCloseAlreadySuppressed,
                    )
                ) {
                    synchronized(automaticCloseSuppressed) {
                        automaticCloseSuppressed[activity] = true
                    }
                    result = null
                    Log.i("HuaweiPods", "Suppressed ColorOS automatic headset card close")
                } else if (isUserClickInProgress) {
                    synchronized(lastUserTouchElapsed) {
                        lastUserTouchElapsed.remove(activity)
                    }
                    Log.i(
                        "HuaweiPods",
                        "Allowed ColorOS headset card close after user action (touchAgeMs=$touchAge)",
                    )
                }
            }
        }.onFailure {
            Log.w("HuaweiPods", "ColorOS Quick Connect close guard unavailable", it)
        }

        hookAfter(
            Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
                isAccessible = true
            },
        ) {
            (args[0] as? Context)?.let(::registerHostHandshake)
        }
        // API 102 热重载不会重放 Application.attach()；新代直接用当前 Application 恢复握手。
        currentApplicationOrNull()?.let(::registerHostHandshake)
    }

    override fun onClose() {
        synchronized(registrationLock) {
            val receiver = handshakeReceiver
            val context = accessoryContext
            if (receiver != null && context != null) {
                runCatching { context.unregisterReceiver(receiver) }
            }
            handshakeReceiver = null
            receiverRegistered = false
        }
        accessoryContext = null
        popupImageFileName = null
        popupFallbackDrawableName = null
        synchronized(automaticCloseSuppressed) { automaticCloseSuppressed.clear() }
        synchronized(lastUserTouchElapsed) { lastUserTouchElapsed.clear() }
    }

    /** Marks close callbacks reached synchronously from a real ColorOS view click. */
    private fun hookUserClicks() {
        val performClick = View::class.java.getDeclaredMethod("performClick").apply {
            isAccessible = true
        }
        hookBefore(performClick) {
            val view = instance as? View
            val activity = view?.context?.findActivity()
            val resourceName = view?.id?.takeIf { it != View.NO_ID }?.let { id ->
                runCatching { view.resources.getResourceEntryName(id) }.getOrNull()
            }
            if (
                activity?.javaClass?.name == DIALOG_ACTIVITY &&
                resourceName in setOf("button_single_big", "button_close") &&
                isHuaweiPodsCard(readPopupCard(activity))
            ) {
                synchronized(lastUserTouchElapsed) {
                    lastUserTouchElapsed.remove(activity)
                }
                activity.finish()
                result = true
                Log.i("HuaweiPods", "Closed ColorOS headset card from $resourceName")
                return@hookBefore
            }
            userClickDepth.set((userClickDepth.get() ?: 0) + 1)
        }
        hookAfter(performClick) {
            val nextDepth = (userClickDepth.get() ?: 1) - 1
            if (nextDepth <= 0) userClickDepth.remove() else userClickDepth.set(nextDepth)
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? Activity
    }

    /**
     * 自动关闭卡片的静态入口 `static void x(DialogActivity)`：16.1 名为 `h`，17.6 名为 `g`。
     * 名称随混淆变化，按签名定位；出现多个候选时放弃，避免拦截到其他逻辑。
     */
    internal fun findPopupFinishMethod(activityClass: Class<*>): java.lang.reflect.Method {
        val candidates = activityClass.declaredMethods.filter { method ->
            java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(activityClass))
        }
        return candidates.singleOrNull()?.apply { isAccessible = true }
            ?: throw NoSuchMethodException(
                "static void (DialogActivity) candidates=${candidates.map { it.name }}",
            )
    }

    private data class PopupCard(val packageName: String?, val title: String?)

    private fun readPopupCard(activity: Any): PopupCard? = runCatching {
        val intent = activity.javaClass.getMethod("getIntent").invoke(activity) as? Intent
            ?: return@runCatching null
        val outer = intent.getBundleExtra("intent_bundle") ?: return@runCatching null
        val bytes = outer.getByteArray("base_view_data") ?: return@runCatching null
        val baseClass = POPUP_BASE_VIEW_DATA_CLASSES.firstNotNullOfOrNull { name ->
            runCatching { findClass(name) }.getOrNull()
        } ?: throw ClassNotFoundException(POPUP_BASE_VIEW_DATA_CLASSES.joinToString())
        val base = baseClass.getMethod("parseFrom", ByteArray::class.java)
            .invoke(null, bytes)
        val plugin = baseClass.getMethod("getPluginViewData").invoke(base)
            ?: return@runCatching null
        val packageName = plugin.javaClass.getMethod("getPackageName").invoke(plugin) as? String
        val title = plugin.javaClass.getMethod("getDialogTitle").invoke(plugin) as? String
        PopupCard(packageName, title)
    }.onFailure {
        Log.w("HuaweiPods", "Unable to identify ColorOS Quick Connect card", it)
    }.getOrNull()

    private fun isHuaweiPodsCard(card: PopupCard?): Boolean =
        ColorOsAccessoryPopupBridge.isHuaweiPodsMarker(card?.packageName)

    private fun registerHostHandshake(sourceContext: Context) {
        val context = sourceContext.applicationContext ?: sourceContext
        accessoryContext = context
        synchronized(registrationLock) {
            if (receiverRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    if (intent?.action == HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_PROBE) {
                        sendReady(context)
                    }
                }
            }
            runCatching {
                context.registerReceiver(
                    receiver,
                    IntentFilter(HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_PROBE),
                    "android.permission.BLUETOOTH_PRIVILEGED",
                    null,
                    Context.RECEIVER_EXPORTED,
                )
            }.onSuccess {
                handshakeReceiver = receiver
                receiverRegistered = true
                sendReady(context)
            }.onFailure {
                Log.w("HuaweiPods", "ColorOS popup host handshake registration failed", it)
            }
        }
    }

    private fun sendReady(context: Context) {
        context.sendBroadcast(
            Intent(HuaweiPodsAction.ACTION_COLOROS_POPUP_HOST_READY).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
        )
    }

    private fun hookPopupImageResources() {
        listOf(
            runCatching {
                Resources::class.java.getDeclaredMethod(
                    "getDrawable",
                    Int::class.javaPrimitiveType,
                )
            }.getOrNull(),
            runCatching {
                Resources::class.java.getDeclaredMethod(
                    "getDrawable",
                    Int::class.javaPrimitiveType,
                    Resources.Theme::class.java,
                )
            }.getOrNull(),
        ).filterNotNull().forEach { method ->
            hookBefore(method) {
                val resourceId = args.firstOrNull() as? Int ?: return@hookBefore
                if (resourceId != ColorOsAccessoryPopupBridge.POPUP_IMAGE_DRAWABLE_ID) {
                    return@hookBefore
                }
                loadHuaweiPodsPopupDrawable(instance as? Resources)?.let { result = it }
            }
        }
    }

    private fun loadHuaweiPodsPopupDrawable(resources: Resources?): Drawable? {
        val context = accessoryContext ?: return null
        val targetResources = resources ?: context.resources
        val fileName = popupImageFileName
        if (fileName != null) {
            val cachedImage = runCatching {
                context.contentResolver.openInputStream(
                    Uri.Builder()
                        .scheme("content")
                        .authority("${BuildConfig.APPLICATION_ID}.podimages")
                        .appendPath(fileName)
                        .build(),
                )?.use(BitmapFactory::decodeStream)
            }.onFailure {
                Log.w("HuaweiPods", "ColorOS popup cached image unavailable; using bundled image", it)
            }.getOrNull()
            if (cachedImage != null) return BitmapDrawable(targetResources, cachedImage)
        }
        return runCatching {
            val moduleResources = ModuleResourceResolver.resources(context)
                ?: return@runCatching null
            // 型号专属内置图由蓝牙进程按已识别的机型写入卡片标记；未知机型使用通用耳机图。
            val modelDrawable = popupFallbackDrawableName
                ?.let { moduleResources.getIdentifier(it, "drawable", BuildConfig.APPLICATION_ID) }
                ?.takeIf { it != 0 }
            val bitmap = BitmapFactory.decodeResource(moduleResources, modelDrawable ?: R.drawable.img_box)
                ?: return@runCatching null
            BitmapDrawable(targetResources, bitmap)
        }.onFailure {
            Log.w("HuaweiPods", "ColorOS popup bundled headset image unavailable", it)
        }.getOrNull()
    }
}
