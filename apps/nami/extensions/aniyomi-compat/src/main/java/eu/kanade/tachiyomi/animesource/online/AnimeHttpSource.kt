/*
 * Runtime compatibility implementation derived from Aniyomi AnimeHttpSource.
 * Licensed under Apache-2.0.
 */
package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

abstract class AnimeHttpSource : AnimeCatalogueSource {
    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = name.lowercase() + "/" + lang + "/" + versionId
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7)
            .map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder =
        Headers.Builder().add("User-Agent", network.defaultUserAgentProvider())

    override fun toString(): String = name + " (" + lang.uppercase() + ")"

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        client.newCall(popularAnimeRequest(page)).awaitSuccess().use(::popularAnimeParse)

    protected open fun popularAnimeRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun popularAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = client.newCall(searchAnimeRequest(page, query, filters))
        .awaitSuccess()
        .use(::searchAnimeParse)

    protected open fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request = throw UnsupportedOperationException()

    protected open fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        client.newCall(latestUpdatesRequest(page)).awaitSuccess().use(::latestUpdatesParse)

    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        client.newCall(animeDetailsRequest(anime)).awaitSuccess().use { response ->
            animeDetailsParse(response).apply { initialized = true }
        }

    open fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        client.newCall(episodeListRequest(anime)).awaitSuccess().use(::episodeListParse)

    protected open fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        client.newCall(seasonListRequest(anime)).awaitSuccess().use(::seasonListParse)

    protected open fun seasonListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun seasonListParse(response: Response): List<SAnime> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        client.newCall(hosterListRequest(episode)).awaitSuccess().use(::hosterListParse)

    protected open fun hosterListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun hosterListParse(response: Response): List<Hoster> = throw UnsupportedOperationException()

    override suspend fun getVideoList(hoster: Hoster): List<Video> =
        client.newCall(videoListRequest(hoster)).awaitSuccess().use { response ->
            videoListParse(response, hoster)
        }

    protected open fun videoListRequest(hoster: Hoster): Request = GET(hoster.hosterUrl, headers)

    protected open fun videoListParse(
        response: Response,
        hoster: Hoster,
    ): List<Video> = hoster.videoList ?: throw UnsupportedOperationException()

    open suspend fun resolveVideo(video: Video): Video? = video

    open fun List<Hoster>.sortHosters(): List<Hoster> = this

    protected open fun List<Video>.sortVideos(): List<Video> = this

    fun sortVideosForNami(videos: List<Video>): List<Video> = with(this) {
        videos.sortVideos()
    }

    @Deprecated("Use resolveVideo instead")
    open suspend fun getVideoUrl(video: Video): String = resolveVideo(video)?.videoUrl ?: video.videoUrl

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    open fun getAnimeUrl(anime: SAnime): String = absoluteUrl(anime.url)

    open fun getEpisodeUrl(episode: SEpisode): String = absoluteUrl(episode.url)

    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) = Unit

    private fun absoluteUrl(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) value else baseUrl + value
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            buildString {
                append(uri.rawPath.orEmpty())
                uri.rawQuery?.let { append('?').append(it) }
                uri.rawFragment?.let { append('#').append(it) }
            }
        } catch (_: URISyntaxException) {
            orig
        }
    }
}
