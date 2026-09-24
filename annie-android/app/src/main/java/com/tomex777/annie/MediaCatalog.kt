package com.tomex777.annie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class SeasonItem(
    val id: Int,
    val title: String,
    val image: String,
    val year: Int?,
    val episodes: Int?
) {
    fun asCatalogItem() = CatalogItem(id, "ANIME", title, image, year, "UNKNOWN", episodes, null)
}

internal data class CatalogItem(
    val id: Int,
    val mediaType: String,
    val title: String,
    val image: String,
    val year: Int?,
    val status: String,
    val episodes: Int?,
    val chapters: Int?,
    val format: String = "",
    val seasons: List<SeasonItem> = emptyList(),
    val creator: String? = null,
    val genres: List<String> = emptyList(),
    val summary: String = "",
    val runtimeMinutes: Int? = null,
    val sourceLabel: String = "AniList",
    val sourceUrl: String = ""
)

internal fun selectedDetailsStage(item: CatalogItem): String? = when {
    item.mediaType == "ANIME" && item.format == "MOVIE" -> "movie"
    item.mediaType == "ANIME" && item.seasons.size > 1 -> "series"
    item.mediaType == "ANIME" -> "episodes"
    item.mediaType == "MANGA" -> "manga"
    item.mediaType == "TV" -> "tv"
    item.mediaType == "MOVIE" -> "movie"
    else -> null
}

internal suspend fun searchCatalog(mediaType: String, query: String): List<CatalogItem> = when (mediaType) {
    "anime", "manga" -> searchAniList(mediaType, query)
    "movie" -> searchWikidataMovies(query)
    "tv" -> searchTvMaze(query)
    else -> emptyList()
}.filter { it.title.isUsableCatalogText() && it.mediaType in setOf("ANIME", "MANGA", "MOVIE", "TV") }

internal suspend fun searchAniList(mediaType: String, search: String): List<CatalogItem> = withContext(Dispatchers.IO) {
    val quotedSearch = JSONObject.quote(search)
    val typeName = if (mediaType == "anime") "ANIME" else "MANGA"
    val graph = """
        query AnnieSearch {
          Page(page: 1, perPage: 10) {
            media(search: $quotedSearch, type: $typeName, isAdult: false, sort: SEARCH_MATCH) {
              id
              type
              title { english romaji native }
              startDate { year }
              episodes
              chapters
              status
              format
              duration
              description(asHtml: false)
              genres
              staff(sort: RELEVANCE, perPage: 5) {
                edges { role node { name { full } } }
              }
              relations {
                edges {
                  relationType
                  node {
                    id
                    type
                    format
                    title { english romaji native }
                    startDate { year }
                    episodes
                    coverImage { large }
                  }
                }
              }
              coverImage { large }
            }
          }
        }
    """.trimIndent()
    val payload = httpJson("https://graphql.anilist.co", method = "POST", body = JSONObject().put("query", graph).toString())
    parseAniListSearchPayload(payload, mediaType)
}

internal fun parseAniListSearchPayload(payload: String, mediaType: String): List<CatalogItem> {
    val rows = JSONObject(payload).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val titles = row.optJSONObject("title") ?: continue
            val name = titleFrom(titles)?.takeIf(String::isUsableCatalogText) ?: continue
            val itemId = row.optInt("id")
            if (itemId <= 0) continue
            val itemType = row.usableString("type") ?: continue
            val itemFormat = row.usableString("format").orEmpty()
            val itemYear = row.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 }
            val creators = buildList {
                val edges = row.optJSONObject("staff")?.optJSONArray("edges") ?: return@buildList
                for (staffIndex in 0 until edges.length()) {
                    val edge = edges.optJSONObject(staffIndex) ?: continue
                    val role = edge.optString("role")
                    if (!role.contains("story", ignoreCase = true) && !role.contains("art", ignoreCase = true)) continue
                    val staffName = edge.optJSONObject("node")?.optJSONObject("name")?.optString("full").orEmpty()
                    if (staffName.isNotBlank()) add("$staffName · $role")
                }
            }
            val genres = row.optJSONArray("genres").toStringList()
            val linkedSeasons = buildList {
                val edges = row.optJSONObject("relations")?.optJSONArray("edges") ?: return@buildList
                for (edgeIndex in 0 until edges.length()) {
                    val edge = edges.optJSONObject(edgeIndex) ?: continue
                    if (edge.optString("relationType") !in setOf("SEQUEL", "PREQUEL")) continue
                    val node = edge.optJSONObject("node") ?: continue
                    if (node.optString("type") != "ANIME" || node.optString("format") != "TV") continue
                    val relatedName = titleFrom(node.optJSONObject("title")) ?: continue
                    add(SeasonItem(
                        id = node.optInt("id"),
                        title = relatedName,
                        image = node.optJSONObject("coverImage")?.optString("large").orEmpty(),
                        year = node.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 },
                        episodes = node.optInt("episodes").takeIf { it > 0 }
                    ))
                }
            }.plus(
                if (itemType == "ANIME" && itemFormat == "TV") listOf(
                    SeasonItem(itemId, name, row.optJSONObject("coverImage")?.optString("large").orEmpty(), itemYear,
                        row.optInt("episodes").takeIf { it > 0 })
                ) else emptyList()
            ).distinctBy { it.id }.sortedWith(compareBy<SeasonItem> { it.year ?: Int.MAX_VALUE }.thenBy { it.id })
            add(CatalogItem(
                id = itemId,
                mediaType = itemType,
                title = name,
                image = row.optJSONObject("coverImage")?.optString("large").orEmpty(),
                year = itemYear,
                status = row.optString("status"),
                episodes = row.optInt("episodes").takeIf { it > 0 },
                chapters = row.optInt("chapters").takeIf { it > 0 },
                format = itemFormat,
                seasons = linkedSeasons,
                creator = creators.firstOrNull(),
                genres = genres,
                summary = row.optString("description"),
                runtimeMinutes = row.optInt("duration").takeIf { it > 0 },
                sourceLabel = "AniList",
                sourceUrl = "https://anilist.co/${if (itemType == "MANGA") "manga" else "anime"}/$itemId"
            ))
        }
    }
}

