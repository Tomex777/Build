package com.tomex777.annie.browser.engine.bookmarks.storage

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.bookmarks.model.BookmarkCollection
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests real Android AtomicFile storage, not just JSON round trips. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniBookmarkStorageTest {
    @Test fun failedSaveRestoresPreviouslyCommittedBookmarkTree() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "omni-bookmarks-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val collection = BookmarkCollection()
            val folder = collection.addFolder("Imported")
            collection.addBookmark("Docs", "https://example.org", folder.id)
            saveBookmarksToDir(root, collection)
            assertEquals(1, loadBookmarksFromDir(root).bookmarkCount())
            assertEquals(1, loadBookmarksFromDir(root).folderCount())

            val atomic = AtomicFile(File(root, "annie_browser_bookmarks_v2.json"))
            val interrupted = atomic.startWrite()
            interrupted.write("{bad-json".toByteArray())
            atomic.failWrite(interrupted)
            val restored = loadBookmarksFromDir(root)
            assertEquals(1, restored.bookmarkCount())
            assertEquals("https://example.org", restored.allBookmarks().single().url)
            assertTrue(restored.validate().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun preservesLegacyFlatBookmarksDuringMigration() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "omni-legacy-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val old = JSONArray()
                .put(JSONObject().put("title", "Original").put("url", "https://example.org"))
                .put(JSONObject().put("title", "Other").put("url", "https://example.net"))
            File(root, "annie_browser_bookmarks.json").writeText(old.toString())
            val migrated = loadBookmarksFromDir(root)
            assertEquals(2, migrated.bookmarkCount())
            assertTrue(File(root, "annie_browser_bookmarks_v2.json").exists())
            assertEquals(2, loadBookmarksFromDir(root).bookmarkCount())
        } finally {
            root.deleteRecursively()
        }
    }
}
