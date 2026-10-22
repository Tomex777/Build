package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.browser.engine.AnnieSessionState
import com.tomex777.annie.browser.engine.SessionStatePersistence
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Invalid, mixed-up and incognito recovery files must never open in normal tabs. */
@RunWith(AndroidJUnit4::class)
class AnnieOmniPersistenceSecurityTest {
    @Test fun rejectsPrivateWrongUrlWrongTabAndUnknownSchemaFromDisk() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "omni-security-${UUID.randomUUID()}").apply { mkdirs() }
        val persistence = SessionStatePersistence(root)
        try {
            val states = JSONObject()
            fun add(id: String, diskTabId: String, url: String, privateTab: Boolean,
                    schema: Int = AnnieSessionState.CURRENT_SCHEMA_VERSION) {
                states.put(id, AnnieSessionState(
                    schemaVersion = schema,
                    tabId = diskTabId,
                    // Deliberately invalid Gecko state. All four reads must
                    // refuse metadata BEFORE attempting to deserialize it.
                    sessionStateBytes = "not a Gecko SessionState".toByteArray(),
                    metadata = AnnieSessionState.TabMetadata(
                        title = "Fixture", url = url, isIncognito = privateTab,
                        lastActiveTime = 0L, canGoBack = false, canGoForward = false
                    )
                ).toJson())
            }
            add("private", "private", "https://example.test/secret", true)
            add("changed", "changed", "https://example.test/old", false)
            add("swapped", "someone-else", "https://example.test/right", false)
            add("future", "future", "https://example.test/right", false, schema = 100)
            File(root, "browser_session_states.json").writeText(
                JSONObject().put("version", 1).put("states", states).toString()
            )

            assertNull(persistence.readDurableState("private", "https://example.test/secret"))
            assertNull(persistence.readDurableState("changed", "https://example.test/new"))
            assertNull(persistence.readDurableState("swapped", "https://example.test/right"))
            assertNull(persistence.readDurableState("future", "https://example.test/right"))
        } finally {
            persistence.shutdown()
            root.deleteRecursively()
        }
    }
}
