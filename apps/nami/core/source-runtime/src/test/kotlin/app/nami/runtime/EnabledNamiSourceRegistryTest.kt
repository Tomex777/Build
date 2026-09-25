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

class EnabledNamiSourceRegistryTest {

    @Test
    fun sourcesDefaultEnabledAndDisabledSourcesAreFilteredImmediately() = runBlocking {
        val first = FakeSource("first")
        val second = FakeSource("second")
        val store = FakeEnablementStore()
        val registry = EnabledNamiSourceRegistry(
            installedRegistry = NamiSourceRegistry { listOf(first, second) },
            enablementStore = store,
        )

        assertEquals(listOf("first", "second"), registry.installedSources().map { it.metadata.id })

        store.setEnabled("second", false)
        assertEquals(listOf("first"), registry.installedSources().map { it.metadata.id })

        store.setEnabled("second", true)
        assertEquals(listOf("first", "second"), registry.installedSources().map { it.metadata.id })
    }

    @Test
    fun enablementDoesNotMutateInstalledRegistryOrdering() = runBlocking {
        val sources = listOf(FakeSource("z"), FakeSource("a"), FakeSource("m"))
        val store = FakeEnablementStore().apply {
            setEnabled("a", false)
        }
        val registry = EnabledNamiSourceRegistry(
            installedRegistry = NamiSourceRegistry { sources },
            enablementStore = store,
        )

        assertEquals(listOf("z", "m"), registry.installedSources().map { it.metadata.id })
    }

    private class FakeEnablementStore : SourceEnablementStore {
        private val disabled = mutableSetOf<String>()

        override fun isEnabled(sourceId: String): Boolean = sourceId !in disabled

        override fun setEnabled(sourceId: String, enabled: Boolean) {
            if (enabled) disabled -= sourceId else disabled += sourceId
        }
    }

    private class FakeSource(id: String) : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = id,
            name = id,
            origin = SourceOrigin.ANIYOMI_COMPATIBLE,
        )

        override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> =
            SourcePage(emptyList(), false)

        override suspend fun details(anime: AnimeRef): AnimeDetails = error("unused")
        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = error("unused")
        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> = error("unused")
    }
}
