package com.night.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs

class KeyboardController(private val service: InputMethodService) {
    private val connection: InputConnection? get() = service.currentInputConnection

    fun commit(text: String) {
        connection?.commitText(text, 1)
    }

    fun replaceCurrentWord(text: String) {
        val ic = connection ?: return
        val before = ic.getTextBeforeCursor(96, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { !it.isWhitespace() && it !in ".,!?;:\n" }
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingTextInCodePoints(prefix.codePointCount(0, prefix.length), 0)
        }
        ic.commitText(text, 1)
    }

    fun backspace() {
        val ic = connection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        }
    }

    fun enter() {
        val ic = connection ?: return
        val options = service.currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = options and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (!ic.performEditorAction(action)) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        } else {
            ic.commitText("\n", 1)
        }
    }

    /**
     * Move the real host-app caret without committing placeholder text.
     *
     * setSelection is preferred because it gives exact bounded movement when the
     * editor exposes extracted text. ExtractedText selection offsets are relative
     * to startOffset, so convert them back to absolute editor offsets before
     * calling setSelection. Some editors expose incomplete extracted state or
     * reject setSelection while composing, so directional key events are used as
     * a compatibility fallback.
     */
    fun moveCursor(delta: Int): Boolean {
        if (delta == 0) return false
        val ic = connection ?: return false

        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted?.text != null) {
            val textLength = extracted.text.length
            val startOffset = extracted.startOffset.coerceAtLeast(0)
            val currentRelative = extracted.selectionEnd.coerceIn(0, textLength)
            val currentAbsolute = startOffset + currentRelative
            val minAbsolute = startOffset
            val maxAbsolute = startOffset + textLength
            val targetAbsolute = (currentAbsolute + delta).coerceIn(minAbsolute, maxAbsolute)
            if (targetAbsolute != currentAbsolute && ic.setSelection(targetAbsolute, targetAbsolute)) {
                return true
            }
        }

        val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        var moved = false
        repeat(abs(delta)) {
            val down = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val up = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            moved = moved || down || up
        }
        return moved
    }

    fun textBeforeCursor(maxChars: Int = 120): String =
        connection?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()

    fun selectedText(): String =
        connection?.getSelectedText(0)?.toString().orEmpty()

    fun showInputPicker() {
        service.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }
}
