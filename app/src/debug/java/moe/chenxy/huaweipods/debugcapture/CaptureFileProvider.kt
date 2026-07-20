package moe.chenxy.huaweipods.debugcapture

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** 仅向获得临时 URI 授权的分享目标暴露 Debug 抓包 ZIP。 */
class CaptureFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = CaptureContract.MIME_CAPTURE_ARCHIVE

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("只允许读取抓包文件")
        val file = resolveFile(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = resolveFile(uri)
        val requested = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val supported = requested.filter {
            it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE
        }
        return MatrixCursor(supported.toTypedArray(), 1).apply {
            addRow(
                supported.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("只读 Provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("只读 Provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("只读 Provider")

    private fun resolveFile(uri: Uri): File {
        if (uri.authority != requireNotNull(context).packageName + AUTHORITY_SUFFIX) {
            throw FileNotFoundException("未知的抓包 URI")
        }
        if (uri.pathSegments.size != 2 || uri.pathSegments[0] != URI_PATH) {
            throw FileNotFoundException("无效的抓包 URI")
        }
        val fileName = uri.pathSegments[1]
        if (!fileName.startsWith(FILE_PREFIX) || !fileName.endsWith(".zip")) {
            throw FileNotFoundException("无效的抓包文件名")
        }
        val root = exportDirectory(requireNotNull(context)).canonicalFile
        val file = File(root, fileName).canonicalFile
        if (file.parentFile != root || !file.isFile) {
            throw FileNotFoundException("抓包文件不存在")
        }
        return file
    }

    companion object {
        const val AUTHORITY_SUFFIX = ".debugcapture.files"
        private const val URI_PATH = "captures"
        private const val FILE_PREFIX = "huaweipods-capture-"
        private const val EXPORT_DIRECTORY = "debug-capture-exports"

        fun uriFor(context: Context, file: File): Uri {
            val root = exportDirectory(context).canonicalFile
            val canonicalFile = file.canonicalFile
            require(canonicalFile.parentFile == root && canonicalFile.isFile) {
                "只能分享 Debug 抓包导出目录中的文件"
            }
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + AUTHORITY_SUFFIX)
                .appendPath(URI_PATH)
                .appendPath(canonicalFile.name)
                .build()
        }

        private fun exportDirectory(context: Context): File =
            File(context.cacheDir, EXPORT_DIRECTORY)
    }
}
