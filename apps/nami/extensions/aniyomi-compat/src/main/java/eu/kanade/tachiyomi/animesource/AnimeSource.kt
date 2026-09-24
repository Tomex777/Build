/*
 * Compatibility API adapted from Aniyomi/extensions-lib v16.
 * Licensed under Apache-2.0.
 */
package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

interface AnimeSource {
    val id: Long
    val name: String

    suspend fun getAnimeDetails(anime: SAnime): SAnime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>
    suspend fun getSeasonList(anime: SAnime): List<SAnime>
    suspend fun getHosterList(episode: SEpisode): List<Hoster>
    suspend fun getVideoList(hoster: Hoster): List<Video>
}

interface AnimeCatalogueSource : AnimeSource {
    val lang: String
    val supportsLatest: Boolean

    suspend fun getPopularAnime(page: Int): AnimesPage
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage
    suspend fun getLatestUpdates(page: Int): AnimesPage
    fun getFilterList(): AnimeFilterList
}

interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}

interface ConfigurableAnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
