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

/** Native Omni-style Home is an unloaded Gecko tab, not a Google page request. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniNativeHomeTest {
    @Test fun newNormalAndPrivateTabsAreBlankWithNoSavedPrivateMetadata() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext
        val prefs = app.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val saved = prefs.all
        var model: AnnieGeckoBrowserModel? = null
        try {
            prefs.edit().clear().commit()
            instrument.runOnMainSync {
                model = AnnieGeckoBrowserModel(app)
                val normal = model!!.active!!
                assertEquals("about:blank", normal.url)
                assertFalse(normal.loading)
                assertTrue(normal.session.isOpen)
                val private = model!!.newTab(privateMode = true)
                assertEquals("about:blank", private.url)
                assertFalse(private.loading)
                assertTrue(private.session.isOpen)
                val stored = prefs.getString("open", "")!!
                assertFalse(stored.contains(private.id))
                assertFalse(stored.contains("Private"))
                assertEquals(1, JSONArray(stored).length())
                model!!.destroy()
                model = AnnieGeckoBrowserModel(app)
                assertTrue(model!!.tabs.none { it.privateMode })
                assertEquals("about:blank", model!!.active!!.url)
            }
        } finally {
            instrument.runOnMainSync { model?.destroy() }
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.commit()
        }
    }
}
