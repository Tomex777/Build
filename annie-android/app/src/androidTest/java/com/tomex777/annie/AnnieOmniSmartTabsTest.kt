package com.tomex777.annie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.tabs.BrowserTab
import com.tomex777.annie.browser.engine.tabs.SmartTabManager
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Genuine upstream Omni SmartTabManager, secured against substring spoofing. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniSmartTabsTest {
    @Test fun groupsOnlyFullDomainsOrTrueSubdomainsWithSemanticIconKeys() {
        val groups = SmartTabManager().categorize(listOf(
            BrowserTab("a", "Dev", "https://github.com/topic"),
            BrowserTab("b", "GitHub subdomain", "https://gist.github.com"),
            BrowserTab("c", "Spoof", "https://github.com.attacker.example/trap"),
            BrowserTab("d", "Partial match", "https://notgithub.com"),
            BrowserTab("e", "Social", "https://www.reddit.com/r/test"),
            BrowserTab("f", "Unknown", "about:blank")
        ))
        val dev = groups.single { it.name == "Dev" }
        assertEquals(setOf("a", "b"), dev.tabs.map { it.id }.toSet())
        assertEquals("code", dev.icon)
        val other = groups.single { it.name == "Other" }
        assertEquals(setOf("c", "d", "f"), other.tabs.map { it.id }.toSet())
        assertEquals("globe", other.icon)
        assertEquals(listOf("e"), groups.single { it.name == "Social" }.tabs.map { it.id })
        assertFalse(groups.any { it.icon.contains("🌐") })
    }

    @Test fun privateTabsMustNeverAppearInPublicGroupedNormalTabs() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val prior = prefs.all
        var model: AnnieGeckoBrowserModel? = null
        try {
            prefs.edit().clear().putString("open", JSONArray().toString()).commit()
            instrument.runOnMainSync {
                model = AnnieGeckoBrowserModel(context)
                val normal = model!!.newTab(address = "https://github.com", autoLoad = false)
                val private = model!!.newTab(
                    privateMode = true, address = "https://reddit.com/private-search", autoLoad = false
                )
                val allIds = model!!.groupedNormalTabs().flatMap { it.tabs.map(BrowserTab::id) }
                assertTrue(allIds.contains(normal.id))
                assertFalse("Do not reveal private tabs to regular tab groups", allIds.contains(private.id))
                assertFalse(allIds.any { it == private.id })
            }
        } finally {
            instrument.runOnMainSync { model?.destroy() }
            val edit = prefs.edit().clear()
            prior.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Int -> edit.putInt(key, value)
                }
            }
            edit.commit()
        }
    }
}
