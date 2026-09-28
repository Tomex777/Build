package app.yomi.reader.local

data class LocalEntry(
    val relativePath: String,
    val kind: Kind,
) {
    enum class Kind {
        IMAGE,
        ARCHIVE,
        OTHER,
    }
}

data class DetectedChapter(
    val title: String,
    val order: Int,
    val members: List<LocalEntry>,
)

object BookStructure {
    fun detect(entries: List<LocalEntry>): List<DetectedChapter> {
        val candidates = entries.filter { it.kind == LocalEntry.Kind.IMAGE || it.kind == LocalEntry.Kind.ARCHIVE }
        if (candidates.isEmpty()) return emptyList()

        val archives = candidates.filter { it.kind == LocalEntry.Kind.ARCHIVE && '/' !in normalized(it.relativePath) }
        val nestedImages = candidates.filter { it.kind == LocalEntry.Kind.IMAGE && '/' in normalized(it.relativePath) }

        if (archives.isNotEmpty() && nestedImages.isEmpty()) {
            return archives
                .sortedWith(compareBy(NaturalOrder) { normalized(it.relativePath) })
                .mapIndexed { index, entry ->
                    DetectedChapter(titleWithoutArchive(entry.relativePath), index, listOf(entry))
                }
        }

        if (nestedImages.isNotEmpty()) {
            return nestedImages
                .groupBy { normalized(it.relativePath).substringBeforeLast('/') }
                .entries
                .sortedWith(compareBy(NaturalOrder) { it.key })
                .mapIndexed { index, (directory, pages) ->
                    DetectedChapter(
                        title = directory.substringAfterLast('/'),
                        order = index,
                        members = pages.sortedWith(compareBy(NaturalOrder) { normalized(it.relativePath) }),
                    )
                }
        }

        val rootImages = candidates.filter { it.kind == LocalEntry.Kind.IMAGE }
            .sortedWith(compareBy(NaturalOrder) { normalized(it.relativePath) })

        return if (rootImages.isEmpty()) {
            emptyList()
        } else {
            listOf(DetectedChapter("Book", 0, rootImages))
        }
    }

    private fun normalized(path: String) = path.replace('\\', '/').trim('/')

    private fun titleWithoutArchive(path: String): String {
        val name = normalized(path).substringAfterLast('/')
        return name.substringBeforeLast('.', name)
    }
}
