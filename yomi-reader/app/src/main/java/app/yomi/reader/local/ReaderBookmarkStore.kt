package app.yomi.reader.local

import android.content.Context
import android.net.Uri
import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation

data class ReaderBookmark(
    val chapterId: ReaderChapterId,
    val pageIndex: Int,
    val pageOffsetFraction: Double,
    val overallProgress: Double,
    val createdAtEpochMillis: Long,
)

class ReaderBookmarkStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(bookId: ReaderBookId): List<ReaderBookmark> {
        return (prefs.getStringSet(key(bookId), emptySet()) ?: emptySet())
            .mapNotNull(::decode)
            .sortedBy { it.createdAtEpochMillis }
    }

    fun contains(location: ReaderLocation): Boolean {
        return list(location.bookId).any {
            it.chapterId == location.chapterId && it.pageIndex == location.pageIndex
        }
    }

    fun toggle(location: ReaderLocation): Boolean {
        val key = key(location.bookId)
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        val matching = current.filter { raw ->
            decode(raw)?.let {
                it.chapterId == location.chapterId && it.pageIndex == location.pageIndex
            } == true
        }
        val added = matching.isEmpty()
        if (added) {
            current += encode(
                ReaderBookmark(
                    chapterId = location.chapterId,
                    pageIndex = location.pageIndex,
                    pageOffsetFraction = location.pageOffsetFraction,
                    overallProgress = location.overallProgress,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            current.removeAll(matching.toSet())
        }
        check(prefs.edit().putStringSet(key, current).commit()) {
            "Unable to persist Yomi bookmark"
        }
        return added
    }

    fun clear(bookId: ReaderBookId) {
        check(prefs.edit().remove(key(bookId)).commit()) {
            "Unable to clear Yomi bookmarks"
        }
    }

    private fun encode(bookmark: ReaderBookmark): String {
        return listOf(
            Uri.encode(bookmark.chapterId.value),
            bookmark.pageIndex.toString(),
            bookmark.pageOffsetFraction.toBits().toString(),
            bookmark.overallProgress.toBits().toString(),
            bookmark.createdAtEpochMillis.toString(),
        ).joinToString(SEPARATOR)
    }

    private fun decode(raw: String): ReaderBookmark? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 5) return null
        return runCatching {
            ReaderBookmark(
                chapterId = ReaderChapterId(Uri.decode(parts[0])),
                pageIndex = parts[1].toInt(),
                pageOffsetFraction = Double.fromBits(parts[2].toLong()).coerceIn(0.0, 1.0),
                overallProgress = Double.fromBits(parts[3].toLong()).coerceIn(0.0, 1.0),
                createdAtEpochMillis = parts[4].toLong(),
            )
        }.getOrNull()
    }

    private fun key(bookId: ReaderBookId): String = "book." + bookId.value

    private companion object {
        const val PREFS = "yomi_reader_bookmarks"
        const val SEPARATOR = "\t"
    }
}
