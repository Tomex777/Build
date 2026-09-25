package app.nami.runtime

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.source.NamiAnimeSource
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSearchPagerTest {

    @Test
    fun pagesAreLoadedSequentiallyAndOverlapsAreDeduplicated() = runBlocking {
        val source = FakePagedSource()
        val pager = SourceSearchPager(source)

        val first = pager.search("  bleach  ")
        assertEquals("bleach", first.query)
        assertEquals(1, first.loadedPage)
        assertTrue(first.hasNextPage)
        assertEquals(listOf("one", "two"), first.items.map { it.ref.sourceAnimeId })

        val second = pager.next(first)
        assertEquals(2, second.loadedPage)
        assertFalse(second.hasNextPage)
        assertEquals(
            listOf("one", "two", "three"),
            second.items.map { it.ref.sourceAnimeId },
        )
        assertEquals(listOf(1, 2), source.requestedPages)
        assertEquals(
            listOf("extension", "extension", "extension"),
            second.items.map { it.ref.sourceId },
        )
        assertEquals(
            listOf("state-one", "state-two", "state-three"),
            second.items.map { it.sourceState },
        )
    }

    @Test
    fun nextDoesNothingWhenSourceHasNoMorePages() = runBlocking {
        val source = FakePagedSource()
        val pager = SourceSearchPager(source)

        val done = pager.next(pager.next(pager.search("bleach")))

        assertEquals(2, done.loadedPage)
        assertEquals(listOf(1, 2), source.requestedPages)
    }

    @Test
    fun blankQueryDoesNotCallSource() = runBlocking {
        val source = FakePagedSource()
        val state = SourceSearchPager(source).search("   ")

        assertEquals(SourceSearchState(), state)
        assertEquals(emptyList(), source.requestedPages)
    }

    private class FakePagedSource : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = "extension",
            name = "Extension",
            origin = SourceOrigin.ANIYOMI_COMPATIBLE,
        )

        val requestedPages = mutableListOf<Int>()

        override suspend fun search(
            query: String,
            page: Int,
        ): SourcePage<AnimeSearchResult> {
            requestedPages += page
            return when (page) {
                1 -> SourcePage(
                    items = listOf(
                        item(sourceId = "wrong", animeId = "one"),
                        item(sourceId = "wrong", animeId = "two"),
                    ),
                    hasNextPage = true,
                )
                2 -> SourcePage(
                    items = listOf(
                        item(sourceId = "wrong", animeId = "two"),
                        item(sourceId = "wrong", animeId = "three"),
                    ),
                    hasNextPage = false,
                )
                else -> error("Unexpected page $page")
            }
        }

        override suspend fun details(anime: AnimeRef): AnimeDetails =
            error("Not used")

        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> =
            error("Not used")

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            error("Not used")

        private fun item(sourceId: String, animeId: String) = AnimeSearchResult(
            ref = AnimeRef(sourceId, animeId),
            title = animeId,
            sourceState = "state-" + animeId,
        )
    }
}
