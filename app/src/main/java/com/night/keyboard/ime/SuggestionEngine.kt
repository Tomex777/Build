package com.night.keyboard.ime

object SuggestionEngine {
    private val common = listOf("the", "to", "and", "you", "that", "it", "is", "for", "of", "in", "this", "with", "thank", "thanks", "okay", "good", "going", "want", "will", "can", "just", "really", "because", "have", "what", "when", "where", "how", "yeah", "right", "please", "done")
    fun suggest(textBeforeCursor: String, limit: Int = 3): List<String> {
        val prefix = textBeforeCursor.lowercase().takeLastWhile { it.isLetter() || it == '\'' }
        if (prefix.isBlank()) return listOf("I’m", "the", "thank you").take(limit)
        return common.asSequence().filter { it.startsWith(prefix) && it != prefix }.distinct().take(limit).toList().ifEmpty { listOf(prefix) }
    }
}
