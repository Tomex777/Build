package com.night.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun prefixFiltersSuggestions() {
        val result = SuggestionEngine.suggest("I really tha")
        assertTrue(result.all { it.startsWith("tha") })
    }

    @Test
    fun emptyPrefixReturnsUsefulDefaults() {
        assertTrue(SuggestionEngine.suggest("hello ").isNotEmpty())
    }

    @Test
    fun autocorrectFixesSimpleTransposition() {
        assertEquals(
            Autocorrection("teh", "the"),
            SuggestionEngine.autocorrect("teh", aggression = 3),
        )
    }

    @Test
    fun autocorrectPreservesLeadingCapital() {
        assertEquals(
            "The",
            SuggestionEngine.autocorrect("Teh", aggression = 3)?.replacement,
        )
    }

    @Test
    fun knownWordsAreNotCorrected() {
        assertNull(SuggestionEngine.autocorrect("keyboard", aggression = 3))
    }

    @Test
    fun shortWordsAreLeftAlone() {
        assertNull(SuggestionEngine.autocorrect("an", aggression = 3))
    }

    @Test
    fun swipeDecoderUnderstandsCompressedRepeatedLetters() {
        assertEquals("hello", SuggestionEngine.decodeSwipe("helo"))
    }

    @Test
    fun swipeDecoderRejectsMeaninglessPath() {
        assertNull(SuggestionEngine.decodeSwipe("qzx"))
    }
}
