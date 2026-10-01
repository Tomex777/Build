package app.mira.source

import app.mira.domain.ContentDetails
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import app.mira.domain.EpisodeRef
import app.mira.domain.ResolvedMedia
import app.mira.domain.SeasonRef
import app.mira.domain.TvEpisode
import app.mira.domain.TvSeason

interface MiraSource {
    val metadata: SourceMetadata

    suspend fun search(query: String, page: Int = 1): SourcePage<ContentSearchResult>

    suspend fun popular(page: Int = 1): SourcePage<ContentSearchResult> =
        SourcePage(emptyList(), false)

    suspend fun details(
        content: ContentRef,
        sourceState: String? = null,
    ): ContentDetails

    suspend fun seasons(
        series: ContentRef,
        sourceState: String? = null,
    ): List<TvSeason> = emptyList()

    suspend fun episodes(
        season: SeasonRef,
        sourceState: String? = null,
    ): List<TvEpisode> = emptyList()

    suspend fun resolveMovie(
        movie: ContentRef,
        sourceState: String? = null,
    ): List<ResolvedMedia> = emptyList()

    suspend fun resolveEpisode(
        episode: EpisodeRef,
        sourceState: String? = null,
    ): List<ResolvedMedia> = emptyList()
}

data class SourceCapabilities(
    val movies: Boolean = false,
    val series: Boolean = false,
    val searchable: Boolean = true,
    val browsable: Boolean = false,
    val movieStreaming: Boolean = false,
    val episodeStreaming: Boolean = false,
    val downloadable: Boolean = false,
    val configurable: Boolean = false,
)

data class SourceMetadata(
    val id: String,
    val name: String,
    val language: String? = null,
    val homeUrl: String? = null,
    val capabilities: SourceCapabilities,
    val extensionPackage: String? = null,
    val extensionVersion: String? = null,
    val extensionApiVersion: Int? = null,
)

data class SourcePage<T>(
    val items: List<T>,
    val hasNextPage: Boolean,
)

fun SourceMetadata.supports(kind: ContentKind): Boolean = when (kind) {
    ContentKind.MOVIE -> capabilities.movies
    ContentKind.SERIES -> capabilities.series
}
