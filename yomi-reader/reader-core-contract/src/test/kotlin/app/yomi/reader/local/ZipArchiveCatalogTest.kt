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
