package app.yomi.reader.local

/**
 * The persisted reader position is chapter-relative. A folder's library
 * pageCount covers ALL chapters, so multiplying its overall progress by that
 * total invented a false "page X of Y" when chapter lengths differ.
 */
fun continueReadingLocation(
    type: LibraryLocationType,
    totalPages: Int?,
    savedChapterPageIndex: Int?,
): String {
    val pageIndex = savedChapterPageIndex?.takeIf { it >= 0 }
        ?: return "Resume where you left off"
    val pageNumber = pageIndex.toLong() + 1L
    return if (type == LibraryLocationType.DOCUMENT && totalPages != null && totalPages > 0) {
        "Resume at page ${pageNumber.coerceAtMost(totalPages.toLong())} of $totalPages"
    } else {
        "Resume at page $pageNumber"
    }
}
