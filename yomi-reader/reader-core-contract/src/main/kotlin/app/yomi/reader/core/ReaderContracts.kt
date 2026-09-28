package app.yomi.reader.core

import java.io.Closeable
import java.io.InputStream

@JvmInline
value class ReaderBookId(val value: String)

@JvmInline
value class ReaderChapterId(val value: String)

@JvmInline
value class ReaderPageId(val value: String)

data class ReaderBook(
    val id: ReaderBookId,
    val title: String,
)

data class ReaderChapter(
    val id: ReaderChapterId,
    val bookId: ReaderBookId,
    val title: String,
    val order: Int,
)

data class ReaderPage(
    val id: ReaderPageId,
    val chapterId: ReaderChapterId,
    val index: Int,
    val displayName: String,
)

interface ReaderPageSource : Closeable {
    suspend fun pages(chapter: ReaderChapter): List<ReaderPage>
    suspend fun open(page: ReaderPage): InputStream
    override fun close() = Unit
}

enum class ReadingMode {
    LTR_PAGED,
    RTL_PAGED,
    VERTICAL_PAGED,
    WEBTOON,
}

enum class ReaderBackground {
    BLACK,
    DARK,
    LIGHT,
}

enum class ReaderScaleMode {
    FIT_SCREEN,
    STRETCH,
    FIT_WIDTH,
    FIT_HEIGHT,
    ORIGINAL,
    SMART_FIT,
}

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.LTR_PAGED,
    val background: ReaderBackground = ReaderBackground.BLACK,
    val scaleMode: ReaderScaleMode = ReaderScaleMode.FIT_SCREEN,
    val cropBorders: Boolean = false,
    val volumeKeysEnabled: Boolean = false,
    val showPageNumber: Boolean = true,
)

data class ReaderLocation(
    val bookId: ReaderBookId,
    val chapterId: ReaderChapterId,
    val pageIndex: Int,
    val pageOffsetFraction: Double = 0.0,
    val overallProgress: Double = 0.0,
) {
    init {
        require(pageIndex >= 0)
        require(pageOffsetFraction in 0.0..1.0)
        require(overallProgress in 0.0..1.0)
    }
}

interface ReaderProgressSink {
    suspend fun restore(bookId: ReaderBookId): ReaderLocation?
    suspend fun onLocationChanged(location: ReaderLocation)
    suspend fun onSessionClosed(location: ReaderLocation)
}

interface ReaderSessionHost {
    val book: ReaderBook
    val settings: ReaderSettings
    val pageSource: ReaderPageSource
    val progressSink: ReaderProgressSink

    suspend fun chapters(): List<ReaderChapter>
    suspend fun previous(chapter: ReaderChapter): ReaderChapter?
    suspend fun next(chapter: ReaderChapter): ReaderChapter?
}

interface ReaderHostActions {
    suspend fun bookmark(location: ReaderLocation)
    suspend fun share(page: ReaderPage): Boolean
    suspend fun save(page: ReaderPage): Boolean
}
