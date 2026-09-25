package app.nami.fixture.v17

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * A separately installed extensions-lib 17 fixture.
 *
 * This APK intentionally compileOnly-links against Nami's host API so runtime class resolution
 * must come from the Nami process, matching a real Aniyomi extension boundary.
 */
class V17FixtureSource : AnimeSource {
    override val id: Long = 17_000_001L
    override val name: String = "Nami V17 Fixture"
    override val lang: String = "en"

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        if (page != 1 || query.isBlank()) return AnimesPage(emptyList(), false)
        return AnimesPage(
            animes = listOf(
                SAnime.create().apply {
                    url = "/fixture/anime"
                    title = "Fixture $query"
                    description = "Synthetic v17 source used by Nami compatibility tests."
                    genre = "Action, Test"
                    status = SAnime.ONGOING
                    initialized = true
                },
            ),
            hasNextPage = false,
        )
    }

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        val updatedAnime = anime.copy().apply {
            if (fetchDetails) {
                title = "Fixture Details"
                author = "Nami"
                initialized = true
            }
        }
        val updatedEpisodes = if (fetchEpisodes) {
            listOf(
                SEpisode.create().apply {
                    url = "/fixture/episode-1"
                    name = "Fixture Episode 1"
                    episode_number = 1f
                },
            )
        } else {
            episodes
        }
        return SAnimeEpisodeUpdate(updatedAnime, updatedEpisodes)
    }

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(anime, seasons)

    override val supportsRelatedAnime: Boolean = true

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> =
        listOf(AnimeRelation("Related", listOf(anime.copy())))

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        listOf(
            Hoster(
                hosterName = "Embedded fixture",
                videoList = listOf(
                    Video(
                        videoUrl = "https://example.invalid/fixture-v17.mp4",
                        videoTitle = "1080p",
                        initialized = true,
                    ),
                ),
            ),
        )
}
