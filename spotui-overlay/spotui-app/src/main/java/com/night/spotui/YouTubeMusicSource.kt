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
    suspend fun resolve(track: Track): Result<ResolvedAudio>
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

    override suspend fun resolve(track: Track): Result<ResolvedAudio> = runCatching {
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
        val item = candidates.firstOrNull {
            val mime = it.optString("mimeType")
            mime.contains("mp4", ignoreCase = true) || mime.contains("aac", ignoreCase = true)
        } ?: candidates.firstOrNull()
            ?: error("${target.name} returned no playable audio stream")

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
        JSONObject(raw).optBoolean("signedIn")
    }

    private suspend fun target(): Target {
        activeTarget?.let { return it }
        val discovered = discoverTarget()
        activeTarget = discovered
        return discovered
    }

    @Suppress("DEPRECATION")
    private suspend fun discoverTarget(): Target {
        val matches = appContext.packageManager.queryIntentServices(
            Intent(MusicSourceContract.ACTION_BIND_SOURCE),
            PackageManager.GET_META_DATA,
        )

        if (matches.isEmpty()) {
            error("No SpotUI music source extension is installed.")
        }

        var lastError: Throwable? = null
        for (match in matches) {
            val info = match.serviceInfo ?: continue
            val apiVersion = info.metaData?.getInt(
                MusicSourceContract.META_API_VERSION,
                -1,
            ) ?: -1
            if (apiVersion != MusicSourceContract.API_VERSION) continue

            val component = ComponentName(info.packageName, info.name)
            val manifest = call(
                component,
                MusicSourceContract.Method.MANIFEST,
                JSONObject(),
            ).getOrElse {
                lastError = it
                continue
            }

            val parsed = runCatching { parseManifest(component, manifest) }
            parsed.getOrNull()?.let { return it }
            lastError = parsed.exceptionOrNull()
        }

        throw lastError ?: IllegalStateException(
            "No compatible SpotUI music source extension was found."
        )
    }

    private fun parseManifest(component: ComponentName, raw: String): Target {
        val root = JSONObject(raw)
        require(root.optInt("apiVersion") == MusicSourceContract.API_VERSION) {
            "Unsupported music source API"
        }

        val sources = root.optJSONArray("sources") ?: JSONArray()
        for (index in 0 until sources.length()) {
            val source = sources.optJSONObject(index) ?: continue
            if (!contains(source.optJSONArray("contentTypes"), "music")) continue
            val sourceId = source.optString("id")
            if (sourceId.isBlank()) continue
            return Target(
                component = component,
                sourceId = sourceId,
                name = source.optString("name")
                    .ifBlank { root.optString("name", "Music source") },
            )
        }
        error("Extension does not expose a music source")
    }

    private suspend fun call(
        component: ComponentName,
        method: String,
        payload: JSONObject,
    ): Result<String> = try {
        withTimeout(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString()
            val finished = AtomicBoolean(false)
            var connection: ServiceConnection? = null

            fun finish(result: Result<String>) {
                if (!finished.compareAndSet(false, true)) return
                connection?.let { bound ->
                    runCatching { appContext.unbindService(bound) }
                }
                if (continuation.isActive) continuation.resume(result)
            }

            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.what != MusicSourceContract.MSG_RESPONSE) {
                        return@Handler true
                    }
                    if (
                        message.data.getString(MusicSourceContract.KEY_REQUEST_ID) !=
                        requestId
                    ) {
                        return@Handler true
                    }

                    val result = if (
                        message.data.getBoolean(MusicSourceContract.KEY_OK)
                    ) {
                        Result.success(
                            message.data.getString(
                                MusicSourceContract.KEY_RESULT_JSON
                            ).orEmpty()
                        )
                    } else {
                        Result.failure(
                            IllegalStateException(
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
                        artist = parts.getOrNull(0).orEmpty()
                            .ifBlank { name },
                        album = parts.getOrNull(1).orEmpty(),
                        artworkUrl = item.optString("artworkUrl")
                            .takeIf(String::isNotBlank),
                        durationSeconds = item.optLong("durationSeconds"),
                        explicit = item.optBoolean("explicit"),
                    )
                )
            }
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
