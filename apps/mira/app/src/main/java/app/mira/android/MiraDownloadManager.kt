package app.mira.android

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.mira.domain.ResolvedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class MiraDownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    WAITING_FOR_NETWORK,
    DOWNLOADED,
    ERROR,
}

data class MiraDownloadStatus(
    val id: String,
    val sourceId: String,
    val contentId: String,
    val episodeId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val state: MiraDownloadState,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val tempPath: String? = null,
    val finalPath: String? = null,
    val contentUri: String? = null,
    val mediaKind: String? = null,
    val hlsCompletedParts: Int = 0,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
)

class MiraDownloadManager(
    private val context: Context,
    private val serviceOwned: Boolean = true,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val gates = ConcurrentHashMap<String, Semaphore>()
    private val mutableStatuses = MutableStateFlow(loadStatuses())
    private val mutableGlobalPaused = MutableStateFlow(
        preferences.getBoolean(KEY_GLOBAL_PAUSED, false),
    )

    val statuses: StateFlow<Map<String, MiraDownloadStatus>> = mutableStatuses.asStateFlow()
    val globalPaused: StateFlow<Boolean> = mutableGlobalPaused.asStateFlow()

    init {
        recoverPersistedQueue()
    }

    fun startBackgroundEngine() {
        if (mutableStatuses.value.values.any {
                it.state == MiraDownloadState.QUEUED ||
                    it.state == MiraDownloadState.DOWNLOADING ||
                    it.state == MiraDownloadState.WAITING_FOR_NETWORK
            }
        ) {
            ensureServiceRunning()
            kickScheduler()
        }
    }

    fun key(sourceId: String, contentId: String, episodeId: String?): String =
        sourceId + "\u0000" + contentId + "\u0000" + episodeId.orEmpty()

    fun enqueue(
        sourceId: String,
        contentId: String,
        episodeId: String? = null,
        title: String,
        subtitle: String? = null,
        media: ResolvedMedia,
    ) {
        val id = key(sourceId, contentId, episodeId)
        val existing = mutableStatuses.value[id]
        if (existing?.state in setOf(
                MiraDownloadState.QUEUED,
                MiraDownloadState.DOWNLOADING,
                MiraDownloadState.PAUSED,
                MiraDownloadState.WAITING_FOR_NETWORK,
                MiraDownloadState.DOWNLOADED,
            )
        ) {
            return
        }

        val partial = partialFile(id)
        val queued = MiraDownloadStatus(
            id = id,
            sourceId = sourceId,
            contentId = contentId,
            episodeId = episodeId,
            title = title,
            subtitle = subtitle,
            url = media.url,
            mimeType = media.mimeType,
            headers = media.headers,
            state = if (mutableGlobalPaused.value) {
                MiraDownloadState.PAUSED
            } else {
                MiraDownloadState.QUEUED
            },
            tempPath = partial.absolutePath,
        )
        setAndPersist(queued)
        if (!mutableGlobalPaused.value) {
            ensureServiceRunning()
            schedule(queued)
        }
    }

    fun pause(status: MiraDownloadStatus) {
        val latest = mutableStatuses.value[status.id] ?: return
        if (latest.state !in setOf(
                MiraDownloadState.QUEUED,
                MiraDownloadState.DOWNLOADING,
                MiraDownloadState.WAITING_FOR_NETWORK,
            )
        ) {
            return
        }
        setAndPersist(latest.copy(state = MiraDownloadState.PAUSED, errorMessage = null))
        activeJobs[status.id]?.cancel(CancellationException("Paused by user"))
    }

    fun resume(status: MiraDownloadStatus) {
        if (mutableGlobalPaused.value) return
        val latest = mutableStatuses.value[status.id] ?: return
        if (latest.state != MiraDownloadState.PAUSED) return
        val queued = latest.copy(
            state = MiraDownloadState.QUEUED,
            errorMessage = null,
            retryCount = 0,
        )
        setAndPersist(queued)
        ensureServiceRunning()
        schedule(queued)
    }

    fun pauseAll() {
        if (mutableGlobalPaused.value) return
        mutableGlobalPaused.value = true
        preferences.edit().putBoolean(KEY_GLOBAL_PAUSED, true).apply()
        mutableStatuses.value.values.toList().forEach { status ->
            if (status.state in setOf(
                    MiraDownloadState.QUEUED,
                    MiraDownloadState.DOWNLOADING,
                    MiraDownloadState.WAITING_FOR_NETWORK,
                )
            ) {
                setAndPersist(status.copy(state = MiraDownloadState.PAUSED))
            }
        }
        activeJobs.values.forEach { it.cancel(CancellationException("Globally paused")) }
    }

    fun resumeAll() {
        mutableGlobalPaused.value = false
        preferences.edit().putBoolean(KEY_GLOBAL_PAUSED, false).apply()
        mutableStatuses.value.values.toList().forEach { status ->
            if (status.state == MiraDownloadState.PAUSED) {
                setAndPersist(status.copy(state = MiraDownloadState.QUEUED, errorMessage = null))
            }
        }
        ensureServiceRunning()
        kickScheduler()
    }

    fun retry(status: MiraDownloadStatus) {
        val latest = mutableStatuses.value[status.id] ?: return
        if (latest.state != MiraDownloadState.ERROR) return
        val queued = latest.copy(
            state = if (mutableGlobalPaused.value) MiraDownloadState.PAUSED else MiraDownloadState.QUEUED,
            errorMessage = null,
            retryCount = 0,
        )
        setAndPersist(queued)
        if (!mutableGlobalPaused.value) {
            ensureServiceRunning()
            schedule(queued)
        }
    }

    fun cancel(status: MiraDownloadStatus) = remove(status)

    fun remove(status: MiraDownloadStatus) {
        val job = activeJobs[status.id]
        job?.cancel(CancellationException("Removed"))
        scope.launch {
            job?.join()
            val latest = mutableStatuses.value[status.id] ?: status
            latest.tempPath?.let { runCatching { File(it).delete() } }
            latest.finalPath?.let { runCatching { File(it).delete() } }
            mutableStatuses.value = mutableStatuses.value - status.id
            persistAll()
        }
    }

    fun completedMedia(status: MiraDownloadStatus): ResolvedMedia? {
        if (status.state != MiraDownloadState.DOWNLOADED) return null
        val uri = status.contentUri ?: return null
        return ResolvedMedia(
            url = uri,
            mimeType = status.mimeType ?: "video/*",
            quality = "Offline",
            hosterName = "Mira download",
        )
    }

    internal fun kickScheduler() {
        if (mutableGlobalPaused.value) return
        mutableStatuses.value.values
            .filter { it.state == MiraDownloadState.QUEUED }
            .forEach(::schedule)
    }

    private fun schedule(status: MiraDownloadStatus) {
        if (mutableGlobalPaused.value || status.state != MiraDownloadState.QUEUED) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            gateFor(status.sourceId).withPermit {
                val current = mutableStatuses.value[status.id] ?: return@withPermit
                if (current.state != MiraDownloadState.QUEUED || mutableGlobalPaused.value) {
                    return@withPermit
                }
                runTransfer(current)
            }
        }
        val previous = activeJobs.putIfAbsent(status.id, job)
        if (previous == null) {
            job.invokeOnCompletion {
                activeJobs.remove(status.id, job)
                mutableStatuses.value[status.id]?.let(::reconcilePausedPartial)
                val latest = mutableStatuses.value[status.id]
                if (
                    latest?.state == MiraDownloadState.QUEUED &&
                    !mutableGlobalPaused.value
                ) {
                    schedule(latest)
                }
            }
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun gateFor(sourceId: String): Semaphore =
        gates.computeIfAbsent(sourceId) { Semaphore(MAX_PARALLEL_PER_SOURCE) }

    private suspend fun runTransfer(initial: MiraDownloadStatus) {
        var attempt = initial.retryCount
        setAndPersist(initial.copy(state = MiraDownloadState.DOWNLOADING, errorMessage = null))

        while (true) {
            currentCoroutineContext().ensureActive()
            val latest = mutableStatuses.value[initial.id] ?: return
            if (latest.state == MiraDownloadState.PAUSED) return

            try {
                if (!isNetworkAvailable()) {
                    setAndPersist(
                        latest.copy(
                            state = MiraDownloadState.WAITING_FOR_NETWORK,
                            errorMessage = "Waiting for network",
                        ),
                    )
                    waitForNetwork()
                    setAndPersist(
                        (mutableStatuses.value[initial.id] ?: latest).copy(
                            state = MiraDownloadState.DOWNLOADING,
                            errorMessage = null,
                        ),
                    )
                }

                val now = mutableStatuses.value[initial.id] ?: return
                if (isHls(now.url, now.mimeType) || now.mediaKind == MEDIA_KIND_HLS) {
                    downloadHls(now)
                } else {
                    downloadDirect(now)
                }
                return
            } catch (cancelled: CancellationException) {
                return
            } catch (failure: Throwable) {
                val current = mutableStatuses.value[initial.id] ?: return
                if (current.state == MiraDownloadState.PAUSED) return
                if (!isRecoverable(failure)) {
                    setAndPersist(
                        current.copy(
                            state = MiraDownloadState.ERROR,
                            errorMessage = failure.message ?: failure.javaClass.simpleName,
                        ),
                    )
                    return
                }

                attempt += 1
                if (attempt > MAX_RETRY_ATTEMPTS) {
                    setAndPersist(
                        current.copy(
                            state = MiraDownloadState.ERROR,
                            errorMessage = failure.message ?: "Download failed",
                            retryCount = attempt,
                        ),
                    )
                    return
                }

                val backoff = RETRY_BACKOFF[
                    (attempt - 1).coerceAtMost(RETRY_BACKOFF.lastIndex)
                ]
                setAndPersist(
                    current.copy(
                        state = MiraDownloadState.WAITING_FOR_NETWORK,
                        errorMessage = if (isNetworkAvailable()) {
                            "Retrying in ${backoff / 1000}s"
                        } else {
                            "Waiting for network"
                        },
                        retryCount = attempt,
                    ),
                )
                if (!isNetworkAvailable()) waitForNetwork() else delay(backoff)
                setAndPersist(
                    (mutableStatuses.value[initial.id] ?: current).copy(
                        state = MiraDownloadState.DOWNLOADING,
                        errorMessage = null,
                    ),
                )
            }
        }
    }

    private suspend fun downloadDirect(initial: MiraDownloadStatus) {
        val temp = File(requireNotNull(initial.tempPath))
        temp.parentFile?.mkdirs()
        var existing = temp.takeIf { it.exists() }?.length() ?: 0L

        val headers = initial.headers.toMutableMap()
        if (existing > 0L) headers["Range"] = "bytes=$existing-"

        var connection = open(initial.url, headers, requireSuccess = false)
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val start = contentRangeStart(connection)
                    if (start != existing) {
                        connection.disconnect()
                        RandomAccessFile(temp, "rw").use { it.setLength(0L) }
                        existing = 0L
                        connection = open(initial.url, initial.headers, requireSuccess = true)
                    }
                }
                in 200..299 -> {
                    if (existing > 0L) {
                        RandomAccessFile(temp, "rw").use { it.setLength(0L) }
                        existing = 0L
                    }
                }
                416 -> {
                    connection.disconnect()
                    RandomAccessFile(temp, "rw").use { it.setLength(0L) }
                    existing = 0L
                    connection = open(initial.url, initial.headers, requireSuccess = true)
                }
                else -> throw DownloadHttpException(
                    connection.responseCode,
                    "Download request failed with HTTP ${connection.responseCode}",
                )
            }

            val responseMime = connection.contentType?.substringBefore(';')?.trim()
            val mime = initial.mimeType ?: responseMime ?: "video/mp4"
            val total = totalBytes(connection, existing)
            var copied = existing
            var lastPersisted = copied

            setAndPersist(
                initial.copy(
                    state = MiraDownloadState.DOWNLOADING,
                    mimeType = mime,
                    mediaKind = MEDIA_KIND_DIRECT,
                    bytesDownloaded = copied,
                    totalBytes = total,
                    errorMessage = null,
                ),
            )

            connection.inputStream.use { input ->
                FileOutputStream(temp, existing > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastPersisted >= PROGRESS_PERSIST_BYTES) {
                            lastPersisted = copied
                            publishProgress(initial.id, copied, total)
                        }
                    }
                    output.flush()
                }
            }

            if (total != null && temp.length() < total) {
                throw EOFException("Download ended early at ${temp.length()} of $total bytes")
            }
            publishProgress(initial.id, temp.length(), total ?: temp.length())
            finalizeDownload(mutableStatuses.value[initial.id] ?: initial)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadHls(initial: MiraDownloadStatus) {
        var playlistUrl = initial.url
        var playlist = getText(playlistUrl, initial.headers)
        val variant = selectBestVariant(playlistUrl, playlist)
        if (variant != null) {
            playlistUrl = variant
            playlist = getText(playlistUrl, initial.headers)
        }

        val segments = parseSegments(playlistUrl, playlist)
        if (segments.isEmpty()) throw IOException("HLS playlist did not contain media segments")

        val temp = File(requireNotNull(initial.tempPath))
        temp.parentFile?.mkdirs()
        if (!temp.exists()) temp.createNewFile()

        var completed = initial.hlsCompletedParts.coerceIn(0, segments.size)
        val durableBoundary = initial.bytesDownloaded.coerceAtLeast(0L)
        if (completed > 0 && temp.length() != durableBoundary) {
            RandomAccessFile(temp, "rw").use { it.setLength(durableBoundary.coerceAtMost(temp.length())) }
        } else if (completed == 0 && temp.length() > 0L) {
            RandomAccessFile(temp, "rw").use { it.setLength(0L) }
        }

        var current = initial.copy(
            state = MiraDownloadState.DOWNLOADING,
            mimeType = "video/mp2t",
            mediaKind = MEDIA_KIND_HLS,
            hlsCompletedParts = completed,
            bytesDownloaded = temp.length(),
            totalBytes = null,
            progress = ((completed.toDouble() / segments.size) * 100).roundToInt().coerceIn(0, 99),
            errorMessage = null,
        )
        setAndPersist(current)

        for (index in completed until segments.size) {
            currentCoroutineContext().ensureActive()
            val before = temp.length()
            val connection = open(segments[index], initial.headers, requireSuccess = true)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temp, true).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
            } catch (failure: Throwable) {
                RandomAccessFile(temp, "rw").use { it.setLength(before) }
                throw failure
            } finally {
                connection.disconnect()
            }

            completed = index + 1
            current = (mutableStatuses.value[initial.id] ?: current).copy(
                bytesDownloaded = temp.length(),
                hlsCompletedParts = completed,
                progress = ((completed.toDouble() / segments.size) * 100)
                    .roundToInt()
                    .coerceIn(0, 99),
            )
            setAndPersist(current)
        }

        finalizeDownload(current)
    }

    private fun finalizeDownload(status: MiraDownloadStatus) {
        val temp = File(requireNotNull(status.tempPath))
        if (!temp.exists() || temp.length() <= 0L) error("Download produced no file")
        val extension = extensionFor(status)
        val final = finalFile(status.id, extension)
        final.parentFile?.mkdirs()
        if (final.exists()) final.delete()
        if (!temp.renameTo(final)) {
            temp.copyTo(final, overwrite = true)
            temp.delete()
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".downloads",
            final,
        )
        setAndPersist(
            status.copy(
                state = MiraDownloadState.DOWNLOADED,
                progress = 100,
                bytesDownloaded = final.length(),
                totalBytes = status.totalBytes ?: final.length(),
                finalPath = final.absolutePath,
                contentUri = uri.toString(),
                errorMessage = null,
            ),
        )
    }

    private fun publishProgress(id: String, bytes: Long, total: Long?) {
        val latest = mutableStatuses.value[id] ?: return
        if (latest.state != MiraDownloadState.DOWNLOADING) return
        val progress = total?.takeIf { it > 0L }
            ?.let { ((bytes.toDouble() / it) * 100).roundToInt().coerceIn(0, 99) }
            ?: latest.progress
        setAndPersist(
            latest.copy(
                bytesDownloaded = bytes,
                totalBytes = total ?: latest.totalBytes,
                progress = progress,
            ),
        )
    }

    private fun reconcilePausedPartial(status: MiraDownloadStatus) {
        if (status.state != MiraDownloadState.PAUSED) return
        val path = status.tempPath ?: return
        val file = File(path)
        if (!file.exists()) return

        if (status.mediaKind == MEDIA_KIND_HLS) {
            val boundary = status.bytesDownloaded.coerceAtLeast(0L).coerceAtMost(file.length())
            if (file.length() != boundary) {
                RandomAccessFile(file, "rw").use { it.setLength(boundary) }
            }
            return
        }

        val durable = file.length()
        val progress = status.totalBytes?.takeIf { it > 0L }
            ?.let { ((durable.toDouble() / it) * 100).roundToInt().coerceIn(0, 99) }
            ?: status.progress
        if (durable != status.bytesDownloaded || progress != status.progress) {
            setAndPersist(status.copy(bytesDownloaded = durable, progress = progress))
        }
    }

    private fun recoverPersistedQueue() {
        val recovered = mutableStatuses.value.mapValues { (_, status) ->
            when (status.state) {
                MiraDownloadState.DOWNLOADED -> {
                    if (status.finalPath?.let(::File)?.exists() == true) {
                        status
                    } else {
                        status.copy(
                            state = MiraDownloadState.ERROR,
                            errorMessage = "Downloaded file is missing",
                            contentUri = null,
                        )
                    }
                }
                MiraDownloadState.PAUSED -> status
                MiraDownloadState.QUEUED,
                MiraDownloadState.DOWNLOADING,
                MiraDownloadState.WAITING_FOR_NETWORK,
                -> if (mutableGlobalPaused.value) {
                    status.copy(state = MiraDownloadState.PAUSED)
                } else {
                    status.copy(state = MiraDownloadState.QUEUED, errorMessage = null)
                }
                MiraDownloadState.ERROR -> status
            }
        }
        mutableStatuses.value = recovered
        persistAll()
    }

    private fun loadStatuses(): Map<String, MiraDownloadStatus> {
        val raw = preferences.getString(KEY_STATUSES, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    val status = json.toStatus()
                    put(status.id, status)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun setAndPersist(status: MiraDownloadStatus) {
        mutableStatuses.value = mutableStatuses.value + (status.id to status)
        persistAll()
    }

    private fun persistAll() {
        val array = JSONArray()
        mutableStatuses.value.values.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_STATUSES, array.toString()).apply()
    }

    private fun MiraDownloadStatus.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceId", sourceId)
        put("contentId", contentId)
        put("episodeId", episodeId)
        put("title", title)
        put("subtitle", subtitle)
        put("url", url)
        put("mimeType", mimeType)
        put("headers", JSONObject(headers))
        put("state", state.name)
        put("progress", progress)
        put("bytesDownloaded", bytesDownloaded)
        put("totalBytes", totalBytes)
        put("tempPath", tempPath)
        put("finalPath", finalPath)
        put("contentUri", contentUri)
        put("mediaKind", mediaKind)
        put("hlsCompletedParts", hlsCompletedParts)
        put("errorMessage", errorMessage)
        put("retryCount", retryCount)
    }

    private fun JSONObject.toStatus(): MiraDownloadStatus {
        val headersJson = optJSONObject("headers") ?: JSONObject()
        val headers = buildMap {
            headersJson.keys().forEach { key -> put(key, headersJson.optString(key)) }
        }
        return MiraDownloadStatus(
            id = getString("id"),
            sourceId = getString("sourceId"),
            contentId = getString("contentId"),
            episodeId = optNullableString("episodeId"),
            title = getString("title"),
            subtitle = optNullableString("subtitle"),
            url = getString("url"),
            mimeType = optNullableString("mimeType"),
            headers = headers,
            state = runCatching { MiraDownloadState.valueOf(getString("state")) }
                .getOrDefault(MiraDownloadState.ERROR),
            progress = optInt("progress", 0),
            bytesDownloaded = optLong("bytesDownloaded", 0L),
            totalBytes = if (isNull("totalBytes")) null else optLong("totalBytes"),
            tempPath = optNullableString("tempPath"),
            finalPath = optNullableString("finalPath"),
            contentUri = optNullableString("contentUri"),
            mediaKind = optNullableString("mediaKind"),
            hlsCompletedParts = optInt("hlsCompletedParts", 0),
            errorMessage = optNullableString("errorMessage"),
            retryCount = optInt("retryCount", 0),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun partialFile(id: String): File =
        File(File(context.filesDir, "downloads/partial"), "${digest(id)}.part")

    private fun finalFile(id: String, extension: String): File =
        File(File(context.filesDir, "downloads/completed"), "${digest(id)}.$extension")

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun extensionFor(status: MiraDownloadStatus): String {
        if (status.mediaKind == MEDIA_KIND_HLS) return "ts"
        return when {
            status.mimeType?.contains("matroska", true) == true -> "mkv"
            status.mimeType?.contains("webm", true) == true -> "webm"
            status.url.substringBefore('?').endsWith(".mkv", true) -> "mkv"
            status.url.substringBefore('?').endsWith(".webm", true) -> "webm"
            else -> "mp4"
        }
    }

    private fun isHls(url: String, mimeType: String?): Boolean =
        url.substringBefore('?').endsWith(".m3u8", true) ||
            mimeType?.contains("mpegurl", true) == true

    private suspend fun getText(
        url: String,
        headers: Map<String, String>,
    ): String {
        val connection = open(url, headers, requireSuccess = true)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun selectBestVariant(baseUrl: String, playlist: String): String? {
        val lines = playlist.lines().map { it.trim() }
        var bestBandwidth = -1L
        var bestUrl: String? = null
        for (index in 0 until lines.lastIndex) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF", true)) continue
            val bandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val candidate = lines.drop(index + 1).firstOrNull {
                it.isNotBlank() && !it.startsWith("#")
            } ?: continue
            if (bandwidth >= bestBandwidth) {
                bestBandwidth = bandwidth
                bestUrl = resolveUrl(baseUrl, candidate)
            }
        }
        return bestUrl
    }

    private fun parseSegments(baseUrl: String, playlist: String): List<String> =
        playlist.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolveUrl(baseUrl, it) }

    private fun resolveUrl(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)

    private fun open(
        url: String,
        headers: Map<String, String>,
        requireSuccess: Boolean,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        headers.forEach { (name, value) ->
            if (value.isNotBlank()) connection.setRequestProperty(name, value)
        }
        connection.connect()
        if (requireSuccess && connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw DownloadHttpException(code, "Download request failed with HTTP $code")
        }
        return connection
    }

    private fun contentRangeStart(connection: HttpURLConnection): Long? =
        connection.getHeaderField("Content-Range")
            ?.let {
                Regex("""(?i)^bytes\s+(\d+)-\d+/[^\s]+$""")
                    .find(it.trim())?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

    private fun totalBytes(connection: HttpURLConnection, start: Long): Long? {
        val rangeTotal = connection.getHeaderField("Content-Range")
            ?.substringAfterLast('/', "")
            ?.takeIf { it != "*" }
            ?.toLongOrNull()
        if (rangeTotal != null && rangeTotal > 0L) return rangeTotal
        val length = connection.contentLengthLong.takeIf { it > 0L } ?: return null
        return if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) start + length else length
    }

    private suspend fun waitForNetwork() {
        while (!isNetworkAvailable()) {
            currentCoroutineContext().ensureActive()
            delay(NETWORK_POLL_MILLIS)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isRecoverable(failure: Throwable): Boolean {
        var current: Throwable? = failure
        while (current != null) {
            if (current is DownloadHttpException) {
                return current.code == 408 || current.code == 429 || current.code in 500..599
            }
            if (current is IOException) return true
            current = current.cause
        }
        return false
    }

    private fun ensureServiceRunning() {
        if (!serviceOwned) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, MiraDownloadService::class.java)
                .setAction(MiraDownloadService.ACTION_START),
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "mira_download_engine"
        private const val KEY_STATUSES = "statuses"
        private const val KEY_GLOBAL_PAUSED = "global_paused"
        private const val MEDIA_KIND_DIRECT = "DIRECT"
        private const val MEDIA_KIND_HLS = "HLS"
        private const val MAX_PARALLEL_PER_SOURCE = 2
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val NETWORK_POLL_MILLIS = 2_000L
        private const val PROGRESS_PERSIST_BYTES = 512L * 1024L
        private val RETRY_BACKOFF = longArrayOf(2_000L, 5_000L, 10_000L, 30_000L)
    }

    private class DownloadHttpException(
        val code: Int,
        message: String,
    ) : IOException(message)
}
