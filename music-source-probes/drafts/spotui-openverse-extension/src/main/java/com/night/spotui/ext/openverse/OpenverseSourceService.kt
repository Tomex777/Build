package com.night.spotui.ext.openverse

import android.app.Service
import android.content.Intent
import android.os.Base64
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.night.spotui.source.api.MusicSourceContract
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class OpenverseSourceService : Service() {
    private val executor = Executors.newFixedThreadPool(3)

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MusicSourceContract.MSG_REQUEST) return
            val callers = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if (HOST_PACKAGE !in callers) {
                respond(msg, Result.failure(IllegalAccessException("Caller is not Lyra")))
                return
            }

            val requestId = msg.data.getString(MusicSourceContract.KEY_REQUEST_ID).orEmpty()
            val method = msg.data.getString(MusicSourceContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(
                    msg.data.getString(MusicSourceContract.KEY_PAYLOAD_JSON)
                        .orEmpty()
                        .ifBlank { "{}" },
                )
            }.getOrDefault(JSONObject())

            executor.execute {
                val result = runCatching {
                    require(payload.optString("sourceId").ifBlank { SOURCE_ID } == SOURCE_ID) {
                        "Unsupported source"
                    }
                    when (method) {
                        MusicSourceContract.Method.MANIFEST -> manifestJson()
                        MusicSourceContract.Method.BROWSE -> searchInternal("music", 30)
                        MusicSourceContract.Method.SEARCH ->
                            searchInternal(payload.optString("query"), 30)
                        MusicSourceContract.Method.SUGGESTIONS ->
                            suggestions(payload.optString("query"))
                        MusicSourceContract.Method.STREAMS -> streams(payload.optString("id"))
                        else -> error("Unsupported method: $method")
                    }
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
                                it.message ?: "Openverse source error",
                            )
                            putString(
                                MusicSourceContract.KEY_ERROR_CODE,
                                MusicSourceContract.ERROR_CODE_GENERIC,
                            )
                        }
                    }
                }
                runCatching { msg.replyTo?.send(response) }
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun manifestJson(): String {
        val capabilities = JSONArray()
            .put("browse")
            .put("search")
            .put("suggestions")
            .put("streams")
        return JSONObject()
            .put("id", "spotui.openverse.extension")
            .put("name", SOURCE_NAME)
            .put("version", "0.1.0")
            .put("apiVersion", MusicSourceContract.API_VERSION)
            .put("contentTypes", JSONArray().put("music"))
            .put("capabilities", capabilities)
            .put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("id", SOURCE_ID)
                        .put("name", SOURCE_NAME)
                        .put("contentTypes", JSONArray().put("music"))
                        .put("capabilities", capabilities),
                ),
            )
            .toString()
    }

    private fun searchInternal(query: String, pageSize: Int): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        val root = getJson(
            "/audio/?q=" + enc(clean) +
                "&category=music&page_size=" + pageSize,
        )
        val items = root.optJSONArray("results") ?: JSONArray()
        val mapped = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            trackJson(item)?.let(mapped::put)
        }
        return mapped.toString()
    }

    private fun suggestions(query: String): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        val root = getJson(
            "/audio/?q=" + enc(clean) +
                "&category=music&page_size=10",
        )
        val items = root.optJSONArray("results") ?: JSONArray()
        val result = JSONArray()
        val seen = linkedSetOf<String>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            listOf(item.optString("title"), item.optString("creator"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { value ->
                    if (seen.add(value.lowercase())) result.put(value)
                }
            if (result.length() >= 8) break
        }
        return result.toString()
    }

    private fun trackJson(item: JSONObject): JSONObject? {
        val mediaUrl = item.optString("url").trim()
        val title = item.optString("title").trim()
        if (!mediaUrl.startsWith("http") || title.isBlank()) return null

        val filetype = item.optString("filetype").lowercase()
        val filesize = item.optLong("filesize", -1L)
        val openverseId = item.optString("id")
        val payload = JSONObject()
            .put("u", mediaUrl)
            .put("m", filetype)
            .put("s", filesize)
            .put("i", openverseId)
        val id = "ov:" + Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        val license = buildString {
            append(item.optString("license").trim())
            item.optString("license_version").trim()
                .takeIf(String::isNotBlank)
                ?.let { version ->
                    if (isNotBlank()) append(' ')
                    append(version)
                }
        }.trim()

        val creator = item.optString("creator").trim().ifBlank { "Unknown creator" }
        val licenseUrl = item.optString("license_url").trim()
        val attribution = item.optString("attribution").trim().ifBlank {
            buildString {
                append('"').append(title).append('"')
                append(" by ").append(creator)
                if (license.isNotBlank()) append(" · ").append(license)
            }
        }

        val durationMs = item.optLong("duration", 0L)
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", creator)
            .put("artistId", "")
            .put("album", "")
            .put("albumId", "")
            .put("artworkUrl", item.optString("thumbnail"))
            .put("durationSeconds", if (durationMs > 0L) durationMs / 1000L else 0L)
            .put("explicit", false)
            .put("license", license)
            .put("licenseUrl", licenseUrl)
            .put("attribution", attribution)
    }

    private fun streams(encodedId: String): String {
        require(encodedId.startsWith("ov:")) { "Invalid Openverse track id" }
        val raw = encodedId.removePrefix("ov:")
        val decoded = String(
            Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        val payload = JSONObject(decoded)
        val url = payload.getString("u")
        require(url.startsWith("http")) { "Invalid Openverse media URL" }
        val filetype = payload.optString("m")
        val size = payload.optLong("s", -1L)

        return JSONArray()
            .put(
                JSONObject()
                    .put("url", url)
                    .put(
                        "label",
                        filetype.uppercase().ifBlank { "Openverse audio" },
                    )
                    .put("mimeType", mimeFor(filetype))
                    .apply {
                        if (size > 0L) put("contentLength", size)
                    },
            )
            .toString()
    }

    private fun mimeFor(filetype: String): String = when (filetype.lowercase()) {
        "mp3", "mpeg" -> "audio/mpeg"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav", "wave" -> "audio/wav"
        "flac" -> "audio/flac"
        "m4a", "mp4", "aac" -> "audio/mp4"
        else -> "audio/*"
    }

    private fun getJson(path: String): JSONObject {
        val connection = URI(BASE_URL + path).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) { "Openverse HTTP $status" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

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
                    putString(MusicSourceContract.KEY_ERROR, it.message ?: "Source error")
                    putString(
                        MusicSourceContract.KEY_ERROR_CODE,
                        MusicSourceContract.ERROR_CODE_GENERIC,
                    )
                }
            }
        }
        runCatching { msg.replyTo?.send(response) }
    }

    companion object {
        private const val HOST_PACKAGE = "com.night.spotui"
        private const val SOURCE_ID = "openverse.audio"
        private const val SOURCE_NAME = "Openverse"
        private const val BASE_URL = "https://api.openverse.org/v1"
        private const val USER_AGENT = "Lyra/1.0 (Android; Openverse source)"
    }
}
