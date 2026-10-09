package app.yomi.reader

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import app.yomi.reader.core.ReadingMode
import app.yomi.reader.local.LibraryLocationType
import app.yomi.reader.local.LocalLibraryStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YomiCiFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ARCHIVE
        val requestedMode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?: ReadingMode.LTR_PAGED

        val target = if (kind == KIND_FOLDER) {
            val uri = DocumentsContract.buildTreeDocumentUri(
                packageName + YomiCiDocumentsProvider.AUTHORITY_SUFFIX,
                YomiCiDocumentsProvider.ROOT_ID,
            )
            Triple(uri, "folder", "Yomi CI Folder")
        } else if (kind == KIND_EXTENSIONLESS) {
            // Test actual Android decoding of a PNG whose ZIP member has no
            // suffix. Mihon detects images from their content signatures.
            val archive = File(cacheDir, "yomi-ci-extensionless.cbz")
            if (!archive.exists() || intent.getBooleanExtra(EXTRA_RESET, false)) {
                archive.parentFile?.mkdirs()
                ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                    writePage(zip, "Chapter 1/1", Color.rgb(42, 56, 86))
                    writePage(zip, "Chapter 1/2", Color.rgb(68, 86, 122))
                    writePage(zip, "Chapter 1/3", Color.rgb(94, 116, 154))
                }
            }
            Triple(Uri.fromFile(archive), "archive", "Yomi CI Extensionless")
        } else if (kind == KIND_IMPORTED) {
            val stored = File(filesDir, "imported-books").walkTopDown()
                .firstOrNull { it.isFile && it.extension.equals("cbz", true) }
                ?: error("No privately retained CBZ found; ACTION_VIEW import regression failed")
            Triple(Uri.fromFile(stored), "archive", stored.name)
        } else {
            val archive = File(cacheDir, "yomi-ci-book.cbz")
            if (!archive.exists() || intent.getBooleanExtra(EXTRA_RESET, false)) {
                writeFixture(archive)
            }
            Triple(Uri.fromFile(archive), "archive", "Yomi CI Book")
        }

        LocalLibraryStore(this).upsert(
            uri = target.first,
            title = target.third,
            locationType = if (kind == KIND_FOLDER) LibraryLocationType.TREE else LibraryLocationType.DOCUMENT,
            pageCount = if (kind == KIND_FOLDER) 4 else ARCHIVE_PAGE_COUNT,
        )

        val readerIntent = ReaderActivity.newIntent(this, target.first.toString(), target.second, target.third)
            .putExtra(ReaderActivity.EXTRA_MODE, requestedMode.name)
        if (intent.getBooleanExtra(EXTRA_KEEP_CHROME, false)) {
            readerIntent.putExtra(ReaderActivity.EXTRA_CI_KEEP_CHROME, true)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            readerIntent.putExtra(ReaderActivity.EXTRA_CI_OPEN_SETTINGS, true)
        }
        if (kind == KIND_FOLDER) {
            // Match ACTION_OPEN_DOCUMENT_TREE: the reader receives the tree URI through
            // ClipData and read/persistable grant flags. A bare URI string is not a valid
            // SAF grant and makes the fixture exercise a permission failure instead of a
            // real folder import.
            // ClipData.newUri() asks the provider for the URI's stream types. A tree root
            // URI is a grant target, but it is not itself a document URI, so that query is
            // rejected by DocumentsProvider.enforceTree(). Construct the grant item without
            // querying the provider, as the system picker does for tree selections.
            readerIntent.clipData = ClipData(
                "Yomi CI Folder",
                arrayOf(DocumentsContract.Document.MIME_TYPE_DIR),
                ClipData.Item(target.first),
            )
            readerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivity(readerIntent)
        finish()
    }

    private fun writeFixture(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            writePage(zip, "Chapter 1/1.png", Color.rgb(42, 56, 86))
            zip.putNextEntry(ZipEntry("Chapter 1/notes.txt"))
            zip.write("ignored by Yomi".encodeToByteArray())
            zip.closeEntry()
            writePage(zip, "Chapter 1/2.png", Color.rgb(68, 86, 122))
            writePage(zip, "Chapter 1/10.png", Color.rgb(94, 116, 154))
        }
    }

    private fun writePage(zip: ZipOutputStream, name: String, background: Int) {
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 8f
        }
        paint.style = Paint.Style.STROKE
        canvas.drawRect(28f, 28f, 692f, 1252f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 76f
        canvas.drawText("YOMI", 64f, 150f, paint)
        paint.textSize = 38f
        canvas.drawText("Archive reader page", 64f, 230f, paint)
        canvas.drawText(name, 64f, 292f, paint)
        paint.alpha = 160
        canvas.drawRect(64f, 360f, 656f, 372f, paint)
        paint.alpha = 255
        canvas.drawText("Page ${name.substringAfterLast('/').substringBeforeLast('.')}", 64f, 440f, paint)
        zip.putNextEntry(ZipEntry(name))
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
        zip.closeEntry()
        bitmap.recycle()
    }

    private companion object {
        const val ARCHIVE_PAGE_COUNT = 3
        const val EXTRA_MODE = "mode"
        const val EXTRA_RESET = "reset"
        const val EXTRA_KIND = "kind"
        const val EXTRA_KEEP_CHROME = "keepChrome"
        const val EXTRA_OPEN_SETTINGS = "openSettings"
        const val KIND_ARCHIVE = "archive"
        const val KIND_FOLDER = "folder"
        const val KIND_IMPORTED = "imported"
        const val KIND_EXTENSIONLESS = "extensionless"
    }
}