internal suspend fun searchTvMaze(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    parseTvMazeSearchPayload(httpJson("https://api.tvmaze.com/search/shows?q=$encoded"))
}

internal fun parseTvMazeSearchPayload(payload: String): List<CatalogItem> {
    val rows = JSONArray(payload)
    return buildList {
        for (index in 0 until rows.length()) {
            val show = rows.optJSONObject(index)?.optJSONObject("show") ?: continue
            val id = show.optInt("id")
            val title = show.usableString("name") ?: continue
            if (id <= 0) continue
            val premiered = show.optString("premiered")
            val image = show.optJSONObject("image")?.optString("original")?.takeIf { it.isNotBlank() }
                ?: show.optJSONObject("image")?.optString("medium").orEmpty()
            add(CatalogItem(
                id = id,
                mediaType = "TV",
                title = title,
                image = image,
                year = premiered.take(4).toIntOrNull(),
                status = show.optString("status", "UNKNOWN"),
                episodes = null,
                chapters = null,
                format = show.optString("type"),
                genres = show.optJSONArray("genres").toStringList(),
                summary = cleanSummary(show.optString("summary")),
                runtimeMinutes = show.optInt("averageRuntime").takeIf { it > 0 }
                    ?: show.optInt("runtime").takeIf { it > 0 },
                sourceLabel = "TVmaze",
                sourceUrl = show.optString("url").replace("http://", "https://")
            ))
        }
    }
}

internal suspend fun searchWikidataMovies(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val searchPayload = httpJson("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&uselang=en&type=item&limit=10&format=json")
    val ids = parseWikidataSearchIds(searchPayload)
    if (ids.isEmpty()) return@withContext emptyList()

    val entitiesPayload = fetchWikidataEntities(ids)
    val entityMap = JSONObject(entitiesPayload).optJSONObject("entities") ?: return@withContext emptyList()
    val movieEntities = ids.mapNotNull { id ->
        val entity = entityMap.optJSONObject(id) ?: return@mapNotNull null
        if (!entity.has("missing") && isFilmEntity(entity)) id to entity else null
    }
    if (movieEntities.isEmpty()) return@withContext emptyList()

    val linkedIds = movieEntities.flatMap { (_, entity) -> listOf("P57", "P136").flatMap { property -> claimIds(entity, property) } }.distinct()
    val labels = if (linkedIds.isNotEmpty()) parseWikidataLabels(fetchWikidataEntities(linkedIds, props = "labels")) else emptyMap()
    movieEntities.map { (id, entity) -> parseWikidataMovie(id, entity, labels) }
}

internal fun parseWikidataSearchIds(payload: String): List<String> {
    val rows = JSONObject(payload).optJSONArray("search") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            rows.optJSONObject(index)?.optString("id")?.takeIf { it.matches(Regex("Q[0-9]+")) }?.let(::add)
        }
    }.distinct().take(10)
}

