package app.yomi.reader.local

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageId
import app.yomi.reader.core.ReaderPageSource
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile

class ZipDocumentPageSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val limits: ArchiveLimits = ArchiveLimits(),
) : ReaderPageSource {
    private var catalog: List<ZipCatalogEntry>? = null
    private val pageTargets = mutableMapOf<ReaderPageId, String>()

    override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
        val entries = catalog ?: run {
            val localFile = if (uri.scheme == "file") uri.path?.let(::File)?.takeIf(File::isFile) else null
            val scanned = if (localFile != null) {
                val result = ZipArchiveCatalog.scanFile(localFile, limits)
                Log.i("YomiReader", "indexed-cbz-catalog-ready pages=${result.size}")
                result
            } else {
                resolver.openInputStream(uri)?.use { ZipArchiveCatalog.scan(it, limits) }
                    ?: error("Unable to open local archive")
            }
            catalog = scanned
            scanned
        }

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

        // Mihon uses random-access archives. Reading page 400 by walking the
        // first 399 entries decompresses hundreds of images repeatedly, which
        // made large offline CBZs appear blank or take ages to turn pages.
        // Privately retained file:// CBZs support direct ZipFile entry access.
        if (uri.scheme == "file") {
            val local = uri.path?.let(::File)
            if (local != null && local.isFile) {
                return openLocalZipEntry(local, target)
            }
        }

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

    /**
     * Random-access read for locally retained CBZs. The returned InputStream
     * owns both its entry stream and its ZipFile handle; decoding or recycling
     * the page releases the file descriptor deterministically.
     */
    private fun openLocalZipEntry(file: File, target: String): InputStream {
        val archive = ZipFile(file)
        try {
            val entry = archive.getEntry(target)
                ?: archive.entries().asSequence().firstOrNull {
                    it.name.replace('\\', '/') == target
                }
                ?: throw IllegalStateException("Archive page no longer exists: $target")
            require(!entry.isDirectory) { "Archive page is a directory: $target" }
            if (entry.size > limits.maxSingleEntryBytes) {
                throw UnsafeArchiveException("entry-too-large")
            }
            val stream = object : FilterInputStream(archive.getInputStream(entry)) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        archive.close()
                    }
                }
            }
            Log.i("YomiReader", "indexed-cbz-page-open name=${target.substringAfterLast('/')}")
            return BoundedEntryInputStream(stream, limits.maxSingleEntryBytes)
        } catch (error: Throwable) {
            archive.close()
            throw error
        }
    }

    override fun close() {
        catalog = null
        pageTargets.clear()
    }

    private class BoundedEntryInputStream(
        input: InputStream,
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
