package com.veya.app.youtube

import java.net.URI

object YouTubeUrlParser {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun videoId(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (idPattern.matches(text)) return text

        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null

        return when {
            host == "youtu.be" ->
                uri.path?.trim('/')?.substringBefore('/')?.takeIf(idPattern::matches)

            host == "youtube.com" || host.endsWith(".youtube.com") -> {
                val path = uri.path.orEmpty()
                when {
                    path == "/watch" -> queryValue(uri.rawQuery, "v")?.takeIf(idPattern::matches)
                    path.startsWith("/shorts/") ->
                        path.removePrefix("/shorts/").substringBefore('/').takeIf(idPattern::matches)
                    path.startsWith("/embed/") ->
                        path.removePrefix("/embed/").substringBefore('/').takeIf(idPattern::matches)
                    else -> null
                }
            }

            else -> null
        }
    }

    private fun queryValue(query: String?, key: String): String? =
        query.orEmpty()
            .split('&')
            .asSequence()
            .mapNotNull { part ->
                val name = part.substringBefore('=')
                val value = part.substringAfter('=', "")
                value.takeIf { name == key && value.isNotBlank() }
            }
            .firstOrNull()
}
