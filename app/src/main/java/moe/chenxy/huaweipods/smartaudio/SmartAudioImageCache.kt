package moe.chenxy.huaweipods.smartaudio

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PersistableBundle
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection
import moe.chenxy.huaweipods.HuaweiPodsApp
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.PodImagePrefs
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.config.PodImageChangeNotifier

/** 在模块进程中下载并缓存智慧音频官方设备图；所有失败都保留内置图片兜底。 */
internal object SmartAudioImageCache {
    const val PROVIDER_METHOD_RECORD_IDENTITY = "record_smart_audio_resource_identity"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_MODEL_ID = "model_id"
    const val EXTRA_SUB_MODEL_ID = "sub_model_id"
    const val EXTRA_ROUTE_TEMPLATE = "route_template"

    val providerUri: Uri = Uri.parse("content://${PodImagePrefs.AUTHORITY}")

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_DECODE_DIMENSION = 4_096
    private const val MAX_DECODE_PIXELS = 16_777_216L
    private const val MAX_NORMALIZATION_PIXELS = 4_194_304L
    private const val VISIBLE_ALPHA_THRESHOLD = 8
    private const val STAGING_DIRECTORY = "smart_audio_image_staging"
    private const val TAG = "HuaweiPods-CloudImage"

    /** 仅从固定华为 CDN 读取指定机型的官方配色，调用方必须在 I/O 线程执行。 */
    fun loadOfficialOptions(modelId: String): List<OfficialSmartAudioResourceOption> {
        val configBytes = downloadBytes(
            uri = OfficialSmartAudioResource.configUri(modelId),
            maxBytes = OfficialSmartAudioResource.MAX_CONFIG_BYTES.toLong(),
            accept = "application/json",
        )
        return OfficialSmartAudioResource.listOptions(configBytes, modelId)
    }

