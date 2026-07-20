package moe.chenxy.huaweipods.debugcapture

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import moe.chenxy.huaweipods.BuildConfig
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
import java.util.concurrent.TimeUnit
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
    private const val HCI_SNOOP_FILE = "btsnoop_hci.log"

    private const val MAX_SESSION_BYTES = 16L * 1024L * 1024L
    private const val MAX_SESSION_EVENTS = 50_000L
    private const val MAX_SHORT_TEXT_LENGTH = 1_024
    private const val MAX_SUMMARY_LENGTH = 8_192
    private const val MAX_PAYLOAD_LENGTH = 65_536
    private const val MAX_HCI_SNOOP_BYTES = 64L * 1024L * 1024L

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

        val startedAt = System.currentTimeMillis()
        val sessionId = createSessionId(startedAt)
        val directory = sessionDirectory(appContext, sessionId)
        check(directory.mkdirs() || directory.isDirectory) {
            "无法创建抓包目录"
        }

        val normalized = normalizeMetadata(metadata)
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

    fun exportLatest(
        context: Context,
        includeHciSnoop: Boolean = false,
    ): CaptureExport = synchronized(lock) {
        val appContext = context.applicationContext
        val state = getState(appContext)
        val session = state.latestSession ?: error("还没有可导出的抓包会话")
        val directory = sessionDirectory(appContext, session.id)
        val metadataFile = File(directory, METADATA_FILE)
        val eventsFile = File(directory, EVENTS_FILE)
        check(metadataFile.isFile && eventsFile.isFile) { "抓包会话文件不完整" }
        val hciSnoop = if (includeHciSnoop) {
            collectOptionalHciSnoop(directory)
        } else {
            HciSnoopResult(
                status = "not_requested",
                source = null,
                file = null,
                bytes = 0L,
                truncated = false,
                message = "本次导出未请求 HCI snoop；需要用户明确同意后再采集",
            )
        }
        val metadata = readMetadataLocked(directory) ?: error("无法读取抓包元数据")
        metadata.put("hci_snoop", hciSnoop.toJson())
        writeMetadataLocked(directory, metadata)

        val exportDirectory = File(appContext.cacheDir, EXPORT_DIRECTORY)
        check(exportDirectory.mkdirs() || exportDirectory.isDirectory) {
            "无法创建导出目录"
        }
        val fileName = "huaweipods-capture-${session.id}.zip"
        val outputFile = File(exportDirectory, fileName)
        val temporaryFile = File(exportDirectory, "$fileName.tmp")

        ZipOutputStream(FileOutputStream(temporaryFile).buffered()).use { zip ->
            zip.putTextEntry(README_FILE, buildReadme(session, hciSnoop))
            zip.putFileEntry(METADATA_FILE, metadataFile)
            zip.putFileEntry(EVENTS_FILE, eventsFile)
            hciSnoop.file?.takeIf(File::isFile)?.let { zip.putFileEntry(HCI_SNOOP_FILE, it) }
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
    ): Boolean = synchronized(lock) {
        withActiveSessionLocked(context.applicationContext) { directory, metadata ->
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
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

    private fun collectOptionalHciSnoop(directory: File): HciSnoopResult {
        val destination = File(directory, HCI_SNOOP_FILE)
        if (destination.isFile && destination.length() > 0L) {
            return HciSnoopResult(
                status = "included",
                source = "previous_export",
                file = destination,
                bytes = destination.length(),
                truncated = destination.length() >= MAX_HCI_SNOOP_BYTES,
            )
        }

        val publicSource = File("/sdcard/btsnoop_hci.log")
        runCatching {
            if (publicSource.isFile && publicSource.canRead()) {
                val copy = copyStreamLimited(publicSource.inputStream(), destination)
                if (copy.bytes > 0L) {
                    return HciSnoopResult(
                        status = "included",
                        source = publicSource.absolutePath,
                        file = destination,
                        bytes = copy.bytes,
                        truncated = copy.truncated,
                    )
                }
            }
        }

        val rootSources = listOf(
            "/data/misc/bluetooth/logs/btsnoop_hci.log",
            "/data/misc/bluedroid/btsnoop_hci.log",
        )
        val failures = mutableListOf<String>()
        rootSources.forEach { source ->
            val result = runCatching { copyRootFileLimited(source, destination) }
                .getOrElse { throwable ->
                    failures += "$source: ${throwable.javaClass.simpleName}"
                    null
                }
            if (result != null && result.bytes > 0L) {
                return HciSnoopResult(
                    status = "included",
                    source = source,
                    file = destination,
                    bytes = result.bytes,
                    truncated = result.truncated,
                )
            }
        }

        return HciSnoopResult(
            status = "unavailable",
            source = null,
            file = null,
            bytes = 0L,
            truncated = false,
            message = if (failures.isEmpty()) {
                "未找到可读的 HCI snoop；应用不会自动修改开发者选项"
            } else {
                "未找到可读的 HCI snoop；Root 读取失败：${failures.joinToString("; ")}"
            },
        )
    }

    private fun copyRootFileLimited(source: String, destination: File): StreamCopyResult? {
        val temporaryFile = File(destination.parentFile, "${destination.name}.tmp")
        val process = ProcessBuilder("su", "-c", "cat '$source' 2>/dev/null").start()
        val copy = copyProcessStreamLimited(process, temporaryFile)
        val completed = if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(1L, TimeUnit.SECONDS)
        } else {
            true
        }
        val successful = copy.bytes > 0L &&
            (copy.truncated || (completed && process.exitValue() == 0))
        if (!successful) {
            temporaryFile.delete()
            return null
        }
        moveReplacing(temporaryFile, destination)
        return copy
    }

    private fun copyProcessStreamLimited(
        process: Process,
        destination: File,
    ): StreamCopyResult {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(8L)
        var bytesWritten = 0L
        var truncated = false
        process.inputStream.buffered().use { source ->
            FileOutputStream(destination, false).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val remaining = MAX_HCI_SNOOP_BYTES - bytesWritten
                    if (remaining <= 0L) {
                        truncated = process.isAlive || source.available() > 0
                        break
                    }
                    val available = source.available()
                    if (available > 0) {
                        val read = source.read(
                            buffer,
                            0,
                            minOf(buffer.size.toLong(), remaining, available.toLong()).toInt(),
                        )
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        continue
                    }
                    if (!process.isAlive) {
                        val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        continue
                    }
                    if (System.nanoTime() >= deadlineNanos) {
                        truncated = bytesWritten > 0L
                        break
                    }
                    Thread.sleep(20L)
                }
            }
        }
        return StreamCopyResult(bytesWritten, truncated)
    }

    private fun copyStreamLimited(
        input: java.io.InputStream,
        destination: File,
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
                    val remaining = MAX_HCI_SNOOP_BYTES - bytesWritten
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

    private fun buildReadme(session: CaptureSession, hciSnoop: HciSnoopResult): String = """
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
        - btsnoop_hci.log：${hciSnoop.readmeDescription()}

        提交前请注意：
        1. 设备蓝牙地址默认已脱敏，但原始协议载荷仍可能包含设备或账号相关信息。
        2. 请先自行检查压缩包，只把它发送给可信任的项目维护者。
        3. 分享时请补充实际操作结果和官方智慧生活中的预期表现；Issue 可稍后关联。
    """.trimIndent() + "\n"

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
        val bytesWritten: Long,
    )

    private data class HciSnoopResult(
        val status: String,
        val source: String?,
        val file: File?,
        val bytes: Long,
        val truncated: Boolean,
        val message: String? = null,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("status", status)
            .put("source", source ?: JSONObject.NULL)
            .put("file", file?.name ?: JSONObject.NULL)
            .put("bytes", bytes)
            .put("truncated", truncated)
            .put("message", message ?: JSONObject.NULL)

        fun readmeDescription(): String = if (file != null) {
            "已附带（来源 $source，$bytes 字节${if (truncated) "，已按 64 MiB 上限截断" else ""}）。"
        } else {
            "未附带。${message.orEmpty()}"
        }
    }
}
