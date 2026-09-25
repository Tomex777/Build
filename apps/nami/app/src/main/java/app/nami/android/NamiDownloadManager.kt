package app.nami.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import app.nami.data.local.DownloadDirectoryLayout
import app.nami.data.local.NamiDatabase
import app.nami.data.local.StoredDownload
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class NamiDownloadState {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
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
)

class NamiDownloadManager(
    private val context: Context,
    private val database: NamiDatabase,
    private val sourceRegistry: NamiSourceRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadPermits = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val mutableStatuses = MutableStateFlow<Map<String, NamiDownloadStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, NamiDownloadStatus>> = mutableStatuses.asStateFlow()

    init {
        scope.launch { reloadFromDatabase() }
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
            state = NamiDownloadState.QUEUED,
        )
        mutableStatuses.value = mutableStatuses.value + (k to queued)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            persist(
                status = queued,
                sourceAnimeId = episode.ref.sourceAnimeId,
            )
            downloadPermits.withPermit {
                runDownload(source, anime, episode, queued)
            }
        }
        activeJobs[k] = job
        job.invokeOnCompletion {
            activeJobs.remove(k, job)
        }
        job.start()
    }

    fun openDownloaded(context: Context, status: NamiDownloadStatus) {
        val uri = status.contentUri?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, status.mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }

    fun cancel(status: NamiDownloadStatus) {
        if (status.state != NamiDownloadState.QUEUED &&
            status.state != NamiDownloadState.DOWNLOADING
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
            // Do not race MediaStore/file deletion against a writer that still owns the
            // stream. Waiting for the cancelled job lets runDownload unwind its use/finally
            // blocks, close the output, abort the pending target and disconnect HTTP first.
            job?.join()

            // Progress updates can replace the status object after the caller captured it.
            // Use the latest value so cleanup always sees the final target URI/name.
            val latest = mutableStatuses.value[k] ?: status
            deleteTarget(latest)
            database.deleteDownloadRecord(status.sourceId, status.sourceEpisodeId)
            mutableStatuses.value = mutableStatuses.value - k
            if (job != null) {
                activeJobs.remove(k, job)
            }
        }
    }

    fun retry(status: NamiDownloadStatus) {
        if (status.state != NamiDownloadState.ERROR) return

        scope.launch {
            val source = runCatching {
                sourceRegistry.installedSources()
                    .firstOrNull { it.metadata.id == status.sourceId }
            }.getOrNull()

            if (source == null) {
                update(
                    status.copy(errorMessage = "The source for this download is not installed."),
                    status.sourceAnimeId,
                )
                return@launch
            }

            val request = DownloadRetryPlanner.create(status)
            if (request == null) {
                update(
                    status.copy(errorMessage = "This download record cannot be retried."),
                    status.sourceAnimeId,
                )
                return@launch
            }

            enqueueInternal(
                source = source,
                anime = request.anime,
                episode = request.episode,
                relativeDirectory = request.relativeDirectory,
            )
        }
    }

    private suspend fun runDownload(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        queued: NamiDownloadStatus,
    ) {
        update(
            queued.copy(
                state = NamiDownloadState.DOWNLOADING,
                progress = 0,
                errorMessage = null,
            ),
            episode.ref.sourceAnimeId,
        )

        try {
            val media = source.resolve(episode.ref, episode.sourceState)
                .firstOrNull { it.url.isNotBlank() }
                ?: error("This source did not return a downloadable video.")

            downloadResolvedMedia(
                media = media,
                anime = anime,
                episode = episode,
                initial = queued,
            )
        } catch (_: CancellationException) {
            return
        } catch (t: Throwable) {
            val current = mutableStatuses.value[key(queued.sourceId, queued.sourceEpisodeId)] ?: queued
            update(
                current.copy(
                    contentUri = null,
                    state = NamiDownloadState.ERROR,
                    errorMessage = t.message ?: t.javaClass.simpleName,
                ),
                episode.ref.sourceAnimeId,
            )
        }
    }

    private suspend fun downloadResolvedMedia(
        media: ResolvedMedia,
        anime: AnimeDetails,
        episode: AnimeEpisode,
        initial: NamiDownloadStatus,
    ) {
        val firstConnection = openConnection(media.url, media.headers)
        try {
            val responseMime = firstConnection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
            val isHls = DownloadMediaNaming.isHls(media.url, media.mimeType ?: responseMime)

            if (isHls) {
                val playlist = firstConnection.inputStream.bufferedReader().use { it.readText() }
                downloadHls(
                    initialPlaylistUrl = firstConnection.url.toString(),
                    initialPlaylist = playlist,
                    headers = media.headers,
                    anime = anime,
                    episode = episode,
                    initial = initial,
                )
            } else {
                val mime = media.mimeType ?: responseMime ?: "application/octet-stream"
                val extension = DownloadMediaNaming.extensionFor(
                    mimeType = mime,
                    url = firstConnection.url.toString(),
                    contentDisposition = firstConnection.getHeaderField("Content-Disposition"),
                )
                val displayName = DownloadMediaNaming.episodeFileName(episode, extension)
                val target = createTarget(
                    relativeDirectory = initial.relativePath,
                    displayName = displayName,
                    mimeType = mime,
                )
                try {
                    val total = firstConnection.contentLengthLong.takeIf { it > 0L }
                    firstConnection.inputStream.use { input ->
                        target.output.use { output ->
                            copyWithProgress(
                                input = input,
                                output = output,
                                totalBytes = total,
                                status = initial.copy(
                                    displayName = displayName,
                                    contentUri = target.publicUri,
                                    mimeType = mime,
                                    state = NamiDownloadState.DOWNLOADING,
                                ),
                                sourceAnimeId = episode.ref.sourceAnimeId,
                            )
                        }
                    }
                    target.finish()
                    update(
                        initial.copy(
                            displayName = displayName,
                            contentUri = target.publicUri,
                            mimeType = mime,
                            state = NamiDownloadState.DOWNLOADED,
                            progress = 100,
                            errorMessage = null,
                        ),
                        episode.ref.sourceAnimeId,
                    )
                } catch (t: Throwable) {
                    target.abort()
                    throw t
                }
            }
        } finally {
            firstConnection.disconnect()
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
        val mime = plan.mimeType
        val extension = plan.extension
        val displayName = DownloadMediaNaming.episodeFileName(episode, extension)
        val target = createTarget(
            relativeDirectory = initial.relativePath,
            displayName = displayName,
            mimeType = mime,
        )

        try {
            target.output.use { output ->
                val allParts = plan.allPartUrls
                allParts.forEachIndexed { index, partUrl ->
                    val connection = openConnection(partUrl, headers)
                    try {
                        connection.inputStream.use { input ->
                            copyCancellable(input, output)
                        }
                    } finally {
                        connection.disconnect()
                    }
                    val progress = (((index + 1).toDouble() / allParts.size) * 100)
                        .roundToInt()
                        .coerceIn(0, 99)
                    update(
                        initial.copy(
                            displayName = displayName,
                            contentUri = target.publicUri,
                            mimeType = mime,
                            state = NamiDownloadState.DOWNLOADING,
                            progress = progress,
                        ),
                        episode.ref.sourceAnimeId,
                    )
                }
            }
            target.finish()
            update(
                initial.copy(
                    displayName = displayName,
                    contentUri = target.publicUri,
                    mimeType = mime,
                    state = NamiDownloadState.DOWNLOADED,
                    progress = 100,
                ),
                episode.ref.sourceAnimeId,
            )
        } catch (t: Throwable) {
            target.abort()
            throw t
        }
    }

    private fun openConnection(
        url: String,
        headers: Map<String, String>,
    ): HttpURLConnection {
        if (GoogleDriveDownloadPlanner.isDriveDownload(url)) {
            return openGoogleDriveConnection(url, headers)
        }
        return openRawConnection(url, headers, followRedirects = true)
    }

    private fun openGoogleDriveConnection(
        sourceUrl: String,
        headers: Map<String, String>,
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

            if (code !in 200..299) {
                connection.disconnect()
                error("Google Drive download failed with HTTP $code.")
            }

            val contentDisposition = connection.getHeaderField("Content-Disposition")
            val contentType = connection.contentType.orEmpty()
            if (!contentDisposition.isNullOrBlank() ||
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
        if (requireSuccess && connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Download request failed with HTTP $code.")
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
        status: NamiDownloadStatus,
        sourceAnimeId: String,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastProgress = -1
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read

            val progress = totalBytes
                ?.let { ((copied.toDouble() / it) * 100).roundToInt().coerceIn(0, 99) }
                ?: 0
            if (progress != lastProgress && (progress == 0 || progress - lastProgress >= 2)) {
                lastProgress = progress
                update(status.copy(progress = progress), sourceAnimeId)
            }
        }
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

    private fun createTarget(
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
            val output = FileOutputStream(file)
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

    private fun deleteTarget(status: NamiDownloadStatus) {
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

    @Suppress("DEPRECATION")
    private fun legacyPublicFile(
        relativeDirectory: String,
        displayName: String,
    ): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(File(root, "Nami/" + relativeDirectory), displayName)
    }

    private fun update(status: NamiDownloadStatus, sourceAnimeId: String) {
        val k = key(status.sourceId, status.sourceEpisodeId)
        mutableStatuses.value = mutableStatuses.value + (k to status)
        persist(status, sourceAnimeId)
    }

    private fun persist(status: NamiDownloadStatus, sourceAnimeId: String) {
        database.upsertDownload(
            sourceId = status.sourceId,
            extensionName = status.extensionName,
            sourceAnimeId = sourceAnimeId,
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
        val recoveredState = DownloadRecoveryPolicy.recoverState(status.state)
        if (recoveredState == status.state) return status

        if (DownloadRecoveryPolicy.shouldDiscardPartialTarget(status.state)) {
            discardPartialTarget(status.contentUri)
        }

        val recovered = status.copy(
            contentUri = null,
            state = recoveredState,
            progress = 0,
            errorMessage = DownloadRecoveryPolicy.INTERRUPTED_MESSAGE,
        )

        database.upsertDownload(
            sourceId = stored.sourceId,
            extensionName = stored.extensionName,
            sourceAnimeId = stored.sourceAnimeId,
            sourceEpisodeId = stored.sourceEpisodeId,
            relativePath = stored.relativePath,
            animeTitle = stored.animeTitle,
            episodeTitle = stored.episodeTitle,
            animeSourceState = stored.animeSourceState,
            episodeSourceState = stored.episodeSourceState,
            displayName = stored.displayName,
            contentUri = null,
            mimeType = stored.mimeType,
            state = recovered.state.name,
            progress = recovered.progress,
            errorMessage = recovered.errorMessage,
        )

        return recovered
    }

    private fun discardPartialTarget(uriString: String?) {
        if (uriString.isNullOrBlank()) return

        runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme?.lowercase()) {
                "content" -> context.contentResolver.delete(uri, null, null)
                "file" -> uri.path?.let(::File)?.delete()
            }
        }
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
        state = runCatching { NamiDownloadState.valueOf(state) }.getOrDefault(NamiDownloadState.ERROR),
        progress = progress,
        errorMessage = errorMessage,
    )

    companion object {
        internal const val MAX_PARALLEL_DOWNLOADS = 2
    }

    private data class DownloadTarget(
        val output: OutputStream,
        val publicUri: String,
        val finish: () -> Unit,
        val abort: () -> Unit,
    )
}
