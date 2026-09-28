package app.yomi.reader.local

import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId

enum class LibraryLocationType {
    DOCUMENT,
    TREE,
}

enum class LibraryAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_LOST,
}

data class LibraryBook(
    val id: ReaderBookId,
    val title: String,
    val locationUri: String,
    val locationType: LibraryLocationType,
    val fingerprint: String?,
    val dateAddedEpochMillis: Long,
    val lastOpenedEpochMillis: Long?,
    val availability: LibraryAvailability = LibraryAvailability.AVAILABLE,
    val pageCount: Int? = null,
    val progress: Double = 0.0,
    val coverUri: String? = null,
)

data class LibraryChapter(
    val id: ReaderChapterId,
    val bookId: ReaderBookId,
    val title: String,
    val order: Int,
    val location: String,
    val pageCount: Int?,
)
