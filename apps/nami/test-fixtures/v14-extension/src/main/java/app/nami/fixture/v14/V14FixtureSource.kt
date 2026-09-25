package app.nami.fixture.v14

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

class V14FixtureSource : AnimeCatalogueSource {
    override val id: Long = 14_000_001L
    override val name: String = "Nami V14 Fixture"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    @Deprecated("Legacy fixture path")
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        Observable.just(
            AnimesPage(
                listOf(
                    SAnime.create().apply {
                        url = "/fixture14/popular"
                        title = "Fixture14 Popular"
                        initialized = true
                    },
                ),
                false,
            ),
        )

    @Deprecated("Legacy fixture path")
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        Observable.just(
            AnimesPage(
                listOf(
                    SAnime.create().apply {
                        url = "/fixture14/latest"
                        title = "Fixture14 Latest"
                        initialized = true
                    },
                ),
                false,
            ),
        )

    @Deprecated("Legacy fixture path")
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> {
        if (page != 1 || query.isBlank()) {
            return Observable.just(AnimesPage(emptyList(), false))
        }

        return Observable.just(
            AnimesPage(
                listOf(
                    SAnime.create().apply {
                        url = "/fixture14/anime"
                        title = "Fixture14 $query"
                        description = "Synthetic extensions-lib 14 source."
                        genre = "Action, Legacy"
                        status = SAnime.ONGOING
                        initialized = true
                    },
                ),
                false,
            ),
        )
    }

    @Deprecated("Legacy fixture path")
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        Observable.just(
            anime.copy().apply {
                title = "Fixture14 Details"
                author = "Nami"
                initialized = true
            },
        )

    @Deprecated("Legacy fixture path")
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        Observable.just(
            listOf(
                SEpisode.create().apply {
                    url = "/fixture14/episode-1"
                    name = "Fixture14 Episode 1"
                    episode_number = 1f
                },
            ),
        )

    @Deprecated("Legacy fixture path")
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        Observable.just(
            listOf(
                Video(
                    url = "https://example.invalid/v14-page",
                    quality = "720p",
                    videoUrl = "https://example.invalid/fixture-v14.mp4",
                ),
            ),
        )
}
