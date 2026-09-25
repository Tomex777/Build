package app.nami.compat.aniyomi

import app.nami.domain.EpisodeRef
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.test.runTest
import rx.Observable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyAnimeSourceAdapterTest {

    @Test
    fun v14UsesLegacyRxSearchDetailsAndEpisodes() = runTest {
        val source = V14Source()
        val adapter = adapter(api = 14, source = source)

        val search = adapter.search("bleach")
        assertEquals(listOf("Bleach v14"), search.items.map { it.title })
        assertEquals(1, source.rxSearchCalls)

        val anime = search.items.single()
        val details = adapter.details(anime.ref)
        assertEquals("Bleach v14 details", details.title)
        assertEquals(1, source.rxDetailsCalls)

        val episodes = adapter.episodes(anime.ref)
        assertEquals(listOf("Episode 1"), episodes.map { it.title })
        assertEquals(1, source.rxEpisodeCalls)

        val media = adapter.resolve(episodes.single().ref)
        assertEquals(listOf("https://cdn.example/v14.mp4"), media.map { it.url })
        assertEquals("720p", media.single().quality)
    }

    @Test
    fun v16UsesSuspendApiAndEmbeddedHosterVideosWithoutSecondRequest() = runTest {
        val source = V16Source()
        val adapter = adapter(api = 16, source = source)

        val search = adapter.search("frieren")
        assertEquals("Frieren v16", search.items.single().title)
        assertEquals(1, source.searchCalls)

        val details = adapter.details(search.items.single().ref)
        assertEquals("Frieren v16 details", details.title)
        assertEquals(1, source.detailsCalls)

        val episodes = adapter.episodes(search.items.single().ref)
        val media = adapter.resolve(episodes.single().ref)

        assertEquals("https://cdn.example/v16.mkv", media.single().url)
        assertEquals(1, source.hosterCalls)
        assertEquals(
            0,
            source.hosterVideoCalls,
            "Embedded hoster.videoList must be consumed directly instead of making a second network request.",
        )
    }

    @Test
    fun v17UsesCombinedEpisodeUpdateApiAndIsSearchableWithoutCatalogueMarker() = runTest {
        val source = V17Source()
        val adapter = adapter(api = 17, source = source)

        assertTrue(adapter.metadata.capabilities.searchable)
        assertEquals(17, adapter.metadata.extensionApiVersion)

        val search = adapter.search("dandadan")
        assertEquals("Dandadan v17", search.items.single().title)
        assertEquals(1, source.searchCalls)

        val details = adapter.details(search.items.single().ref)
        assertEquals("Dandadan v17 details", details.title)
        assertEquals(1, source.detailUpdateCalls)
        assertEquals(0, source.episodeUpdateCalls)

        val episodes = adapter.episodes(search.items.single().ref)
        assertEquals(1, source.episodeUpdateCalls)
        assertEquals("Episode 7", episodes.single().title)

        val media = adapter.resolve(episodes.single().ref)
        assertEquals("https://cdn.example/v17.webm", media.single().url)
    }

    @Test
    fun adapterMetadataKeepsStablePackageScopedIdentity() {
        val adapter = adapter(api = 16, source = V16Source())

        assertTrue(adapter.metadata.id.startsWith("test.extension:"))
        assertEquals("Test Extension", adapter.metadata.extensionName)
        assertEquals("16.9", adapter.metadata.extensionVersion)
        assertEquals(16, adapter.metadata.extensionApiVersion)
        assertFalse(adapter.metadata.capabilities.configurable)
    }

    private fun adapter(
        api: Int,
        source: AnimeSource,
    ) = LegacyAnimeSourceAdapter(
        packageName = "test.extension",
        extensionName = "Test Extension",
        extensionVersion = "$api.9",
        extensionApiVersion = api,
        source = source,
    )

    private class V14Source : AnimeCatalogueSource {
        override val id = 14L
        override val name = "V14"
        override val lang = "en"

        var rxSearchCalls = 0
        var rxDetailsCalls = 0
        var rxEpisodeCalls = 0

        @Deprecated("legacy test")
        override fun fetchSearchAnime(
            page: Int,
            query: String,
            filters: AnimeFilterList,
        ): Observable<AnimesPage> {
            rxSearchCalls++
            return Observable.just(
                AnimesPage(
                    listOf(anime("/v14/bleach", "Bleach v14")),
                    false,
                ),
            )
        }

        @Deprecated("legacy test")
        override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
            rxDetailsCalls++
            return Observable.just(
                anime.copy().apply {
                    title = "Bleach v14 details"
                    description = "Legacy details"
                },
            )
        }

        @Deprecated("legacy test")
        override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
            rxEpisodeCalls++
            return Observable.just(listOf(episode("/v14/e1", "Episode 1", 1f)))
        }

        @Deprecated("legacy test")
        override suspend fun getVideoList(episode: SEpisode): List<Video> =
            listOf(
                Video(
                    videoUrl = "https://cdn.example/v14.mp4",
                    videoTitle = "720p",
                    initialized = true,
                ),
            )
    }

    private class V16Source : AnimeCatalogueSource {
        override val id = 16L
        override val name = "V16"
        override val lang = "en"

        var searchCalls = 0
        var detailsCalls = 0
        var hosterCalls = 0
        var hosterVideoCalls = 0

        override suspend fun getSearchAnime(
            page: Int,
            query: String,
            filters: AnimeFilterList,
        ): AnimesPage {
            searchCalls++
            return AnimesPage(listOf(anime("/v16/frieren", "Frieren v16")), false)
        }

        @Deprecated("compatibility test")
        override suspend fun getAnimeDetails(anime: SAnime): SAnime {
            detailsCalls++
            return anime.copy().apply { title = "Frieren v16 details" }
        }

        @Deprecated("compatibility test")
        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
            listOf(episode("/v16/e1", "Episode 1", 1f))

        override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
            hosterCalls++
            return listOf(
                Hoster(
                    hosterUrl = "https://unused.example/hoster",
                    hosterName = "Embedded",
                    videoList = listOf(
                        Video(
                            videoUrl = "https://cdn.example/v16.mkv",
                            videoTitle = "1080p",
                            initialized = true,
                        ),
                    ),
                ),
            )
        }

        override suspend fun getVideoList(hoster: Hoster): List<Video> {
            hosterVideoCalls++
            error("Embedded hoster videos should avoid getVideoList(hoster)")
        }
    }

    private class V17Source : AnimeSource {
        override val id = 17L
        override val name = "V17"
        override val lang = "en"

        var searchCalls = 0
        var detailUpdateCalls = 0
        var episodeUpdateCalls = 0

        override suspend fun getSearchAnime(
            page: Int,
            query: String,
            filters: AnimeFilterList,
        ): AnimesPage {
            searchCalls++
            return AnimesPage(listOf(anime("/v17/dandadan", "Dandadan v17")), false)
        }

        override suspend fun getAnimeEpisodeUpdate(
            anime: SAnime,
            episodes: List<SEpisode>,
            fetchDetails: Boolean,
            fetchEpisodes: Boolean,
        ): SAnimeEpisodeUpdate {
            if (fetchDetails) detailUpdateCalls++
            if (fetchEpisodes) episodeUpdateCalls++

            val updatedAnime = anime.copy().apply {
                if (fetchDetails) title = "Dandadan v17 details"
            }
            val updatedEpisodes = if (fetchEpisodes) {
                listOf(episode("/v17/e7", "Episode 7", 7f))
            } else {
                episodes
            }
            return SAnimeEpisodeUpdate(updatedAnime, updatedEpisodes)
        }

        override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
            listOf(
                Hoster(
                    hosterName = "Embedded v17",
                    videoList = listOf(
                        Video(
                            videoUrl = "https://cdn.example/v17.webm",
                            videoTitle = "1080p",
                            initialized = true,
                        ),
                    ),
                ),
            )
    }

    companion object {
        private fun anime(url: String, title: String): SAnime =
            SAnime.create().apply {
                this.url = url
                this.title = title
            }

        private fun episode(url: String, title: String, number: Float): SEpisode =
            SEpisode.create().apply {
                this.url = url
                name = title
                episode_number = number
            }
    }
}
