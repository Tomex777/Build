package app.nami.source.jikan

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.source.NamiAnimeSource
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Native Nami source backed by Jikan's public anime catalogue API.
 *
 * Jikan provides catalogue metadata and episode listings, but no playable media. Capabilities
 * therefore explicitly disable stream and download actions.
 */
class JikanAnimeSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) : NamiAnimeSource {

    override val metadata = SourceMetadata(
        id = SOURCE_ID,
        name = "Jikan",
        language = "multi",
        origin = SourceOrigin.NATIVE_NAMI,
        homeUrl = "https://myanimelist.net/",
        capabilities = SourceCapabilities(
            searchable = true,
            browsable = false,
            details = true,
            episodes = true,
            streamable = false,
            downloadable = false,
        ),
    )

    override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
        if (query.isBlank()) return SourcePage(emptyList(), false)
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val root = getJson("/anime?q=$encoded&page=${page.coerceAtLeast(1)}&limit=25")
        val results = root["data"].arrayOrEmpty().mapNotNull { element ->
            val item = element.objectOrNull() ?: return@mapNotNull null
            val id = item.string("mal_id") ?: return@mapNotNull null
            AnimeSearchResult(
                ref = AnimeRef(SOURCE_ID, id),
                title = item.string("title_english")?.takeIf(String::isNotBlank)
                    ?: item.string("title") ?: return@mapNotNull null,
                coverUrl = item.imageUrl(),
                description = item.string("synopsis"),
            )
        }
        return SourcePage(
            items = results,
            hasNextPage = root["pagination"].objectOrNull()
                ?.get("has_next_page")?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    override suspend fun details(anime: AnimeRef): AnimeDetails {
        require(anime.sourceId == SOURCE_ID) { "Anime reference belongs to another source" }
        val id = anime.sourceAnimeId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid Jikan anime id")
        val item = getJson("/anime/$id/full")["data"].objectOrNull()
            ?: throw IllegalStateException("Jikan returned no anime details")
        val metadata = buildMap {
            item.string("type")?.let { put("Type", it) }
            item.string("status")?.let { put("Status", it) }
            item.string("episodes")?.let { put("Episodes", it) }
            item.string("score")?.let { put("Score", it) }
            item.string("year")?.let { put("Year", it) }
        }
        return AnimeDetails(
            ref = anime,
            title = item.string("title_english")?.takeIf(String::isNotBlank)
                ?: item.string("title") ?: anime.sourceAnimeId,
            coverUrl = item.imageUrl(),
            description = item.string("synopsis"),
            metadata = metadata,
            genres = item["genres"].arrayOrEmpty().mapNotNull {
                it.objectOrNull()?.string("name")
            },
            webUrl = item.string("url"),
        )
    }

    override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> {
        require(anime.sourceId == SOURCE_ID) { "Anime reference belongs to another source" }
        val id = anime.sourceAnimeId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid Jikan anime id")
        val root = getJson("/anime/$id/episodes?page=1")
        return root["data"].arrayOrEmpty().mapIndexedNotNull { index, element ->
            val episode = element.objectOrNull() ?: return@mapIndexedNotNull null
            val episodeId = episode.string("mal_id") ?: (index + 1).toString()
            val title = episode.string("title")?.takeIf(String::isNotBlank)
                ?: "Episode ${index + 1}"
            AnimeEpisode(
                ref = EpisodeRef(SOURCE_ID, anime.sourceAnimeId, episodeId),
                title = title,
                number = episode.string("mal_id")?.toDoubleOrNull(),
            )
        }
    }

    override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> {
        require(episode.sourceId == SOURCE_ID) { "Episode reference belongs to another source" }
        return emptyList()
    }

    private suspend fun getJson(path: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_BASE + path)
            .header("Accept", "application/json")
            .header("User-Agent", "Nami/0.1 (Android anime source)")
            .build()
        var attempt = 0
        var lastFailure: Exception? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val failure = IllegalStateException("Jikan request failed with HTTP ${response.code}")
                        if (response.code != 429 && response.code < 500) throw failure
                        lastFailure = failure
                    } else {
                        val body = response.body?.string()
                            ?: throw IllegalStateException("Jikan returned an empty response")
                        return@withContext JSON.parseToJsonElement(body).jsonObject
                    }
                }
            } catch (failure: IOException) {
                lastFailure = failure
            }
            if (attempt < MAX_ATTEMPTS) delay(attempt * RETRY_DELAY_MS)
        }
        throw lastFailure ?: IllegalStateException("Jikan request failed after retries")
    }

    private fun JsonObject.imageUrl(): String? =
        this["images"].objectOrNull()?.get("jpg").objectOrNull()?.string("image_url")

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value === JsonNull) return null
        return value.jsonPrimitive.contentOrNull
    }

    private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.arrayOrEmpty(): JsonArray =
        this as? JsonArray ?: JsonArray(emptyList())

    companion object {
        const val SOURCE_ID = "native:jikan"
        private const val API_BASE = "https://api.jikan.moe/v4"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_000L
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
