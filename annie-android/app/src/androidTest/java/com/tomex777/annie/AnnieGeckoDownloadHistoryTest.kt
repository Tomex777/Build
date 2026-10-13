package com.tomex777.annie

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieGeckoDownloadHistoryTest {
    @Test fun completedFileReopensFromRecreatedHistoryWithoutNetworkOrRequestSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("annie_gecko_downloads_v1", Context.MODE_PRIVATE)
        val previous = prefs.getString("completed", null)
        val name = "annie-offline-${UUID.randomUUID()}.txt"
        val bytes = "Downloaded once; available offline after Annie reopens.".toByteArray()
        val uri = runBlocking {
            AnnieGeckoDownloadStore.saveResponse(context, name, "text/plain", bytes.inputStream())
        }
        try {
            val completed = AnnieGeckoDownloadEntry(
                UUID.randomUUID().toString(), name, "text/plain", bytes.size.toLong(),
                bytesDone = bytes.size.toLong(), status = AnnieGeckoDownloadStatus.COMPLETED,
                contentUri = uri.toString(),
            )
            AnnieGeckoDownloadHistory(context).save(listOf(
                completed,
                completed.copy(id = "active", status = AnnieGeckoDownloadStatus.DOWNLOADING),
                completed.copy(id = "cancelled", status = AnnieGeckoDownloadStatus.CANCELLED),
                completed.copy(id = "failed", status = AnnieGeckoDownloadStatus.FAILED,
                    reason = "https://user:password@host.example/?token=secret"),
            ))
            val reopened = AnnieGeckoDownloadHistory(context).read()
            assertEquals(listOf(completed), reopened)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(
                android.net.Uri.parse(reopened.single().contentUri)
            )!!.use { it.readBytes() })
            val snapshot = prefs.getString("completed", "")!!
            assertFalse(snapshot.contains("password"))
            assertFalse(snapshot.contains("token"))
            assertFalse(snapshot.contains("host.example"))
        } finally {
            if (previous == null) prefs.edit().remove("completed").commit()
            else prefs.edit().putString("completed", previous).commit()
            if (Build.VERSION.SDK_INT >= 29) context.contentResolver.delete(uri, null, null)
            else {
                val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, "Annie") } ?: File(context.filesDir, "downloads/Annie")
                File(root, name).delete()
            }
        }
    }
}
