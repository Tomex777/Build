package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.whatsapp.data.NightFileLibrary
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightLibraryIntegrationInstrumentedTest {
    @Test
    fun canonicalLibraryIsSharedByUiStorageAndAiFileTools() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = NightLibraryStore.get(context)
        val files = NightFileContextService.get(context)

        val marker = "night-library-" + UUID.randomUUID()
        val saved = store.saveText(
            name = "Library bridge " + marker,
            text = "Persistent Library marker: " + marker,
            markdown = true,
            sourceChatId = "library-integration-test",
        ).getOrThrow()

        val listed = files.listLibrary(query = marker, limit = 10)
        assertTrue(listed.any { it.id == saved.id })

        val extracted = files.read(
            id = saved.id,
            query = marker,
        ).getOrThrow()
        assertEquals(saved.id, extracted.item.id)
        assertTrue(extracted.text.contains(marker))

        val legacySource = File(context.cacheDir, "legacy_" + marker + ".txt")
        legacySource.writeText("Legacy Library marker: " + marker)
        val legacy = requireNotNull(
            NightFileLibrary.registerLocalFile(
                context = context,
                source = legacySource,
                name = "Legacy " + marker + ".txt",
                mimeType = "text/plain",
            )
        )

        val migrated = store.migrateLegacy()
        assertTrue(migrated >= 1)

        val migratedRead = files.read(id = legacy.id).getOrThrow()
        assertEquals(legacy.id, migratedRead.item.id)
        assertTrue(migratedRead.text.contains(marker))
    }
}
