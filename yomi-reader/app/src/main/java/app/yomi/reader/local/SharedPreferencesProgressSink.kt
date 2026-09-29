package app.yomi.reader.local

import android.content.Context
import android.util.Log
import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import app.yomi.reader.core.ReaderProgressSink

class SharedPreferencesProgressSink(context: Context) : ReaderProgressSink {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yomi_reader_progress", Context.MODE_PRIVATE)
    private val libraryStore = LocalLibraryStore(appContext)

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
        val page = location.pageIndex + 1
        Log.i(TAG, "reader-progress-write-start book=${location.bookId.value} chapter=${location.chapterId.value} page=$page")
        val committed = prefs.edit()
            .putString(prefix + "chapter", location.chapterId.value)
            .putInt(prefix + "page", location.pageIndex)
            .putLong(prefix + "offset", location.pageOffsetFraction.toBits())
            .putLong(prefix + "overall", location.overallProgress.toBits())
            .commit()
        Log.i(TAG, "reader-progress-preferences-committed book=${location.bookId.value} page=$page result=$committed")
        check(committed) { "Unable to persist reader progress" }

        Log.i(TAG, "reader-progress-library-write-start book=${location.bookId.value} page=$page")
        libraryStore.markProgress(location.bookId, location.overallProgress)
        Log.i(TAG, "reader-progress-write-complete book=${location.bookId.value} page=$page")
    }

    private companion object {
        const val TAG = "YomiProgress"
    }

    private fun prefix(bookId: ReaderBookId): String = "book." + bookId.value + "."
}
