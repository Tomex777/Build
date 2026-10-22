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

@RunWith(AndroidJUnit4::class)
class AnnieGeckoTabRestoreTest {
    @Test fun recreationPreservesNormalTabsBeyondTwelveAndTheirSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val original = prefs.all
        instrumentation.runOnMainSync {
            var model: AnnieGeckoBrowserModel? = null
            try {
                val saved = JSONArray()
                repeat(13) { saved.put("https://example.org/tab-$it") }
                prefs.edit().clear().putString("open", saved.toString()).putInt("selected", 12).commit()
                model = AnnieGeckoBrowserModel(context)
                assertEquals(13, model!!.tabs.size)
                assertEquals("Normal-tab identities must survive cold recreation", stableIds, model!!.tabs.map { it.id })
                assertEquals("Background Gecko tabs must remain lazy after restart",
                    1, model!!.tabs.count { it.session.isOpen })
                val stableIds = model!!.tabs.map { it.id }
                assertEquals(13, stableIds.toSet().size)
                assertEquals("Cold start may open only the selected Gecko renderer",
                    1, model!!.tabs.count { it.session.isOpen })
                assertTrue(model!!.tabs.filterNot { it.id == model!!.active!!.id }
                    .all { it.needsRestoration })
                assertEquals("https://example.org/tab-12", model!!.active!!.url)
                model!!.toggleDesktop(model!!.active!!)
                model!!.newTab(privateMode = true, address = "https://example.net/private-overflow-proof", autoLoad = false)
                assertEquals(13, JSONArray(prefs.getString("open", "[]")).length())
                assertFalse(prefs.getString("open", "")!!.contains("private-overflow-proof"))
                model!!.destroy()
                model = AnnieGeckoBrowserModel(context)
                assertEquals(13, model!!.tabs.size)
                assertEquals("https://example.org/tab-12", model!!.active!!.url)
                assertTrue(model!!.active!!.desktopMode)
                assertTrue(model!!.tabs.none { it.privateMode })
            } finally {
                model?.destroy()
                val edit = prefs.edit().clear()
                original.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                    }
                }
                edit.commit()
            }
        }
    }

    @Test fun recreationRestoresSelectedNormalTabAndDesktopModeWithoutPrivateTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val original = prefs.all
        instrumentation.runOnMainSync {
            var model: AnnieGeckoBrowserModel? = null
            try {
                // Exercise migration from the previously shipped URL-only snapshot.
                prefs.edit().clear().putString("open", JSONArray()
                    .put("https://example.com/first")
                    .put("https://example.org/second").toString()).commit()
                model = AnnieGeckoBrowserModel(context)
                assertEquals(2, model!!.tabs.size)
                assertEquals("Legacy URL-only migration must assign stable tab IDs", stableIds, model!!.tabs.map { it.id })
                assertEquals(1, model!!.tabs.count { it.session.isOpen })
                val selected = model!!.tabs[1]
                assertEquals("Only restored foreground tab should create a renderer",
                    1, model!!.tabs.count { it.session.isOpen })
                val stableIds = model!!.tabs.map { it.id }
                model!!.select(selected)
                model!!.toggleDesktop(selected)
                model!!.newTab(privateMode = true, address = "https://example.net/private-proof", autoLoad = false)
                val snapshot = prefs.getString("open", "")!!
                assertFalse(snapshot.contains("private-proof"))
                assertEquals(1, prefs.getInt("selected", -1))
                model!!.destroy()

                model = AnnieGeckoBrowserModel(context)
                assertEquals(2, model!!.tabs.size)
                assertEquals("https://example.org/second", model!!.active!!.url)
                assertTrue(model!!.active!!.desktopMode)
                assertTrue(model!!.tabs.none { it.privateMode })
                assertFalse(model!!.tabs.first().desktopMode)
            } finally {
                model?.destroy()
                // Do not replace a user's browser snapshot with fixture tabs.
                val edit = prefs.edit().clear()
                original.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                    }
                }
                edit.commit()
            }
        }
    }
}
