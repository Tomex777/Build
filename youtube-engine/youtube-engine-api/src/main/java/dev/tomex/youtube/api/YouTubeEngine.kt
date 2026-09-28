package dev.tomex.youtube.api

/** Version 1. Temporary URLs are never stable identifiers. */
interface YouTubeEngine {
    suspend fun search(query: String, continuation: String? = null): Page<SearchResult>
    suspend fun videoDetails(videoId: String): VideoDetails
    suspend fun resolve(videoId: String): PlaybackDescriptor
    suspend fun refreshMedia(videoId: String, stableFormatIdentity: String): MediaFormat
    suspend fun probe(format: MediaFormat, byteLimit: Int = 4096): TransportProof
    suspend fun probeRange(format: MediaFormat, startByte: Long, byteLimit: Int = 4096): TransportProof
    suspend fun resolveVerified(videoId: String, minimumHeight: Int = 1080): VerifiedPlayback
    suspend fun fetchSubtitle(track: SubtitleTrack, byteLimit: Int = 256_000): SubtitleProof
}

data class Page<T>(val items: List<T>, val continuation: String? = null)
sealed interface SearchResult {
    data class Video(val id: String, val title: String, val channel: String?, val thumbnail: String?, val durationSeconds: Int?) : SearchResult
    data class Channel(val id: String, val title: String, val thumbnail: String?) : SearchResult
    data class Playlist(val id: String, val title: String, val thumbnail: String?) : SearchResult
}
data class Chapter(val title: String, val startMs: Long)
data class SubtitleTrack(val language: String, val name: String, val url: String, val automatic: Boolean)
data class SubtitleProof(val status: Int, val bytesRead: Int, val contentType: String?)
data class VideoDetails(
    val id: String, val title: String, val channel: String?, val channelId: String?,
    val description: String?, val durationSeconds: Long?, val thumbnails: List<String>,
    val chapters: List<Chapter> = emptyList(), val subtitles: List<SubtitleTrack> = emptyList()
)
enum class Delivery { PROGRESSIVE, ADAPTIVE, DASH, SABR }
data class MediaFormat(
    val stableIdentity: String, val itag: Int, val url: String, val mimeType: String,
    val codecs: String?, val container: String?, val width: Int?, val height: Int?, val fps: Int?,
    val bitrate: Long?, val contentLength: Long?, val audioChannels: Int?, val audioSampleRate: Int?,
    val hasVideo: Boolean, val hasAudio: Boolean, val delivery: Delivery,
    val requiredHeaders: Map<String, String>, val expiresAtEpochSeconds: Long?,
    val rangeSupported: Boolean? = null
)
enum class ResolutionState { SUPPORTED_AND_PROVEN, UNVERIFIED, CHALLENGED, CIPHERED, SABR_ONLY, EXPIRED, UNSUPPORTED }
data class AdaptivePlaybackSelection(val video: MediaFormat, val audio: MediaFormat)
data class VerifiedPlayback(
    val descriptor: PlaybackDescriptor, val selection: AdaptivePlaybackSelection,
    val videoProof: TransportProof, val audioProof: TransportProof,
    val state: ResolutionState = ResolutionState.SUPPORTED_AND_PROVEN
)
data class PlaybackDescriptor(
    val videoId: String, val formats: List<MediaFormat>, val client: String,
    val diagnostics: List<String>, val subtitles: List<SubtitleTrack> = emptyList()
) {
    val videoOnly get() = formats.filter { it.hasVideo && !it.hasAudio }
    val audioOnly get() = formats.filter { it.hasAudio && !it.hasVideo }
    val progressive get() = formats.filter { it.hasVideo && it.hasAudio }
    val state: ResolutionState get() = ResolutionState.UNVERIFIED
    fun selectAdaptive(minimumHeight: Int = 1080): AdaptivePlaybackSelection? {
        val videos = videoOnly.filter { (it.height ?: 0) >= minimumHeight }
            .sortedWith(compareByDescending<MediaFormat> { it.height ?: 0 }.thenByDescending { it.bitrate ?: 0 })
        for (video in videos) {
            val audio = audioOnly.filter { it.container == video.container }
                .maxByOrNull { it.bitrate ?: 0 }
            if (audio != null) return AdaptivePlaybackSelection(video, audio)
        }
        return null
    }
}
data class TransportProof(val host: String, val status: Int, val bytesRead: Int, val contentRange: String?, val contentLength: Long?, val startByte: Long = 0)

sealed class ResolverFailure(message: String) : Exception(message) {
    class VideoUnavailable(message: String) : ResolverFailure(message)
    class SignInRequired(message: String) : ResolverFailure(message)
    class ChallengeRequired(message: String) : ResolverFailure(message)
    class NoPlayableFormats(message: String) : ResolverFailure(message)
    class PlayerResponseFailure(message: String) : ResolverFailure(message)
    class MediaUrlExpired(message: String) : ResolverFailure(message)
    class NetworkFailure(message: String) : ResolverFailure(message)
    class UnsupportedDelivery(message: String) : ResolverFailure(message)
    class Ciphered(message: String) : ResolverFailure(message)
    class SabrOnly(message: String) : ResolverFailure(message)
}

/** Host app owns storage and credentials; the engine never shares them between apps. */
interface SessionProvider {
    suspend fun visitorData(): String? = null
    suspend fun requestHeaders(): Map<String, String> = emptyMap()
}
object AnonymousSession : SessionProvider
