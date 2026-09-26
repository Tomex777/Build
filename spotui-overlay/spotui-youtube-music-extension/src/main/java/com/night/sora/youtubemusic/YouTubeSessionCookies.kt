package com.night.sora.youtubemusic

/**
 * Normalize the browser Cookie header for the pinned Innertube implementation.
 * Google may omit SAPISID while still providing __Secure-3PAPISID; Innertube's
 * SAPISIDHASH builder currently reads SAPISID specifically, so mirror the secure
 * value into SAPISID without dropping any of the original browser cookies.
 */
internal fun normalizeYouTubeCookieHeader(raw: String): String {
    val tokens = raw
        .split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && '=' in it }

    if (tokens.isEmpty()) return ""

    val values = LinkedHashMap<String, String>()
    tokens.forEach { token ->
        val name = token.substringBefore('=').trim()
        val value = token.substringAfter('=', "").trim()
        if (name.isNotBlank()) values[name] = value
    }

    if (values["SAPISID"].isNullOrBlank()) {
        val fallback = values["__Secure-3PAPISID"]
            ?.takeIf(String::isNotBlank)
            ?: values["__Secure-1PAPISID"]?.takeIf(String::isNotBlank)
        if (fallback != null) values["SAPISID"] = fallback
    }

    return values.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
