package com.night.browser

/**
 * On back/forward Gecko can deliver HistoryList before or after a location
 * callback. Never display a cached title belonging to a different document.
 * This is tab-only UI state; private history must remain in memory.
 */
internal object BrowserHistoryTitle {
    fun atCommittedUrl(
        committedUrl: String,
        historyUrl: String?,
        historyTitle: String?
    ): String? {
        if (committedUrl.isBlank() || committedUrl != historyUrl) return null
        return historyTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
    }
}
