package app.nami.fixture.v17

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

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
                    description = "Synthetic extensions-lib 17 source."
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

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val base = Video(
            videoUrl = "https://example.invalid/fixture-v17.mp4",
            videoTitle = "base",
            initialized = true,
        )
        val copied = base.copy(videoTitle = "1080p")

        return listOf(
            Hoster(
                hosterName = "Embedded fixture",
                videoList = listOf(copied),
            ),
        )
    }
}
