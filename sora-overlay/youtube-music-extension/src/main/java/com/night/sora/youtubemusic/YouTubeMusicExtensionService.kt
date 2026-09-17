package com.night.sora.youtubemusic

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.metrolist.innertube.YouTube
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.ExtensionPermission
import com.night.sora.extension.api.ExtensionSessionContract
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.extension.api.toJson
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.Executors

class YouTubeMusicExtensionService : Service() {
    private val executor = Executors.newFixedThreadPool(3)

    private val descriptor = ExtensionDescriptor(
        id = "night.sora.youtube.music",
        name = "Sora YouTube Music",
        version = "0.1.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Night",
        description = "YouTube Music search and audio resolver for Sora.",
        contentTypes = setOf("music"),
        capabilities = setOf("catalog", "browse", "search", "details", "streams", ExtensionSessionContract.CAPABILITY_WEBVIEW),
        permissions = listOf(
            ExtensionPermission(
                "network",
                listOf("music.youtube.com", "youtube.com", "googlevideo.com", "googleapis.com", "accounts.google.com"),
            ),
        ),
        sources = listOf(
            SourceDescriptor(
                "youtube.music",
                "YouTube Music",
                setOf("music"),
                setOf("browse", "search", "details", "streams", ExtensionSessionContract.CAPABILITY_WEBVIEW),
            ),
        ),
    )

    override fun onCreate() {
        super.onCreate()
        YouTubeMusicCatalog.initialize(applicationContext)
        YouTubeMusicSession.restore(applicationContext)
    }

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
                    runBlocking {
                        when (method) {
                            ExtensionContract.Method.MANIFEST -> descriptor.toJson()
                            ExtensionContract.Method.BROWSE -> YouTubeMusicCatalog.browse(sourceId)
                            ExtensionContract.Method.SEARCH -> YouTubeMusicCatalog.search(sourceId, payload.optString("query"))
                            ExtensionContract.Method.DETAILS -> YouTubeMusicCatalog.details(sourceId, id)
                            ExtensionContract.Method.STREAMS -> resolveStreams(sourceId, id)
                            ExtensionSessionContract.METHOD_BROWSER_SESSION -> {
                                require(sourceId == "youtube.music") { "Unsupported YouTube Music source: $sourceId" }
                                YouTubeMusicSession.browserSession()
                            }
                            ExtensionSessionContract.METHOD_STORE_SESSION -> {
                                // Apply the browser page identifiers before validating the cookie so
                                // account_menu and the subsequent PoToken request see one coherent session.
                                payload.optString("visitorData").trim().takeIf(String::isNotBlank)?.let {
                                    YouTube.visitorData = it
                                }
                                payload.optString("dataSyncId").trim().substringBefore("||").takeIf(String::isNotBlank)?.let {
                                    YouTube.dataSyncId = it
                                }

                                val stored = YouTubeMusicCatalog.storeSession(
                                    sourceId = sourceId,
                                    cookieHeader = normalizeYouTubeCookieHeader(payload.optString("cookieHeader")),
                                    userAgent = payload.optString("userAgent"),
                                )
                                val signedIn = runCatching { JSONObject(stored).optBoolean("signedIn") }.getOrDefault(false)
                                YouTubeMusicSession.storePageContext(applicationContext, payload, signedIn)
                                stored
                            }
                            ExtensionContract.Method.EPISODES,
                            ExtensionContract.Method.CHAPTERS,
                            ExtensionContract.Method.PAGES,
                            ExtensionContract.Method.RELATED_ARTISTS,
                            ExtensionContract.Method.FEED -> "[]"
                            ExtensionContract.Method.LYRICS -> JSONObject()
                                .put("trackId", id)
                                .put("synced", false)
                                .put("text", "")
                                .toString()
                            else -> error("Unsupported method: $method")
                        }
                    }
                }

                result.exceptionOrNull()?.let { error ->
                    Log.e("SoraYouTubeMusic", "$method request failed: ${error.message}", error)
                }

                val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                        putBoolean(ExtensionContract.KEY_OK, result.isSuccess)
                        result.onSuccess { putString(ExtensionContract.KEY_RESULT_JSON, it) }
                        result.onFailure { putString(ExtensionContract.KEY_ERROR, it.message ?: "YouTube Music extension error") }
                    }
                }
                runCatching { replyTo?.send(response) }
            }
        }
    })

    private suspend fun resolveStreams(sourceId: String, id: String): String {
        return try {
            YouTubeMusicCatalog.streams(sourceId, id)
        } catch (cause: Throwable) {
            val detail = cause.message.orEmpty()
            val challenged = detail.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                detail.contains("confirm you", ignoreCase = true) ||
                detail.contains("not a bot", ignoreCase = true)
            if (challenged) {
                Log.w("SoraYouTubeMusic", "YouTube challenged playback; source browser sign-in required", cause)
                error(
                    "YouTube Music needs a signed-in session on this network. " +
                        "Open More → Extensions → Sora YouTube Music → Open YouTube Music, sign in, then try this track again."
                )
            }
            throw cause
        }
    }

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
