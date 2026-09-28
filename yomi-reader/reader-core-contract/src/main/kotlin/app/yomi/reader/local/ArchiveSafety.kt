package app.yomi.reader.local

data class ArchiveLimits(
    val maxEntries: Int = 20_000,
    val maxSingleEntryBytes: Long = 256L * 1024 * 1024,
    val maxExpandedBytes: Long = 4L * 1024 * 1024 * 1024,
)

data class ArchiveEntryDescriptor(
    val name: String,
    val uncompressedSize: Long,
    val encrypted: Boolean = false,
)

sealed interface ArchiveValidation {
    data object Safe : ArchiveValidation
    data class Rejected(val reason: String) : ArchiveValidation
}

object ArchiveSafety {
    fun validate(entries: List<ArchiveEntryDescriptor>, limits: ArchiveLimits = ArchiveLimits()): ArchiveValidation {
        if (entries.size > limits.maxEntries) {
            return ArchiveValidation.Rejected("entry-count")
        }

        var expandedBytes = 0L
        val normalizedNames = HashSet<String>()

        for (entry in entries) {
            if (entry.encrypted) return ArchiveValidation.Rejected("encrypted")
            if (entry.uncompressedSize < 0L) return ArchiveValidation.Rejected("unknown-size")
            if (entry.uncompressedSize > limits.maxSingleEntryBytes) {
                return ArchiveValidation.Rejected("entry-too-large")
            }
            if (isTraversal(entry.name)) return ArchiveValidation.Rejected("path-traversal")

            val key = entry.name.replace('\\', '/').lowercase()
            if (!normalizedNames.add(key)) return ArchiveValidation.Rejected("duplicate-name")

            if (Long.MAX_VALUE - expandedBytes < entry.uncompressedSize) {
                return ArchiveValidation.Rejected("expanded-size-overflow")
            }
            expandedBytes += entry.uncompressedSize
            if (expandedBytes > limits.maxExpandedBytes) {
                return ArchiveValidation.Rejected("archive-too-large")
            }
        }

        return ArchiveValidation.Safe
    }

    private fun isTraversal(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/')) return true
        if (Regex("""^[A-Za-z]:/""").containsMatchIn(normalized)) return true

        var depth = 0
        for (part in normalized.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    depth--
                    if (depth < 0) return true
                }
                else -> depth++
            }
        }
        return false
    }
}
