package com.night.pahebatcher.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.night.pahebatcher.R
import com.night.pahebatcher.data.DownloadQueueStore
import com.night.pahebatcher.data.DownloadState
import com.night.pahebatcher.data.EpisodeInfo
import com.night.pahebatcher.data.PaheRepository
import com.night.pahebatcher.data.PersistentDownload
import com.night.pahebatcher.data.SessionStore
import com.night.pahebatcher.data.VerificationRequired
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PaheDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val store = DownloadQueueStore(appContext)

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val record = store.get(downloadId) ?: return Result.failure()

        store.update(
            id = downloadId,
            state = DownloadState.RUNNING,
            status = if (record.progress > 0f) "Resuming…" else "Resolving release…",
        )
        setForeground(foregroundInfo(record, "Resolving release…", record.progress))

        val episode = EpisodeInfo(
            number = record.episodeNumber,
            session = record.episodeSession,
            title = record.episodeTitle,
            fansub = record.fansub,
            audio = record.sourceAudio,
            playUrl = record.playUrl,
        )

        return try {
            val repository = PaheRepository(applicationContext, SessionStore(applicationContext))
            val stream = repository.resolveStream(
                episode = episode,
                requestedQuality = record.requestedQuality,
                requestedAudio = record.requestedAudio,
            )

            store.update(
                id = downloadId,
                state = DownloadState.RUNNING,
                status = "Downloading ${stream.quality}p…",
            )
            updateForeground(record, "Downloading ${stream.quality}p…", record.progress)

            val uri = repository.downloadStream(
                stream = stream,
                animeTitle = record.animeTitle,
                episode = episode,
                downloadKey = downloadId,
            ) { progress ->
                val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
                val status = "Downloading $percent%"
                store.update(
                    id = downloadId,
                    progress = progress,
                    status = status,
                    state = DownloadState.RUNNING,
                )
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_STATUS to status,
                    )
                )
                updateForeground(record, status, progress)
            }

            store.update(
                id = downloadId,
                progress = 1f,
                status = "Saved to Downloads/PaheBatcher",
                state = DownloadState.COMPLETED,
                uri = uri,
            )
            Result.success(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STATUS to "Saved to Downloads/PaheBatcher",
                    KEY_URI to uri.toString(),
                )
            )
        } catch (e: VerificationRequired) {
            store.update(
                id = downloadId,
                status = "Verify AnimePahe to resume",
                state = DownloadState.WAITING,
            )
            Result.retry()
        } catch (e: IOException) {
            store.update(
                id = downloadId,
                status = "Waiting to resume · downloaded segments are kept",
                state = DownloadState.WAITING,
            )
            Result.retry()
        } catch (e: Exception) {
            store.update(
                id = downloadId,
                status = e.message ?: "Download failed",
                state = DownloadState.FAILED,
            )
            Result.failure(workDataOf(KEY_STATUS to (e.message ?: "Download failed")))
        }
    }

    private fun foregroundInfo(
        record: PersistentDownload,
        status: String,
        progress: Float,
    ): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(record.animeTitle)
            .setContentText("Episode ${episodeLabel(record.episodeNumber)} · $status")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, (progress.coerceIn(0f, 1f) * 100f).toInt(), false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()

        val notificationId = notificationId(record.id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateForeground(
        record: PersistentDownload,
        status: String,
        progress: Float,
    ) {
        val info = foregroundInfo(record, status, progress)
        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId(record.id), info.notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Episode downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Background anime episode downloads"
                }
            )
        }
    }

    private fun notificationId(downloadId: String): Int =
        10_000 + (downloadId.hashCode() and 0x3FFF)

    private fun episodeLabel(number: Double): String =
        if (number == number.toInt().toDouble()) number.toInt().toString() else number.toString()

    companion object {
        const val TAG = "pahe_episode_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_URI = "uri"
        private const val CHANNEL_ID = "pahe_downloads"

        fun enqueue(
            context: Context,
            record: PersistentDownload,
            replace: Boolean = false,
        ) {
            val store = DownloadQueueStore(context)
            store.put(record)

            val request = OneTimeWorkRequestBuilder<PaheDownloadWorker>()
                .setInputData(workDataOf(KEY_DOWNLOAD_ID to record.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS,
                )
                .addTag(TAG)
                .addTag("$TAG:${record.id}")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$TAG:${record.id}",
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun resumeWaiting(context: Context) {
            DownloadQueueStore(context).list()
                .filter { it.state == DownloadState.WAITING || it.state == DownloadState.QUEUED }
                .forEach { enqueue(context, it.copy(state = DownloadState.QUEUED, status = "Queued"), replace = true) }
        }

        fun remove(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("$TAG:$downloadId")
            DownloadQueueStore(context).remove(downloadId)
            File(context.filesDir, "pahe_downloads/$downloadId").deleteRecursively()
        }
    }
}
