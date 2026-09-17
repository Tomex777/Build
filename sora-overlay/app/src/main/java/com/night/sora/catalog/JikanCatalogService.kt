package com.night.sora.catalog

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
import com.night.sora.model.ContentType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in Anime/Manga catalog provider shipped inside Sora Core.
 *
 * It uses the same structured contract as external extensions so the UI does
 * not need a second data shape, but it is not an external source APK and does
 * not provide streams or manga pages.
 */
class JikanCatalogService : Service() {
    private val client = JikanCatalogClient()

    private val descriptor = ExtensionDescriptor(
        id = "sora.core.jikan",
        name = "Sora Anime & Manga Catalog",
        version = "1.0.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Sora",
        description = "Built-in Anime and Manga metadata powered by Jikan.",
        contentTypes = setOf("anime", "manga"),
        capabilities = setOf("builtin", "catalog", "browse", "search", "details"),
        permissions = listOf(ExtensionPermission("network", listOf("api.jikan.moe"))),
        sources = listOf(
            SourceDescriptor(
                id = JikanCatalogClient.ANIME_SOURCE,
                name = "Jikan Anime",
                contentTypes = setOf("anime"),
                capabilities = setOf("browse", "search", "details"),
            ),
            SourceDescriptor(
                id = JikanCatalogClient.MANGA_SOURCE,
                name = "Jikan Manga",
                contentTypes = setOf("manga"),
                capabilities = setOf("browse", "search", "details"),
            ),
        ),
    )

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != ExtensionContract.MSG_REQUEST) return
            val requestId = message.data.getString(ExtensionContract.KEY_REQUEST_ID).orEmpty()
            val method = message.data.getString(ExtensionContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(message.data.getString(ExtensionContract.KEY_PAYLOAD_JSON).orEmpty().ifBlank { "{}" })
            }.getOrDefault(JSONObject())
            val reply = message.replyTo ?: return

            when (method) {
                ExtensionContract.Method.MANIFEST -> sendSuccess(reply, requestId, descriptor.toJson())
                ExtensionContract.Method.BROWSE -> {
                    val type = typeFromPayload(payload)
                    if (type == null) sendError(reply, requestId, "Jikan supports Anime and Manga only")
                    else client.browse(type, payload.optString("feed")) { result -> sendCatalogResult(reply, requestId, result) }
                }
                ExtensionContract.Method.SEARCH -> {
                    val type = typeFromPayload(payload)
                    if (type == null) sendError(reply, requestId, "Jikan supports Anime and Manga only")
                    else client.search(type, payload.optString("query")) { result -> sendCatalogResult(reply, requestId, result) }
                }
                ExtensionContract.Method.DETAILS -> {
                    val type = typeFromSource(payload.optString("sourceId"))
                    if (type == null) sendError(reply, requestId, "Unknown Jikan source")
                    else client.details(type, payload.optString("id")) { result ->
                        result.fold(
                            onSuccess = { details ->
                                sendSuccess(
                                    reply,
                                    requestId,
                                    JSONObject().apply {
                                        put("id", details.id)
                                        put("title", details.title)
                                        put("alternateTitle", details.alternateTitle)
                                        put("subtitle", details.subtitle)
                                        put("description", details.synopsis)
                                        put("artworkUrl", details.artworkUrl ?: JSONObject.NULL)
                                        put("score", details.score ?: JSONObject.NULL)
                                        put("status", details.status)
                                        put("genres", JSONArray(details.genres))
                                        put("year", details.year ?: JSONObject.NULL)
                                        put("season", details.season)
                                        put("episodes", details.episodes ?: JSONObject.NULL)
                                        put("chapters", details.chapters ?: JSONObject.NULL)
                                        put("volumes", details.volumes ?: JSONObject.NULL)
                                    }.toString(),
                                )
                            },
                            onFailure = { sendError(reply, requestId, it.message ?: "Jikan details failed") },
                        )
                    }
                }
                ExtensionContract.Method.EPISODES,
                ExtensionContract.Method.CHAPTERS -> sendSuccess(reply, requestId, "[]")
                else -> sendError(reply, requestId, "Catalog metadata only")
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun sendCatalogResult(
        reply: Messenger,
        requestId: String,
        result: Result<List<JikanCatalogClient.CatalogItem>>,
    ) {
        result.fold(
            onSuccess = { items ->
                val array = JSONArray()
                items.forEach { item ->
                    array.put(JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("subtitle", item.subtitle)
                        put("artworkUrl", item.artworkUrl ?: JSONObject.NULL)
                    })
                }
                sendSuccess(reply, requestId, array.toString())
            },
            onFailure = { sendError(reply, requestId, it.message ?: "Jikan request failed") },
        )
    }

    private fun typeFromPayload(payload: JSONObject): ContentType? {
        return when (payload.optString("type").lowercase()) {
            "anime" -> ContentType.ANIME
            "manga" -> ContentType.MANGA
            else -> typeFromSource(payload.optString("sourceId"))
        }
    }

    private fun typeFromSource(sourceId: String): ContentType? = when (sourceId) {
        JikanCatalogClient.ANIME_SOURCE -> ContentType.ANIME
        JikanCatalogClient.MANGA_SOURCE -> ContentType.MANGA
        else -> null
    }

    private fun sendSuccess(reply: Messenger, requestId: String, resultJson: String) {
        val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                putBoolean(ExtensionContract.KEY_OK, true)
                putString(ExtensionContract.KEY_RESULT_JSON, resultJson)
            }
        }
        runCatching { reply.send(response) }
    }

    private fun sendError(reply: Messenger, requestId: String, error: String) {
        val response = Message.obtain(null, ExtensionContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(ExtensionContract.KEY_REQUEST_ID, requestId)
                putBoolean(ExtensionContract.KEY_OK, false)
                putString(ExtensionContract.KEY_ERROR, error)
            }
        }
        runCatching { reply.send(response) }
    }
}
