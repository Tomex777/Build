package com.night.pahebatcher.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.night.pahebatcher.R
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.math.roundToInt

class EpisodeDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val store = DownloadTaskStore(appContext)
    private val sessionStore = SessionStore(appContext)
    private val repository = PaheRepository(appContext, sessionStore)
    private val taskId = inputData.getString(INPUT_TASK_ID).orEmpty()

    override suspend fun doWork(): Result {
        if (taskId.isBlank()) return Result.failure()
        var task = store.get(taskId) ?: return Result.failure()

        ensureNotificationChannel()
        store.markRunning(taskId, "Preparing download…")
        setForeground(foregroundInfo(task, task.progress))

        return try {
            var stream = task.resolvedStreamIfFresh()
            if (stream == null) {
                val resolved = resolveFreshEpisodeAndStream(task)
                task = resolved.first
                stream = resolved.second
                store.saveResolved(taskId, stream)
            }

            try {
                completeWithStream(task, stream)
            } catch (e: IOException) {
                if (looksLikeExpiredStream(e)) {
                    store.clearResolved(taskId)
                    store.markRunning(taskId, "Refreshing expired episode link…")
                    val refreshed = resolveFreshEpisodeAndStream(store.get(taskId) ?: task)
                    task = refreshed.first
                    stream = refreshed.second
                    store.saveResolved(taskId, stream)
                    completeWithStream(task, stream)
                } else {
                    throw e
                }
            }

            Result.success()
        } catch (e: VerificationRequired) {
            val hasSavedBrowserSession = sessionStore.animeCookie().isNotBlank()
            val status = if (hasSavedBrowserSession) {
                "Paused — AnimePahe source blocked; retrying with saved browser session"
            } else {
                "Paused — verify AnimePahe to resume"
            }
            store.markPaused(taskId, status)
            notifyCurrent(status, store.get(taskId)?.progress ?: 0f)
            Result.retry()
        } catch (e: CancellationException) {
            store.markPaused(taskId, "Paused — will resume automatically")
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            store.markPaused(
                taskId,
                if (message.isBlank()) {
                    "Paused — waiting to resume"
                } else {
                    "Paused — $message"
                },
            )
            notifyCurrent("Paused — will resume", store.get(taskId)?.progress ?: 0f)
            Result.retry()
        }
    }

    private suspend fun resolveFreshEpisodeAndStream(
        initialTask: StoredDownloadTask,
    ): Pair<StoredDownloadTask, StreamInfo> {
        var task = initialTask
        var episode = task.episode()

        if (episode.playUrl.isBlank()) {
            store.markRunning(taskId, "Matching AnimePahe source…")
            episode = repository.resolveCatalogEpisode(
                catalog = task.catalog(),
                episodeNumber = task.episodeNumber,
                preferredAudio = task.requestedAudio,
            )
            store.saveEpisodeSource(taskId, episode)
            task = store.get(taskId) ?: task.copy(
                episodeSession = episode.session,
                episodeTitle = episode.title,
                episodeFansub = episode.fansub,
                episodeAudio = episode.audio,
                playUrl = episode.playUrl,
            )
        }

        store.markRunning(taskId, "Resolving episode stream…")
        val stream = try {
            repository.resolveStream(
                episode = episode,
                requestedQuality = task.requestedQuality,
                requestedAudio = task.requestedAudio,
            )
        } catch (_: VerificationRequired) {
            // Do not immediately blame the browser session. Refresh the
            // AnimePahe title/session/episode first; only a blocked refresh
            // propagates VerificationRequired.
            store.markRunning(taskId, "Refreshing AnimePahe source…")
            episode = repository.resolveCatalogEpisode(
                catalog = task.catalog(),
                episodeNumber = task.episodeNumber,
                preferredAudio = task.requestedAudio,
            )
            store.saveEpisodeSource(taskId, episode)
            task = store.get(taskId) ?: task
            repository.resolveStream(
                episode = episode,
                requestedQuality = task.requestedQuality,
                requestedAudio = task.requestedAudio,
            )
        } catch (_: IOException) {
            store.markRunning(taskId, "Refreshing AnimePahe source…")
            episode = repository.resolveCatalogEpisode(
                catalog = task.catalog(),
                episodeNumber = task.episodeNumber,
                preferredAudio = task.requestedAudio,
            )
            store.saveEpisodeSource(taskId, episode)
            task = store.get(taskId) ?: task
            repository.resolveStream(
                episode = episode,
                requestedQuality = task.requestedQuality,
                requestedAudio = task.requestedAudio,
            )
        }

        return task to stream
    }

    private suspend fun completeWithStream(task: StoredDownloadTask, stream: StreamInfo) {
        val uri = repository.downloadStream(
            stream = stream,
            animeTitle = task.animeTitle,
            episode = task.episode(),
            downloadKey = task.id,
        ) { progress ->
            val percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
            store.markProgress(
                task.id,
                progress,
                "Downloading ${stream.quality}p… $percent%",
            )
            notifyCurrent("Downloading ${task.animeTitle}", progress)
        }

        store.markCompleted(task.id, uri)
        notifyCurrent("Download complete", 1f, done = true)
    }

    private fun looksLikeExpiredStream(error: IOException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 403", true) ||
            message.contains("HTTP 404", true) ||
            message.contains("HTTP 410", true)
    }

    private fun foregroundInfo(task: StoredDownloadTask, progress: Float): ForegroundInfo {
        val notification = buildNotification(
            title = "Downloading ${task.animeTitle}",
            progress = progress,
            done = false,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId(), notification)
        }
    }

    private fun notifyCurrent(title: String, progress: Float, done: Boolean = false) {
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId(), buildNotification(title, progress, done))
        }
    }

    private fun buildNotification(
        title: String,
        progress: Float,
        done: Boolean,
    ) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(
            if (done) {
                if (progress >= 1f) "Saved to Downloads/PaheBatcher" else "Open PaheBatcher to continue"
            } else {
                "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
            }
        )
        .setOnlyAlertOnce(true)
        .setOngoing(!done)
        .setProgress(100, (progress.coerceIn(0f, 1f) * 100f).roundToInt(), false)
        .build()

    private fun notificationId(): Int =
        (taskId.hashCode() and 0x7fffffff).coerceAtLeast(1001)

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Episode downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background anime episode downloads"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val INPUT_TASK_ID = "download_task_id"
        private const val CHANNEL_ID = "pahe_episode_downloads"
    }
}
