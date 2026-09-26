Warning: truncated output (original token count: 5041)
Total output lines: 535

package com.night.spotui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.night.spotui.source.api.MusicSourceContract
import com.night.spotui.source.api.MusicSourceCallException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class BrowserSessionSpec(
    val url: String,
    val title: String,
    val scripts: Map<String, String>,
)

interface MusicSource {
    val name: String
    suspend fun home(): Result<List<Track>>
    suspend fun search(query: String): Result<List<Track>>
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun artist(query: String, artistId: String? = null): Result<ArtistCatalog>
    suspend fun album(query: String, albumId: String? = null): Result<AlbumCatalog>
    suspend fun resolveCandidates(track: Track): Result<List<ResolvedAudio>>
    suspend fun resolve(track: Track): Result<ResolvedAudio> =
        resolveCandidates(track).map { candidates ->
            candidates.firstOrNull() ?: error("No playable audio stream")
        }
    suspend fun browserSession(): Result<BrowserSessionSpec>
    suspend fun storeBrowserSession(
        cookieHeader: String,
        userAgent: String,
        visitorData: String = "",
        dataSyncId: String = "",
        authUser: String = "0",
    ): Result<Boolean>
}

class ExtensionMusicSource(context: Context) : MusicSource {
    private val appContext = context.applicationContext

    @Volatile
    private var activeTarget: Target? = null

    override val name: String
        get() = activeTarget?.name ?: "Music source"

    override suspend fun home(): Result<List<Track>> = runCatching {
        val target = target()
        parseTracks(
            call(
                target.component,
                MusicSourceContract.Method.BROWSE,
                JSONObject().put("sourceId", target.sourceId),
            ).getOrThrow()
        )
    }

    override suspend fun search(query: String): Result<List<Track>> = runCatching {
        val target = target()
        parseTracks(
            call(
                target.component,
                MusicSourceContract.Method.SEARCH,
                JSONObject()
                    .put("sourceId", target.sourceId)
                    .put("query", query),
            ).getOrThrow()
        )
    }

