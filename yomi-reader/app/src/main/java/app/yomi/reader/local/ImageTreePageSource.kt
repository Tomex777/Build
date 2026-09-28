package app.yomi.reader.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageId
import app.yomi.reader.core.ReaderPageSource
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Locale

class ImageTreePageSource(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : ReaderPageSource {
    private val pageUris = mutableMapOf<ReaderPageId, Uri>()

    override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<FolderNode>()
        val visited = HashSet<String>()
        val items = mutableListOf<ImageDocument>()
        queue.add(FolderNode(rootId, ""))

        var discoveredDocuments = 0
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (!visited.add(folder.documentId)) continue

            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.documentId)
            resolver.query(
                children,
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
                    discoveredDocuments++
                    require(discoveredDocuments <= MAX_DOCUMENTS) { "Folder contains too many documents" }

                    val id = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val mime = cursor.getString(mimeColumn)
                    val relativePath = if (folder.relativePath.isEmpty()) name else folder.relativePath + "/" + name

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(FolderNode(id, relativePath))
                    } else if (isSupportedImage(name, mime)) {
                        items += ImageDocument(
                            relativePath = relativePath,
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        )
                    }
                }
            }
        }

        pageUris.clear()
        return items
            .sortedWith(compareBy(NaturalOrder) { it.relativePath })
            .mapIndexed { index, item ->
                val id = ReaderPageId(chapter.id.value + "#" + item.uri.toString())
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
        val pageUri = pageUris[page.id] ?: error("Unknown image page " + page.id.value)
        return resolver.openInputStream(pageUri) ?: error("Unable to open image " + page.displayName)
    }

    override fun close() {
        pageUris.clear()
    }

    private fun isSupportedImage(name: String, mime: String?): Boolean {
        if (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp") return true
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in SUPPORTED_EXTENSIONS
    }

    private data class FolderNode(
        val documentId: String,
        val relativePath: String,
    )

    private data class ImageDocument(
        val relativePath: String,
        val uri: Uri,
    )

    private companion object {
        const val MAX_DOCUMENTS = 20_000
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
