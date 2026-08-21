package moe.chenxy.huaweipods.debugcapture

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import moe.chenxy.huaweipods.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Debug 抓包的唯一持久化入口。
 *
 * 所有写入都在同一把锁内完成，并在每条 JSONL 后同步到磁盘，避免进程被杀时留下半条记录。
 */
object CaptureStore {
    private const val SCHEMA_VERSION = 2
    private const val PREFS_NAME = "debug_capture_store"
    private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    private const val KEY_LATEST_SESSION_ID = "latest_session_id"
    private const val CAPTURE_DIRECTORY = "debug-captures"
    private const val EXPORT_DIRECTORY = "debug-capture-exports"
    private const val METADATA_FILE = "metadata.json"
    private const val EVENTS_FILE = "events.jsonl"
    private const val README_FILE = "README.txt"
    private const val SMART_AUDIO_ASSETS_FILE = "smart-audio-device-assets.zip"
    private const val SMART_AUDIO_CONFIG_FILE = "smart-audio-product-config.json"
    private const val SMART_AUDIO_ASSETS_EXPORT_PATH =
        "resources/smartaudio/smart-audio-device-assets.zip"
    private const val SMART_AUDIO_CONFIG_EXPORT_PATH =
        "resources/smartaudio/smart-audio-product-config.json"

    private const val MAX_SESSION_BYTES = 16L * 1024L * 1024L
    private const val MAX_SESSION_EVENTS = 50_000L
    private const val MAX_SHORT_TEXT_LENGTH = 1_024
    private const val MAX_SUMMARY_LENGTH = 8_192
    private const val MAX_PAYLOAD_LENGTH = 65_536
    private const val MAX_RESOURCE_CANDIDATES = 16

    private val lock = Any()
    private val macRegex = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val maskedMacRegex = Regex(
        "(?i)^\\*\\*:\\*\\*:\\*\\*:\\*\\*:[0-9a-f]{2}:[0-9a-f]{2}$",
    )
    private val hashedAddressRegex = Regex("(?i)^<redacted:[0-9a-f]{10}>$")

    fun startSession(
        context: Context,
        metadata: CaptureSessionMetadata,
    ): CaptureSession = synchronized(lock) {
        val appContext = context.applicationContext
        requireNoActiveSession(readActiveSessionLocked(appContext))
        val normalized = normalizeMetadata(metadata)
        requireConnectedHeadsetMetadata(normalized)
        require(normalized.officialAppPackage == SmartAudioCaptureTarget.PACKAGE_NAME) {
            "协议采集仅支持华为智慧音频"
        }

        val startedAt = System.currentTimeMillis()
        val sessionId = createSessionId(startedAt)
        val directory = sessionDirectory(appContext, sessionId)
        check(directory.mkdirs() || directory.isDirectory) {
            "无法创建抓包目录"
        }

        val metadataJson = createMetadataJson(appContext, sessionId, startedAt, normalized)
        writeMetadataLocked(directory, metadataJson)
        preferences(appContext).edit()
            .putString(KEY_ACTIVE_SESSION_ID, sessionId)
            .putString(KEY_LATEST_SESSION_ID, sessionId)
            .apply()

        appendEventLocked(
            directory = directory,
            metadata = metadataJson,
            type = "session_start",
            eventTimestamp = startedAt,
            fields = JSONObject()
                .putNullable("issue_id", normalized.issueId)
                .put("headset_model", normalized.headsetModel)
                .put("headset_name", normalized.headsetModel)
                .put("headset_name_source", normalized.headsetNameSource)
                .put("feature_catalog_version", normalized.featureCatalogVersion),
            allowLimitEvent = false,
        )
        sessionFromMetadata(metadataJson)
    }