    fun request(context: Context, identity: SmartAudioResourceIdentity): Boolean {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val identityPersisted = PodImagePrefs.recordLatestCloudIdentity(
            prefs = prefs,
            address = identity.address,
            modelId = identity.modelId,
            subModelId = identity.subModelId,
        )
        if (!identityPersisted) {
            Log.w(TAG, "Official image identity could not be persisted model=${identity.modelId}")
            return false
        }
        if (isReady(appContext, identity)) {
            Log.i(TAG, "Official image already ready model=${identity.modelId}/${identity.subModelId}")
            return true
        }
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: run {
            Log.w(TAG, "JobScheduler unavailable for official image model=${identity.modelId}")
            return false
        }
        val jobId = SmartAudioImageJobPolicy.jobId(identity)
        if (runCatching { scheduler.getPendingJob(jobId) }.getOrNull() != null) {
            Log.i(TAG, "Official image job already pending model=${identity.modelId}/${identity.subModelId}")
            return true
        }
        val extras = PersistableBundle().apply {
            putString(EXTRA_ADDRESS, identity.address)
            putString(EXTRA_MODEL_ID, identity.modelId)
            putString(EXTRA_SUB_MODEL_ID, identity.subModelId)
        }
        return runCatching {
            scheduler.schedule(
                JobInfo.Builder(
                    jobId,
                    ComponentName(appContext, SmartAudioImageDownloadJobService::class.java),
                )
                    .setExtras(extras)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(false)
                    .build(),
            )
        }.onFailure {
            Log.w(TAG, "Official image job scheduling failed model=${identity.modelId}", it)
        }.getOrDefault(JobScheduler.RESULT_FAILURE).also { result ->
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Official image job scheduled model=${identity.modelId}/${identity.subModelId}")
            } else {
                Log.w(TAG, "Official image job rejected model=${identity.modelId}/${identity.subModelId}")
            }
        } == JobScheduler.RESULT_SUCCESS
    }

    fun resumePending(context: Context) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val identities = PodImagePrefs.latestCloudIdentities(prefs)
        if (identities.isNotEmpty()) {
            Log.i(TAG, "Resuming ${identities.size} stored official image identities")
        }
        identities.forEach { stored ->
            SmartAudioResourceIdentityPolicy.normalize(
                address = stored.address,
                modelId = stored.modelId,
                subModelId = stored.subModelId,
            )?.let { request(appContext, it) }
        }
    }

    internal fun isReady(context: Context, identity: SmartAudioResourceIdentity): Boolean {
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val earphone = PodImagePrefs.find(prefs, identity.address) ?: return false
        if (earphone.cloudModelId != identity.modelId || earphone.cloudSubModelId != identity.subModelId) {
            return false
        }
        return earphone.cloudImagePath(PodImageResource.BOX)?.let(::File)?.isFile == true
    }

    internal fun downloadAndInstall(context: Context, identity: SmartAudioResourceIdentity) {
        if (!isLatestIdentity(context, identity)) return
        if (isReady(context, identity)) return
        throwIfInterrupted()
        val configBytes = downloadBytes(
            uri = OfficialSmartAudioResource.configUri(identity.modelId),
            maxBytes = OfficialSmartAudioResource.MAX_CONFIG_BYTES.toLong(),
            accept = "application/json",
        )
        val selection = OfficialSmartAudioResource.selectResource(configBytes, identity)
        throwIfInterrupted()
        val stagingRoot = File(context.cacheDir, STAGING_DIRECTORY)
        check(stagingRoot.mkdirs() || stagingRoot.isDirectory) { "无法创建官方图片临时目录" }
        val staging = Files.createTempDirectory(stagingRoot.toPath(), "${identity.modelId}_").toFile()
        try {
            val archive = File(staging, selection.archiveFileName)
            downloadFile(
                uri = OfficialSmartAudioResource.archiveUri(selection),
                destination = archive,
                maxBytes = OfficialSmartAudioResource.MAX_ARCHIVE_BYTES,
                accept = "application/zip, application/octet-stream",
            )
            check(
                OfficialSmartAudioResource.isArchiveSizePlausible(
                    actualBytes = archive.length(),
                    declaredSizeKb = selection.declaredSizeKb,
                ),
            ) { "官方资源包大小与配置不符" }
            throwIfInterrupted()
            val extracted = OfficialSmartAudioArchive.extract(archive, File(staging, "images"))
            listOfNotNull(extracted.box, extracted.left, extracted.right).forEach(::requireDecodablePng)
            normalizeSparseTransparentBox(extracted.box)
            installImages(context, identity, extracted)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun installImages(
        context: Context,
        identity: SmartAudioResourceIdentity,
        extracted: ExtractedSmartAudioImages,
    ) {
        val imageDirectory = PodImagePrefs.imageDir(context)
        val prefix = listOf(identity.address, identity.modelId, identity.subModelId)
            .joinToString("_")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val sourceImages = mapOf(
            PodImageResource.BOX to extracted.box,
            PodImageResource.LEFT to extracted.left,
            PodImageResource.RIGHT to extracted.right,
        )
        val installed = sourceImages.mapNotNull { (resource, source) ->
            source ?: return@mapNotNull null
            val destination = File(imageDirectory, "${prefix}_${resource.fileSuffix}.png")
            val temporary = File(imageDirectory, ".${destination.name}.tmp")
            source.inputStream().use { input ->
                FileOutputStream(temporary, false).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            moveReplacing(temporary, destination)
            resource to destination.absolutePath
        }.toMap()
        check(installed[PodImageResource.BOX]?.let(::File)?.isFile == true) {
            "官方耳机主图写入失败"
        }
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val committed = PodImagePrefs.saveCloudImagesIfLatest(
            prefs = prefs,
            service = HuaweiPodsApp.xposedService,
            address = identity.address,
            modelId = identity.modelId,
            subModelId = identity.subModelId,
            imagePaths = installed,
        )
        if (committed) {
            PodImageChangeNotifier.notify(context, identity.address)
        }
    }

    private fun isLatestIdentity(context: Context, identity: SmartAudioResourceIdentity): Boolean {
        val prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        return PodImagePrefs.isLatestCloudIdentity(
            prefs = prefs,
            address = identity.address,
            modelId = identity.modelId,
            subModelId = identity.subModelId,
        )
    }

    /**
     * 华为部分眼镜主图是大方图中间的一条窄内容；只对这种极端透明留白做裁边，
     * 避免覆盖模块中已优化过的内置图后显示得过小。
     */
    private fun normalizeSparseTransparentBox(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels > MAX_NORMALIZATION_PIXELS) return
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: error("官方耳机主图无法解码")
        try {
            val content = findAlphaContentBounds(bitmap) ?: error("官方耳机主图没有可见内容")
            val crop = SmartAudioImageBoundsPolicy.cropBounds(bitmap.width, bitmap.height, content)
                ?: return
            val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
            try {
                val temporary = File(file.parentFile, ".${file.name}.normalized.tmp")
                try {
                    FileOutputStream(temporary, false).use { output ->
                        check(cropped.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            "官方耳机主图规范化失败"
                        }
                        output.fd.sync()
                    }
                    moveReplacing(temporary, file)
                } finally {
                    temporary.delete()
                }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
        requireDecodablePng(file)
    }

    private fun findAlphaContentBounds(bitmap: Bitmap): AlphaContentBounds? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                if ((row[x] ushr 24) <= VISIBLE_ALPHA_THRESHOLD) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return null
        return AlphaContentBounds(left, top, right + 1, bottom + 1)
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun requireDecodablePng(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth in 1..MAX_DECODE_DIMENSION) { "官方 PNG 宽度不合法" }
        require(bounds.outHeight in 1..MAX_DECODE_DIMENSION) { "官方 PNG 高度不合法" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_DECODE_PIXELS) {
            "官方 PNG 像素数量过大"
        }
    }

    private fun downloadBytes(uri: URI, maxBytes: Long, accept: String): ByteArray =
        openConnection(uri, accept).useConnection { connection ->
            val expectedBytes = connection.contentLengthLong
            check(expectedBytes <= maxBytes || expectedBytes < 0L) { "官方资源响应超过大小上限" }
            connection.inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream(
                    expectedBytes.takeIf { it in 1..maxBytes }?.toInt() ?: DEFAULT_BUFFER_SIZE,
                )
                copyLimited(input, output, maxBytes)
                output.toByteArray()
            }
        }

    private fun downloadFile(uri: URI, destination: File, maxBytes: Long, accept: String) {
        openConnection(uri, accept).useConnection { connection ->
            val expectedBytes = connection.contentLengthLong
            check(expectedBytes <= maxBytes || expectedBytes < 0L) { "官方资源响应超过大小上限" }
            val actualBytes = connection.inputStream.buffered().use { input ->
                FileOutputStream(destination, false).buffered().use { output ->
                    copyLimited(input, output, maxBytes)
                }
            }
            check(expectedBytes < 0L || actualBytes == expectedBytes) { "官方资源响应长度不完整" }
        }
    }

    private fun openConnection(uri: URI, accept: String): HttpsURLConnection {
        check(OfficialSmartAudioResource.isAllowedUri(uri)) { "拒绝访问非官方智慧音频资源地址" }
        val connection = uri.toURL().openConnection() as? HttpsURLConnection
            ?: error("智慧音频资源连接不是 HTTPS")
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", accept)
        val responseCode = connection.responseCode
        check(responseCode == HttpsURLConnection.HTTP_OK) {
            "官方资源下载失败（HTTP $responseCode）"
        }
        return connection
    }

    private inline fun <T> HttpsURLConnection.useConnection(
        block: (HttpsURLConnection) -> T,
    ): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private fun copyLimited(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
    ): Long {
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            throwIfInterrupted()
            val read = input.read(buffer)
            if (read < 0) break
            written += read
            check(written <= maxBytes) { "官方资源响应超过大小上限" }
            output.write(buffer, 0, read)
        }
        output.flush()
        return written
    }

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("官方图片下载任务已停止")
        }
    }
}
