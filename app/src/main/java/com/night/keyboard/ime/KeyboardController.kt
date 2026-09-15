package com.night.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class KeyboardController(private val service: InputMethodService) {
    private val connection: InputConnection? get() = service.currentInputConnection
    fun commit(text: String) { connection?.commitText(text, 1) }
    fun replaceCurrentWord(text: String) {
        val ic = connection ?: return
        val before = ic.getTextBeforeCursor(96, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { !it.isWhitespace() && it !in ".,!?;:\n" }
        if (prefix.isNotEmpty()) ic.deleteSurroundingTextInCodePoints(prefix.codePointCount(0, prefix.length), 0)
        ic.commitText(text, 1)
    }
    fun backspace() {
        val ic = connection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingTextInCodePoints(1, 0)
    }
    fun enter() {
        val ic = connection ?: return
        val options = service.currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = options and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (!ic.performEditorAction(action)) ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        } else ic.commitText("\n", 1)
    }
    fun moveCursor(delta: Int): Boolean {
        if (delta == 0) return false
        val ic = connection ?: return false
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val textLength = extracted.text?.length ?: return false
        val current = extracted.selectionEnd.coerceAtLeast(0)
        val target = (current + delta).coerceIn(0, textLength)
        if (target == current) return false
        return ic.setSelection(target, target)
    }
    fun textBeforeCursor(maxChars: Int = 120): String = connection?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()
    fun selectedText(): String = connection?.getSelectedText(0)?.toString().orEmpty()
    fun showInputPicker() { service.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
}
