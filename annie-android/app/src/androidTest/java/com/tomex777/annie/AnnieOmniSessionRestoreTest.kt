package com.tomex777.annie

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.io.File

/**
 * Actual Gecko navigation/session checkpoints, not a fake tab serializer.
 * Runs against a local HTTP server, so network provider flakiness is excluded.
 */
@RunWith(AndroidJUnit4::class)
class AnnieOmniSessionRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realOmniCheckpointRecoversNormalSessionWithoutPrivateTabLeak() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
        val originalPrefs = prefs.all
        val root = File(context.filesDir, "annie_browser_sessions_v2")
        val durable = File(root, "browser_session_states.json")
        val backup = File(root, "browser_session_states.json.bak")
        val originalDurable = durable.takeIf { it.exists() }?.readBytes()
        val originalBackup = backup.takeIf { it.exists() }?.readBytes()
        val server = GeckoLocalFixture()
        lateinit var model: AnnieGeckoBrowserModel
        var initialized = false
        lateinit var normalId: String
        lateinit var privateId: String
        val uiSession = mutableStateOf<GeckoSession?>(null)
        try {
            compose.runOnUiThread {
                // Unit-test navigation must not inherit previously saved network tabs.
                prefs.edit().clear().commit()
                model = AnnieGeckoBrowserModel(context)
                initialized = true
                val oldTabs = model.tabs.toList()
                val normal = model.newTab(address = server.baseUrl + "/one", autoLoad = false)
                oldTabs.forEach(model::close)
                normalId = normal.id
                val private = model.newTab(
                    privateMode = true, address = server.baseUrl + "/private-not-persisted", autoLoad = false
                )
                privateId = private.id
                model.select(normal)
                uiSession.value = normal.session
            }
            compose.setContent {
                val current = uiSession.value
                if (current != null) key(current) {
                    AndroidView(
                        factory = { GeckoView(it).apply { setSession(current) } },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.releaseSession() }
                    )
                }
            }
            compose.runOnUiThread { assertTrue(model.load(model.active!!, server.baseUrl + "/one")) }
            compose.waitUntil(45_000) {
                model.active?.title == "Gecko one" && model.active?.loading == false
            }
            compose.runOnUiThread { assertTrue(model.load(model.active!!, server.baseUrl + "/two")) }
            compose.waitUntil(45_000) {
                model.active?.url == server.baseUrl + "/two" && model.active?.loading == false
            }
            // Real upstream Omni persistence waits 500ms after Gecko's native
            // onSessionStateChange; waiting on actual on-disk bytes validates wiring.
            compose.waitUntil(45_000) {
                runCatching {
                    val states = JSONObject(durable.readText()).getJSONObject("states")
                    val normal = states.getJSONObject(normalId)
                    normal.getJSONObject("metadata").getString("url") == server.baseUrl + "/two"
                }.getOrDefault(false)
            }
            val raw = durable.readText()
            val states = JSONObject(raw).getJSONObject("states")
            assertTrue(states.has(normalId))
            assertFalse("Private identity must not reach persistent storage", raw.contains(privateId))
            assertFalse("Private URL must not reach persistent storage", raw.contains("private-not-persisted"))
            val originalNormalId = normalId
            compose.runOnUiThread {
                model.destroy()
                model = AnnieGeckoBrowserModel(context)
                val restored = model.tabs.single { it.id == originalNormalId }
                assertEquals(server.baseUrl + "/two", restored.url)
                assertTrue("Private Gecko tabs must die with browser process", model.tabs.none { it.privateMode })
                uiSession.value = restored.session
            }
            // The browser had visited /one and /two; native Gecko restore should
            // preserve the actual history stack, not just reload the saved URL.
            compose.waitUntil(45_000) {
                model.active?.id == originalNormalId && model.active?.canBack == true
            }
            compose.runOnUiThread { model.active!!.session.goBack() }
            compose.waitUntil(45_000) { model.active?.url == server.baseUrl + "/one" }
        } finally {
            compose.runOnUiThread {
                uiSession.value = null
                if (initialized) model.destroy()
                val edit = prefs.edit().clear()
                originalPrefs.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Float -> edit.putFloat(key, value)
                    }
                }
                edit.commit()
            }
            // Return exact previous content rather than deleting other user tabs.
            if (originalDurable == null) durable.delete() else durable.writeBytes(originalDurable)
            if (originalBackup == null) backup.delete() else backup.writeBytes(originalBackup)
            server.close()
        }
    }
}
