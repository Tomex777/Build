package com.night.extensions.animepahe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePaheDescriptorTest {
    @Test
    fun descriptorLeavesGenericDownloadingToNightCore() {
        val descriptor = AnimePaheNightExtensionService().descriptor()

        val capabilities = descriptor.getJSONArray("capabilities")
        val capabilityValues =
            (0 until capabilities.length()).map(capabilities::getString)
        assertFalse(capabilityValues.contains("downloads"))

        val types = descriptor.getJSONArray("messageTypes")
        val messageTypeValues =
            (0 until types.length()).map {
                types.getJSONObject(it).getString("messageType")
            }
        assertFalse(messageTypeValues.any { it.contains("download_status") })
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
