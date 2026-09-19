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
        val recent = repository.getRecentMessagesAcrossChats()
            .asSequence()
            .filter { it.chatId != currentChatId }
            .filter { it.text.isNotBlank() }
            .mapNotNull { message ->
                val haystack = message.text.lowercase()
                val matched = tokens.count { token -> token in haystack }
                if (matched == 0) return@mapNotNull null

                val exactPhraseBonus =
                    if (query.length >= 12 && haystack.contains(query.lowercase())) 5 else 0

                val score = matched * 2 + exactPhraseBonus
                val library = message.libraryFileId
                    ?.let { repository.getLibraryItem(it) }

                NightRecallHit(
                    chatTitle = chats[message.chatId]?.title ?: "Older chat",
                    message = message,
                    libraryItem = library,
                    score = score,
                )
            }
            .sortedWith(
                compareByDescending<NightRecallHit> { it.score }
                    .thenByDescending { it.message.createdAt }
            )
            .take(limit)
            .toList()

        return recent
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
