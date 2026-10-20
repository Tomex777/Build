package com.night.browser

import java.net.URI
import java.net.URLDecoder

/**
 * Parse site-supplied download metadata without trusting it as a local file path.
 * No query strings, credentials or URL fragments enter the Gecko download ledger.
 */
internal object GeckoDownloadNames {
    fun fromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf8 = Regex("""(?:^|;)\s*filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
        if (!utf8.isNullOrBlank()) {
            val encoded = utf8.substringAfter("''", utf8)
            val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        val plain = Regex(
            """(?:^|;)\s*filename\s*=\s*(?:"([^"]+)"|([^;]+))""",
            RegexOption.IGNORE_CASE
        ).find(header)
        return (plain?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: plain?.groupValues?.getOrNull(2))?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun redactUrl(url: String): String = runCatching {
        val uri = URI(url)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
        require(!uri.host.isNullOrBlank())
        URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
    }.getOrDefault("Web download")
}
