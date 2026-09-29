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
    fun downloadsEntireAudioAndPinsIt() {
        runBlocking {
            val source = ExtensionMusicSource(context)
            var expectedLength = 0L
            var expectedKey = ""

            val firstCache = LyraAudioCache(context)
            try {
                firstCache.downloadKeyForTrack(track.id)?.let(firstCache::deleteDownload)

                val namespace = source.cacheNamespace()
                val candidate = source.resolveCandidates(track).getOrThrow().first()
                expectedLength = requireNotNull(candidate.contentLength) {
                    "Resolved stream must expose a stable content length"
                }
                assertTrue("Resolved stream is implausibly small", expectedLength > 512 * 1024)

                expectedKey = firstCache.download(
                    sourceId = namespace,
                    track = track,
                    audio = candidate.copy(
                        cacheKey = firstCache.cacheKey(namespace, track.id, candidate.mimeType, candidate.label),
                        cacheSourceId = namespace,
                    ),
                )

                val downloaded = firstCache.completeDownloadedVariant(track.id)
                assertNotNull("Download was not persisted as a complete offline variant", downloaded)
                val length = requireNotNull(downloaded!!.contentLength)
                val cachedBytes = firstCache.cachedBytes(expectedKey)

                assertEquals("Not every byte was cached", length, cachedBytes)
                assertEquals("Resolved content length changed during download", expectedLength, length)
                assertTrue("Download is not pinned", firstCache.isDownloaded(expectedKey))

                val offlineBytes = readWithoutNetwork(firstCache, downloaded)
                assertEquals("Cache-only read did not return the whole file", length, offlineBytes)

                Log.i(
                    TAG,
                    "LYRA_FULL_DOWNLOAD_PROOF track=${track.id} key=$expectedKey bytes=$cachedBytes " +
                        "offlineReadBytes=$offlineBytes mime=${downloaded.mimeType.orEmpty()}",
                )
            } finally {
                firstCache.release()
            }

            // Reopen the persisted cache from scratch before returning from instrumentation.
            // This proves the download is not only readable from the original SimpleCache instance.
            val reopenedCache = LyraAudioCache(context)
            try {
                val persisted = reopenedCache.completeDownloadedVariant(track.id)
                assertNotNull("Pinned download was lost after reopening the cache", persisted)
                val persistedLength = requireNotNull(persisted!!.contentLength)
                assertEquals("Persisted download length changed after cache reopen", expectedLength, persistedLength)
                assertTrue("Persisted download lost its pinned key", reopenedCache.isDownloaded(expectedKey))

                val reopenedBytes = readWithoutNetwork(reopenedCache, persisted)
                assertEquals("Reopened cache could not read the complete download without network", expectedLength, reopenedBytes)

                Log.i(
                    TAG,
                    "LYRA_CACHE_REOPEN_PROOF track=${track.id} key=$expectedKey bytes=$reopenedBytes",
                )
            } finally {
                reopenedCache.release()
            }
        }
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
