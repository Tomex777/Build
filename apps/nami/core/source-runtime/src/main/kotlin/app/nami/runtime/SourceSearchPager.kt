package app.nami.runtime

import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiAnimeSource

data class SourceSearchState(
    val query: String = "",
    val items: List<AnimeSearchResult> = emptyList(),
    val loadedPage: Int = 0,
    val hasNextPage: Boolean = false,
)

/**
 * Deterministic pagination for a single extension/source.
 *
 * The UI owns loading/error presentation; this class owns page numbering, stable source identity,
 * and duplicate suppression across pages.
 */
class SourceSearchPager(
    private val source: NamiAnimeSource,
) {
    suspend fun search(query: String): SourceSearchState {
        val normalized = query.trim()
        if (normalized.isEmpty()) return SourceSearchState()
        return loadPage(
            query = normalized,
            page = 1,
            existing = emptyList(),
        )
    }

    suspend fun next(current: SourceSearchState): SourceSearchState {
        if (current.query.isBlank() || !current.hasNextPage || current.loadedPage < 1) {
            return current
        }
        return loadPage(
            query = current.query,
            page = current.loadedPage + 1,
            existing = current.items,
        )
    }

    private suspend fun loadPage(
        query: String,
        page: Int,
        existing: List<AnimeSearchResult>,
    ): SourceSearchState {
        val result = source.search(query, page)
        val normalizedItems = result.items.map { item ->
            item.copy(ref = item.ref.copy(sourceId = source.metadata.id))
        }
        val combined = (existing + normalizedItems).distinctBy { item ->
            item.ref.sourceAnimeId
        }

        return SourceSearchState(
            query = query,
            items = combined,
            loadedPage = page,
            hasNextPage = result.hasNextPage,
        )
    }
}
