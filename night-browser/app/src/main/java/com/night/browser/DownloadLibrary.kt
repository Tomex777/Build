package com.night.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale

/**
 * System DownloadManager supplies real progress, state and persistence after process death.
 * Agent 4: pass Gecko-triggered URL, fileName, MIME and optional session cookie/user agent.
 * Never save authorization headers. Private browser sessions MUST NOT silently enqueue public
 * DownloadManager records, because the system provider is not private/incognito.
 */
data class LibraryDownload(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val createdAt: Long,
    val status: Int = DownloadManager.STATUS_PENDING,
    val bytes: Long = 0,
    val totalBytes: Long = -1,
    val reason: Int = 0
) {
    val complete get() = status == DownloadManager.STATUS_SUCCESSFUL
}

internal object DownloadFileNames {
    fun safe(name: String?, mime: String? = null): String {
        val raw = name.orEmpty().substringBefore('?').substringBefore('#')
            .substringAfterLast('/').substringAfterLast('\\')
        val cleaned = raw.replace(Regex("[\\x00-\\x1f\\x7f<>:\"|?*/\\\\]"), "_")
            .trim().trim('.').take(100)
        if (cleaned.isNotBlank()) return cleaned
        val extension = when (mime?.lowercase(Locale.ROOT)) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            "image/jpeg" -> ".jpg"
            "image/webp" -> ".webp"
            "text/plain" -> ".txt"
            else -> ""
        }
        return "download$extension"
    }
}

class DownloadLibrary(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("night_downloads_v1", Context.MODE_PRIVATE)
    var items by mutableStateOf(load())
        private set

    /**
     * Files stay in app-specific external Downloads; users can export using SAF.
     * This does not request broad storage permissions.
     */
    fun enqueue(
        url: String,
        fileName: String? = null,
        mimeType: String = "application/octet-stream",
        userAgent: String? = null,
        cookieHeader: String? = null
    ): Result<LibraryDownload> = runCatching {
        require(LibraryRules.validWebUrl(url)) { "Only HTTP(S) downloads are supported." }
        val parsed = Uri.parse(url)
        require(!parsed.host.isNullOrBlank()) { "Invalid download address." }
        val suggested = fileName ?: parsed.lastPathSegment?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()
        }
        val cleaned = DownloadFileNames.safe(suggested, mimeType)
        val destination = System.currentTimeMillis().toString() + "-" + cleaned
        val request = DownloadManager.Request(parsed)
            .setTitle(cleaned)
            .setDescription(parsed.host)
            .setMimeType(mimeType.ifBlank { "application/octet-stream" })
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, destination)
        userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
        cookieHeader?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
        val id = manager.enqueue(request)
        val record = LibraryDownload(id, url, cleaned, mimeType, System.currentTimeMillis())
        items = listOf(record) + items
        persist()
        record
    }

    fun refresh() {
        val updated = items.map { original ->
            runCatching {
                manager.query(DownloadManager.Query().setFilterById(original.id))?.use { cursor ->
                    if (cursor.moveToFirst()) original.copy(
                        status = cursor.long(DownloadManager.COLUMN_STATUS).toInt(),
                        bytes = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        totalBytes = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                        reason = cursor.long(DownloadManager.COLUMN_REASON).toInt()
                    ) else original.copy(
                        status = DownloadManager.STATUS_FAILED,
                        reason = DownloadManager.ERROR_FILE_ERROR
                    )
                } ?: original
            }.getOrDefault(original)
        }
        if (updated != items) {
            items = updated
            persist()
        }
    }

    fun retry(record: LibraryDownload): Result<LibraryDownload> {
        val fresh = enqueue(record.url, record.fileName, record.mimeType)
        if (fresh.isSuccess) remove(record)
        return fresh
    }

    fun remove(record: LibraryDownload): Result<Unit> = runCatching {
        manager.remove(record.id)
        items = items.filterNot { it.id == record.id }
        persist()
    }

    fun open(record: LibraryDownload): Result<Unit> = runCatching {
        require(record.complete) { "Download has not completed." }
        val uri = manager.getUriForDownloadedFile(record.id)
            ?: throw IOException("Downloaded file is missing.")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
            uri, record.mimeType.ifBlank { "application/octet-stream" }
        ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (notFound: ActivityNotFoundException) {
            throw IOException("No application can open this file.", notFound)
        }
    }

    /** Run from Dispatchers.IO after ACTION_CREATE_DOCUMENT returns a destination. */
    fun export(record: LibraryDownload, target: Uri): Result<Unit> = runCatching {
        require(record.complete) { "Download has not completed." }
        val source = manager.getUriForDownloadedFile(record.id)
            ?: throw IOException("Downloaded file is missing.")
        val resolver = context.contentResolver
        val input = resolver.openInputStream(source) ?: throw IOException("Cannot read download.")
        input.use { src ->
            val output = resolver.openOutputStream(target, "w")
                ?: throw IOException("Cannot write selected location.")
            output.use { dst -> src.copyTo(dst) }
        }
    }

    fun statusText(row: LibraryDownload): String = when (row.status) {
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_PENDING -> "Waiting"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PAUSED -> when (row.reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "System retry pending"
            else -> "Paused by Android"
        }
        DownloadManager.STATUS_FAILED -> when (row.reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage"
            DownloadManager.ERROR_CANNOT_RESUME -> "Server cannot resume"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server HTTP error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Connection error"
            DownloadManager.ERROR_FILE_ERROR -> "File/storage error"
            else -> if (row.reason in 400..599) "HTTP " + row.reason else "Download failed"
        }
        else -> "Unknown"
    }

    private fun Cursor.long(column: String): Long =
        getLong(getColumnIndexOrThrow(column))

    private fun load(): List<LibraryDownload> = runCatching {
        val array = JSONArray(prefs.getString("rows", "[]"))
        (0 until array.length()).map { i ->
            val x = array.getJSONObject(i)
            LibraryDownload(x.getLong("id"), x.getString("url"), x.getString("file"),
                x.optString("mime", "application/octet-stream"), x.optLong("created"),
                x.optInt("status", DownloadManager.STATUS_PENDING),
                x.optLong("bytes"), x.optLong("total", -1), x.optInt("reason"))
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("url", it.url).put("file", it.fileName)
                .put("mime", it.mimeType).put("created", it.createdAt)
                .put("status", it.status).put("bytes", it.bytes)
                .put("total", it.totalBytes).put("reason", it.reason))
        }
        prefs.edit().putString("rows", array.toString()).apply()
    }
}
