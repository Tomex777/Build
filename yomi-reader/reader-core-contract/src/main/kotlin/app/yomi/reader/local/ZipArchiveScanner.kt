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
                    val signature = ByteArray(12)
                    var signatureCount = 0
                    if (!entry.isDirectory) {
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            if (signatureCount < signature.size) {
                                val copy = minOf(read, signature.size - signatureCount)
                                buffer.copyInto(signature, signatureCount, 0, copy)
                                signatureCount += copy
                            }
                            entryBytes += read
                            expandedBytes += read
                            if (entryBytes > limits.maxSingleEntryBytes) return ArchiveScanResult.Rejected("entry-too-large")
                            if (expandedBytes > limits.maxExpandedBytes) return ArchiveScanResult.Rejected("archive-too-large")
                        }
                    }

                    descriptors += ArchiveEntryDescriptor(normalizedName, entryBytes)
                    // Mihon's ArchivePageLoader accepts images with a supported
                    // file extension OR a recognizable image signature. CBZ
                    // pages frequently have no extension or a mislabeled name.
                    // Ignore macOS resource-fork files even if named .jpg:
                    // they contain metadata, not a readable comic page.
                    if (!entry.isDirectory && !isArchiveMetadata(normalizedName) &&
                        (isSupportedImage(normalizedName) || isSupportedImageSignature(signature, signatureCount))
                    ) {
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
        if (requested.isBlank() || isTraversal(requested) || isArchiveMetadata(requested)) {
            return ArchivePageRead.Rejected("invalid-page")
        }

        return try {
            var result: ArchivePageRead = ArchivePageRead.Rejected("page-not-found")
            var expandedBeforeTarget = 0L

            ZipInputStream(input.buffered()).use { zip ->
                var entries = 0
                scan@ while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > limits.maxEntries) {
                        result = ArchivePageRead.Rejected("entry-count")
                        break
                    }

                    val normalized = normalize(entry.name)
                    if (isTraversal(normalized)) {
                        result = ArchivePageRead.Rejected("path-traversal")
                        break
                    }
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
                            if (pageBytes > limits.maxSingleEntryBytes) {
                                result = ArchivePageRead.Rejected("entry-too-large")
                                break@scan
                            }
                            output.write(buffer, 0, read)
                        }
                        result = ArchivePageRead.Success(output.toByteArray())
                        break
                    }

                    val skipped = drainEntry(zip, limits.maxSingleEntryBytes)
                    if (skipped == null) {
                        result = ArchivePageRead.Rejected("entry-too-large")
                        break
                    }
                    expandedBeforeTarget += skipped
                    if (expandedBeforeTarget > limits.maxExpandedBytes) {
                        result = ArchivePageRead.Rejected("archive-too-large")
                        break
                    }
                    zip.closeEntry()
                }
            }

            result
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

    private fun isArchiveMetadata(name: String): Boolean {
        val segments = name.split('/')
        return segments.any { it.equals("__MACOSX", ignoreCase = true) } ||
            segments.lastOrNull()?.startsWith("._") == true ||
            segments.lastOrNull()?.equals(".DS_Store", ignoreCase = true) == true
    }

    /** Fast, bounded signature sniffing for JPEG, PNG and WebP (Android 8+). */
    private fun isSupportedImageSignature(bytes: ByteArray, count: Int): Boolean {
        val jpeg = count >= 3 &&
            (bytes[0].toInt() and 0xff) == 0xff &&
            (bytes[1].toInt() and 0xff) == 0xd8 &&
            (bytes[2].toInt() and 0xff) == 0xff
        val png = count >= 8 && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
        val webp = count >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&
            bytes.copyOfRange(8, 12).contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
        return jpeg || png || webp
    }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    )

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
