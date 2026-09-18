package com.night.sora.catalog

import android.os.Handler
import android.os.Looper
import com.night.sora.model.ContentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sora-owned Anime/Manga catalog client backed by AniList GraphQL.
 * Catalog metadata stays in Core; external extensions still own watch/read data.
 */
class AniListCatalogClient {
    data class CatalogItem(
        val id: String,
        val malId: Int?,
        val type: ContentType,
        val title: String,
        val englishTitle: String,
        val romajiTitle: String,
        val aliases: List<String>,
        val subtitle: String,
        val artworkUrl: String?,
    )

    data class CatalogDetails(
        val id: String,
        val malId: Int?,
        val type: ContentType,
        val title: String,
        val alternateTitle: String,
        val aliases: List<String>,
        val subtitle: String,
        val synopsis: String,
        val artworkUrl: String?,
        val score: Double?,
        val status: String,
        val genres: List<String>,
        val year: Int?,
        val season: String,
        val episodes: Int?,
        val chapters: Int?,
        val volumes: Int?,
    )

    private data class DiscoverySnapshot(
        val fetchedAt: Long,
        val feeds: Map<String, List<CatalogItem>>,
    )

    private val cacheLock = Any()
    private val discoveryCache = mutableMapOf<ContentType, DiscoverySnapshot>()
    private val animeSearchGeneration = AtomicInteger()
    private val mangaSearchGeneration = AtomicInteger()

    fun browse(type: ContentType, feed: String = "", callback: (Result<List<CatalogItem>>) -> Unit) {
        requireCatalogType(type)
        execute(callback) {
            val key = normalizeFeed(type, feed)
            cachedFeed(type, key)?.let { return@execute it }

            val fresh = fetchDiscovery(type)
            synchronized(cacheLock) {
                discoveryCache[type] = DiscoverySnapshot(System.currentTimeMillis(), fresh)
            }
            fresh[key].orEmpty()
        }
    }

    fun search(type: ContentType, query: String, callback: (Result<List<CatalogItem>>) -> Unit) {
        requireCatalogType(type)
        val clean = query.trim()
        if (clean.isBlank()) {
            callback(Result.success(emptyList()))
            return
        }

        val generationRef = if (type == ContentType.ANIME) animeSearchGeneration else mangaSearchGeneration
        val generation = generationRef.incrementAndGet()
        execute(callback) {
            if (generation != generationRef.get()) return@execute emptyList()
            val mediaType = typeLiteral(type)
            val quoted = JSONObject.quote(clean)
            val graphQl = """
                query {
                  Page(page: 1, perPage: 25) {
                    media(search: $quoted, type: $mediaType, isAdult: false, sort: [SEARCH_MATCH, POPULARITY_DESC]) {
                      $CARD_FIELDS
                    }
                  }
                }
            """.trimIndent()
            val root = requestGraphQl(graphQl)
            if (generation != generationRef.get()) return@execute emptyList()
            parsePage(root.getJSONObject("data").optJSONObject("Page"), type)
        }
    }

    fun details(type: ContentType, id: String, callback: (Result<CatalogDetails>) -> Unit) =
        detailsInternal(type, id, byMalId = false, callback)

    fun detailsByMalId(type: ContentType, malId: String, callback: (Result<CatalogDetails>) -> Unit) =
        detailsInternal(type, malId, byMalId = true, callback)

    fun counterpart(type: ContentType, id: String, callback: (Result<CatalogItem?>) -> Unit) =
        counterpartInternal(type, id, byMalId = false, callback)

    fun counterpartByMalId(type: ContentType, malId: String, callback: (Result<CatalogItem?>) -> Unit) =
        counterpartInternal(type, malId, byMalId = true, callback)

    private fun detailsInternal(
        type: ContentType,
        rawId: String,
        byMalId: Boolean,
        callback: (Result<CatalogDetails>) -> Unit,
    ) {
        requireCatalogType(type)
        val id = rawId.trim().toIntOrNull()
        if (id == null || id <= 0) {
            callback(Result.failure(IllegalArgumentException("Invalid catalog media id")))
            return
        }

        execute(callback) {
            val typeArg = typeLiteral(type)
            val idArg = if (byMalId) "idMal: $id" else "id: $id"
            val graphQl = """
                query {
                  Media($idArg, type: $typeArg) {
                    $DETAIL_FIELDS
                  }
                }
            """.trimIndent()
            val media = requestGraphQl(graphQl).getJSONObject("data").optJSONObject("Media")
                ?: error("AniList returned no media details")
            parseDetails(media, type)
        }
    }

