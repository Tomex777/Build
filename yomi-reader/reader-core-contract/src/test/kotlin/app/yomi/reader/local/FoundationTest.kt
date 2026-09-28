package app.yomi.reader.local

import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import kotlin.test.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
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
    fun nestedNamesStayInHumanReadingOrder() {
        val pages = listOf(
            "Chapter 10/2.jpg",
            "Chapter 2/10.jpg",
            "Chapter 2/2.jpg",
            "Chapter 2/001.jpg",
        ).sortedWith(NaturalOrder)

        assertEquals(
            listOf(
                "Chapter 2/001.jpg",
                "Chapter 2/2.jpg",
                "Chapter 2/10.jpg",
                "Chapter 10/2.jpg",
            ),
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

    @Test
    fun realZipPagesAreScannedSortedAndReadable() {
        val archive = zipOf(
            "10.jpg" to byteArrayOf(10),
            "notes.txt" to byteArrayOf(99),
            "2.jpg" to byteArrayOf(2, 2),
            "1.jpg" to byteArrayOf(1),
        )
        val scan = ZipArchiveScanner.scan(ByteArrayInputStream(archive))
        val success = assertIs<ArchiveScanResult.Success>(scan)
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), success.catalog.pages.map { it.name })
        val read = ZipArchiveScanner.readPage(ByteArrayInputStream(archive), "2.jpg")
        val page = assertIs<ArchivePageRead.Success>(read)
        assertContentEquals(byteArrayOf(2, 2), page.bytes)
    }

    @Test
    fun archiveScannerRejectsTraversalBeforeImport() {
        val archive = zipOf("../escape.jpg" to byteArrayOf(1))
        assertEquals(
            ArchiveScanResult.Rejected("path-traversal"),
            ZipArchiveScanner.scan(ByteArrayInputStream(archive)),
        )
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
