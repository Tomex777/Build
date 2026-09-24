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
    suspend fun details(anime: AnimeRef): AnimeDetails
    suspend fun episodes(anime: AnimeRef): List<AnimeEpisode>
    suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia>
}

data class SourceMetadata(
    val id: String,
    val name: String,
    val language: String? = null,
    val origin: SourceOrigin,
)

enum class SourceOrigin { NATIVE_NAMI, ANIYOMI_COMPATIBLE }

data class SourcePage<T>(val items: List<T>, val hasNextPage: Boolean)
