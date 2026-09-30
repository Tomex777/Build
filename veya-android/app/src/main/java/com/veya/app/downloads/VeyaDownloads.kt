package com.veya.app.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.veya.app.MainActivity
import com.veya.app.VeyaApplication
import dev.tomex.youtube.api.MediaTransferCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class VeyaDownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETE,
    FAILED
}

data class VeyaDownload(
    val videoId: String,
    val title: String,
    val thumbnail: String?,
    val preferredHeight: Int,
    val state: VeyaDownloadState,
    val videoStableIdentity: String? = null,
    val audioStableIdentity: String? = null,
    val videoContainer: String? = null,
    val audioContainer: String? = null,
    val videoDownloadedBytes: Long = 0L,
    val videoTotalBytes: Long? = null,
    val audioDownloadedBytes: Long = 0L,
    val audioTotalBytes: Long? = null,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val downloadedBytes: Long get() = videoDownloadedBytes + audioDownloadedBytes
    val totalBytes: Long? get() {
        val v = videoTotalBytes ?: return null
        val a = audioTotalBytes ?: return null
        return v + a
    }
    val progress: Float
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
            ?: 0f
}

class VeyaDownloadStore(context: Context) {
    private val file = File(context.filesDir, "veya-downloads.json")
    private val lock = Any()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<VeyaDownload>> = _items.asStateFlow()

    fun entry(videoId: String): VeyaDownload? =
        _items.value.firstOrNull { it.videoId == videoId }

    fun replace(entry: VeyaDownload) = synchronized(lock) {
        val next = _items.value.filterNot { it.videoId == entry.videoId }.toMutableList()
        next.add(entry.copy(updatedAt = System.currentTimeMillis()))
        publish(next)
    }

    fun update(videoId: String, transform: (VeyaDownload) -> VeyaDownload) =
        synchronized(lock) {
            val current = entry(videoId) ?: return@synchronized
            val next = _items.value.filterNot { it.videoId == videoId }.toMutableList()
            next.add(transform(current).copy(updatedAt = System.currentTimeMillis()))
            publish(next)
        }

    fun remove(videoId: String) = synchronized(lock) {
        publish(_items.value.filterNot { it.videoId == videoId })
    }

    private fun publish(next: List<VeyaDownload>) {
        val sorted = next.sortedByDescending { it.updatedAt }
        _items.value = sorted
        persist(sorted)
    }

    private fun load(): List<VeyaDownload> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val o = array.getJSONObject(index)
                add(
                    VeyaDownload(
                        videoId = o.getString("videoId"),
                        title = o.optString("title", "Video"),
                        thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                        preferredHeight = o.optInt("preferredHeight", 720),
                        state = runCatching {
                            VeyaDownloadState.valueOf(o.optString("state"))
                        }.getOrDefault(VeyaDownloadState.FAILED),
                        videoStableIdentity = o.optString("videoStableIdentity").takeIf { it.isNotBlank() },
                        audioStableIdentity = o.optString("audioStableIdentity").takeIf { it.isNotBlank() },
                        videoContainer = o.optString("videoContainer").takeIf { it.isNotBlank() },
                        audioContainer = o.optString("audioContainer").takeIf { it.isNotBlank() },
                        videoDownloadedBytes = o.optLong("videoDownloadedBytes", 0L),
                        videoTotalBytes = o.optLong("videoTotalBytes", -1L).takeIf { it >= 0L },
                        audioDownloadedBytes = o.optLong("audioDownloadedBytes", 0L),
                        audioTotalBytes = o.optLong("audioTotalBytes", -1L).takeIf { it >= 0L },
                        videoPath = o.optString("videoPath").takeIf { it.isNotBlank() },
                        audioPath = o.optString("audioPath").takeIf { it.isNotBlank() },
                        error = o.optString("error").takeIf { it.isNotBlank() },
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    private fun persist(items: List<VeyaDownload>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("videoId", item.videoId)
                put("title", item.title)
                put("thumbnail", item.thumbnail.orEmpty())
                put("preferredHeight", item.preferredHeight)
                put("state", item.state.name)
                put("videoStableIdentity", item.videoStableIdentity.orEmpty())
                put("audioStableIdentity", item.audioStableIdentity.orEmpty())
                put("videoContainer", item.videoContainer.orEmpty())
                put("audioContainer", item.audioContainer.orEmpty())
                put("videoDownloadedBytes", item.videoDownloadedBytes)
                put("videoTotalBytes", item.videoTotalBytes ?: -1L)
                put("audioDownloadedBytes", item.audioDownloadedBytes)
                put("audioTotalBytes", item.audioTotalBytes ?: -1L)
                put("videoPath", item.videoPath.orEmpty())
                put("audioPath", item.audioPath.orEmpty())
                put("error", item.error.orEmpty())
                put("updatedAt", item.updatedAt)
            })
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }
}

