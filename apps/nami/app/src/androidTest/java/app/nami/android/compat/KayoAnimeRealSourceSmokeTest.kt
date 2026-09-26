package app.nami.android.compat

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.nami.android.NamiApplication
import app.nami.android.NamiDownloadState
import app.nami.android.NamiDownloadStatus
import app.nami.android.NamiVlcPlayer
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.domain.ResolvedMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KayoAnimeRealSourceSmokeTest {

    @Test
    fun realKayoAnimeDriveMkvDownloadsAndPlaysOfflineWithVlc() = runBlocking<Unit> {
        val application = ApplicationProvider.getApplicationContext<NamiApplication>()
        val context: Context = application
        val source = AniyomiExtensionRegistry(context)
            .installedSources()
            .firstOrNull {
                it.metadata.extensionPackage == "app.nami.source.kayoanime"
            }
        assertNotNull("KayoAnime extension APK was not discovered", source)
        source!!

        val results = withTimeout(120_000) {
            source.search("Re:ZERO").items
        }
        assertTrue("KayoAnime search returned no real Re:ZERO results", results.isNotEmpty())

        val result = results.firstOrNull {
            it.ref.sourceAnimeId.contains("rezero-starting-life", ignoreCase = true) ||
                it.title.contains("Re:ZERO", ignoreCase = true) ||
                it.title.contains("ReZero", ignoreCase = true)
        } ?: throw AssertionError(
            "KayoAnime search did not expose the real Re:ZERO page: " +
                results.take(12).joinToString { it.title },
        )

        val details = withTimeout(90_000) {
            source.details(result.ref, result.sourceState)
        }
        assertTrue("KayoAnime details title was empty", details.title.isNotBlank())

        val episodes = withTimeout(300_000) {
            source.episodes(details.ref, details.sourceState)
        }
        assertTrue("KayoAnime did not expose any Google Drive media files", episodes.isNotEmpty())

        val mkvEpisode = episodes.firstOrNull {
            it.title.endsWith(".mkv", ignoreCase = true)
        } ?: throw AssertionError(
            "KayoAnime Google Drive listing did not expose a real MKV file: " +
                episodes.take(20).joinToString { it.title },
        )

        val media = withTimeout(90_000) {
            source.resolve(mkvEpisode.ref, mkvEpisode.sourceState)
        }
        val driveMedia = media.firstOrNull {
            it.url.contains("drive.google.com") &&
                it.url.contains("export=download") &&
                it.url.contains("id=")
        }
        assertNotNull("KayoAnime MKV did not resolve into a Google Drive download URL", driveMedia)

        val manager = application.downloadManager
        val key = manager.key(source.metadata.id, mkvEpisode.ref.sourceEpisodeId)
        var completed: NamiDownloadStatus? = null
        var player: NamiVlcPlayer? = null

        try {
            manager.statuses.value[key]?.let { existing ->
                manager.remove(existing)
                withTimeout(30_000) {
                    while (manager.statuses.value.containsKey(key)) delay(100)
                }
            }

            manager.enqueue(source, details, mkvEpisode)

            val started = withTimeout(120_000) {
                var value: NamiDownloadStatus? = null
                while (value == null) {
                    val status = manager.statuses.value[key]
                    if (
                        status?.state == NamiDownloadState.DOWNLOADING &&
                        status.bytesDownloaded > 0L
                    ) {
                        value = status
                    } else if (status?.state == NamiDownloadState.ERROR) {
                        throw AssertionError(
                            "KayoAnime failed before background-survival check: " +
                                status.errorMessage,
                        )
                    } else {
                        delay(200)
                    }
                }
                value
            }

            val beforeBackgroundBytes = started.bytesDownloaded
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
            delay(2_000)
            val afterBackground = manager.statuses.value[key]
                ?: throw AssertionError("Download disappeared after pressing Home")
            assertTrue(
                "Foreground download did not survive leaving Nami",
                afterBackground.state != NamiDownloadState.ERROR &&
                    afterBackground.state != NamiDownloadState.PAUSED,
            )
            assertTrue(
                "Background download lost its persisted progress",
                afterBackground.bytesDownloaded >= beforeBackgroundBytes,
            )

            completed = withTimeout(900_000) {
                var value: NamiDownloadStatus? = null
                while (value == null) {
                    val status = manager.statuses.value[key]
                    when (status?.state) {
                        NamiDownloadState.DOWNLOADED -> value = status
                        NamiDownloadState.ERROR -> throw AssertionError(
                            "Real KayoAnime Google Drive download failed: " + status.errorMessage,
                        )
                        else -> delay(250)
                    }
                }
                value
            }

            assertTrue(
                "KayoAnime Google Drive download changed the MKV container: ${completed!!.displayName}",
                completed!!.displayName?.endsWith(".mkv", ignoreCase = true) == true,
            )
            assertTrue(
                "KayoAnime Google Drive download reported the wrong container MIME: ${completed!!.mimeType}",
                completed!!.mimeType == "video/x-matroska" ||
                    completed!!.mimeType == "video/mkv" ||
                    completed!!.mimeType == "application/octet-stream",
            )

            val contentUri = completed!!.contentUri
            assertTrue("KayoAnime download did not persist a content URI", !contentUri.isNullOrBlank())
            val uri = android.net.Uri.parse(contentUri)
            val size = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) -1L else cursor.getLong(0)
            } ?: -1L
            assertTrue("Downloaded KayoAnime MKV was unexpectedly small ($size bytes)", size > 1_000_000L)

            val signature = context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(4).also { bytes ->
                    val read = input.read(bytes)
                    assertTrue("Downloaded KayoAnime file was too short to identify", read == bytes.size)
                }
            } ?: throw AssertionError("Downloaded KayoAnime MKV could not be reopened")
            assertTrue(
                "Downloaded KayoAnime file is not an MKV/EBML stream",
                signature.contentEquals(
                    byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()),
                ),
            )

            Log.i(
                "NamiKayoSmoke",
                "downloaded=true mkv=${completed!!.displayName} mime=${completed!!.mimeType} " +
                    "bytes=$size ebml=true",
            )

            player = NamiVlcPlayer(context)
            player!!.play(
                ResolvedMedia(
                    url = contentUri!!,
                    mimeType = "video/x-matroska",
                    quality = "Offline MKV",
                ),
            )
            withTimeout(90_000) {
                while (
                    player!!.state.value.durationMs <= 0L &&
                    !player!!.state.value.isPlaying &&
                    player!!.state.value.error == null
                ) {
                    delay(200)
                }
            }
            assertTrue(
                "VLC rejected the downloaded KayoAnime MKV: ${player!!.state.value.error}",
                player!!.state.value.error == null &&
                    (player!!.state.value.durationMs > 0L || player!!.state.value.isPlaying),
            )

            Log.i(
                "NamiKayoSmoke",
                "query=Re:ZERO results=${results.size} anime=${details.title} " +
                    "driveFiles=${episodes.size} mkv=${mkvEpisode.title} " +
                    "download=${completed!!.displayName} bytes=$size offlineVlc=true",
            )

            manager.remove(completed!!)
            withTimeout(30_000) {
                while (manager.statuses.value.containsKey(key)) {
                    delay(100)
                }
            }
        } finally {
            player?.release()
            manager.statuses.value[key]?.let { leftover ->
                if (leftover.state != NamiDownloadState.DOWNLOADED || completed == null) {
                    manager.remove(leftover)
                }
            }
        }
    }
}
