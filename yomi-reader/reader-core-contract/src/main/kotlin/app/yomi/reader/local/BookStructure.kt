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

        // A library folder can contain chapter archives at any depth,
        // alongside image chapter folders or root images. The original
        // detection recognized only *root* ZIPs; nested CBZ chapter files were
        // silently excluded and could leave the entire book empty.
        val archiveChapters = candidates
            .filter { it.kind == LocalEntry.Kind.ARCHIVE }
            .map { archive ->
                val path = normalized(archive.relativePath)
                path to DetectedChapter(
                    title = titleWithoutArchive(path),
                    order = 0,
                    members = listOf(archive),
                )
            }
        val imageChapters = candidates
            .filter { it.kind == LocalEntry.Kind.IMAGE }
            .groupBy { normalized(it.relativePath).substringBeforeLast('/', "") }
            .map { (directory, images) ->
                val pages = images.sortedWith(compareBy(NaturalOrder) { normalized(it.relativePath) })
                val chapterTitle = directory.substringAfterLast('/').ifBlank { "Book" }
                val sortPath = if (directory.isEmpty()) pages.first().relativePath else directory
                sortPath to DetectedChapter(
                    title = chapterTitle,
                    order = 0,
                    members = pages,
                )
            }
        return (archiveChapters + imageChapters)
            .sortedWith(compareBy(NaturalOrder) { it.first })
            .mapIndexed { index, (_, chapter) -> chapter.copy(order = index) }
    }

    private fun normalized(path: String) = path.replace('\\', '/').trim('/')

    private fun titleWithoutArchive(path: String): String {
        val name = normalized(path).substringAfterLast('/')
        return name.substringBeforeLast('.', name)
    }
}
