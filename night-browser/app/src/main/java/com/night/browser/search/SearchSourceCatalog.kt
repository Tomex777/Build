package com.night.browser.search

import com.night.browser.Bookmark
import com.night.browser.Journey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Construct the *local* eligible source catalog. IDs must stay bound to URLs,
 * otherwise a bookmark/history reorder could silently change which page a
 * previously checked row refers to. Scan past unsafe entries before applying
 * the 60 eligible-page display limit; sensitive pages must not crowd out safe ones.
 */
object SearchSourceCatalog {
    /** A bounded *on-device* index, never sent wholesale to a provider. */
    fun index(
        bookmarks: List<Bookmark>,
        journeys: List<Journey>,
    ): List<SearchSource> {
        val marks = bookmarks.asSequence().take(500).map { bookmark ->
            SearchSource(
                id = stableId(bookmark.url),
                title = bookmark.title,
                url = bookmark.url,
                category = "Bookmark",
            )
        }
        val visited = journeys.asSequence()
            .flatMap { it.nodes.asReversed().asSequence() }
            .take(1500)
            .map { node ->
                SearchSource(
                    id = stableId(node.url),
                    title = node.title,
                    url = node.url,
                    category = "Visit",
                )
            }
        return UniversalSearchPolicy.eligibleCandidates(
            (marks + visited).toList(), limit = UniversalSearchPolicy.MAX_LOCAL_INDEX
        )
    }

    /** The visible 60-row shortlist is filtered/ranked entirely on-device. */
    fun match(index: List<SearchSource>, localQuery: String): List<SearchSource> {
        val terms = localQuery.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotEmpty() }.take(8)
        if (terms.isEmpty()) return index.take(UniversalSearchPolicy.MAX_CANDIDATES)

        data class Ranked(val source: SearchSource, val points: Int, val order: Int)
        return index.mapIndexedNotNull { order, source ->
            val title = source.title.lowercase(Locale.ROOT)
            val host = UniversalSearchPolicy.safeHost(source.url).orEmpty()
            if (!terms.all { title.contains(it) || host.contains(it) }) null
            else Ranked(source, terms.sumOf { term ->
                when {
                    title.startsWith(term) -> 4
                    title.contains(term) -> 3
                    host.contains(term) -> 1
                    else -> 0
                }
            }, order)
        }.sortedWith(compareByDescending<Ranked> { it.points }.thenBy { it.order })
            .take(UniversalSearchPolicy.MAX_CANDIDATES)
            .map { it.source }
    }

    fun collect(
        bookmarks: List<Bookmark>,
        journeys: List<Journey>,
    ): List<SearchSource> = match(index(bookmarks, journeys), "")

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
