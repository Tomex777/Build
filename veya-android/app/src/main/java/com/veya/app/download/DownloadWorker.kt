package com.veya.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.veya.app.BuildConfig
import com.veya.app.VeyaApplication
import com.veya.app.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val app = appContext as VeyaApplication
    private val store = app.downloads

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val item = store.get(id) ?: return@withContext Result.failure()
        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.CANCELLED) {
            return@withContext Result.success()
        }

        setForeground(foreground(item.title, 0, true))
        store.update(id) { it.copy(status = DownloadStatus.DOWNLOADING, error = null) }

        val partsDir = File(applicationContext.filesDir, "partials").apply { mkdirs() }
        val part = File(partsDir, "$id.part")

        try {
            var existing = part.length()
            var conn = open(item.url, existing)
            var response = conn.responseCode

            if (existing > 0 && response == HttpURLConnection.HTTP_OK) {
                part.delete()
                existing = 0
                conn.disconnect()
                conn = open(item.url, 0)
                response = conn.responseCode
            }

            if (response !in 200..299) error("Server returned HTTP $response")

            val total = parseTotal(conn, existing)
            store.update(id) { it.copy(totalBytes = if (total > 0) total else it.totalBytes) }

            RandomAccessFile(part, "rw").use { raf ->
                if (response == HttpURLConnection.HTTP_PARTIAL) raf.seek(existing) else raf.setLength(0)
                conn.inputStream.buffered(128 * 1024).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = if (response == HttpURLConnection.HTTP_PARTIAL) existing else 0L
                    var lastPublish = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val current = store.get(id)
                        if (current?.status == DownloadStatus.PAUSED || current?.status == DownloadStatus.CANCELLED) {
                            return@withContext Result.success()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublish > 350) {
                            lastPublish = now
                            val knownTotal = if (total > 0) total else item.totalBytes
                            store.update(id) { it.copy(downloadedBytes = downloaded, totalBytes = knownTotal) }
                            val pct = if (knownTotal > 0) ((downloaded * 100) / knownTotal).toInt().coerceIn(0, 100) else 0
                            setForeground(foreground(item.title, pct, knownTotal <= 0))
                        }
                    }
                }
            }
            conn.disconnect()

            val finalSize = part.length()
            val publicUri = publish(part, item.fileName, item.mimeType)
            part.delete()
            store.update(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = if (it.totalBytes > 0) it.totalBytes else finalSize,
                    publicUri = publicUri,
                    error = null
                )
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            val current = store.get(id)
            if (current?.status == DownloadStatus.PAUSED || current?.status == DownloadStatus.CANCELLED) {
                Result.success()
            } else {
                throw cancelled
            }
        } catch (t: Throwable) {
            val current = store.get(id)
            if (current?.status == DownloadStatus.PAUSED || current?.status == DownloadStatus.CANCELLED) {
                Result.success()
            } else {
                store.update(id) { it.copy(status = DownloadStatus.FAILED, error = t.message ?: "Download failed") }
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }
    }

    private fun open(url: String, offset: Long): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "Veya/0.1 Android")
        setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
    }

    private fun parseTotal(conn: HttpURLConnection, offset: Long): Long {
        val rangeTotal = conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
        if (rangeTotal != null) return rangeTotal
        val len = conn.contentLengthLong
        return if (len > 0) len + if (conn.responseCode == HttpURLConnection.HTTP_PARTIAL) offset else 0 else -1
    }

    private fun publish(part: File, fileName: String, mime: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(part, fileName, mime)
        } else {
            publishLegacy(part, fileName)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun publishScoped(part: File, fileName: String, mime: String): String {
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Veya")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Could not create output file")
        try {
            resolver.openOutputStream(uri, "w")!!.use { out ->
                part.inputStream().buffered().use { input -> input.copyTo(out, 256 * 1024) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun publishLegacy(part: File, fileName: String): String {
        val root = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("External download storage is unavailable")
        val dir = File(root, "Veya").apply { mkdirs() }
        val output = uniqueFile(dir, fileName)
        part.inputStream().buffered().use { input ->
            output.outputStream().buffered().use { out -> input.copyTo(out, 256 * 1024) }
        }
        return FileProvider.getUriForFile(
            applicationContext,
            BuildConfig.APPLICATION_ID + ".files",
            output
        ).toString()
    }

    private fun uniqueFile(dir: File, requestedName: String): File {
        val first = File(dir, requestedName)
        if (!first.exists()) return first
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = File(dir, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun foreground(title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Veya download progress"
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Downloading…" else "$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id.hashCode(), notification)
    }

    companion object {
        const val KEY_ID = "download_id"
        private const val CHANNEL_ID = "veya_downloads"
    }
}
