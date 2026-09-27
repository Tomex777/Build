package app.mira.source.internetarchive

import app.mira.domain.ContentDetails
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import app.mira.domain.ResolvedMedia
import app.mira.source.MiraSource
import app.mira.source.SourceCapabilities
import app.mira.source.SourceMetadata
import app.mira.source.SourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class InternetArchiveMovieSource : MiraSource {
    override val metadata = SourceMetadata(
        id = "internet-archive-movies",
        name = "Internet Archive",
        language = "en",
        homeUrl = "https://archive.org/details/movies",
        capabilities = SourceCapabilities(
            movies = true,
            series = false,
            searchable = true,
            browsable = true,
            movieStreaming = true,
            episodeStreaming = false,
            downloadable = true,
        ),
    )

    override suspend fun search(query: String, page: Int): SourcePage<ContentSearchResult> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return SourcePage(emptyList(), false)
        val rows = 24
        val encodedQuery = encode("mediatype:movies AND title:(\"$normalized\")")
        val url = "https://archive.org/advancedsearch.php" +
            "?q=$encodedQuery" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=description" +
            "&rows=$rows&page=${page.coerceAtLeast(1)}&output=json"

        val root = JSONObject(get(url))
        val response = root.getJSONObject("response")
        val docs = response.optJSONArray("docs") ?: JSONArray()
        val numFound = response.optInt("numFound", docs.length())

        val items = buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.optJSONObject(index) ?: continue
                val identifier = doc.optString("identifier").trim()
                val title = doc.optString("title").trim()
                if (identifier.isEmpty() || title.isEmpty()) continue
                add(
                    ContentSearchResult(
                        ref = ContentRef(metadata.id, identifier, ContentKind.MOVIE),
                        title = title,
                        posterUrl = "https://archive.org/services/img/${path(identifier)}",
                        year = doc.optFlexibleInt("year"),
                        description = doc.optFlexibleString("description")?.stripHtml(),
                    ),
                )
            }
        }
        return SourcePage(items, page.coerceAtLeast(1) * rows < numFound)
    }

    override suspend fun popular(page: Int): SourcePage<ContentSearchResult> {
        val rows = 24
        val url = "https://archive.org/advancedsearch.php" +
            "?q=${encode("mediatype:movies")}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year" +
            "&sort%5B%5D=downloads+desc" +
            "&rows=$rows&page=${page.coerceAtLeast(1)}&output=json"
        val root = JSONObject(get(url))
        val response = root.getJSONObject("response")
        val docs = response.optJSONArray("docs") ?: JSONArray()
        val numFound = response.optInt("numFound", docs.length())

        val items = buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.optJSONObject(index) ?: continue
                val identifier = doc.optString("identifier").trim()
                val title = doc.optString("title").trim()
                if (identifier.isBlank() || title.isBlank()) continue
                add(
                    ContentSearchResult(
                        ref = ContentRef(metadata.id, identifier, ContentKind.MOVIE),
                        title = title,
                        posterUrl = "https://archive.org/services/img/${path(identifier)}",
                        year = doc.optFlexibleInt("year"),
                    ),
                )
            }
        }

        return SourcePage(items, page.coerceAtLeast(1) * rows < numFound)
    }

    override suspend fun details(content: ContentRef, sourceState: String?): ContentDetails {
        require(content.kind == ContentKind.MOVIE)
        val root = metadataJson(content.sourceContentId)
        val meta = root.optJSONObject("metadata") ?: JSONObject()
        val title = meta.optFlexibleString("title") ?: content.sourceContentId
        val subjects = meta.optFlexibleList("subject")
        val creator = meta.optFlexibleString("creator")
        val runtime = meta.optFlexibleString("runtime")

        return ContentDetails(
            ref = content,
            title = title,
            posterUrl = "https://archive.org/services/img/${path(content.sourceContentId)}",
            description = meta.optFlexibleString("description")?.stripHtml(),
            year = meta.optFlexibleInt("year") ?: meta.optFlexibleInt("date"),
            genres = subjects.take(12),
            metadata = buildMap {
                if (!creator.isNullOrBlank()) put("Creator", creator)
                if (!runtime.isNullOrBlank()) put("Runtime", runtime)
                meta.optFlexibleString("language")?.let { put("Language", it) }
            },
            webUrl = "https://archive.org/details/${path(content.sourceContentId)}",
        )
    }

    override suspend fun resolveMovie(movie: ContentRef, sourceState: String?): List<ResolvedMedia> {
        require(movie.kind == ContentKind.MOVIE)
        val root = metadataJson(movie.sourceContentId)
        val files = root.optJSONArray("files") ?: JSONArray()

        return buildList {
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                val name = file.optString("name").trim()
                if (name.isBlank()) continue
                val format = file.optString("format").lowercase()
                val lowerName = name.lowercase()
                val mime = when {
                    lowerName.endsWith(".mp4") -> "video/mp4"
                    lowerName.endsWith(".mkv") -> "video/x-matroska"
                    lowerName.endsWith(".webm") -> "video/webm"
                    else -> null
                } ?: continue

                if ("sample" in lowerName || "thumb" in lowerName || "trailer" in lowerName) continue

                val likelyPlayable =
                    "mpeg4" in format || "h.264" in format || "matroska" in format ||
                    "webm" in format || lowerName.endsWith(".mp4") ||
                    lowerName.endsWith(".mkv") || lowerName.endsWith(".webm")
                if (!likelyPlayable) continue

                val height = file.optFlexibleInt("height")
                val width = file.optFlexibleInt("width")
                val quality = when {
                    height != null -> "${height}p"
                    width != null -> "${width}w"
                    else -> file.optFlexibleString("format")
                }

                add(
                    ResolvedMedia(
                        url = "https://archive.org/download/${path(movie.sourceContentId)}/${path(name)}",
                        mimeType = mime,
                        quality = quality,
                        hosterName = "Internet Archive",
                    ),
                )
            }
        }.distinctBy { it.url }
            .sortedWith(
                compareByDescending<ResolvedMedia> {
                    Regex("""(\d{3,4})p""").find(it.quality.orEmpty())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                }.thenBy { it.url },
            )
    }

    private suspend fun metadataJson(identifier: String): JSONObject =
        JSONObject(get("https://archive.org/metadata/${path(identifier)}"))

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Mira/0.1 Android")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Internet Archive returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun path(value: String): String =
        URI(null, null, value, null).rawPath
}

private fun JSONObject.optFlexibleString(key: String): String? {
    val value = opt(key) ?: return null
    return when (value) {
        is JSONArray -> if (value.length() > 0) value.optString(0).takeIf { it.isNotBlank() } else null
        JSONObject.NULL -> null
        else -> value.toString().takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.optFlexibleList(key: String): List<String> {
    val value = opt(key) ?: return emptyList()
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                value.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        JSONObject.NULL -> emptyList()
        else -> value.toString().split(';', ',').map { it.trim() }.filter { it.isNotBlank() }
    }
}

private fun JSONObject.optFlexibleInt(key: String): Int? {
    val raw = optFlexibleString(key) ?: return null
    return Regex("""\d{4}|\d+""").find(raw)?.value?.toIntOrNull()
}

private fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
