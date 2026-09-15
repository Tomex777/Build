package com.night.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutFactoryTest {
    @Test fun lettersPaneHasPermanent123Key() {
        val bottom = KeyboardLayoutFactory.letterRows.last()
        assertEquals(SpecialKey.NUMBERS, bottom.first().special)
        assertEquals("123", bottom.first().label)
    }
    @Test fun symbolPanesCanReturnToLetters() {
        assertTrue(KeyboardLayoutFactory.symbolRows.last().any { it.special == SpecialKey.LETTERS })
        assertTrue(KeyboardLayoutFactory.moreSymbolRows.last().any { it.special == SpecialKey.LETTERS })
    }
    @Test fun qwertyHasEveryLetterExactlyOnce() {
        val letters = KeyboardLayoutFactory.letterRows.flatten().mapNotNull { key -> key.output?.singleOrNull()?.takeIf(Char::isLetter) }
        assertEquals(26, letters.size)
        assertEquals(26, letters.toSet().size)
    }
}
