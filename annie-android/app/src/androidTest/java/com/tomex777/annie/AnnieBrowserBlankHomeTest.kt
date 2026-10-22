package com.tomex777.annie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Gecko model and SharedPreferences (API26/API36), not Compose fake
 * state. A real new-tab Home must NEVER silently download the Google homepage.
 */
@RunWith(AndroidJUnit4::class)
class AnnieBrowserBlankHomeTest {
    @Test fun coldStartAndLastTabCloseStayEmptyWithoutCreatingFakeHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val tabPrefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val libraryPrefs = context.getSharedPreferences("annie_gecko_library_v1", Context.MODE_PRIVATE)
        val originalTabs = tabPrefs.all
        val originalLibrary = libraryPrefs.all

        instrumentation.runOnMainSync {
            var model: AnnieGeckoBrowserModel? = null
            try {
                tabPrefs.edit().clear().commit()
                libraryPrefs.edit().clear().commit()
                model = AnnieGeckoBrowserModel(context)
                assertEquals(1, model!!.tabs.size)
                val tab = model!!.active!!
                assertEquals("about:blank", tab.url)
                assertEquals("New tab", tab.title)
                assertFalse(tab.loading)
                assertTrue(model!!.history.isEmpty())
                assertTrue(model!!.bookmarks.isEmpty())

                val firstId = tab.id
                model!!.close(tab)
                assertEquals(1, model!!.tabs.size)
                assertEquals("about:blank", model!!.active!!.url)
                assertFalse(model!!.active!!.id == firstId)
                assertTrue(model!!.history.isEmpty())

                // Internal blank URL is restorable metadata, never history.
                val saved = JSONArray(tabPrefs.getString("open", "[]"))
                assertEquals(1, saved.length())
                assertEquals("about:blank", saved.getJSONObject(0).getString("url"))
                model!!.destroy()
                model = AnnieGeckoBrowserModel(context)
                assertEquals("about:blank", model!!.active!!.url)
                assertTrue(model!!.history.isEmpty())
            } finally {
                model?.destroy()
                restore(tabPrefs, originalTabs)
                restore(libraryPrefs, originalLibrary)
            }
        }
    }

    @Test fun privateBlankTabsNeverPersistAndOmniboxBlocksActiveSchemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val original = prefs.all
        instrumentation.runOnMainSync {
            var model: AnnieGeckoBrowserModel? = null
            try {
                prefs.edit().clear().commit()
                model = AnnieGeckoBrowserModel(context)
                val privateTab = model!!.newTab(privateMode = true)
                assertEquals("about:blank", privateTab.url)
                assertFalse(model!!.load(privateTab, "javascript:alert(1)"))
                assertFalse(model!!.load(privateTab, "file:///data/local/tmp/secret"))
                assertFalse(model!!.load(privateTab, "about:blank"))
                assertEquals("about:blank", privateTab.url)
                assertEquals(1, JSONArray(prefs.getString("open", "[]")).length())
                assertTrue(model!!.history.none { it.url == "about:blank" })
            } finally {
                model?.destroy()
                restore(prefs, original)
            }
        }
    }

    private fun restore(prefs: android.content.SharedPreferences, values: Map<String, *>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) ->
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
}
