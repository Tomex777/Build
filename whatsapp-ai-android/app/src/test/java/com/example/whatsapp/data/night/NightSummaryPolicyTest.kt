package com.example.whatsapp.data.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightSummaryPolicyTest {
    @Test
    fun checkpointCadenceIsExactlyFiveMinutes() {
        assertEquals(300_000L, NightSummaryPolicy.CHECKPOINT_INTERVAL_MS)
    }

    @Test
    fun currentChatContextRestoresPersistentSummary() {
        val context = NightSummaryText.currentChatContext(
            "User prefers dark mode. Pending task: finish Library + Tools integration."
        )

        assertTrue(context.contains("Persistent summary of this chat"))
        assertTrue(context.contains("User prefers dark mode."))
        assertTrue(context.contains("newer messages override it"))
    }

    @Test
    fun blankCurrentChatSummaryAddsNoPromptContext() {
        assertEquals("", NightSummaryText.currentChatContext("   "))
    }

    @Test
    fun fallbackPreservesPreviousSummaryAndAddsRecentUpdate() {
        val merged = NightSummaryText.mergeFallback(
            previous = "User prefers dark mode. Pending task: finish browser verification.",
            transcript = """
                You: Browser verification is now green.
                Night: Next we are moving to Memory & Summary.
            """.trimIndent(),
        )

        assertTrue(merged.contains("User prefers dark mode."))
        assertTrue(merged.contains("Pending task: finish browser verification."))
        assertTrue(merged.contains("Browser verification is now green."))
        assertTrue(merged.contains("Recent update:"))
    }

    @Test
    fun emptyPreviousSummaryUsesRecentTranscriptDirectly() {
        val merged = NightSummaryText.mergeFallback(
            previous = "",
            transcript = "You: Keep this decision.",
        )

        assertEquals("You: Keep this decision.", merged)
        assertFalse(merged.contains("Recent update:"))
    }

    @Test
    fun emptyRecentTranscriptDoesNotEraseExistingSummary() {
        val merged = NightSummaryText.mergeFallback(
            previous = "Existing memory.",
            transcript = "   ",
        )

        assertEquals("Existing memory.", merged)
    }
}
