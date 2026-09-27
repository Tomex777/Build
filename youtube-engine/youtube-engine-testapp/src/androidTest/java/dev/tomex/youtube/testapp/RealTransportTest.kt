package dev.tomex.youtube.testapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.core.NativeYouTubeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A network integration gate: metadata and URL strings alone cannot pass. */
@RunWith(AndroidJUnit4::class)
class RealTransportTest {
    @Test fun searchPlayerAdaptiveBytesAndRefresh() = runBlocking {
        val engine = NativeYouTubeEngine()
        val results = engine.search("House MD")
        assertTrue("Search returned no videos", results.items.any { it is SearchResult.Video })
        println("YT_PROOF search=${results.items.size} first=${results.items.filterIsInstance<SearchResult.Video>().first().id}")

        val id = "dQw4w9WgXcQ" // Public 2160p video observed in the September 2026 live response.
        val resolved = engine.resolve(id)
        println("YT_PROOF player=${resolved.client} formats=${resolved.formats.size} diagnostics=${resolved.diagnostics}")
        val video = resolved.videoOnly.filter { (it.height ?: 0) >= 1080 }.maxByOrNull { it.height ?: 0 }
        val audio = resolved.audioOnly.maxByOrNull { it.bitrate ?: 0 }
        assertNotNull("No adaptive 1080p+ video descriptor", video)
        assertNotNull("No adaptive audio descriptor", audio)
        val videoProof = engine.probe(video!!)
        val audioProof = engine.probe(audio!!)
        println("YT_PROOF video itag=${video.itag} height=${video.height} $videoProof")
        println("YT_PROOF audio itag=${audio.itag} codec=${audio.codecs} $audioProof")
        assertTrue(videoProof.bytesRead >= 512)
        assertTrue(audioProof.bytesRead >= 512)

        val refreshed = engine.refreshMedia(id, video.stableIdentity)
        assertEquals(video.stableIdentity, refreshed.stableIdentity)
        val refreshProof = engine.probe(refreshed)
        println("YT_PROOF refresh itag=${refreshed.itag} $refreshProof")
        assertTrue(refreshProof.bytesRead >= 512)
    }
}
