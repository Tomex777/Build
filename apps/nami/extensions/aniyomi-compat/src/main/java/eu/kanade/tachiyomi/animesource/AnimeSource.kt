/*
 * Compatibility API adapted from the pinned Aniyomi source API.
 * It intentionally keeps legacy v14/v16 entry points beside the v17 API.
 * Licensed under Apache-2.0.
 */
package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

interface AnimeSource {
    val id: Long
    val name: String

    val lang: String
        get() = ""

    val supportsLatest: Boolean
        get() = false

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    suspend fun getPopularAnime(page: Int): AnimesPage =
        throw UnsupportedOperationException("Popular anime is not supported")

    suspend fun getLatestUpdates(page: Int): AnimesPage =
        throw UnsupportedOperationException("Latest updates are not supported")

    suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = throw UnsupportedOperationException("Search is not supported")

    suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        val updatedAnime = if (fetchDetails) getAnimeDetails(anime) else anime
        val updatedEpisodes = if (fetchEpisodes) getEpisodeList(anime) else episodes
        return SAnimeEpisodeUpdate(updatedAnime, updatedEpisodes)
    }

    suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate {
        val updatedAnime = if (fetchDetails) getAnimeDetails(anime) else anime
        val updatedSeasons = if (fetchSeasons) getSeasonList(anime) else seasons
        return SAnimeSeasonUpdate(updatedAnime, updatedSeasons)
    }

    val supportsRelatedAnime: Boolean
        get() = false

    suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    /**
     * Komikku/Anikku related-anime callback ABI used by maintained extensions.
     */
    suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        try {
            val relations = getRelatedAnimeList(anime)
            if (relations.isEmpty()) {
                pushResults("Related" to emptyList(), true)
            } else {
                relations.forEachIndexed { index, relation ->
                    pushResults(
                        relation.name to relation.animes,
                        index == relations.lastIndex,
                    )
                }
            }
        } catch (throwable: Throwable) {
            exceptionHandler(throwable)
        }
    }

    suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        throw UnsupportedOperationException("Hosters are not supported")

    suspend fun getVideoList(hoster: Hoster): List<Video> =
        throw UnsupportedOperationException("Hoster videos are not supported")

    @Deprecated("Use the combined suspend API instead")
    suspend fun getAnimeDetails(anime: SAnime): SAnime =
        throw UnsupportedOperationException("Anime details are not supported")

    @Deprecated("Use the combined suspend API instead")
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        throw UnsupportedOperationException("Episodes are not supported")

    @Deprecated("Use the combined suspend API instead")
    suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("Seasons are not supported")

    // extensions-lib 14 / legacy direct-video API.
    @Deprecated("Use the hoster API instead")
    suspend fun getVideoList(episode: SEpisode): List<Video> =
        fetchVideoList(episode).toBlocking().single()

    @Deprecated("Use the combined suspend API instead")
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        Observable.error(UnsupportedOperationException("Anime details are not supported"))

    @Deprecated("Use the suspend API instead")
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        Observable.error(UnsupportedOperationException("Episodes are not supported"))

    @Deprecated("Use the suspend API instead")
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        Observable.error(UnsupportedOperationException("Videos are not supported"))
}

interface AnimeCatalogueSource : AnimeSource {
    override val lang: String

    /** Komikku/Anikku extensions-lib compatibility surface. */
    val supportsRelatedAnimes: Boolean
        get() = supportsRelatedAnime

    val disableRelatedAnimesBySearch: Boolean
        get() = false

    val disableRelatedAnimes: Boolean
        get() = false

    override suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        try {
            val relations = getRelatedAnimeList(anime)
            if (relations.isNotEmpty()) {
                relations.forEachIndexed { index, relation ->
                    pushResults(
                        relation.name to relation.animes,
                        index == relations.lastIndex,
                    )
                }
            } else if (!disableRelatedAnimes) {
                getRelatedAnimeListByExtension(anime, pushResults)
            }
        } catch (throwable: Throwable) {
            exceptionHandler(throwable)
        }
    }

    suspend fun getRelatedAnimeListByExtension(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val related = fetchRelatedAnimeList(anime)
        pushResults("Related" to related, true)
    }

    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> =
        getRelatedAnimeList(anime).flatMap { it.animes }

    fun String.stripKeywordForRelatedAnimes(): List<String> =
        lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.length > 1 }
            .distinct()

    suspend fun getRelatedAnimeListBySearch(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val keywords = anime.title.stripKeywordForRelatedAnimes()
        if (keywords.isEmpty()) {
            pushResults("Related" to emptyList(), true)
            return
        }

        keywords.forEachIndexed { index, keyword ->
            val page = try {
                getSearchAnime(1, keyword, getFilterList())
            } catch (_: Throwable) {
                AnimesPage(emptyList(), false)
            }
            pushResults(
                keyword to page.animes.filterNot { it.url == anime.url },
                index == keywords.lastIndex,
            )
        }
    }

    @Deprecated("Use the suspend API instead")
    fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        Observable.error(UnsupportedOperationException("Popular anime is not supported"))

    @Deprecated("Use the suspend API instead")
    fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> =
        Observable.error(UnsupportedOperationException("Search is not supported"))

    @Deprecated("Use the suspend API instead")
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        Observable.error(UnsupportedOperationException("Latest updates are not supported"))
}

interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}

interface ConfigurableAnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
