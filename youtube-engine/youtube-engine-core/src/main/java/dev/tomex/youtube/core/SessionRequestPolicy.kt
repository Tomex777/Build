package dev.tomex.youtube.core

import java.net.URL

/**
 * The host owns credentials, but the engine owns where they may be attached.
 * Session-assisted headers never leave HTTPS YouTube origins and cannot replace
 * transport/client headers controlled by the resolver.
 */
object SessionRequestPolicy {
    private val blocked = setOf(
        "host", "content-length", "transfer-encoding", "connection", "range",
        "user-agent", "content-type", "x-youtube-client-name",
        "x-youtube-client-version", "x-goog-visitor-id"
    )

    fun allowsSessionOrigin(url: String): Boolean = runCatching {
        val parsed = URL(url)
        val host = parsed.host.lowercase()
        parsed.protocol.equals("https", ignoreCase = true) &&
            (host == "youtube.com" || host.endsWith(".youtube.com"))
    }.getOrDefault(false)

    fun isSafeHeaderValue(value: String): Boolean =
        value.isNotBlank() && value.length <= 8192 && '\r' !in value && '\n' !in value

    fun sanitize(url: String, headers: Map<String, String>): Map<String, String> {
        if (!allowsSessionOrigin(url)) return emptyMap()
        return headers.entries
            .filter { (name, value) ->
                name.isNotBlank() && name.length <= 128 &&
                    name.lowercase() !in blocked &&
                    name.none { it == '\r' || it == '\n' || it == ':' } &&
                    isSafeHeaderValue(value)
            }
            .associate { it.key to it.value }
    }
}
