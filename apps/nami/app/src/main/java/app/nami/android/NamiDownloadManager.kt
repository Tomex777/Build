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
import app.nami.source.NamiAnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import java.net.URL
import kotlin.math.roundToInt

enum class NamiDownloadState {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

data class NamiDownloadStatus(
    val sourceId: String,
    val sourceEpisodeId: String,
    val extensionName: String,
    val animeTitle: String,
    val episodeTitle: String,
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadPermits = Semaphore(MAX_PARALLEL_DOWNLOADS)
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
            sourceEpisodeId = episode.ref.sourceEpisodeId,
            extensionName = extensionName,
            animeTitle = anime.title,
            episodeTitle = episode.title,
            relativePath = relativeDirectory,
            state = NamiDownloadState.QUEUED,
        )
        mutableStatuses.value = mutableStatuses.value + (k to queued)
        scope.launch {
            persist(
                status = queued,
                sourceAnimeId = episode.ref.sourceAnimeId,
            )
            downloadPermits.withPermit {
                // QUEUED is now a real queue state: only permit holders enter DOWNLOADING.
                runDownload(source, anime, episode, queued)
            }
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

    fun retry(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        episode: AnimeEpisode,
    ) {
        enqueue(source, anime, episode)
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
                val extension = DownloadMediaNaming.extensionFor(mime, firstConnection.url.toString())
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
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
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
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
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
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Download request failed with HTTP $code.")
        }
        return connection
    }

    private fun copyWithProgress(
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
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val directory = File(root, "Nami/" + relativeDirectory).apply { mkdirs() }
            val file = File(directory, displayName)
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
        sourceEpisodeId = sourceEpisodeId,
        extensionName = extensionName,
        animeTitle = relativePath.substringAfter('/').substringBefore('/'),
        episodeTitle = displayName?.substringBeforeLast('.') ?: sourceEpisodeId,
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