    private fun counterpartInternal(
        type: ContentType,
        rawId: String,
        byMalId: Boolean,
        callback: (Result<CatalogItem?>) -> Unit,
    ) {
        requireCatalogType(type)
        val id = rawId.trim().toIntOrNull()
        if (id == null || id <= 0) {
            callback(Result.failure(IllegalArgumentException("Invalid catalog media id")))
            return
        }

        execute(callback) {
            val opposite = if (type == ContentType.ANIME) ContentType.MANGA else ContentType.ANIME
            val expectedRelation = if (type == ContentType.ANIME) "SOURCE" else "ADAPTATION"
            val typeArg = typeLiteral(type)
            val idArg = if (byMalId) "idMal: $id" else "id: $id"
            val graphQl = """
                query {
                  Media($idArg, type: $typeArg) {
                    relations {
                      edges {
                        relationType
                        node {
                          $CARD_FIELDS
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            val media = requestGraphQl(graphQl).getJSONObject("data").optJSONObject("Media")
                ?: return@execute null
            val edges = media.optJSONObject("relations")?.optJSONArray("edges") ?: JSONArray()

            for (index in 0 until edges.length()) {
                val edge = edges.optJSONObject(index) ?: continue
                if (edge.optString("relationType") != expectedRelation) continue
                val node = edge.optJSONObject("node") ?: continue
                if (!node.optString("type").equals(typeLiteral(opposite), ignoreCase = true)) continue
                return@execute parseItem(node, opposite)
            }
            null
        }
    }

    private fun fetchDiscovery(type: ContentType): Map<String, List<CatalogItem>> {
        val graphQl = when (type) {
            ContentType.ANIME -> {
                val (season, year) = currentSeason()
                """
                    query {
                      current: Page(page: 1, perPage: 25) {
                        media(type: ANIME, status: RELEASING, isAdult: false, sort: [POPULARITY_DESC]) { $CARD_FIELDS }
                      }
                      popular: Page(page: 1, perPage: 25) {
                        media(type: ANIME, isAdult: false, sort: [POPULARITY_DESC]) { $CARD_FIELDS }
                      }
                      season: Page(page: 1, perPage: 25) {
                        media(type: ANIME, season: $season, seasonYear: $year, isAdult: false, sort: [POPULARITY_DESC, SCORE_DESC]) { $CARD_FIELDS }
                      }
                      upcoming: Page(page: 1, perPage: 25) {
                        media(type: ANIME, status: NOT_YET_RELEASED, isAdult: false, sort: [POPULARITY_DESC]) { $CARD_FIELDS }
                      }
                      top: Page(page: 1, perPage: 25) {
                        media(type: ANIME, isAdult: false, sort: [SCORE_DESC, POPULARITY_DESC]) { $CARD_FIELDS }
                      }
                    }
                """.trimIndent()
            }
            ContentType.MANGA -> """
                query {
                  current: Page(page: 1, perPage: 25) {
                    media(type: MANGA, status: RELEASING, isAdult: false, sort: [POPULARITY_DESC]) { $CARD_FIELDS }
                  }
                  popular: Page(page: 1, perPage: 25) {
                    media(type: MANGA, isAdult: false, sort: [POPULARITY_DESC]) { $CARD_FIELDS }
                  }
                  top: Page(page: 1, perPage: 25) {
                    media(type: MANGA, isAdult: false, sort: [SCORE_DESC, POPULARITY_DESC]) { $CARD_FIELDS }
                  }
                  recent: Page(page: 1, perPage: 25) {
                    media(type: MANGA, status: RELEASING, isAdult: false, sort: [START_DATE_DESC]) { $CARD_FIELDS }
                  }
                }
            """.trimIndent()
            else -> error("Unsupported AniList type: $type")
        }

        val data = requestGraphQl(graphQl).getJSONObject("data")
        return if (type == ContentType.ANIME) {
            mapOf(
                "current" to parsePage(data.optJSONObject("current"), type),
                "popular" to parsePage(data.optJSONObject("popular"), type),
                "season" to parsePage(data.optJSONObject("season"), type),
                "upcoming" to parsePage(data.optJSONObject("upcoming"), type),
                "top" to parsePage(data.optJSONObject("top"), type),
            )
        } else {
            mapOf(
                "current" to parsePage(data.optJSONObject("current"), type),
                "popular" to parsePage(data.optJSONObject("popular"), type),
                "top" to parsePage(data.optJSONObject("top"), type),
                "recent" to parsePage(data.optJSONObject("recent"), type),
            )
        }
    }

    private fun parsePage(page: JSONObject?, type: ContentType): List<CatalogItem> {
        val media = page?.optJSONArray("media") ?: JSONArray()
        return buildList {
            for (index in 0 until media.length()) {
                media.optJSONObject(index)?.let { parseItem(it, type) }?.let(::add)
            }
        }.distinctBy { it.id }
    }

    private fun parseItem(item: JSONObject, type: ContentType): CatalogItem? {
        val id = item.optInt("id", -1)
        if (id <= 0) return null
        val titleObject = item.optJSONObject("title") ?: JSONObject()
        val english = clean(titleObject.optString("english"))
        val romaji = clean(titleObject.optString("romaji"))
        val native = clean(titleObject.optString("native"))
        val preferred = clean(titleObject.optString("userPreferred"))
        val title = english.ifBlank { romaji.ifBlank { preferred.ifBlank { native } } }
        if (title.isBlank()) return null

        return CatalogItem(
            id = id.toString(),
            malId = item.optInt("idMal", 0).takeIf { it > 0 },
            type = type,
            title = title,
            englishTitle = english,
            romajiTitle = romaji,
            aliases = aliases(item, title, english, romaji, native, preferred),
            subtitle = subtitle(item, type),
            artworkUrl = image(item),
        )
    }

    private fun parseDetails(item: JSONObject, type: ContentType): CatalogDetails {
        val base = parseItem(item, type) ?: error("AniList returned invalid media details")
        val titleObject = item.optJSONObject("title") ?: JSONObject()
        val native = clean(titleObject.optString("native"))
        val alternate = (listOf(base.romajiTitle, native) + base.aliases)
            .firstOrNull { it.isNotBlank() && !it.equals(base.title, ignoreCase = true) }
            .orEmpty()
        val genres = mutableListOf<String>()
        val genreArray = item.optJSONArray("genres") ?: JSONArray()
        for (index in 0 until genreArray.length()) {
            clean(genreArray.optString(index)).takeIf(String::isNotBlank)?.let(genres::add)
        }
        val year = item.optInt("seasonYear", 0).takeIf { it > 0 }
            ?: item.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }

        return CatalogDetails(
            id = base.id,
            malId = base.malId,
            type = type,
            title = base.title,
            alternateTitle = alternate,
            aliases = base.aliases,
            subtitle = base.subtitle,
            synopsis = cleanDescription(item.optString("description")),
            artworkUrl = base.artworkUrl,
            score = item.optInt("averageScore", 0).takeIf { it > 0 }?.div(10.0),
            status = humanStatus(item.optString("status"), type),
            genres = genres,
            year = year,
            season = humanEnum(item.optString("season")),
            episodes = item.optInt("episodes", 0).takeIf { it > 0 },
            chapters = item.optInt("chapters", 0).takeIf { it > 0 },
            volumes = item.optInt("volumes", 0).takeIf { it > 0 },
        )
    }

    private fun aliases(
        item: JSONObject,
        title: String,
        english: String,
        romaji: String,
        native: String,
        preferred: String,
    ): List<String> {
        val values = mutableListOf(english, romaji, native, preferred)
        val synonyms = item.optJSONArray("synonyms") ?: JSONArray()
        for (index in 0 until synonyms.length()) values += clean(synonyms.optString(index))
        return values.filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }

    private fun subtitle(item: JSONObject, type: ContentType): String {
        val parts = mutableListOf<String>()
        item.optInt("averageScore", 0).takeIf { it > 0 }?.let { parts += String.format("%.1f", it / 10.0) }
        humanEnum(item.optString("format")).takeIf(String::isNotBlank)?.let(parts::add)
        if (type == ContentType.ANIME) {
            item.optInt("episodes", 0).takeIf { it > 0 }?.let { parts += "$it eps" }
        } else if (type == ContentType.MANGA) {
            item.optInt("chapters", 0).takeIf { it > 0 }?.let { parts += "$it ch" }
        }
        return parts.joinToString(" · ")
    }

    private fun image(item: JSONObject): String? {
        val cover = item.optJSONObject("coverImage") ?: return null
        return listOf("extraLarge", "large", "medium").firstNotNullOfOrNull { key ->
            clean(cover.optString(key)).takeIf { it.startsWith("https://") || it.startsWith("http://") }
        }
    }

    private fun cleanDescription(value: String): String =
        clean(value)
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    private fun humanStatus(raw: String, type: ContentType): String = when (raw) {
        "RELEASING" -> if (type == ContentType.ANIME) "Currently Airing" else "Publishing"
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Not Yet Released"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> humanEnum(raw)
    }

    private fun humanEnum(raw: String): String =
        clean(raw).lowercase().replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

    private fun clean(value: String): String = value.takeUnless { it == "null" }.orEmpty().trim()

    private fun normalizeFeed(type: ContentType, feed: String): String {
        val cleanFeed = feed.trim().lowercase()
        return when (type) {
            ContentType.ANIME -> cleanFeed.takeIf { it in setOf("popular", "season", "upcoming", "top") } ?: "current"
            ContentType.MANGA -> cleanFeed.takeIf { it in setOf("popular", "top", "recent") } ?: "current"
            else -> "current"
        }
    }

    private fun cachedFeed(type: ContentType, feed: String): List<CatalogItem>? = synchronized(cacheLock) {
        val snapshot = discoveryCache[type] ?: return@synchronized null
        if (System.currentTimeMillis() - snapshot.fetchedAt > DISCOVERY_MEMORY_TTL_MS) return@synchronized null
        snapshot.feeds[feed]
    }

    private fun currentSeason(): Pair<String, Int> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val month = calendar.get(Calendar.MONTH) + 1
        val season = when (month) {
            in 1..3 -> "WINTER"
            in 4..6 -> "SPRING"
            in 7..9 -> "SUMMER"
            else -> "FALL"
        }
        return season to calendar.get(Calendar.YEAR)
    }

    private fun requestGraphQl(query: String): JSONObject {
        var lastError: Throwable? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                throttle()
                val connection = URL(BASE_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 14_000
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "Sora-Android/0.5")

                val body = JSONObject().put("query", query).toString().toByteArray(StandardCharsets.UTF_8)
                connection.outputStream.use { it.write(body) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.let { input ->
                    BufferedReader(InputStreamReader(input)).use { reader -> reader.readText() }
                }.orEmpty()
                val retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                connection.disconnect()

                if (code in 200..299) {
                    val root = JSONObject(responseBody)
                    val errors = root.optJSONArray("errors")
                    if ((errors == null || errors.length() == 0) && root.optJSONObject("data") != null) return root
                    val message = errors?.optJSONObject(0)?.optString("message").orEmpty()
                    error(message.ifBlank { "AniList returned an invalid GraphQL response" })
                }

                val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                if (!retryable || attempt == MAX_REQUEST_ATTEMPTS - 1) error("AniList HTTP $code")
                lastError = IllegalStateException("AniList HTTP $code")
                Thread.sleep(retryAfterSeconds?.times(1_000L) ?: retryDelayMs(attempt))
            } catch (t: Throwable) {
                lastError = t
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) Thread.sleep(retryDelayMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("AniList request failed")
    }

    private fun <T> execute(callback: (Result<T>) -> Unit, block: () -> T) {
        executor.execute {
            val result = runCatching(block)
            main.post { callback(result) }
        }
    }

    private fun retryDelayMs(attempt: Int): Long = if (attempt == 0) 1_200L else 2_400L

    private fun throttle() {
        synchronized(rateLock) {
            val wait = MIN_REQUEST_GAP_MS - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun requireCatalogType(type: ContentType) {
        require(type == ContentType.ANIME || type == ContentType.MANGA) {
            "AniList catalog supports only Anime/Manga in Sora Core"
        }
    }

    private fun typeLiteral(type: ContentType): String =
        if (type == ContentType.ANIME) "ANIME" else "MANGA"

    companion object {
        const val ORIGIN_PACKAGE = "core.catalog.anilist"
        const val ANIME_SOURCE = "anilist.anime"
        const val MANGA_SOURCE = "anilist.manga"
        const val LEGACY_JIKAN_ANIME_SOURCE = "jikan.anime"
        const val LEGACY_JIKAN_MANGA_SOURCE = "jikan.manga"

        fun sourceFor(type: ContentType): String =
            if (type == ContentType.ANIME) ANIME_SOURCE else MANGA_SOURCE

        fun isAniListSource(sourceId: String): Boolean =
            sourceId == ANIME_SOURCE || sourceId == MANGA_SOURCE

        fun isLegacyJikanSource(sourceId: String): Boolean =
            sourceId == LEGACY_JIKAN_ANIME_SOURCE || sourceId == LEGACY_JIKAN_MANGA_SOURCE

        private const val BASE_URL = "https://graphql.anilist.co"
        private const val MIN_REQUEST_GAP_MS = 1_250L
        private const val MAX_REQUEST_ATTEMPTS = 3
        private const val DISCOVERY_MEMORY_TTL_MS = 30_000L

        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        private val rateLock = Any()
        private var lastRequestAt = 0L

        private val CARD_FIELDS = """
            id
            idMal
            type
            title { userPreferred romaji english native }
            synonyms
            format
            status
            averageScore
            episodes
            chapters
            volumes
            coverImage { extraLarge large medium }
        """.trimIndent()

        private val DETAIL_FIELDS = """
            id
            idMal
            type
            title { userPreferred romaji english native }
            synonyms
            description(asHtml: false)
            format
            status
            averageScore
            episodes
            chapters
            volumes
            season
            seasonYear
            startDate { year month day }
            genres
            coverImage { extraLarge large medium }
        """.trimIndent()
    }
}
