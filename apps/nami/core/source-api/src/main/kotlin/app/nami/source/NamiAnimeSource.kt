package app.nami.source

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia

/** Source implementations return Nami-owned models, regardless of their extension origin. */
interface NamiAnimeSource {
    val metadata: SourceMetadata

    suspend fun search(query: String, page: Int = 1): SourcePage<AnimeSearchResult>

    suspend fun popular(page: Int = 1): SourcePage<AnimeSearchResult> =
        SourcePage(emptyList(), hasNextPage = false)

    suspend fun latest(page: Int = 1): SourcePage<AnimeSearchResult> =
        SourcePage(emptyList(), hasNextPage = false)

    suspend fun details(anime: AnimeRef): AnimeDetails

    suspend fun details(
        anime: AnimeRef,
        sourceState: String?,
    ): AnimeDetails = details(anime)

    suspend fun episodes(anime: AnimeRef): List<AnimeEpisode>

    suspend fun episodes(
        anime: AnimeRef,
        sourceState: String?,
    ): List<AnimeEpisode> = episodes(anime)

    suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia>

    suspend fun resolve(
        episode: EpisodeRef,
        sourceState: String?,
    ): List<ResolvedMedia> = resolve(episode)
}

data class SourceCapabilities(
    val searchable: Boolean = true,
    val browsable: Boolean = false,
    val popular: Boolean = false,
    val latest: Boolean = false,
    val details: Boolean = true,
    val episodes: Boolean = true,
    val streamable: Boolean = true,
    val downloadable: Boolean = false,
    val configurable: Boolean = false,
)

data class SourceMetadata(
    val id: String,
    val name: String,
    val language: String? = null,
    val origin: SourceOrigin,
    /** Extension/package display name used for source-aware download folders. */
    val extensionName: String? = null,
    /** Source homepage used by source browse WebView. */
    val homeUrl: String? = null,
    val capabilities: SourceCapabilities = SourceCapabilities(),
    /** Package metadata is kept at the adapter edge, not used as domain identity. */
    val extensionPackage: String? = null,
    val extensionVersion: String? = null,
    val extensionApiVersion: Int? = null,
)

enum class SourceOrigin { NATIVE_NAMI, ANIYOMI_COMPATIBLE }

data class SourcePage<T>(val items: List<T>, val hasNextPage: Boolean)
