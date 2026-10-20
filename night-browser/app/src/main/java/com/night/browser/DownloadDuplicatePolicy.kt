package com.night.browser

import java.net.URI

/**
 * A repeat candidate is a completed, still-present file with the same
 * cleaned file name at the same web origin.
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
        val nextOrigin = origin(sourceUrl) ?: return false
        val previousOrigin = origin(priorUrl) ?: return false
        if (nextOrigin != previousOrigin) return false
        // A generic endpoint such as /download?id=1 and /download?id=2
        // is not proof of the same bytes: signed query fields are intentionally
        // scrubbed from our Library. Only a matching name at that site warns.
        return proposedName.isNotBlank() &&
            proposedName.equals(priorName, ignoreCase = true)
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

    /** Origin without query, fragment or embedded URL credentials. */
    private fun origin(raw: String): String? = runCatching {
        val safe = URI(GeckoDownloadNames.redactUrl(raw))
        val scheme = safe.scheme?.lowercase() ?: return@runCatching null
        if (scheme != "http" && scheme != "https") return@runCatching null
        val host = safe.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val port = if (safe.port >= 0) ":${safe.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
}
