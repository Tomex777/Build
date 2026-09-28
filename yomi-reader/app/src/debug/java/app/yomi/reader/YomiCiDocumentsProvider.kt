package app.yomi.reader

import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

class YomiCiDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_COLUMNS
        return MatrixCursor(columns).apply {
            newRow().apply {
                put(columns, DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
                put(columns, DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
                put(columns, DocumentsContract.Root.COLUMN_TITLE, "Yomi CI Folder")
                put(columns, DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
            }
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        return MatrixCursor(columns).apply {
            addDocumentRow(this, columns, documentId)
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        val children = when (parentDocumentId) {
            ROOT_ID -> listOf(CHAPTER_1, CHAPTER_2)
            CHAPTER_1 -> listOf(C1_PAGE_1, C1_PAGE_2)
            CHAPTER_2 -> listOf(C2_PAGE_1, C2_PAGE_2)
            else -> emptyList()
        }
        return MatrixCursor(columns).apply {
            children.forEach { addDocumentRow(this, columns, it) }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(documentId in IMAGE_IDS) { "Not an image document: $documentId" }
        val file = fixtureFile(documentId)
        if (!file.exists()) writeImage(file, documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return when (parentDocumentId) {
            ROOT_ID -> documentId == CHAPTER_1 || documentId == CHAPTER_2 || documentId in IMAGE_IDS
            CHAPTER_1 -> documentId == C1_PAGE_1 || documentId == C1_PAGE_2
            CHAPTER_2 -> documentId == C2_PAGE_1 || documentId == C2_PAGE_2
            else -> false
        }
    }

    private fun addDocumentRow(cursor: MatrixCursor, columns: Array<out String>, id: String) {
        val directory = id == ROOT_ID || id == CHAPTER_1 || id == CHAPTER_2
        val name = when (id) {
            ROOT_ID -> "Yomi CI Folder"
            CHAPTER_1 -> "Chapter 1"
            CHAPTER_2 -> "Chapter 2"
            C1_PAGE_1, C2_PAGE_1 -> "1.png"
            C1_PAGE_2, C2_PAGE_2 -> "2.png"
            else -> id.substringAfterLast('/')
        }
        cursor.newRow().apply {
            put(columns, DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            put(columns, DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
            put(
                columns,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "image/png",
            )
            put(columns, DocumentsContract.Document.COLUMN_FLAGS, 0)
            if (!directory) put(columns, DocumentsContract.Document.COLUMN_SIZE, fixtureFile(id).length())
        }
    }

    private fun fixtureFile(documentId: String): File {
        val safe = documentId.replace('/', '_')
        return File(requireContext().cacheDir, "yomi-ci-tree-$safe.png")
    }

    private fun writeImage(file: File, documentId: String) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val base = when (documentId) {
            C1_PAGE_1 -> Color.rgb(36, 52, 78)
            C1_PAGE_2 -> Color.rgb(52, 72, 102)
            C2_PAGE_1 -> Color.rgb(72, 94, 126)
            else -> Color.rgb(92, 116, 148)
        }
        Canvas(bitmap).drawColor(base)
        file.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
    }

    private fun MatrixCursor.RowBuilder.put(columns: Array<out String>, column: String, value: Any?) {
        if (column in columns) add(column, value)
    }

    private fun requireContext() = context ?: error("Provider context unavailable")

    companion object {
        const val AUTHORITY_SUFFIX = ".ci.documents"
        const val ROOT_ID = "root"
        private const val CHAPTER_1 = "chapter-1"
        private const val CHAPTER_2 = "chapter-2"
        private const val C1_PAGE_1 = "chapter-1/1.png"
        private const val C1_PAGE_2 = "chapter-1/2.png"
        private const val C2_PAGE_1 = "chapter-2/1.png"
        private const val C2_PAGE_2 = "chapter-2/2.png"
        private val IMAGE_IDS = setOf(C1_PAGE_1, C1_PAGE_2, C2_PAGE_1, C2_PAGE_2)

        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
        )

        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
