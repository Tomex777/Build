package com.night.sora.youtubemusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catalog-only YouTube Music client.
 *
 * The pinned Spotui Innertube snapshot intentionally trims artist/album/suggestion
 * browsing because upstream Spotui uses Spotify for catalog metadata. Auri does not:
 * the standalone music app needs YouTube Music's public catalog hierarchy, so this
 * small extension-local client restores those browse endpoints without leaking any
 * YouTube-specific model into the app core.
 */
object YouTubeMusicBrowseApi {
    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val ARTIST_FILTER = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
    private const val ALBUM_FILTER = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"

    suspend fun suggestions(input: String): String = withContext(Dispatchers.IO) {
        val clean = input.trim()
        if (clean.length < 2) return@withContext "[]"
        val root = post(
            "music/get_search_suggestions",
            JSONObject()
                .put("context", contextJson())
                .put("input", clean),
        )
        val values = LinkedHashSet<String>()
        walk(root) { objectValue ->
            val renderer = objectValue.optJSONObject("searchSuggestionRenderer") ?: return@walk
            val runs = renderer.optJSONObject("suggestion")?.optJSONArray("runs") ?: return@walk
            buildString {
                for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }.trim().takeIf(String::isNotBlank)?.let(values::add)
        }
        JSONArray(values.take(10)).toString()
    }

    suspend fun artist(query: String, requestedId: String?): String = withContext(Dispatchers.IO) {
        val artist = if (requestedId.isNullOrBlank()) resolveArtist(query) else null
        val artistId = requestedId?.takeIf(String::isNotBlank) ?: artist?.id ?: error("Artist not found")
        val root = browse(artistId)
        val artistName = headerTitle(root).ifBlank { artist?.title ?: query }
        val artistArtwork = headerArtwork(root).ifBlank { artist?.thumbnail.orEmpty() }
        val songObjects = LinkedHashMap<String, JSONObject>()
        collectSongs(
            root,
            fallbackArtist = artistName,
            fallbackArtistId = artistId,
            fallbackArtwork = artistArtwork,
        ).forEach { songObjects[it.getString("id")] = it }

        val releases = LinkedHashMap<String, JSONObject>()
        collectAlbums(root).forEach { releases[it.getString("id")] = it }

        sectionEndpoint(root, setOf("songs", "popular songs"))?.let { endpoint ->
            var page = browse(endpoint.first, endpoint.second)
            repeat(4) {
                collectSongs(
                    page,
                    fallbackArtist = artistName,
                    fallbackArtistId = artistId,
                    fallbackArtwork = artistArtwork,
                ).forEach { songObjects[it.getString("id")] = it }
                val continuation = firstContinuation(page) ?: return@repeat
                page = browse(continuation = continuation)
            }
        }

        val releaseTitles = setOf("albums", "singles", "singles & eps", "releases", "discography")
        sectionEndpoint(root, releaseTitles)?.let { endpoint ->
            val page = browse(endpoint.first, endpoint.second)
            collectAlbums(page).forEach { releases[it.getString("id")] = it }
        }

        JSONObject()
            .put("id", artistId)
            .put("name", artistName)
            .put("artworkUrl", artistArtwork)
            .put("songs", JSONArray(songObjects.values.toList()))
            .put("releases", JSONArray(releases.values.toList()))
            .toString()
    }

    suspend fun album(query: String, requestedId: String?): String = withContext(Dispatchers.IO) {
        val album = if (requestedId.isNullOrBlank()) resolveAlbum(query) else null
        val albumId = requestedId?.takeIf(String::isNotBlank) ?: album?.id ?: error("Album not found")
        val root = browse(albumId)
        val year = album?.year?.takeIf { it > 0 } ?: headerYear(root)
        val title = cleanAlbumTitle(headerTitle(root), year)
            .ifBlank { album?.title ?: query }
        val (headerArtist, headerArtistId) = headerArtistDetails(root)
        val artistName = headerArtist.ifBlank {
            album?.artists?.joinToString(", ") { it.name }.orEmpty()
        }
        val artistId = headerArtistId.ifBlank { album?.artists?.firstOrNull()?.id.orEmpty() }
        val artwork = headerArtwork(root).ifBlank { album?.thumbnail.orEmpty() }
        val songs = collectSongs(
            root,
            fallbackArtist = artistName,
            fallbackArtistId = artistId,
            fallbackAlbum = title,
            fallbackAlbumId = albumId,
            fallbackArtwork = artwork,
        )

        JSONObject()
            .put("id", albumId)
            .put("title", title)
            .put("artist", artistName)
            .put("artistId", artistId)
            .put("year", year)
            .put("artworkUrl", artwork)
            .put("songs", JSONArray(songs))
            .toString()
    }

