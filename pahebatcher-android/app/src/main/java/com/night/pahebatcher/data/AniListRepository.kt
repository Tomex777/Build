package com.night.pahebatcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class AniListRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val gql = """
            query SearchAnime(${D}search: String) {
              Page(page: 1, perPage: 30) {
                media(type: ANIME, search: ${D}search, sort: SEARCH_MATCH) {
                  id
                  title { romaji english native }
                  synonyms
                  coverImage { extraLarge large }
                  format
                  status
                  episodes
                  seasonYear
                  averageScore
                  genres
                  description(asHtml: false)
                }
              }
            }
        """.trimIndent()

        val data = execute(gql, JSONObject().put("search", clean))
        val media = data
            .optJSONObject("Page")
            ?.optJSONArray("media")
            ?: return@withContext emptyList()

        buildList {
            for (index in 0 until media.length()) {
                media.optJSONObject(index)?.let(::parseMedia)?.let(::add)
            }
        }
    }

    suspend fun recentlyAired(): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000L
        val from = now - (7L * 24L * 60L * 60L)

        val gql = """
            query RecentlyAired(${D}from: Int, ${D}to: Int) {
              Page(page: 1, perPage: 35) {
                airingSchedules(
                  airingAt_greater: ${D}from
                  airingAt_lesser: ${D}to
                  sort: TIME_DESC
                ) {
                  episode
                  airingAt
                  media {
                    id
                    title { romaji english native }
                    coverImage { extraLarge large }
                    format
                    status
                    episodes
                    seasonYear
                    averageScore
                    genres
                    description(asHtml: false)
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject()
            .put("from", from)
            .put("to", now)

        val data = execute(gql, variables)
        val schedules = data
            .optJSONObject("Page")
            ?.optJSONArray("airingSchedules")
            ?: return@withContext emptyList()

        val seen = hashSetOf<Int>()
        buildList {
            for (index in 0 until schedules.length()) {
                val schedule = schedules.optJSONObject(index) ?: continue
                val media = schedule.optJSONObject("media") ?: continue
                val id = media.optInt("id", 0)
                if (id <= 0 || !seen.add(id)) continue

                val episode = schedule.optInt("episode", 0)
                val item = parseMedia(media) ?: continue
                add(
                    item.copy(
                        catalogNote = if (episode > 0) "Episode $episode aired" else "Recently aired",
                    )
                )
            }
        }
    }

    suspend fun enrichAvailable(
        sourceItems: List<AnimeSearchResult>,
    ): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val items = sourceItems.take(20)
        if (items.isEmpty()) return@withContext emptyList()

        val variableDefs = items.indices.joinToString(", ") { index -> "$q$index: String" }
        val fields = """
            id
            title { romaji english native }
            synonyms
            coverImage { extraLarge large }
            format
            status
            episodes
            seasonYear
            averageScore
            genres
            description(asHtml: false)
        """.trimIndent()

        val selections = items.indices.joinToString("\n") { index ->
            "m$index: Media(type: ANIME, search: $q$index) { $fields }"
        }
        val gql = "query AvailableAnime($variableDefs) {\n$selections\n}"

        val variables = JSONObject()
        items.forEachIndexed { index, item ->
            variables.put("q$index", item.title)
        }

        val data = execute(gql, variables)
        items.mapIndexed { index, source ->
            val media = data.optJSONObject("m$index")
            val enriched = media?.let(::parseMedia)
            if (enriched == null) {
                source
            } else {
                enriched.copy(
                    session = source.session,
                    animeId = source.animeId,
                    sourceQueries = (enriched.sourceQueries + source.title).distinct(),
                    catalogNote = source.catalogNote.ifBlank { "Available now" },
                )
            }
        }
    }

    private fun execute(query: String, variables: JSONObject): JSONObject {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)

        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "PaheBatcher-Android/0.2")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("AniList HTTP ${response.code}")
            }
            val root = JSONObject(body)
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val message = errors.optJSONObject(0)?.optString("message").orEmpty()
                throw IOException(message.ifBlank { "AniList returned an error" })
            }
            return root.optJSONObject("data")
                ?: throw IOException("AniList returned no data")
        }
    }

    private fun parseMedia(media: JSONObject): AnimeSearchResult? {
        val id = media.optInt("id", 0)
        if (id <= 0) return null

        val title = media.optJSONObject("title")
        val romaji = title.cleanString("romaji")
        val english = title.cleanString("english")
        val native = title.cleanString("native")
        val displayTitle = english.ifBlank { romaji.ifBlank { native } }
        if (displayTitle.isBlank()) return null

        val cover = media.optJSONObject("coverImage")
        val poster = cover.cleanString("extraLarge")
            .ifBlank { cover.cleanString("large") }

        val genresArray = media.optJSONArray("genres")
        val genres = buildList {
            if (genresArray != null) {
                for (index in 0 until genresArray.length()) {
                    genresArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

        val rawDescription = media.cleanString("description")
        val description = if (rawDescription.isBlank()) {
            ""
        } else {
            Jsoup.parse(rawDescription).text().trim()
        }

        val synonymsArray = media.optJSONArray("synonyms")
        val synonyms = buildList {
            if (synonymsArray != null) {
                for (index in 0 until synonymsArray.length()) {
                    synonymsArray.optString(index)
                        .takeIf { it.isNotBlank() && !it.equals("null", true) }
                        ?.let(::add)
                }
            }
        }

        val sourceQueries = (listOf(english, romaji, native) + synonyms)
            .filter { it.isNotBlank() }
            .distinct()

        return AnimeSearchResult(
            session = "",
            title = displayTitle,
            poster = poster,
            type = formatLabel(media.cleanString("format")),
            episodes = media.optInt("episodes", 0),
            status = statusLabel(media.cleanString("status")),
            animeId = null,
            aniListId = id,
            sourceQueries = sourceQueries,
            description = description,
            genres = genres,
            year = media.optInt("seasonYear", 0).takeIf { it > 0 },
            score = media.optInt("averageScore", 0).takeIf { it > 0 },
        )
    }

    private fun JSONObject?.cleanString(key: String): String {
        if (this == null || isNull(key)) return ""
        return optString(key, "")
            .takeUnless { it.equals("null", ignoreCase = true) }
            .orEmpty()
            .trim()
    }

    private fun formatLabel(value: String): String = when (value.uppercase(Locale.US)) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> value.replace('_', ' ').lowercase(Locale.US)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun statusLabel(value: String): String = when (value.uppercase(Locale.US)) {
        "RELEASING" -> "Airing"
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> value.replace('_', ' ').lowercase(Locale.US)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    companion object {
        private const val API_URL = "https://graphql.anilist.co"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val D = "$"
    }
}
