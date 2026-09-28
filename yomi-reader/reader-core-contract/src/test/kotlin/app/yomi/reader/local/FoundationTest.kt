package app.yomi.reader.local

import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FoundationTest {
    @Test
    fun naturalPageOrderIsNumericAware() {
        val pages = listOf("10.jpg", "2.jpg", "1.jpg", "001.jpg", "page20.webp", "page3.webp")
            .sortedWith(NaturalOrder)

        assertEquals(
            listOf("1.jpg", "001.jpg", "2.jpg", "10.jpg", "page3.webp", "page20.webp"),
            pages,
        )
    }

    @Test
    fun chapterFoldersBecomeOneOrderedBook() {
        val chapters = BookStructure.detect(
            listOf(
                LocalEntry("Chapter 10/002.jpg", LocalEntry.Kind.IMAGE),
                LocalEntry("Chapter 2/010.jpg", LocalEntry.Kind.IMAGE),
                LocalEntry("Chapter 2/002.jpg", LocalEntry.Kind.IMAGE),
                LocalEntry("Chapter 1/001.jpg", LocalEntry.Kind.IMAGE),
            ),
        )

        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10"), chapters.map { it.title })
        assertEquals(
            listOf("Chapter 2/002.jpg", "Chapter 2/010.jpg"),
            chapters[1].members.map { it.relativePath },
        )
    }

    @Test
    fun chapterArchivesBecomeOrderedChapters() {
        val chapters = BookStructure.detect(
            listOf(
                LocalEntry("Chapter 10.cbz", LocalEntry.Kind.ARCHIVE),
                LocalEntry("Chapter 2.zip", LocalEntry.Kind.ARCHIVE),
                LocalEntry("Chapter 1.cbz", LocalEntry.Kind.ARCHIVE),
            ),
        )

        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10"), chapters.map { it.title })
    }

    @Test
    fun unsafeArchivePathsAreRejected() {
        val result = ArchiveSafety.validate(
            listOf(ArchiveEntryDescriptor("../outside.jpg", 1024)),
        )

        assertEquals(ArchiveValidation.Rejected("path-traversal"), result)
    }

    @Test
    fun duplicateArchiveNamesAreRejectedCaseInsensitively() {
        val result = ArchiveSafety.validate(
            listOf(
                ArchiveEntryDescriptor("001.jpg", 1024),
                ArchiveEntryDescriptor("001.JPG", 1024),
            ),
        )

        assertEquals(ArchiveValidation.Rejected("duplicate-name"), result)
    }

    @Test
    fun boundedArchivePasses() {
        val result = ArchiveSafety.validate(
            listOf(
                ArchiveEntryDescriptor("001.jpg", 1024),
                ArchiveEntryDescriptor("002.webp", 2048),
            ),
        )

        assertIs<ArchiveValidation.Safe>(result)
    }

    @Test
    fun exactRestoreKeepsChapterPageAndFraction() {
        val location = ReaderLocation(
            bookId = ReaderBookId("book-1"),
            chapterId = ReaderChapterId("chapter-7"),
            pageIndex = 18,
            pageOffsetFraction = 0.713,
            overallProgress = 0.44,
        )

        assertEquals("chapter-7", location.chapterId.value)
        assertEquals(18, location.pageIndex)
        assertEquals(0.713, location.pageOffsetFraction)
    }
}
