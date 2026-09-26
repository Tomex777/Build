package com.night.spotui.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.night.spotui.ResolvedAudio
import com.night.spotui.Track
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Shared byte store for streaming, offline downloads, and offline playback. */
@UnstableApi
class LyraAudioCache(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("lyra-audio-cache", Context.MODE_PRIVATE)
    private val pinnedKeys = preferences.getStringSet(PREF_PINNED_KEYS, emptySet()).orEmpty().toMutableSet()
    private val activeKey = AtomicReference<String?>(null)
    private val inProgressDownloads = ConcurrentHashMap.newKeySet<String>()
    private val evictor = PinnedLruCacheEvictor(MAX_CACHE_BYTES, pinnedKeys, activeKey, inProgressDownloads)
    private val cache = SimpleCache(
        File(appContext.filesDir, "lyra-audio-cache"),
        evictor,
        StandaloneDatabaseProvider(appContext),
    ).also(evictor::initialize)
    private val downloadNetworkSource = ChunkedDataSource.Factory(
        DefaultDataSource.Factory(
            appContext,
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000),
        )
    )

    fun cacheKey(sourceId: String, trackId: String, mimeType: String?, quality: String): String =
        "lyra-audio-v1-" + sha256(listOf(sourceId, trackId, mimeType.orEmpty(), quality).joinToString("\u0000"))

    fun rememberVariant(sourceId: String, track: Track, audio: ResolvedAudio): String {
        val key = audio.cacheKey ?: cacheKey(sourceId, track.id, audio.mimeType, audio.label)
        val length = audio.contentLength?.takeIf { it > 0L } ?: C.LENGTH_UNSET.toLong()
        preferences.edit().putString(
            entryKey(sourceId, track.id),
            JSONObject()
                .put("key", key)
                .put("mimeType", audio.mimeType.orEmpty())
                .put("label", audio.label)
                .put("length", length)
                .put("sourceId", sourceId)
                .put("trackId", track.id)
                .put("title", track.title)
                .put("artist", track.artist)
                .put("artistId", track.artistId)
                .put("album", track.album)
                .put("albumId", track.albumId)
                .put("artworkUrl", track.artworkUrl.orEmpty())
                .put("durationSeconds", track.durationSeconds)
                .put("explicit", track.explicit)
                .toString(),
        ).apply()
        return key
    }

    /** Returns a cache-only stream only when every byte is present and its length is known. */
    fun completeCachedVariant(sourceId: String, trackId: String): ResolvedAudio? {
        val raw = preferences.getString(entryKey(sourceId, trackId), null) ?: return null
        val entry = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val key = entry.optString("key").takeIf(String::isNotBlank) ?: return null
        activeKey.set(key)
        val contentLength = cache.getContentMetadata(key)
            .get(ContentMetadata.KEY_CONTENT_LENGTH, entry.optLong("length", C.LENGTH_UNSET.toLong()))
        if (contentLength <= 0L || !cache.isCached(key, 0L, contentLength)) {
            activeKey.compareAndSet(key, null)
            return null
        }
        val mimeType = entry.optString("mimeType").takeIf(String::isNotBlank)
        val extension = when {
            mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
            mimeType?.contains("mp4", ignoreCase = true) == true -> "m4a"
            else -> "audio"
        }
        return ResolvedAudio(
            url = Uri.Builder()
                .scheme("https")
                .authority("lyra-cache.invalid")
                .appendPath("$key.$extension")
                .appendQueryParameter("clen", contentLength.toString())
                .build()
                .toString(),
            label = entry.optString("label", "Audio"),
            mimeType = mimeType,
            contentLength = contentLength,
            cacheKey = key,
            cacheSourceId = sourceId,
            fromCache = true,
        )
    }

    fun cacheDataSourceFactory(upstream: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { dataSpec ->
                dataSpec.key ?: dataSpec.uri.toString()
            }

    fun protectForPlayback(key: String?) {
        activeKey.set(key)
    }

    fun isDownloaded(key: String): Boolean = synchronized(pinnedKeys) { key in pinnedKeys }

    fun downloadKeyForTrack(trackId: String): String? = synchronized(pinnedKeys) {
        val entries = JSONObject(preferences.getString(PREF_DOWNLOADS, "{}") ?: "{}")
        pinnedKeys.firstOrNull { key -> entries.optJSONObject(key)?.optString("id") == trackId }
    }

    fun cachedBytes(key: String): Long = cache.getCachedSpans(key).sumOf(CacheSpan::length)

    fun downloadedTracks(): List<Track> = synchronized(pinnedKeys) {
        val entries = JSONObject(preferences.getString(PREF_DOWNLOADS, "{}") ?: "{}")
        pinnedKeys.mapNotNull { key -> entries.optJSONObject(key)?.toTrack() }
    }

    fun completeDownloadedVariant(trackId: String): ResolvedAudio? = synchronized(pinnedKeys) {
        val entries = JSONObject(preferences.getString(PREF_DOWNLOADS, "{}") ?: "{}")
        pinnedKeys.firstNotNullOfOrNull { key ->
            val entry = entries.optJSONObject(key) ?: return@firstNotNullOfOrNull null
            if (entry.optString("id") != trackId) return@firstNotNullOfOrNull null
            val length = entry.optLong("length", C.LENGTH_UNSET.toLong())
            if (length <= 0L || !cache.isCached(key, 0L, length)) return@firstNotNullOfOrNull null
            activeKey.set(key)
            val mimeType = entry.optString("mimeType").takeIf(String::isNotBlank)
            val extension = if (mimeType?.contains("webm", true) == true) "webm" else "m4a"
            ResolvedAudio(
                url = Uri.Builder()
                    .scheme("https")
                    .authority("lyra-cache.invalid")
                    .appendPath("$key.$extension")
                    .appendQueryParameter("clen", length.toString())
                    .build()
                    .toString(),
                label = entry.optString("label", "Audio"),
                mimeType = mimeType,
                contentLength = length,
                cacheKey = key,
                cacheSourceId = entry.optString("sourceId"),
                fromCache = true,
            )
        }
    }

    /** CacheWriter sees existing spans first, so it fetches only holes in the shared cache. */
    suspend fun download(
        sourceId: String,
        track: Track,
        audio: ResolvedAudio,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val key = rememberVariant(sourceId, track, audio)
        val uri = Uri.parse(audio.url)
        val requestLength = audio.contentLength?.takeIf { it > 0L }
            ?: cache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong())
        val spec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(0L)
            .setLength(requestLength)
            .setKey(key)
            .setHttpRequestHeaders(audio.headers)
            .build()
        val cacheDataSource = cacheDataSourceFactory(downloadNetworkSource).createDataSource() as CacheDataSource
        val job = currentCoroutineContext()[Job]
        lateinit var writer: CacheWriter
        writer = CacheWriter(
            cacheDataSource,
            spec,
            ByteArray(DEFAULT_CACHE_BUFFER_BYTES),
        ) { requestLength, bytesCached, _ ->
            if (job?.isActive == false) writer.cancel()
            onProgress(bytesCached, requestLength)
        }
        inProgressDownloads.add(key)
        try {
            writer.cache()
            val knownLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                .takeIf { it > 0L }
                ?: cachedBytes(key)
            val completeAudio = audio.copy(contentLength = knownLength, cacheKey = key)
            rememberVariant(sourceId, track, completeAudio)
            pinDownload(key, sourceId, track, completeAudio)
            key
        } finally {
            inProgressDownloads.remove(key)
            runCatching { cacheDataSource.close() }
        }
    }

    fun deleteDownload(key: String) {
        synchronized(pinnedKeys) {
            pinnedKeys.remove(key)
            preferences.edit().putStringSet(PREF_PINNED_KEYS, pinnedKeys.toSet()).apply()
            val downloads = JSONObject(preferences.getString(PREF_DOWNLOADS, "{}") ?: "{}")
            downloads.remove(key)
            preferences.edit().putString(PREF_DOWNLOADS, downloads.toString()).apply()
        }
        cache.removeResource(key)
    }

    private fun pinDownload(key: String, sourceId: String, track: Track, audio: ResolvedAudio) {
        synchronized(pinnedKeys) {
            pinnedKeys.add(key)
            preferences.edit().putStringSet(PREF_PINNED_KEYS, pinnedKeys.toSet()).apply()
            val downloads = JSONObject(preferences.getString(PREF_DOWNLOADS, "{}") ?: "{}")
            downloads.put(
                key,
                JSONObject()
                    .put("sourceId", sourceId)
                    .put("key", key)
                    .put("mimeType", audio.mimeType.orEmpty())
                    .put("label", audio.label)
                    .put("length", audio.contentLength ?: C.LENGTH_UNSET.toLong())
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("artistId", track.artistId)
                    .put("album", track.album)
                    .put("albumId", track.albumId)
                    .put("artworkUrl", track.artworkUrl.orEmpty())
                    .put("durationSeconds", track.durationSeconds)
                    .put("explicit", track.explicit),
            )
            preferences.edit().putString(PREF_DOWNLOADS, downloads.toString()).apply()
        }
    }

    private fun entryKey(sourceId: String, trackId: String): String =
        "entry-" + sha256("$sourceId\u0000$trackId")

    private fun JSONObject.toTrack(): Track = Track(
        id = optString("id"),
        title = optString("title"),
        artist = optString("artist"),
        artistId = optString("artistId"),
        album = optString("album"),
        albumId = optString("albumId"),
        artworkUrl = optString("artworkUrl").takeIf(String::isNotBlank),
        durationSeconds = optLong("durationSeconds"),
        explicit = optBoolean("explicit"),
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val PREF_PINNED_KEYS = "pinned_download_keys"
        private const val PREF_DOWNLOADS = "downloaded_track_metadata"
        private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
        private const val DEFAULT_CACHE_BUFFER_BYTES = 128 * 1024
    }
}

