package com.night.spotui.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.spotui.ExtensionMusicSource
import com.night.spotui.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyraAudioDownloadTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val track = Track(
        id = "dQw4w9WgXcQ",
        title = "Never Gonna Give You Up",
        artist = "Rick Astley",
    )

    @Test
    fun downloadsEntireAudioAndPinsIt() = runBlocking {
        val source = ExtensionMusicSource(context)
        val cache = LyraAudioCache(context)

        cache.downloadKeyForTrack(track.id)?.let(cache::deleteDownload)

        val namespace = source.cacheNamespace()
        val candidate = source.resolveCandidates(track).getOrThrow().first()
        val expectedLength = candidate.contentLength
        assertNotNull("Resolved stream must expose a stable content length", expectedLength)
        assertTrue("Resolved stream is implausibly small", expectedLength!! > 512 * 1024)

        val key = cache.download(
            sourceId = namespace,
            track = track,
            audio = candidate.copy(
                cacheKey = cache.cacheKey(namespace, track.id, candidate.mimeType, candidate.label),
                cacheSourceId = namespace,
            ),
        )

        val downloaded = cache.completeDownloadedVariant(track.id)
        assertNotNull("Download was not persisted as a complete offline variant", downloaded)
        val length = downloaded!!.contentLength
        assertNotNull("Offline variant lost its content length", length)

        val cachedBytes = cache.cachedBytes(key)
        assertEquals("Not every byte was cached", length, cachedBytes)
        assertEquals("Resolved content length changed during download", expectedLength, length)
        assertTrue("Download is not pinned", cache.isDownloaded(key))

        val offlineBytes = readWithoutNetwork(cache, downloaded)
        assertEquals("Cache-only read did not return the whole file", length, offlineBytes)

        Log.i(
            TAG,
            "LYRA_FULL_DOWNLOAD_PROOF track=${track.id} key=$key bytes=$cachedBytes " +
                "offlineReadBytes=$offlineBytes mime=${downloaded.mimeType.orEmpty()}",
        )
    }

    @Test
    fun readsPinnedDownloadWithSourceExtensionUnavailable() {
        val cache = LyraAudioCache(context)
        val downloaded = cache.completeDownloadedVariant(track.id)
        assertNotNull(
            "Pinned download disappeared after the source extension was removed",
            downloaded,
        )
        val length = downloaded!!.contentLength
        assertNotNull("Offline download has no known length", length)

        val offlineBytes = readWithoutNetwork(cache, downloaded)
        assertEquals("Offline read attempted to miss the cache", length, offlineBytes)

        Log.i(
            TAG,
            "LYRA_OFFLINE_DOWNLOAD_PROOF track=${track.id} bytes=$offlineBytes sourceInstalled=false",
        )
    }

    private fun readWithoutNetwork(cache: LyraAudioCache, audio: com.night.spotui.ResolvedAudio): Long {
        val noNetwork = object : DataSource.Factory {
            override fun createDataSource(): DataSource = object : DataSource {
                override fun addTransferListener(transferListener: TransferListener) = Unit
                override fun open(dataSpec: DataSpec): Long =
                    throw AssertionError("Offline cache attempted network access: ${dataSpec.uri}")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    throw AssertionError("Offline cache attempted network read")
                override fun getUri(): Uri? = null
                override fun close() = Unit
            }
        }

        val source = cache.cacheDataSourceFactory(noNetwork).createDataSource()
        val length = requireNotNull(audio.contentLength)
        val key = requireNotNull(audio.cacheKey)
        val opened = source.open(
            DataSpec.Builder()
                .setUri(Uri.parse(audio.url))
                .setKey(key)
                .setPosition(0L)
                .setLength(length)
                .build(),
        )
        assertEquals("Cache-only source opened with a different length", length, opened)

        var total = 0L
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                assertTrue("Cache-only source returned an invalid byte count", read > 0)
                total += read
            }
        } finally {
            source.close()
        }
        return total
    }

    companion object {
        private const val TAG = "LyraDownloadTest"
    }
}
