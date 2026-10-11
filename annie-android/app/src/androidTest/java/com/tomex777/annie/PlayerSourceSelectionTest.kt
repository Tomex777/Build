package com.tomex777.annie

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerSourceSelectionTest {
    private fun payload(): JSONObject = JSONObject()
        .put("type", "video")
        .put("headers", JSONObject().put("Referer", "https://watch.example/show"))
        .put("qualities", JSONArray()
            .put(JSONObject().put("label", "360p").put("url", "https://media.example/ep/360.mp4"))
            .put(JSONObject().put("label", "720p").put("url", "https://media.example/ep/720.mp4")
                .put("headers", JSONObject().put("Origin", "https://watch.example")))
            .put(JSONObject().put("label", "1080p").put("url", "https://media.example/ep/1080.mp4")))

    @Test fun explicitDefaultMatchesVideoDownloadSourceInsteadOfFirstQuality() {
        val data = payload().put("defaultQuality", "720p")
        val selected = requireNotNull(ScriptVideoDownloadSource.from(data))
        val sources = parsePlayerSources(data, Uri.parse(selected.url))
        assertEquals(selected.url, sources.first().uri.toString())
        assertEquals("720p", sources.first().label)
        assertEquals("https://watch.example/show", sources.first().headers["Referer"])
        assertEquals("https://watch.example", sources.first().headers["Origin"])
        assertEquals(3, sources.size)
    }

    @Test fun highestResolutionIsInitialPlayerSourceWithoutExplicitDefault() {
        val data = payload()
        val selected = requireNotNull(ScriptVideoDownloadSource.from(data))
        val sources = parsePlayerSources(data, Uri.parse(selected.url))
        assertEquals("1080p", sources.first().label)
        assertEquals(selected.url, sources.first().uri.toString())
        assertEquals("360p", sources[1].label)
        assertEquals("720p", sources[2].label)
    }

    @Test fun streamUrlAndUrlWorkWithoutExplicitFallback() {
        val stream = JSONObject().put("streamUrl", "https://media.example/show/live.m3u8")
        assertEquals("https://media.example/show/live.m3u8",
            parsePlayerSources(stream, null).single().uri.toString())
        val address = JSONObject().put("url", "https://media.example/show/episode.mp4")
        assertEquals("https://media.example/show/episode.mp4",
            parsePlayerSources(address, null).single().uri.toString())
    }

    @Test fun missingConfigurationUsesSuppliedOfflineFile() {
        val file = Uri.parse("file:///storage/emulated/0/Movies/test.mp4")
        assertEquals(file, parsePlayerSources(null, file).single().uri)
    }
}
