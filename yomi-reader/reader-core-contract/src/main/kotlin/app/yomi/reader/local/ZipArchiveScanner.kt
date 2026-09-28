package app.yomi.reader.local

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class ArchivePageEntry(val name: String, val uncompressedBytes: Long)
data class ArchiveCatalog(val pages: List<ArchivePageEntry>, val entryCount: Int, val expandedBytes: Long)

sealed interface ArchiveScanResult {
    data class Success(val catalog: ArchiveCatalog) : ArchiveScanResult
    data class Rejected(val reason: String) : ArchiveScanResult
}

sealed interface ArchivePageRead {
    data class Success(val bytes: ByteArray) : ArchivePageRead
    data class Rejected(val reason: String) : ArchivePageRead
}

object ZipArchiveScanner {
    private const val BUFFER_SIZE = 32 * 1024

    fun scan(input: InputStream, limits: ArchiveLimits = ArchiveLimits()): ArchiveScanResult {
        val descriptors = mutableListOf<ArchiveEntryDescriptor>()
        val imagePages = mutableListOf<ArchivePageEntry>()
        var expandedBytes = 0L
        var entryCount = 0

        return try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > limits.maxEntries) return ArchiveScanResult.Rejected("entry-count")

                    val normalizedName = normalize(entry.name)
                    if (isTraversal(normalizedName)) return ArchiveScanResult.Rejected("path-traversal")

                    var entryBytes = 0L
                    if (!entry.isDirectory) {
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            expandedBytes += read
                            if (entryBytes > limits.maxSingleEntryBytes) return ArchiveScanResult.Rejected("entry-too-large")
                            if (expandedBytes > limits.maxExpandedBytes) return ArchiveScanResult.Rejected("archive-too-large")
                        }
                    }

                    descriptors += ArchiveEntryDescriptor(normalizedName, entryBytes)
                    if (!entry.isDirectory && isSupportedImage(normalizedName)) {
                        imagePages += ArchivePageEntry(normalizedName, entryBytes)
                    }
                    zip.closeEntry()
                }
            }

            when (val validation = ArchiveSafety.validate(descriptors, limits)) {
                ArchiveValidation.Safe -> if (imagePages.isEmpty()) {
                    ArchiveScanResult.Rejected("no-image-pages")
                } else {
                    ArchiveScanResult.Success(
                        ArchiveCatalog(
                            pages = imagePages.sortedWith(compareBy(NaturalOrder) { it.name }),
                            entryCount = entryCount,
                            expandedBytes = expandedBytes,
                        ),
                    )
                }
                is ArchiveValidation.Rejected -> ArchiveScanResult.Rejected(validation.reason)
            }
        } catch (_: ZipException) {
            ArchiveScanResult.Rejected("corrupt-or-unsupported")
        } catch (_: IOException) {
            ArchiveScanResult.Rejected("io-error")
        }
    }

    fun readPage(input: InputStream, pageName: String, limits: ArchiveLimits = ArchiveLimits()): ArchivePageRead {
        val requested = normalize(pageName)
        if (!isSupportedImage(requested) || isTraversal(requested)) return ArchivePageRead.Rejected("invalid-page")
        var expandedBeforeTarget = 0L

        return try {
            ZipInputStream(input.buffered()).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: return ArchivePageRead.Rejected("page-not-found")
                    entries++
                    if (entries > limits.maxEntries) return ArchivePageRead.Rejected("entry-count")
                    val normalized = normalize(entry.name)
                    if (isTraversal(normalized)) return ArchivePageRead.Rejected("path-traversal")
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    if (normalized == requested) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        var pageBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            pageBytes += read
                            if (pageBytes > limits.maxSingleEntryBytes) return ArchivePageRead.Rejected("entry-too-large")
                            output.write(buffer, 0, read)
                        }
                        return ArchivePageRead.Success(output.toByteArray())
                    }

                    val skipped = drainEntry(zip, limits.maxSingleEntryBytes)
                        ?: return ArchivePageRead.Rejected("entry-too-large")
                    expandedBeforeTarget += skipped
                    if (expandedBeforeTarget > limits.maxExpandedBytes) return ArchivePageRead.Rejected("archive-too-large")
                    zip.closeEntry()
                }
            }
        } catch (_: ZipException) {
            ArchivePageRead.Rejected("corrupt-or-unsupported")
        } catch (_: IOException) {
            ArchivePageRead.Rejected("io-error")
        }
    }

    private fun drainEntry(zip: ZipInputStream, maxBytes: Long): Long? {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) return total
            total += read
            if (total > maxBytes) return null
        }
    }

    private fun normalize(name: String): String = name.replace('\\', '/')
    private fun isSupportedImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }
    private fun isTraversal(name: String): Boolean {
        if (name.startsWith('/')) return true
        if (Regex("""^[A-Za-z]:/""").containsMatchIn(name)) return true
        var depth = 0
        for (part in name.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> { depth--; if (depth < 0) return true }
                else -> depth++
            }
        }
        return false
    }
}
