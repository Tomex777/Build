package com.tomex777.annie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.bookmarks.model.BookmarkCollection
import com.tomex777.annie.browser.engine.bookmarks.export.exportNetscapeBookmarkHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses real Omni HTML codec while preserving Annie's preexisting bookmarks prefs.
 * Nested-folder UI migration is deliberately a separate Agent 2 task.
 */
@RunWith(AndroidJUnit4::class)
class AnnieOmniBookmarkImportBridgeTest {
    @Test fun importIsIdempotentAndSurvivesBrowserRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val library = context.getSharedPreferences("annie_gecko_library_v1", Context.MODE_PRIVATE)
        val tabs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val previousLibrary = library.all
        val previousTabs = tabs.all
        var model: AnnieGeckoBrowserModel? = null
        try {
            library.edit().clear().commit()
            tabs.edit().clear().commit()
            val tree = BookmarkCollection()
            val folder = tree.addFolder("Engineering")
            tree.addBookmark("Docs", "https://docs.example.org", folder.id)
            tree.addBookmark("Search", "https://example.net/find")
            val html = exportNetscapeBookmarkHtml(tree)
            instrumentation.runOnMainSync {
                model = AnnieGeckoBrowserModel(context)
                assertEquals(2, model!!.importBookmarksHtml(html))
                assertEquals(0, model!!.importBookmarksHtml(html))
                assertEquals(2, model!!.bookmarks.size)
                assertTrue(model!!.exportBookmarksHtml().contains("https://docs.example.org"))
                model!!.destroy()
                model = AnnieGeckoBrowserModel(context)
                assertEquals(2, model!!.bookmarks.size)
                assertEquals(0, model!!.importBookmarksHtml(html))
            }
        } finally {
            instrumentation.runOnMainSync { model?.destroy() }
            fun restore(prefs: android.content.SharedPreferences, snapshot: Map<String, *>) {
                val edit = prefs.edit().clear()
                snapshot.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                    }
                }
                edit.commit()
            }
            restore(library, previousLibrary)
            restore(tabs, previousTabs)
        }
    }
}
