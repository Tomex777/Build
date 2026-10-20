package com.night.browser

import java.net.URI

/**
 * A repeat download means the same cleaned file name from the same web origin,
 * or a different filename served from the same cleaned source URL.
 *
 * Only a genuinely completed, still-present Library item may trigger the
 * warning. This is advisory: downloading again must remain possible.
 * Compare redacted source URLs to avoid saving signed query tokens.
 */
internal object DownloadDuplicatePolicy {
    fun matches(
        sourceUrl: String,
        proposedName: String,
        priorUrl: String,
        priorName: String
    ): Boolean {
        val next = identity(sourceUrl) ?: return false
        val previous = identity(priorUrl) ?: return false
        if (next.first != previous.first) return false
        return next.second == previous.second ||
            (proposedName.isNotBlank() &&
                proposedName.equals(priorName, ignoreCase = true))
    }

    fun findCompleted(
        sourceUrl: String,
        proposedName: String,
        items: List<LibraryDownload>,
        isPresent: (LibraryDownload) -> Boolean
    ): LibraryDownload? = items.firstOrNull {
        it.complete && matches(sourceUrl, proposedName, it.url, it.fileName) &&
            isPresent(it)
    }

    /** [origin, path] with query, fragment and user credentials removed. */
    private fun identity(raw: String): Pair<String, String>? = runCatching {
        val safe = URI(GeckoDownloadNames.redactUrl(raw))
        val scheme = safe.scheme?.lowercase() ?: return@runCatching null
        if (scheme != "http" && scheme != "https") return@runCatching null
        val host = safe.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val port = if (safe.port >= 0) ":${safe.port}" else ""
        Pair("$scheme://$host$port", safe.rawPath.orEmpty().ifBlank { "/" })
    }.getOrNull()
}
