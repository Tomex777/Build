package com.night.browser.search

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Titles and hostnames only. There is no implicit page-body indexing or background upload.
 * Candidate approval is session-scoped; every provider request requires a separate confirmation.
 */
data class SearchSource(
    val id: String,
    val title: String,
    val url: String,
    val category: String,
)

data class SharedSearchSource(
    val id: String,
    val title: String,
    val host: String,
    val original: SearchSource,
)

sealed interface SearchPreparation {
    data class Ready(val question: String, val sources: List<SharedSearchSource>) : SearchPreparation
    data class Rejected(val reason: String) : SearchPreparation
}

object UniversalSearchPolicy {
    const val MAX_CANDIDATES = 60
    const val MAX_SHARED_SOURCES = 12

    // Conservative suppression of pages whose titles or URLs could expose account or sensitive data.
    private val sensitiveTerms = Regex(
        "(?i)(password|passkey|secret|api.?key|bearer|auth(?:entication|orization)?|" +
            "sign.?in|log.?in|log.?out|reset.?password|checkout|payment|billing|bank|" +
            "medical|patient|health|therapy|diagnos|prescription|private|incognito|" +
            "2fa|otp|verification.?code|session.?id|access.?token|refresh.?token)"
    )

    // Page titles can be a URL (Gecko's fallback when no document title exists), or
    // contain personal email addresses. Titles are SENT to the provider, so treating
    // the URL field alone as sensitive would accidentally leak full URLs or tokens.
    private val urlOrEmailInTitle = Regex(
        """(?i)(?:\\b(?:https?://|www\\.)|\\b[a-z0-9.-]+\\.[a-z]{2,}/\\S*|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,})"""
    )

    // Sites sometimes double-percent-encode sensitive routes. Decode for the
    // classification ONLY; the original URL is never included in a request.
    private fun decodeForSafety(value: String): String {
        var decoded = value
        repeat(3) {
            decoded = try {
                URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
            } catch (_: IllegalArgumentException) {
                return decoded
            }
        }
        return decoded
    }

    /** Original URLs stay on device. Only safe hostname and title can be submitted. */
    fun safeHost(url: String): String? {
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return null
        if (uri.rawUserInfo != null || uri.rawFragment != null) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host.length > 253 || host.isBlank() || host == "localhost" ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".localhost") || host.endsWith(".test") ||
            host.endsWith(".invalid") || host.contains(':') ||
            host.all { it.isDigit() || it == '.' } || !host.contains('.')
        ) return null
        if (host.split('.').any { it.isEmpty() }) return null
        if (uri.port !in listOf(-1, 443)) return null
        return host
    }

    fun isSensitive(source: SearchSource): Boolean {
        val title = source.title.trim()
        if (title.isBlank() || title.length > 240 || title.any { it.isISOControl() }) return true
        val uri = try { URI(source.url) } catch (_: Exception) { return true }
        // URL query parameters and fragments may carry private tokens; never index or share them.
        if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return true
        val inspectedTitle = decodeForSafety(title)
        val inspectedPath = decodeForSafety(uri.rawPath.orEmpty())
        return urlOrEmailInTitle.containsMatchIn(inspectedTitle) ||
            sensitiveTerms.containsMatchIn(inspectedTitle) ||
            sensitiveTerms.containsMatchIn(uri.host.orEmpty()) ||
            sensitiveTerms.containsMatchIn(inspectedPath)
    }

    /** Local candidate listing does not send any data to a model or network. */
    fun eligibleCandidates(raw: List<SearchSource>): List<SearchSource> {
        val usedUrls = HashSet<String>()
        val usedIds = HashSet<String>()
        return raw.asSequence()
            .filter { safeHost(it.url) != null && !isSensitive(it) }
            .filter { usedUrls.add(it.url) && usedIds.add(it.id) }
            .take(MAX_CANDIDATES).toList()
    }

    /**
     * A private-tab flag, a fresh explicit consent confirmation and individually selected
     * sources are enforced BEFORE any transport is invoked.
     */
    fun prepare(
        question: String,
        eligible: List<SearchSource>,
        selectedIds: Set<String>,
        explicitlyConfirmed: Boolean,
        privateTab: Boolean,
    ): SearchPreparation {
        if (privateTab) return SearchPreparation.Rejected("AI search is disabled in private tabs.")
        if (!explicitlyConfirmed) return SearchPreparation.Rejected("Confirm sharing before sending.")
        val text = question.trim()
        if (text.isBlank()) return SearchPreparation.Rejected("Enter a question.")
        if (text.length > 500) return SearchPreparation.Rejected("Use a shorter question (500 characters maximum).")
        if (selectedIds.isEmpty()) return SearchPreparation.Rejected("Select at least one page.")
        val sources = eligibleCandidates(eligible).asSequence()
            .filter { selectedIds.contains(it.id) }
            .take(MAX_SHARED_SOURCES)
            .mapIndexedNotNull { index, source ->
                safeHost(source.url)?.let { host ->
                    // Model-visible aliases are ephemeral, not local URL-derived IDs.
                    SharedSearchSource("S" + (index + 1), source.title.trim().take(160), host, source)
                }
            }.toList()
        if (sources.isEmpty()) return SearchPreparation.Rejected("No eligible approved pages were selected.")
        return SearchPreparation.Ready(text, sources)
    }

    /** Model-provided source IDs are treated as untrusted; only approved IDs can be cited. */
    fun confirmedCitations(
        claimedIds: List<String>,
        approved: List<SharedSearchSource>,
    ): List<SearchSource> {
        val byId = approved.associateBy { it.id }
        return claimedIds.distinct().mapNotNull { byId[it]?.original }
    }
}
