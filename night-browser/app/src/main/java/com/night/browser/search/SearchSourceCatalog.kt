package com.night.browser.search

import com.night.browser.Bookmark
import com.night.browser.Journey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Construct the *local* eligible source catalog. IDs must stay bound to URLs,
 * otherwise a bookmark/history reorder could silently change which page a
 * previously checked row refers to.
 */
object SearchSourceCatalog {
    fun collect(
        bookmarks: List<Bookmark>,
        journeys: List<Journey>,
    ): List<SearchSource> {
        val marks = bookmarks.asSequence().take(60).map { bookmark ->
            SearchSource(
                id = stableId(bookmark.url),
                title = bookmark.title,
                url = bookmark.url,
                category = "Bookmark",
            )
        }
        val visited = journeys.asSequence()
            .flatMap { it.nodes.asReversed().asSequence() }
            .take(250)
            .map { node ->
                SearchSource(
                    id = stableId(node.url),
                    title = node.title,
                    url = node.url,
                    category = "Visit",
                )
            }
        return UniversalSearchPolicy.eligibleCandidates((marks + visited).toList())
    }

    internal fun stableId(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(StandardCharsets.UTF_8))
        // 96 bits of SHA-256 (24 hex digits), locally derived without a network request.
        // Truncation is used only for ephemeral selection IDs, never for authentication.
        return buildString(25) {
            append('S')
            bytes.take(12).forEach {
                append("%02x".format(it.toInt() and 0xff))
            }
        }
    }
}
