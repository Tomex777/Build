package app.nami.android

import android.content.Intent
import android.provider.Browser
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.domain.MediaTrack
import app.nami.domain.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPlayerLauncherTest {

    @Test
    fun playerIntentPreservesResolvedUrlMimeHeadersAndSubtitle() {
        val intent = ExternalPlayerLauncher.buildIntent(
            ResolvedMedia(
                url = "https://cdn.example/episode.mkv",
                mimeType = "video/x-matroska",
                headers = mapOf(
                    "Referer" to "https://source.example/",
                    "User-Agent" to "Nami-Test",
                    "X-Test" to "yes",
                ),
                subtitles = listOf(
                    MediaTrack(
                        url = "https://cdn.example/subtitle.vtt",
                        language = "English",
                    ),
                ),
            ),
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://cdn.example/episode.mkv", intent.dataString)
        assertEquals("video/x-matroska", intent.type)
        assertEquals("https://source.example/", intent.getStringExtra("http-referrer"))
        assertEquals("Nami-Test", intent.getStringExtra("user-agent"))
        assertEquals(
            "https://cdn.example/subtitle.vtt",
            intent.getStringExtra("subtitles_location"),
        )

        val headers = intent.getBundleExtra(Browser.EXTRA_HEADERS)
        assertEquals("yes", headers?.getString("X-Test"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
