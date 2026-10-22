package com.tomex777.annie.browser.engine.bookmarks

import com.tomex777.annie.browser.engine.bookmarks.model.BookmarkCollection
import com.tomex777.annie.browser.engine.bookmarks.export.exportNetscapeBookmarkHtml
import com.tomex777.annie.browser.engine.bookmarks.parser.parseNetscapeBookmarkHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genuine upstream Omni bookmark tree and HTML interchange, without network. */
class AnnieOmniBookmarksTest {
    @Test fun nestedBookmarkFoldersRoundTripThroughNetscapeHtml() {
        val original = BookmarkCollection(clock = { 12345L })
        val folder = original.addFolder("Software & Tools")
        original.addBookmark(
            title = "Work <docs>",
            url = "https://example.org/path?q=one&tag=two",
            parentId = folder.id
        )
        original.addBookmark(title = "Reference", url = "https://developer.android.com")
        val html = exportNetscapeBookmarkHtml(original)
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("&amp;"))
        val restored = parseNetscapeBookmarkHtml(html, clock = { 12345L })
        assertEquals(2, restored.importedBookmarks)
        assertEquals(1, restored.importedFolders)
        val collection = restored.collection
        assertEquals(2, collection.bookmarkCount())
        assertEquals(1, collection.folderCount())
        assertTrue(collection.allBookmarks().any {
            it.title == "Work <docs>" &&
                it.url == "https://example.org/path?q=one&tag=two"
        })
        assertTrue(collection.validate().isEmpty())
    }

    @Test fun importedBookmarkHtmlNeverAcceptsExecutableOrMalformedUrls() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
            <DT><A HREF="javascript:alert(1)">script</A>
            <DT><A HREF="intent://example.org">intent</A>
            <DT><A HREF="content://provider/path">content</A>
            <DT><A HREF="data:text/html,evil">data</A>
            <DT><A HREF="https://user@example.org/private">user-info</A>
            <DT><A HREF="https://example.org/safe">valid</A>
            </DL><p>
        """.trimIndent()
        val parsed = parseNetscapeBookmarkHtml(html)
        assertEquals(1, parsed.importedBookmarks)
        assertEquals(5, parsed.skippedEntries)
        assertEquals("https://example.org/safe", parsed.collection.allBookmarks().single().url)
        assertFalse(parsed.collection.allBookmarks().any { it.url.startsWith("javascript:") })
    }
}
