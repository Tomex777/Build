package com.nice.juicewrld

import org.json.JSONArray
import org.json.JSONObject

const val ARTIST_ID = "4e4ebde4-0c56-4dec-844b-6c73adcdd92d"
const val APPLE_MUSIC_ARTIST = "https://music.apple.com/us/artist/juice-wrld/1368733420"
const val MUSICBRAINZ_ARTIST = "https://musicbrainz.org/artist/$ARTIST_ID"

data class ReleaseGroup(
    val id: String,
    val title: String,
    val type: String,
    val year: String,
    val firstReleaseDate: String,
) {
    val coverUrl: String
        get() = "https://coverartarchive.org/release-group/$id/front-250"
}

data class Track(
    val position: String,
    val title: String,
    val milliseconds: Long?,
) {
    fun formattedLength(): String? {
        val total = (milliseconds ?: return null) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}

data class CatalogPage(
    val entries: List<ReleaseGroup>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

object CatalogParser {
    private val TYPES = setOf("Album", "EP", "Single")

    fun parseReleaseGroups(raw: String, offset: Int = 0): CatalogPage {
        val objectValue = JSONObject(raw)
        val records = objectValue.optJSONArray("release-groups") ?: JSONArray()
        val result = (0 until records.length()).mapNotNull { index ->
            val record = records.optJSONObject(index) ?: return@mapNotNull null
            val id = record.optString("id")
            val title = record.optString("title").trim()
            val type = record.optString("primary-type", "Other")
            if (id.isBlank() || title.isBlank() || type !in TYPES) return@mapNotNull null
            val date = record.optString("first-release-date")
            ReleaseGroup(
                id = id,
                title = title,
                type = type,
                year = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "—",
                firstReleaseDate = date,
            )
        }
        val next = offset + records.length()
        return CatalogPage(
            entries = result.distinctBy { it.id }.sortedWith(
                compareByDescending<ReleaseGroup> { it.firstReleaseDate }.thenBy { it.title }
            ),
            nextOffset = next,
            hasMore = next < objectValue.optInt("release-group-count", next),
        )
    }

    fun parseReleases(raw: String): String? {
        val releases = JSONObject(raw).optJSONArray("releases") ?: return null
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optString("status").equals("Official", ignoreCase = true)) {
                val id = release.optString("id")
                if (id.isNotBlank()) return id
            }
        }
        return null
    }

    fun parseTracks(raw: String): List<Track> {
        val media = JSONObject(raw).optJSONArray("media") ?: return emptyList()
        val tracks = mutableListOf<Track>()
        for (disc in 0 until media.length()) {
            val medium = media.optJSONObject(disc) ?: continue
            val list = medium.optJSONArray("tracks") ?: continue
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                val time = item.optLong("length", -1L).takeIf { it > 0L }
                val number = item.optString("number").ifBlank { (index + 1).toString() }
                tracks.add(Track(number, title, time))
            }
        }
        return tracks
    }

    fun search(items: List<ReleaseGroup>, query: String, type: String): List<ReleaseGroup> {
        val needle = query.trim()
        return items.filter {
            (type == "All" || it.type == type) &&
                (needle.isBlank() || it.title.contains(needle, ignoreCase = true) ||
                    it.year.contains(needle, ignoreCase = true))
        }
    }

    fun merge(first: List<ReleaseGroup>, second: List<ReleaseGroup>): List<ReleaseGroup> =
        (first + second).distinctBy { it.id }.sortedWith(
            compareByDescending<ReleaseGroup> { it.firstReleaseDate }.thenBy { it.title }
        )
}
