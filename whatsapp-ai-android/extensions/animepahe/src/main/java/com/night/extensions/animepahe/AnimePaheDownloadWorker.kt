package com.night.extensions.animepahe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class AnimePaheDownloadQueue(
    private val context: Context,
) {
    fun enqueue(
        payload: JSONObject,
        parallelism: Int,
    ): String {
        val mediaUrl =
            payload.optString("mediaUrl")
                .trim()
        require(mediaUrl.startsWith("http")) {
            "AnimePahe did not provide a downloadable HLS URL."
        }

        val headers =
            payload.optJSONObject("headers")
                ?: JSONObject()

        val workKey =
            payload.optString("mediaId")
                .trim()
                .ifBlank {
                    UUID.randomUUID()
                        .toString()
                }

        val data =
            Data.Builder()
                .putString(
                    AnimePaheDownloadWorker.INPUT_URL,
                    mediaUrl,
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_HEADERS,
                    headers.toString(),
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_FILE_NAME,
                    payload.optString("fileName")
                        .trim()
                        .ifBlank {
                            "AnimePahe episode.mp4"
                        },
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_TITLE,
                    payload.optString("title")
                        .trim()
                        .ifBlank {
                            "AnimePahe episode"
                        },
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_ANIME_SESSION,
                    payload.optString("animeSession")
                        .trim(),
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_EPISODE_SESSION,
                    payload.optString("episodeSession")
                        .trim(),
                )
                .putString(
                    AnimePaheDownloadWorker.INPUT_WORK_KEY,
                    workKey,
                )
                .putInt(
                    AnimePaheDownloadWorker.INPUT_PARALLELISM,
                    parallelism.coerceIn(1, 6),
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<
                AnimePaheDownloadWorker
            >()
                .setInputData(data)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "animepahe:" + workKey,
                ExistingWorkPolicy.KEEP,
                request,
            )

        return request.id.toString()
    }
}

class AnimePaheDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(
    appContext,
    params,
) {
    override suspend fun doWork(): Result {
        val url =
            inputData.getString(INPUT_URL)
                .orEmpty()
                .trim()
        if (url.isBlank()) {
            return Result.failure()
        }

        val title =
            inputData.getString(INPUT_TITLE)
                .orEmpty()
                .ifBlank {
                    "AnimePahe episode"
                }

        val headersJson =
            inputData.getString(INPUT_HEADERS)
                .orEmpty()
        val headers =
            runCatching {
                val raw =
                    JSONObject(headersJson)
                buildMap<String, String> {
                    raw.keys().forEach { key ->
                        val value =
                            raw.optString(key)
                                .trim()
                        if (
                            key.isNotBlank() &&
                            value.isNotBlank()
                        ) {
                            put(
                                key,
                                value,
                            )
                        }
                    }
                }
            }.getOrElse {
                emptyMap()
            }

        val animeSession =
            inputData.getString(
                INPUT_ANIME_SESSION
            ).orEmpty()
                .trim()

        val episodeSession =
            inputData.getString(
                INPUT_EPISODE_SESSION
            ).orEmpty()
                .trim()

        val fileName =
            inputData.getString(
                INPUT_FILE_NAME
            ).orEmpty()
                .ifBlank {
                    "AnimePahe episode.mp4"
                }

        val workKey =
            inputData.getString(
                INPUT_WORK_KEY
            ).orEmpty()
                .ifBlank {
                    id.toString()
                }

        val parallelism =
            inputData.getInt(
                INPUT_PARALLELISM,
                2,
            ).coerceIn(1, 6)

        ensureChannel()
        setForeground(
            foregroundInfo(
                title = title,
                progress = 0f,
                done = false,
            )
        )

        return try {
            var downloadRequest =
                AnimePaheDownloadRequest(
                    manifestUrl = url,
                    headers = headers,
                    fileName = fileName,
                    workKey = workKey,
                    parallelism = parallelism,
                )

            val downloader =
                AnimePaheHlsDownloader(
                    applicationContext
                )

            suspend fun runDownload(): AnimePaheDownloadResult =
                downloader.download(
                    request = downloadRequest,
                ) { progress ->
                    setProgress(
                        Data.Builder()
                            .putInt(
                                OUTPUT_PROGRESS,
                                (
                                    progress
                                        .coerceIn(
                                            0f,
                                            1f,
                                        ) *
                                        100f
                                    ).roundToInt(),
                            )
                            .build()
                    )
                    setForeground(
                        foregroundInfo(
                            title = title,
                            progress = progress,
                            done = false,
                        )
                    )
                }

            val result =
                try {
                    runDownload()
                } catch (error: java.io.IOException) {
                    if (
                        animeSession.isBlank() ||
                        episodeSession.isBlank() ||
                        !looksLikeExpiredStream(error)
                    ) {
                        throw error
                    }

                    setForeground(
                        foregroundInfo(
                            title =
                                "Refreshing " +
                                    title,
                            progress = 0f,
                            done = false,
                        )
                    )

                    downloadRequest =
                        refreshResolvedRequest(
                            current =
                                downloadRequest,
                            animeSession =
                                animeSession,
                            episodeSession =
                                episodeSession,
                        )

                    runDownload()
                }

            notify(
                title = "Download complete",
                text =
                    if (
                        result.remuxedToMp4
                    ) {
                        "Saved MP4 to Downloads/Night/AnimePahe"
                    } else {
                        "Saved TS fallback to Downloads/Night/AnimePahe"
                    },
                progress = 1f,
                done = true,
            )

            Result.success(
                Data.Builder()
                    .putString(
                        OUTPUT_URI,
                        result.uri.toString(),
                    )
                    .putString(
                        OUTPUT_FILE_NAME,
                        result.displayName,
                    )
                    .putString(
                        OUTPUT_MIME_TYPE,
                        result.mimeType,
                    )
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            notify(
                title = "AnimePahe download paused",
                text =
                    error.message
                        .orEmpty()
                        .ifBlank {
                            "The download can be retried."
                        },
                progress = 0f,
                done = true,
            )
            Result.retry()
        }
    }

    private fun refreshResolvedRequest(
        current: AnimePaheDownloadRequest,
        animeSession: String,
        episodeSession: String,
    ): AnimePaheDownloadRequest {
        val store =
            AnimePaheSessionStore(
                applicationContext
            )
        val client =
            AnimePaheClient(store)
        val sources =
            client.sources(
                animeSession = animeSession,
                episodeSession =
                    episodeSession,
            )
        val selected =
            client.selectPreferredSource(
                sources
            )
        val resolved =
            PaheBatcherHlsResolver(
                store
            ).resolve(
                source = selected,
                playUrl =
                    store.baseUrl() +
                        "/play/" +
                        animeSession +
                        "/" +
                        episodeSession,
            )

        return current.copy(
            manifestUrl = resolved.url,
            headers = resolved.headers,
        )
    }

    private fun looksLikeExpiredStream(
        error: java.io.IOException,
    ): Boolean {
        val message =
            error.message.orEmpty()
        return message.contains(
            "HTTP 403",
            true,
        ) ||
            message.contains(
                "HTTP 404",
                true,
            ) ||
            message.contains(
                "HTTP 410",
                true,
            ) ||
            message.contains(
                "playlist",
                true,
            )
    }

    private fun foregroundInfo(
        title: String,
        progress: Float,
        done: Boolean,
    ): ForegroundInfo {
        val notification =
            notification(
                title = title,
                text =
                    (
                        progress.coerceIn(
                            0f,
                            1f,
                        ) *
                            100f
                        ).roundToInt()
                        .toString() +
                        "%",
                progress = progress,
                done = done,
            )

        return if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            ForegroundInfo(
                notificationId(),
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                notificationId(),
                notification,
            )
        }
    }

    private fun notify(
        title: String,
        text: String,
        progress: Float,
        done: Boolean,
    ) {
        runCatching {
            val manager =
                applicationContext
                    .getSystemService(
                        NotificationManager::class.java
                    )
            manager.notify(
                notificationId(),
                notification(
                    title = title,
                    text = text,
                    progress = progress,
                    done = done,
                ),
            )
        }
    }

    private fun notification(
        title: String,
        text: String,
        progress: Float,
        done: Boolean,
    ) =
        NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID,
        )
            .setSmallIcon(
                android.R.drawable
                    .stat_sys_download
            )
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)
            .setProgress(
                100,
                (
                    progress.coerceIn(
                        0f,
                        1f,
                    ) *
                        100f
                    ).roundToInt(),
                false,
            )
            .build()

    private fun notificationId(): Int =
        (
            id.hashCode() and
                0x7fffffff
            ).coerceAtLeast(1201)

    private fun ensureChannel() {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            applicationContext
                .getSystemService(
                    NotificationManager::class.java
                )
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Anime episode downloads",
                NotificationManager
                    .IMPORTANCE_LOW,
            ).apply {
                description =
                    "Background AnimePahe episode downloads"
            }
        manager.createNotificationChannel(
            channel
        )
    }

    companion object {
        const val INPUT_URL =
            "animepahe_download_url"
        const val INPUT_HEADERS =
            "animepahe_download_headers"
        const val INPUT_FILE_NAME =
            "animepahe_download_file_name"
        const val INPUT_TITLE =
            "animepahe_download_title"
        const val INPUT_ANIME_SESSION =
            "animepahe_download_anime_session"
        const val INPUT_EPISODE_SESSION =
            "animepahe_download_episode_session"
        const val INPUT_WORK_KEY =
            "animepahe_download_work_key"
        const val INPUT_PARALLELISM =
            "animepahe_download_parallelism"

        const val OUTPUT_PROGRESS =
            "animepahe_download_progress"
        const val OUTPUT_URI =
            "animepahe_download_uri"
        const val OUTPUT_FILE_NAME =
            "animepahe_download_output_file"
        const val OUTPUT_MIME_TYPE =
            "animepahe_download_output_mime"

        private const val CHANNEL_ID =
            "night_animepahe_downloads"
    }
}
