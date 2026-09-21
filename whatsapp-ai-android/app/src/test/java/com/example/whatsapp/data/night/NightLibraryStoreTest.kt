package com.example.whatsapp.data.night

import com.example.whatsapp.data.NightLibraryFile
import org.junit.Assert.assertEquals
import org.junit.Test

class NightLibraryStoreTest {
    @Test
    fun textFileNamesGetExpectedExtension() {
        assertEquals("Project notes.md", NightLibraryStore.normalizedTextName("Project notes", true))
        assertEquals("Project notes.md", NightLibraryStore.normalizedTextName("Project notes.md", true))
        assertEquals("Project notes.txt", NightLibraryStore.normalizedTextName("Project notes", false))
    }


    @Test
    fun legacyLibraryEntryMapsToCanonicalRoomEntity() {
        val legacy = NightLibraryFile(
            id = "library-1",
            name = "notes.pdf",
            mimeType = "application/pdf",
            sizeBytes = 42L,
            localPath = "/data/user/0/night/files/night_library/library-1_notes.pdf",
            createdAt = 1234L,
        )

        val entity = NightLibraryStore.entityFromLegacy(legacy)

        assertEquals(legacy.id, entity.id)
        assertEquals(legacy.name, entity.name)
        assertEquals(legacy.mimeType, entity.mimeType)
        assertEquals(legacy.sizeBytes, entity.sizeBytes)
        assertEquals(legacy.localPath, entity.localPath)
        assertEquals(legacy.createdAt, entity.createdAt)
        assertEquals(null, entity.sourceChatId)
        assertEquals(null, entity.sourceMessageId)
    }
}