@UnstableApi
private class PinnedLruCacheEvictor(
    private val maxBytes: Long,
    private val pinnedKeys: MutableSet<String>,
    private val activeKey: AtomicReference<String?>,
    private val inProgressDownloads: MutableSet<String>,
) : CacheEvictor {
    private val spans = mutableListOf<CacheSpan>()

    override fun onCacheInitialized() = Unit

    @Synchronized
    fun initialize(cache: androidx.media3.datasource.cache.Cache) {
        spans.clear()
        cache.keys.forEach { key -> spans.addAll(cache.getCachedSpans(key)) }
        evict(cache, 0L)
    }

    @Synchronized
    override fun onStartFile(cache: androidx.media3.datasource.cache.Cache, key: String, position: Long, length: Long) {
        evict(cache, length.coerceAtLeast(0L))
    }

    @Synchronized
    override fun onSpanAdded(cache: androidx.media3.datasource.cache.Cache, span: CacheSpan) {
        spans.removeAll { it.file == span.file }
        spans.add(span)
        evict(cache, 0L)
    }

    @Synchronized
    override fun onSpanRemoved(cache: androidx.media3.datasource.cache.Cache, span: CacheSpan) {
        spans.removeAll { it.file == span.file }
    }

    @Synchronized
    override fun onSpanTouched(
        cache: androidx.media3.datasource.cache.Cache,
        oldSpan: CacheSpan,
        newSpan: CacheSpan,
    ) {
        spans.removeAll { it.file == oldSpan.file }
        spans.add(newSpan)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    private fun evict(cache: androidx.media3.datasource.cache.Cache, bytesToFree: Long) {
        while (cache.cacheSpace + bytesToFree > maxBytes) {
            val protected = activeKey.get()
            val candidate = synchronized(pinnedKeys) {
                spans.asSequence()
                    .filterNot {
                        it.key == protected || it.key in pinnedKeys || it.key in inProgressDownloads
                    }
                    .minByOrNull(CacheSpan::lastTouchTimestamp)
            } ?: return
            runCatching { cache.removeSpan(candidate) }
            spans.removeAll { it.file == candidate.file }
        }
    }
}
