package com.night.keyboard.ime

import kotlin.math.min

data class Autocorrection(
    val original: String,
    val replacement: String,
)

object SuggestionEngine {
    private val common = listOf(
        "the", "to", "and", "you", "that", "it", "is", "for", "of", "in", "this", "with",
        "thank", "thanks", "okay", "good", "going", "want", "will", "can", "just", "really",
        "because", "have", "what", "when", "where", "how", "yeah", "right", "please", "done",
        "hello", "hey", "hi", "yes", "no", "not", "we", "they", "he", "she", "there", "their",
        "they're", "your", "you're", "i'm", "i'll", "i've", "don't", "doesn't", "didn't", "can't",
        "could", "would", "should", "about", "after", "again", "also", "always", "another", "any",
        "are", "around", "back", "been", "before", "better", "but", "come", "day", "do", "even",
        "feel", "find", "first", "from", "get", "give", "go", "great", "had", "has", "here", "him",
        "her", "home", "if", "into", "know", "like", "look", "make", "me", "more", "most", "much",
        "my", "need", "new", "now", "one", "only", "other", "our", "out", "over", "people",
        "same", "say", "see", "send", "so", "some", "something", "still", "take", "tell", "than",
        "then", "thing", "think", "time", "too", "try", "up", "use", "very", "way", "well", "why",
        "work", "world", "write", "today", "tomorrow", "tonight", "morning", "night", "love", "sure",
        "sorry", "maybe", "probably", "actually", "already", "ready", "keep", "continue", "start",
        "finish", "complete", "build", "change", "fix", "check", "works", "working", "message",
        "keyboard", "phone", "app", "text", "word", "typing", "clipboard", "editor", "voice",
    ).distinct()

    private val frequencies: Map<String, Int> = common.withIndex().associate { (index, word) ->
        word to (common.size - index)
    }

    private val nextWords: Map<String, List<String>> = mapOf(
        "thank" to listOf("you", "you so much", "you again"),
        "how" to listOf("are", "is", "do"),
        "what" to listOf("do", "is", "are"),
        "i" to listOf("think", "want", "need"),
        "i'm" to listOf("going", "sure", "ready"),
        "you" to listOf("can", "are", "know"),
        "we" to listOf("can", "should", "need"),
        "good" to listOf("morning", "night", "job"),
        "see" to listOf("you", "this", "that"),
    )

    fun suggest(
        textBeforeCursor: String,
        learnedWords: Map<String, Int> = emptyMap(),
        limit: Int = 3,
    ): List<String> {
        if (limit <= 0) return emptyList()
        val prefix = currentWord(textBeforeCursor).lowercase()
        if (prefix.isNotBlank()) {
            return lexicon(learnedWords)
                .filter { it.startsWith(prefix) && it != prefix }
                .sortedByDescending { scoreFrequency(it, learnedWords) }
                .take(limit)
                .toList()
                .ifEmpty { listOf(prefix) }
        }

        val previous = previousWord(textBeforeCursor).lowercase()
        val contextual = nextWords[previous].orEmpty()
        if (contextual.isNotEmpty()) return contextual.take(limit)
        val personal = learnedWords.entries
            .asSequence()
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
        return if (personal.isNotEmpty()) personal else listOf("I’m", "the", "thank you").take(limit)
    }

    fun decodeSwipe(
        path: String,
        learnedWords: Map<String, Int> = emptyMap(),
    ): String? {
        val normalized = collapseRepeats(path.lowercase().filter(Char::isLetter))
        if (normalized.length < 2) return null

        return lexicon(learnedWords)
            .filter { candidate ->
                val compact = collapseRepeats(candidate.filter(Char::isLetter))
                compact.isNotEmpty() &&
                    compact.first() == normalized.first() &&
                    compact.last() == normalized.last()
            }
            .map { candidate ->
                val compact = collapseRepeats(candidate.filter(Char::isLetter))
                val distance = editDistance(normalized, compact, 8)
                val lengthPenalty = kotlin.math.abs(compact.length - normalized.length)
                val frequencyBonus = scoreFrequency(candidate, learnedWords) / 40
                Triple(candidate, distance * 3 + lengthPenalty - frequencyBonus, compact.length)
            }
            .filter { (_, score, length) -> score <= maxOf(6, length) }
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.second }
                    .thenByDescending { scoreFrequency(it.first, learnedWords) },
            )
            .firstOrNull()
            ?.first
    }

    fun autocorrect(
        word: String,
        aggression: Int = 2,
        learnedWords: Map<String, Int> = emptyMap(),
    ): Autocorrection? {
        val raw = word.trim()
        if (raw.length < 3 || raw.any(Char::isDigit) || raw.count(Char::isUpperCase) > 1) return null

        val lower = raw.lowercase()
        if (lower in common || lower in learnedWords) return null

        val maxDistance = when (aggression.coerceIn(1, 3)) {
            1 -> 1
            2 -> if (lower.length >= 6) 2 else 1
            else -> if (lower.length >= 3) 2 else 1
        }

        val candidate = lexicon(learnedWords)
            .filter { kotlin.math.abs(it.length - lower.length) <= maxDistance }
            .map { candidate ->
                val distance = editDistance(lower, candidate, maxDistance)
                Triple(candidate, distance, scoreFrequency(candidate, learnedWords))
            }
            .filter { (_, distance, _) -> distance in 1..maxDistance }
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.second }
                    .thenByDescending { it.third },
            )
            .firstOrNull()
            ?.first
            ?: return null

        return Autocorrection(raw, preserveCase(raw, candidate))
    }

    fun currentWord(text: String): String =
        text.takeLastWhile { it.isLetter() || it.code == 39 || it == '’' }

    private fun previousWord(text: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isBlank()) return ""
        return trimmed.takeLastWhile { it.isLetter() || it.code == 39 || it == '’' }
    }

    private fun lexicon(learnedWords: Map<String, Int>): Sequence<String> =
        (common.asSequence() + learnedWords.keys.asSequence()).distinct()

    private fun scoreFrequency(word: String, learnedWords: Map<String, Int>): Int =
        (frequencies[word] ?: 0) + (learnedWords[word] ?: 0) * 1_000

    private fun collapseRepeats(value: String): String = buildString {
        value.forEach { ch ->
            if (isEmpty() || last() != ch) append(ch)
        }
    }

    private fun preserveCase(source: String, target: String): String = when {
        source.firstOrNull()?.isUpperCase() == true ->
            target.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        else -> target
    }

    private fun editDistance(a: String, b: String, cutoff: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > cutoff) return cutoff + 1

        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost,
                )
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > cutoff) return cutoff + 1
            previous = current
        }
        return previous[b.length]
    }
}
