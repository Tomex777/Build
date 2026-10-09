package app.yomi.reader.local

import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import app.yomi.reader.core.assembleContinuousPagedWindow
import app.yomi.reader.core.calculateOverallProgress
import app.yomi.reader.core.ReadingMode
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
    fun pagedWindowCrossesChapterBoundaryWithoutSyntheticPage() {
        val chapter1 = listOf("c1p1", "c1p2")
        val chapter2 = listOf("c2p1", "c2p2")

        val ltr = assembleContinuousPagedWindow(
            previous = null,
            current = chapter1,
            next = chapter2,
        )
        assertEquals("c2p1", ltr[ltr.indexOf("c1p2") + 1])
        assertEquals("c1p2", ltr[ltr.indexOf("c2p1") - 1])

        val rtl = assembleContinuousPagedWindow(
            previous = null,
            current = chapter1,
            next = chapter2,
            reversed = true,
        )
        assertEquals("c2p1", rtl[rtl.indexOf("c1p2") - 1])
        assertEquals("c1p2", rtl[rtl.indexOf("c2p1") + 1])
    }

    @Test
    fun pagedLastPageCompletesBook() {
        val progress = calculateOverallProgress(
            mode = ReadingMode.LTR_PAGED,
            chapterIndex = 0,
            chapterCount = 1,
            pageIndex = 2,
            pageCount = 3,
            pageOffsetFraction = 0.0,
        )

        assertEquals(1.0, progress)
    }

    @Test
    fun pagedProgressAccountsForCompletedSelectedPageAcrossChapters() {
        val progress = calculateOverallProgress(
            mode = ReadingMode.RTL_PAGED,
            chapterIndex = 0,
            chapterCount = 2,
            pageIndex = 1,
            pageCount = 2,
            pageOffsetFraction = 0.0,
        )

        assertEquals(0.5, progress)
    }

    @Test
    fun webtoonProgressKeepsWithinPageOffset() {
        val progress = calculateOverallProgress(
            mode = ReadingMode.WEBTOON,
            chapterIndex = 0,
            chapterCount = 1,
            pageIndex = 2,
            pageCount = 3,
            pageOffsetFraction = 0.5,
        )

        assertEquals(5.0 / 6.0, progress)
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
    fun extensionlessImagesAreRecognizedByRealImageMagic() {
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
        val webp = "RIFF".encodeToByteArray() +
            byteArrayOf(0, 0, 0, 0) + "WEBP".encodeToByteArray()
        val archive = zipOf(
            "Chapter 1/001" to jpg,
            "Chapter 1/002.data" to png,
            "Chapter 1/003.bin" to webp,
            "note.txt" to "not an image".encodeToByteArray(),
        )
        val scan = assertIs<ArchiveScanResult.Success>(
            ZipArchiveScanner.scan(ByteArrayInputStream(archive)),
        )
        assertEquals(
            listOf("Chapter 1/001", "Chapter 1/002.data", "Chapter 1/003.bin"),
            scan.catalog.pages.map { it.name },
        )
        val page = assertIs<ArchivePageRead.Success>(
            ZipArchiveScanner.readPage(ByteArrayInputStream(archive), "Chapter 1/002.data"),
        )
        assertContentEquals(png, page.bytes)
        assertEquals(
            scan.catalog.pages.map { it.name },
            ZipArchiveCatalog.scan(ByteArrayInputStream(archive)).map { it.name },
        )
    }

    @Test
    fun macOsResourceForksAreNotIndexedAsUnreadablePages() {
        val archive = zipOf(
            "__MACOSX/._001.jpg" to byteArrayOf(1),
            "Chapter 1/._002.png" to byteArrayOf(1),
            "Chapter 1/001.jpg" to byteArrayOf(1),
            ".DS_Store" to byteArrayOf(1),
        )
        val scan = assertIs<ArchiveScanResult.Success>(
            ZipArchiveScanner.scan(ByteArrayInputStream(archive)),
        )
        assertEquals(listOf("Chapter 1/001.jpg"), scan.catalog.pages.map { it.name })
    }

    @Test
    fun archiveScannerRejectsArchiveWithoutImages() {
        val archive = zipOf(
            "README.txt" to "no pages".encodeToByteArray(),
            "__MACOSX/metadata" to byteArrayOf(1, 2, 3),
        )

        assertEquals(
            ArchiveScanResult.Rejected("no-image-pages"),
            ZipArchiveScanner.scan(ByteArrayInputStream(archive)),
        )
    }

    @Test
    fun archiveScannerRejectsCorruptZip() {
        val corrupt = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x7F)

        assertIs<ArchiveScanResult.Rejected>(
            ZipArchiveScanner.scan(ByteArrayInputStream(corrupt)),
        )
    }

    @Test
    fun fiveHundredPageArchiveKeepsNaturalOrder() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (500 downTo 1).forEach { number ->
                zip.putNextEntry(ZipEntry("page$number.jpg"))
                zip.write(byteArrayOf((number and 0xFF).toByte()))
                zip.closeEntry()
            }
        }

        val scan = assertIs<ArchiveScanResult.Success>(
            ZipArchiveScanner.scan(ByteArrayInputStream(output.toByteArray())),
        )

        assertEquals(500, scan.catalog.pages.size)
        assertEquals("page1.jpg", scan.catalog.pages.first().name)
        assertEquals("page500.jpg", scan.catalog.pages.last().name)
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
