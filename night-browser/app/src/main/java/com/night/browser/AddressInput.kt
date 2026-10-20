package com.night.browser

import java.net.URI
import java.net.URLEncoder

/** Separates address navigation from web queries, without executing arbitrary URI schemes. */
object AddressInput {
    fun resolve(raw: String): String? {
        val input = raw.trim()
        if (input.isBlank()) return null
        if (input.contains("://")) {
            val uri = runCatching { URI(input) }.getOrNull() ?: return null
            return if ((uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null) input else null
        }
        if (!input.contains(' ') &&
            (input.equals("localhost", true) ||
                input.startsWith("localhost:", true) ||
                input.startsWith("localhost/", true) ||
                input.contains('.')) &&
            !input.startsWith("javascript:", true) &&
            !input.startsWith("data:", true)) {
            val uri = runCatching { URI("https://$input") }.getOrNull()
            if (uri?.host != null) return uri.toString()
        }
        return "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
    }
}
