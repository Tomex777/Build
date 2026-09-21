package com.example.whatsapp.data.night

object NightSummaryPolicy {
    const val CHECKPOINT_INTERVAL_MS: Long = 5L * 60L * 1000L
}

object NightSummaryText {
    fun mergeFallback(
        previous: String,
        transcript: String,
    ): String {
        val old = previous.trim()
        val recent = transcript
            .lineSequence()
            .filter { it.isNotBlank() }
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
