package com.veya.app.youtube

import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.PlaybackDescriptor
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.api.TransportProof
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.api.VerifiedMedia
import dev.tomex.youtube.api.VideoDetails
import dev.tomex.youtube.api.YouTubeEngine

class VeyaYouTubeRepository(
    private val engine: YouTubeEngine
) {
    suspend fun searchVideos(query: String, continuation: String? = null): VeyaSearchPage {
        require(query.isNotBlank())
        val page = engine.search(query.trim(), continuation)
        return VeyaSearchPage(
            videos = page.items.filterIsInstance<SearchResult.Video>(),
            continuation = page.continuation
        )
    }

    suspend fun videoDetails(videoId: String): VideoDetails =
        engine.videoDetails(videoId)

    suspend fun preparePlayback(
        videoId: String,
        preferredHeight: Int = 720
    ): VeyaPlaybackSelection {
        val descriptor = engine.resolve(videoId)
        val videoCandidates = descriptor.videoOnly
            .filter { it.transportReady && (it.height ?: 0) > 0 }

        val requested = preferredHeight.coerceAtLeast(144)
        val requestedVideo = videoCandidates
            .filter { (it.height ?: 0) <= requested }
            .maxWithOrNull(
                compareBy<MediaFormat> { it.height ?: 0 }
                    .thenBy { it.bitrate ?: 0L }
            )
            ?: videoCandidates.minWithOrNull(
                compareBy<MediaFormat> { it.height ?: Int.MAX_VALUE }
                    .thenByDescending { it.bitrate ?: 0L }
            )
            ?: throw ResolverFailure.NoPlayableFormats(
                "No transport-ready video representation is available"
            )

        return prepareQuality(
            videoId = videoId,
            descriptor = descriptor,
            requestedVideo = requestedVideo
        )
    }

    suspend fun prepareQuality(
        videoId: String,
        descriptor: PlaybackDescriptor,
        requestedVideo: MediaFormat
    ): VeyaPlaybackSelection {
        require(requestedVideo.hasVideo && !requestedVideo.hasAudio)
        require(requestedVideo.transportReady)

        val verifiedVideo = engine.refreshMediaVerified(
            videoId,
            requestedVideo.stableIdentity
        )

        val audioCandidate = descriptor.audioOnly
            .asSequence()
            .filter { it.transportReady }
            .sortedWith(
                compareByDescending<MediaFormat> {
                    it.container.equals(requestedVideo.container, ignoreCase = true)
                }.thenByDescending { it.bitrate ?: 0L }
            )
            .firstOrNull()
            ?: error("No compatible audio representation is available")

        val verifiedAudio = engine.refreshMediaVerified(
            videoId,
            audioCandidate.stableIdentity
        )

        return VeyaPlaybackSelection(
            videoId = videoId,
            descriptor = descriptor,
            video = verifiedVideo,
            audio = verifiedAudio
        )
    }
}

data class VeyaSearchPage(
    val videos: List<SearchResult.Video>,
    val continuation: String?
)

data class VeyaPlaybackSelection(
    val videoId: String,
    val descriptor: PlaybackDescriptor,
    val video: VerifiedMedia,
    val audio: VerifiedMedia
) {
    val proven: Boolean
        get() = video.proof.bytesRead > 0 && audio.proof.bytesRead > 0

    val qualityOptions: List<MediaFormat>
        get() = descriptor.videoOnly
            .filter { it.transportReady && (it.height ?: 0) > 0 }
            .sortedWith(
                compareByDescending<MediaFormat> { it.height ?: 0 }
                    .thenByDescending { it.bitrate ?: 0L }
            )
            .distinctBy { it.height }
}

internal fun MediaFormat.totalBytesFrom(proof: TransportProof): Long? =
    contentLength
        ?: proof.contentRange
            ?.substringAfterLast('/', "")
            ?.takeIf { it != "*" }
            ?.toLongOrNull()
