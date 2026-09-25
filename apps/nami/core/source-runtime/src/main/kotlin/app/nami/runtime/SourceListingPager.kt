package app.nami.runtime

import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiAnimeSource

sealed interface SourceListing {
    data object Popular : SourceListing
    data object Latest : SourceListing
    data class Search(val query: String) : SourceListing
}

data class SourceListingState(
    val listing: SourceListing? = null,
    val items: List<AnimeSearchResult> = emptyList(),
    val loadedPage: Int = 0,
    val hasNextPage: Boolean = false,
)

/**
 * Shared pagination for a single source's Popular, Latest, and Search listings.
 */
class SourceListingPager(
    private val source: NamiAnimeSource,
) {
    suspend fun load(listing: SourceListing): SourceListingState {
        val normalized = when (listing) {
            SourceListing.Popular -> listing
            SourceListing.Latest -> listing
            is SourceListing.Search -> {
                val query = listing.query.trim()
                if (query.isEmpty()) return SourceListingState()
                SourceListing.Search(query)
            }
        }

        return loadPage(
            listing = normalized,
            page = 1,
            existing = emptyList(),
        )
    }

    suspend fun next(current: SourceListingState): SourceListingState {
        val listing = current.listing ?: return current
        if (!current.hasNextPage || current.loadedPage < 1) return current

        return loadPage(
            listing = listing,
            page = current.loadedPage + 1,
            existing = current.items,
        )
    }

    private suspend fun loadPage(
        listing: SourceListing,
        page: Int,
        existing: List<AnimeSearchResult>,
    ): SourceListingState {
        val result = when (listing) {
            SourceListing.Popular -> source.popular(page)
            SourceListing.Latest -> source.latest(page)
            is SourceListing.Search -> source.search(listing.query, page)
        }

        val normalizedItems = result.items.map { item ->
            item.copy(ref = item.ref.copy(sourceId = source.metadata.id))
        }
        val combined = (existing + normalizedItems).distinctBy { item ->
            item.ref.sourceAnimeId
        }

        return SourceListingState(
            listing = listing,
            items = combined,
            loadedPage = page,
            hasNextPage = result.hasNextPage,
        )
    }
}
