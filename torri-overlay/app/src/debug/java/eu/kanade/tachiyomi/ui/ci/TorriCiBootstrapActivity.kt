package eu.kanade.tachiyomi.ui.ci

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only deterministic data bootstrap for Torri's emulator visual/runtime CI.
 *
 * This never ships in release builds. It intentionally uses the normal Local Source
 * directory layout so CI exercises Mihon's real source/details/chapter/reader path.
 */
class TorriCiBootstrapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        check(BuildConfig.DEBUG) { "Torri CI bootstrap must never run in release builds" }

        val root = File(requireNotNull(getExternalFilesDir(null)), "TorriCiStorage")
        root.deleteRecursively()
        val localRoot = File(root, "local").apply { mkdirs() }

        fixtures.forEach { createFixture(localRoot, it) }

        val preferencesCommitted = PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("__APP_STATE_onboarding_complete", true)
            .putBoolean("__APP_STATE_donation_campaign_shown", true)
            .putString("__APP_STATE_storage_dir", Uri.fromFile(root).toString())
            .commit()
        check(preferencesCommitted) { "Torri CI bootstrap preferences were not persisted" }

        // Runtime smoke scripts poll this instead of relying on `am start -W`.
        // Android 8.0 can wait indefinitely for -W when this headless bootstrap
        // finishes before ActivityManager observes a drawn window.
        File(root, ".bootstrap-complete").writeText("ready")

        setResult(RESULT_OK)
        finish()
    }

    private fun createFixture(root: File, fixture: Fixture) {
        val manga = File(root, fixture.title).apply { mkdirs() }
        val chapter = if (fixture.hasChapter) {
            File(manga, "Chapter 1").apply { mkdirs() }
        } else {
            null
        }

        File(manga, "ComicInfo.xml").writeText(
            """
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <Series>${fixture.title}</Series>
                <Summary>Deterministic Torri CI manga used to verify cover-adaptive details, chapter controls, and reader launch.</Summary>
                <Writer>Torri CI Author</Writer>
                <Penciller>Torri CI Artist</Penciller>
                <Genre>Action, Fantasy, Test Fixture</Genre>
            </ComicInfo>
            """.trimIndent(),
        )

        if (fixture.hasCover) {
            writeImage(
                file = File(manga, "cover.jpg"),
                background = fixture.background,
                accent = fixture.accent,
                cover = true,
            )
        }
        if (chapter != null) {
            writeImage(
                file = File(chapter, "001.jpg"),
                background = fixture.pageBackground,
                accent = fixture.accent,
                cover = false,
            )
            writeImage(
                file = File(chapter, "002.jpg"),
                background = fixture.accent,
                accent = fixture.pageBackground,
                cover = false,
            )
        }
    }

    private fun writeImage(
        file: File,
        background: Int,
        accent: Int,
        cover: Boolean,
    ) {
        val width = if (cover) 600 else 900
        val height = if (cover) 900 else 1400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(background)

        paint.color = accent
        canvas.drawRect(
            width * 0.08f,
            height * 0.08f,
            width * 0.92f,
            height * 0.42f,
            paint,
        )
        canvas.drawCircle(
            width * 0.68f,
            height * 0.70f,
            width * 0.22f,
            paint,
        )

        paint.color = Color.argb(210, 245, 240, 240)
        paint.strokeWidth = width * 0.035f
        canvas.drawLine(
            width * 0.12f,
            height * 0.58f,
            width * 0.88f,
            height * 0.58f,
            paint,
        )
        canvas.drawLine(
            width * 0.26f,
            height * 0.50f,
            width * 0.26f,
            height * 0.88f,
            paint,
        )
        canvas.drawLine(
            width * 0.74f,
            height * 0.50f,
            width * 0.74f,
            height * 0.88f,
            paint,
        )

        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        bitmap.recycle()
    }

    private data class Fixture(
        val title: String,
        val background: Int,
        val accent: Int,
        val pageBackground: Int,
        val hasCover: Boolean = true,
        val hasChapter: Boolean = true,
    )

    private companion object {
        val fixtures = listOf(
            Fixture("Torri Red", Color.rgb(92, 18, 26), Color.rgb(244, 97, 66), Color.rgb(246, 230, 216)),
            Fixture("Torri Blue", Color.rgb(16, 32, 68), Color.rgb(55, 139, 235), Color.rgb(225, 236, 250)),
            Fixture("Torri Green", Color.rgb(18, 55, 42), Color.rgb(76, 181, 121), Color.rgb(225, 244, 232)),
            Fixture("Torri Missing", Color.rgb(26, 30, 36), Color.rgb(128, 138, 148), Color.rgb(236, 238, 240), hasCover = false, hasChapter = false),
            Fixture("Torri Dark", Color.rgb(8, 10, 15), Color.rgb(72, 116, 144), Color.rgb(28, 32, 40)),
            Fixture("Torri Bright", Color.rgb(248, 247, 242), Color.rgb(255, 220, 190), Color.rgb(252, 250, 245)),
            Fixture("Torri Monochrome", Color.rgb(238, 238, 238), Color.rgb(35, 35, 35), Color.rgb(248, 248, 248)),
        )
    }
}
