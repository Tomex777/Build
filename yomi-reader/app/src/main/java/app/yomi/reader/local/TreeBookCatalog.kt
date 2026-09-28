package app.yomi.reader.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageId
import app.yomi.reader.core.ReaderPageSource
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Locale

data class LocalChapterBinding(
    val chapter: ReaderChapter,
    val source: ReaderPageSource,
)

class TreeBookCatalog(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) {
    suspend fun chapters(bookId: ReaderBookId, bookTitle: String): List<LocalChapterBinding> {
        val documents = discover()
        val byPath = documents.associateBy { it.relativePath }
        val structure = BookStructure.detect(
            documents.map { document ->
                LocalEntry(
                    relativePath = document.relativePath,
                    kind = when {
                        document.isImage -> LocalEntry.Kind.IMAGE
                        document.isArchive -> LocalEntry.Kind.ARCHIVE
                        else -> LocalEntry.Kind.OTHER
                    },
                )
            },
        )

        return structure.map { detected ->
            val chapterId = ReaderChapterId(bookId.value + "#chapter:" + stableAnchor(detected))
            val chapter = ReaderChapter(
                id = chapterId,
                bookId = bookId,
                title = detected.title.ifBlank { bookTitle },
                order = detected.order,
            )
            val members = detected.members.mapNotNull { byPath[it.relativePath] }

            val source = if (members.size == 1 && members.single().isArchive) {
                ZipDocumentPageSource(resolver, members.single().uri)
            } else {
                FixedTreeImagePageSource(resolver, members.filter { it.isImage })
            }
            LocalChapterBinding(chapter, source)
        }
    }

    private fun discover(): List<TreeDocument> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<FolderNode>()
        val visited = HashSet<String>()
        val documents = mutableListOf<TreeDocument>()
        queue.add(FolderNode(rootId, ""))

        var discovered = 0
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (!visited.add(folder.documentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.documentId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    discovered++
                    require(discovered <= MAX_DOCUMENTS) { "Folder contains too many documents" }

                    val id = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val mime = cursor.getString(mimeColumn)
                    val relativePath = if (folder.relativePath.isBlank()) name else folder.relativePath + "/" + name

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(FolderNode(id, relativePath))
                    } else {
                        documents += TreeDocument(
                            relativePath = relativePath,
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            isImage = isSupportedImage(name, mime),
                            isArchive = isSupportedArchive(name, mime),
                        )
                    }
                }
            }
        }
        return documents
    }

    private fun stableAnchor(chapter: DetectedChapter): String {
        val first = chapter.members.firstOrNull()?.relativePath?.replace('\\', '/') ?: chapter.title
        return if (chapter.members.size == 1 && chapter.members.single().kind == LocalEntry.Kind.ARCHIVE) {
            first
        } else {
            first.substringBeforeLast('/', "root")
        }
    }

    private fun isSupportedImage(name: String, mime: String?): Boolean {
        if (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp") return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
    }

    private fun isSupportedArchive(name: String, mime: String?): Boolean {
        if (mime == "application/zip" || mime == "application/x-cbz" || mime == "application/vnd.comicbook+zip") return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARCHIVE_EXTENSIONS
    }

    private data class FolderNode(
        val documentId: String,
        val relativePath: String,
    )

    private data class TreeDocument(
        val relativePath: String,
        val uri: Uri,
        val isImage: Boolean,
        val isArchive: Boolean,
    )

    private class FixedTreeImagePageSource(
        private val resolver: ContentResolver,
        private val items: List<TreeDocument>,
    ) : ReaderPageSource {
        private val pageUris = mutableMapOf<ReaderPageId, Uri>()

        override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
            pageUris.clear()
            return items
                .sortedWith(compareBy(NaturalOrder) { it.relativePath })
                .mapIndexed { index, item ->
                    val id = ReaderPageId(chapter.id.value + "#" + item.relativePath)
                    pageUris[id] = item.uri
                    ReaderPage(
                        id = id,
                        chapterId = chapter.id,
                        index = index,
                        displayName = item.relativePath.substringAfterLast('/'),
                    )
                }
        }

        override suspend fun open(page: ReaderPage): InputStream {
            val uri = pageUris[page.id] ?: error("Unknown image page " + page.id.value)
            return resolver.openInputStream(uri) ?: error("Unable to open image " + page.displayName)
        }

        override fun close() {
            pageUris.clear()
        }
    }

    private companion object {
        const val MAX_DOCUMENTS = 20_000
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val ARCHIVE_EXTENSIONS = setOf("cbz", "zip")
    }
}
