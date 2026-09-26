package com.night.sora.liveextension

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object LiveCatalog {
    private const val CACHE_MS = 5 * 60 * 1000L
    private const val TMDB_TOKEN_REQUIRED = "Movies require a TMDB API Read Access Token. Open More → Extensions → Sora Live Sources to configure it."
    private data class CacheEntry(val at: Long, val body: String)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun browse(sourceId: String, type: String, tmdbToken: String = ""): String = when (sourceId) {
        "live.jikan.anime" -> mapJikanList(get("https://api.jikan.moe/v4/top/anime?limit=25"), isManga = false)
        "live.jikan.manga" -> mapJikanList(get("https://api.jikan.moe/v4/top/manga?limit=25"), isManga = true)
        "live.itunes.movies", "live.tmdb.movies" -> mapTmdbMovies(tmdbGet("https://api.themoviedb.org/3/trending/movie/week?language=en-US&page=1", tmdbToken))
        "live.tvmaze.tv" -> mapTvSchedule(get("https://api.tvmaze.com/schedule?country=US"))
        "live.itunes.music" -> mapItunes(get(itunesSearch("top hits", "music", "song")), type = "music")
        else -> JSONArray().toString()
    }

    fun search(sourceId: String, query: String, tmdbToken: String = ""): String {
        val q = query.trim()
        if (q.isBlank()) return browse(sourceId, "", tmdbToken)
        return when (sourceId) {
            "live.jikan.anime" -> mapJikanList(get("https://api.jikan.moe/v4/anime?q=${enc(q)}&limit=25&sfw=true"), false)
            "live.jikan.manga" -> mapJikanList(get("https://api.jikan.moe/v4/manga?q=${enc(q)}&limit=25&sfw=true"), true)
            "live.itunes.movies", "live.tmdb.movies" -> mapTmdbMovies(
                tmdbGet("https://api.themoviedb.org/3/search/movie?query=${enc(q)}&include_adult=false&language=en-US&page=1", tmdbToken)
            )
            "live.tvmaze.tv" -> mapTvSearch(get("https://api.tvmaze.com/search/shows?q=${enc(q)}"))
            "live.itunes.music" -> mapItunes(get(itunesSearch(q, "music", "song")), "music")
            else -> JSONArray().toString()
        }
    }

    fun details(sourceId: String, id: String, tmdbToken: String = ""): String = when (sourceId) {
        "live.jikan.anime" -> mapJikanDetails(get("https://api.jikan.moe/v4/anime/${numericId(id)}/full"))
        "live.jikan.manga" -> mapJikanDetails(get("https://api.jikan.moe/v4/manga/${numericId(id)}/full"))
        "live.itunes.movies", "live.tmdb.movies" -> mapTmdbDetails(
            tmdbGet("https://api.themoviedb.org/3/movie/${numericId(id)}?language=en-US", tmdbToken)
        )
        "live.tvmaze.tv" -> mapTvDetails(get("https://api.tvmaze.com/shows/${numericId(id)}"))
        "live.itunes.music" -> mapItunesDetails(get("https://itunes.apple.com/lookup?id=${numericId(id)}&entity=song"), "music")
        else -> JSONObject().put("description", "No details available.").toString()
    }

    fun episodes(sourceId: String, id: String): String = when (sourceId) {
        "live.jikan.anime" -> mapJikanEpisodes(numericId(id), get("https://api.jikan.moe/v4/anime/${numericId(id)}/episodes?page=1"))
        "live.tvmaze.tv" -> mapTvEpisodes(get("https://api.tvmaze.com/shows/${numericId(id)}/episodes"))
        else -> JSONArray().toString()
    }

    fun chapters(sourceId: String, id: String): String = JSONArray().toString()
    fun pages(sourceId: String, id: String): String = JSONArray().toString()

    fun streams(sourceId: String, id: String): String = when (sourceId) {
        "live.jikan.anime" -> {
            val animeId = id.substringBefore('|').ifBlank { id }
            mapJikanStreaming(get("https://api.jikan.moe/v4/anime/${numericId(animeId)}/streaming"))
        }
        "live.itunes.music" -> mapItunesMusicStreams(
            get("https://itunes.apple.com/lookup?id=${numericId(id)}&entity=song&country=US")
        )
        else -> JSONArray().toString()
    }

    fun lyrics(sourceId: String, id: String): String = JSONObject()
        .put("trackId", id)
        .put("synced", false)
        .put("text", "Lyrics are not provided by this catalog source.")
        .toString()

    fun relatedArtists(sourceId: String, id: String): String = JSONArray().toString()
    fun feed(sourceId: String): String = JSONArray().toString()

    private fun get(url: String, bearerToken: String? = null): String {
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return it.body }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Sora/0.2 Android")
        if (!bearerToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code from ${URI(url).host}: ${body.take(160)}")
            cache[url] = CacheEntry(now, body)
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun tmdbGet(url: String, accessToken: String): String {
        val token = accessToken.trim()
        require(token.isNotBlank()) { TMDB_TOKEN_REQUIRED }
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return it.body }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("User-Agent", "Sora/0.2 Android")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = when (code) {
                    401, 403 -> "TMDB rejected the API Read Access Token (HTTP $code). Check it in More → Extensions → Sora Live Sources."
                    429 -> "TMDB is rate-limiting requests. Wait a moment and retry."
                    else -> "TMDB returned HTTP $code. Retry the movie catalog."
                }
                error(message)
            }
            cache[url] = CacheEntry(now, body)
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun mapTmdbMovies(raw: String): String {
        val data = JSONObject(raw).optJSONArray("results") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                if (item.optBoolean("adult", false)) continue
                val id = item.optInt("id")
                val title = item.optString("title").trim()
                if (id <= 0 || title.isBlank()) continue
                val date = item.optString("release_date").take(4)
                val rating = item.optDouble("vote_average", 0.0).takeIf { it > 0.0 }?.let { String.format("%.1f", it) }
                val poster = item.optString("poster_path").takeIf { it.startsWith("/") }
                    ?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty()
                put(
                    JSONObject()
                        .put("id", id.toString())
                        .put("title", title)
                        .put("subtitle", listOfNotNull(date, rating?.let { "$it TMDB" }).joinToString(" · "))
                        .put("artworkUrl", poster)
                )
            }
        }.toString()
    }

    private fun mapTmdbDetails(raw: String): String {
        val movie = JSONObject(raw)
        val genres = movie.optJSONArray("genres")
        val genreNames = if (genres == null) emptyList() else buildList {
            for (index in 0 until genres.length()) genres.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
        }
        val date = movie.optString("release_date").take(4)
        val rating = movie.optDouble("vote_average", 0.0).takeIf { it > 0.0 }?.let { String.format("%.1f / 10 on TMDB", it) }.orEmpty()
        return JSONObject()
            .put("description", movie.optString("overview").ifBlank { "No overview available from TMDB." })
            .put("genres", JSONArray(genreNames))
            .put("year", date.toIntOrNull() ?: 0)
            .put("score", rating)
            .put("status", movie.optString("status"))
            .toString()
    }

    private fun itunesSearch(term: String, media: String, entity: String): String =
        "https://itunes.apple.com/search?term=${enc(term)}&media=$media&entity=$entity&limit=25&country=US"

    private fun mapJikanList(raw: String, isManga: Boolean): String {
        val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optInt("mal_id").toString()
                val title = item.optString("title_english").ifBlank { item.optString("title") }
                val score = item.optDouble("score", 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) }
                val format = item.optString("type")
                val count = if (isManga) item.optInt("chapters", 0) else item.optInt("episodes", 0)
                val suffix = if (isManga) "ch" else "eps"
                val subtitle = listOfNotNull(score, format.takeIf { it.isNotBlank() }, count.takeIf { it > 0 }?.let { "$it $suffix" }).joinToString(" · ")
                val images = item.optJSONObject("images")?.optJSONObject("jpg")
                val art = images?.optString("large_image_url").takeUnless { it.isNullOrBlank() }
                    ?: images?.optString("image_url").orEmpty()
                put(JSONObject().put("id", id).put("title", title).put("subtitle", subtitle).put("artworkUrl", art))
            }
        }.toString()
    }

    private fun mapJikanDetails(raw: String): String {
        val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
        val synopsis = data.optString("synopsis").ifBlank { "No synopsis available." }
        val status = data.optString("status")
        val score = data.optDouble("score", 0.0)
        return JSONObject()
            .put("description", synopsis)
            .put("status", status)
            .put("score", score)
            .toString()
    }

    private fun mapJikanEpisodes(animeId: String, raw: String): String {
        val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val episodeId = item.optInt("mal_id", i + 1)
                val title = item.optString("title").ifBlank { "Episode $episodeId" }
                put(JSONObject()
                    .put("id", "$animeId|$episodeId")
                    .put("number", episodeId)
                    .put("title", title))
            }
        }.toString()
    }

    private fun mapJikanStreaming(raw: String): String {
        val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val name = item.optString("name").ifBlank { "Official stream" }
                val url = item.optString("url")
                if (url.isNotBlank()) put(JSONObject().put("label", name).put("url", url))
            }
        }.toString()
    }

    private fun mapItunes(raw: String, type: String): String {
        val data = JSONObject(raw).optJSONArray("results") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optLong("trackId", item.optLong("collectionId", 0L)).toString()
                if (id == "0") continue
                val title = item.optString("trackName").ifBlank { item.optString("collectionName") }
                val subtitle = if (type == "music") {
                    listOf(item.optString("artistName"), item.optString("collectionName")).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    val year = item.optString("releaseDate").take(4)
                    listOf(item.optString("primaryGenreName"), year).filter { it.isNotBlank() }.joinToString(" · ")
                }
                val art = item.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                put(JSONObject().put("id", id).put("title", title).put("subtitle", subtitle).put("artworkUrl", art))
            }
        }.toString()
    }

    private fun mapItunesDetails(raw: String, type: String): String {
        val results = JSONObject(raw).optJSONArray("results") ?: JSONArray()
        val item = results.optJSONObject(0) ?: JSONObject()
        val description = if (type == "movie") {
            item.optString("longDescription").ifBlank { item.optString("shortDescription") }.ifBlank { "No description available." }
        } else {
            listOf(item.optString("artistName"), item.optString("collectionName"), item.optString("primaryGenreName"))
                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No track metadata available." }
        }
        return JSONObject().put("description", description).toString()
    }

    private fun mapItunesMusicStreams(raw: String): String {
        val results = JSONObject(raw).optJSONArray("results") ?: JSONArray()
        val item = results.optJSONObject(0) ?: return JSONArray().toString()
        val preview = item.optString("previewUrl")
        if (!preview.startsWith("http://") && !preview.startsWith("https://")) return JSONArray().toString()
        return JSONArray().put(
            JSONObject()
                .put("label", "iTunes Preview")
                .put("url", preview)
                .put("mimeType", "audio/mp4")
                .put("durationMs", item.optLong("trackTimeMillis"))
        ).toString()
    }

    private fun mapTvSchedule(raw: String): String {
        val data = JSONArray(raw)
        val seen = hashSetOf<Int>()
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val show = data.getJSONObject(i).optJSONObject("show") ?: continue
                val id = show.optInt("id")
                if (id == 0 || !seen.add(id)) continue
                put(tvShowToItem(show))
                if (length() >= 25) break
            }
        }.toString()
    }

    private fun mapTvSearch(raw: String): String {
        val data = JSONArray(raw)
        return JSONArray().apply {
            for (i in 0 until minOf(data.length(), 25)) {
                val show = data.getJSONObject(i).optJSONObject("show") ?: continue
                put(tvShowToItem(show))
            }
        }.toString()
    }

    private fun tvShowToItem(show: JSONObject): JSONObject {
        val image = show.optJSONObject("image")
        val art = image?.optString("original").takeUnless { it.isNullOrBlank() }
            ?: image?.optString("medium").orEmpty()
        val genres = show.optJSONArray("genres")
        val genre = if (genres != null && genres.length() > 0) genres.optString(0) else "TV"
        val network = show.optJSONObject("network")?.optString("name")
            ?: show.optJSONObject("webChannel")?.optString("name").orEmpty()
        return JSONObject()
            .put("id", show.optInt("id").toString())
            .put("title", show.optString("name"))
            .put("subtitle", listOf(genre, network).filter { it.isNotBlank() }.joinToString(" · "))
            .put("artworkUrl", art)
    }

    private fun mapTvDetails(raw: String): String {
        val show = JSONObject(raw)
        return JSONObject()
            .put("description", stripHtml(show.optString("summary")).ifBlank { "No description available." })
            .put("status", show.optString("status"))
            .toString()
    }

    private fun mapTvEpisodes(raw: String): String {
        val data = JSONArray(raw)
        return JSONArray().apply {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val season = item.optInt("season", 0)
                val number = item.optInt("number", 0)
                val label = when {
                    season > 0 && number > 0 -> "S${season}E${number}"
                    number > 0 -> "Episode $number"
                    else -> "Episode ${i + 1}"
                }
                put(JSONObject()
                    .put("id", item.optInt("id").toString())
                    .put("number", number.takeIf { it > 0 } ?: i + 1)
                    .put("title", "$label · ${item.optString("name")}"))
            }
        }.toString()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private fun numericId(value: String): String = value.substringBefore('|').filter { it.isDigit() }.ifBlank { "0" }
    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"")
}
