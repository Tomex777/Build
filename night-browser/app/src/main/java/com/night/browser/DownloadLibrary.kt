package com.night.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.WebResponse
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val reason: Int = 0,
    /** App-owned Gecko response filename, never a user-controlled path. Empty for system records. */
    val localFile: String = "",
    /** False for expiring/signed URL and authenticated requests; never replay stripped tokens. */
    val retryable: Boolean = false
) {
    val complete get() = status == DownloadManager.STATUS_SUCCESSFUL
}

internal object DownloadFileNames {
    /** A URL without expiring query credentials may be retried using DownloadManager. */
    fun canSafelyRetry(url: String, hasSessionHeaders: Boolean = false): Boolean = runCatching {
        val uri = java.net.URI(url)
        !hasSessionHeaders && LibraryRules.validWebUrl(url) &&
            uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    fun safe(name: String?, mime: String? = null): String {
        val raw = name.orEmpty().substringBefore('?').substringBefore('#')
            .substringAfterLast('/').substringAfterLast('\\')
        val cleaned = raw.replace(Regex("[\\x00-\\x1f\\x7f<>:\"|?*/\\\\]"), "_")
            // Reject invisible directional controls that can spoof extensions in UI.
            .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
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

class DownloadLibrary private constructor(private val context: Context) {
    companion object {
        @Volatile private var singleton: DownloadLibrary? = null
        fun shared(context: Context): DownloadLibrary = singleton ?: synchronized(this) {
            singleton ?: DownloadLibrary(context.applicationContext).also { singleton = it }
        }
    }

    private val mainThread = Handler(Looper.getMainLooper())
    private val localIds = AtomicLong(-System.currentTimeMillis())
    private val cancellations = ConcurrentHashMap<Long, AtomicBoolean>()
    private val localDirectory get() = File(context.filesDir, "browser-downloads")
    private fun findLocalFile(record: LibraryDownload): File {
        require(record.localFile.isNotBlank()) { "Missing local file." }
        // Only internally generated UUID-based names are valid, including after JSON reload.
        require(record.localFile.length in 38..137 &&
            record.localFile[36] == '_' &&
            UUID.fromString(record.localFile.substring(0, 36)).toString() ==
                record.localFile.substring(0, 36) &&
            !record.localFile.contains('/') && !record.localFile.contains('\\')) {
            "Unsafe file name."
        }
        return File(localDirectory, record.localFile)
    }

    private fun updateRow(id: Long, transform: (LibraryDownload) -> LibraryDownload) {
        check(Looper.myLooper() == Looper.getMainLooper())
        items = items.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("night_downloads_v1", Context.MODE_PRIVATE)
    var items by mutableStateOf(load())
        private set

    init {
        // Re-serialize legacy DownloadManager rows at startup, permanently removing
        // old URL query strings/userinfo from our SharedPreferences (not just in RAM).
        // OS DownloadManager retains the original network request for its own lifecycle.
        persist()
        // A process-killed Gecko stream cannot be resumed; remove orphaned temp fragments.
        Thread({
            localDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }
                ?.forEach { runCatching { it.delete() } }
        }, "night-download-cleanup").start()
    }

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
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null) {
            "Invalid download address."
        }
        val canRetry = DownloadFileNames.canSafelyRetry(
            url, !cookieHeader.isNullOrBlank() || !userAgent.isNullOrBlank()
        )
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
        // DownloadManager holds the original transfer address. Library keeps only a
        // credential-free, query-free display address: never persist signed tokens.
        val record = LibraryDownload(
            id, GeckoDownloadNames.redactUrl(url), cleaned, mimeType,
            System.currentTimeMillis(), retryable = canRetry
        )
        items = listOf(record) + items
        persist()
        record
    }

    /**
     * Warn only for a finished download we still own or can open.
     * Never consult this normal-profile history from private browsing.
     */
    fun existingCompletedDuplicate(url: String, filename: String): LibraryDownload? =
        DownloadDuplicatePolicy.findCompleted(url, filename, items) { row ->
            if (row.localFile.isNotBlank()) {
                runCatching { findLocalFile(row).isFile }.getOrDefault(false)
            } else {
                runCatching {
                    val fileUri = manager.getUriForDownloadedFile(row.id)
                    fileUri != null && context.contentResolver.openFileDescriptor(fileUri, "r")
                        ?.use { true } == true
                }
                    .getOrDefault(false)
            }
        }

    /** Query the Android provider off the UI thread, then update observable state on main. */
    suspend fun refresh() {
        val systemRecords = items.filter { it.localFile.isBlank() }
        if (systemRecords.isEmpty()) return
        val checked = withContext(Dispatchers.IO) {
            systemRecords.associate { original ->
                original.id to runCatching {
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
        }
        // The set may change during I/O (new download, retry, cancel); never resurrect old IDs.
        val updated = items.map { row -> checked[row.id] ?: row }
        if (updated != items) {
            items = updated
            persist()
        }
    }

    fun retry(record: LibraryDownload): Result<LibraryDownload> {
        if (record.localFile.isNotBlank() || !record.retryable) return Result.failure(
            IOException("Return to the original site to get a fresh download link."))
        if (record.status != DownloadManager.STATUS_FAILED) return Result.failure(
            IOException("Only failed downloads can be retried."))
        val fresh = enqueue(record.url, record.fileName, record.mimeType)
        if (fresh.isSuccess) remove(record)
        return fresh
    }

    fun remove(record: LibraryDownload): Result<Unit> = runCatching {
        if (record.localFile.isNotBlank()) cancellations[record.id]?.set(true)
        if (record.localFile.isBlank()) manager.remove(record.id)
        else {
            val file = findLocalFile(record)
            if (file.exists() && !file.delete()) throw IOException("Could not delete the file.")
        }
        items = items.filterNot { it.id == record.id }
        persist()
    }

    fun open(record: LibraryDownload): Result<Unit> = runCatching {
        require(record.complete) { "Download has not completed." }
        val uri = if (record.localFile.isNotBlank()) {
            val file = findLocalFile(record)
            require(file.isFile) { "Downloaded file is missing." }
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        } else manager.getUriForDownloadedFile(record.id)
            ?: throw IOException("Downloaded file is missing.")
        // Downloaded PDFs must remain usable offline even when a phone has
        // no external PDF viewer installed. Share the existing non-exported
        // native PdfRenderer Activity used for Time Machine; other formats
        // still open through Android's normal MIME handlers.
        val isPdf = record.mimeType.substringBefore(';').equals("application/pdf", true) ||
            record.fileName.endsWith(".pdf", ignoreCase = true)
        val intent = (if (isPdf) Intent(context, SavedPdfActivity::class.java)
            else Intent(Intent.ACTION_VIEW)).setDataAndType(
            uri, if (isPdf) "application/pdf"
                else record.mimeType.ifBlank { "application/octet-stream" }
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
        val resolver = context.contentResolver
        val input = if (record.localFile.isNotBlank()) {
            findLocalFile(record).inputStream()
        } else {
            val source = manager.getUriForDownloadedFile(record.id)
                ?: throw IOException("Downloaded file is missing.")
            resolver.openInputStream(source) ?: throw IOException("Cannot read download.")
        }
        input.use { src ->
            val output = resolver.openOutputStream(target, "w")
                ?: throw IOException("Cannot write selected location.")
            output.use { dst -> src.copyTo(dst) }
        }
    }

    /**
     * Save Gecko's ORIGINAL HTTP response stream, never a second HTTP fetch. This is essential
     * for POST downloads, redirects, authenticated sessions and single-use signed links.
     * Called by Agent 4's onExternalResponse UI-thread callback.
     *
     * The event body is always closed, even for errors, so Gecko's connection is released.
     * The caller should show an explicit user confirmation before downloading from private tabs.
     */
    fun acceptGeckoResponse(response: WebResponse, onEvent: (String) -> Unit = {}) {
        val body = response.body
        val url = response.uri
        val mime = response.headers.entries.firstOrNull {
            it.key.equals("content-type", true)
        }?.value?.substringBefore(';')?.trim().orEmpty().ifBlank { "application/octet-stream" }
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", true)
        }?.value
        val providedName = GeckoDownloadNames.fromDisposition(disposition)
        val fromUrl = Uri.parse(url).lastPathSegment
        val filename = DownloadFileNames.safe(providedName ?: fromUrl, mime)
        val length = response.headers.entries.firstOrNull {
            it.key.equals("content-length", true)
        }?.value?.toLongOrNull()?.takeIf { it > 0 } ?: -1L

        if (body == null || response.statusCode !in 200..299) {
            runCatching { body?.close() }
            onEvent("The site did not provide a downloadable file (HTTP " +
                response.statusCode + ").")
            return
        }

        val id = localIds.decrementAndGet()
        val storedName = UUID.randomUUID().toString() + "_" + filename
        val cancelled = AtomicBoolean(false)
        cancellations[id] = cancelled
        val record = LibraryDownload(id, GeckoDownloadNames.redactUrl(url),
            filename, mime, System.currentTimeMillis(),
            DownloadManager.STATUS_RUNNING, 0, length, localFile = storedName)
        items = listOf(record) + items
        persist()
        onEvent("Downloading " + filename)

        Thread({
            val directory = localDirectory
            val temp = File(directory, storedName + ".part")
            val destination = File(directory, storedName)
            try {
                directory.mkdirs()
                var count = 0L
                var reported = 0L
                var lastUpdate = 0L
                body.use { stream ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get()) throw InterruptedIOException("Download cancelled")
                            val n = stream.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            count += n
                            val now = System.currentTimeMillis()
                            if (count - reported >= 512 * 1024 && now - lastUpdate > 450) {
                                val progress = count
                                mainThread.post { updateRow(id) { it.copy(bytes = progress) } }
                                reported = count
                                lastUpdate = now
                            }
                        }
                    }
                }
                if (cancelled.get()) throw InterruptedIOException("Download cancelled")
                if (count == 0L && length > 0) throw IOException("Empty downloaded file.")
                if (length > 0 && count != length) throw IOException("Incomplete download.")
                if (!temp.renameTo(destination)) throw IOException("Could not finalize download.")
                if (cancelled.get()) throw InterruptedIOException("Download cancelled")
                mainThread.post {
                    updateRow(id) {
                        it.copy(bytes = count, totalBytes = if (length > 0) length else count,
                            status = DownloadManager.STATUS_SUCCESSFUL)
                    }
                    onEvent(filename + " downloaded")
                    cancellations.remove(id)
                }
            } catch (error: Exception) {
                // Close the response stream even if opening destination fails.
                runCatching { body.close() }
                temp.delete()
                if (cancelled.get()) destination.delete()
                mainThread.post {
                    updateRow(id) { it.copy(status = DownloadManager.STATUS_FAILED) }
                    if (!cancelled.get())
                        onEvent("Download failed: " + (error.message ?: "storage or connection error"))
                    cancellations.remove(id)
                }
            }
        }, "night-gecko-download").start()
    }

