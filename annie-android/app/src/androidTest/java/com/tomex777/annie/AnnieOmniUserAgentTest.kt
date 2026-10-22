package com.tomex777.annie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.useragent.UserAgentManager
import com.tomex777.annie.browser.engine.useragent.UserAgentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Test real upstream Omni UA preferences and GeckoSessionSettings integration. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniUserAgentTest {
    @Test fun siteRulesApplyOnlyToTheCorrectHostnameAndPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("annie_browser_user_agent_prefs_v1", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            prefs.edit().clear().commit()
            val manager = UserAgentManager(context)
            assertNull(manager.resolveUserAgent("https://example.org"))
            manager.addOrUpdateSiteRule("example.org", UserAgentPreset.CHROME_ANDROID)
            assertEquals(UserAgentPreset.CHROME_ANDROID.userAgentString,
                manager.resolveUserAgent("https://example.org/news"))
            assertEquals(UserAgentPreset.CHROME_ANDROID.userAgentString,
                manager.resolveUserAgent("https://www.example.org/news"))
            assertNull("Must not spoof an unrelated suffix", manager.resolveUserAgent("https://notexample.org"))
            manager.addOrUpdateSiteRule("login.example.org", UserAgentPreset.FIREFOX_ANDROID)
            assertEquals(UserAgentPreset.CHROME_ANDROID.userAgentString,
                manager.resolveUserAgent("https://example.org"))
            assertEquals(UserAgentPreset.FIREFOX_ANDROID.userAgentString,
                manager.resolveUserAgent("https://login.example.org"))
            assertEquals(UserAgentPreset.FIREFOX_ANDROID.userAgentString,
                UserAgentManager(context).resolveUserAgent("https://login.example.org"))
            manager.removeSiteRulesForDomain("example.org")
            assertNull(manager.resolveUserAgent("https://example.org"))
        } finally {
            val editor = prefs.edit().clear()
            for ((key, value) in saved) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.commit()
        }
    }

    @Test fun realGeckoSessionReceivesAndRevertsOverride() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val uaPrefs = context.getSharedPreferences("annie_browser_user_agent_prefs_v1", Context.MODE_PRIVATE)
        val tabPrefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val oldUA = uaPrefs.all
        val oldTabs = tabPrefs.all
        var model: AnnieGeckoBrowserModel? = null
        try {
            uaPrefs.edit().clear().commit()
            tabPrefs.edit().clear().commit()
            instrumentation.runOnMainSync {
                model = AnnieGeckoBrowserModel(context)
                model!!.setSiteUserAgent("example.org", UserAgentPreset.CHROME_ANDROID)
                val valid = model!!.newTab(address = "https://login.example.org", autoLoad = false)
                val unrelated = model!!.newTab(address = "https://other.org", autoLoad = false)
                assertEquals(UserAgentPreset.CHROME_ANDROID.userAgentString,
                    valid.session.settings.userAgentOverride)
                assertNull(unrelated.session.settings.userAgentOverride)
                model!!.setGlobalUserAgent(UserAgentPreset.FIREFOX_ANDROID)
                assertEquals(UserAgentPreset.FIREFOX_ANDROID.userAgentString,
                    unrelated.session.settings.userAgentOverride)
                assertEquals(UserAgentPreset.CHROME_ANDROID.userAgentString,
                    valid.session.settings.userAgentOverride)
                model!!.removeSiteUserAgent("example.org")
                assertEquals(UserAgentPreset.FIREFOX_ANDROID.userAgentString,
                    valid.session.settings.userAgentOverride)
                model!!.setGlobalUserAgent(UserAgentPreset.DEFAULT)
                assertNull(valid.session.settings.userAgentOverride)
                assertNull(unrelated.session.settings.userAgentOverride)
            }
        } finally {
            instrumentation.runOnMainSync { model?.destroy() }
            fun restore(prefs: android.content.SharedPreferences, saved: Map<String, *>) {
                val editor = prefs.edit().clear()
                for ((key, value) in saved) {
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
            restore(uaPrefs, oldUA)
            restore(tabPrefs, oldTabs)
        }
    }
}
