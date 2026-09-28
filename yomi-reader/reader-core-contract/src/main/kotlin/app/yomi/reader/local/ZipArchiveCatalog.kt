package app.yomi.reader.local

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class ZipCatalogEntry(
    val name: String,
    val declaredSize: Long?,
)

class UnsafeArchiveException(message: String) : IllegalArgumentException(message)

object ZipArchiveCatalog {
    private val supportedImageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun scan(
        input: InputStream,
        limits: ArchiveLimits = ArchiveLimits(),
    ): List<ZipCatalogEntry> {
        val images = mutableListOf<ZipCatalogEntry>()
        val names = HashSet<String>()
        var entryCount = 0
        var declaredExpandedBytes = 0L

        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > limits.maxEntries) {
                        throw UnsafeArchiveException("entry-count")
                    }

                    val normalized = entry.name.replace('\\', '/')
                    validatePath(normalized)

                    val duplicateKey = normalized.lowercase(Locale.ROOT)
                    if (!names.add(duplicateKey)) {
                        throw UnsafeArchiveException("duplicate-name")
                    }

                    val declaredSize = entry.size.takeIf { it >= 0L }
                    if (declaredSize != null) {
                        if (declaredSize > limits.maxSingleEntryBytes) {
                            throw UnsafeArchiveException("entry-too-large")
                        }
                        if (Long.MAX_VALUE - declaredExpandedBytes < declaredSize) {
                            throw UnsafeArchiveException("expanded-size-overflow")
                        }
                        declaredExpandedBytes += declaredSize
                        if (declaredExpandedBytes > limits.maxExpandedBytes) {
                            throw UnsafeArchiveException("archive-too-large")
                        }
                    }

                    if (!entry.isDirectory && isSupportedImage(normalized)) {
                        images += ZipCatalogEntry(
                            name = normalized,
                            declaredSize = declaredSize,
                        )
                    }

                    zip.closeEntry()
                }
            }
        } catch (e: UnsafeArchiveException) {
            throw e
        } catch (e: ZipException) {
            throw UnsafeArchiveException("corrupt-zip: " + (e.message ?: "invalid archive"))
        }

        return images.sortedWith(compareBy(NaturalOrder) { it.name })
    }

    private fun isSupportedImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in supportedImageExtensions
    }

    private fun validatePath(name: String) {
        if (name.startsWith('/')) {
            throw UnsafeArchiveException("path-traversal")
        }
        if (Regex("""^[A-Za-z]:/""").containsMatchIn(name)) {
            throw UnsafeArchiveException("path-traversal")
        }

        var depth = 0
        for (part in name.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    depth--
                    if (depth < 0) throw UnsafeArchiveException("path-traversal")
                }
                else -> depth++
            }
        }
    }
}
