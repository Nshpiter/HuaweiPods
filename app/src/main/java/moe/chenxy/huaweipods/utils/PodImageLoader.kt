package moe.chenxy.huaweipods.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import moe.chenxy.huaweipods.BuildConfig
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.config.EarphonePref
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.config.cloudImageUri
import moe.chenxy.huaweipods.config.imageUri
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.isSupported

object PodImageLoader {
    private const val MAX_CUSTOM_IMAGE_DIMENSION = 768

    fun loadBitmap(
        context: Context,
        prefs: SharedPreferences,
        address: String,
        resource: PodImageResource,
        fallbackResId: Int,
        verifiedRoute: HuaweiDeviceRoute? = null,
    ): Bitmap? {
        val earphone = currentImagePreference(prefs, address)
        val custom = runCatching {
            earphone?.imageUri(resource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (custom != null) {
            //android.util.Log.d("HuaweiPods-PodImage", "loaded custom $resource for ${earphone?.address}")
            return custom
        }
        val cloud = runCatching {
            earphone?.cloudImageUri(resource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (cloud != null) return cloud

        val moduleContext = runCatching {
            context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val configuredRoute = runCatching {
            DeviceRoutePrefs.resolve(prefs, address, earphone?.name)
        }.getOrDefault(HuaweiDeviceRoute.UNSUPPORTED)
        return BitmapFactory.decodeResource(
            moduleContext.resources,
            modelFallbackResId(
                imageFallbackRoute(verifiedRoute, configuredRoute),
                resource,
                fallbackResId,
            ),
        )
    }

    fun loadBitmapWithCustomFallback(
        context: Context,
        prefs: SharedPreferences,
        address: String,
        resource: PodImageResource,
        customFallbackResource: PodImageResource,
        fallbackResId: Int,
        verifiedRoute: HuaweiDeviceRoute? = null,
    ): Bitmap? {
        val earphone = currentImagePreference(prefs, address)
        val custom = runCatching {
            earphone?.imageUri(resource)?.let { uri -> decodeUri(context, uri) }
                ?: earphone?.imageUri(customFallbackResource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (custom != null) {
            //android.util.Log.d("HuaweiPods-PodImage", "loaded custom $resource for ${earphone?.address}")
            return custom
        }
        val cloud = runCatching {
            earphone?.cloudImageUri(resource)?.let { uri -> decodeUri(context, uri) }
        }.getOrNull()
        if (cloud != null) return cloud

        val moduleContext = runCatching {
            context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val configuredRoute = runCatching {
            DeviceRoutePrefs.resolve(prefs, address, earphone?.name)
        }.getOrDefault(HuaweiDeviceRoute.UNSUPPORTED)
        return BitmapFactory.decodeResource(
            moduleContext.resources,
            modelFallbackResId(
                imageFallbackRoute(verifiedRoute, configuredRoute),
                resource,
                fallbackResId,
            ),
        )
    }

    /** 图片偏好必须严格按当前蓝牙地址读取，禁止借用最近连接设备的图片。 */
    internal fun currentImagePreference(
        prefs: SharedPreferences,
        address: String,
    ): EarphonePref? = runCatching { PodImagePrefs.find(prefs, address) }.getOrNull()

    /** 宿主已确认机型时直接用于内置图兜底，避免独立配置尚未同步时退回通用图。 */
    internal fun imageFallbackRoute(
        verifiedRoute: HuaweiDeviceRoute?,
        configuredRoute: HuaweiDeviceRoute,
    ): HuaweiDeviceRoute = verifiedRoute?.takeIf(HuaweiDeviceRoute::isSupported)
        ?: configuredRoute

    /** 当前地址没有自定义图时，先使用机型专属图，最后才使用全局默认图。 */
    internal fun modelFallbackResId(
        route: HuaweiDeviceRoute,
        resource: PodImageResource,
        globalFallbackResId: Int,
    ): Int = when (route) {
        HuaweiDeviceRoute.HUAWEI_FREEBUDS5 -> when (resource) {
            PodImageResource.BOX -> R.drawable.img_freebuds5_box
            PodImageResource.LEFT -> R.drawable.img_freebuds5_left
            PodImageResource.RIGHT -> R.drawable.img_freebuds5_right
        }

        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I -> when (resource) {
            PodImageResource.BOX -> R.drawable.img_freebuds6i_box
            PodImageResource.LEFT -> R.drawable.img_freebuds6i_left
            PodImageResource.RIGHT -> R.drawable.img_freebuds6i_right
        }

        HuaweiDeviceRoute.HUAWEI_FREECLIP2 -> when (resource) {
            PodImageResource.BOX -> R.drawable.img_freeclip2_box
            PodImageResource.LEFT -> R.drawable.img_freeclip2_left
            PodImageResource.RIGHT -> R.drawable.img_freeclip2_right
        }

        HuaweiDeviceRoute.HUAWEI_EYEWEAR2 -> when (resource) {
            PodImageResource.BOX -> R.drawable.img_eyewear2_box
            PodImageResource.LEFT, PodImageResource.RIGHT -> globalFallbackResId
        }

        else -> globalFallbackResId
    }

    fun loadBoxBitmap(
        context: Context,
        prefs: SharedPreferences,
        address: String,
        verifiedRoute: HuaweiDeviceRoute? = null,
    ): Bitmap? {
        return loadBitmap(
            context = context,
            prefs = prefs,
            address = address,
            resource = PodImageResource.BOX,
            fallbackResId = R.drawable.img_box,
            verifiedRoute = verifiedRoute,
        )
    }


    fun loadIslandLeftBitmap(context: Context, prefs: SharedPreferences, address: String): Bitmap? {
        return loadBitmapWithCustomFallback(
            context = context,
            prefs = prefs,
            address = address,
            resource = PodImageResource.LEFT,
            customFallbackResource = PodImageResource.BOX,
            fallbackResId = R.drawable.img_left,
        )?.withIslandPadding()
    }

    fun loadIslandRightBitmap(context: Context, prefs: SharedPreferences, address: String): Bitmap? {
        return loadBitmapWithCustomFallback(
            context = context,
            prefs = prefs,
            address = address,
            resource = PodImageResource.RIGHT,
            customFallbackResource = PodImageResource.BOX,
            fallbackResId = R.drawable.img_right,
        )?.withIslandPadding()
    }

    /** 为顶部小尺寸图标预留透明边距，避免耳机柄被系统裁切。 */
    private fun Bitmap.withIslandPadding(contentScale: Float = 0.90f): Bitmap {
        // 焦点通知会跨进程传递 Icon，限制像素尺寸以避免 Binder 与内存压力。
        val canvasSize = 96
        val sourceBounds = visibleBounds() ?: Rect(0, 0, width, height)
        val scale = minOf(
            canvasSize * contentScale / sourceBounds.width().coerceAtLeast(1),
            canvasSize * contentScale / sourceBounds.height().coerceAtLeast(1),
        )
        val targetWidth = sourceBounds.width() * scale
        val targetHeight = sourceBounds.height() * scale
        val left = (canvasSize - targetWidth) / 2f
        val top = (canvasSize - targetHeight) / 2f
        return Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                this,
                sourceBounds,
                RectF(left, top, left + targetWidth, top + targetHeight),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    /** 查找真正有内容的区域，避免原图透明边距让顶部小图标被重复缩小。 */
    private fun Bitmap.visibleBounds(alphaThreshold: Int = 8): Rect? = runCatching {
        val row = IntArray(width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (x in row.indices) {
                if ((row[x] ushr 24) <= alphaThreshold) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) null else Rect(minX, minY, maxX + 1, maxY + 1)
    }.getOrNull()

    private fun decodeUri(context: Context, uri: android.net.Uri): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { input ->
                input?.let { BitmapFactory.decodeStream(it, null, bounds) }
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_CUSTOM_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_CUSTOM_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri).use { input ->
                input?.let { BitmapFactory.decodeStream(it, null, decodeOptions) }
            }
        }.getOrNull()
    }
}
