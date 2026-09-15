package com.night.sora.model

enum class ContentType(val label: String) {
    ANIME("Anime"),
    MANGA("Manga"),
    MOVIE("Movies"),
    TV("TV"),
    MUSIC("Music"),
    MEME("Memes"),
}

data class MediaItem(
    val id: String,
    val sourceId: String,
    val extensionPackage: String,
    val type: ContentType,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val progress: Float = 0f,
    val metadata: Map<String, String> = emptyMap(),
)

data class AiMessage(
    val id: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}

data class AiConversation(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messages: List<AiMessage>,
)

data class LibraryEntry(
    val id: String,
    val label: String,
    val kind: String,
    val detail: String,
    val mediaId: String? = null,
    val sourceId: String? = null,
    val extensionPackage: String? = null,
    val contentType: ContentType? = null,
    val mediaSubtitle: String = "",
    val artworkUrl: String? = null,
) {
    fun toMediaSelection(): ExtensionMediaSelection? {
        val itemId = mediaId ?: return null
        val source = sourceId ?: return null
        val extension = extensionPackage ?: return null
        val type = contentType ?: return null
        return ExtensionMediaSelection(
            id = itemId,
            sourceId = source,
            extensionPackage = extension,
            type = type,
            title = label,
            subtitle = mediaSubtitle.ifBlank { detail },
            artworkUrl = artworkUrl,
        )
    }
}

data class ListeningSignal(
    val artistId: String,
    val artistName: String,
    val plays: Int = 0,
    val completions: Int = 0,
    val skips: Int = 0,
    val saved: Boolean = false,
    val lastPlayedEpochMs: Long = 0L,
)

data class ExtensionMediaSelection(
    val id: String,
    val sourceId: String,
    val extensionPackage: String,
    val type: ContentType,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
)

/** A page resolved by a manga source extension. Core owns rendering only. */
data class ReaderPage(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/** Immutable hand-off from an external manga source into Sora Core's reader. */
data class ReaderSession(
    val title: String,
    val chapterTitle: String,
    val sourceName: String,
    val pages: List<ReaderPage>,
    val initialPage: Int = 0,
)
