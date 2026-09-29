package com.night.sora.youtubemusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedYouTubeEngineHostTest {
    @Test
    fun resolveRefreshAndResumeUseOneStableRepresentation() = runBlocking {
        val videoId = "dQw4w9WgXcQ"
        val first = JSONArray(SharedYouTubeAudioResolver.resolveStreams(videoId)).getJSONObject(0)
        val identity = first.getString("stableIdentity")
        assertTrue(identity.isNotBlank())
        assertEquals("shared-youtube-engine", first.getString("resolverClient"))
        assertTrue(first.getString("url").contains("googlevideo.com"))

        val refreshed = JSONObject(SharedYouTubeAudioResolver.refreshStream(videoId, identity))
        assertEquals(identity, refreshed.getString("stableIdentity"))
        assertEquals("shared-youtube-engine", refreshed.getString("resolverClient"))

        val firstChunk = JSONObject(
            SharedYouTubeAudioResolver.fetchChunkCheckpoint(
                videoId, identity, startByte = 8192L, byteLimit = 4096
            )
        )
        assertEquals(identity, firstChunk.getString("stableIdentity"))
        assertEquals(8192L, firstChunk.getLong("startByte"))
        assertTrue("Host checkpoint request returned too few CDN bytes", firstChunk.getInt("bytesRead") >= 512)
        assertTrue(firstChunk.getString("contentRange").startsWith("bytes 8192-"))
        val persistedCheckpoint = firstChunk.getJSONObject("checkpoint")
        assertEquals(8192L + firstChunk.getInt("bytesRead"), persistedCheckpoint.getLong("nextByteOffset"))

        // Pass only serialized host checkpoint fields to a newly created engine instance. This
        // exercises the public persistence boundary and resume after engine/process recreation.
        val resumed = JSONObject(
            SharedYouTubeAudioResolver.resumeFromCheckpoint(persistedCheckpoint.toString(), byteLimit = 4096)
        )
        assertEquals(identity, resumed.getString("stableIdentity"))
        assertEquals(persistedCheckpoint.getLong("nextByteOffset"), resumed.getLong("startByte"))
        assertTrue("Recreated host engine returned too few resumed CDN bytes", resumed.getInt("bytesRead") >= 512)
        assertTrue(
            resumed.getString("contentRange").startsWith("bytes ${resumed.getLong("startByte")}-")
        )
        val nextCheckpoint = resumed.getJSONObject("checkpoint")
        assertEquals(resumed.getLong("startByte") + resumed.getInt("bytesRead"), nextCheckpoint.getLong("nextByteOffset"))

        println(
            "LYRA_ENGINE_HOST_PROOF status=SUPPORTED_AND_PROVEN " +
                "identity=$identity itag=${refreshed.optInt("itag")} " +
                "first=8192+${firstChunk.getInt("bytesRead")} " +
                "recreated=${resumed.getLong("startByte")}+${resumed.getInt("bytesRead")} " +
                "total=${nextCheckpoint.optLong("totalBytes")}"
        )
    }
}
