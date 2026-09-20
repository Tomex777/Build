package com.example.whatsapp.data.night

import android.content.Context
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NightExtractedFile(
    val item: NightLibraryItemEntity,
    val text: String,
    val truncated: Boolean,
)

class NightFileContextService private constructor(
    context: Context,
    private val repository: NightRepository,
) {
    private val appContext = context.applicationContext

    init {
        runCatching { PDFBoxResourceLoader.init(appContext) }
    }

    suspend fun listLibrary(
        query: String? = null,
        limit: Int = 30,
    ): List<NightLibraryItemEntity> = withContext(Dispatchers.IO) {
        val normalized = query.orEmpty().trim().lowercase(Locale.ROOT)
        repository.getLibraryItems()
            .asSequence()
            .filter {
                normalized.isBlank() ||
                    it.name.lowercase(Locale.ROOT).contains(normalized) ||
                    it.mimeType.lowercase(Locale.ROOT).contains(normalized)
            }
            .take(limit.coerceIn(1, 100))
            .toList()
    }

    suspend fun read(
        id: String? = null,
        name: String? = null,
        query: String? = null,
        maxChars: Int = 18_000,
    ): Result<NightExtractedFile> = withContext(Dispatchers.IO) {
        runCatching {
            val items = repository.getLibraryItems()
            val item = when {
                !id.isNullOrBlank() -> items.firstOrNull { it.id == id }
                !name.isNullOrBlank() -> {
                    val needle = name.trim().lowercase(Locale.ROOT)
                    items.firstOrNull { it.name.lowercase(Locale.ROOT) == needle }
                        ?: items.firstOrNull { it.name.lowercase(Locale.ROOT).contains(needle) }
                }
                else -> null
            } ?: error("No matching file was found in Night Library.")

            val file = File(item.localPath)
            require(file.exists() && file.isFile) { "The Library file is no longer available on this device." }

            val raw = extractText(file, item.mimeType)
            require(raw.isNotBlank()) {
                "Night could not extract readable text from " + item.name + ". It may be image-only or an unsupported format."
            }

            val budget = maxChars.coerceIn(2_000, 40_000)
            val relevant = selectRelevant(raw, query.orEmpty(), budget)
            NightExtractedFile(
                item = item,
                text = relevant,
                truncated = relevant.length < raw.length,
            )
        }
    }

    private fun extractText(file: File, mimeType: String): String {
        val mime = mimeType.lowercase(Locale.ROOT)
        val ext = file.extension.lowercase(Locale.ROOT)

        return when {
            mime == "application/pdf" || ext == "pdf" -> extractPdf(file)
            ext == "docx" || mime.contains("wordprocessingml") -> extractDocx(file)
            mime.startsWith("text/") ||
                ext in setOf("txt", "md", "markdown", "json", "csv", "tsv", "xml", "yaml", "yml", "log", "kt", "java", "js", "ts", "py", "html", "htm", "css") ->
                extractPlain(file, ext)
            else -> ""
        }.replace("\u0000", "").trim()
    }

    private fun extractPdf(file: File): String {
        PDFBoxResourceLoader.init(appContext)
        return PDDocument.load(file).use { document ->
            PDFTextStripper().apply {
                sortByPosition = true
            }.getText(document)
        }
    }

    private fun extractDocx(file: File): String =
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: error("This Word document has no readable document body.")
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val paragraphs = xml
                .replace(Regex("</w:p\\s*>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<w:tab[^>]*/>", RegexOption.IGNORE_CASE), "\t")

            Regex("<w:t[^>]*>(.*?)</w:t>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(paragraphs)
                .joinToString(" ") { decodeXml(it.groupValues[1]) }
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank {
                    decodeXml(paragraphs.replace(Regex("<[^>]+>"), " "))
                }
        }

    private fun extractPlain(file: File, extension: String): String {
        require(file.length() <= 8L * 1024L * 1024L) {
            "This text document is too large to read in one pass."
        }
        val raw = file.readText()
        return if (extension in setOf("html", "htm", "xml")) {
            Html.fromHtml(
                raw
                    .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " "),
                Html.FROM_HTML_MODE_LEGACY,
            ).toString()
        } else {
            raw
        }
    }

    private fun decodeXml(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun selectRelevant(
        text: String,
        query: String,
        maxChars: Int,
    ): String {
        val compact = text
            .replace("\r\n", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        if (compact.length <= maxChars) return compact

        val terms = query
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .distinct()
            .take(16)

        if (terms.isEmpty()) return compact.take(maxChars)

        val chunks = compact.chunked(2_400)
        val scored = chunks.mapIndexed { index, chunk ->
            val lower = chunk.lowercase(Locale.ROOT)
            val score = terms.sumOf { term ->
                Regex(Regex.escape(term)).findAll(lower).count()
            }
            Triple(index, score, chunk)
        }

        val selected = scored
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.second }.thenBy { it.first })
            .take(8)
            .sortedBy { it.first }

        val joined = selected.joinToString("\n\n[…]\n\n") { it.third }
        return joined.take(maxChars)
    }

    companion object {
        @Volatile private var instance: NightFileContextService? = null

        fun get(context: Context): NightFileContextService =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NightFileContextService(
                    context = app,
                    repository = NightRepository.get(app),
                ).also { instance = it }
            }
    }
}
