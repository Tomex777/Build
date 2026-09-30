package com.night.spotui

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class TrackLyrics(
    val plain: String?,
    val synced: List<LyricLine>,
    val instrumental: Boolean = false,
)

class LyricsRepository {
    private val cache = ConcurrentHashMap<String, TrackLyrics>()
    private val unavailable = ConcurrentHashMap.newKeySet<String>()

    suspend fun lyrics(track: Track): Result<TrackLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val key = listOf(track.title, track.artist, track.album, track.durationSeconds)
                .joinToString("|")
                .lowercase()
            cache[key]?.let { return@runCatching it }
            if (unavailable.contains(key)) error("Lyrics unavailable")

            val direct = requestGet(track)
            val lyrics = direct ?: requestSearch(track)
            if (lyrics == null) {
                unavailable.add(key)
                error("Lyrics unavailable for this track")
            }
            cache[key] = lyrics
            lyrics
        }
    }

    private fun requestGet(track: Track): TrackLyrics? {
        val params = linkedMapOf(
            "track_name" to track.title,
            "artist_name" to track.artist,
        )
        if (track.album.isNotBlank()) params["album_name"] = track.album
        if (track.durationSeconds in 1L..3600L) params["duration"] = track.durationSeconds.toString()

        val url = "https://lrclib.net/api/get?" + params.entries.joinToString("&") {
            encode(it.key) + "=" + encode(it.value)
        }
        val response = request(url) ?: return null
        return parseRecord(JSONObject(response), track)
    }

    private fun requestSearch(track: Track): TrackLyrics? {
        val url = "https://lrclib.net/api/search?track_name=" + encode(track.title) +
            "&artist_name=" + encode(track.artist)
        val response = request(url) ?: return null
        val array = JSONArray(response)
        if (array.length() == 0) return null

        val candidates = (0 until array.length())
            .mapNotNull(array::optJSONObject)
            .filter { metadataMatches(it, track) }
            .sortedBy { candidate ->
                val duration = candidate.optDouble("duration", 0.0).toLong()
                if (track.durationSeconds > 0 && duration > 0) {
                    kotlin.math.abs(duration - track.durationSeconds)
                } else {
                    0L
                }
            }
        return candidates.firstNotNullOfOrNull { parseRecord(it, track) }
    }

    private fun request(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty(
                "User-Agent",
                "SpotUI/0.2 (https://github.com/Tomex777/Build)",
            )
            setRequestProperty("Accept", "application/json")
        }
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                429 -> null
                else -> null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecord(item: JSONObject, track: Track): TrackLyrics? {
        if (!metadataMatches(item, track)) return null

        val instrumental = item.optBoolean("instrumental")
        val plain = item.optString("plainLyrics").takeIf(String::isNotBlank)
        val syncedRaw = item.optString("syncedLyrics").takeIf(String::isNotBlank)
        val synced = syncedRaw?.let(::parseSynced).orEmpty()

        if (!instrumental && plain.isNullOrBlank() && synced.isEmpty()) return null
        if (!instrumental && !hasUsefulLyrics(plain, synced)) return null
        return TrackLyrics(
            plain = plain,
            synced = synced,
            instrumental = instrumental,
        )
    }

    private fun metadataMatches(item: JSONObject, track: Track): Boolean {
        val resultTitle = item.optString("trackName").normalizedMetadata()
        val resultArtist = item.optString("artistName").normalizedMetadata()
        val wantedTitle = track.title.normalizedMetadata()
        val wantedArtist = track.artist.normalizedMetadata()

        if (resultTitle.isNotBlank() && wantedTitle.isNotBlank() &&
            resultTitle != wantedTitle &&
            !resultTitle.contains(wantedTitle) &&
            !wantedTitle.contains(resultTitle)
        ) return false

        if (resultArtist.isNotBlank() && wantedArtist.isNotBlank() &&
            resultArtist != wantedArtist &&
            !resultArtist.contains(wantedArtist) &&
            !wantedArtist.contains(resultArtist)
        ) return false

        val resultDuration = item.optDouble("duration", 0.0).toLong()
        if (track.durationSeconds > 0 && resultDuration > 0 &&
            kotlin.math.abs(resultDuration - track.durationSeconds) > 8L
        ) return false

        return true
    }

    private fun hasUsefulLyrics(plain: String?, synced: List<LyricLine>): Boolean {
        val text = buildString {
            if (!plain.isNullOrBlank()) append(plain)
            if (synced.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(synced.joinToString(" ") { it.text })
            }
        }.trim()

        val words = text.split(Regex("""\s+""")).filter(String::isNotBlank)
        // Reject obvious upstream garbage such as a one-word probe/placeholder,
        // while preserving short but legitimate lyric fragments.
        return words.size >= 4 || text.length >= 24
    }

    private fun String.normalizedMetadata(): String =
        lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    private fun parseSynced(raw: String): List<LyricLine> {
        val regex = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
        return buildList {
            raw.lineSequence().forEach { line ->
                val matches = regex.findAll(line).toList()
                if (matches.isEmpty()) return@forEach
                val text = line.replace(regex, "").trim()
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        1 -> (fraction.toLongOrNull() ?: 0L) * 100
                        2 -> (fraction.toLongOrNull() ?: 0L) * 10
                        3 -> fraction.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    add(
                        LyricLine(
                            timeMs = minutes * 60_000 + seconds * 1_000 + millis,
                            text = text,
                        )
                    )
                }
            }
        }.sortedBy(LyricLine::timeMs)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
