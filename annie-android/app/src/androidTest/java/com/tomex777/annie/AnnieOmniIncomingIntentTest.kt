package com.tomex777.annie

import android.app.SearchManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tomex777.annie.browser.engine.intents.IntentUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the real adapted upstream Omni parser, not Annie's local omnibox parser. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniIncomingIntentTest {
    @Test fun extractsSafeShareUrlAndPreservesWikiBalancedParentheses() {
        val text = "Read https://en.wikipedia.org/wiki/Rust_(programming_language)."
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        assertEquals(
            "https://en.wikipedia.org/wiki/Rust_(programming_language)",
            IntentUrlParser.parseIntent(intent)
        )
        assertEquals("https://example.org/path",
            IntentUrlParser.parseIntent(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.org/path"))
            )
        )
        assertEquals("https://example.org",
            IntentUrlParser.extractUrlOrQuery("www.example.org.", fallbackToQuery = false))
        assertEquals("portrait photos", IntentUrlParser.parseIntent(
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "portrait photos")))
    }

    @Test fun blocksExternalJavascriptFileContentAndMisleadingHosts() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "content://provider/secret",
            "intent://something", "data:text/html,hi", "blob:https://site/id",
            "about:config", "https://user@example.org", "https://",
            "https://bad host.example").forEach { value ->
            assertNull("Unsafe external target: $value",
                IntentUrlParser.extractUrlOrQuery(value, fallbackToQuery = false))
        }
        assertNull(IntentUrlParser.extractUrlOrQuery("foo\nhttps://example.org"))
        assertNull(IntentUrlParser.parseIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("javascript:alert(1)"))
        ))
    }

    @Test fun noUnexpectedActionOrDefaultSearchWhenExternalIntentIsEmpty() {
        assertNull(IntentUrlParser.parseIntent(Intent()))
        assertNull(IntentUrlParser.parseIntent(null))
        assertNull(IntentUrlParser.extractUrlOrQuery("", true))
        assertNull(IntentUrlParser.parseIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("about:blank"))
        ))
    }
}
