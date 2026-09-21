package com.example.whatsapp.data.browser

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightBrowserVerificationTest {
    @Test
    fun explicitVerifiedTrueWins() {
        val outcome = NightBrowserVerification.interpretResult(
            JSONObject()
                .put("verified", true)
                .put("message", "Session accepted.")
        )

        assertTrue(outcome.verified)
        assertEquals("Session accepted.", outcome.message)
    }

    @Test
    fun successStatusIsAcceptedWhenVerifiedBooleanIsAbsent() {
        val outcome = NightBrowserVerification.interpretResult(
            JSONObject()
                .put("status", "success")
                .put("detail", "Signed in.")
        )

        assertTrue(outcome.verified)
        assertEquals("Signed in.", outcome.message)
    }

    @Test
    fun explicitFalseBeatsSuccessStatus() {
        val outcome = NightBrowserVerification.interpretResult(
            JSONObject()
                .put("verified", false)
                .put("status", "success")
                .put("error", "Cookie expired.")
        )

        assertFalse(outcome.verified)
        assertEquals("Cookie expired.", outcome.message)
    }

    @Test
    fun missingHandlerProducesFailure() {
        val outcome = NightBrowserVerification.interpretResult(null)

        assertFalse(outcome.verified)
        assertTrue(outcome.message.contains("not available"))
    }

    @Test
    fun applyingOutcomeUpdatesStateWithoutChangingSession() {
        val spec = NightBrowserSpec(
            sessionId = "anime.verify",
            initialUrl = "https://anime.example.com/start",
            allowedHosts = listOf("anime.example.com"),
        )

        val updated = NightBrowserVerification.applyOutcome(
            spec = spec,
            outcome = NightBrowserVerificationOutcome(
                verified = true,
                message = "Good.",
            ),
            now = 1234L,
        )

        assertEquals("anime.verify", updated.sessionId)
        assertEquals(NightBrowserVerificationState.Verified, updated.verificationState)
        assertEquals("Good.", updated.verificationMessage)
        assertEquals(1234L, updated.verifiedAt)
    }
}
