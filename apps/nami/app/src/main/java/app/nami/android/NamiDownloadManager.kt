package app.nami.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.nami.data.local.DownloadDirectoryLayout
import app.nami.data.local.NamiDatabase
import app.nami.data.local.StoredDownload
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NamiAnimeSource
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
import kotlinx.coroutines.runBlocking
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class NamiDownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    WAITING_FOR_NETWORK,
    DOWNLOADED,
    ERROR,
}

enum class NamiPauseReason {
    USER,
    GLOBAL,
}

data class NamiDownloadStatus(
    val sourceId: String,
    val sourceAnimeId: String,
    val sourceEpisodeId: String,
    val extensionName: String,
    val animeTitle: String,
    val episodeTitle: String,
    val animeSourceState: String? = null,
    val episodeSourceState: String? = null,
    val relativePath: String,
    val displayName: String? = null,
    val contentUri: String? = null,
    val mimeType: String? = null,
    val state: NamiDownloadState,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val tempPath: String? = null,
    val hlsCompletedParts: Int = 0,
    val pauseReason: NamiPauseReason? = null,
    val retryCount: Int = 0,
    val mediaKind: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
)

class NamiDownloadManager(
    private val context: Context,
    private val database: NamiDatabase,
    private val sourceRegistry: NamiSourceRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadGate = PerSourceDownloadGate(MAX_PARALLEL_DOWNLOADS_PER_SOURCE)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableStatuses = MutableStateFlow<Map<String, NamiDownloadStatus>>(emptyMap())
    private val mutableGlobalPaused = MutableStateFlow(
        preferences.getBoolean(KEY_GLOBAL_PAUSED, false),
    )

    val statuses: StateFlow<Map<String, NamiDownloadStatus>> = mutableStatuses.asStateFlow()
    val globalPaused: StateFlow<Boolean> = mutableGlobalPaused.asStateFlow()

    init {
        // The service and UI need the persisted queue immediately after process recreation.
        // Load the small download table before either can make scheduling decisions.
        runBlocking(Dispatchers.IO) {
            reloadFromDatabase()
        }
    }

    fun key(sourceId: String, sourceEpisodeId: String): String =
        sourceId + "\u0000" + sourceEpisodeId

    fun enqueue(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
    ) {
        val extensionName = source.metadata.extensionName
            ?.takeIf { it.isNotBlank() }
            ?: source.metadata.name
        val season = anime.metadata["Season"]?.takeIf { it.isNotBlank() }
        val relativeDirectory = DownloadDirectoryLayout.relativeDirectory(
            extensionName = extensionName,
            title = anime.title,
            season = season,
        )
        enqueueInternal(
            source = source,
            anime = anime,
            episode = episode,
            relativeDirectory = relativeDirectory,
        )
    }

    fun enqueueAll(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episodes: List<AnimeEpisode>,
    ) {
        episodes.forEach { episode ->
            val existing = mutableStatuses.value[
                key(source.metadata.id, episode.ref.sourceEpisodeId)
            ]
            if (DownloadBatchPolicy.shouldEnqueue(existing?.state)) {
                enqueue(source, anime, episode)
            }
        }
    }

    private fun enqueueInternal(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        relativeDirectory: String,
    ) {
        val extensionName = source.metadata.extensionName
            ?.takeIf { it.isNotBlank() }
            ?: source.metadata.name
        val k = key(source.metadata.id, episode.ref.sourceEpisodeId)
        if (mutableStatuses.value[k]?.state in setOf(
                NamiDownloadState.QUEUED,
                NamiDownloadState.DOWNLOADING,
                NamiDownloadState.PAUSED,
                NamiDownloadState.WAITING_FOR_NETWORK,
                NamiDownloadState.DOWNLOADED,
            )
        ) {
            return
        }

        val queued = NamiDownloadStatus(
            sourceId = source.metadata.id,
            sourceAnimeId = episode.ref.sourceAnimeId,
            sourceEpisodeId = episode.ref.sourceEpisodeId,
            extensionName = extensionName,
            animeTitle = anime.title,
            episodeTitle = episode.title,
            animeSourceState = anime.sourceState,
            episodeSourceState = episode.sourceState,
            relativePath = relativeDirectory,
            tempPath = tempFileForKey(k).absolutePath,
            state = if (mutableGlobalPaused.value) {
                NamiDownloadState.PAUSED
            } else {
                NamiDownloadState.QUEUED
            },
            pauseReason = if (mutableGlobalPaused.value) NamiPauseReason.GLOBAL else null,
        )
        setAndPersist(queued)

        if (!mutableGlobalPaused.value) {
            ensureServiceRunning()
            schedule(queued, source, anime, episode)
        }
    }

    fun startBackgroundEngine() {
        ensureServiceRunning()
    }

    internal fun kickScheduler() {
        if (mutableGlobalPaused.value) return
        scope.launch {
            val sources = runCatching { sourceRegistry.installedSources() }
                .getOrDefault(emptyList())
                .associateBy { it.metadata.id }

            mutableStatuses.value.values
                .filter { it.state == NamiDownloadState.QUEUED }
                .forEach { status ->
                    val source = sources[status.sourceId]
                    if (source == null) {
                        setAndPersist(
                            status.copy(
                                state = NamiDownloadState.ERROR,
                                errorMessage = "The source for this download is not installed.",
                            ),
                        )
                        return@forEach
                    }
                    val request = requestFromStatus(status) ?: run {
                        setAndPersist(
                            status.copy(
                                state = NamiDownloadState.ERROR,
                                errorMessage = "This download record cannot be resumed.",
                            ),
                        )
                        return@forEach
                    }
                    schedule(status, source, request.anime, request.episode)
                }
        }
    }

    private fun schedule(
        status: NamiDownloadStatus,
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
    ) {
        if (mutableGlobalPaused.value || status.state != NamiDownloadState.QUEUED) return

        val k = key(status.sourceId, status.sourceEpisodeId)
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            downloadGate.withPermit(status.sourceId) {
                val current = mutableStatuses.value[k] ?: return@withPermit
                if (mutableGlobalPaused.value || current.state != NamiDownloadState.QUEUED) {
                    return@withPermit
                }
                runDownload(source, anime, episode, current)
            }
        }

        val previous = activeJobs.putIfAbsent(k, newJob)
        if (previous == null) {
            newJob.invokeOnCompletion {
                activeJobs.remove(k, newJob)

                // Pause/Resume can race with coroutine cancellation. If Resume changed the
                // persisted item back to QUEUED before the old transfer finished unwinding,
                // the immediate kick may have seen this still-active job and skipped it.
                // Re-arm that exact item once the old job has actually left the map.
                val latest = mutableStatuses.value[k]
                if (
                    latest?.state == NamiDownloadState.QUEUED &&
                    !mutableGlobalPaused.value
                ) {
                    schedule(latest, source, anime, episode)
                }
            }
            newJob.start()
        } else {
            newJob.cancel()
        }
    }


    fun openDownloaded(context: Context, status: NamiDownloadStatus) {
        val uri = status.contentUri?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, status.mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }

    fun pause(status: NamiDownloadStatus) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        val latest = mutableStatuses.value[k] ?: return
        if (latest.state !in setOf(
                NamiDownloadState.QUEUED,
                NamiDownloadState.DOWNLOADING,
                NamiDownloadState.WAITING_FOR_NETWORK,
            )
        ) {
            return
        }

        setAndPersist(
            latest.copy(
                state = NamiDownloadState.PAUSED,
                pauseReason = NamiPauseReason.USER,
                errorMessage = null,
            ),
        )
        activeJobs[k]?.cancel(CancellationException("Paused by user"))
    }

    fun resume(status: NamiDownloadStatus) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        val latest = mutableStatuses.value[k] ?: return
        if (latest.state != NamiDownloadState.PAUSED) return

        if (mutableGlobalPaused.value) {
            // A user-paused item stays user-paused. Resume All must never override that
            // explicit per-item decision merely because Resume was tapped during a global pause.
            return
        }

        val queued = latest.copy(
            state = NamiDownloadState.QUEUED,
            pauseReason = null,
            errorMessage = null,
        )
        setAndPersist(queued)
        ensureServiceRunning()
        kickScheduler()
    }

    fun pauseAll() {
        if (mutableGlobalPaused.value) return

        preferences.edit().putBoolean(KEY_GLOBAL_PAUSED, true).apply()
        mutableGlobalPaused.value = true

        val snapshot = mutableStatuses.value.values.toList()
        snapshot.forEach { status ->
            if (status.state in setOf(
                    NamiDownloadState.QUEUED,
                    NamiDownloadState.DOWNLOADING,
                    NamiDownloadState.WAITING_FOR_NETWORK,
                )
            ) {
                setAndPersist(
                    status.copy(
                        state = NamiDownloadState.PAUSED,
                        pauseReason = NamiPauseReason.GLOBAL,
                        errorMessage = null,
                    ),
                )
            }
        }
        activeJobs.values.forEach { it.cancel(CancellationException("Downloads globally paused")) }
    }

    fun resumeAll() {
        preferences.edit().putBoolean(KEY_GLOBAL_PAUSED, false).apply()
        mutableGlobalPaused.value = false

        mutableStatuses.value.values.toList().forEach { status ->
            if (
                status.state == NamiDownloadState.PAUSED &&
                status.pauseReason == NamiPauseReason.GLOBAL
            ) {
                setAndPersist(
                    status.copy(
                        state = NamiDownloadState.QUEUED,
                        pauseReason = null,
                        errorMessage = null,
                    ),
                )
            }
        }
        ensureServiceRunning()
        kickScheduler()
    }

    fun cancel(status: NamiDownloadStatus) {
        if (status.state !in setOf(
                NamiDownloadState.QUEUED,
                NamiDownloadState.DOWNLOADING,
                NamiDownloadState.PAUSED,
                NamiDownloadState.WAITING_FOR_NETWORK,
            )
        ) {
            return
        }
        cancelAndCleanup(status, "Cancelled by user")
    }

    fun remove(status: NamiDownloadStatus) {
        cancelAndCleanup(status, "Removed by user")
    }

    private fun cancelAndCleanup(
        status: NamiDownloadStatus,
        reason: String,
    ) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        val job = activeJobs[k]
        job?.cancel(CancellationException(reason))

        scope.launch {
            job?.join()
            val latest = mutableStatuses.value[k] ?: status
            deleteFinalTarget(latest)
            deletePartial(latest)
            database.deleteDownloadRecord(status.sourceId, status.sourceEpisodeId)
            mutableStatuses.value = mutableStatuses.value - k
            if (job != null) {
                activeJobs.remove(k, job)
            }
        }
    }

    fun retry(status: NamiDownloadStatus) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        val latest = mutableStatuses.value[k] ?: status
        if (latest.state != NamiDownloadState.ERROR) return

        val next = latest.copy(
            state = if (mutableGlobalPaused.value) {
                NamiDownloadState.PAUSED
            } else {
                NamiDownloadState.QUEUED
            },
            pauseReason = if (mutableGlobalPaused.value) NamiPauseReason.GLOBAL else null,
            errorMessage = null,
            retryCount = 0,
        )
        setAndPersist(next)

        if (!mutableGlobalPaused.value) {
            ensureServiceRunning()
            kickScheduler()
        }
    }

    private suspend fun runDownload(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        queued: NamiDownloadStatus,
    ) {
        val k = key(queued.sourceId, queued.sourceEpisodeId)
        val current = mutableStatuses.value[k] ?: return
        if (current.state != NamiDownloadState.QUEUED || mutableGlobalPaused.value) return

        setAndPersist(
            current.copy(
                state = NamiDownloadState.DOWNLOADING,
                pauseReason = null,
                errorMessage = null,
            ),
        )

        var recoveryAttempt = current.retryCount.coerceAtLeast(0)

        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                val latest = mutableStatuses.value[k] ?: return
                if (latest.state !in setOf(
                        NamiDownloadState.DOWNLOADING,
                        NamiDownloadState.WAITING_FOR_NETWORK,
                    )
                ) {
                    return
                }

                if (latest.state == NamiDownloadState.WAITING_FOR_NETWORK) {
                    setAndPersist(
                        latest.copy(
                            state = NamiDownloadState.DOWNLOADING,
                            errorMessage = null,
                        ),
                    )
                }

                val media = source.resolve(episode.ref, episode.sourceState)
                    .firstOrNull { it.url.isNotBlank() }
                    ?: error("This source did not return a downloadable video.")

                downloadResolvedMedia(
                    media = media,
                    anime = anime,
                    episode = episode,
                    initial = mutableStatuses.value[k] ?: latest,
                )
                return
            } catch (cancelled: CancellationException) {
                return
            } catch (failure: Throwable) {
                val latest = mutableStatuses.value[k] ?: return
                if (latest.state == NamiDownloadState.PAUSED) return

                if (!isRecoverableFailure(failure)) {
                    markHardError(latest, failure)
                    return
                }

                if (!isNetworkAvailable()) {
                    setAndPersist(
                        latest.copy(
                            state = NamiDownloadState.WAITING_FOR_NETWORK,
                            errorMessage = "Waiting for network",
                            retryCount = recoveryAttempt,
                        ),
                    )
                    waitForNetwork()
                    continue
                }

                recoveryAttempt += 1
                if (recoveryAttempt > MAX_RECOVERY_ATTEMPTS) {
                    markHardError(latest, failure)
                    return
                }

                val backoffMillis = RETRY_BACKOFF_MILLIS[
                    (recoveryAttempt - 1).coerceAtMost(RETRY_BACKOFF_MILLIS.lastIndex)
                ]
                setAndPersist(
                    latest.copy(
                        state = NamiDownloadState.WAITING_FOR_NETWORK,
                        errorMessage = "Retrying in " + (backoffMillis / 1000) + "s",
                        retryCount = recoveryAttempt,
                    ),
                )
                delay(backoffMillis)
            }
        }
    }

    private fun markHardError(
        status: NamiDownloadStatus,
        failure: Throwable,
    ) {
        setAndPersist(
            status.copy(
                state = NamiDownloadState.ERROR,
                pauseReason = null,
                errorMessage = failure.message ?: failure.javaClass.simpleName,
            ),
        )
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

    private fun isRecoverableFailure(failure: Throwable): Boolean {
        var current: Throwable? = failure
        while (current != null) {
            if (current is DownloadHttpException) {
                return current.code == 408 ||
                    current.code == 425 ||
                    current.code == 429 ||
                    current.code in 500..599
            }
            if (current is IOException) return true
            current = current.cause
        }

        val message = failure.message.orEmpty().lowercase()
        return "timeout" in message ||
            "temporar" in message ||
            "connection reset" in message ||
            Regex("""http\s+5\d\d""").containsMatchIn(message)
    }

    private suspend fun downloadResolvedMedia(
        media: ResolvedMedia,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        initial: NamiDownloadStatus,
    ) {
        val knownHls = initial.mediaKind == MEDIA_KIND_HLS ||
            DownloadMediaNaming.isHls(media.url, media.mimeType)

        if (knownHls) {
            val connection = openConnection(media.url, media.headers)
            try {
                val playlist = connection.inputStream.bufferedReader().use { it.readText() }
                downloadHls(
                    initialPlaylistUrl = connection.url.toString(),
                    initialPlaylist = playlist,
                    headers = media.headers,
                    anime = anime,
                    episode = episode,
                    initial = initial,
                )
            } finally {
                connection.disconnect()
            }
            return
        }

        val existingFile = partialFile(initial)
        val existingBytes = existingFile.takeIf { it.exists() }?.length() ?: 0L

        if (
            initial.mediaKind == MEDIA_KIND_DIRECT &&
            initial.totalBytes != null &&
            initial.totalBytes > 0L &&
            existingBytes == initial.totalBytes &&
            !initial.displayName.isNullOrBlank() &&
            !initial.mimeType.isNullOrBlank()
        ) {
            finalizePartial(
                initial.copy(
                    bytesDownloaded = existingBytes,
                    progress = 99,
                    errorMessage = null,
                ),
            )
            return
        }

        val requestHeaders = buildMap {
            putAll(media.headers)
            if (existingBytes > 0L) {
                put("Range", "bytes=$existingBytes-")
                val validator = initial.etag ?: initial.lastModified
                if (!validator.isNullOrBlank()) {
                    put("If-Range", validator)
                }
            }
        }

        var connection = openConnection(
            url = media.url,
            headers = requestHeaders,
            requireSuccess = false,
        )
        try {
            val initialResponseMime = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()

            if (
                initial.mediaKind == null &&
                DownloadMediaNaming.isHls(
                    connection.url.toString(),
                    media.mimeType ?: initialResponseMime,
                )
            ) {
                if (existingBytes > 0L) {
                    resetPartial(initial)
                }
                val playlist = connection.inputStream.bufferedReader().use { it.readText() }
                downloadHls(
                    initialPlaylistUrl = connection.url.toString(),
                    initialPlaylist = playlist,
                    headers = media.headers,
                    anime = anime,
                    episode = episode,
                    initial = initial.copy(
                        bytesDownloaded = 0L,
                        hlsCompletedParts = 0,
                        mediaKind = MEDIA_KIND_HLS,
                    ),
                )
                return
            }

            var appendFrom = existingBytes
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val responseStart = contentRangeStart(connection)
                    if (responseStart != appendFrom) {
                        connection.disconnect()
                        resetPartial(initial)
                        appendFrom = 0L
                        connection = openConnection(
                            media.url,
                            media.headers,
                            requireSuccess = false,
                        )
                        if (connection.responseCode !in 200..299) {
                            throw DownloadHttpException(
                                connection.responseCode,
                                "Download restart failed with HTTP " +
                                    connection.responseCode + ".",
                            )
                        }
                        if (
                            connection.responseCode == HttpURLConnection.HTTP_PARTIAL &&
                            contentRangeStart(connection) != 0L
                        ) {
                            throw IOException(
                                "Server returned an invalid Content-Range while restarting.",
                            )
                        }
                    }
                }
                in 200..299 -> {
                    if (appendFrom > 0L) {
                        // Server ignored Range. Start from byte zero rather than appending a
                        // second full response to the existing partial.
                        resetPartial(initial)
                        appendFrom = 0L
                    }
                }
                416 -> {
                    connection.disconnect()
                    resetPartial(initial)
                    appendFrom = 0L
                    connection = openConnection(media.url, media.headers)
                }
                else -> throw DownloadHttpException(
                    connection.responseCode,
                    "Download request failed with HTTP " + connection.responseCode + ".",
                )
            }

            val responseMime = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
            val responseType = media.mimeType ?: responseMime ?: "application/octet-stream"
            val extension = DownloadMediaNaming.extensionFor(
                mimeType = responseType,
                url = connection.url.toString(),
                contentDisposition = connection.getHeaderField("Content-Disposition"),
            )
            val mime = DownloadMediaNaming.normalizedMimeType(
                mimeType = responseType,
                extension = extension,
            )
            val displayName = initial.displayName
                ?: DownloadMediaNaming.episodeFileName(episode, extension)
            val total = totalBytesFromConnection(connection, appendFrom)
            val temp = partialFile(initial)
            temp.parentFile?.mkdirs()

            val transferStatus = initial.copy(
                displayName = displayName,
                contentUri = null,
                mimeType = mime,
                state = NamiDownloadState.DOWNLOADING,
                mediaKind = MEDIA_KIND_DIRECT,
                bytesDownloaded = appendFrom,
                totalBytes = total,
                tempPath = temp.absolutePath,
                etag = connection.getHeaderField("ETag") ?: initial.etag,
                lastModified = connection.getHeaderField("Last-Modified") ?: initial.lastModified,
                errorMessage = null,
            )
            setAndPersist(transferStatus)

            connection.inputStream.use { input ->
                FileOutputStream(temp, appendFrom > 0L).use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        totalBytes = total,
                        startBytes = appendFrom,
                        status = transferStatus,
                    )
                }
            }

            if (total != null && temp.length() < total) {
                throw EOFException(
                    "Download ended early at " + temp.length() + " of " + total + " bytes.",
                )
            }

            val completed = (mutableStatuses.value[
                key(initial.sourceId, initial.sourceEpisodeId)
            ] ?: transferStatus).copy(
                bytesDownloaded = temp.length(),
                totalBytes = total ?: temp.length(),
                progress = 99,
            )
            setAndPersist(completed)
            finalizePartial(completed)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadHls(
        initialPlaylistUrl: String,
        initialPlaylist: String,
        headers: Map<String, String>,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        initial: NamiDownloadStatus,
    ) {
        var playlistUrl = initialPlaylistUrl
        var playlist = initialPlaylist

        val variant = HlsPlaylistPlanner.selectMasterVariant(playlistUrl, playlist)
        if (variant != null) {
            playlistUrl = variant
            val connection = openConnection(playlistUrl, headers)
            try {
                playlist = connection.inputStream.bufferedReader().use { it.readText() }
                playlistUrl = connection.url.toString()
            } finally {
                connection.disconnect()
            }
        }

        val plan = HlsPlaylistPlanner.mediaPlan(playlistUrl, playlist)
        val displayName = initial.displayName
            ?: DownloadMediaNaming.episodeFileName(episode, plan.extension)
        val temp = partialFile(initial)
        temp.parentFile?.mkdirs()

        var completedParts = if (
            initial.mediaKind == MEDIA_KIND_HLS &&
            temp.exists()
        ) {
            initial.hlsCompletedParts.coerceIn(0, plan.allPartUrls.size)
        } else {
            0
        }

        if (
            initial.mediaKind != null &&
            initial.mediaKind != MEDIA_KIND_HLS
        ) {
            resetPartial(initial)
            completedParts = 0
        }

        if (!temp.exists() && completedParts > 0) {
            completedParts = 0
        }

        if (initial.mediaKind == MEDIA_KIND_HLS && temp.exists()) {
            // bytesDownloaded is persisted only after a complete HLS part. Android can kill
            // the process in the middle of the next segment before our exception handler has
            // a chance to roll the file back. Truncate to the last durable segment boundary
            // before resuming so a full segment is never appended after a stale half-segment.
            val durableBoundary = initial.bytesDownloaded
            if (
                durableBoundary < 0L ||
                durableBoundary > temp.length() ||
                (durableBoundary == 0L && completedParts > 0)
            ) {
                RandomAccessFile(temp, "rw").use { it.setLength(0L) }
                completedParts = 0
            } else if (temp.length() != durableBoundary) {
                RandomAccessFile(temp, "rw").use { it.setLength(durableBoundary) }
            }
        }

        if (completedParts == 0 && temp.exists() && initial.hlsCompletedParts == 0) {
            RandomAccessFile(temp, "rw").use { it.setLength(0L) }
        }

        var transferStatus = initial.copy(
            displayName = displayName,
            contentUri = null,
            mimeType = plan.mimeType,
            state = NamiDownloadState.DOWNLOADING,
            mediaKind = MEDIA_KIND_HLS,
            tempPath = temp.absolutePath,
            hlsCompletedParts = completedParts,
            bytesDownloaded = temp.takeIf { it.exists() }?.length() ?: 0L,
            totalBytes = null,
            errorMessage = null,
        )
        setAndPersist(transferStatus)

        val allParts = plan.allPartUrls
        for (index in completedParts until allParts.size) {
            currentCoroutineContext().ensureActive()
            val segmentStart = temp.takeIf { it.exists() }?.length() ?: 0L
            val connection = openConnection(allParts[index], headers)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temp, true).use { output ->
                        copyCancellable(input, output)
                    }
                }
            } catch (failure: Throwable) {
                if (temp.exists()) {
                    RandomAccessFile(temp, "rw").use { it.setLength(segmentStart) }
                }
                throw failure
            } finally {
                connection.disconnect()
            }

            val partCount = index + 1
            val progress = ((partCount.toDouble() / allParts.size) * 100)
                .roundToInt()
                .coerceIn(0, 99)
            transferStatus = transferStatus.copy(
                hlsCompletedParts = partCount,
                bytesDownloaded = temp.length(),
                progress = progress,
            )
            setAndPersist(transferStatus)
        }

        finalizePartial(
            transferStatus.copy(
                bytesDownloaded = temp.length(),
                progress = 99,
            ),
        )
    }

    private suspend fun finalizePartial(status: NamiDownloadStatus) {
        val temp = partialFile(status)
        require(temp.exists() && temp.length() > 0L) {
            "The partial download file is missing."
        }
        val displayName = status.displayName ?: error("The download file name is missing.")
        val mimeType = status.mimeType ?: "application/octet-stream"
        val target = createFinalTarget(
            relativeDirectory = status.relativePath,
            displayName = displayName,
            mimeType = mimeType,
        )

        try {
            temp.inputStream().use { input ->
                target.output.use { output ->
                    copyCancellable(input, output)
                }
            }
            target.finish()
        } catch (failure: Throwable) {
            target.abort()
            throw failure
        }

        temp.delete()
        setAndPersist(
            status.copy(
                contentUri = target.publicUri,
                state = NamiDownloadState.DOWNLOADED,
                progress = 100,
                errorMessage = null,
                pauseReason = null,
                retryCount = 0,
                tempPath = null,
            ),
        )
    }

    private fun contentRangeStart(connection: HttpURLConnection): Long? =
        connection.getHeaderField("Content-Range")
            ?.let { value ->
                Regex("""(?i)^bytes\s+(\d+)-\d+/[^\s]+$""")
                    .find(value.trim())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
            }

    private fun totalBytesFromConnection(
        connection: HttpURLConnection,
        startBytes: Long,
    ): Long? {
        val contentRange = connection.getHeaderField("Content-Range")
        val rangeTotal = contentRange
            ?.substringAfterLast('/', "")
            ?.takeIf { it != "*" }
            ?.toLongOrNull()
        if (rangeTotal != null && rangeTotal > 0L) return rangeTotal

        val length = connection.contentLengthLong.takeIf { it > 0L } ?: return null
        return if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            startBytes + length
        } else {
            length
        }
    }

    private fun openConnection(
        url: String,
        headers: Map<String, String>,
        requireSuccess: Boolean = true,
    ): HttpURLConnection {
        if (GoogleDriveDownloadPlanner.isDriveDownload(url)) {
            return openGoogleDriveConnection(url, headers, requireSuccess)
        }
        return openRawConnection(url, headers, followRedirects = true, requireSuccess = requireSuccess)
    }

    private fun openGoogleDriveConnection(
        sourceUrl: String,
        headers: Map<String, String>,
        requireSuccess: Boolean,
    ): HttpURLConnection {
        var currentUrl = GoogleDriveDownloadPlanner.directDownloadUrl(sourceUrl)

        repeat(8) {
            val connection = openRawConnection(
                currentUrl,
                headers,
                followRedirects = false,
                requireSuccess = false,
            )
            saveResponseCookies(currentUrl, connection)

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: run {
                        connection.disconnect()
                        error("Google Drive redirect did not include a Location header.")
                    }
                connection.disconnect()
                currentUrl = runCatching { URI(currentUrl).resolve(location).toString() }
                    .getOrDefault(location)
                return@repeat
            }

            if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
                if (!requireSuccess) return connection
                connection.disconnect()
                throw DownloadHttpException(
                    code,
                    "Google Drive download failed with HTTP $code.",
                )
            }

            val contentDisposition = connection.getHeaderField("Content-Disposition")
            val contentType = connection.contentType.orEmpty()
            if (
                code == HttpURLConnection.HTTP_PARTIAL ||
                !contentDisposition.isNullOrBlank() ||
                !contentType.contains("text/html", ignoreCase = true)
            ) {
                return connection
            }

            val confirmationPage = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                while (out.length < 512 * 1024) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    out.append(buffer, 0, read)
                }
                out.toString()
            }
            connection.disconnect()

            currentUrl = GoogleDriveDownloadPlanner.confirmationUrl(
                html = confirmationPage,
                baseUrl = currentUrl,
            ) ?: error(
                "Google Drive returned an HTML confirmation page without a usable download link.",
            )
        }

        error("Google Drive exceeded the redirect/confirmation limit.")
    }

    private fun openRawConnection(
        url: String,
        headers: Map<String, String>,
        followRedirects: Boolean,
        requireSuccess: Boolean = true,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        headers.forEach { (name, value) ->
            if (value.isNotBlank()) connection.setRequestProperty(name, value)
        }

        if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Cookie", it)
            }
        }

        connection.connect()
        if (
            requireSuccess &&
            connection.responseCode !in 200..299
        ) {
            val code = connection.responseCode
            connection.disconnect()
            throw DownloadHttpException(
                code,
                "Download request failed with HTTP $code.",
            )
        }
        return connection
    }

    private fun saveResponseCookies(
        url: String,
        connection: HttpURLConnection,
    ) {
        val manager = CookieManager.getInstance()
        connection.headerFields
            .filterKeys { key -> key?.equals("Set-Cookie", ignoreCase = true) == true }
            .values
            .flatten()
            .forEach { cookie -> manager.setCookie(url, cookie) }
        manager.flush()
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: OutputStream,
        totalBytes: Long?,
        startBytes: Long,
        status: NamiDownloadStatus,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = startBytes
        var lastProgress = status.progress
        var lastPersistedBytes = startBytes

        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read

            val progress = totalBytes
                ?.takeIf { it > 0L }
                ?.let { ((copied.toDouble() / it) * 100).roundToInt().coerceIn(0, 99) }
                ?: status.progress

            val shouldPersist = progress >= lastProgress + 2 ||
                copied - lastPersistedBytes >= PROGRESS_PERSIST_BYTES
            if (shouldPersist) {
                lastProgress = progress
                lastPersistedBytes = copied
                publishTransferProgress(status, copied, totalBytes, progress)
            }
        }
        output.flush()
        publishTransferProgress(status, copied, totalBytes, 99)
    }

    private fun publishTransferProgress(
        base: NamiDownloadStatus,
        bytesDownloaded: Long,
        totalBytes: Long?,
        progress: Int,
    ) {
        val k = key(base.sourceId, base.sourceEpisodeId)
        val latest = mutableStatuses.value[k] ?: return
        if (latest.state != NamiDownloadState.DOWNLOADING) return

        setAndPersist(
            latest.copy(
                bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
                totalBytes = totalBytes ?: latest.totalBytes,
                progress = progress.coerceIn(0, 99),
            ),
        )
    }

    private suspend fun copyCancellable(
        input: java.io.InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) return
            output.write(buffer, 0, read)
        }
    }

    private fun createFinalTarget(
        relativeDirectory: String,
        displayName: String,
        mimeType: String,
    ): DownloadTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Nami/" + relativeDirectory,
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: error("Android could not create the destination file.")
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: run {
                    context.contentResolver.delete(uri, null, null)
                    error("Android could not open the destination file.")
                }
            DownloadTarget(
                output = output,
                publicUri = uri.toString(),
                finish = {
                    val complete = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, complete, null, null)
                },
                abort = {
                    runCatching { output.close() }
                    context.contentResolver.delete(uri, null, null)
                },
            )
        } else {
            val file = legacyPublicFile(relativeDirectory, displayName)
            file.parentFile?.mkdirs()
            val output = FileOutputStream(file, false)
            val publicUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".downloads",
                file,
            ).toString()
            DownloadTarget(
                output = output,
                publicUri = publicUri,
                finish = {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(mimeType),
                        null,
                    )
                },
                abort = {
                    runCatching { output.close() }
                    file.delete()
                },
            )
        }
    }

    private fun deleteFinalTarget(status: NamiDownloadStatus) {
        val uriString = status.contentUri ?: return

        runCatching {
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                Uri.parse(uriString).authority == context.packageName + ".downloads"
            ) {
                val displayName = status.displayName ?: return@runCatching
                val file = legacyPublicFile(status.relativePath, displayName)
                if (file.delete()) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(status.mimeType ?: "video/*"),
                        null,
                    )
                }
            } else {
                context.contentResolver.delete(Uri.parse(uriString), null, null)
            }
        }
    }

    private fun partialFile(status: NamiDownloadStatus): File {
        val stored = status.tempPath?.takeIf { it.isNotBlank() }
        return if (stored != null) {
            File(stored)
        } else {
            tempFileForKey(key(status.sourceId, status.sourceEpisodeId))
        }
    }

    private fun tempFileForKey(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "downloads/partial"), "$digest.part")
    }

    private fun resetPartial(status: NamiDownloadStatus) {
        val file = partialFile(status)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(0L) }
    }

    private fun deletePartial(status: NamiDownloadStatus) {
        runCatching { partialFile(status).delete() }
    }

    @Suppress("DEPRECATION")
    private fun legacyPublicFile(
        relativeDirectory: String,
        displayName: String,
    ): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(File(root, "Nami/" + relativeDirectory), displayName)
    }

    private fun setAndPersist(status: NamiDownloadStatus) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        mutableStatuses.value = mutableStatuses.value + (k to status)
        persist(status)
    }

    private fun persist(status: NamiDownloadStatus) {
        database.upsertDownload(
            sourceId = status.sourceId,
            extensionName = status.extensionName,
            sourceAnimeId = status.sourceAnimeId,
            sourceEpisodeId = status.sourceEpisodeId,
            relativePath = status.relativePath,
            animeTitle = status.animeTitle,
            episodeTitle = status.episodeTitle,
            animeSourceState = status.animeSourceState,
            episodeSourceState = status.episodeSourceState,
            displayName = status.displayName,
            contentUri = status.contentUri,
            mimeType = status.mimeType,
            state = status.state.name,
            progress = status.progress,
            errorMessage = status.errorMessage,
            bytesDownloaded = status.bytesDownloaded,
            totalBytes = status.totalBytes,
            tempPath = status.tempPath,
            hlsCompletedParts = status.hlsCompletedParts,
            pauseReason = status.pauseReason?.name,
            retryCount = status.retryCount,
            mediaKind = status.mediaKind,
            etag = status.etag,
            lastModified = status.lastModified,
        )
    }

    private fun reloadFromDatabase() {
        mutableStatuses.value = database.getDownloads().associate { stored ->
            val persisted = stored.toStatus()
            val recovered = recoverPersistedDownload(stored, persisted)
            key(stored.sourceId, stored.sourceEpisodeId) to recovered
        }
    }

    private fun recoverPersistedDownload(
        stored: StoredDownload,
        status: NamiDownloadStatus,
    ): NamiDownloadStatus {
        var recovered = status

        // Records made by the old direct-to-MediaStore downloader can contain a pending
        // content URI but no durable private partial. Do not mistake that URI for resumable
        // data after upgrading to the new engine.
        if (
            status.state != NamiDownloadState.DOWNLOADED &&
            status.tempPath.isNullOrBlank() &&
            !status.contentUri.isNullOrBlank()
        ) {
            deleteFinalTarget(status)
            recovered = recovered.copy(contentUri = null)
        }

        recovered = when (recovered.state) {
            NamiDownloadState.DOWNLOADING,
            NamiDownloadState.WAITING_FOR_NETWORK,
            NamiDownloadState.QUEUED,
            -> if (mutableGlobalPaused.value) {
                recovered.copy(
                    state = NamiDownloadState.PAUSED,
                    pauseReason = NamiPauseReason.GLOBAL,
                    errorMessage = null,
                )
            } else {
                recovered.copy(
                    state = NamiDownloadState.QUEUED,
                    pauseReason = null,
                    errorMessage = null,
                )
            }

            NamiDownloadState.PAUSED,
            NamiDownloadState.DOWNLOADED,
            NamiDownloadState.ERROR,
            -> recovered
        }

        if (recovered != status) {
            persist(recovered)
        }
        return recovered
    }

    private fun StoredDownload.toStatus(): NamiDownloadStatus = NamiDownloadStatus(
        sourceId = sourceId,
        sourceAnimeId = sourceAnimeId,
        sourceEpisodeId = sourceEpisodeId,
        extensionName = extensionName,
        animeTitle = animeTitle
            ?: relativePath.substringAfter('/').substringBefore('/'),
        episodeTitle = episodeTitle
            ?: displayName?.substringBeforeLast('.')
            ?: sourceEpisodeId,
        animeSourceState = animeSourceState,
        episodeSourceState = episodeSourceState,
        relativePath = relativePath,
        displayName = displayName,
        contentUri = contentUri,
        mimeType = mimeType,
        state = runCatching { NamiDownloadState.valueOf(state) }
            .getOrDefault(NamiDownloadState.ERROR),
        progress = progress,
        errorMessage = errorMessage,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        tempPath = tempPath,
        hlsCompletedParts = hlsCompletedParts,
        pauseReason = pauseReason?.let {
            runCatching { NamiPauseReason.valueOf(it) }.getOrNull()
        },
        retryCount = retryCount,
        mediaKind = mediaKind,
        etag = etag,
        lastModified = lastModified,
    )

    private fun requestFromStatus(status: NamiDownloadStatus): RetryDownloadRequest? {
        if (
            status.sourceId.isBlank() ||
            status.sourceAnimeId.isBlank() ||
            status.sourceEpisodeId.isBlank()
        ) {
            return null
        }
        return RetryDownloadRequest(
            anime = AnimeDetails(
                ref = AnimeRef(status.sourceId, status.sourceAnimeId),
                title = status.animeTitle,
                sourceState = status.animeSourceState,
            ),
            episode = AnimeEpisode(
                ref = EpisodeRef(
                    sourceId = status.sourceId,
                    sourceAnimeId = status.sourceAnimeId,
                    sourceEpisodeId = status.sourceEpisodeId,
                ),
                title = status.episodeTitle,
                sourceState = status.episodeSourceState,
            ),
            relativeDirectory = status.relativePath,
        )
    }

    private fun ensureServiceRunning() {
        // Only the process-wide application manager is owned by the foreground service.
        // Instrumentation and isolated managers use their own database/registry and must not
        // start a service whose singleton manager observes a different queue.
        val appManagerOwnsThisQueue = runCatching {
            (context.applicationContext as? NamiApplication)?.downloadManager === this
        }.getOrDefault(false)
        if (!appManagerOwnsThisQueue) return

        val intent = Intent(context, NamiDownloadService::class.java)
            .setAction(NamiDownloadService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        internal const val MAX_PARALLEL_DOWNLOADS_PER_SOURCE = 2
        @Deprecated("Use MAX_PARALLEL_DOWNLOADS_PER_SOURCE")
        internal const val MAX_PARALLEL_DOWNLOADS = MAX_PARALLEL_DOWNLOADS_PER_SOURCE

        private const val PREFERENCES_NAME = "nami_download_engine"
        private const val KEY_GLOBAL_PAUSED = "global_paused"
        private const val MEDIA_KIND_DIRECT = "DIRECT"
        private const val MEDIA_KIND_HLS = "HLS"
        private const val MAX_RECOVERY_ATTEMPTS = 6
        private const val NETWORK_POLL_MILLIS = 2_000L
        private const val PROGRESS_PERSIST_BYTES = 1L * 1024L * 1024L
        private val RETRY_BACKOFF_MILLIS = longArrayOf(
            2_000L,
            5_000L,
            10_000L,
            30_000L,
        )
    }

    private data class DownloadTarget(
        val output: OutputStream,
        val publicUri: String,
        val finish: () -> Unit,
        val abort: () -> Unit,
    )

    private class DownloadHttpException(
        val code: Int,
        message: String,
    ) : IOException(message)
}
