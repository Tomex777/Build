package app.nami.android.compat

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.security.NetworkSecurityPolicy
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.nami.android.AniyomiSourcePreferencesActivity
import app.nami.android.NamiNativeConfigurationHandle
import app.nami.android.NamiApplication
import app.nami.android.MainActivity
import app.nami.android.NamiDownloadService
import app.nami.data.local.NamiDatabase
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.source.NamiConfigurableSource
import app.nami.source.SourceOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class Api36SmokeTest {

    @Test
    fun cleartextIsLimitedToTheLocalMediaProxy() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(
            "Loopback HLS proxy must allow cleartext",
            policy.isCleartextTrafficPermitted("127.0.0.1"),
        )
        assertTrue(
            "Localhost HLS proxy must allow cleartext",
            policy.isCleartextTrafficPermitted("localhost"),
        )
        assertFalse(
            "Remote source hosts must require HTTPS",
            policy.isCleartextTrafficPermitted("example.com"),
        )
    }

    @Test
    fun onlyLauncherAndGrantedDownloadProviderAreExported() {
        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        val manager = app.packageManager
        val launcher = manager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        val preferences = manager.getActivityInfo(
            ComponentName(app, AniyomiSourcePreferencesActivity::class.java),
            0,
        )
        val downloadService = manager.getServiceInfo(
            ComponentName(app, NamiDownloadService::class.java),
            0,
        )
        val downloadsProvider = manager.getProviderInfo(
            ComponentName(app, androidx.core.content.FileProvider::class.java),
            0,
        )
        assertTrue("Nami launcher must be externally launchable", launcher.exported)
        assertFalse("Source settings must remain private", preferences.exported)
        assertFalse("Download service must remain private", downloadService.exported)
        assertFalse("Download FileProvider must remain private", downloadsProvider.exported)
        assertTrue(
            "Download FileProvider must grant content URIs",
            downloadsProvider.grantUriPermissions,
        )
        assertTrue(
            "Download FileProvider authority must be app-scoped",
            downloadsProvider.authority.endsWith(".downloads"),
        )
    }

    @Test
    fun productionPlaybackSourceIsAvailableWithoutCompanionInstall() = runBlocking {
        assertEquals("This smoke must run on Android 16 / API 36", 36, Build.VERSION.SDK_INT)
        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        app.installedSourceRegistry.invalidate()

        val source = app.installedSourceRegistry.installedSources().firstOrNull {
            it.metadata.id == "app.nami.source.kayoanime:en"
        }
        assertNotNull("Production KayoAnime source was not available on a clean Nami install", source)
        source!!
        assertEquals(SourceOrigin.NATIVE_NAMI, source.metadata.origin)
        assertTrue("Bundled KayoAnime must be streamable", source.metadata.capabilities.streamable)
        assertTrue("Bundled KayoAnime must be downloadable", source.metadata.capabilities.downloadable)
        assertTrue(
            "Clean-install smoke unexpectedly resolved a companion KayoAnime extension",
            source.metadata.extensionPackage == null,
        )
    }

    @Test
    fun firstPartyExtensionIsDiscoveredAndRunsTheNamiSourceContract() = runBlocking {
        assertEquals("This smoke must run on Android 16 / API 36", 36, Build.VERSION.SDK_INT)
        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        app.installedSourceRegistry.invalidate()

        val source = app.installedSourceRegistry.installedSources().firstOrNull {
            it.metadata.extensionPackage == "app.nami.fixture.nativeextension"
        }
        assertNotNull("First-party Nami extension APK was not discovered", source)
        source!!
        assertEquals(SourceOrigin.NATIVE_NAMI, source.metadata.origin)
        assertEquals(1, source.metadata.extensionApiVersion)
        assertEquals("1.0.0", source.metadata.extensionVersion)
        assertTrue(
            "Extension host dropped the native source's configurable settings interface",
            source is NamiConfigurableSource,
        )
        assertEquals(
            "quality",
            (source as NamiConfigurableSource).settings().single().key,
        )

        val configurationHandle = source as NamiNativeConfigurationHandle
        val settingsIntent = Intent(app, AniyomiSourcePreferencesActivity::class.java)
            .putExtra(AniyomiSourcePreferencesActivity.EXTRA_SOURCE_ID, source.metadata.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val settingsActivity = ActivityScenario.launch<AniyomiSourcePreferencesActivity>(settingsIntent)
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertNotNull(
                "Nami's native source settings screen did not open",
                device.wait(Until.findObject(By.text("Preferred quality")), 15_000),
            )
            val quality720 = device.wait(Until.findObject(By.text("720p")), 10_000)
                ?: throw AssertionError("Nami did not render the source quality choice")
            quality720.click()
            assertEquals(
                "Native source setting was not persisted through Nami's host preference store",
                "720p",
                configurationHandle.getPreference("quality"),
            )
            assertEquals(
                "The configured source could not read its saved quality value",
                "720p",
                (source as NamiConfigurableSource).settings().single()
                    .let { it as app.nami.source.NamiSourceSetting.Choice }
                    .defaultValue,
            )
            device.waitForIdle()
            SystemClock.sleep(500)
            val settingsScreenshot = File(app.filesDir, "nami-native-source-preferences.png")
            settingsActivity.onActivity { activity ->
                val view = activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(
                    view.width.coerceAtLeast(1),
                    view.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                view.draw(Canvas(bitmap))
                try {
                    FileOutputStream(settingsScreenshot).use { output ->
                        assertTrue(
                            "Could not encode Nami's native source settings screen",
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output),
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            val screenshotBitmap = BitmapFactory.decodeFile(settingsScreenshot.absolutePath)
                ?: throw AssertionError("Nami's native settings screenshot was not a valid image")
            try {
                var nonBlackSamples = 0
                for (y in 0 until screenshotBitmap.height step 8) {
                    for (x in 0 until screenshotBitmap.width step 8) {
                        if (screenshotBitmap.getPixel(x, y) != Color.BLACK) nonBlackSamples++
                    }
                }
                assertTrue(
                    "Nami's native settings screenshot was blank",
                    nonBlackSamples > 100,
                )
            } finally {
                screenshotBitmap.recycle()
            }
        } finally {
            settingsActivity.close()
            configurationHandle.removePreference("quality")
        }

        val result: AnimeSearchResult = source.search("Nami", page = 1).items.single()
        assertEquals("Nami Contract Sample", result.title)
        val details = source.details(result.ref, result.sourceState)
        assertEquals("Nami Contract Sample", details.title)
        assertEquals("Ongoing", details.metadata["status"])
        val episode = source.episodes(details.ref, details.sourceState).single()
        val stream = source.resolve(episode.ref, episode.sourceState).single()
        assertEquals("1080p", stream.quality)
        assertEquals("application/vnd.apple.mpegurl", stream.mimeType)
        assertEquals("https://example.invalid/", stream.headers["Referer"])
        assertEquals("Sample CDN", stream.hosterName)

        val enabledSourceRegistry = app.sourceRegistry
        app.sourceEnablementStore.setEnabled(source.metadata.id, false)
        try {
            assertTrue(
                "Disabled first-party source remained in normal source discovery",
                enabledSourceRegistry.installedSources().none {
                    it.metadata.id == source.metadata.id
                },
            )
        } finally {
            app.sourceEnablementStore.setEnabled(source.metadata.id, true)
        }
        assertTrue(
            "Re-enabled first-party source was not immediately discoverable",
            enabledSourceRegistry.installedSources().any {
                it.metadata.id == source.metadata.id
            },
        )
    }

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

    @Test
    fun persistHomeStateForProcessRestartAcceptance() {
        val app = ApplicationProvider.getApplicationContext<NamiApplication>()
        val details = AnimeDetails(
            ref = AnimeRef("process-restart-fixture", "/nami-process-fixture"),
            title = "Nami Process Fixture",
            sourceState = "anime-state",
        )
        app.database.addToLibrary(details)
        app.database.upsertWatchProgress(
            sourceId = details.ref.sourceId,
            sourceAnimeId = details.ref.sourceAnimeId,
            sourceEpisodeId = "/episode-1",
            animeTitle = details.title,
            episodeTitle = "Episode 1",
            animeSourceState = details.sourceState,
            episodeSourceState = "episode-state",
            positionMs = 125_000L,
            durationMs = 1_200_000L,
            completed = false,
        )

        assertEquals("Nami Process Fixture", app.database.getLibraryEntries()
            .single { it.ref.sourceId == details.ref.sourceId }.title)
        assertEquals("Nami Process Fixture", app.database.getContinueWatching()
            .single { it.sourceId == details.ref.sourceId }.animeTitle)
    }
}
