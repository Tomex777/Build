package app.nami.domain

/** Stable Nami identity for an anime supplied by a particular source. */
data class AnimeRef(val sourceId: String, val sourceAnimeId: String)

data class AnimeSearchResult(
    val ref: AnimeRef,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    /** Opaque source-owned state used to reopen this item after process death. */
    val sourceState: String? = null,
)

data class AnimeDetails(
    val ref: AnimeRef,
    val title: String,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val genres: List<String> = emptyList(),
    /** Source page used by the Aniyomi-style WebView action. */
    val webUrl: String? = null,
    /** Updated opaque source-owned state to persist with a library entry. */
    val sourceState: String? = null,
)

data class EpisodeRef(
    val sourceId: String,
    val sourceAnimeId: String,
    val sourceEpisodeId: String,
)

data class AnimeEpisode(
    val ref: EpisodeRef,
    val title: String,
    val number: Double? = null,
    val uploadedAtEpochMillis: Long? = null,
    /** Opaque source-owned state needed to resolve this episode after adapter recreation. */
    val sourceState: String? = null,
)

data class MediaTrack(val url: String, val language: String? = null)

data class ResolvedMedia(
    val url: String,
    val mimeType: String? = null,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    val expiresAtEpochMillis: Long? = null,
    /** Opaque source-owned data that may be used to refresh a temporary URL later. */
    val refreshToken: String? = null,
    /** Hoster label reported by a compatible extension, when available. */
    val hosterName: String? = null,
)


/**
 * Rejects malformed source output before it reaches downloads or VLC.
 *
 * Nami accepts any absolute URI scheme so local/content and VLC-supported protocols remain
 * available, but HTTP(S) locations must also contain a host. Relative/bare strings are never
 * valid final media locations.
 */
fun String?.isPlayableMediaLocation(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isEmpty() || value.equals("null", ignoreCase = true)) return false

    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return false

    return when (scheme.lowercase()) {
        "http", "https" -> !uri.host.isNullOrBlank()
        else -> true
    }
}
