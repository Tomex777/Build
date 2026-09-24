package app.nami.data.local

/**
 * Produces the relative folder layout below Nami's user-selected download root.
 *
 * Example:
 * AnimePahe/Bleach/Season 01/Episode 001.mkv
 */
object DownloadDirectoryLayout {
    fun relativeDirectory(
        extensionName: String,
        title: String,
        season: String? = null,
    ): String {
        return buildList {
            add(sanitize(extensionName))
            add(sanitize(title))
            season?.takeIf { it.isNotBlank() }?.let { add(sanitize(it)) }
        }.joinToString("/")
    }

    fun sanitize(value: String): String {
        val cleaned = value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        return cleaned.ifBlank { "Unknown" }
    }
}
