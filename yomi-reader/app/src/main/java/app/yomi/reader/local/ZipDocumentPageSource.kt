package app.yomi.reader.local

import android.content.ContentResolver
import android.net.Uri
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageId
import app.yomi.reader.core.ReaderPageSource
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ZipDocumentPageSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val limits: ArchiveLimits = ArchiveLimits(),
) : ReaderPageSource {
    private var catalog: List<ZipCatalogEntry>? = null
    private val pageTargets = mutableMapOf<ReaderPageId, String>()

    override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
        val entries = catalog ?: resolver.openInputStream(uri)?.use { input ->
            ZipArchiveCatalog.scan(input, limits)
        }?.also { catalog = it }
            ?: error("Unable to open local archive")

        pageTargets.clear()
        return entries.mapIndexed { index, entry ->
            val id = ReaderPageId(chapter.id.value + "#" + entry.name)
            pageTargets[id] = entry.name
            ReaderPage(
                id = id,
                chapterId = chapter.id,
                index = index,
                displayName = entry.name.substringAfterLast('/'),
            )
        }
    }

    override suspend fun open(page: ReaderPage): InputStream {
        val target = pageTargets[page.id] ?: page.id.value.substringAfter('#', "")
        require(target.isNotEmpty()) { "Unknown page " + page.id.value }

        val source = resolver.openInputStream(uri) ?: error("Unable to open local archive")
        val zip = ZipInputStream(BufferedInputStream(source))

        try {
            var entryCount = 0
            var expandedSkippedBytes = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > limits.maxEntries) throw UnsafeArchiveException("entry-count")

                val normalized = entry.name.replace('\\', '/')
                if (!entry.isDirectory && normalized == target) {
                    val declared = entry.size
                    if (declared > limits.maxSingleEntryBytes) {
                        throw UnsafeArchiveException("entry-too-large")
                    }
                    return BoundedEntryInputStream(zip, limits.maxSingleEntryBytes)
                }
                if (!entry.isDirectory) {
                    // ZipInputStream.closeEntry() decompresses the skipped ZIP
                    // member without exposing a byte count. Read it ourselves
                    // so a corrupted/replaced source cannot force unbounded work.
                    val buffer = ByteArray(32 * 1024)
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        expandedSkippedBytes += read
                        if (entryBytes > limits.maxSingleEntryBytes) {
                            throw UnsafeArchiveException("entry-too-large")
                        }
                        if (expandedSkippedBytes > limits.maxExpandedBytes) {
                            throw UnsafeArchiveException("archive-too-large")
                        }
                    }
                }
                zip.closeEntry()
            }
        } catch (t: Throwable) {
            zip.close()
            throw t
        }

        zip.close()
        error("Archive page no longer exists: " + target)
    }

    override fun close() {
        catalog = null
        pageTargets.clear()
    }

    private class BoundedEntryInputStream(
        input: ZipInputStream,
        private val limit: Long,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) account(read.toLong())
            return read
        }

        private fun account(bytes: Long) {
            count += bytes
            if (count > limit) {
                throw UnsafeArchiveException("entry-too-large")
            }
        }
    }
}