    fun addMarker(
        context: Context,
        label: String,
        details: String? = null,
    ): Boolean = synchronized(lock) {
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
            appendEventLocked(
                directory = directory,
                metadata = metadata,
                type = "marker",
                fields = JSONObject()
                    .put("label", sanitize(label, MAX_SHORT_TEXT_LENGTH).ifBlank { "unnamed_marker" })
                    .putNullable("details", details?.let { sanitize(it, MAX_SUMMARY_LENGTH) }),
            )
        } ?: false
    }

    fun recordHookReady(
        context: Context,
        sourcePackage: String,
        sourceProcess: String?,
    ): Boolean = synchronized(lock) {
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
            if (!metadataAcceptsSource(metadata, sourcePackage)) {
                return@withActiveSessionLocked false
            }
            val appended = appendEventLocked(
                directory = directory,
                metadata = metadata,
                type = "hook_status",
                fields = JSONObject()
                    .put("status", "ready")
                    .put("source_package", sanitize(sourcePackage, MAX_SHORT_TEXT_LENGTH))
                    .putNullable(
                        "source_process",
                        sourceProcess?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) },
                    ),
            )
            if (appended) {
                metadata.put(
                    "hook_ready_count",
                    metadata.optLong("hook_ready_count", 0L) + 1L,
                )
                writeMetadataLocked(directory, metadata)
            }
            appended
        } ?: false
    }

    internal fun recordSmartAudioResourceCandidate(
        context: Context,
        sourcePackage: String,
        originWireName: String?,
        modelId: String?,
        subModelId: String?,
        resourceKind: String?,
        sourceProcess: String?,
        observedAtEpochMs: Long,
    ): Boolean = synchronized(lock) {
        val candidate = SmartAudioResourceLocator.candidate(
            originWireName = originWireName,
            modelId = modelId,
            subModelId = subModelId,
            observedAtEpochMs = observedAtEpochMs,
        ) ?: return@synchronized false
        val normalizedResourceKind = resourceKind?.takeIf { it == "config" || it == "archive" }
            ?: return@synchronized false
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
            if (!metadataAcceptsSource(metadata, sourcePackage)) {
                return@withActiveSessionLocked false
            }
            if (candidate.observedAtEpochMs < metadata.optLong("started_at_epoch_ms", 0L)) {
                return@withActiveSessionLocked false
            }
            val existingJson = metadata.optJSONObject("smart_audio_resource_candidate")
            val existing = existingJson?.toSmartAudioResourceCandidate()
            val effectiveCandidate = SmartAudioResourceCandidatePolicy
                .mergeRouteWithKnownIdentity(existing, candidate)
            val existingIdentitySource = existingJson
                ?.optNonBlankString("identity_source")
            val inheritsAddressBoundIdentity =
                existing?.modelId == candidate.modelId &&
                    existing.subModelId != null &&
                    (candidate.subModelId == null || candidate.subModelId == existing.subModelId) &&
                    existingIdentitySource?.contains("device_info_tlv") == true
            recordSmartAudioResourceCandidateLocked(
                directory = directory,
                metadata = metadata,
                candidate = effectiveCandidate,
                sourcePackage = sourcePackage,
                sourceProcess = sourceProcess,
                identitySource = if (
                    inheritsAddressBoundIdentity
                ) {
                    "device_info_tlv+official_url"
                } else {
                    "official_url"
                },
                originSource = "observed_url",
                resourceKind = normalizedResourceKind,
            )
        } ?: false
    }

    internal fun recordSmartAudioDeviceIdentity(
        context: Context,
        sourcePackage: String,
        identity: SmartAudioDeviceIdentity,
        sourceProcess: String?,
        deviceAddress: String?,
        observedAtEpochMs: Long,
        identitySource: String = "device_info_tlv",
    ): Boolean = synchronized(lock) {
        val normalizedIdentitySource = when (identitySource) {
            "device_info_tlv", "current_device_bus" -> identitySource
            else -> return@synchronized false
        }
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
            if (!metadataAcceptsSource(metadata, sourcePackage)) {
                return@withActiveSessionLocked false
            }
            if (observedAtEpochMs < metadata.optLong("started_at_epoch_ms", 0L)) {
                return@withActiveSessionLocked false
            }
            val expectedAddress = metadata.optNonBlankString("headset_address")
                ?: return@withActiveSessionLocked false
            val observedAddress = deviceAddress?.let(::maskDeviceAddress)
                ?: return@withActiveSessionLocked false
            if (observedAddress != expectedAddress) {
                return@withActiveSessionLocked false
            }
            val existing = metadata.optJSONObject("smart_audio_resource_candidate")
                ?.toSmartAudioResourceCandidate()
            val observedOrigin = existing
                ?.takeIf { it.modelId == identity.modelId }
                ?.origin
            val candidate = SmartAudioResourceLocator.candidate(
                originWireName = (observedOrigin ?: SmartAudioResourceOrigin.CHINA).wireName,
                modelId = identity.modelId,
                subModelId = identity.subModelId,
                observedAtEpochMs = observedAtEpochMs,
            ) ?: return@withActiveSessionLocked false
            recordSmartAudioResourceCandidateLocked(
                directory = directory,
                metadata = metadata,
                candidate = candidate,
                sourcePackage = sourcePackage,
                sourceProcess = sourceProcess,
                identitySource = normalizedIdentitySource,
                originSource = if (observedOrigin != null) "observed_url" else "china_fallback",
                resourceKind = if (normalizedIdentitySource == "device_info_tlv") {
                    "device_info"
                } else {
                    "current_device"
                },
            )
        } ?: false
    }

    fun stopSession(
        context: Context,
        reason: String = "user",
    ): CaptureSession? = synchronized(lock) {
        val appContext = context.applicationContext
        val activeSessionId = preferences(appContext).getString(KEY_ACTIVE_SESSION_ID, null)
            ?: return@synchronized null
        val directory = sessionDirectory(appContext, activeSessionId)
        val metadata = readMetadataLocked(directory) ?: run {
            preferences(appContext).edit().remove(KEY_ACTIVE_SESSION_ID).apply()
            return@synchronized null
        }
        val stoppedAt = System.currentTimeMillis()
        appendEventLocked(
            directory = directory,
            metadata = metadata,
            type = "session_stop",
            eventTimestamp = stoppedAt,
            fields = JSONObject().put("reason", sanitize(reason, MAX_SHORT_TEXT_LENGTH)),
            allowLimitEvent = false,
        )
        metadata.put("stopped_at_epoch_ms", stoppedAt)
        metadata.put("stopped_at_iso", isoTimestamp(stoppedAt))
        writeMetadataLocked(directory, metadata)
        preferences(appContext).edit()
            .remove(KEY_ACTIVE_SESSION_ID)
            .putString(KEY_LATEST_SESSION_ID, activeSessionId)
            .apply()
        sessionFromMetadata(metadata)
    }

    fun getState(context: Context): CaptureState = synchronized(lock) {
        val appContext = context.applicationContext
        val prefs = preferences(appContext)
        val activeId = prefs.getString(KEY_ACTIVE_SESSION_ID, null)
        val latestId = prefs.getString(KEY_LATEST_SESSION_ID, null)
        CaptureState(
            activeSession = activeId?.let { readSessionLocked(appContext, it) },
            latestSession = latestId?.let { readSessionLocked(appContext, it) },
        )
    }

    fun isCaptureActive(context: Context): Boolean =
        preferences(context.applicationContext).contains(KEY_ACTIVE_SESSION_ID)

    /** 作用域升级后结束旧目标会话，避免把旧目标与智慧音频事件写进同一个采集包。 */
    fun stopActiveSessionForDifferentTarget(
        context: Context,
        requiredPackage: String,
    ): CaptureSession? = synchronized(lock) {
        val activeSession = readActiveSessionLocked(context.applicationContext)
            ?: return@synchronized null
        if (activeSession.officialAppPackage == requiredPackage) return@synchronized null
        stopSession(context, reason = "capture_target_changed")
    }

    internal fun attachSmartAudioAssets(
        context: Context,
        expectedSessionId: String,
        sourceUri: Uri,
    ): SmartAudioAssetAttachment = synchronized(lock) {
        val appContext = context.applicationContext
        val session = getState(appContext).latestSession ?: error("还没有可关联的抓包会话")
        check(session.id == expectedSessionId) { "抓包会话已变化，请返回后重试" }
        check(!session.isActive) { "请先停止当前采集" }
        check(session.officialAppPackage == SmartAudioCaptureTarget.PACKAGE_NAME) {
            "旧版会话不能附加智慧音频资源，请开始新一轮采集"
        }
        val directory = sessionDirectory(appContext, session.id)
        check(directory.isDirectory) { "抓包会话目录不存在" }

        val destination = File(directory, SMART_AUDIO_ASSETS_FILE)
        val temporaryFile = File(directory, "$SMART_AUDIO_ASSETS_FILE.tmp")
        temporaryFile.delete()
        try {
            val copy = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                copyStreamLimited(
                    input = input,
                    destination = temporaryFile,
                    maxBytes = SmartAudioAssetArchive.MAX_ARCHIVE_BYTES,
                )
            } ?: error("无法读取所选资源包")
            check(!copy.truncated) { "资源包超过 128 MiB" }

            val info = SmartAudioAssetArchive.inspect(temporaryFile)
            val metadata = readMetadataLocked(directory) ?: error("无法读取抓包元数据")
            metadata.remove("smart_audio_device_assets")
            writeMetadataLocked(directory, metadata)
            moveReplacing(temporaryFile, destination)
            File(directory, SMART_AUDIO_CONFIG_FILE).delete()
            val sourceName = queryDisplayName(appContext, sourceUri)
            storeSmartAudioAssetMetadataLocked(
                directory = directory,
                metadata = metadata,
                info = info,
                sourceName = sourceName,
                sourceType = "manual_file",
                candidate = null,
                selection = null,
                configSha256 = null,
            )
            SmartAudioAssetAttachment(
                bytes = info.bytes,
                pngEntryCount = info.pngEntryCount,
                mainImageCount = info.mainImageEntries.size,
                leftImageCount = info.leftImageEntries.size,
                rightImageCount = info.rightImageEntries.size,
                sourceName = sourceName,
            )
        } catch (throwable: Throwable) {
            temporaryFile.delete()
            throw throwable
        }
    }

    internal fun autoAttachSmartAudioAssets(
        context: Context,
        expectedSessionId: String,
    ): SmartAudioAutoAttachmentResult {
        val appContext = context.applicationContext
        val candidate = synchronized(lock) {
            val session = getState(appContext).latestSession
                ?: return@synchronized null
            if (
                session.id != expectedSessionId ||
                session.isActive ||
                session.officialAppPackage != SmartAudioCaptureTarget.PACKAGE_NAME
            ) {
                return@synchronized null
            }
            val directory = sessionDirectory(appContext, session.id)
            val metadata = readMetadataLocked(directory)
                ?: return@synchronized null
            if (smartAudioAssetsAreReadyLocked(directory, metadata)) {
                return SmartAudioAutoAttachmentResult(
                    status = SmartAudioAutoAttachmentStatus.ALREADY_ATTACHED,
                )
            }
            cleanupIncompleteSmartAudioAssetsLocked(directory, metadata)
            if (metadata.optBoolean("smart_audio_resource_ambiguous", false)) {
                return SmartAudioAutoAttachmentResult(
                    status = SmartAudioAutoAttachmentStatus.AMBIGUOUS,
                )
            }
            val candidateJson = metadata.optJSONObject("smart_audio_resource_candidate")
                ?: return@synchronized null
            val candidate = candidateJson.toSmartAudioResourceCandidate()
                ?: return@synchronized null
            if (!isAddressBoundIdentitySource(candidateJson.optString("identity_source"))) {
                return SmartAudioAutoAttachmentResult(
                    status = SmartAudioAutoAttachmentStatus.NEEDS_DEVICE_IDENTITY,
                    modelId = candidate.modelId,
                )
            }
            candidate
        } ?: return SmartAudioAutoAttachmentResult(
            status = SmartAudioAutoAttachmentStatus.NOT_OBSERVED,
        )
        if (candidate.subModelId == null) {
            return SmartAudioAutoAttachmentResult(
                status = SmartAudioAutoAttachmentStatus.NEEDS_SUBMODEL,
                modelId = candidate.modelId,
            )
        }

        val downloaded = runCatching {
            SmartAudioResourceDownloader.download(appContext, candidate)
        }.getOrElse { throwable ->
            return SmartAudioAutoAttachmentResult(
                status = SmartAudioAutoAttachmentStatus.FAILED,
                message = throwable.message?.take(MAX_SHORT_TEXT_LENGTH),
            )
        }
        return try {
            val attachment = attachDownloadedSmartAudioAssets(
                context = appContext,
                expectedSessionId = expectedSessionId,
                downloaded = downloaded,
            )
            SmartAudioAutoAttachmentResult(
                status = SmartAudioAutoAttachmentStatus.ATTACHED,
                attachment = attachment,
                modelId = downloaded.selection.modelId,
                subModelId = downloaded.selection.subModelId,
            )
        } catch (throwable: Throwable) {
            SmartAudioAutoAttachmentResult(
                status = SmartAudioAutoAttachmentStatus.FAILED,
                message = throwable.message?.take(MAX_SHORT_TEXT_LENGTH),
            )
        } finally {
            downloaded.archiveFile.delete()
        }
    }

    private fun attachDownloadedSmartAudioAssets(
        context: Context,
        expectedSessionId: String,
        downloaded: DownloadedSmartAudioResources,
    ): SmartAudioAssetAttachment = synchronized(lock) {
        val session = getState(context).latestSession ?: error("还没有可关联的抓包会话")
        check(session.id == expectedSessionId) { "抓包会话已变化，请返回后重试" }
        check(!session.isActive) { "请先停止当前采集" }
        check(session.officialAppPackage == SmartAudioCaptureTarget.PACKAGE_NAME) {
            "旧版会话不能附加智慧音频资源，请开始新一轮采集"
        }
        val directory = sessionDirectory(context, session.id)
        check(directory.isDirectory) { "抓包会话目录不存在" }
        val initialMetadata = readMetadataLocked(directory) ?: error("无法读取抓包元数据")
        check(!smartAudioAssetsAreReadyLocked(directory, initialMetadata)) {
            "会话已经附加智慧音频资源"
        }
        val archiveDestination = File(directory, SMART_AUDIO_ASSETS_FILE)
        val archiveTemporary = File(directory, "$SMART_AUDIO_ASSETS_FILE.tmp")
        val configDestination = File(directory, SMART_AUDIO_CONFIG_FILE)
        val configTemporary = File(directory, "$SMART_AUDIO_CONFIG_FILE.tmp")
        archiveTemporary.delete()
        configTemporary.delete()
        try {
            val copy = downloaded.archiveFile.inputStream().use { input ->
                copyStreamLimited(
                    input = input,
                    destination = archiveTemporary,
                    maxBytes = SmartAudioAssetArchive.MAX_ARCHIVE_BYTES,
                )
            }
            check(!copy.truncated && copy.bytes == downloaded.archiveInfo.bytes) {
                "智慧音频资源包复制不完整"
            }
            val info = SmartAudioAssetArchive.inspect(archiveTemporary)
            check(info.sha256 == downloaded.archiveInfo.sha256) { "智慧音频资源包校验值不一致" }
            check(downloaded.configBytes.size <= SmartAudioProductConfig.MAX_CONFIG_BYTES) {
                "智慧音频资源配置过大"
            }
            FileOutputStream(configTemporary, false).use { output ->
                output.write(downloaded.configBytes)
                output.flush()
                output.fd.sync()
            }
            initialMetadata.remove("smart_audio_device_assets")
            writeMetadataLocked(directory, initialMetadata)
            moveReplacing(archiveTemporary, archiveDestination)
            moveReplacing(configTemporary, configDestination)

            val metadata = readMetadataLocked(directory) ?: error("无法读取抓包元数据")
            storeSmartAudioAssetMetadataLocked(
                directory = directory,
                metadata = metadata,
                info = info,
                sourceName = downloaded.selection.archiveFileName,
                sourceType = "official_cdn",
                candidate = downloaded.candidate,
                selection = downloaded.selection,
                configSha256 = sha256(downloaded.configBytes),
            )
            SmartAudioAssetAttachment(
                bytes = info.bytes,
                pngEntryCount = info.pngEntryCount,
                mainImageCount = info.mainImageEntries.size,
                leftImageCount = info.leftImageEntries.size,
                rightImageCount = info.rightImageEntries.size,
                sourceName = downloaded.selection.archiveFileName,
            )
        } catch (throwable: Throwable) {
            archiveTemporary.delete()
            configTemporary.delete()
            archiveDestination.delete()
            configDestination.delete()
            readMetadataLocked(directory)?.let { metadata ->
                metadata.remove("smart_audio_device_assets")
                runCatching { writeMetadataLocked(directory, metadata) }
            }
            throw throwable
        }
    }

    private fun smartAudioAssetsAreReadyLocked(
        directory: File,
        metadata: JSONObject,
    ): Boolean {
        val assetMetadata = metadata.optJSONObject("smart_audio_device_assets") ?: return false
        if (assetMetadata.optString("status") != "included") return false
        val archive = File(directory, SMART_AUDIO_ASSETS_FILE)
        val expectedBytes = assetMetadata.optLong("bytes", -1L)
        if (!archive.isFile || expectedBytes <= 0L || archive.length() != expectedBytes) return false
        return when (assetMetadata.optString("source_type")) {
            "manual_file" -> true
            "official_cdn" -> {
                val config = File(directory, SMART_AUDIO_CONFIG_FILE)
                assetMetadata.optString("config_file") == SMART_AUDIO_CONFIG_EXPORT_PATH &&
                    assetMetadata.optNonBlankString("config_sha256") != null &&
                    config.isFile &&
                    config.length() in 1..SmartAudioProductConfig.MAX_CONFIG_BYTES.toLong()
            }

            else -> false
        }
    }

    private fun cleanupIncompleteSmartAudioAssetsLocked(
        directory: File,
        metadata: JSONObject,
    ) {
        File(directory, SMART_AUDIO_ASSETS_FILE).delete()
        File(directory, SMART_AUDIO_CONFIG_FILE).delete()
        File(directory, "$SMART_AUDIO_ASSETS_FILE.tmp").delete()
        File(directory, "$SMART_AUDIO_CONFIG_FILE.tmp").delete()
        if (metadata.remove("smart_audio_device_assets") != null) {
            writeMetadataLocked(directory, metadata)
        }
    }

    fun hasSmartAudioAssets(context: Context, sessionId: String?): Boolean = synchronized(lock) {
        if (sessionId.isNullOrBlank()) return@synchronized false
        val directory = sessionDirectory(context.applicationContext, sessionId)
        val metadata = readMetadataLocked(directory) ?: return@synchronized false
        smartAudioAssetsAreReadyLocked(directory, metadata)
    }

    fun exportLatest(context: Context): CaptureExport = synchronized(lock) {
        val appContext = context.applicationContext
        val state = getState(appContext)
        val session = state.latestSession ?: error("还没有可导出的抓包会话")
        val directory = sessionDirectory(appContext, session.id)
        val metadataFile = File(directory, METADATA_FILE)
        val eventsFile = File(directory, EVENTS_FILE)
        check(metadataFile.isFile && eventsFile.isFile) { "抓包会话文件不完整" }
        val metadata = readMetadataLocked(directory) ?: error("无法读取抓包元数据")
        val smartAudioAssetsReady = smartAudioAssetsAreReadyLocked(directory, metadata)
        val smartAudioAssets = File(directory, SMART_AUDIO_ASSETS_FILE)
            .takeIf { smartAudioAssetsReady && it.isFile }
            ?.also { SmartAudioAssetArchive.inspect(it) }
        val smartAudioConfig = File(directory, SMART_AUDIO_CONFIG_FILE)
            .takeIf { smartAudioAssets != null && it.isFile }
        val exportDirectory = File(appContext.cacheDir, EXPORT_DIRECTORY)
        check(exportDirectory.mkdirs() || exportDirectory.isDirectory) {
            "无法创建导出目录"
        }
        val fileName = "huaweipods-capture-${session.id}.zip"
        val outputFile = File(exportDirectory, fileName)
        val temporaryFile = File(exportDirectory, "$fileName.tmp")

        ZipOutputStream(FileOutputStream(temporaryFile).buffered()).use { zip ->
            zip.putTextEntry(
                README_FILE,
                buildReadme(
                    session = session,
                    smartAudioAssetsIncluded = smartAudioAssets != null,
                ),
            )
            zip.putFileEntry(METADATA_FILE, metadataFile)
            zip.putFileEntry(EVENTS_FILE, eventsFile)
            smartAudioAssets?.let { zip.putFileEntry(SMART_AUDIO_ASSETS_EXPORT_PATH, it) }
            smartAudioConfig?.let { zip.putFileEntry(SMART_AUDIO_CONFIG_EXPORT_PATH, it) }
        }
        moveReplacing(temporaryFile, outputFile)

        CaptureExport(
            uri = CaptureFileProvider.uriFor(appContext, outputFile),
            fileName = fileName,
            sessionId = session.id,
        )
    }

    internal fun appendProtocolEvent(
        context: Context,
        event: CapturedProtocolEvent,
        sourcePackage: String,
    ): Boolean = synchronized(lock) {
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
            if (!metadataAcceptsSource(metadata, sourcePackage)) {
                return@withActiveSessionLocked false
            }
            val eventTimestamp = event.timestampEpochMs
                ?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            if (eventTimestamp < metadata.optLong("started_at_epoch_ms", 0L)) {
                return@withActiveSessionLocked false
            }
            val fields = JSONObject()
                .putNullable("event_type", event.eventType?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("direction", event.direction?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("channel", event.channel?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("operation", event.operation?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("payload_hex", event.payloadHex?.let(::sanitizePayload))
                .putNullable("summary", event.summary?.let { sanitize(it, MAX_SUMMARY_LENGTH) })
                .put("source_package", sourcePackage)
                .putNullable("source_process", event.sourceProcess?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("device_name", event.deviceName?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) })
                .putNullable("device_address", event.deviceAddress?.let(::maskDeviceAddress))
            val appended = appendEventLocked(
                directory = directory,
                metadata = metadata,
                type = "protocol_event",
                eventTimestamp = eventTimestamp,
                fields = fields,
            )
            if (appended) {
                metadata.put(
                    "protocol_event_count",
                    metadata.optLong("protocol_event_count", 0L) + 1L,
                )
                writeMetadataLocked(directory, metadata)
            }
            appended
        } ?: false
    }

    private inline fun <T> withActiveSessionLocked(
        context: Context,
        block: (File, JSONObject) -> T,
    ): T? {
        val activeSessionId = preferences(context).getString(KEY_ACTIVE_SESSION_ID, null) ?: return null
        val directory = sessionDirectory(context, activeSessionId)
        val metadata = readMetadataLocked(directory) ?: run {
            preferences(context).edit().remove(KEY_ACTIVE_SESSION_ID).apply()
            return null
        }
        return block(directory, metadata)
    }

    private fun appendEventLocked(
        directory: File,
        metadata: JSONObject,
        type: String,
        eventTimestamp: Long = System.currentTimeMillis(),
        fields: JSONObject,
        allowLimitEvent: Boolean = true,
    ): Boolean {
        val eventCount = metadata.optLong("event_count", 0L)
        val bytesWritten = metadata.optLong("bytes_written", 0L)
        if (allowLimitEvent && (eventCount >= MAX_SESSION_EVENTS || bytesWritten >= MAX_SESSION_BYTES)) {
            if (!metadata.optBoolean("limit_reached", false)) {
                metadata.put("limit_reached", true)
                appendEventLocked(
                    directory = directory,
                    metadata = metadata,
                    type = "capture_limit_reached",
                    fields = JSONObject()
                        .put("max_events", MAX_SESSION_EVENTS)
                        .put("max_bytes", MAX_SESSION_BYTES),
                    allowLimitEvent = false,
                )
            }
            writeMetadataLocked(directory, metadata)
            return false
        }

        val sequence = eventCount + 1L
        val event = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("sequence", sequence)
            .put("timestamp_epoch_ms", eventTimestamp)
            .put("timestamp_iso", isoTimestamp(eventTimestamp))
            .put("type", type)
        fields.keys().forEach { key -> event.put(key, fields.get(key)) }
        val lineBytes = (event.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
        if (bytesWritten + lineBytes.size > MAX_SESSION_BYTES && allowLimitEvent) {
            metadata.put("limit_reached", true)
            writeMetadataLocked(directory, metadata)
            return false
        }

        val eventsFile = File(directory, EVENTS_FILE)
        FileOutputStream(eventsFile, true).use { output ->
            output.write(lineBytes)
            output.flush()
            output.fd.sync()
        }
        metadata.put("event_count", sequence)
        metadata.put("bytes_written", bytesWritten + lineBytes.size)
        writeMetadataLocked(directory, metadata)
        return true
    }

    private fun createMetadataJson(
        context: Context,
        sessionId: String,
        startedAt: Long,
        metadata: CaptureSessionMetadata,
    ): JSONObject = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("session_id", sessionId)
        .putNullable("issue_id", metadata.issueId)
        .put("headset_model", metadata.headsetModel)
        .put("headset_name", metadata.headsetModel)
        .put("headset_name_source", metadata.headsetNameSource)
        .put("feature_catalog_version", metadata.featureCatalogVersion)
        .putNullable("headset_address", metadata.headsetAddress)
        .putNullable("notes", metadata.notes)
        .put("official_app", officialAppMetadata(context, metadata.officialAppPackage))
        .put("started_at_epoch_ms", startedAt)
        .put("started_at_iso", isoTimestamp(startedAt))
        .put("stopped_at_epoch_ms", JSONObject.NULL)
        .put("stopped_at_iso", JSONObject.NULL)
        .put("event_count", 0L)
        .put("protocol_event_count", 0L)
        .put("hook_ready_count", 0L)
        .put("bytes_written", 0L)
        .put("limit_reached", false)
        .put(
            "app",
            JSONObject()
                .put("application_id", BuildConfig.APPLICATION_ID)
                .put("version_name", BuildConfig.VERSION_NAME)
                .put("version_code", BuildConfig.VERSION_CODE)
                .put("build_type", BuildConfig.BUILD_TYPE)
                .put("build_timestamp", BuildConfig.BUILD_TIMESTAMP),
        )
        .put(
            "phone",
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("android_release", Build.VERSION.RELEASE)
                .put("sdk_int", Build.VERSION.SDK_INT),
        )
        .put(
            "privacy",
            JSONObject()
                .put("device_addresses", "masked_by_default")
                .put("archive_may_contain_sensitive_protocol_payloads", true),
        )

    private fun officialAppMetadata(context: Context, packageName: String?): JSONObject {
        val packageInfo = packageName?.let { targetPackage ->
            runCatching {
                context.packageManager.getPackageInfo(
                    targetPackage,
                    PackageManager.PackageInfoFlags.of(0L),
                )
            }.getOrNull()
        }
        return JSONObject()
            .putNullable("package_name", packageName)
            .putNullable("version_name", packageInfo?.versionName)
            .putNullable("version_code", packageInfo?.longVersionCode)
    }

    private fun readActiveSessionLocked(context: Context): CaptureSession? {
        val activeId = preferences(context).getString(KEY_ACTIVE_SESSION_ID, null) ?: return null
        val session = readSessionLocked(context, activeId)
        if (session == null || !session.isActive) {
            preferences(context).edit().remove(KEY_ACTIVE_SESSION_ID).apply()
            return null
        }
        return session
    }

    private fun readSessionLocked(context: Context, sessionId: String): CaptureSession? =
        readMetadataLocked(sessionDirectory(context, sessionId))?.let(::sessionFromMetadata)

    internal fun sessionFromMetadata(metadata: JSONObject): CaptureSession {
        val officialAppPackage = metadata.optJSONObject("official_app")
            ?.optNonBlankString("package_name")
            ?: metadata.optNonBlankString("official_app_package")
        return sessionFromStoredValues(
            StoredSessionValues(
                schemaVersion = metadata.optInt("schema_version", 1),
                id = metadata.getString("session_id"),
                issueId = metadata.optNonBlankString("issue_id"),
                headsetName = metadata.optNonBlankString("headset_name"),
                headsetModel = metadata.optNonBlankString("headset_model"),
                officialAppPackage = officialAppPackage,
                headsetAddress = metadata.optNonBlankString("headset_address"),
                headsetNameSource = metadata.optNonBlankString("headset_name_source"),
                featureCatalogVersion = metadata.optNonBlankString("feature_catalog_version"),
                startedAtEpochMs = metadata.optLong("started_at_epoch_ms"),
                stoppedAtEpochMs = metadata.optLongOrNull("stopped_at_epoch_ms"),
                eventCount = metadata.optLong("event_count", 0L),
                protocolEventCount = metadata.optLong("protocol_event_count", 0L),
                hookReadyCount = metadata.optLong("hook_ready_count", 0L),
                bytesWritten = metadata.optLong("bytes_written", 0L),
            ),
        )
    }

    internal fun sessionFromStoredValues(values: StoredSessionValues): CaptureSession {
        val headsetName = values.headsetName ?: values.headsetModel ?: "unspecified"
        return CaptureSession(
            id = values.id,
            issueId = values.issueId
                ?.takeUnless { it.equals("unspecified", ignoreCase = true) },
            headsetModel = headsetName,
            officialAppPackage = values.officialAppPackage,
            headsetAddress = values.headsetAddress,
            headsetNameSource = values.headsetNameSource
                ?: if (values.schemaVersion < SCHEMA_VERSION) {
                    LEGACY_HEADSET_NAME_SOURCE
                } else {
                    DEFAULT_HEADSET_NAME_SOURCE
                },
            featureCatalogVersion = values.featureCatalogVersion
                ?: if (values.schemaVersion < SCHEMA_VERSION) {
                    LEGACY_FEATURE_CATALOG_VERSION
                } else {
                    DEFAULT_FEATURE_CATALOG_VERSION
                },
            startedAtEpochMs = values.startedAtEpochMs,
            stoppedAtEpochMs = values.stoppedAtEpochMs,
            eventCount = values.eventCount,
            protocolEventCount = values.protocolEventCount,
            hookReadyCount = values.hookReadyCount,
            bytesWritten = values.bytesWritten,
        )
    }

    internal fun requireNoActiveSession(activeSession: CaptureSession?) {
        check(activeSession == null) {
            "已有进行中的抓包会话 ${activeSession?.id}，请先恢复或结束该会话"
        }
    }

    internal fun normalizeMetadata(metadata: CaptureSessionMetadata): CaptureSessionMetadata =
        metadata.copy(
            issueId = metadata.issueId
                ?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) }
                ?.ifBlank { null }
                ?.takeUnless { it.equals("unspecified", ignoreCase = true) },
            headsetModel = sanitize(metadata.headsetModel, MAX_SHORT_TEXT_LENGTH)
                .ifBlank { "unspecified" },
            officialAppPackage = metadata.officialAppPackage
                ?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) }
                ?.ifBlank { null },
            headsetAddress = metadata.headsetAddress?.let(::maskDeviceAddress),
            headsetNameSource = sanitize(metadata.headsetNameSource, MAX_SHORT_TEXT_LENGTH)
                .ifBlank { DEFAULT_HEADSET_NAME_SOURCE },
            featureCatalogVersion = sanitize(metadata.featureCatalogVersion, MAX_SHORT_TEXT_LENGTH)
                .ifBlank { DEFAULT_FEATURE_CATALOG_VERSION },
            notes = metadata.notes?.let { sanitize(it, MAX_SUMMARY_LENGTH) }?.ifBlank { null },
        )

    private fun recordSmartAudioResourceCandidateLocked(
        directory: File,
        metadata: JSONObject,
        candidate: SmartAudioResourceCandidate,
        sourcePackage: String,
        sourceProcess: String?,
        identitySource: String,
        originSource: String,
        resourceKind: String,
    ): Boolean {
        val existingJson = metadata.optJSONObject("smart_audio_resource_candidate")
        val existing = existingJson?.toSmartAudioResourceCandidate()
        val candidateJson = candidate.toJson()
            .put("identity_source", identitySource)
            .put("origin_source", originSource)
            .put("resource_kind", resourceKind)
        val candidates = metadata.optJSONArray("smart_audio_resource_candidates") ?: JSONArray()
        val lastCandidate = candidates.optJSONObject(candidates.length() - 1)
        if (!lastCandidate.sameResourceIdentity(candidateJson)) {
            while (candidates.length() >= MAX_RESOURCE_CANDIDATES) candidates.remove(0)
            candidates.put(candidateJson)
            metadata.put("smart_audio_resource_candidates", candidates)
        }
        val decision = decideSmartAudioResourceCandidate(candidates, existingJson)
        metadata.put("smart_audio_resource_ambiguous", decision.ambiguous)
        decision.selected?.let {
            metadata.put("smart_audio_resource_candidate", it)
        }
        val conflictingModel = existing?.modelId
            ?.takeIf { it != candidate.modelId }
        appendEventLocked(
            directory = directory,
            metadata = metadata,
            type = "smart_audio_resource",
            eventTimestamp = candidate.observedAtEpochMs,
            fields = JSONObject()
                .put("source_package", sourcePackage)
                .putNullable(
                    "source_process",
                    sourceProcess?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) },
                )
                .put("identity_source", identitySource)
                .put("origin_source", originSource)
                .put("resource_kind", resourceKind)
                .put("origin", candidate.origin.wireName)
                .put("model_id", candidate.modelId)
                .putNullable("sub_model_id", candidate.subModelId)
                .putNullable("conflicts_with_model_id", conflictingModel)
                .put("ambiguous", metadata.optBoolean("smart_audio_resource_ambiguous", false)),
        )
        writeMetadataLocked(directory, metadata)
        return true
    }

    internal fun requireConnectedHeadsetMetadata(metadata: CaptureSessionMetadata) {
        require(metadata.headsetNameSource == CONNECTED_HEADSET_NAME_SOURCE) {
            "请先从当前已连接的蓝牙耳机中选择采集对象"
        }
        require(metadata.headsetAddress?.let(maskedMacRegex::matches) == true) {
            "目标耳机地址缺失，请重新检测并选择当前已连接的耳机"
        }
    }

    private fun JSONObject?.sameResourceIdentity(other: JSONObject): Boolean {
        this ?: return false
        return optString("origin") == other.optString("origin") &&
            optString("model_id") == other.optString("model_id") &&
            optString("sub_model_id") == other.optString("sub_model_id") &&
            optString("identity_source") == other.optString("identity_source") &&
            optString("resource_kind") == other.optString("resource_kind")
    }

    private data class SmartAudioResourceDecision(
        val selected: JSONObject?,
        val ambiguous: Boolean,
    )

    private fun decideSmartAudioResourceCandidate(
        candidates: JSONArray,
        fallbackSelected: JSONObject?,
    ): SmartAudioResourceDecision {
        val entries = (0 until candidates.length()).mapNotNull { index ->
            candidates.optJSONObject(index)?.let { json ->
                json.toSmartAudioResourceCandidate()?.let { candidate -> json to candidate }
            }
        }
        if (entries.isEmpty()) return SmartAudioResourceDecision(fallbackSelected, false)
        val modelIds = entries.map { it.second.modelId }.distinct()
        if (modelIds.size != 1) return SmartAudioResourceDecision(fallbackSelected, true)

        val archiveEntries = entries.filter { (json, candidate) ->
            json.optString("resource_kind") == "archive" && candidate.subModelId != null
        }
        val archivePairs = archiveEntries.map { it.second.modelId to it.second.subModelId }.distinct()
        if (archivePairs.size > 1) return SmartAudioResourceDecision(fallbackSelected, true)

        val addressBoundEntries = entries.filter { (json, candidate) ->
            candidate.subModelId != null &&
                isAddressBoundIdentitySource(json.optString("identity_source"))
        }
        val addressBoundPairs = addressBoundEntries
            .map { it.second.modelId to it.second.subModelId }
            .distinct()

        if (archivePairs.size == 1) {
            val authoritativePair = archivePairs.single()
            val archiveEntry = archiveEntries.last { entry ->
                (entry.second.modelId to entry.second.subModelId) == authoritativePair
            }
            val addressConfirmed = authoritativePair in addressBoundPairs
            val selected = JSONObject(archiveEntry.first.toString()).apply {
                if (addressConfirmed) {
                    val confirmedSource = addressBoundEntries.last { entry ->
                        (entry.second.modelId to entry.second.subModelId) == authoritativePair
                    }.first.optString("identity_source")
                    put("identity_source", "$confirmedSource+official_url")
                }
            }
            return SmartAudioResourceDecision(
                selected = selected,
                ambiguous = addressBoundPairs.isNotEmpty() && !addressConfirmed,
            )
        }

        if (addressBoundPairs.size > 1) {
            return SmartAudioResourceDecision(fallbackSelected, true)
        }
        if (addressBoundPairs.size == 1) {
            val confirmedPair = addressBoundPairs.single()
            val selected = addressBoundEntries.last { entry ->
                (entry.second.modelId to entry.second.subModelId) == confirmedPair
            }.first
            return SmartAudioResourceDecision(selected, false)
        }
        return SmartAudioResourceDecision(entries.last().first, false)
    }

    internal fun isAddressBoundIdentitySource(value: String?): Boolean = value
        ?.split('+')
        ?.any { it == "device_info_tlv" || it == "current_device_bus" }
        ?: false

    private fun SmartAudioResourceCandidate.toJson(): JSONObject = JSONObject()
        .put("origin", origin.wireName)
        .put("model_id", modelId)
        .putNullable("sub_model_id", subModelId)
        .put("observed_at_epoch_ms", observedAtEpochMs)

    private fun JSONObject.toSmartAudioResourceCandidate(): SmartAudioResourceCandidate? =
        SmartAudioResourceLocator.candidate(
            originWireName = optNonBlankString("origin"),
            modelId = optNonBlankString("model_id"),
            subModelId = optNonBlankString("sub_model_id"),
            observedAtEpochMs = optLong("observed_at_epoch_ms", 0L),
        )

    private fun storeSmartAudioAssetMetadataLocked(
        directory: File,
        metadata: JSONObject,
        info: SmartAudioAssetArchiveInfo,
        sourceName: String?,
        sourceType: String,
        candidate: SmartAudioResourceCandidate?,
        selection: SmartAudioResourceSelection?,
        configSha256: String?,
    ) {
        metadata.put(
            "smart_audio_device_assets",
            JSONObject()
                .put("status", "included")
                .put("source_type", sourceType)
                .put("file", SMART_AUDIO_ASSETS_EXPORT_PATH)
                .putNullable(
                    "config_file",
                    configSha256?.let { SMART_AUDIO_CONFIG_EXPORT_PATH },
                )
                .putNullable(
                    "source_name",
                    sourceName?.let { sanitize(it, MAX_SHORT_TEXT_LENGTH) },
                )
                .putNullable("origin", candidate?.origin?.wireName)
                .putNullable("model_id", selection?.modelId)
                .putNullable("sub_model_id", selection?.subModelId)
                .put(
                    "sub_model_source",
                    when {
                        selection == null -> "manual"
                        else -> "device_info_tlv"
                    },
                )
                .put("bytes", info.bytes)
                .put("entry_count", info.entryCount)
                .put("png_entry_count", info.pngEntryCount)
                .put("main_image_entries", JSONArray(info.mainImageEntries))
                .put("left_image_entries", JSONArray(info.leftImageEntries))
                .put("right_image_entries", JSONArray(info.rightImageEntries))
                .put("sha256", info.sha256)
                .putNullable("config_sha256", configSha256),
        )
        writeMetadataLocked(directory, metadata)
    }

    private fun readMetadataLocked(directory: File): JSONObject? {
        val file = File(directory, METADATA_FILE)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
    }

    private fun writeMetadataLocked(directory: File, metadata: JSONObject) {
        val outputFile = File(directory, METADATA_FILE)
        val temporaryFile = File(directory, "$METADATA_FILE.tmp")
        FileOutputStream(temporaryFile, false).use { output ->
            output.write(metadata.toString(2).toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        moveReplacing(temporaryFile, outputFile)
    }

    private fun sessionDirectory(context: Context, sessionId: String): File =
        File(File(context.filesDir, CAPTURE_DIRECTORY), sessionId)

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun sanitize(value: String, maxLength: Int): String =
        redactMacAddresses(value.replace('\u0000', ' ')).take(maxLength).trim()

    private fun sanitizePayload(value: String): String = value
        .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ' ' || it == ':' || it == '-' }
        .take(MAX_PAYLOAD_LENGTH)
        .trim()

    private fun redactMacAddresses(value: String): String =
        macRegex.replace(value) { match -> maskDeviceAddress(match.value) }

    private fun maskDeviceAddress(value: String): String {
        val normalized = value.trim().uppercase(Locale.US)
        if (maskedMacRegex.matches(normalized)) return normalized
        if (hashedAddressRegex.matches(normalized)) return normalized.lowercase(Locale.US)
        val parts = normalized.split(':')
        return if (parts.size == 6 && parts.all { it.length == 2 }) {
            "**:**:**:**:${parts[4]}:${parts[5]}"
        } else {
            "<redacted:${sha256(normalized).take(10)}>"
        }
    }

    private fun sha256(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun createSessionId(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return "${formatter.format(Date(timestamp))}-${UUID.randomUUID().toString().take(8)}"
    }

    private fun isoTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun copyStreamLimited(
        input: java.io.InputStream,
        destination: File,
        maxBytes: Long,
    ): StreamCopyResult {
        val temporaryFile = if (destination.name.endsWith(".tmp")) {
            destination
        } else {
            File(destination.parentFile, "${destination.name}.tmp")
        }
        var bytesWritten = 0L
        var truncated = false
        input.buffered().use { source ->
            FileOutputStream(temporaryFile, false).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val remaining = maxBytes - bytesWritten
                    if (remaining <= 0L) {
                        truncated = source.read() != -1
                        break
                    }
                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytesWritten += read
                }
            }
        }
        if (temporaryFile != destination) {
            moveReplacing(temporaryFile, destination)
        }
        return StreamCopyResult(bytesWritten, truncated)
    }

    private fun buildReadme(
        session: CaptureSession,
        smartAudioAssetsIncluded: Boolean,
    ): String = """
        HuaweiPods Debug 抓包
        =====================

        会话：${session.id}
        Issue：${readmeIssueLabel(session.issueId)}
        耳机名称：${session.headsetModel}
        名称来源：${session.headsetNameSource}
        功能清单版本：${session.featureCatalogVersion}
        协议事件：${session.protocolEventCount} 条

        文件说明：
        - metadata.json：可选 Issue、耳机名称、手机和应用版本等环境信息。
        - events.jsonl：按时间排列的操作标记和协议事件，每行一个 JSON 对象。
        - $SMART_AUDIO_ASSETS_EXPORT_PATH：${if (smartAudioAssetsIncluded) "已附带经校验的智慧音频图片资源包（可能来自华为官方 CDN 或用户手动兜底导入）。" else "未附带；自动定位失败时可按采集向导手动选择当前子型号 ZIP。"}
        - $SMART_AUDIO_CONFIG_EXPORT_PATH：自动下载成功时附带对应的官方产品配置 JSON；手动导入时不生成。

        提交前请注意：
        1. 设备蓝牙地址默认已脱敏，但原始协议载荷仍可能包含设备或账号相关信息。
        2. 请先自行检查压缩包，只把它发送给可信任的项目维护者。
        3. 分享时请补充实际操作结果和智慧音频中的预期表现；Issue 可稍后关联。
    """.trimIndent() + "\n"

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun ZipOutputStream.putTextEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFileEntry(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { input -> input.copyTo(this) }
        closeEntry()
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optNonBlankString(key: String): String? =
        if (isNull(key) || !has(key)) {
            null
        } else {
            optString(key).trim().ifBlank { null }
        }

    private fun metadataAcceptsSource(metadata: JSONObject, sourcePackage: String): Boolean {
        val sessionPackage = metadata.optJSONObject("official_app")
            ?.optNonBlankString("package_name")
            ?: metadata.optNonBlankString("official_app_package")
        return SmartAudioCaptureTarget.matchesSession(sessionPackage, sourcePackage)
    }

    internal fun readmeIssueLabel(issueId: String?): String =
        issueId?.trim()?.takeIf { it.isNotEmpty() } ?: "未关联 Issue"

    private data class StreamCopyResult(
        val bytes: Long,
        val truncated: Boolean,
    )

    internal data class StoredSessionValues(
        val schemaVersion: Int,
        val id: String,
        val issueId: String?,
        val headsetName: String?,
        val headsetModel: String?,
        val officialAppPackage: String?,
        val headsetAddress: String?,
        val headsetNameSource: String?,
        val featureCatalogVersion: String?,
        val startedAtEpochMs: Long,
        val stoppedAtEpochMs: Long?,
        val eventCount: Long,
        val protocolEventCount: Long,
        val hookReadyCount: Long,
        val bytesWritten: Long,
    )

}

internal data class SmartAudioAssetAttachment(
    val bytes: Long,
    val pngEntryCount: Int,
    val mainImageCount: Int,
    val leftImageCount: Int,
    val rightImageCount: Int,
    val sourceName: String?,
)

internal enum class SmartAudioAutoAttachmentStatus {
    ATTACHED,
    ALREADY_ATTACHED,
    NOT_OBSERVED,
    NEEDS_DEVICE_IDENTITY,
    NEEDS_SUBMODEL,
    AMBIGUOUS,
    FAILED,
}

internal data class SmartAudioAutoAttachmentResult(
    val status: SmartAudioAutoAttachmentStatus,
    val attachment: SmartAudioAssetAttachment? = null,
    val modelId: String? = null,
    val subModelId: String? = null,
    val message: String? = null,
)
