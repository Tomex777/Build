package com.example.whatsapp.data.night

data class NightRecallHit(
    val chatTitle: String,
    val message: NightMessageEntity,
    val libraryItem: NightLibraryItemEntity?,
    val score: Int,
)

class NightRecallEngine(
    private val repository: NightRepository,
) {
    suspend fun findRelevant(
        currentChatId: String,
        query: String,
        limit: Int = 8,
    ): List<NightRecallHit> {
        val tokens = meaningfulTokens(query)
        if (tokens.isEmpty()) return emptyList()

        val chats = repository.getChats().associateBy { it.id }
        val candidates = mutableListOf<Pair<NightMessageEntity, Int>>()

        for (message in repository.getRecentMessagesAcrossChats()) {
            if (message.chatId == currentChatId || message.text.isBlank()) continue

            val haystack = message.text.lowercase()
            val matched = tokens.count { token -> token in haystack }
            if (matched == 0) continue

            val exactPhraseBonus =
                if (query.length >= 12 && haystack.contains(query.lowercase())) 5 else 0

            candidates += message to (matched * 2 + exactPhraseBonus)
        }

        val ranked = candidates
            .sortedWith(
                compareByDescending<Pair<NightMessageEntity, Int>> { it.second }
                    .thenByDescending { it.first.createdAt }
            )
            .take(limit)

        val hits = mutableListOf<NightRecallHit>()
        for ((message, score) in ranked) {
            val library = message.libraryFileId
                ?.let { repository.getLibraryItem(it) }

            hits += NightRecallHit(
                chatTitle = chats[message.chatId]?.title ?: "Older chat",
                message = message,
                libraryItem = library,
                score = score,
            )
        }

        return hits
    }

    private fun meaningfulTokens(text: String): Set<String> {
        val stop = setOf(
            "this", "that", "with", "from", "have", "what", "when", "where",
            "which", "about", "there", "their", "would", "could", "should",
            "into", "then", "than", "just", "like", "know", "want", "need",
            "does", "doing", "make", "made", "chat", "night", "thing",
        )

        return Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,}")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 4 }
            .filterNot { it in stop }
            .toSet()
            .take(12)
            .toSet()
    }
}
