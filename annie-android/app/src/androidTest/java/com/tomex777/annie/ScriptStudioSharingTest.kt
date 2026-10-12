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
