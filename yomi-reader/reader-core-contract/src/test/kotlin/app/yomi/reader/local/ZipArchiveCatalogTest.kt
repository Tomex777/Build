package app.yomi.reader.local

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipArchiveCatalogTest {
    @Test
    fun indexesSupportedImagesInNaturalOrder() {
        val bytes = zipOf(
            "10.jpg" to byteArrayOf(10),
            "notes.txt" to byteArrayOf(1),
            "2.webp" to byteArrayOf(2),
            "1.png" to byteArrayOf(1),
        )

        val names = ZipArchiveCatalog.scan(ByteArrayInputStream(bytes)).map { it.name }

        assertEquals(listOf("1.png", "2.webp", "10.jpg"), names)
    }

    @Test
    fun rejectsTraversal() {
        val bytes = zipOf("../outside.jpg" to byteArrayOf(1))

        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(ByteArrayInputStream(bytes))
        }

        assertEquals("path-traversal", error.message)
    }

    @Test
    fun rejectsAbsolutePath() {
        val bytes = zipOf("/outside.jpg" to byteArrayOf(1))

        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(ByteArrayInputStream(bytes))
        }

        assertEquals("path-traversal", error.message)
    }

    @Test
    fun entryCountIsBounded() {
        val bytes = zipOf(
            "1.jpg" to byteArrayOf(1),
            "2.jpg" to byteArrayOf(2),
        )

        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(
                ByteArrayInputStream(bytes),
                ArchiveLimits(maxEntries = 1),
            )
        }

        assertEquals("entry-count", error.message)
    }

    @Test
    fun measuresDataDescriptorSizesRatherThanTrustingUnknownZipMetadata() {
        // The default ZipOutputStream writes its entry sizes AFTER the data,
        // meaning ZipInputStream.nextEntry.size returns -1 while indexing.
        val bytes = zipOf("001.jpg" to ByteArray(128) { 7 })
        val result = ZipArchiveCatalog.scan(ByteArrayInputStream(bytes))
        assertEquals(128L, result.single().declaredSize)
    }

    @Test
    fun rejectsInflatedEntryEvenWhenDeclaredSizeIsInitiallyUnknown() {
        val bytes = zipOf("001.jpg" to ByteArray(128) { 7 })
        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(
                ByteArrayInputStream(bytes),
                ArchiveLimits(maxSingleEntryBytes = 64L),
            )
        }
        assertEquals("entry-too-large", error.message)
    }

    @Test
    fun rejectsExceededExpandedArchiveLimit() {
        val bytes = zipOf(
            "001.jpg" to ByteArray(40) { 1 },
            "002.png" to ByteArray(40) { 2 },
        )
        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(
                ByteArrayInputStream(bytes),
                ArchiveLimits(maxExpandedBytes = 70L),
            )
        }
        assertEquals("archive-too-large", error.message)
    }

    @Test
    fun rejectsDuplicateCaseInsensitiveImageNames() {
        val bytes = zipOf(
            "Chapter/001.jpg" to byteArrayOf(1),
            "chapter/001.JPG" to byteArrayOf(2),
        )
        val error = assertFailsWith<UnsafeArchiveException> {
            ZipArchiveCatalog.scan(ByteArrayInputStream(bytes))
        }
        assertEquals("duplicate-name", error.message)
    }

    @Test
    fun indexedLocalFilePreservesNumericOrderAndRealCentralDirectorySizes() {
        val bytes = zipOf(
            "10.jpg" to ByteArray(20) { 10 },
            "notes.txt" to byteArrayOf(1),
            "2.png" to ByteArray(48) { 2 },
            "1.webp" to ByteArray(64) { 1 },
        )
        withTempZip(bytes) { file ->
            val pages = ZipArchiveCatalog.scanFile(file)
            assertEquals(listOf("1.webp", "2.png", "10.jpg"), pages.map { it.name })
            assertEquals(listOf(64L, 48L, 20L), pages.map { it.declaredSize })
        }
    }

    @Test
    fun indexedLocalFileFindsExtensionlessPageThroughPngMagic() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
        val bytes = zipOf(
            "001" to pngHeader,
            "__MACOSX/._002.jpg" to byteArrayOf(1),
            "002.jpeg" to byteArrayOf(2),
        )
        withTempZip(bytes) { file ->
            assertEquals(
                listOf("001", "002.jpeg"),
                ZipArchiveCatalog.scanFile(file).map { it.name },
            )
        }
    }

    @Test
    fun indexedLocalFileStillRejectsInvalidArchivePathsAndDuplicates() {
        withTempZip(zipOf("../outside.jpg" to byteArrayOf(1))) { file ->
            assertEquals(
                "path-traversal",
                assertFailsWith<UnsafeArchiveException> {
                    ZipArchiveCatalog.scanFile(file)
                }.message,
            )
        }
        withTempZip(zipOf("1.jpg" to byteArrayOf(1), "1.JPG" to byteArrayOf(2))) { file ->
            assertEquals(
                "duplicate-name",
                assertFailsWith<UnsafeArchiveException> {
                    ZipArchiveCatalog.scanFile(file)
                }.message,
            )
        }
    }

    @Test
    fun indexedLocalFileEnforcesDeclaredSizeLimitsBeforeDecoding() {
        val bytes = zipOf("001.png" to ByteArray(1024) { 4 })
        withTempZip(bytes) { file ->
            assertEquals(
                "entry-too-large",
                assertFailsWith<UnsafeArchiveException> {
                    ZipArchiveCatalog.scanFile(file, ArchiveLimits(maxSingleEntryBytes = 100L))
                }.message,
            )
        }
    }

    private fun <T> withTempZip(bytes: ByteArray, test: (java.io.File) -> T): T {
        val file = java.io.File.createTempFile("yomi-reader-", ".cbz")
        return try {
            file.writeBytes(bytes)
            test(file)
        } finally {
            file.delete()
        }
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
