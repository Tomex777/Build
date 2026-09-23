package com.example.whatsapp.extensions.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExtensionIdTest {
    @Test
    fun acceptsReverseDomainPackageIdAndCurrentManifestId() {
        val packageId = "com.night.extensions.animepahe"

        assertTrue(isValidNightExtensionId(packageId))
        assertEquals(packageId, parseNightExtensionId(JSONObject(), packageId))
        assertEquals(
            "animepahe",
            parseNightExtensionId(JSONObject().put("id", "animepahe"), packageId),
        )
    }

    @Test
    fun prefersLegacyExtensionIdAndRejectsMalformedNamespaces() {
        assertEquals(
            "reader.extension",
            parseNightExtensionId(
                JSONObject().put("extensionId", "Reader.Extension").put("id", "ignored"),
                "com.example.reader",
            ),
        )
        assertFalse(isValidNightExtensionId("com..animepahe"))
        assertFalse(isValidNightExtensionId("../animepahe"))
    }

    @Test
    fun fallsBackToInstalledPackageWhenManifestIdIsMalformed() {
        val packageId = "com.night.extensions.animepahe"
        assertEquals(
            packageId,
            parseNightExtensionId(
                JSONObject().put("extensionId", "../bad").put("id", "also..bad"),
                packageId,
            ),
        )
    }
}
