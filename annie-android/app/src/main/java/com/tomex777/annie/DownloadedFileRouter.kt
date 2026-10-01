package com.tomex777.annie

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

internal enum class DownloadOpenRoute { VIDEO, MUSIC, EXTERNAL }

/** The single opening/sharing boundary. Failed viewing never changes download state. */
internal object DownloadedFileRouter {
    fun mime(item: DownloadItem): String {
        val name = item.filename ?: File(item.localPath).name
        val inferred = MimeTypeMap.getSingleton().getMimeTypeFromExtension(DownloadFileMetadata.extension(name).orEmpty())
        return DownloadFileMetadata.mime(item.sourceMimeType, inferred, name)
    }

    fun route(item: DownloadItem): DownloadOpenRoute = when {
        mime(item).startsWith("video/") -> DownloadOpenRoute.VIDEO
        mime(item).startsWith("audio/") -> DownloadOpenRoute.MUSIC
        else -> DownloadOpenRoute.EXTERNAL
    }

    fun viewIntent(context: Context, item: DownloadItem): Intent = Intent(Intent.ACTION_VIEW).apply {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.download-files", File(item.localPath))
        setDataAndType(uri, mime(item))
        clipData = ClipData.newRawUri(item.filename ?: "Download", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun shareIntent(context: Context, item: DownloadItem): Intent = Intent(Intent.ACTION_SEND).apply {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.download-files", File(item.localPath))
        type = mime(item)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(item.filename ?: "Download", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun openExternal(context: Context, item: DownloadItem): Boolean {
        if (!File(item.localPath).isFile) { message(context, "File is no longer available."); return false }
        return try {
            val view = viewIntent(context, item)
            if (context.packageManager.queryIntentActivities(view, 0).isEmpty()) {
                message(context, "No app can open this file.")
                false
            } else {
                context.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (_: ActivityNotFoundException) {
            message(context, "No app can open this file."); false
        } catch (_: IllegalArgumentException) {
            message(context, "File is no longer available."); false
        }
    }

    fun share(context: Context, item: DownloadItem) {
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent(context, item), "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { message(context, "Unable to share this file.") }
    }

    private fun message(context: Context, value: String) = Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
}