class VeyaDownloadController(
    context: Context,
    private val store: VeyaDownloadStore
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueue(
        videoId: String,
        title: String,
        thumbnail: String?,
        preferredHeight: Int
    ) {
        val current = store.entry(videoId)
        if (current?.state == VeyaDownloadState.COMPLETE) return

        store.replace(
            (current ?: VeyaDownload(
                videoId = videoId,
                title = title,
                thumbnail = thumbnail,
                preferredHeight = preferredHeight.coerceAtLeast(360),
                state = VeyaDownloadState.QUEUED
            )).copy(
                title = title,
                thumbnail = thumbnail ?: current?.thumbnail,
                preferredHeight = preferredHeight.coerceAtLeast(360),
                state = VeyaDownloadState.QUEUED,
                error = null
            )
        )
        schedule(videoId)
    }

    fun pause(videoId: String) {
        workManager.cancelUniqueWork(workName(videoId))
        store.update(videoId) { it.copy(state = VeyaDownloadState.PAUSED, error = null) }
    }

    fun resume(videoId: String) {
        val current = store.entry(videoId) ?: return
        store.replace(current.copy(state = VeyaDownloadState.QUEUED, error = null))
        schedule(videoId)
    }

    fun retry(videoId: String) = resume(videoId)

    fun delete(videoId: String) {
        workManager.cancelUniqueWork(workName(videoId))
        File(appContext.filesDir, "veya-downloads/${safeId(videoId)}").deleteRecursively()
        store.remove(videoId)
    }

    private fun schedule(videoId: String) {
        val request = OneTimeWorkRequestBuilder<VeyaDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
            .build()
        workManager.enqueueUniqueWork(
            workName(videoId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(videoId: String) = "veya-download-${safeId(videoId)}"
}

class VeyaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val app = applicationContext as VeyaApplication
        val store = app.downloads
        var entry = store.entry(videoId) ?: return Result.failure()

        return try {
            setForeground(foreground(entry))

            store.update(videoId) {
                it.copy(state = VeyaDownloadState.DOWNLOADING, error = null)
            }
            entry = store.entry(videoId) ?: return Result.failure()

            if (entry.videoStableIdentity == null || entry.audioStableIdentity == null) {
                val prepared = app.youtubeRepository.preparePlayback(
                    videoId = videoId,
                    preferredHeight = entry.preferredHeight
                )

                store.update(videoId) {
                    it.copy(
                        videoStableIdentity = prepared.video.format.stableIdentity,
                        audioStableIdentity = prepared.audio.format.stableIdentity,
                        videoContainer = prepared.video.format.container,
                        audioContainer = prepared.audio.format.container,
                        videoTotalBytes = totalBytes(
                            prepared.video.format.contentLength,
                            prepared.video.proof.contentRange
                        ),
                        audioTotalBytes = totalBytes(
                            prepared.audio.format.contentLength,
                            prepared.audio.proof.contentRange
                        )
                    )
                }
                entry = store.entry(videoId) ?: return Result.failure()
            }

            val root = File(applicationContext.filesDir, "veya-downloads/${safeId(videoId)}")
                .apply { mkdirs() }

            val videoFinal = File(
                root,
                "video.${normalizedExtension(entry.videoContainer, "video")}"
            )
            val audioFinal = File(
                root,
                "audio.${normalizedExtension(entry.audioContainer, "audio")}"
            )

            val videoResult = downloadTrack(
                videoId = videoId,
                stableIdentity = requireNotNull(entry.videoStableIdentity),
                partFile = File(root, "${videoFinal.name}.part"),
                finalFile = videoFinal,
                knownTotal = entry.videoTotalBytes,
                onProgress = { bytes, total ->
                    store.update(videoId) {
                        it.copy(
                            state = VeyaDownloadState.DOWNLOADING,
                            videoDownloadedBytes = bytes,
                            videoTotalBytes = total ?: it.videoTotalBytes
                        )
                    }
                }
            )
            store.update(videoId) {
                it.copy(
                    videoDownloadedBytes = videoResult.second ?: videoFinal.length(),
                    videoTotalBytes = videoResult.second ?: it.videoTotalBytes,
                    videoPath = videoFinal.absolutePath
                )
            }

            entry = store.entry(videoId) ?: return Result.failure()
            val audioResult = downloadTrack(
                videoId = videoId,
                stableIdentity = requireNotNull(entry.audioStableIdentity),
                partFile = File(root, "${audioFinal.name}.part"),
                finalFile = audioFinal,
                knownTotal = entry.audioTotalBytes,
                onProgress = { bytes, total ->
                    store.update(videoId) {
                        it.copy(
                            state = VeyaDownloadState.DOWNLOADING,
                            audioDownloadedBytes = bytes,
                            audioTotalBytes = total ?: it.audioTotalBytes
                        )
                    }
                }
            )

            store.update(videoId) {
                it.copy(
                    state = VeyaDownloadState.COMPLETE,
                    audioDownloadedBytes = audioResult.second ?: audioFinal.length(),
                    audioTotalBytes = audioResult.second ?: it.audioTotalBytes,
                    audioPath = audioFinal.absolutePath,
                    error = null
                )
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val current = store.entry(videoId)
            if (current != null && current.state != VeyaDownloadState.PAUSED) {
                if (runAttemptCount < 2) {
                    store.update(videoId) {
                        it.copy(state = VeyaDownloadState.QUEUED, error = null)
                    }
                    Result.retry()
                } else {
                    store.update(videoId) {
                        it.copy(
                            state = VeyaDownloadState.FAILED,
                            error = t.message ?: t::class.java.simpleName
                        )
                    }
                    Result.failure()
                }
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun downloadTrack(
        videoId: String,
        stableIdentity: String,
        partFile: File,
        finalFile: File,
        knownTotal: Long?,
        onProgress: (Long, Long?) -> Unit
    ): Pair<File, Long?> {
        if (finalFile.exists() && finalFile.length() > 0L) {
            return finalFile to (knownTotal ?: finalFile.length())
        }

        partFile.parentFile?.mkdirs()
        var offset = partFile.takeIf(File::exists)?.length() ?: 0L
        var total = knownTotal
        onProgress(offset, total)

        while (!isStopped && (total == null || offset < total)) {
            val app = applicationContext as VeyaApplication
            val chunk = app.youtubeEngine.fetchChunkFromCheckpoint(
                MediaTransferCheckpoint(
                    videoId = videoId,
                    stableFormatIdentity = stableIdentity,
                    nextByteOffset = offset,
                    totalBytes = total
                ),
                CHUNK_SIZE
            )
            if (chunk.bytes.isEmpty()) break

            FileOutputStream(partFile, true).use { out ->
                out.write(chunk.bytes)
                out.fd.sync()
            }
            offset += chunk.bytes.size
            total = chunk.totalBytes ?: total
            onProgress(offset, total)
        }

        if (isStopped) throw CancellationException("Download stopped")
        if (total != null && offset < total) {
            error("Download ended before all bytes were received")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        return finalFile to (total ?: finalFile.length())
    }

    private fun foreground(entry: VeyaDownload): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Veya downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(entry.title)
            .setContentText("Downloading for offline playback")
            .setContentIntent(intent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId(entry.videoId),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId(entry.videoId), notification)
        }
    }

    private fun totalBytes(contentLength: Long?, contentRange: String?): Long? =
        contentLength
            ?: contentRange
                ?.substringAfterLast('/', "")
                ?.takeIf { it != "*" }
                ?.toLongOrNull()

    private fun normalizedExtension(container: String?, kind: String): String =
        when (container?.lowercase()) {
            "mp4" -> if (kind == "audio") "m4a" else "mp4"
            "webm" -> "webm"
            "mkv", "matroska" -> "mkv"
            else -> "media"
        }

    private fun notificationId(videoId: String): Int =
        (videoId.hashCode() and 0x7fffffff).coerceAtLeast(1)

    companion object {
        private const val CHANNEL_ID = "veya_downloads"
        private const val CHUNK_SIZE = 4 * 1024 * 1024
    }
}

private const val KEY_VIDEO_ID = "video_id"

private fun safeId(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
