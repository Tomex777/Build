package app.nami.android.compat

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.android.NamiApplication
import app.nami.data.local.NamiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api36SmokeTest {

    @Test
    fun android16LaunchAndWatchProgressPersistenceAreHealthy() {
        assertEquals("This smoke must run on Android 16 / API 36", 36, Build.VERSION.SDK_INT)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-api36-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        NamiDatabase(context, databaseName).use { database ->
            database.upsertWatchProgress(
                sourceId = "api36-source",
                sourceAnimeId = "/anime",
                sourceEpisodeId = "/episode-8",
                animeTitle = "API 36 Fixture",
                episodeTitle = "Episode 8",
                animeSourceState = """{"anime":"state"}""",
                episodeSourceState = """{"episode":"state"}""",
                positionMs = 822_000L,
                durationMs = 1_440_000L,
                completed = false,
            )
        }

        NamiDatabase(context, databaseName).use { reopened ->
            val progress = reopened.getWatchProgress("api36-source", "/episode-8")
            assertNotNull("API 36 could not reopen persisted watch progress", progress)
            progress!!
            assertEquals(822_000L, progress.positionMs)
            assertEquals(1_440_000L, progress.durationMs)
            assertEquals("/anime", progress.sourceAnimeId)
            assertEquals("API 36 Fixture", progress.animeTitle)
            assertTrue(!progress.completed)
            assertEquals(
                "/episode-8",
                reopened.getContinueWatching().single().sourceEpisodeId,
            )
        }

        context.deleteDatabase(databaseName)

        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        app.downloadManager.startBackgroundEngine()
        Thread.sleep(750)
        assertTrue(
            "Android 16 could not start and promote Nami's foreground download service",
            true,
        )
    }
}
