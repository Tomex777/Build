package app.nami.android.compat

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.data.local.NamiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MinSdk26SmokeTest {

    @Test
    fun android8CanWriteAndShareNamiMovieThroughFileProvider() {
        assertEquals("This smoke must run on the minimum supported API", 26, Build.VERSION.SDK_INT)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: throw AssertionError("API 26 external Movies directory is unavailable")
        val file = File(movies, "Nami/MinSdk26/Provider smoke.txt")
        assertTrue("Could not create the API 26 smoke directory", file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        file.writeText("nami-api-26")

        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".downloads",
                file,
            )
            assertEquals("content", uri.scheme)
            assertEquals(context.packageName + ".downloads", uri.authority)
            context.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
                assertNotNull("FileProvider could not reopen its legacy download", descriptor)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun android8MigratesV4DownloadRowsToRetrySchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-api26-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            """CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                extension_name TEXT NOT NULL,
                source_anime_id TEXT NOT NULL,
                source_episode_id TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                display_name TEXT,
                content_uri TEXT,
                mime_type TEXT,
                state TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(source_id, source_episode_id)
            )""".trimIndent(),
        )
        legacy.version = 4
        legacy.close()

        val database = NamiDatabase(context, databaseName)
        try {
            database.upsertDownload(
                sourceId = "api26-source",
                extensionName = "API 26 fixture",
                sourceAnimeId = "/anime",
                sourceEpisodeId = "/episode",
                relativePath = "API 26 fixture/Show/Season 01",
                state = "ERROR",
                progress = 0,
                animeTitle = "Show",
                episodeTitle = "Episode 1",
                animeSourceState = """{"token":"anime"}""",
                episodeSourceState = """{"token":"episode"}""",
            )

            val stored = database.getDownloads().single()
            assertEquals("Show", stored.animeTitle)
            assertEquals("Episode 1", stored.episodeTitle)
            assertEquals("""{"token":"anime"}""", stored.animeSourceState)
            assertEquals("""{"token":"episode"}""", stored.episodeSourceState)

            val columns = database.writableDatabase
                .rawQuery("PRAGMA table_info(downloads)", null)
                .use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }
            assertTrue("anime_source_state" in columns)
            assertTrue("episode_source_state" in columns)
            assertTrue("bytes_downloaded" in columns)
            assertTrue("total_bytes" in columns)
            assertTrue("temp_path" in columns)
            assertTrue("hls_completed_parts" in columns)
            assertTrue("pause_reason" in columns)
            assertTrue("retry_count" in columns)
            assertTrue("media_kind" in columns)
            assertTrue("etag" in columns)
            assertTrue("last_modified" in columns)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
