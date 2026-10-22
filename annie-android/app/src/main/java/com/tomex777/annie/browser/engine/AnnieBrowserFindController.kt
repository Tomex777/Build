/*
 * Derived from Omni Browser's BrowserViewModel_FindInPage.kt
 * Copyright (C) 2026 RebelRoot Ltd
 * Original: https://github.com/REBEL-ROOT/omni-browser
 * GPL-3.0-or-later. Retains the upstream find/clear/next/previous behavior
 * while replacing its monolithic ViewModel dependency with Annie's engine API.
 */
package com.tomex777.annie.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

/** An engine feature, not a duplicate browser toolbar or a cosmetic mock. */
internal class AnnieBrowserFindController {
    var visible by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var current by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var found by mutableStateOf(true)
        private set

    fun open() {
        visible = true
        query = ""
        current = 0
        total = 0
        found = true
    }

    fun close(session: GeckoSession?) {
        visible = false
        query = ""
        current = 0
        total = 0
        found = true
        if (session?.isOpen == true) runCatching { session.finder.clear() }
    }

    /** Called by the user-facing UI when its custom search input changes. */
    fun update(session: GeckoSession?, newQuery: String) {
        query = newQuery.take(1024)
        if (query.isBlank()) {
            current = 0
            total = 0
            found = true
            if (session?.isOpen == true) runCatching { session.finder.clear() }
            return
        }
        search(session, GeckoSession.FINDER_FIND_FORWARD)
    }

    fun next(session: GeckoSession?) = search(session, GeckoSession.FINDER_FIND_FORWARD)

    fun previous(session: GeckoSession?) = search(session, GeckoSession.FINDER_FIND_BACKWARDS)

    private fun search(session: GeckoSession?, direction: Int) {
        val text = query
        if (text.isBlank() || session?.isOpen != true) return
        session.finder.find(text, direction).accept(
            { result ->
                // A later keystroke might already have replaced this request.
                if (query != text) return@accept
                found = result?.found ?: false
                current = result?.current ?: 0
                total = result?.total ?: 0
            },
            {
                if (query == text) {
                    found = false
                    current = 0
                    total = 0
                }
            }
        )
    }
}
