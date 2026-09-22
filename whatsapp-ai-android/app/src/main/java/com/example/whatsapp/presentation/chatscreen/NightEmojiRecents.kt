package com.example.whatsapp.presentation.chatscreen

import android.content.Context
import android.icu.text.BreakIterator

/**
 * Persistent emoji recents shared by the Night composer and emoji picker.
 *
 * Most-recent first, unique, capped at 30. Keyboard-entered emoji are recorded
 * too, so Recent reflects actual usage rather than only picker taps.
 */
object NightEmojiRecents {
    private const val PREFS = "night_emoji_recents"
    private const val KEY = "recent"
    private const val MAX_RECENTS = 30
    private const val SEPARATOR = "\u001F"

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_RECENTS)

    fun record(
        context: Context,
        emoji: String,
    ): List<String> {
        val normalized = emoji.trim()
        if (normalized.isBlank()) return load(context)

        val updated =
            buildList {
                add(normalized)
                load(context)
                    .filterNot { it == normalized }
                    .forEach(::add)
            }.take(MAX_RECENTS)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, updated.joinToString(SEPARATOR))
            .apply()

        return updated
    }

    fun recordFromText(
        context: Context,
        text: String,
    ): List<String> {
        var recents = load(context)
        extractEmojiClusters(text).forEach { emoji ->
            recents = record(context, emoji)
        }
        return recents
    }

    internal fun extractEmojiClusters(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)

        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            if (cluster.codePoints().anyMatch(::isEmojiCodePoint)) {
                result += cluster
            }
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint in 0x2B00..0x2BFF ||
            codePoint in 0x1F1E6..0x1F1FF ||
            codePoint == 0x00A9 ||
            codePoint == 0x00AE ||
            codePoint == 0x203C ||
            codePoint == 0x2049 ||
            codePoint == 0x2122 ||
            codePoint == 0x2139 ||
            codePoint == 0x3030 ||
            codePoint == 0x303D ||
            codePoint == 0x3297 ||
            codePoint == 0x3299
}
