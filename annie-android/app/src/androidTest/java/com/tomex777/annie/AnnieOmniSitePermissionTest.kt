package com.tomex777.annie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.permissions.AnnieSitePermissionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Original Omni consent model with strict origin scoping for Annie permissions. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniSitePermissionTest {
    @Test fun exactOriginRulesCannotApplyToLookalikeHostsOrSchemes() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = app.getSharedPreferences("annie_browser_site_permissions_v1", Context.MODE_PRIVATE)
        val old = prefs.all
        try {
            prefs.edit().clear().commit()
            val store = AnnieSitePermissionStore(app)
            assertEquals("https://example.org", store.origin("https://Example.Org:443/path?session=private"))
            assertEquals("https://example.org:8443", store.origin("https://example.org:8443/"))
            assertEquals("http://example.org", store.origin("http://example.org:80"))
            assertNull(store.origin("javascript:alert(1)"))
            assertNull(store.origin("https://user@example.org/"))
            assertNull(store.origin("file:///private/data"))
            store.remember("https://example.org/login", "content_0", allow = true)
            assertEquals(true, store.decision("https://example.org/profile", "content_0"))
            assertNull(store.decision("http://example.org/profile", "content_0"))
            assertNull(store.decision("https://example.org.attacker.invalid", "content_0"))
            assertNull(store.decision("https://sub.example.org", "content_0"))
            store.remember("https://example.org", "camera", allow = false)
            assertEquals(false, store.decision("https://example.org", "camera"))
            store.clear("https://example.org", "camera")
            assertNull(store.decision("https://example.org", "camera"))
        } finally {
            val edit = prefs.edit().clear()
            old.forEach { (key, value) ->
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
}
