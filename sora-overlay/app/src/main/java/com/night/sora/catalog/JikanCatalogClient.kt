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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Sora-owned Anime/Manga metadata provider.
 *
 * Jikan is deliberately part of Core rather than an extension. It tells Sora
 * what titles exist and supplies catalog metadata. External extensions remain
 * responsible for resolving watch/read sources.
 */
class JikanCatalogClient {
    data class CatalogItem(
        val id: String,
        val type: ContentType,
        val title: String,
        val subtitle: String,
        val artworkUrl: String?,
    )

    data class CatalogDetails(
        val id: String,
        val type: ContentType,
        val title: String,
        val alternateTitle: String,
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

    fun browse(type: ContentType, feed: String = "", callback: (Result<List<CatalogItem>>) -> Unit) {
        requireJikanType(type)
        val cleanFeed = feed.trim().lowercase()
        val endpoint = when (type) {
            ContentType.ANIME -> when (cleanFeed) {
                "popular" -> "$BASE_URL/top/anime?filter=bypopularity&limit=25&sfw=true"
                "season" -> "$BASE_URL/seasons/now?limit=25&sfw=true"
                "upcoming" -> "$BASE_URL/top/anime?filter=upcoming&limit=25&sfw=true"
                "top" -> "$BASE_URL/top/anime?limit=25&sfw=true"
                else -> "$BASE_URL/top/anime?filter=airing&limit=25&sfw=true"
            }
            ContentType.MANGA -> when (cleanFeed) {
                "popular" -> "$BASE_URL/top/manga?filter=bypopularity&limit=25"
                "recent" -> "$BASE_URL/manga?order_by=start_date&sort=desc&limit=25"
                "upcoming" -> "$BASE_URL/top/manga?filter=upcoming&limit=25"
                "top" -> "$BASE_URL/top/manga?limit=25"
                else -> "$BASE_URL/top/manga?filter=publishing&limit=25"
            }
            else -> error("Unsupported Jikan type: $type")
        }
        request(endpoint) { result ->
            callback(result.mapCatching { root -> parseList(root, type) })
        }
    }

    fun search(type: ContentType, query: String, callback: (Result<List<CatalogItem>>) -> Unit) {
        requireJikanType(type)
        val q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val endpoint = when (type) {
            ContentType.ANIME -> "$BASE_URL/anime?q=$q&limit=25&sfw=true&order_by=popularity"
            ContentType.MANGA -> "$BASE_URL/manga?q=$q&limit=25&order_by=popularity"
            else -> error("Unsupported Jikan type: $type")
        }
        request(endpoint) { result ->
            callback(result.mapCatching { root -> parseList(root, type) })
        }
    }

    fun details(type: ContentType, id: String, callback: (Result<CatalogDetails>) -> Unit) {
        requireJikanType(type)
        // Jikan's /full object carries the same core metadata plus the exact
        // MAL relationship graph. Using it keeps details and relationship
        // truth anchored to one canonical response instead of extra calls.
        val endpoint = "$BASE_URL/${pathFor(type)}/${id.trim()}/full"
        request(endpoint) { result ->
            callback(result.mapCatching { root -> parseDetails(root.getJSONObject("data"), type) })
        }
    }

    fun counterpart(type: ContentType, id: String, callback: (Result<CatalogItem?>) -> Unit) {
        requireJikanType(type)
        val opposite = if (type == ContentType.ANIME) ContentType.MANGA else ContentType.ANIME
        val cleanId = id.trim()

        fun finish(related: Pair<Int, String>?) {
            if (related == null) {
                callback(Result.success(null))
                return
            }
            details(opposite, related.first.toString()) { detailResult ->
                callback(
                    detailResult.map { detail ->
                        CatalogItem(
                            id = detail.id,
                            type = opposite,
                            title = detail.title.ifBlank { related.second },
                            subtitle = detail.subtitle,
                            artworkUrl = detail.artworkUrl,
                        )
                    }
                )
            }
        }

        // Prefer the canonical full object because it already includes Jikan's
        // MAL relations. The dedicated relations endpoint is only a fallback
        // for transient/full-endpoint failures. Both paths accept Adaptation
        // only; Sora never guesses counterparts by title.
        val fullEndpoint = "$BASE_URL/${pathFor(type)}/$cleanId/full"
        request(fullEndpoint) { fullResult ->
            fullResult.fold(
                onSuccess = { root ->
                    val item = root.optJSONObject("data") ?: JSONObject()
                    val relations = item.optJSONArray("relations") ?: JSONArray()
                    finish(findOppositeRelation(relations, opposite))
                },
                onFailure = { fullError ->
                    val relationsEndpoint = "$BASE_URL/${pathFor(type)}/$cleanId/relations"
                    request(relationsEndpoint) { relationsResult ->
                        relationsResult.fold(
                            onSuccess = { root ->
                                finish(findOppositeRelation(root.optJSONArray("data") ?: JSONArray(), opposite))
                            },
                            onFailure = { relationsError ->
                                relationsError.addSuppressed(fullError)
                                callback(Result.failure(relationsError))
                            },
                        )
                    }
                },
            )
        }
    }

    private fun findOppositeRelation(data: JSONArray, opposite: ContentType): Pair<Int, String>? {
        val targetType = if (opposite == ContentType.ANIME) "anime" else "manga"
        for (i in 0 until data.length()) {
            val relation = data.optJSONObject(i) ?: continue
            if (!relation.optString("relation").equals("Adaptation", ignoreCase = true)) continue
            val entries = relation.optJSONArray("entry") ?: continue
            for (j in 0 until entries.length()) {
                val entry = entries.optJSONObject(j) ?: continue
                if (!entry.optString("type").equals(targetType, ignoreCase = true)) continue
                val id = entry.optInt("mal_id", -1)
                if (id > 0) return id to entry.optString("name")
            }
        }
        return null
    }

    private fun parseList(root: JSONObject, type: ContentType): List<CatalogItem> {
        val data = root.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                parseItem(item, type)?.let(::add)
            }
        }.distinctBy { it.id }
    }

    private fun parseItem(item: JSONObject, type: ContentType): CatalogItem? {
        val id = item.optInt("mal_id", -1)
        if (id <= 0) return null
        val title = preferredTitle(item)
        if (title.isBlank()) return null
        return CatalogItem(
            id = id.toString(),
            type = type,
            title = title,
            subtitle = subtitle(item, type),
            artworkUrl = image(item),
        )
    }

    private fun parseDetails(item: JSONObject, type: ContentType): CatalogDetails {
        val id = item.optInt("mal_id", -1).toString()
        val genres = buildList {
            val array = item.optJSONArray("genres") ?: JSONArray()
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val title = preferredTitle(item)
        val originalTitle = item.optString("title").takeUnless { it == "null" }.orEmpty().trim()
        val japaneseTitle = item.optString("title_japanese").takeUnless { it == "null" }.orEmpty().trim()
        val alternateTitle = listOf(originalTitle, japaneseTitle)
            .firstOrNull { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            .orEmpty()
        val directYear = item.optInt("year", 0).takeIf { it > 0 }
        val publishedYear = item.optJSONObject("published")
            ?.optJSONObject("prop")
            ?.optJSONObject("from")
            ?.optInt("year", 0)
            ?.takeIf { it > 0 }
        return CatalogDetails(
            id = id,
            type = type,
            title = title,
            alternateTitle = alternateTitle,
            subtitle = subtitle(item, type),
            synopsis = item.optString("synopsis").takeUnless { it == "null" }.orEmpty(),
            artworkUrl = image(item),
            score = item.optDouble("score").takeUnless { it.isNaN() || it <= 0.0 },
            status = item.optString("status").takeUnless { it == "null" }.orEmpty(),
            genres = genres,
            year = directYear ?: publishedYear,
            season = item.optString("season").takeUnless { it == "null" }.orEmpty(),
            episodes = item.optInt("episodes", 0).takeIf { it > 0 },
            chapters = item.optInt("chapters", 0).takeIf { it > 0 },
            volumes = item.optInt("volumes", 0).takeIf { it > 0 },
        )
    }

    private fun preferredTitle(item: JSONObject): String {
        val english = item.optString("title_english").takeUnless { it == "null" }.orEmpty().trim()
        return english.ifBlank { item.optString("title").trim() }
    }

    private fun subtitle(item: JSONObject, type: ContentType): String {
        val parts = mutableListOf<String>()
        val score = item.optDouble("score")
        if (!score.isNaN() && score > 0.0) parts += String.format("%.1f", score)
        item.optString("type").takeUnless { it.isBlank() || it == "null" }?.let(parts::add)
        when (type) {
            ContentType.ANIME -> {
                val episodes = item.optInt("episodes", 0)
                if (episodes > 0) parts += "$episodes eps"
            }
            ContentType.MANGA -> {
                val chapters = item.optInt("chapters", 0)
                if (chapters > 0) parts += "$chapters ch"
            }
            else -> Unit
        }
        return parts.joinToString(" · ")
    }

    private fun image(item: JSONObject): String? {
        val images = item.optJSONObject("images") ?: return null
        val jpg = images.optJSONObject("jpg") ?: return null
        return listOf("large_image_url", "image_url", "small_image_url")
            .firstNotNullOfOrNull { key ->
                jpg.optString(key).takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
    }

    private fun request(url: String, callback: (Result<JSONObject>) -> Unit) {
        executor.execute {
            val result = runCatching { requestJsonWithRetry(url) }
            main.post { callback(result) }
        }
    }

    private fun requestJsonWithRetry(url: String): JSONObject {
        var lastError: Throwable? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                throttle()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Sora-Android/0.4")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.let { input ->
                    BufferedReader(InputStreamReader(input)).use { reader -> reader.readText() }
                }.orEmpty()
                val retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                connection.disconnect()

                if (code in 200..299) return JSONObject(body)

                val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                if (!retryable || attempt == MAX_REQUEST_ATTEMPTS - 1) {
                    error("Jikan HTTP $code")
                }

                lastError = IllegalStateException("Jikan HTTP $code")
                val serverDelayMs = retryAfterSeconds?.times(1_000L)
                Thread.sleep(serverDelayMs ?: retryDelayMs(attempt))
            } catch (t: Throwable) {
                lastError = t
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) Thread.sleep(retryDelayMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("Jikan request failed")
    }

    private fun retryDelayMs(attempt: Int): Long = when (attempt) {
        0 -> 900L
        1 -> 1_800L
        else -> 3_000L
    }

    private fun throttle() {
        synchronized(rateLock) {
            val now = System.currentTimeMillis()
            val wait = MIN_REQUEST_GAP_MS - (now - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun requireJikanType(type: ContentType) {
        require(type == ContentType.ANIME || type == ContentType.MANGA) {
            "Jikan supports only Anime/Manga in Sora Core"
        }
    }

    private fun pathFor(type: ContentType) = if (type == ContentType.ANIME) "anime" else "manga"

    companion object {
        const val ORIGIN_PACKAGE = "core.catalog.jikan"
        const val ANIME_SOURCE = "jikan.anime"
        const val MANGA_SOURCE = "jikan.manga"

        fun sourceFor(type: ContentType): String = if (type == ContentType.ANIME) ANIME_SOURCE else MANGA_SOURCE
        fun isJikan(packageName: String): Boolean = packageName == ORIGIN_PACKAGE

        private const val BASE_URL = "https://api.jikan.moe/v4"
        private const val MIN_REQUEST_GAP_MS = 380L
        private const val MAX_REQUEST_ATTEMPTS = 3
        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        private val rateLock = Any()
        private var lastRequestAt = 0L
    }
}
