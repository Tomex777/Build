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

data class AiAttachment(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
)

data class AiMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val attachments: List<AiAttachment> = emptyList(),
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

data class MediaProgressEntry(
    val mediaId: String,
    val sourceId: String,
    val extensionPackage: String,
    val contentType: ContentType,
    val title: String,
    val itemId: String,
    val itemLabel: String,
    val position: Long,
    val total: Long,
    val resumeSourceId: String = sourceId,
    val resumeExtensionPackage: String = extensionPackage,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (total > 0L) (position.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    fun toMediaSelection() = ExtensionMediaSelection(
        id = mediaId,
        sourceId = sourceId,
        extensionPackage = extensionPackage,
        type = contentType,
        title = title,
        subtitle = subtitle,
        artworkUrl = artworkUrl,
    )
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



enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadEntry(
    val id: String,
    val title: String,
    val itemLabel: String,
    val contentType: ContentType,
    val artworkUrl: String? = null,
    val sourceName: String = "",
    val mediaId: String? = null,
    val sourceId: String? = null,
    val extensionPackage: String? = null,
    val subtitle: String = "",
    val mimeType: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val filePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ActivitySignal(
    val id: Long,
    val contentType: ContentType,
    val title: String,
    val action: String,
    val occurredAt: Long,
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
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
    val consumptionSourceId: String = "",
    val consumptionExtensionPackage: String = "",
)

/** One concrete stream resolved by a watch source extension. */
data class PlaybackStream(
    val label: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)

/** Immutable hand-off from a source extension into Sora Core's video player. */
data class PlaybackSession(
    val title: String,
    val episodeTitle: String,
    val sourceName: String,
    val streams: List<PlaybackStream>,
    val initialStream: Int = 0,
    val initialPositionMs: Long = 0L,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
    val consumptionSourceId: String = "",
    val consumptionExtensionPackage: String = "",
)
