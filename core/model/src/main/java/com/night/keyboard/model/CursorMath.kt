package com.night.keyboard.model

object CursorMath {
    fun cursorStepForDrag(deltaPx: Float, pixelsPerCharacter: Float = 18f): Int {
        if (pixelsPerCharacter <= 0f) return 0
        return (deltaPx / pixelsPerCharacter).toInt()
    }

    fun clampSelection(current: Int, delta: Int, textLength: Int): Int =
        (current + delta).coerceIn(0, textLength.coerceAtLeast(0))
}
