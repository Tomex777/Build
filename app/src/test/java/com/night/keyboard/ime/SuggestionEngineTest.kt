package com.night.keyboard.ime

import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    @Test fun prefixFiltersSuggestions() { val result = SuggestionEngine.suggest("I really tha"); assertTrue(result.all { it.startsWith("tha") }) }
    @Test fun emptyPrefixReturnsUsefulDefaults() { assertTrue(SuggestionEngine.suggest("hello ").isNotEmpty()) }
}
