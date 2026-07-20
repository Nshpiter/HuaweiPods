package moe.chenxy.huaweipods.debugcapture

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Debug 抓包组件与注入进程之间的稳定协议。 */
object CaptureContract {
    const val ACTION_CAPTURE_EVENT =
        "moe.chenxy.huaweipods.debugcapture.action.CAPTURE_EVENT"

    const val EXTRA_EVENT_TYPE = "event_type"
    const val EXTRA_DIRECTION = "direction"
    const val EXTRA_CHANNEL = "channel"
    const val EXTRA_OPERATION = "operation"
    const val EXTRA_PAYLOAD_HEX = "payload_hex"
    const val EXTRA_SUMMARY = "summary"
    const val EXTRA_SOURCE_PROCESS = "source_process"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_DEVICE_ADDRESS = "device_address"
    const val EXTRA_TIMESTAMP_EPOCH_MS = "timestamp_epoch_ms"

    const val MIME_CAPTURE_ARCHIVE = "application/zip"

    fun createShareIntent(export: CaptureExport): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_CAPTURE_ARCHIVE
            putExtra(Intent.EXTRA_STREAM, export.uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "HuaweiPods debug capture ${export.sessionId}",
            )
            clipData = ClipData.newRawUri(export.fileName, export.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "分享抓包文件")
    }
}

data class CaptureSessionMetadata(
    val issueId: String? = null,
    val headsetModel: String,
    val officialAppPackage: String? = null,
    val headsetAddress: String? = null,
    val headsetNameSource: String = DEFAULT_HEADSET_NAME_SOURCE,
    val featureCatalogVersion: String = DEFAULT_FEATURE_CATALOG_VERSION,
    val notes: String? = null,
)

data class CaptureSession(
    val id: String,
    val issueId: String?,
    val headsetModel: String,
    val officialAppPackage: String?,
    val headsetAddress: String?,
    val headsetNameSource: String,
    val featureCatalogVersion: String,
    val startedAtEpochMs: Long,
    val stoppedAtEpochMs: Long?,
    val eventCount: Long,
    val protocolEventCount: Long,
    val bytesWritten: Long,
) {
    val isActive: Boolean
        get() = stoppedAtEpochMs == null
}

internal const val DEFAULT_HEADSET_NAME_SOURCE = "manual"
internal const val DEFAULT_FEATURE_CATALOG_VERSION = "huawei-headset-v1"
internal const val LEGACY_HEADSET_NAME_SOURCE = "legacy_model"
internal const val LEGACY_FEATURE_CATALOG_VERSION = "legacy-v1"

data class CaptureState(
    val activeSession: CaptureSession?,
    val latestSession: CaptureSession?,
)

data class CaptureExport(
    val uri: Uri,
    val fileName: String,
    val sessionId: String,
)

internal data class CapturedProtocolEvent(
    val eventType: String?,
    val direction: String?,
    val channel: String?,
    val operation: String?,
    val payloadHex: String?,
    val summary: String?,
    val sourceProcess: String?,
    val deviceName: String?,
    val deviceAddress: String?,
    val timestampEpochMs: Long?,
)
