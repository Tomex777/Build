package com.night.spotui

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SoundCloud's official API currently requires a confidential app secret.
 * That secret must never be embedded in an APK, so SpotUI talks to an optional
 * server-side suggestion proxy instead. When no proxy is configured, callers
 * simply fall back to local/source suggestions.
 */
class SoundCloudSuggestions(
    private val proxyBaseUrl: String,
) {
    suspend fun suggest(query: String, limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2 || proxyBaseUrl.isBlank()) return@withContext emptyList()

        runCatching {
            val separator = if ('?' in proxyBaseUrl) '&' else '?'
            val url = proxyBaseUrl + separator + "q=" + encode(clean) + "&limit=" + limit
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "SpotUI/0.2")
            }
            val body = try {
                if (connection.responseCode !in 200..299) return@runCatching emptyList()
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            parseSuggestions(body).take(limit)
        }.getOrDefault(emptyList())
    }

    private fun parseSuggestions(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return parseArray(JSONArray(trimmed))

        val root = JSONObject(trimmed)
        for (key in listOf("suggestions", "collection", "results", "tracks")) {
            val array = root.optJSONArray(key) ?: continue
            return parseArray(array)
        }
        return emptyList()
    }

    private fun parseArray(array: JSONArray): List<String> = buildList {
        for (index in 0 until array.length()) {
            when (val value = array.opt(index)) {
                is String -> value.takeIf(String::isNotBlank)?.let(::add)
                is JSONObject -> {
                    val label = sequenceOf("label", "title", "name", "username", "query")
                        .map { key -> value.optString(key) }
                        .firstOrNull { it.isNotBlank() }
                    label?.let(::add)
                }
            }
        }
    }.distinctBy { it.lowercase() }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
