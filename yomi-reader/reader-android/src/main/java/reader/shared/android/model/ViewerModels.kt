package reader.shared.android.model

import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageSource
import java.io.InputStream

class ViewerPage(
    val page: ReaderPage,
    private val source: ReaderPageSource,
) {
    val index: Int get() = page.index
    val number: Int get() = page.index + 1
    val displayName: String get() = page.displayName
    lateinit var chapter: ViewerChapter
        internal set

    suspend fun open(): InputStream = source.open(page)

    override fun equals(other: Any?): Boolean =
        other is ViewerPage && page.id == other.page.id

    override fun hashCode(): Int = page.id.hashCode()
}

class ViewerChapter(
    val chapter: ReaderChapter,
    private val source: ReaderPageSource,
) {
    var pages: List<ViewerPage>? = null
        private set

    var requestedPage: Int = 0
    var requestedOffsetFraction: Double = 0.0

    suspend fun load(): List<ViewerPage> {
        pages?.let { return it }
        return source.pages(chapter)
            .map { ViewerPage(it, source).also { page -> page.chapter = this } }
            .also { pages = it }
    }

    fun unload() {
        pages = null
    }

    override fun equals(other: Any?): Boolean =
        other is ViewerChapter && chapter.id == other.chapter.id

    override fun hashCode(): Int = chapter.id.hashCode()
}

data class ViewerChapters(
    val currChapter: ViewerChapter,
    val prevChapter: ViewerChapter?,
    val nextChapter: ViewerChapter?,
)

sealed class ChapterTransition {
    abstract val from: ViewerChapter
    abstract val to: ViewerChapter?

    data class Prev(
        override val from: ViewerChapter,
        override val to: ViewerChapter?,
    ) : ChapterTransition()

    data class Next(
        override val from: ViewerChapter,
        override val to: ViewerChapter?,
    ) : ChapterTransition()
}
