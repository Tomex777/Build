package app.yomi.reader.local

import java.io.InputStream

data class ZipCatalogEntry(
    val name: String,
    // The scanner measures each entry's actual uncompressed size, even if the
    // ZIP only supplies its size in a data descriptor after the file contents.
    val declaredSize: Long?,
)

class UnsafeArchiveException(message: String) : IllegalArgumentException(message)

/**
 * One bounded archive scanner for imports, catalog creation, and reader pages.
 *
 * The previous catalog walked ZIP entries using closeEntry() and trusted
 * ZipEntry.size when present. Streaming ZIPs routinely report -1 until EOF,
 * so a malicious/compressed giant entry could bypass the catalog's limits.
 * ZipArchiveScanner instead counts the uncompressed bytes actually read.
 */
object ZipArchiveCatalog {
    fun scan(
        input: InputStream,
        limits: ArchiveLimits = ArchiveLimits(),
    ): List<ZipCatalogEntry> {
        return when (val result = ZipArchiveScanner.scan(input, limits)) {
            is ArchiveScanResult.Success -> result.catalog.pages.map { page ->
                ZipCatalogEntry(page.name, page.uncompressedBytes)
            }
            is ArchiveScanResult.Rejected -> throw UnsafeArchiveException(result.reason)
        }
    }
}
