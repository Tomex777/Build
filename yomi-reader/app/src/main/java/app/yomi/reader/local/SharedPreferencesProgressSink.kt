package app.yomi.reader.local

import android.content.Context
import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import app.yomi.reader.core.ReaderProgressSink

class SharedPreferencesProgressSink(context: Context) : ReaderProgressSink {
    private val prefs = context.getSharedPreferences("yomi_reader_progress", Context.MODE_PRIVATE)

    override suspend fun restore(bookId: ReaderBookId): ReaderLocation? {
        val prefix = prefix(bookId)
        val chapter = prefs.getString(prefix + "chapter", null) ?: return null
        val page = prefs.getInt(prefix + "page", -1)
        if (page < 0) return null

        return ReaderLocation(
            bookId = bookId,
            chapterId = ReaderChapterId(chapter),
            pageIndex = page,
            pageOffsetFraction = Double.fromBits(prefs.getLong(prefix + "offset", 0.0.toBits())),
            overallProgress = Double.fromBits(prefs.getLong(prefix + "overall", 0.0.toBits())),
        )
    }

    override suspend fun onLocationChanged(location: ReaderLocation) {
        persist(location)
    }

    override suspend fun onSessionClosed(location: ReaderLocation) {
        persist(location)
    }

    private fun persist(location: ReaderLocation) {
        val prefix = prefix(location.bookId)
        check(
            prefs.edit()
                .putString(prefix + "chapter", location.chapterId.value)
                .putInt(prefix + "page", location.pageIndex)
                .putLong(prefix + "offset", location.pageOffsetFraction.toBits())
                .putLong(prefix + "overall", location.overallProgress.toBits())
                .commit(),
        ) { "Unable to persist reader progress" }
    }

    private fun prefix(bookId: ReaderBookId): String = "book." + bookId.value + "."
}
