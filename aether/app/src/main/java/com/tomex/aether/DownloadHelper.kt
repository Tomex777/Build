package com.tomex.aether

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.getSystemService

object DownloadHelper {
    fun enqueue(context: Context, post: MemePost): Long {
        val extension = when (post.kind) {
            MediaKind.IMAGE -> post.mediaUrl.substringBefore('?').substringAfterLast('.', "jpg").take(5)
            MediaKind.GIF -> "gif"
            MediaKind.VIDEO -> "mp4"
        }
        val safeTitle = post.title
            .replace(Regex("[^a-zA-Z0-9._ -]"), "")
            .trim()
            .take(60)
            .ifBlank { "aether-${post.id}" }
        val fileName = "$safeTitle-${post.id}.$extension"
        val request = DownloadManager.Request(Uri.parse(post.mediaUrl))
            .setTitle(post.title)
            .setDescription("Downloading from r/${post.subreddit}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Aether/$fileName")
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        return context.getSystemService<DownloadManager>()?.enqueue(request) ?: -1L
    }
}
