package com.nice.juicewrld

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MusicBrainzService(private val context: Context) {
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L
    private val catalogFile = File(context.filesDir, "catalog.json")

    private suspend fun get(path: String): String = requestMutex.withLock {
        // MusicBrainz explicitly limits client applications to <= one request/second.
        val now = SystemClock.elapsedRealtime()
        val remaining = 1_150L - (now - lastRequestAt)
        if (lastRequestAt > 0L && remaining > 0L) delay(remaining)
        // Record request time before connect, even if it fails.
        lastRequestAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            val connection = (URL("https://musicbrainz.org/ws/2/$path").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "NIGHTJuiceWRLD/0.1 (https://github.com/Tomex777/Build)")
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("MusicBrainz returned HTTP $status")
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun releasesPage(offset: Int = 0): CatalogPage {
        val json = get("release-group?artist=$ARTIST_ID&limit=100&offset=$offset&release-group-status=website-default&fmt=json")
        return CatalogParser.parseReleaseGroups(json, offset)
    }

    suspend fun tracks(releaseGroupId: String): List<Track> {
        require(Regex("[a-f0-9-]{36}").matches(releaseGroupId))
        // Browse real official editions; then lookup the first edition's media.
        val releaseList = get("release?release-group=$releaseGroupId&status=official&limit=25&fmt=json")
        val releaseId = CatalogParser.parseReleases(releaseList) ?: return emptyList()
        val detail = get("release/$releaseId?inc=recordings&fmt=json")
        return CatalogParser.parseTracks(detail)
    }

    suspend fun readCache(): List<ReleaseGroup> = withContext(Dispatchers.IO) {
        try {
            if (!catalogFile.isFile) return@withContext emptyList()
            val entries = JSONObject(catalogFile.readText()).optJSONArray("items") ?: return@withContext emptyList()
            val items = mutableListOf<ReleaseGroup>()
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                items += ReleaseGroup(
                    id, title, item.optString("type"), item.optString("year"),
                    item.optString("date"),
                )
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun writeCache(items: List<ReleaseGroup>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title)
                .put("type", item.type).put("year", item.year).put("date", item.firstReleaseDate))
        }
        val temporary = File(context.filesDir, "catalog.tmp")
        temporary.writeText(JSONObject().put("items", array).toString())
        if (!temporary.renameTo(catalogFile)) {
            temporary.copyTo(catalogFile, overwrite = true)
            temporary.delete()
        }
    }
}
