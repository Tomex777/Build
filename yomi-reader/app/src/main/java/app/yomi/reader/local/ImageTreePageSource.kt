package app.yomi.reader.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageId
import app.yomi.reader.core.ReaderPageSource
import java.io.InputStream
import java.util.Locale

class ImageTreePageSource(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : ReaderPageSource {
    private val pageUris = mutableMapOf<ReaderPageId, Uri>()

    override suspend fun pages(chapter: ReaderChapter): List<ReaderPage> {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        val items = mutableListOf<ImageDocument>()

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
                val id = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn)
                if (!isSupportedImage(name, mime)) continue

                items += ImageDocument(
                    name = name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                )
            }
        }

        pageUris.clear()
        return items
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .mapIndexed { index, item ->
                val id = ReaderPageId(chapter.id.value + "#" + item.uri.toString())
                pageUris[id] = item.uri
                ReaderPage(
                    id = id,
                    chapterId = chapter.id,
                    index = index,
                    displayName = item.name,
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
        return extension in setOf("jpg", "jpeg", "png", "webp")
    }

    private data class ImageDocument(
        val name: String,
        val uri: Uri,
    )
}
