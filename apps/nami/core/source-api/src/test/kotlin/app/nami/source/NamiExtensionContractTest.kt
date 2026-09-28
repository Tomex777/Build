package app.nami.source

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamiExtensionContractTest {
    @Test
    fun apiVersionAndManifestKeysAreStable() {
        assertEquals(1, NAMI_EXTENSION_API_VERSION)
        assertEquals("app.nami.extension", NamiExtensionManifest.FEATURE)
        assertEquals("app.nami.extension.provider", NamiExtensionManifest.META_PROVIDER_CLASS)
        assertEquals("app.nami.extension.api", NamiExtensionManifest.META_API_VERSION)
    }

    @Test
    fun providerExposesNamiOwnedSources() {
        val provider = object : NamiExtensionProvider {
            override val extensionId = "example.extension"
            override val displayName = "Example"
            override fun sources(host: NamiExtensionHost): List<NamiAnimeSource> =
                listOf(FakeNativeSource())
        }
        val source = provider.sources(NoOpHost).single()
        assertEquals(SourceOrigin.NATIVE_NAMI, source.metadata.origin)
        assertTrue(source.metadata.capabilities.searchable)
    }

    private object NoOpHost : NamiExtensionHost {
        override fun getPreference(extensionId: String, sourceId: String, key: String) = null
        override fun putPreference(extensionId: String, sourceId: String, key: String, value: String) = Unit
        override fun removePreference(extensionId: String, sourceId: String, key: String) = Unit
    }

    private class FakeNativeSource : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = "example:anime",
            name = "Example Anime",
            origin = SourceOrigin.NATIVE_NAMI,
        )
        override suspend fun search(query: String, page: Int) =
            SourcePage<AnimeSearchResult>(emptyList(), false)
        override suspend fun details(anime: AnimeRef): AnimeDetails = error("Not used")
        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = emptyList()
        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> = emptyList()
    }
}
