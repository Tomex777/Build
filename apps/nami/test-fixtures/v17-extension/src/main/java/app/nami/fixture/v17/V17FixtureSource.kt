package app.nami.fixture.v17

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class V17FixtureSource : AnimeSource, ConfigurableAnimeSource {
    override val id: Long = 17_000_001L
    override val name: String = "Nami V17 Fixture"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            Preference(screen.context).apply {
                key = "fixture_preference"
                title = "Fixture preference"
                summary = "Nami configurable-source smoke fixture"
            },
        )
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        AnimesPage(
            animes = listOf(
                SAnime.create().apply {
                    url = "/fixture/popular"
                    title = "Fixture Popular"
                    initialized = true
                    memo = JsonObject(mapOf("token" to JsonPrimitive(REQUIRED_TOKEN)))
                },
            ),
            hasNextPage = false,
        )

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        AnimesPage(
            animes = listOf(
                SAnime.create().apply {
                    url = "/fixture/latest"
                    title = "Fixture Latest"
                    initialized = true
                    memo = JsonObject(mapOf("token" to JsonPrimitive(REQUIRED_TOKEN)))
                },
            ),
            hasNextPage = false,
        )

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
                    memo = JsonObject(mapOf("token" to JsonPrimitive(REQUIRED_TOKEN)))
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
        check(anime.memo["token"]?.jsonPrimitive?.content == REQUIRED_TOKEN) {
            "Fixture memo token was not restored"
        }

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
                    memo = JsonObject(mapOf("episodeToken" to JsonPrimitive(REQUIRED_EPISODE_TOKEN)))
                },
            )
        } else {
            episodes
        }

        return SAnimeEpisodeUpdate(updatedAnime, updatedEpisodes)
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        check(episode.memo["episodeToken"]?.jsonPrimitive?.content == REQUIRED_EPISODE_TOKEN) {
            "Fixture episode memo token was not restored"
        }

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

    companion object {
        private const val REQUIRED_TOKEN = "nami-v17-state"
        private const val REQUIRED_EPISODE_TOKEN = "nami-v17-episode-state"
    }
}
