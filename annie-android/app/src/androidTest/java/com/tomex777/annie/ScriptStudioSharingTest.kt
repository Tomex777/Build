package com.tomex777.annie

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScriptStudioSharingTest {
    @Test fun exportUsesCurrentDraftOrFreshDiskAndCorrectDocumentContract() {
        val files = ScriptFiles(InstrumentationRegistry.getInstrumentation().targetContext)
        val folder = files.createFolder("exports${System.nanoTime()}")
        try {
            files.writeFile(folder.name, "index.html", "<p>Old</p>")
            val stale = files.listProjects().first { it.id == folder.name }
            files.writeFile(folder.name, "index.html", "<p>Fresh disk</p>")
            assertEquals("<p>Old</p>", stale.files["index.html"])
            assertEquals("<p>Unsaved draft</p>", studioDocumentSource(files, folder.name, "index.html",
                folder.name, "index.html", "<p>Unsaved draft</p>"))
            val snapshot = studioDocumentSource(files, folder.name, "index.html", "another", "index.html", "Other draft")
            assertEquals("<p>Fresh disk</p>", snapshot)
            files.writeFile(folder.name, "index.html", "<p>Later edit</p>")
            assertEquals("<p>Fresh disk</p>", snapshot)
            val intent = studioExportIntent("pages/index.html")
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals("text/html", intent.type)
            assertEquals("index.html", intent.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals("application/json", studioExportIntent("data.json").type)
        } finally { folder.deleteRecursively() }
    }

    @Test fun sharedSnapshotsKeepTheirBytesAndUseDocumentMimeAndReadGrant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "shared-scripts")
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        try {
            val first = studioShareIntent(context, "pages/index.html", "<p>First draft</p>")
            val second = studioShareIntent(context, "pages/index.html", "<p>Latest draft</p>")
            assertEquals(Intent.ACTION_SEND, second.action)
            assertEquals("text/html", second.type)
            assertTrue(second.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            fun read(intent: Intent) = context.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)!!.bufferedReader().use { it.readText() }
            assertEquals("<p>First draft</p>", read(first))
            assertEquals("<p>Latest draft</p>", read(second))
            assertNotEquals(first.clipData!!.getItemAt(0).uri, second.clipData!!.getItemAt(0).uri)
            assertEquals("text/css", studioShareIntent(context, "styles/theme.css", "body {}").type)
        } finally {
            root.listFiles().orEmpty().filter { it.name !in before }.forEach { it.deleteRecursively() }
        }
    }
}
