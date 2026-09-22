package com.example.whatsapp.data.night

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ServiceInfo
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.whatsapp.extensions.messages.NightExtensionMessageActionRegistry
import com.example.whatsapp.extensions.messages.NightExtensionStandardActions
import com.example.whatsapp.extensions.runtime.NightExternalExtensionManager
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class NightMediaDownloadQueue private constructor(
    private val context: Context,
) {
    fun enqueue(
        extensionId: String,
        chatId: String,
        messageId: String,
        messageType: String,
        payload: JSONObject,
        parallelism: Int = 3,
    ): String {
        val url = payload.optString("mediaUrl").trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "This media card does not contain a downloadable URL."
        }

        val title = payload.optString("title")
            .trim()
            .ifBlank { "Night media" }
        val fileName = payload.optString("fileName")
            .trim()
            .ifBlank { title + ".mp4" }
        val mimeType = payload.optString("mimeType")
            .trim()
            .ifBlank { "application/octet-stream" }
        val mediaId = payload.optString("mediaId")
            .trim()
            .ifBlank { url }
        val workKey = digestKey(
            extensionId + "|" + mediaId + "|" + fileName
        )

        val refreshPayload = payload.toString()
            .takeIf { it.toByteArray().size <= MAX_REFRESH_PAYLOAD_BYTES }
            .orEmpty()

        val input = Data.Builder()
            .putString(NightMediaDownloadWorker.INPUT_URL, url)
            .putString(
                NightMediaDownloadWorker.INPUT_HEADERS,
                payload.optJSONObject("headers")?.toString() ?: "{}",
            )
            .putString(NightMediaDownloadWorker.INPUT_FILE_NAME, fileName)
            .putString(NightMediaDownloadWorker.INPUT_MIME_TYPE, mimeType)
            .putString(NightMediaDownloadWorker.INPUT_TITLE, title)
            .putString(NightMediaDownloadWorker.INPUT_WORK_KEY, workKey)
            .putInt(
                NightMediaDownloadWorker.INPUT_PARALLELISM,
                parallelism.coerceIn(1, 6),
            )
            .putString(NightMediaDownloadWorker.INPUT_EXTENSION_ID, extensionId)
            .putString(NightMediaDownloadWorker.INPUT_CHAT_ID, chatId)
            .putString(NightMediaDownloadWorker.INPUT_MESSAGE_ID, messageId)
            .putString(NightMediaDownloadWorker.INPUT_MESSAGE_TYPE, messageType)
            .putString(
                NightMediaDownloadWorker.INPUT_REFRESH_PAYLOAD,
                refreshPayload,
            )
            .build()

        val request =
            OneTimeWorkRequestBuilder<NightMediaDownloadWorker>()
                .setInputData(input)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "night-media:" + workKey,
                ExistingWorkPolicy.KEEP,
                request,
            )

        return request.id.toString()
    }

    private fun digestKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_REFRESH_PAYLOAD_BYTES = 6_000

        @Volatile private var instance: NightMediaDownloadQueue? = null

        fun get(context: Context): NightMediaDownloadQueue =
            instance ?: synchronized(this) {
                instance ?: NightMediaDownloadQueue(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}

class NightMediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(INPUT_URL).orEmpty().trim()
        if (url.isBlank()) return Result.failure()

        val title = inputData.getString(INPUT_TITLE)
            .orEmpty()
            .ifBlank { "Night media" }
        val workKey = inputData.getString(INPUT_WORK_KEY)
            .orEmpty()
            .ifBlank { id.toString() }
        val fileName = inputData.getString(INPUT_FILE_NAME)
            .orEmpty()
            .ifBlank { "Night download.mp4" }
        val mimeType = inputData.getString(INPUT_MIME_TYPE)
            .orEmpty()
            .ifBlank { "application/octet-stream" }
        val parallelism = inputData.getInt(INPUT_PARALLELISM, 3)
            .coerceIn(1, 6)

        ensureChannel()
        setForeground(foregroundInfo(title, 0f, false, "Preparing download"))

        var request = NightMediaDownloadRequest(
            url = url,
            headers = parseHeaders(inputData.getString(INPUT_HEADERS)),
            fileName = fileName,
            mimeType = mimeType,
            workKey = workKey,
            parallelism = parallelism,
        )

        suspend fun runDownload(): NightMediaDownloadResult =
            NightMediaDownloader(applicationContext).download(
                request = request,
            ) { progress ->
                val percent = (
                    progress.coerceIn(0f, 1f) * 100f
                    ).roundToInt()
                setProgress(
                    Data.Builder()
                        .putInt(OUTPUT_PROGRESS, percent)
                        .build()
                )
                setForeground(
                    foregroundInfo(
                        title = title,
                        progress = progress,
                        done = false,
                        text = percent.toString() + "%",
                    )
                )
            }

        return try {
            val result =
                try {
                    runDownload()
                } catch (error: IOException) {
                    val refreshed =
                        if (looksLikeExpiredStream(error)) {
                            refreshRequest(request)
                        } else {
                            null
                        }

                    if (refreshed == null) {
                        throw error
                    }
                    request = refreshed
                    setForeground(
                        foregroundInfo(
                            title = title,
                            progress = 0f,
                            done = false,
                            text = "Refreshing source",
                        )
                    )
                    runDownload()
                }

            notify(
                title = "Download complete",
                text = "Saved " + result.displayName + " to Downloads/Night",
                progress = 1f,
                done = true,
            )
            Result.success(
                Data.Builder()
                    .putString(OUTPUT_URI, result.uri.toString())
                    .putString(OUTPUT_FILE_NAME, result.displayName)
                    .putString(OUTPUT_MIME_TYPE, result.mimeType)
                    .putBoolean(OUTPUT_HLS, result.hls)
                    .putBoolean(OUTPUT_MUXED_MKV, result.muxedToMkv)
                    .putInt(OUTPUT_PROGRESS, 100)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            notify(
                title = if (runAttemptCount < MAX_RETRIES) {
                    "Download paused"
                } else {
                    "Download failed"
                },
                text = error.message.orEmpty().ifBlank {
                    "Night could not finish this download."
                },
                progress = 0f,
                done = true,
            )

            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(
                            OUTPUT_ERROR,
                            error.message ?: "Download failed.",
                        )
                        .build()
                )
            }
        }
    }

    private suspend fun refreshRequest(
        current: NightMediaDownloadRequest,
    ): NightMediaDownloadRequest? {
        val extensionId = inputData.getString(INPUT_EXTENSION_ID)
            .orEmpty()
            .trim()
        val refreshPayload = inputData.getString(INPUT_REFRESH_PAYLOAD)
            .orEmpty()
        if (extensionId.isBlank() || refreshPayload.isBlank()) return null

        val manager = NightExternalExtensionManager.get(applicationContext)
        manager.refreshInstalledExtensions()
        if (!NightExtensionMessageActionRegistry.hasHandler(extensionId)) return null

        val result = NightExtensionMessageActionRegistry.execute(
            extensionId = extensionId,
            chatId = inputData.getString(INPUT_CHAT_ID).orEmpty(),
            messageId = inputData.getString(INPUT_MESSAGE_ID).orEmpty(),
            messageType = inputData.getString(INPUT_MESSAGE_TYPE).orEmpty(),
            actionId = NightExtensionStandardActions.REFRESH_MEDIA,
            payload = JSONObject(refreshPayload),
        ) ?: return null

        if (!result.optBoolean("ok", true)) return null
        val url = result.optString("mediaUrl").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        return current.copy(
            url = url,
            headers = parseHeaders(
                result.optJSONObject("headers")?.toString()
            ),
            fileName = result.optString("fileName")
                .trim()
                .ifBlank { current.fileName },
            mimeType = result.optString("mimeType")
                .trim()
                .ifBlank { current.mimeType },
        )
    }

    private fun parseHeaders(raw: String?): Map<String, String> {
        val json = runCatching {
            JSONObject(raw.orEmpty().ifBlank { "{}" })
        }.getOrElse { JSONObject() }

        return buildMap {
            json.keys().forEach { key ->
                val value = json.optString(key).trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }

    private fun looksLikeExpiredStream(error: IOException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("HTTP 403", true) ||
            message.contains("HTTP 404", true) ||
            message.contains("HTTP 410", true) ||
            message.contains("playlist", true)
    }

    private fun foregroundInfo(
        title: String,
        progress: Float,
        done: Boolean,
        text: String,
    ): ForegroundInfo {
        val notification = notification(
            title = title,
            text = text,
            progress = progress,
            done = done,
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

    private fun notify(
        title: String,
        text: String,
        progress: Float,
        done: Boolean,
    ) {
        runCatching {
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .notify(
                    notificationId(),
                    notification(title, text, progress, done),
                )
        }
    }

    private fun notification(
        title: String,
        text: String,
        progress: Float,
        done: Boolean,
    ) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)
            .setProgress(
                100,
                (progress.coerceIn(0f, 1f) * 100f).roundToInt(),
                false,
            )
            .build()

    private fun notificationId(): Int =
        (id.hashCode() and 0x7fffffff).coerceAtLeast(2201)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(
            NotificationManager::class.java
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Night media downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background media downloads managed by Night"
            }
        )
    }

    companion object {
        const val INPUT_URL = "night_media_url"
        const val INPUT_HEADERS = "night_media_headers"
        const val INPUT_FILE_NAME = "night_media_file_name"
        const val INPUT_MIME_TYPE = "night_media_mime"
        const val INPUT_TITLE = "night_media_title"
        const val INPUT_WORK_KEY = "night_media_work_key"
        const val INPUT_PARALLELISM = "night_media_parallelism"
        const val INPUT_EXTENSION_ID = "night_media_extension_id"
        const val INPUT_CHAT_ID = "night_media_chat_id"
        const val INPUT_MESSAGE_ID = "night_media_message_id"
        const val INPUT_MESSAGE_TYPE = "night_media_message_type"
        const val INPUT_REFRESH_PAYLOAD = "night_media_refresh_payload"

        const val OUTPUT_PROGRESS = "night_media_progress"
        const val OUTPUT_URI = "night_media_output_uri"
        const val OUTPUT_FILE_NAME = "night_media_output_name"
        const val OUTPUT_MIME_TYPE = "night_media_output_mime"
        const val OUTPUT_HLS = "night_media_output_hls"
        const val OUTPUT_MUXED_MKV = "night_media_output_mkv"
        const val OUTPUT_ERROR = "night_media_error"

        private const val CHANNEL_ID = "night_media_downloads"
        private const val MAX_RETRIES = 4
    }
}
