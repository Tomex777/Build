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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GlobalAnimeSearchTest {

    @Test
    fun nonEmptyResultsRiseByArrivalWhileEverythingElseStaysAlphabetical() = runBlocking {
        val alpha = FakeSource(
            id = "alpha",
            name = "Alpha",
            delayMillis = 90,
            results = listOf(result("alpha", "Alpha result")),
        )
        val beta = FakeSource(
            id = "beta",
            name = "Beta",
            delayMillis = 5,
            results = emptyList(),
        )
        val zulu = FakeSource(
            id = "zulu",
            name = "Zulu",
            delayMillis = 20,
            results = listOf(result("zulu", "Zulu result")),
        )

        val search = GlobalAnimeSearch(
            NamiSourceRegistry { listOf(zulu, beta, alpha) },
        )

        val states = search.searchFlow("bleach", timeoutMillis = 2_000).toList()

        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            states.first().sections.map { it.source.metadata.name },
            "Before any source returns a real result, sections must be alphabetical.",
        )

        val zuluFirst = states.first { state ->
            val zuluSection = state.sections.first { it.source.metadata.id == "zulu" }
            val alphaSection = state.sections.first { it.source.metadata.id == "alpha" }
            zuluSection.result is AnimeSearchItemResult.Success &&
                !(zuluSection.result as AnimeSearchItemResult.Success).isEmpty &&
                alphaSection.result is AnimeSearchItemResult.Loading
        }

        assertEquals(
            listOf("Zulu", "Alpha", "Beta"),
            zuluFirst.sections.map { it.source.metadata.name },
            "Zulu returned a non-empty result first, so it must rise above Alpha. " +
                "Alpha and Beta remain alphabetical underneath.",
        )

        val finalState = states.last()
        assertEquals(
            listOf("Zulu", "Alpha", "Beta"),
            finalState.sections.map { it.source.metadata.name },
        )
        assertIs<AnimeSearchItemResult.Success>(
            finalState.sections.first { it.source.metadata.id == "beta" }.result,
        )
        assertTrue(
            (finalState.sections.first { it.source.metadata.id == "beta" }.result as AnimeSearchItemResult.Success)
                .isEmpty,
        )
    }

    @Test
    fun nativeSourcesAreExcludedFromExtensionOnlyGlobalSearch() = runBlocking {
        val extension = FakeSource(
            id = "extension",
            name = "Extension",
            delayMillis = 0,
            results = listOf(result("extension", "Extension result")),
        )
        val native = object : NamiAnimeSource by FakeSource(
            id = "native",
            name = "Native",
            delayMillis = 0,
            results = listOf(result("native", "Native result")),
        ) {
            override val metadata = SourceMetadata(
                id = "native",
                name = "Native",
                language = "en",
                origin = SourceOrigin.NATIVE_NAMI,
            )
        }

        val final = GlobalAnimeSearch(
            NamiSourceRegistry { listOf(native, extension) },
        ).search("bleach")

        assertEquals(listOf("extension"), final.resultsBySource.keys.toList())
        assertEquals(listOf("extension"), final.responseOrder)
    }

    @Test
    fun nonSearchableSourcesAreNotIncluded() = runBlocking {
        val searchable = FakeSource(
            id = "searchable",
            name = "Searchable",
            delayMillis = 0,
            results = listOf(result("searchable", "Result")),
        )
        val hidden = FakeSource(
            id = "hidden",
            name = "Hidden",
            delayMillis = 0,
            results = listOf(result("hidden", "Should not appear")),
            searchable = false,
        )

        val final = GlobalAnimeSearch(
            NamiSourceRegistry { listOf(hidden, searchable) },
        ).search("bleach")

        assertEquals(listOf("searchable"), final.resultsBySource.keys.toList())
        assertEquals(listOf("searchable"), final.responseOrder)
    }

    private fun result(sourceId: String, title: String) = AnimeSearchResult(
        ref = AnimeRef(sourceId, title.lowercase().replace(' ', '-')),
        title = title,
    )

    private class FakeSource(
        id: String,
        name: String,
        private val delayMillis: Long,
        private val results: List<AnimeSearchResult>,
        searchable: Boolean = true,
    ) : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = id,
            name = name,
            language = "en",
            origin = SourceOrigin.ANIYOMI_COMPATIBLE,
            capabilities = app.nami.source.SourceCapabilities(searchable = searchable),
        )

        override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
            delay(delayMillis)
            return SourcePage(results, hasNextPage = false)
        }

        override suspend fun details(anime: AnimeRef): AnimeDetails =
            error("Not used by this test")

        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> =
            error("Not used by this test")

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            error("Not used by this test")
    }
}
