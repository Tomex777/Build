package com.tomex777.annie

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieImageEditorTest {
    @Test fun inlineImageDecodesIntoBoundedEditableBitmap() = runBlocking {
        val source = Bitmap.createBitmap(16_384, 8, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.GREEN)
        val output = java.io.ByteArrayOutputStream()
        assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
        val uri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(output.toByteArray())
        val decoded = loadEditableAnnieBitmap(InstrumentationRegistry.getInstrumentation().targetContext, uri)
        try {
            assertNotNull("Inline image must be editable offline", decoded)
            assertTrue("Editable width must be bounded", decoded!!.width <= 4096)
            assertTrue(decoded.height >= 1)
            assertEquals(Color.GREEN, decoded.getPixel(decoded.width / 2, decoded.height / 2))
            assertEquals(16_384, source.width)
        } finally {
            decoded?.recycle()
            source.recycle()
        }
    }

    @Test fun editsRotateRealBitmapWithoutChangingOriginal() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        source.setPixel(0, 0, Color.BLUE)
        val rotated = applyAnnieImageEdits(source, AnnieImageEdits(quarterTurns = 1))
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
        assertEquals(Color.BLUE, source.getPixel(0, 0))
        assertEquals(4, source.width)
    }

    @Test fun squareCropProducesSquareImageWithoutAlteringOriginal() {
        val source = Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888)
        val crop = applyAnnieImageEdits(source, AnnieImageEdits(squareCrop = true))
        assertEquals(6, crop.width)
        assertEquals(6, crop.height)
        assertEquals(12, source.width)
    }

    @Test fun horizontalMirrorChangesPixelOrientation() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        source.setPixel(1, 0, Color.BLUE)
        val flipped = applyAnnieImageEdits(source, AnnieImageEdits(mirrored = true))
        assertEquals(Color.BLUE, flipped.getPixel(0, 0))
        assertEquals(Color.RED, flipped.getPixel(1, 0))
    }
}
