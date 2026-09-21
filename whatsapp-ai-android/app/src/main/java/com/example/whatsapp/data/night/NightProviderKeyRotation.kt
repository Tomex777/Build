package com.example.whatsapp.data.night

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class NightProviderKeyRotation {
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    fun orderedCredentials(
        profileId: String,
        credentials: List<NightProviderCredential>,
        now: Long = System.currentTimeMillis(),
    ): List<NightProviderCredential> {
        if (credentials.isEmpty()) return emptyList()

        val cursor = cursors
            .getOrPut(profileId) { AtomicInteger(0) }
            .getAndIncrement()
        val start = Math.floorMod(cursor, credentials.size)
        val rotated = List(credentials.size) { offset ->
            credentials[(start + offset) % credentials.size]
        }

        return rotated.filter { credential ->
            (cooldownUntil[key(profileId, credential.id)] ?: 0L) <= now
        }
    }

    fun markCoolingDown(
        profileId: String,
        credentialId: String,
        durationMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        cooldownUntil[key(profileId, credentialId)] =
            now + durationMs.coerceAtLeast(0L)
    }

    fun clearCooldown(
        profileId: String,
        credentialId: String,
    ) {
        cooldownUntil.remove(key(profileId, credentialId))
    }

    private fun key(profileId: String, credentialId: String): String =
        profileId + ":" + credentialId
}
