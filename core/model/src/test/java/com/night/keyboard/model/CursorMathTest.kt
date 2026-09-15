package com.night.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorMathTest {
    @Test fun dragMapsToCharacterSteps() {
        assertEquals(2, CursorMath.cursorStepForDrag(42f, 18f))
        assertEquals(-2, CursorMath.cursorStepForDrag(-42f, 18f))
    }
    @Test fun selectionIsClamped() {
        assertEquals(0, CursorMath.clampSelection(1, -5, 10))
        assertEquals(10, CursorMath.clampSelection(9, 5, 10))
    }
}
