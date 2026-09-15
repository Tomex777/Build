package com.night.sora.liveextension

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.ExtensionPermission
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.extension.api.toJson
import org.json.JSONObject
import java.util.concurrent.Executors

class LiveExtensionService : Service() {
    private val executor = Executors.newFixedThreadPool(3)

    private val descriptor = ExtensionDescriptor(
        id = "night.sora.live.sources",
        name = "Sora Live Sources",
        version = "0.2.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Night",
        description = "Temporary network-backed discovery sources for Movies, TV and Music.",
        contentTypes = setOf("movie", "tv", "music"),
        capabilities = setOf("browse", "search", "details", "episodes", "lyrics", "relatedArtists"),
        permissions = listOf(
            ExtensionPermission("network", listOf("api.tvmaze.com", "itunes.apple.com")),
        ),
        sources = listOf(
            SourceDescriptor("live.itunes.movies", "iTunes Movies", setOf("movie"), setOf("browse", "search", "details")),
            SourceDescriptor("live.tvmaze.tv", "TVmaze", setOf("tv"), setOf("browse", "search", "details", "episodes")),
            SourceDescriptor("live.itunes.music", "iTunes Music", setOf("music"), setOf("browse", "search", "details", "lyrics", "relatedArtists")),
        ),
    )

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != ExtensionContract.MSG_REQUEST) return
            val callerPackages = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if ("com.night.sora" !in callerPackages) {
                respond(msg, Result.failure(IllegalAccessException("Caller is not Sora Core")))
                return
            }

            val requestId = msg.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty()
            val method = msg.data.getString(ExtensionContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(msg.data.getString(ExtensionContract.KEY_PAYLOAD_JSON).orEmpty().ifBlank { "{}" })
            }.getOrDefault(JSONObject())
            val replyTo = msg.replyTo

            executor.execute {
                val result = runCatching {
                    val sourceId = payload.optString("sourceId")
                    val id = payload.optString("id")
                    when (method) {
                        ExtensionContract.Method.MANIFEST -> descriptor.toJson()
                        ExtensionContract.Method.BROWSE -> LiveCatalog.browse(sourceId, payload.optString("type"))
                        ExtensionContract.Method.SEARCH -> LiveCatalog.search(sourceId, payload.optString("query"))
                        ExtensionContract.Method.DETAILS -> LiveCatalog.details(sourceId, id)
                        ExtensionContract.Method.EPISODES -> LiveCatalog.episodes(sourceId, id)
                        ExtensionContract.Method.CHAPTERS -> LiveCatalog.chapters(sourceId, id)
                        ExtensionContract.Method.PAGES -> LiveCatalog.pages(sourceId, id)
                        ExtensionContract.Method.STREAMS -> LiveCatalog.streams(sourceId, id)
                        ExtensionContract.Method.LYRICS -> LiveCatalog.lyrics(sourceId, id)
                        ExtensionContract.Method.RELATED_ARTISTS -> LiveCatalog.relatedArtists(sourceId, id)
                        ExtensionContract.Method.FEED -> LiveCatalog.feed(sourceId)
                        else -> error("Unsupported method: $method")
                    }
                }
                val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                        putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                        result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                        result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "Live source error") }
                    }
                }
                runCatching { replyTo?.send(response) }
            }
        }
    })

    private fun respond(msg: Message, result: Result<String>) {
        val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(ExtensionContract.KEY_REQUEST_ID, msg.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty())
                putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "Extension error") }
            }
        }
        runCatching { msg.replyTo?.send(response) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
