package com.example.whatsapp.data.night

import org.junit.Assert.assertEquals
import org.junit.Test

class NightProviderKeyRotationTest {
    private val keys = listOf(
        NightProviderCredential("a", "Key A", "secret-a"),
        NightProviderCredential("b", "Key B", "secret-b"),
        NightProviderCredential("c", "Key C", "secret-c"),
    )

    @Test
    fun rotatesStartingKeyAcrossRequests() {
        val rotation = NightProviderKeyRotation()

        assertEquals(
            listOf("a", "b", "c"),
            rotation.orderedCredentials("groq", keys, now = 1_000L).map { it.id },
        )
        assertEquals(
            listOf("b", "c", "a"),
            rotation.orderedCredentials("groq", keys, now = 1_000L).map { it.id },
        )
        assertEquals(
            listOf("c", "a", "b"),
            rotation.orderedCredentials("groq", keys, now = 1_000L).map { it.id },
        )
    }

    @Test
    fun coolingKeyIsSkippedUntilCooldownExpires() {
        val rotation = NightProviderKeyRotation()
        rotation.markCoolingDown(
            profileId = "groq",
            credentialId = "b",
            durationMs = 2_000L,
            now = 1_000L,
        )

        assertEquals(
            listOf("a", "c"),
            rotation.orderedCredentials("groq", keys, now = 1_500L).map { it.id },
        )

        assertEquals(
            listOf("b", "c", "a"),
            rotation.orderedCredentials("groq", keys, now = 3_001L).map { it.id },
        )
    }
}
