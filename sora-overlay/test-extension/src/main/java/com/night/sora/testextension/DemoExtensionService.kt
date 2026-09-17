package com.night.sora.testextension

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

class DemoExtensionService : Service() {
    private val descriptor = ExtensionDescriptor(
        id = "night.sora.demo.sources",
        name = "Sora Demo Sources",
        version = "0.1.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Night",
        description = "Separate diagnostic APK for validating Sora Extension API v1.",
        contentTypes = setOf("anime", "manga", "movie", "tv", "music", "memes"),
        capabilities = setOf("diagnostic", "browse", "search", "details", "episodes", "chapters", "pages", "streams", "lyrics", "relatedArtists", "feed"),
        permissions = listOf(ExtensionPermission("network", listOf("storage.googleapis.com", "picsum.photos", "example.invalid"))),
        sources = listOf(
            SourceDescriptor("demo.anime", "Demo Anime", setOf("anime"), setOf("browse", "search", "details", "episodes", "streams")),
            SourceDescriptor("demo.manga", "Demo Manga", setOf("manga"), setOf("browse", "search", "details", "chapters", "pages")),
            SourceDescriptor("demo.video", "Demo Movies & TV", setOf("movie", "tv"), setOf("browse", "search", "details", "episodes", "streams")),
            SourceDescriptor("demo.music", "Demo Music", setOf("music"), setOf("browse", "search", "details", "streams", "lyrics", "relatedArtists")),
            SourceDescriptor("demo.memes", "Demo Memes", setOf("memes"), setOf("browse", "search", "details", "feed")),
        ),
    )

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != ExtensionContract.MSG_REQUEST) return
            val callerPackages = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if ("com.night.sora" !in callerPackages) {
                val denied = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(ExtensionContract.KEY_REQUEST_ID, msg.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty())
                        putBoolean(ExtensionContract.KEY_OK, false)
                        putString(ExtensionContract.KEY_ERROR, "Caller is not Sora Core")
                    }
                }
                runCatching { msg.replyTo?.send(denied) }
                return
            }
            val data = msg.data
            val requestId = data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty()
            val method = data.getString(ExtensionContract.KEY_METHOD).orEmpty()
            val payload = runCatching { JSONObject(data.getString(ExtensionContract.KEY_PAYLOAD_JSON).orEmpty().ifBlank { "{}" }) }
                .getOrDefault(JSONObject())

            val result = runCatching {
                when (method) {
                    ExtensionContract.Method.MANIFEST -> descriptor.toJson()
                    ExtensionContract.Method.BROWSE -> DemoCatalog.browse(payload.optString("type"))
                    ExtensionContract.Method.SEARCH -> DemoCatalog.search(payload.optString("query"), payload.optString("type"))
                    ExtensionContract.Method.DETAILS -> DemoCatalog.details(payload.optString("id"))
                    ExtensionContract.Method.EPISODES -> DemoCatalog.episodes(payload.optString("id"))
                    ExtensionContract.Method.CHAPTERS -> DemoCatalog.chapters(payload.optString("id"))
                    ExtensionContract.Method.PAGES -> DemoCatalog.pages(payload.optString("id"))
                    ExtensionContract.Method.STREAMS -> DemoCatalog.streams(payload.optString("id"))
                    ExtensionContract.Method.LYRICS -> DemoCatalog.lyrics(payload.optString("id"))
                    ExtensionContract.Method.RELATED_ARTISTS -> DemoCatalog.relatedArtists(payload.optString("id"))
                    ExtensionContract.Method.FEED -> DemoCatalog.feed()
                    else -> error("Unsupported method: $method")
                }
            }

            val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
                this.data = Bundle().apply {
                    putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                    putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                    result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                    result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "Unknown extension error") }
                }
            }
            runCatching { msg.replyTo?.send(response) }
        }
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
