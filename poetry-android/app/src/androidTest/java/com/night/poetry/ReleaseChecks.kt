package com.night.poetry

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Release gates executed on actual Android 8 and Android 15 emulators.
 * Exercise the production persistence and PNG renderer, not mocked JSON/bitmaps.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseChecks {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun persistentPoemsAndOfflineBackupRoundTrip() {
        val store = PoemStore(app)
        val previous = store.read()
        val style1 = CardStyleData(
            background = "Custom", color1 = 0xFF183149.toInt(), color2 = 0xFF527891.toInt(),
            textColor = 0xFFFBF5EA.toInt(), fontSize = 16f,
            elements = listOf(Ornament(assetId = "sparkle", x = .34f, y = .20f))
        )
        val style2 = style1.copy(color1 = 0xFF6E4059.toInt(), texture = "Grain")
        val poem = PoemDoc(
            title = "An offline promise", body = "Rain arrives.\n\nThe words remain.",
            author = "Poetry", card = style1,
            slides = listOf(
                CardSlide(titleOverride = "An offline promise", bodyOverride = "Rain arrives.", card = style1),
                CardSlide(titleOverride = "", bodyOverride = "The words remain.", card = style2)
            )
        )
        try {
            store.write(listOf(poem))
            // Deliberately instantiate a new repository, equivalent to a cold app start.
            val disk = PoemStore(app).read()
            assertEquals(1, disk.size)
            assertEquals(poem.id, disk[0].id)
            assertEquals(poem.body, disk[0].body)
            assertEquals(poem.slides.size, disk[0].slides.size)
            assertEquals(style2.color1, disk[0].slides[1].card.color1)
            assertEquals(style1.elements[0].assetId, disk[0].card.elements[0].assetId)

            val backgrounds = listOf(SavedBackground(0xFF081524.toInt(), 0xFF182A41.toInt(), "Grain"))
            val backup = store.backupBytes(disk, backgrounds)
            assertFalse(String(backup).contains("http"))
            val restored = store.parseBackup(backup)
            assertEquals(disk, restored.poems)
            assertEquals(backgrounds, restored.backgrounds)
            assertTrue(runCatching { store.parseBackup("{invalid".toByteArray()) }.isFailure)
        } finally {
            store.write(previous)
        }
    }

    @Test fun pngExportsRespectAspectRatioBothResolutionsAndSlides() {
        val firstColor = 0xFF1C3248.toInt()
        val secondColor = 0xFF55344B.toInt()
        val first = CardStyleData(
            color1 = firstColor, color2 = firstColor, texture = "None",
            decor = "None", textColor = 0xFFEFF9FF.toInt(), poemY = .36f
        )
        val second = first.copy(color1 = secondColor, color2 = secondColor)
        val pages = listOf(
            PoemDoc(title = "Dawn", body = "One line of verse.", card = first),
            PoemDoc(title = "", body = "Another line of verse.", card = second)
        )
        val files = mutableListOf<File>()
        try {
            for ((index, page) in pages.withIndex()) {
                for (width in listOf(1080, 2160)) {
                    val target = File.createTempFile("poetry-release-${index}-${width}-", ".png", app.cacheDir)
                    files += target
                    CardExporter.export(app, page, width, Uri.fromFile(target))
                    assertTrue("PNG must be written", target.length() > 200)
                    val metadata = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    target.inputStream().use { BitmapFactory.decodeStream(it, null, metadata) }
                    assertEquals(width, metadata.outWidth)
                    assertEquals(width * 16 / 9, metadata.outHeight)
                    assertEquals("image/png", metadata.outMimeType)
                    // The blank top-left corner is an unmodified color from preview's
                    // vertical-gradient background: catch wrong backgrounds/slide reuse.
                    val reduced = BitmapFactory.Options().apply { inSampleSize = 16 }
                    val bitmap = target.inputStream().use { BitmapFactory.decodeStream(it, null, reduced) }
                    assertNotNull(bitmap)
                    val sample = bitmap!!.getPixel(2, 2)
                    val expected = if (index == 0) firstColor else secondColor
                    for (shift in listOf(0, 8, 16)) {
                        assertTrue("Export background differs from card style",
                            kotlin.math.abs(((sample shr shift) and 255) - ((expected shr shift) and 255)) <= 3)
                    }
                    bitmap.recycle()
                }
            }
        } finally { files.forEach { it.delete() } }
    }

    @Test fun contentUriExportAndBackupWorkWithoutStoragePermissions() {
        // FileProvider is a real content:// provider: exercises the same
        // ContentResolver.openOutputStream path as Android's file picker.
        val privateOutput = File(app.filesDir, "Poetry")
        assertTrue(privateOutput.isDirectory || privateOutput.mkdirs())
        val imageFile = File(privateOutput, "content-uri-test.png")
        val backupFile = File(privateOutput, "backup-uri-test.json")
        try {
            val card = CardStyleData(color1 = 0xFF214056.toInt(),
                color2 = 0xFF69869B.toInt(), texture = "Grain",
                elements = listOf(Ornament(assetId = "sparkle", size = 1.3f, x = .65f, y = .12f)))
            val doc = PoemDoc(title = "Kept offline", body = "These words stay mine.", card = card)
            val imageUri = FileProvider.getUriForFile(
                app, "${app.packageName}.fileprovider", imageFile)
            assertEquals("content", imageUri.scheme)
            CardExporter.export(app, doc, 1080, imageUri)
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            assertNotNull(bitmap)
            assertEquals(1080, bitmap!!.width)
            assertEquals(1920, bitmap.height)
            bitmap.recycle()

            val backupUri = FileProvider.getUriForFile(
                app, "${app.packageName}.fileprovider", backupFile)
            val originalBytes = PoemStore(app).backupBytes(listOf(doc), emptyList())
            app.contentResolver.openOutputStream(backupUri, "w")!!.use { it.write(originalBytes) }
            val restoredBytes = app.contentResolver.openInputStream(backupUri)!!.use { it.readBytes() }
            assertArrayEquals(originalBytes, restoredBytes)
            assertEquals(doc, PoemStore(app).parseBackup(restoredBytes).poems.single())

            // Proof for reviewing the actual offline PNG renderer's textures,
            // fonts and vectors against the card preview.
            val proof = File(checkNotNull(app.getExternalFilesDir(null)), "poetry-export-card-1080.png")
            imageFile.copyTo(proof, overwrite = true)
        } finally {
            imageFile.delete()
            backupFile.delete()
        }
    }

    @Test fun fullResolutionVisualProofHasCorrectDimensions() {
        val style = CardStyleData(
            background = "Custom", color1 = 0xFF162A38.toInt(),
            color2 = 0xFF516A7B.toInt(), textColor = 0xFFFEF9F4.toInt(),
            fontSize = 19f, decor = "Stars", texture = "Grain",
            elements = listOf(
                Ornament(assetId = "flower", x = .15f, y = .15f, size = 1.5f, opacity = .80f),
                Ornament(assetId = "sparkle", x = .72f, y = .70f, size = 1.2f)
            )
        )
        val doc = PoemDoc(
            title = "Where the evening goes",
            body = "A little light remains,\\n\\nEven when the day grows old.".replace("\\n", "\n"),
            author = "Poetry", card = style
        )
        val proof = File(checkNotNull(app.getExternalFilesDir(null)), "poetry-export-card-4k.png")
        val exported = CardExporter.export(app, doc, 2160, Uri.fromFile(proof))
        assertEquals("file", exported.scheme)
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(proof.absolutePath, size)
        assertEquals(2160, size.outWidth)
        assertEquals(3840, size.outHeight)
        assertTrue(proof.length() > 100_000)
    }

    @Test fun overflowingVerseCannotBeSilentlyCropped() {
        val hugePoem = PoemDoc(
            title = "Long poem",
            body = (1..240).joinToString("\n") { "The words must never be lost on export." }
        )
        val target = File.createTempFile("poetry-overflow-", ".png", app.cacheDir)
        try {
            val attempt = runCatching { CardExporter.export(app, hugePoem, 1080, Uri.fromFile(target)) }
            assertTrue("Long poem must fail instead of producing a truncated card", attempt.isFailure)
            assertTrue("Show a meaningful export error",
                attempt.exceptionOrNull()?.message?.contains("overflows") == true)
            assertEquals("Never save a half-rendered PNG", 0L, target.length())
        } finally { target.delete() }
    }
}
