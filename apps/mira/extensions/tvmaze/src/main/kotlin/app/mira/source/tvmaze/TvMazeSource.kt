package app.mira.source.tvmaze

import app.mira.domain.ContentDetails
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import app.mira.domain.EpisodeRef
import app.mira.domain.SeasonRef
import app.mira.domain.TvEpisode
import app.mira.domain.TvSeason
import app.mira.source.MiraSource
import app.mira.source.SourceCapabilities
import app.mira.source.SourceMetadata
import app.mira.source.SourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class TvMazeSource : MiraSource {
    override val metadata = SourceMetadata(
        id = "tvmaze",
        name = "TVMaze",
        language = "en",
        homeUrl = "https://www.tvmaze.com",
        capabilities = SourceCapabilities(
            movies = false,
            series = true,
            searchable = true,
            browsable = true,
            movieStreaming = false,
            episodeStreaming = false,
            downloadable = false,
        ),
    )

    override suspend fun search(query: String, page: Int): SourcePage<ContentSearchResult> {
        if (page > 1) return SourcePage(emptyList(), false)
        val normalized = query.trim()
        if (normalized.isEmpty()) return SourcePage(emptyList(), false)

        val results = JSONArray(
            get("https://api.tvmaze.com/search/shows?q=${encode(normalized)}"),
        )
        val items = buildList {
            for (index in 0 until results.length()) {
                val wrapper = results.optJSONObject(index) ?: continue
                val show = wrapper.optJSONObject("show") ?: continue
                val id = show.optInt("id", -1)
                val title = show.optString("name").trim()
                if (id < 0 || title.isEmpty()) continue
                add(
                    ContentSearchResult(
                        ref = ContentRef(metadata.id, id.toString(), ContentKind.SERIES),
                        title = title,
                        posterUrl = show.optJSONObject("image")?.optString("medium").blankToNull(),
                        year = show.optString("premiered").take(4).toIntOrNull(),
                        description = show.optString("summary").blankToNull()?.stripHtml(),
                    ),
                )
            }
        }
        return SourcePage(items, false)
    }

    override suspend fun details(content: ContentRef, sourceState: String?): ContentDetails {
        require(content.kind == ContentKind.SERIES)
        val show = JSONObject(get("https://api.tvmaze.com/shows/${content.sourceContentId}"))
        val genres = show.optJSONArray("genres").toStringList()
        val network = show.optJSONObject("network")?.optString("name").blankToNull()
            ?: show.optJSONObject("webChannel")?.optString("name").blankToNull()

        return ContentDetails(
            ref = content,
            title = show.optString("name", content.sourceContentId),
            posterUrl = show.optJSONObject("image")?.optString("original").blankToNull()
                ?: show.optJSONObject("image")?.optString("medium").blankToNull(),
            description = show.optString("summary").blankToNull()?.stripHtml(),
            year = show.optString("premiered").take(4).toIntOrNull(),
            genres = genres,
            metadata = buildMap {
                show.optString("status").blankToNull()?.let { put("Status", it) }
                show.optString("type").blankToNull()?.let { put("Type", it) }
                show.optInt("runtime", -1).takeIf { it > 0 }?.let { put("Runtime", "$it min") }
                network?.let { put("Network", it) }
                show.optJSONObject("rating")
                    ?.optDouble("average", Double.NaN)
                    ?.takeIf { !it.isNaN() }
                    ?.let { put("Rating", it.toString()) }
            },
            webUrl = show.optString("url").blankToNull(),
        )
    }

    override suspend fun seasons(series: ContentRef, sourceState: String?): List<TvSeason> {
        require(series.kind == ContentKind.SERIES)
        val seasons = JSONArray(
            get("https://api.tvmaze.com/shows/${series.sourceContentId}/seasons"),
        )
        return buildList {
            for (index in 0 until seasons.length()) {
                val season = seasons.optJSONObject(index) ?: continue
                val id = season.optInt("id", -1)
                if (id < 0) continue
                val number = season.optInt("number", -1).takeIf { it >= 0 }
                add(
                    TvSeason(
                        ref = SeasonRef(metadata.id, series.sourceContentId, id.toString()),
                        number = number,
                        title = number?.let { "Season $it" } ?: "Season",
                        episodeCount = season.optInt("episodeOrder", -1).takeIf { it >= 0 },
                        posterUrl = season.optJSONObject("image")?.optString("medium").blankToNull(),
                    ),
                )
            }
        }
    }

    override suspend fun episodes(season: SeasonRef, sourceState: String?): List<TvEpisode> {
        val episodes = JSONArray(
            get("https://api.tvmaze.com/seasons/${season.sourceSeasonId}/episodes"),
        )
        return buildList {
            for (index in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(index) ?: continue
                val id = episode.optInt("id", -1)
                if (id < 0) continue
                val seasonNumber = episode.optInt("season", -1).takeIf { it >= 0 }
                val episodeNumber = episode.optInt("number", -1).takeIf { it >= 0 }
                add(
                    TvEpisode(
                        ref = EpisodeRef(
                            sourceId = metadata.id,
                            sourceContentId = season.sourceContentId,
                            sourceSeasonId = season.sourceSeasonId,
                            sourceEpisodeId = id.toString(),
                        ),
                        title = episode.optString("name").blankToNull()
                            ?: "Episode ${episodeNumber ?: index + 1}",
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        description = episode.optString("summary").blankToNull()?.stripHtml(),
                        imageUrl = episode.optJSONObject("image")?.optString("medium").blankToNull(),
                        airDate = episode.optString("airdate").blankToNull(),
                    ),
                )
            }
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Mira/0.1 Android")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("TVMaze returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun String?.blankToNull(): String? =
    this?.takeIf { it.isNotBlank() && it != "null" }

private fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
