package com.night.browser

import java.net.URI
import java.net.URLEncoder

/**
 * A strict web address or an ordinary search query. Never send a pasted, explicit
 * non-web URI to Gecko, and never send credentials embedded in a URL.
 *
 * This is *not* AI Universal Search: it simply constructs a normal web search.
 */
object AddressInput {
    private val blockedScheme = Regex(
        "^(javascript|data|file|content|intent|about|chrome|blob|view-source):",
        RegexOption.IGNORE_CASE
    )

    fun resolve(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty() || input.length > 8192 || input.any(Char::isISOControl)) return null
        if (blockedScheme.containsMatchIn(input)) return null

        if (input.contains("://")) {
            val uri = runCatching { URI(input) }.getOrNull() ?: return null
            return if ((uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                !input.any(Char::isWhitespace) && uri.port != 0 &&
                uri.port <= 65535
            ) input else null
        }

        // Unqualified domains, localhost and common development ports are URLs.
        // Avoid misreading email addresses as URL hostnames.
        if (!input.any(Char::isWhitespace) && !input.contains('@') &&
            (input.equals("localhost", true) || input.startsWith("localhost:", true) ||
                input.startsWith("localhost/", true) || input.contains('.'))) {
            val uri = runCatching { URI("https://$input") }.getOrNull()
            if (!uri?.host.isNullOrBlank() && uri?.rawUserInfo == null &&
                uri?.port != 0 && uri?.port != null && uri.port <= 65535) {
                return uri.toASCIIString()
            }
        }

        return "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
    }
}
