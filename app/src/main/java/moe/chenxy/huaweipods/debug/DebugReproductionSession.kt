package moe.chenxy.huaweipods.debug

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugReproductionSession {
    private const val MAX_LINES = 2_000
    private const val MAX_CHARS = 240_000
    private const val TAG = "HuaweiPods-Repro"

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    @Volatile
    var active: Boolean = false
        private set

    fun start(context: Context) {
        synchronized(lock) {
            lines.clear()
            active = true
            appendLocked("session started device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        }
    }

    fun record(tag: String, message: String) {
        if (!active) return
        synchronized(lock) {
            if (active) appendLocked("$tag: ${sanitize(message)}")
        }
    }

    fun stopAndShare(context: Context) {
        val report = synchronized(lock) {
            if (!active) return
            active = false
            appendLocked("session stopped")
            lines.joinToString("\n")
        }
        val directory = File(context.cacheDir, "reproduction_logs").apply { mkdirs() }
        val file = File(directory, "huaweipods-reproduction-${System.currentTimeMillis()}.txt")
        file.writeText(report, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.debuglogs", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "HuaweiPods reproduction log")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(moe.chenxy.huaweipods.R.string.debug_log_share_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun appendLocked(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        lines.addLast("$time $message")
        while (lines.size > MAX_LINES || lines.sumOf { it.length } > MAX_CHARS) lines.removeFirst()
    }

    private fun sanitize(message: String): String =
        message.replace(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"), "<address>")
            .replace(Regex("(?i)address[=:/ ]+[0-9a-f:]{17}"), "address=<address>")
}
