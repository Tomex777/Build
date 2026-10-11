package com.tomex777.annie

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieGeckoFilePickerTest {
    @Test fun singleFilePickerUsesAndroidDocumentApiAndRestrictsAcceptedMimeType() {
        val intent = AnnieGeckoFilePicker.intent(arrayOf("image/png"), multiple = false)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun multipleFilePickerPreservesSupportedMimeTypes() {
        val intent = AnnieGeckoFilePicker.intent(
            arrayOf("image/png", "image/jpeg", "image/png", "javascript:alert(1)"), true
        )
        assertEquals("*/*", intent.type)
        assertArrayEquals(
            arrayOf("image/png", "image/jpeg"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test fun selectedDocumentUrisAreCollectedWithoutDuplicates() {
        val first = Uri.parse("content://documents/one")
        val second = Uri.parse("content://documents/two")
        val intent = Intent().apply {
            data = first
            clipData = ClipData.newUri(null, "one", first).apply {
                addItem(ClipData.Item(second))
            }
        }
        assertArrayEquals(arrayOf(first, second), AnnieGeckoFilePicker.uris(intent))
        assertTrue(AnnieGeckoFilePicker.uris(null).isEmpty())
    }
}
