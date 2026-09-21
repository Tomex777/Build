package com.example.whatsapp.data.night

object NightSummaryPolicy {
    const val CHECKPOINT_INTERVAL_MS: Long = 5L * 60L * 1000L
}

object NightSummaryText {
    fun currentChatContext(summary: String): String {
        val memory = summary.trim().take(5000)
        if (memory.isBlank()) return ""

        return buildString {
            append("Persistent summary of this chat (older context; newer messages override it):\\n")
            append(memory)
        }
    }

    fun mergeFallback(
        previous: String,
        transcript: String,
    ): String {
        val old = previous.trim()
        val recent = transcript
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(14)
            .joinToString(" • ") { it.take(240) }
            .take(2600)

        if (old.isBlank()) return recent.take(5000)
        if (recent.isBlank()) return old.take(5000)

        return buildString {
            append(old.take(3500))
            append("\nRecent update: ")
            append(recent.take(1400))
        }.take(5000)
    }
}
