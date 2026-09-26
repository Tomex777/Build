package com.night.spotui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Local, source-independent taste model.
 *
 * Nothing here depends on YouTube Music or SoundCloud. Sources only provide
 * candidates; SpotUI decides which candidates deserve to be surfaced.
 */
class TasteStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("spotui_taste_v2", Context.MODE_PRIVATE)

    data class ArtistTaste(
        val name: String,
        val score: Double,
        val lastPlayedEpochMs: Long,
    )

    @Synchronized
    fun recordSearch(query: String, results: List<Track>) {
        val clean = query.trim()
        if (clean.isBlank()) return
        rememberQuery(clean)

        val normalized = clean.lowercase()
        results.take(4).forEachIndexed { index, track ->
            val artistMatch = track.artist.lowercase().contains(normalized) ||
                normalized.contains(track.artist.lowercase())
            val titleMatch = track.title.lowercase().contains(normalized)
            val amount = when {
                index == 0 && artistMatch -> 1.5
                artistMatch -> 0.9
                titleMatch -> 0.55
                else -> 0.2
            }
            boostArtist(track.artist, amount, touchRecency = false)
            boostTrack(track, amount * 0.65, touchRecency = false)
        }
    }

    @Synchronized
    fun recordPlay(track: Track) {
        boostArtist(track.artist, 2.0, touchRecency = true)
        boostTrack(track, 1.4, touchRecency = true)
    }

    @Synchronized
    fun recordCompleted(track: Track) {
        boostArtist(track.artist, 3.5, touchRecency = true)
        boostTrack(track, 2.6, touchRecency = true)
    }

    @Synchronized
    fun recordSkip(track: Track) {
        boostArtist(track.artist, -0.35, touchRecency = false)
        boostTrack(track, -0.8, touchRecency = false)
    }

    @Synchronized
    fun recordLike(track: Track, liked: Boolean) {
        val artistDelta = if (liked) 6.0 else -4.0
        val trackDelta = if (liked) 5.0 else -3.0
        boostArtist(track.artist, artistDelta, touchRecency = liked)
        boostTrack(track, trackDelta, touchRecency = liked)
    }

    @Synchronized
    fun topArtists(limit: Int = 6): List<ArtistTaste> {
        val artists = readObject(KEY_ARTISTS)
        val values = buildList {
            val keys = artists.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = artists.optJSONObject(key) ?: continue
                val name = item.optString("name").ifBlank { key }
                add(
                    ArtistTaste(
                        name = name,
                        score = item.optDouble("score", 0.0),
                        lastPlayedEpochMs = item.optLong("last", 0L),
                    )
                )
            }
        }
        return values
            .filter { it.score > 0.15 }
            .sortedWith(
                compareByDescending<ArtistTaste> { it.score }
                    .thenByDescending { it.lastPlayedEpochMs }
            )
            .take(limit)
    }

    @Synchronized
    fun rank(tracks: List<Track>): List<Track> {
        if (tracks.size < 2) return tracks
        val artists = readObject(KEY_ARTISTS)
        val trackScores = readObject(KEY_TRACKS)
        return tracks.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Track>> { indexed ->
                    val track = indexed.value
                    artistScore(artists, track.artist) * 1.0 +
                        trackScore(trackScores, track.id) * 1.25
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    /**
     * Build the home feed from listening evidence first, while leaving a smaller
     * discovery lane so the model can learn new artists. Once taste exists the
     * feed is intentionally not just a reordered generic chart.
     */
    @Synchronized
    fun homeMix(
        personalCandidates: List<Track>,
        discoveryCandidates: List<Track>,
        limit: Int = 36,
    ): List<Track> {
        val personal = rank(personalCandidates.distinctBy(Track::id)).toMutableList()
        val discovery = rank(discoveryCandidates.distinctBy(Track::id))
            .filterNot { candidate -> personal.any { it.id == candidate.id } }
            .toMutableList()

        if (!hasTaste() || personal.isEmpty()) {
            return discovery.take(limit)
        }

        val mixed = mutableListOf<Track>()
        var personalSinceDiscovery = 0
        while (mixed.size < limit && (personal.isNotEmpty() || discovery.isNotEmpty())) {
            val takeDiscovery = personalSinceDiscovery >= 3 && discovery.isNotEmpty()
            val next = when {
                takeDiscovery -> discovery.removeAt(0).also { personalSinceDiscovery = 0 }
                personal.isNotEmpty() -> personal.removeAt(0).also { personalSinceDiscovery += 1 }
                discovery.isNotEmpty() -> discovery.removeAt(0).also { personalSinceDiscovery = 0 }
                else -> break
            }
            if (mixed.none { it.id == next.id }) mixed += next
        }
        return mixed
    }

    @Synchronized
    fun recentQueries(limit: Int = 8): List<String> {
        val array = readArray(KEY_RECENT_QUERIES)
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }.take(limit)
    }

    @Synchronized
    fun querySuggestions(query: String, limit: Int = 8): List<String> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()

        val recentQueries = readArray(KEY_RECENT_QUERIES)
        val artists = topArtists(12).map { it.name }
        val tracks = readObject(KEY_TRACKS)
        val trackLabels = buildList {
            val keys = tracks.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = tracks.optJSONObject(key) ?: continue
                val title = item.optString("title")
                val artist = item.optString("artist")
                if (title.isNotBlank()) add(title)
                if (artist.isNotBlank()) add(artist)
            }
        }

        val candidates = buildList {
            for (i in 0 until recentQueries.length()) {
                recentQueries.optString(i).takeIf(String::isNotBlank)?.let(::add)
            }
            addAll(artists)
            addAll(trackLabels)
        }

        return candidates
            .asSequence()
            .filter { it.lowercase().contains(needle) }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
    }

    @Synchronized
    fun hasTaste(): Boolean = topArtists(1).isNotEmpty()

    private fun rememberQuery(query: String) {
        val values = mutableListOf<String>()
        val current = readArray(KEY_RECENT_QUERIES)
        for (i in 0 until current.length()) {
            current.optString(i).takeIf(String::isNotBlank)?.let(values::add)
        }
        values.removeAll { it.equals(query, ignoreCase = true) }
        values.add(0, query)
        val result = JSONArray()
        values.take(20).forEach(result::put)
        prefs.edit().putString(KEY_RECENT_QUERIES, result.toString()).apply()
    }

    private fun boostArtist(name: String, delta: Double, touchRecency: Boolean) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val root = readObject(KEY_ARTISTS)
        val key = clean.lowercase()
        val item = root.optJSONObject(key) ?: JSONObject()
        item.put("name", clean)
        item.put("score", max(-8.0, item.optDouble("score", 0.0) + delta))
        if (touchRecency) item.put("last", System.currentTimeMillis())
        root.put(key, item)
        prefs.edit().putString(KEY_ARTISTS, root.toString()).apply()
    }

    private fun boostTrack(track: Track, delta: Double, touchRecency: Boolean) {
        if (track.id.isBlank()) return
        val root = readObject(KEY_TRACKS)
        val item = root.optJSONObject(track.id) ?: JSONObject()
        item.put("title", track.title)
        item.put("artist", track.artist)
        item.put("score", max(-8.0, item.optDouble("score", 0.0) + delta))
        if (touchRecency) item.put("last", System.currentTimeMillis())
        root.put(track.id, item)
        prefs.edit().putString(KEY_TRACKS, root.toString()).apply()
    }

    private fun artistScore(root: JSONObject, artist: String): Double =
        root.optJSONObject(artist.trim().lowercase())?.optDouble("score", 0.0) ?: 0.0

    private fun trackScore(root: JSONObject, id: String): Double =
        root.optJSONObject(id)?.optDouble("score", 0.0) ?: 0.0

    private fun readObject(key: String): JSONObject =
        runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(prefs.getString(key, "[]") ?: "[]") }.getOrElse { JSONArray() }

    companion object {
        private const val KEY_ARTISTS = "artists"
        private const val KEY_TRACKS = "tracks"
        private const val KEY_RECENT_QUERIES = "recent_queries"
    }
}