    override suspend fun suggestions(query: String): Result<List<String>> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.SUGGESTIONS,
            JSONObject()
                .put("sourceId", target.sourceId)
                .put("query", query),
        ).getOrThrow()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    override suspend fun artist(query: String, artistId: String?): Result<ArtistCatalog> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.ARTIST,
            JSONObject()
                .put("sourceId", target.sourceId)
                .put("query", query)
                .put("artistId", artistId.orEmpty()),
        ).getOrThrow()
        parseArtist(JSONObject(raw))
    }

    override suspend fun album(query: String, albumId: String?): Result<AlbumCatalog> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.ALBUM,
            JSONObject()
                .put("sourceId", target.sourceId)
                .put("query", query)
                .put("albumId", albumId.orEmpty()),
        ).getOrThrow()
        parseAlbum(JSONObject(raw))
    }

    override suspend fun resolveCandidates(track: Track): Result<List<ResolvedAudio>> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.STREAMS,
            JSONObject()
                .put("sourceId", target.sourceId)
                .put("id", track.id),
        ).getOrThrow()

        val array = JSONArray(raw)
        val candidates = (0 until array.length())
            .mapNotNull(array::optJSONObject)
            .filter { it.optString("url").startsWith("http") }
            .map { item ->
                val headers = buildMap {
                    val objectHeaders = item.optJSONObject("headers")
                    if (objectHeaders != null) {
                        val keys = objectHeaders.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            objectHeaders.optString(key)
                                .takeIf(String::isNotBlank)
                                ?.let { put(key, it) }
                        }
                    }
                }
                ResolvedAudio(
                    url = item.getString("url"),
                    label = item.optString("label", "Audio"),
                    mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
                    headers = headers,
                )
            }
            .sortedWith(
                compareByDescending<ResolvedAudio> {
                    val mime = it.mimeType.orEmpty()
                    mime.contains("mp4", true) || mime.contains("aac", true)
                }.thenByDescending {
                    Regex("""(\d+)\s*kbps""", RegexOption.IGNORE_CASE)
                        .find(it.label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                }
            )

        if (candidates.isEmpty()) error("${target.name} returned no playable audio stream")
        candidates
    }

    override suspend fun browserSession(): Result<BrowserSessionSpec> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.BROWSER_SESSION,
            JSONObject().put("sourceId", target.sourceId),
        ).getOrThrow()
        val obj = JSONObject(raw)
        val scriptObject = obj.optJSONObject("sessionScripts")
        val scripts = buildMap {
            if (scriptObject != null) {
                val keys = scriptObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    scriptObject.optString(key)
                        .takeIf(String::isNotBlank)
                        ?.let { put(key, it) }
                }
            }
        }
        BrowserSessionSpec(
            url = obj.getString("url"),
            title = obj.optString("title", target.name),
            scripts = scripts,
        )
    }

    override suspend fun storeBrowserSession(
        cookieHeader: String,
        userAgent: String,
        visitorData: String,
        dataSyncId: String,
        authUser: String,
    ): Result<Boolean> = runCatching {
        val target = target()
        val raw = call(
            target.component,
            MusicSourceContract.Method.STORE_SESSION,
            JSONObject()
                .put("sourceId", target.sourceId)
                .put("cookieHeader", cookieHeader)
                .put("userAgent", userAgent)
                .put("visitorData", visitorData)
                .put("dataSyncId", dataSyncId)
                .put("authUser", authUser),
        ).getOrThrow()
        JSONObject(raw).optBoolean("s…1041 tokens truncated…          MusicSourceContract.KEY_ERROR_CODE
                        ).orEmpty().ifBlank { MusicSourceContract.ERROR_CODE_GENERIC }
                        Result.failure(
                            MusicSourceCallException(
                                errorCode,
                                message.data.getString(
                                    MusicSourceContract.KEY_ERROR
                                ) ?: "Music source call failed"
                            )
                        )
                    }
                    finish(result)
                    true
                }
            )

            val sourceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    runCatching {
                        val remote = Messenger(binder)
                        val request = Message.obtain(
                            null,
                            MusicSourceContract.MSG_REQUEST,
                        ).apply {
                            replyTo = reply
                            data = Bundle().apply {
                                putString(
                                    MusicSourceContract.KEY_REQUEST_ID,
                                    requestId,
                                )
                                putString(
                                    MusicSourceContract.KEY_METHOD,
                                    method,
                                )
                                putString(
                                    MusicSourceContract.KEY_PAYLOAD_JSON,
                                    payload.toString(),
                                )
                            }
                        }
                        remote.send(request)
                    }.onFailure { finish(Result.failure(it)) }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    finish(
                        Result.failure(
                            IllegalStateException("Music source disconnected")
                        )
                    )
                }

                override fun onBindingDied(name: ComponentName) {
                    finish(
                        Result.failure(
                            IllegalStateException("Music source binding died")
                        )
                    )
                }
            }
            connection = sourceConnection

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    runCatching { appContext.unbindService(sourceConnection) }
                }
            }

            val bound = runCatching {
                appContext.bindService(
                    Intent(MusicSourceContract.ACTION_BIND_SOURCE)
                        .setComponent(component),
                    sourceConnection,
                    Context.BIND_AUTO_CREATE,
                )
            }.getOrDefault(false)

            if (!bound) {
                finish(
                    Result.failure(
                        IllegalStateException("Could not connect to music source")
                    )
                )
            }
        }
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        Result.failure(IllegalStateException("Music source is taking too long. Try again."))
    }

    private fun parseTracks(raw: String): List<Track> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue

                val subtitle = item.optString("subtitle")
                val parts = subtitle.split(" · ", limit = 2)
                add(
                    Track(
                        id = id,
                        title = title,
                        artist = item.optString("artist").ifBlank {
                            parts.getOrNull(0).orEmpty().ifBlank { name }
                        },
                        artistId = item.optString("artistId"),
                        album = item.optString("album").ifBlank { parts.getOrNull(1).orEmpty() },
                        albumId = item.optString("albumId"),
                        artworkUrl = item.optString("artworkUrl")
                            .takeIf(String::isNotBlank),
                        durationSeconds = item.optLong("durationSeconds"),
                        explicit = item.optBoolean("explicit"),
                    )
                )
            }
        }
    }

    private fun parseArtist(root: JSONObject): ArtistCatalog = ArtistCatalog(
        id = root.optString("id"),
        name = root.optString("name"),
        artworkUrl = root.optString("artworkUrl").takeIf(String::isNotBlank),
        songs = parseTrackArray(root.optJSONArray("songs") ?: JSONArray()),
        releases = parseAlbumSummaries(root.optJSONArray("releases") ?: JSONArray()),
    )

    private fun parseAlbum(root: JSONObject): AlbumCatalog = AlbumCatalog(
        id = root.optString("id"),
        title = root.optString("title"),
        artist = root.optString("artist"),
        artistId = root.optString("artistId"),
        year = root.optInt("year"),
        artworkUrl = root.optString("artworkUrl").takeIf(String::isNotBlank),
        songs = parseTrackArray(root.optJSONArray("songs") ?: JSONArray()),
    )

    private fun parseTrackArray(array: JSONArray): List<Track> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            add(
                Track(
                    id = id,
                    title = title,
                    artist = item.optString("artist").ifBlank { name },
                    artistId = item.optString("artistId"),
                    album = item.optString("album"),
                    albumId = item.optString("albumId"),
                    artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                    durationSeconds = item.optLong("durationSeconds"),
                    explicit = item.optBoolean("explicit"),
                )
            )
        }
    }

    private fun parseAlbumSummaries(array: JSONArray): List<AlbumSummary> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            add(
                AlbumSummary(
                    id = id,
                    title = title,
                    artist = item.optString("artist"),
                    artistId = item.optString("artistId"),
                    year = item.optInt("year"),
                    type = item.optString("type", "Album"),
                    artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                )
            )
        }
    }

    private fun contains(array: JSONArray?, value: String): Boolean {
        if (array == null) return false
        for (index in 0 until array.length()) {
            if (array.optString(index).equals(value, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private data class Target(
        val component: ComponentName,
        val sourceId: String,
        val name: String,
    )

    companion object {
        private const val CALL_TIMEOUT_MS = 30_000L
    }
}
