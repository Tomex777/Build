package com.veya.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.veya.app.VeyaApplication
import com.veya.app.model.DownloadItem
import com.veya.app.model.DownloadStatus
import com.veya.app.model.ResolvedMedia
import java.util.UUID

object VeyaDownloadManager {
    fun enqueue(context: Context, media: ResolvedMedia): String {
        val app = context.applicationContext as VeyaApplication
        val id = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = id,
            url = media.url,
            title = media.title,
            fileName = media.fileName,
            mimeType = media.mimeType,
            totalBytes = media.sizeBytes,
            status = DownloadStatus.QUEUED,
        )
        app.downloads.upsert(item)
        schedule(context, id)
        return id
    }

    fun pause(context: Context, id: String) {
        val app = context.applicationContext as VeyaApplication
        app.downloads.update(id) { it.copy(status = DownloadStatus.PAUSED, error = null) }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun resume(context: Context, id: String) {
        val app = context.applicationContext as VeyaApplication
        app.downloads.update(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
        schedule(context, id)
    }

    fun cancel(context: Context, id: String) {
        val app = context.applicationContext as VeyaApplication
        app.downloads.update(id) { it.copy(status = DownloadStatus.CANCELLED) }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun retry(context: Context, id: String) {
        val app = context.applicationContext as VeyaApplication
        app.downloads.update(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
        schedule(context, id)
    }

    private fun schedule(context: Context, id: String) {
        val prefs = context.getSharedPreferences("veya_settings", Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("wifi_only", false)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_ID, id).build())
            .addTag("veya_download")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(id: String) = "veya_download_$id"
}
