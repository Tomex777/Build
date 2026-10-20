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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZipDocumentPageSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val limits: ArchiveLimits = ArchiveLimits(),
    // When a SAF folder contains zipped chapters, make one bounded local
    // indexed copy for this reader session rather than streaming all previous
    // ZIP entries for every single page turn.
    private val scratchDir: File? = null,
) : ReaderPageSource {
    private var catalog: List<ZipCatalogEntry>? = null
    private val pageTargets = mutableMapOf<ReaderPageId, String>()
    private val copyMutex = Mutex()
    @Volatile private var cachedChapterFile: File? = null
    @Volatile private var closed = false

    private suspend fun indexedFile(): File? {
        if (uri.scheme == "file") {
            return uri.path?.let(::File)?.takeIf { it.isFile }
        }
        val directory = scratchDir ?: return null
        cachedChapterFile?.takeIf { it.isFile }?.let { return it }
        return copyMutex.withLock {
            cachedChapterFile?.takeIf { it.isFile }?.let { return@withLock it }
            check(!closed) { "Reader archive source has closed" }
            if (!directory.isDirectory && !directory.mkdirs()) return@withLock null
            // Preserve storage headroom. Very large archives fall back to
            // bounded streaming instead of duplicating gigabytes on disk.
            val budget = minOf(MAX_INDEX_CACHE_BYTES, directory.usableSpace / 3)
            if (budget < MIN_FREE_CACHE_BUDGET) return@withLock null
            val temporary = File.createTempFile("yomi-chapter-", ".cbz", directory)
            try {
                val source = resolver.openInputStream(uri) ?: error("Unable to open chapter archive")
                var copied = 0L
                var tooLarge = false
                source.use { input ->
                    temporary.outputStream().buffered().use { out ->
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            if (!currentCoroutineContext().isActive || closed) {
                                throw CancellationException("Chapter cache copy cancelled")
                            }
                            val length = input.read(bytes)
                            if (length < 0) break
                            if (length == 0) continue
                            copied += length
                            if (copied > budget) {
                                tooLarge = true
                                break
                            }
                            out.write(bytes, 0, length)
                        }
                    }
                }
                if (tooLarge) {
                    Log.i("YomiReader", "saf-chapter-streaming-fallback compressedBytes>$budget")
                    return@withLock null
                }
                check(!closed) { "Reader archive source has closed" }
                cachedChapterFile = temporary
                Log.i("YomiReader", "indexed-saf-chapter-ready bytes=$copied")
                temporary
            } finally {
                if (cachedChapterFile != temporary) temporary.delete()
            }
        }
    }
    private companion object {
        const val MAX_INDEX_CACHE_BYTES = 512L * 1024 * 1024
        const val MIN_FREE_CACHE_BUDGET = 16L * 1024 * 1024
    }

    override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
        val entries = catalog ?: run {
            val localFile = indexedFile()
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
        indexedFile()?.let { local ->
            if (local.isFile) return openLocalZipEntry(local, target)
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
        closed = true
        catalog = null
        pageTargets.clear()
        cachedChapterFile?.let { file ->
            cachedChapterFile = null
            runCatching { file.delete() }
        }
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
