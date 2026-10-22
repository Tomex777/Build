package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnieDictationDraftTest {
    @Test fun spokenTextIsAppendedWithoutErasingExistingDraft() {
        assertEquals("Already typed some words", appendRecognizedText("Already typed", "some words"))
        assertEquals("Already typed more", appendRecognizedText("Already typed ", " more "))
        assertEquals("First line\nsecond line", appendRecognizedText("First line\n", "second line"))
    }

    @Test fun emptySpeechDoesNotMutateAnyDraft() {
        assertEquals("A draft ", appendRecognizedText("A draft ", "  "))
        assertEquals("", appendRecognizedText("", ""))
        assertEquals("Hello", appendRecognizedText("", " Hello "))
    }

    @Test fun punctuationAndNewlineAreRetained() {
        assertEquals("What next? Continue.", appendRecognizedText("What next?", " Continue. "))
        assertEquals("command /help then", appendRecognizedText("command /help", "then"))
    }
}