internal fun parseWikidataMovie(id: String, entity: JSONObject, labels: Map<String, String> = emptyMap()): CatalogItem {
    val claims = entity.optJSONObject("claims") ?: JSONObject()
    val imageFile = claimString(claims, "P18")
    val image = imageFile?.let { "https://commons.wikimedia.org/wiki/Special:FilePath/${urlPathSegment(it)}" }.orEmpty()
    val year = claimString(claims, "P577")?.let { Regex("[0-9]{4}").find(it)?.value?.toIntOrNull() }
    val runtime = claimMinutes(claims, "P2047")
    val directors = claimIds(entity, "P57").mapNotNull { labels[it] }.distinct()
    val genres = claimIds(entity, "P136").mapNotNull { labels[it] }.distinct()
    return CatalogItem(
        id = id.removePrefix("Q").toIntOrNull() ?: 0,
        mediaType = "MOVIE",
        title = entity.optJSONObject("labels")?.optJSONObject("en")?.usableString("value").orEmpty(),
        image = image,
        year = year,
        status = "METADATA",
        episodes = null,
        chapters = null,
        creator = directors.firstOrNull(),
        genres = genres,
        summary = entity.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value").orEmpty(),
        runtimeMinutes = runtime,
        sourceLabel = "Wikidata",
        sourceUrl = "https://www.wikidata.org/wiki/$id"
    )
}

private suspend fun fetchWikidataEntities(ids: List<String>, props: String = "labels|descriptions|claims"): String {
    val encodedIds = URLEncoder.encode(ids.distinct().take(50).joinToString("|"), Charsets.UTF_8.name())
    return httpJson("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$encodedIds&props=$props&languages=en&format=json")
}

private fun parseWikidataLabels(payload: String): Map<String, String> {
    val entities = JSONObject(payload).optJSONObject("entities") ?: return emptyMap()
    return buildMap {
        val keys = entities.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            entities.optJSONObject(id)?.optJSONObject("labels")?.optJSONObject("en")?.optString("value")
                ?.takeIf { it.isNotBlank() }?.let { put(id, it) }
        }
    }
}

private fun isFilmEntity(entity: JSONObject): Boolean = claimIds(entity, "P31").any { it in FILM_INSTANCE_IDS }

private val FILM_INSTANCE_IDS = setOf("Q11424", "Q202866", "Q24869", "Q506240")

private fun claimIds(entity: JSONObject, property: String): List<String> {
    val rows = entity.optJSONObject("claims")?.optJSONArray(property) ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val id = rows.optJSONObject(index)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                ?.optJSONObject("value")?.optString("id")
            id?.takeIf { it.matches(Regex("Q[0-9]+")) }?.let(::add)
        }
    }
}

private fun claimString(claims: JSONObject, property: String): String? {
    val value = claims.optJSONArray(property)?.optJSONObject(0)?.optJSONObject("mainsnak")
        ?.optJSONObject("datavalue")?.opt("value") ?: return null
    return when (value) {
        is JSONObject -> value.optString("time").takeIf { it.isNotBlank() }
            ?: value.optString("amount").takeIf { it.isNotBlank() }
        else -> value.toString()
    }
}

private fun claimMinutes(claims: JSONObject, property: String): Int? {
    val value = claims.optJSONArray(property)?.optJSONObject(0)?.optJSONObject("mainsnak")
        ?.optJSONObject("datavalue")?.optJSONObject("value") ?: return null
    val amount = value.optString("amount").removePrefix("+").toDoubleOrNull() ?: return null
    val unit = value.optString("unit").substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
    val minutes = when (unit) {
        "Q7727" -> amount
        "Q11574" -> amount / 60.0
        "Q25235" -> amount * 60.0
        else -> return null
    }
    return minutes.toInt().takeIf { it > 0 }
}

private fun titleFrom(titles: JSONObject?): String? = titles?.let {
    it.usableString("english") ?: it.usableString("romaji") ?: it.usableString("native")
}

private fun JSONObject.usableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return opt(key)?.toString()?.trim()?.takeIf(String::isUsableCatalogText)
}

private fun String.isUsableCatalogText(): Boolean = isNotBlank() && !equals("null", ignoreCase = true) && !equals("undefined", ignoreCase = true)

private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
    buildList { for (index in 0 until array.length()) array.opt(index)?.toString()?.trim()?.takeIf(String::isUsableCatalogText)?.let(::add) }
} ?: emptyList()

private fun cleanSummary(value: String): String = value
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("\\s+"), " ").trim()

private fun urlPathSegment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun httpJson(url: String, method: String = "GET", body: String? = null): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 12_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
        if (url.contains("wikidata.org")) setRequestProperty("User-Agent", "AnnieAndroid/0.1 (https://github.com/Tomex777/Build)")
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
    }
    try {
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Catalog API HTTP $code")
        return response
    } finally {
        connection.disconnect()
    }
}
