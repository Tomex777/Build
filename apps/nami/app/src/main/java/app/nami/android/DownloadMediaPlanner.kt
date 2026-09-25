package app.nami.android

import app.nami.data.local.DownloadDirectoryLayout
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.EpisodeRef
import java.net.URI

internal data class HlsMediaPlan(
    val initSegmentUrl: String?,
    val segmentUrls: List<String>,
    val mimeType: String,
    val extension: String,
) {
    val allPartUrls: List<String>
        get() = buildList {
            initSegmentUrl?.let(::add)
            addAll(segmentUrls)
        }
}

/**
 * Pure HLS planning logic. Android/network/file I/O stays in [NamiDownloadManager].
 */
internal object HlsPlaylistPlanner {

    fun selectMasterVariant(baseUrl: String, playlist: String): String? {
        val lines = playlist.lineSequence().map(String::trim).toList()
        val candidates = mutableListOf<Pair<Long, String>>()

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@forEachIndexed

            val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: 0L

            val next = lines.drop(index + 1)
                .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                ?: return@forEachIndexed

            candidates += bandwidth to resolveUrl(baseUrl, next)
        }

        return candidates.maxByOrNull { it.first }?.second
    }

    fun mediaPlan(baseUrl: String, playlist: String): HlsMediaPlan {
        val lines = playlist.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        val encrypted = lines.any { line ->
            line.startsWith("#EXT-X-KEY:") &&
                !line.contains("METHOD=NONE", ignoreCase = true)
        }
        require(!encrypted) {
            "Encrypted HLS downloads are not supported yet."
        }

        val initSegment = lines
            .firstOrNull { it.startsWith("#EXT-X-MAP:") }
            ?.let(::extractQuotedUri)
            ?.let { resolveUrl(baseUrl, it) }

        val segments = lines
            .filter { !it.startsWith('#') }
            .map { resolveUrl(baseUrl, it) }

        require(segments.isNotEmpty()) {
            "The HLS playlist did not contain any media segments."
        }

        val fragmentedMp4 = initSegment != null ||
            segments.first().substringBefore('?').endsWith(".m4s", ignoreCase = true)

        return HlsMediaPlan(
            initSegmentUrl = initSegment,
            segmentUrls = segments,
            mimeType = if (fragmentedMp4) "video/mp4" else "video/mp2t",
            extension = if (fragmentedMp4) "mp4" else "ts",
        )
    }

    internal fun resolveUrl(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }
            .getOrDefault(value)

    private fun extractQuotedUri(line: String): String? =
        Regex("""URI="([^"]+)"""")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
}

internal object DownloadMediaNaming {

    fun isHls(url: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase().orEmpty()
        return url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            mime.contains("mpegurl") ||
            mime.contains("m3u8")
    }

    fun extensionFor(mimeType: String, url: String): String {
        val fromUrl = runCatching {
            URI(url).path.substringAfterLast('.', "").lowercase()
        }.getOrDefault("")

        if (fromUrl in setOf("mp4", "mkv", "webm", "ts", "m4v", "avi")) {
            return fromUrl
        }

        return when (mimeType.lowercase()) {
            "video/mp4" -> "mp4"
            "video/x-matroska", "video/mkv" -> "mkv"
            "video/webm" -> "webm"
            "video/mp2t" -> "ts"
            else -> "video"
        }
    }

    fun episodeFileName(
        episode: AnimeEpisode,
        extension: String,
    ): String {
        val numberPrefix = episode.number?.let {
            if (it % 1.0 == 0.0) {
                "Episode " + it.toInt().toString().padStart(3, '0')
            } else {
                "Episode " + it
            }
        }

        val base = if (
            numberPrefix != null &&
            !episode.title.contains(numberPrefix, ignoreCase = true)
        ) {
            numberPrefix + " - " + episode.title
        } else {
            episode.title
        }

        return DownloadDirectoryLayout.sanitize(base) + "." + extension
    }
}


internal object DownloadRecoveryPolicy {
    const val INTERRUPTED_MESSAGE = "Download was interrupted. Tap retry to continue."

    fun recoverState(state: NamiDownloadState): NamiDownloadState = when (state) {
        NamiDownloadState.QUEUED,
        NamiDownloadState.DOWNLOADING,
        -> NamiDownloadState.ERROR

        NamiDownloadState.DOWNLOADED,
        NamiDownloadState.ERROR,
        -> state
    }

    fun shouldDiscardPartialTarget(state: NamiDownloadState): Boolean =
        state == NamiDownloadState.QUEUED || state == NamiDownloadState.DOWNLOADING
}


internal object DownloadStoragePolicy {
    fun requiresLegacyWritePermission(
        sdkInt: Int,
        permissionGranted: Boolean,
    ): Boolean = sdkInt <= 28 && !permissionGranted
}


internal data class RetryDownloadRequest(
    val anime: AnimeDetails,
    val episode: AnimeEpisode,
    val relativeDirectory: String,
)

internal object DownloadRetryPlanner {
    fun create(status: NamiDownloadStatus): RetryDownloadRequest? {
        if (status.state != NamiDownloadState.ERROR) return null
        if (status.sourceId.isBlank() || status.sourceAnimeId.isBlank() || status.sourceEpisodeId.isBlank()) {
            return null
        }

        return RetryDownloadRequest(
            anime = AnimeDetails(
                ref = AnimeRef(
                    sourceId = status.sourceId,
                    sourceAnimeId = status.sourceAnimeId,
                ),
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
}
