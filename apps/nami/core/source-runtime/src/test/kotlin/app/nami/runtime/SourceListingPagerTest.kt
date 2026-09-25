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

class SourceListingPagerTest {

    @Test
    fun popularLatestAndSearchUseTheirOwnSourceMethods() = runBlocking {
        val source = FakeListingSource()
        val pager = SourceListingPager(source)

        assertEquals("popular-1", pager.load(SourceListing.Popular).items.first().title)
        assertEquals("latest-1", pager.load(SourceListing.Latest).items.first().title)
        assertEquals(
            "search:bleach-1",
            pager.load(SourceListing.Search("  bleach  ")).items.first().title,
        )

        assertEquals(
            listOf("popular:1", "latest:1", "search:bleach:1"),
            source.calls,
        )
    }

    @Test
    fun nextPreservesListingDeduplicatesAndNormalizesSourceIdentity() = runBlocking {
        val source = FakeListingSource()
        val pager = SourceListingPager(source)

        val first = pager.load(SourceListing.Search("bleach"))
        val second = pager.next(first)

        assertEquals(2, second.loadedPage)
        assertEquals(
            listOf("search:bleach-1", "shared", "search:bleach-2"),
            second.items.map { it.title },
        )
        assertEquals(
            listOf("fixture", "fixture", "fixture"),
            second.items.map { it.ref.sourceId },
        )
        assertEquals(
            listOf("state-search:bleach-1", "state-shared", "state-search:bleach-2"),
            second.items.map { it.sourceState },
        )
    }

    @Test
    fun blankSearchDoesNotCallSource() = runBlocking {
        val source = FakeListingSource()
        val state = SourceListingPager(source).load(SourceListing.Search("   "))

        assertEquals(SourceListingState(), state)
        assertEquals(emptyList(), source.calls)
    }

    private class FakeListingSource : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = "fixture",
            name = "Fixture",
            origin = SourceOrigin.ANIYOMI_COMPATIBLE,
        )

        val calls = mutableListOf<String>()

        override suspend fun popular(page: Int): SourcePage<AnimeSearchResult> {
            calls += "popular:$page"
            return page("popular", page)
        }

        override suspend fun latest(page: Int): SourcePage<AnimeSearchResult> {
            calls += "latest:$page"
            return page("latest", page)
        }

        override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
            calls += "search:$query:$page"
            return page("search:$query", page)
        }

        private fun page(prefix: String, page: Int): SourcePage<AnimeSearchResult> {
            val items = if (page == 1) {
                listOf(item("$prefix-1"), item("shared"))
            } else {
                listOf(item("shared"), item("$prefix-2"))
            }
            return SourcePage(items, hasNextPage = page == 1)
        }

        private fun item(id: String) = AnimeSearchResult(
            ref = AnimeRef("wrong-source", id),
            title = id,
            sourceState = "state-$id",
        )

        override suspend fun details(anime: AnimeRef): AnimeDetails = error("unused")
        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = error("unused")
        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> = error("unused")
    }
}
