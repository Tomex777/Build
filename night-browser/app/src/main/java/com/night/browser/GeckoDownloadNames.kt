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
            // RFC 5987 permits UTF-8 / ISO-8859-1 and literal '+' is PLUS,
            // unlike application/x-www-form-urlencoded where '+' means space.
            val parts = utf8.split('\'', limit = 3)
            val charset = if (parts.size == 3) parts[0].lowercase() else "utf-8"
            val encoded = if (parts.size == 3) parts[2] else utf8
            if (charset == "utf-8" || charset == "iso-8859-1") {
                val decoded = runCatching {
                    URLDecoder.decode(encoded.replace("+", "%2B"), charset)
                }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded
            }
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