    private suspend fun resolveArtist(query: String): ArtistItem {
        val result = YouTube.search(query.ifBlank { "artist" }, YouTube.SearchFilter(ARTIST_FILTER)).getOrThrow()
        val items = result.items.filterIsInstance<ArtistItem>()
        return items.firstOrNull { it.title.equals(query, ignoreCase = true) }
            ?: items.firstOrNull()
            ?: error("Artist not found")
    }

    private suspend fun resolveAlbum(query: String): AlbumItem {
        val result = YouTube.search(query.ifBlank { "album" }, YouTube.SearchFilter(ALBUM_FILTER)).getOrThrow()
        val items = result.items.filterIsInstance<AlbumItem>()
        return items.firstOrNull { it.title.equals(query, ignoreCase = true) }
            ?: items.firstOrNull()
            ?: error("Album not found")
    }

    private fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): JSONObject {
        val body = JSONObject().put("context", contextJson())
        browseId?.let { body.put("browseId", it) }
        params?.let { body.put("params", it) }
        return post(
            "browse",
            body,
            buildMap {
                continuation?.let {
                    put("continuation", it)
                    put("ctoken", it)
                    put("type", "next")
                }
            },
        )
    }

    private fun post(path: String, body: JSONObject, query: Map<String, String> = emptyMap()): JSONObject {
        val suffix = if (query.isEmpty()) "" else query.entries.joinToString("&", prefix = "?") {
            java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL(BASE + path + suffix).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", WEB_REMIX.userAgent)
            setRequestProperty("X-Goog-Api-Format-Version", "1")
            setRequestProperty("X-YouTube-Client-Name", WEB_REMIX.clientId)
            setRequestProperty("X-YouTube-Client-Version", WEB_REMIX.clientVersion)
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("X-Origin", "https://music.youtube.com")
            setRequestProperty("Referer", "https://music.youtube.com/")
            YouTube.visitorData?.takeIf(String::isNotBlank)?.let {
                setRequestProperty("X-Goog-Visitor-Id", it)
            }
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Catalog request failed")
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun contextJson(): JSONObject {
        val locale = YouTube.locale
        return JSONObject()
            .put(
                "client",
                JSONObject()
                    .put("clientName", WEB_REMIX.clientName)
                    .put("clientVersion", WEB_REMIX.clientVersion)
                    .put("gl", locale.gl)
                    .put("hl", locale.hl)
                    .put("visitorData", YouTube.visitorData ?: JSONObject.NULL),
            )
            .put("request", JSONObject().put("useSsl", true))
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    private fun collectSongs(
        root: Any?,
        fallbackArtist: String = "",
        fallbackArtistId: String = "",
        fallbackAlbum: String = "",
        fallbackAlbumId: String = "",
        fallbackArtwork: String = "",
    ): List<JSONObject> {
        val result = LinkedHashMap<String, JSONObject>()
        walk(root) { obj ->
            obj.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                parseSong(
                    renderer,
                    fallbackArtist,
                    fallbackArtistId,
                    fallbackAlbum,
                    fallbackAlbumId,
                    fallbackArtwork,
                )?.let { result[it.getString("id")] = it }
            }
        }
        return result.values.toList()
    }

    private fun parseSong(
        renderer: JSONObject,
        fallbackArtist: String,
        fallbackArtistId: String,
        fallbackAlbum: String,
        fallbackAlbumId: String,
        fallbackArtwork: String,
    ): JSONObject? {
        val id = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            .orEmpty()
            .ifBlank {
                renderer.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
                    .orEmpty()
            }
        if (id.isBlank()) return null

        val columns = renderer.optJSONArray("flexColumns") ?: return null
        fun runsAt(index: Int): JSONArray? = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        val title = runsAt(0)?.optJSONObject(0)?.optString("text").orEmpty()
        if (title.isBlank()) return null

        val artistRuns = runsAt(1)
        val artistNames = mutableListOf<String>()
        var artistId = ""
        if (artistRuns != null) {
            for (i in 0 until artistRuns.length()) {
                val run = artistRuns.optJSONObject(i) ?: continue
                val name = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    .orEmpty()
                if (browseId.startsWith("UC") && name.isNotBlank()) {
                    if (artistId.isBlank()) artistId = browseId
                    artistNames += name
                }
            }
        }

        val albumRun = runsAt(2)?.let { runs ->
            (0 until runs.length())
                .mapNotNull(runs::optJSONObject)
                .firstOrNull {
                    it.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        .orEmpty()
                        .startsWith("MPRE")
                }
        }
        val album = albumRun?.optString("text").orEmpty()
        val albumId = albumRun?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            .orEmpty()

        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", artistNames.distinct().joinToString(", ").ifBlank { fallbackArtist })
            .put("artistId", artistId.ifBlank { fallbackArtistId })
            .put("album", album.ifBlank { fallbackAlbum })
            .put("albumId", albumId.ifBlank { fallbackAlbumId })
            .put("artworkUrl", thumbnail(renderer).ifBlank { fallbackArtwork })
            .put("durationSeconds", parseDuration(renderer))
            .put("explicit", renderer.toString().contains("MUSIC_EXPLICIT_BADGE"))
    }

    private fun collectAlbums(root: Any?): List<JSONObject> {
        val result = LinkedHashMap<String, JSONObject>()
        walk(root) { obj ->
            val renderer = obj.optJSONObject("musicTwoRowItemRenderer") ?: return@walk
            val endpoint = renderer.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: return@walk
            val id = endpoint.optString("browseId")
            val pageType = endpoint.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType")
                .orEmpty()
            if (!id.startsWith("MPRE") && !pageType.contains("ALBUM", ignoreCase = true)) return@walk

            val title = renderer.optJSONObject("title")?.optJSONArray("runs")
                ?.optJSONObject(0)?.optString("text").orEmpty()
            if (title.isBlank()) return@walk
            val subtitleRuns = renderer.optJSONObject("subtitle")?.optJSONArray("runs")
            val subtitle = buildString {
                if (subtitleRuns != null) for (i in 0 until subtitleRuns.length()) {
                    append(subtitleRuns.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
            val year = Regex("""\b(19|20)\d{2}\b""").find(subtitle)?.value?.toIntOrNull() ?: 0
            val type = when {
                subtitle.contains("single", true) -> "Single"
                subtitle.contains("EP", true) -> "EP"
                else -> "Album"
            }
            result[id] = JSONObject()
                .put("id", id)
                .put("title", title)
                .put("artist", subtitle.substringBefore(" • ").substringBefore(" · "))
                .put("year", year)
                .put("type", type)
                .put("artworkUrl", thumbnail(renderer))
        }
        return result.values.toList()
    }

    private fun sectionEndpoint(root: Any?, acceptedTitles: Set<String>): Pair<String, String?>? {
        var answer: Pair<String, String?>? = null
        walk(root) { obj ->
            if (answer != null) return@walk
            val shelf = obj.optJSONObject("musicShelfRenderer")
                ?: obj.optJSONObject("musicCarouselShelfRenderer")
                ?: return@walk
            val title = shelfTitle(shelf).lowercase()
            if (acceptedTitles.none { title == it || title.contains(it) }) return@walk

            val chosen = shelf
                .optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?: shelf
                    .optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("moreContentButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                ?: return@walk
            if (chosen.optString("browseId").isBlank()) return@walk
            answer = chosen.optString("browseId") to chosen.optString("params").takeIf(String::isNotBlank)
        }
        return answer
    }

    private fun shelfTitle(shelf: JSONObject): String {
        val direct = shelf.optJSONObject("title")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text").orEmpty()
        if (direct.isNotBlank()) return direct
        return shelf.optJSONObject("header")
            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            ?.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
    }

    private fun firstContinuation(root: Any?): String? {
        var answer: String? = null
        walk(root) { obj ->
            if (answer != null) return@walk
            answer = obj.optJSONObject("nextContinuationData")?.optString("continuation")
                ?.takeIf(String::isNotBlank)
        }
        return answer
    }

    private fun headerTitle(root: JSONObject): String =
        headerRuns(root, "title").firstOrNull().orEmpty()

    private fun headerArtist(root: JSONObject): String = headerArtistDetails(root).first

    private fun headerArtistDetails(root: JSONObject): Pair<String, String> {
        var artistName = ""
        var artistId = ""
        walk(root.optJSONObject("header")) { obj ->
            if (artistName.isNotBlank()) return@walk
            val runs = obj.optJSONObject("subtitle")?.optJSONArray("runs") ?: return@walk
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                val name = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    .orEmpty()
                if (name.isNotBlank() && browseId.startsWith("UC")) {
                    artistName = name
                    artistId = browseId
                    return@walk
                }
            }
        }
        if (artistName.isBlank()) {
            artistName = headerRuns(root, "subtitle")
                .asSequence()
                .map(String::trim)
                .firstOrNull { value ->
                    value.isNotBlank() &&
                        !value.matches(Regex("""\d{4}""")) &&
                        value !in setOf("Album", "Single", "EP", "Playlist")
                }
                .orEmpty()
        }
        return artistName to artistId
    }

    private fun cleanAlbumTitle(value: String, year: Int): String {
        val clean = value.trim()
        if (year <= 0) return clean
        return clean
            .removeSuffix(" • $year")
            .removeSuffix(" · $year")
            .removeSuffix(" $year")
            .trim()
    }

    private fun headerYear(root: JSONObject): Int =
        (headerRuns(root, "subtitle") + headerRuns(root, "secondSubtitle"))
            .firstNotNullOfOrNull { Regex("""\b(19|20)\d{2}\b""").find(it)?.value?.toIntOrNull() } ?: 0

    private fun headerArtwork(root: JSONObject): String {
        var answer = ""
        walk(root.optJSONObject("header")) { obj ->
            if (answer.isBlank()) {
                val thumbs = obj.optJSONArray("thumbnails")
                val candidate = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))?.optString("url").orEmpty()
                if (candidate.startsWith("http")) answer = candidate
            }
        }
        return answer
    }

    private fun headerRuns(root: JSONObject, key: String): List<String> {
        val values = mutableListOf<String>()
        walk(root.optJSONObject("header")) { obj ->
            val runs = obj.optJSONObject(key)?.optJSONArray("runs") ?: return@walk
            for (i in 0 until runs.length()) {
                runs.optJSONObject(i)?.optString("text")?.takeIf(String::isNotBlank)?.let(values::add)
            }
        }
        return values
    }

    private fun thumbnail(renderer: JSONObject): String {
        var answer = ""
        walk(renderer.optJSONObject("thumbnail") ?: renderer.optJSONObject("thumbnailRenderer")) { obj ->
            val thumbs = obj.optJSONArray("thumbnails") ?: return@walk
            val candidate = thumbs.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))?.optString("url").orEmpty()
            if (candidate.startsWith("http")) answer = candidate
        }
        return answer
    }

    private fun parseDuration(renderer: JSONObject): Long {
        val fixed = renderer.optJSONArray("fixedColumns")
        val text = fixed?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
        val parts = text.split(':').mapNotNull(String::toLongOrNull)
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun walk(value: Any?, block: (JSONObject) -> Unit) {
        fun visit(current: Any?) {
            when (current) {
                is JSONObject -> {
                    block(current)
                    val keys = current.keys()
                    while (keys.hasNext()) visit(current.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until current.length()) visit(current.opt(i))
            }
        }
        visit(value)
    }
}
