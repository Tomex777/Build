package com.night.extensions.animepahe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePaheDescriptorTest {
    @Test
    fun extensionNoLongerDeclaresProviderOwnedDownloadStatusType() {
        assertFalse(
            AnimePaheNightExtensionService::class.java.declaredFields
                .any { it.name == "TYPE_DOWNLOAD" }
        )
    }

    @Test
    fun constantsRemainNamespacedAndStable() {
        assertEquals("animepahe", AnimePaheNightExtensionService.EXTENSION_ID)
        assertTrue(
            AnimePaheNightExtensionService.TYPE_ANIME.startsWith(
                AnimePaheNightExtensionService.EXTENSION_ID + "."
            )
        )
        assertTrue(
            AnimePaheNightExtensionService.TYPE_EPISODE.startsWith(
                AnimePaheNightExtensionService.EXTENSION_ID + "."
            )
        )
        assertTrue(
            AnimePaheNightExtensionService.TYPE_SOURCE.startsWith(
                AnimePaheNightExtensionService.EXTENSION_ID + "."
            )
        )
        assertTrue(
            AnimePaheNightExtensionService.TYPE_SETTINGS.startsWith(
                AnimePaheNightExtensionService.EXTENSION_ID + "."
            )
        )
        assertTrue(
            AnimePaheNightExtensionService.TYPE_VERIFY.startsWith(
                AnimePaheNightExtensionService.EXTENSION_ID + "."
            )
        )
    }
}
