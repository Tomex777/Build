package app.mira.domain

enum class ContentKind {
    MOVIE,
    SERIES,
}

data class ContentRef(
    val sourceId: String,
    val sourceContentId: String,
    val kind: ContentKind,
)

data class ContentSearchResult(
    val ref: ContentRef,
    val title: String,
    val posterUrl: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val sourceState: String? = null,
)

data class ContentDetails(
    val ref: ContentRef,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val webUrl: String? = null,
    val sourceState: String? = null,
)

data class SeasonRef(
    val sourceId: String,
    val sourceContentId: String,
    val sourceSeasonId: String,
)

data class TvSeason(
    val ref: SeasonRef,
    val number: Int?,
    val title: String,
    val episodeCount: Int? = null,
    val posterUrl: String? = null,
    val sourceState: String? = null,
)

data class EpisodeRef(
    val sourceId: String,
    val sourceContentId: String,
    val sourceSeasonId: String,
    val sourceEpisodeId: String,
)

data class TvEpisode(
    val ref: EpisodeRef,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val airDate: String? = null,
    val sourceState: String? = null,
)

data class MediaTrack(
    val url: String,
    val language: String? = null,
)

data class ResolvedMedia(
    val url: String,
    val mimeType: String? = null,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    val hosterName: String? = null,
)
