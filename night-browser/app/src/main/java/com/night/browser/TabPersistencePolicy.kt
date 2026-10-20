package com.night.browser

import java.net.URI
import java.util.Locale

/**
 * Enforced at BOTH tab-state serialization and restoration boundaries.
 *
 * A browser URL is untrusted text (the network can navigate there). Reject opaque, local,
 * malformed and oversized URLs before writing an encrypted tab manifest. Credentials and
 * fragments have no place in the persisted display URL; Gecko session state, when available,
 * remains the authoritative source for normal history restoration.
 */
internal object TabPersistencePolicy {
    const val MAX_TABS = 80
    const val MAX_URL_LENGTH = 4_096
    const val MAX_SESSION_STATE_LENGTH = 128_000
    const val MAX_MANIFEST_BYTES = 12 * 1024 * 1024

    private val safeId = Regex("[A-Za-z0-9_-]{1,128}")

    fun validId(id: String): Boolean = safeId.matches(id)

    fun sanitizeUrl(url: String): String? {
        if (url.isEmpty()) return ""
        if (url.length > MAX_URL_LENGTH) return null
        if (url.any { it.isISOControl() || it == ' ' }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.host.isNullOrBlank()) return null

        // Preserve already percent-encoded path/query bytes verbatim. URI's multi-argument
        // constructor would double-encode an existing %XX sequence.
        val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
        val safe = buildString {
            append(scheme).append("://").append(authority)
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
        }
        return safe.takeIf { it.length <= MAX_URL_LENGTH }
    }

    fun safeState(state: String?): String? =
        state?.takeIf { it.length in 1..MAX_SESSION_STATE_LENGTH }
}
