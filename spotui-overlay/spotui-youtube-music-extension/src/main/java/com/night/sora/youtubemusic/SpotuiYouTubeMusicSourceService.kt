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
import com.night.spotui.source.api.MusicSourceContract
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class SpotuiYouTubeMusicSourceService : Service() {
    private val executor = Executors.newFixedThreadPool(3)

    override fun onCreate() {
        super.onCreate()
        YouTubeMusicCatalog.initialize(applicationContext)
        YouTubeMusicSession.restore(applicationContext)
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MusicSourceContract.MSG_REQUEST) return

            val callerPackages = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if ("com.night.spotui" !in callerPackages) {
                respond(msg, Result.failure(IllegalAccessException("Caller is not SpotUI")))
                return
            }

            val requestId = msg.data.getString(MusicSourceContract.KEY_REQUEST_ID).orEmpty()
            val method = msg.data.getString(MusicSourceContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(
                    msg.data.getString(MusicSourceContract.KEY_PAYLOAD_JSON)
                        .orEmpty()
                        .ifBlank { "{}" }
                )
            }.getOrDefault(JSONObject())
            val replyTo = msg.replyTo

            executor.execute {
                val result = runCatching {
                    val sourceId = payload.optString("sourceId").ifBlank { SOURCE_ID }
                    val id = payload.optString("id")
                    runBlocking {
                        when (method) {
                            MusicSourceContract.Method.MANIFEST -> manifestJson()
                            MusicSourceContract.Method.BROWSE -> YouTubeMusicCatalog.browse(sourceId)
                            MusicSourceContract.Method.SEARCH ->
                                YouTubeMusicCatalog.search(sourceId, payload.optString("query"))
                            MusicSourceContract.Method.SUGGESTIONS ->
                                YouTubeMusicCatalog.suggestions(sourceId, payload.optString("query"))
                            MusicSourceContract.Method.ARTIST ->
                                YouTubeMusicCatalog.artist(
                                    sourceId,
                                    payload.optString("query"),
                                    payload.optString("artistId").takeIf(String::isNotBlank),
                                )
                            MusicSourceContract.Method.ALBUM ->
                                YouTubeMusicCatalog.album(
                                    sourceId,
                                    payload.optString("query"),
                                    payload.optString("albumId").takeIf(String::isNotBlank),
                                )
                            MusicSourceContract.Method.STREAMS -> resolveStreams(sourceId, id)
                            MusicSourceContract.Method.BROWSER_SESSION -> {
                                require(sourceId == SOURCE_ID) { "Unsupported source: $sourceId" }
                                YouTubeMusicSession.browserSession()
                            }
                            MusicSourceContract.Method.STORE_SESSION -> {
                                payload.optString("visitorData")
                                    .trim()
                                    .takeIf(String::isNotBlank)
                                    ?.let { YouTube.visitorData = it }
                                payload.optString("dataSyncId")
                                    .trim()
                                    .substringBefore("||")
                                    .takeIf(String::isNotBlank)
                                    ?.let { YouTube.dataSyncId = it }

                                val stored = YouTubeMusicCatalog.storeSession(
                                    sourceId = sourceId,
                                    cookieHeader = normalizeYouTubeCookieHeader(
                                        payload.optString("cookieHeader")
                                    ),
                                    userAgent = payload.optString("userAgent"),
                                )
                                val signedIn = runCatching {
                                    JSONObject(stored).optBoolean("signedIn")
                                }.getOrDefault(false)
                                YouTubeMusicSession.storePageContext(
                                    applicationContext,
                                    payload,
                                    signedIn,
                                )
                                stored
                            }
                            else -> error("Unsupported method: $method")
                        }
                    }
                }

                result.exceptionOrNull()?.let { sourceError ->
                    Log.e(TAG, "$method failed: ${sourceError.message}", sourceError)
                }

                val response = Message.obtain(null, MusicSourceContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(MusicSourceContract.KEY_REQUEST_ID, requestId)
                        putBoolean(MusicSourceContract.KEY_OK, result.isSuccess)
                        result.onSuccess {
                            putString(MusicSourceContract.KEY_RESULT_JSON, it)
                        }
                        result.onFailure {
                            putString(
                                MusicSourceContract.KEY_ERROR,
                                it.message ?: "YouTube Music source error",
                            )
                        }
                    }
                }
                runCatching { replyTo?.send(response) }
            }
        }
    })

    private fun manifestJson(): String = JSONObject()
        .put("id", "spotui.youtube.music")
        .put("name", "YouTube Music")
        .put("version", "0.1.0")
        .put("apiVersion", MusicSourceContract.API_VERSION)
        .put("contentTypes", JSONArray().put("music"))
        .put(
            "capabilities",
            JSONArray()
                .put("browse")
                .put("search")
                .put("suggestions")
                .put("artist")
                .put("album")
                .put("streams")
                .put("browserSession"),
        )
        .put(
            "sources",
            JSONArray().put(
                JSONObject()
                    .put("id", SOURCE_ID)
                    .put("name", "YouTube Music")
                    .put("contentTypes", JSONArray().put("music"))
                    .put(
                        "capabilities",
                        JSONArray()
                            .put("browse")
                            .put("search")
                            .put("suggestions")
                            .put("artist")
                            .put("album")
                            .put("streams")
                            .put("browserSession"),
                    )
            )
        )
        .toString()

    private suspend fun resolveStreams(sourceId: String, id: String): String {
        return try {
            YouTubeMusicCatalog.streams(sourceId, id)
        } catch (cause: Throwable) {
            val detail = cause.message.orEmpty()
            val challenged = detail.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                detail.contains("confirm you", ignoreCase = true) ||
                detail.contains("not a bot", ignoreCase = true) ||
                detail.contains("sign in", ignoreCase = true)
            if (challenged) {
                Log.w(TAG, "YouTube challenged playback; browser session required", cause)
                error(
                    "This music source needs a browser session on this network. " +
                        "Open the source session, complete YouTube Music if requested, then retry."
                )
            }
            throw cause
        }
    }

    private fun respond(msg: Message, result: Result<String>) {
        val response = Message.obtain(null, MusicSourceContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(
                    MusicSourceContract.KEY_REQUEST_ID,
                    msg.data.getString(MusicSourceContract.KEY_REQUEST_ID).orEmpty(),
                )
                putBoolean(MusicSourceContract.KEY_OK, result.isSuccess)
                result.onSuccess {
                    putString(MusicSourceContract.KEY_RESULT_JSON, it)
                }
                result.onFailure {
                    putString(
                        MusicSourceContract.KEY_ERROR,
                        it.message ?: "Source error",
                    )
                }
            }
        }
        runCatching { msg.replyTo?.send(response) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val SOURCE_ID = "youtube.music"
        private const val TAG = "SpotuiYouTubeMusic"
    }
}