    /**
     * Private-tab downloads are explicitly exported with SAF. Their Gecko stream
     * goes directly to the user-selected document, never to DownloadManager or
     * the persistent Library download index. A cancelled picker closes the body.
     * Agent 1 must pass BrowserTab.isPrivate to WebDownloadConsent.offer after A3.
     */
    fun exportPrivateGeckoResponse(
        response: WebResponse, destination: Uri, onEvent: (String) -> Unit = {}
    ) {
        val body = response.body
        if (body == null || response.statusCode !in 200..299) {
            runCatching { body?.close() }
            onEvent("The website did not provide a downloadable file.")
            return
        }
        val length = response.headers.entries.firstOrNull {
            it.key.equals("content-length", true)
        }?.value?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
        Thread({
            try {
                var count = 0L
                body.use { input ->
                    val sink = context.contentResolver.openOutputStream(destination, "w")
                        ?: throw IOException("Cannot write to the selected location.")
                    sink.use { output ->
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            output.write(chunk, 0, n)
                            count += n
                        }
                    }
                }
                if (length > 0 && count != length) throw IOException("Incomplete transfer.")
                mainThread.post { onEvent("Private file saved") }
            } catch (error: Exception) {
                runCatching { body.close() }
                // ACTION_CREATE_DOCUMENT created this URI just for this transfer;
                // remove the incomplete document when a provider permits deletion.
                runCatching { context.contentResolver.delete(destination, null, null) }
                mainThread.post {
                    onEvent("Couldn't save private file: " +
                        (error.message ?: "storage or connection error"))
                }
            }
        }, "night-private-download").start()
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
            else -> if (row.reason in 400..599) "HTTP " + row.reason else if (row.localFile.isNotBlank()) "Download interrupted" else "Download failed"
        }
        else -> "Unknown"
    }

    private fun Cursor.long(column: String): Long =
        getLong(getColumnIndexOrThrow(column))

    private fun load(): List<LibraryDownload> = runCatching {
        val array = JSONArray(prefs.getString("rows", "[]"))
        (0 until array.length()).map { i ->
            val x = array.getJSONObject(i)
            // Migrate v1 rows in memory without resurfacing query/access tokens.
            // Old records have unknown cookie/user-agent inputs; disable blind retry.
            val rawUrl = x.getString("url")
            val safeUrl = GeckoDownloadNames.redactUrl(rawUrl)
            val eligible = x.has("retryable") && x.optBoolean("retryable") &&
                DownloadFileNames.canSafelyRetry(rawUrl)
            LibraryDownload(x.getLong("id"), safeUrl, x.getString("file"),
                x.optString("mime", "application/octet-stream"), x.optLong("created"),
                x.optInt("status", DownloadManager.STATUS_PENDING),
                x.optLong("bytes"), x.optLong("total", -1), x.optInt("reason"),
                x.optString("localFile"), retryable = eligible).let { row ->
                if (row.localFile.isNotBlank() && row.status == DownloadManager.STATUS_RUNNING)
                    row.copy(status = DownloadManager.STATUS_FAILED) else row
            }
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("url", it.url).put("file", it.fileName)
                .put("mime", it.mimeType).put("created", it.createdAt)
                .put("status", it.status).put("bytes", it.bytes)
                .put("total", it.totalBytes).put("reason", it.reason)
                .put("localFile", it.localFile).put("retryable", it.retryable))
        }
        prefs.edit().putString("rows", array.toString()).apply()
    }
}
