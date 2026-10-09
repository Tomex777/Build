package app.yomi.reader.local

import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

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
    /**
     * Fast local-reader path for app-private CBZs: the ZIP central directory
     * contains every page offset and expanded size. Unlike streaming a whole
     * 500-page archive just to show page 1, this only decompresses a 12-byte
     * signature from entries whose names have no recognized image suffix.
     *
     * Imports still run the full expanded-byte scanner; individual page reads
     * are also size-bounded. This optimization does not weaken import checks.
     */
    fun scanFile(file: File, limits: ArchiveLimits = ArchiveLimits()): List<ZipCatalogEntry> {
        try {
            return ZipFile(file).use { archive ->
                val descriptors = ArrayList<ArchiveEntryDescriptor>()
                val pages = ArrayList<ZipCatalogEntry>()
                val entries = archive.entries()
                var expandedBytes = 0L

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (descriptors.size >= limits.maxEntries) {
                        throw UnsafeArchiveException("entry-count")
                    }
                    val name = entry.name.replace('\\', '/')
                    val size = entry.size
                    if (size < 0L) throw UnsafeArchiveException("unknown-size")
                    if (size > limits.maxSingleEntryBytes) {
                        throw UnsafeArchiveException("entry-too-large")
                    }
                    if (Long.MAX_VALUE - expandedBytes < size) {
                        throw UnsafeArchiveException("expanded-size-overflow")
                    }
                    expandedBytes += size
                    if (expandedBytes > limits.maxExpandedBytes) {
                        throw UnsafeArchiveException("archive-too-large")
                    }
                    descriptors += ArchiveEntryDescriptor(name, size)

                    if (entry.isDirectory || ZipArchiveScanner.isArchiveMetadata(name)) continue
                    val supported = ZipArchiveScanner.isSupportedImage(name) || run {
                        val magic = ByteArray(12)
                        val count = archive.getInputStream(entry).use { stream ->
                            var count = 0
                            while (count < magic.size) {
                                val n = stream.read(magic, count, magic.size - count)
                                if (n <= 0) break
                                count += n
                            }
                            count
                        }
                        ZipArchiveScanner.isSupportedImageSignature(magic, count)
                    }
                    if (supported) pages += ZipCatalogEntry(name, size)
                }

                when (val check = ArchiveSafety.validate(descriptors, limits)) {
                    ArchiveValidation.Safe -> Unit
                    is ArchiveValidation.Rejected -> throw UnsafeArchiveException(check.reason)
                }
                if (pages.isEmpty()) throw UnsafeArchiveException("no-image-pages")
                pages.sortedWith(compareBy(NaturalOrder) { it.name })
            }
        } catch (e: ZipException) {
            throw UnsafeArchiveException("corrupt-or-unsupported")
        }
    }

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
