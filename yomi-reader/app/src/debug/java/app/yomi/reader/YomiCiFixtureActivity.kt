package app.yomi.reader

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        if (kind == KIND_FOLDER) {
            // Match ACTION_OPEN_DOCUMENT_TREE: the reader receives the tree URI through
            // ClipData and read/persistable grant flags. A bare URI string is not a valid
            // SAF grant and makes the fixture exercise a permission failure instead of a
            // real folder import.
            readerIntent.clipData = ClipData.newUri(contentResolver, "Yomi CI Folder", target.first)
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
        Canvas(bitmap).drawColor(background)
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
        const val KIND_ARCHIVE = "archive"
        const val KIND_FOLDER = "folder"
    }
}
