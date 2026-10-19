package com.night.tachymns

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Section-safe audio identity: Redemption hymn 1 and Àkọkún hymn 1 are different tracks.
 * A different language or vocal arrangement is always a different recording.
 */
data class AudioKey(
    val collection: String,
    val number: Int,
    val language: String,
    val voice: String
) {
    init {
        require(collection == "main" || collection == "akokun")
        require(number in 1..(if (collection == "akokun") 305 else 800))
        require(language == "yo" || language == "en")
        require(voice == "solo" || voice == "choir")
    }

    val token: String get() = "${collection}:${number}:${language}:${voice}"
    val hymnKey: String get() = "$collection:$number"

    companion object {
        fun parse(value: String?): AudioKey? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 4) return null
            return runCatching {
                AudioKey(parts[0], parts[1].toInt(), parts[2], parts[3])
            }.getOrNull()
        }
    }
}

/**
 * Only approved, attributable recordings are eligible for remote download.
 * An empty audio catalogue is intentional: never ship arbitrary recordings
 * or pretend that the hymn's matched title guarantees its sung version.
 */
data class ApprovedRecording(
    val key: AudioKey,
    val url: String,
    val sha256: String,
    val credit: String,
    val rights: String
)

sealed class AudioSource {
    data class Imported(val uri: Uri) : AudioSource()
    data class Downloaded(val file: File) : AudioSource()
}

class HymnAudioRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("hymn_recordings", Context.MODE_PRIVATE)
    private val tracks: Map<String, ApprovedRecording> = loadCatalog()

    private fun loadCatalog(): Map<String, ApprovedRecording> {
        val catalog = JSONObject(
            context.assets.open("audio_catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ).getJSONArray("recordings")
        val result = linkedMapOf<String, ApprovedRecording>()
        for (i in 0 until catalog.length()) {
            val row = catalog.getJSONObject(i)
            if (!row.optBoolean("rights_verified", false)) continue
            val key = AudioKey(
                row.getString("collection"),
                row.getInt("number"),
                row.getString("language"),
                row.getString("voice")
            )
            val url = row.getString("url")
            val hash = row.getString("sha256").lowercase()
            val credit = row.getString("credit").trim()
            val rights = row.getString("rights").trim()
            require(url.startsWith("https://") && !url.contains('@')) { "Audio URL must be HTTPS" }
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "Missing audio integrity hash" }
            require(credit.isNotBlank() && rights.isNotBlank()) { "Audio rights attribution required" }
            require(result.put(key.token, ApprovedRecording(key, url, hash, credit, rights)) == null) {
                "Duplicate audio recording: ${key.token}"
            }
        }
        return result
    }

    fun recording(key: AudioKey): ApprovedRecording? = tracks[key.token]

    private fun downloadedFile(key: AudioKey): File {
        val dir = File(context.filesDir, "recordings")
        return File(dir, key.token.replace(':', '_') + ".mp3")
    }

    fun source(key: AudioKey): AudioSource? {
        // User-chosen recordings take precedence over bundled catalogue downloads.
        prefs.getString("import:${key.token}", null)?.let { stored ->
            return AudioSource.Imported(Uri.parse(stored))
        }
        val file = downloadedFile(key)
        return if (file.isFile && file.length() > 0L) AudioSource.Downloaded(file) else null
    }

    fun hasRecording(hymn: Hymn): Boolean =
        listOf("yo", "en").any { language ->
            listOf("solo", "choir").any { voice ->
                val key = AudioKey(hymn.collection, hymn.number, language, voice)
                source(key) != null || recording(key) != null
            }
        }

    fun import(key: AudioKey, uri: Uri) {
        require(uri.scheme == "content") { "Select an audio file from your device" }
        prefs.edit().putString("import:${key.token}", uri.toString()).apply()
    }

    fun clearImport(key: AudioKey) {
        prefs.edit().remove("import:${key.token}").apply()
    }

    suspend fun download(key: AudioKey) = withContext(Dispatchers.IO) {
        val track = recording(key) ?: error("No approved download for this recording")
        val destination = downloadedFile(key)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(track.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "audio/mpeg")
            }
            require(connection.responseCode == 200) { "Recording download failed" }
            val mime = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
            require(mime == "audio/mpeg" || mime == "audio/mp3" || mime == "application/octet-stream") {
                "Downloaded file is not MP3 audio"
            }
            val maxBytes = 40L * 1024L * 1024L
            var bytes = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        bytes += n
                        require(bytes <= maxBytes) { "Audio recording exceeds 40 MB" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            require(bytes > 0) { "Downloaded recording is empty" }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == track.sha256) { "Recording verification failed" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        } finally {
            connection?.disconnect()
        }
    }
}

/** A single real MediaPlayer instance, shared by the ordinary reader and optional Learning Mode. */
class HymnAudioPlayer(private val context: Context) {
    private var media: MediaPlayer? = null

    var activeKey by mutableStateOf<AudioKey?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var rate by mutableStateOf(1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var startMark by mutableStateOf<Int?>(null)
        private set
    var endMark by mutableStateOf<Int?>(null)
        private set
    var loopEnabled by mutableStateOf(false)
        private set

    fun toggle(key: AudioKey, source: AudioSource) {
        if (activeKey == key && !loading && media != null) {
            try {
                if (playing) {
                    media?.pause()
                    playing = false
                } else {
                    media?.start()
                    applyRate()
                    playing = true
                }
            } catch (e: Exception) {
                fail("Unable to play this recording")
            }
            return
        }
        stop()
        activeKey = key
        loading = true
        error = null
        try {
            val player = MediaPlayer()
            media = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            when (source) {
                is AudioSource.Imported -> player.setDataSource(context, source.uri)
                is AudioSource.Downloaded -> player.setDataSource(source.file.absolutePath)
            }
            player.setOnPreparedListener { prepared ->
                if (media !== prepared) return@setOnPreparedListener
                durationMs = prepared.duration.coerceAtLeast(0)
                loading = false
                try {
                    prepared.start()
                    applyRate()
                    playing = true
                } catch (_: Exception) {
                    fail("Unable to start this recording")
                }
            }
            player.setOnCompletionListener { completed ->
                if (media !== completed) return@setOnCompletionListener
                val start = startMark
                if (loopEnabled && start != null) {
                    completed.seekTo(start)
                    completed.start()
                    applyRate()
                    playing = true
                } else {
                    playing = false
                    positionMs = durationMs
                }
            }
            player.setOnErrorListener { _, _, _ ->
                fail("This audio file could not be played")
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            fail("This recording cannot be opened. Choose another audio file.")
        }
    }

    private fun applyRate() {
        try {
            media?.playbackParams = PlaybackParams().setSpeed(rate)
        } catch (_: Exception) {
            // Keep normal-speed playback available if a device cannot time-stretch audio.
        }
    }

    fun changeSpeed(value: Float) {
        rate = value.coerceIn(0.65f, 1.25f)
        if (playing) applyRate()
    }

    fun seekTo(ms: Int) {
        val player = media ?: return
        if (loading) return
        val position = ms.coerceIn(0, durationMs)
        runCatching { player.seekTo(position) }
        positionMs = position
    }

    fun markStart() {
        startMark = positionMs
        if (endMark != null && endMark!! <= positionMs) endMark = null
        loopEnabled = false
    }

    fun markEnd() {
        if (startMark != null && positionMs > startMark!! + 250) {
            endMark = positionMs
            loopEnabled = true
        }
    }

    fun clearLoop() {
        startMark = null
        endMark = null
        loopEnabled = false
    }

    fun refresh() {
        val player = media ?: return
        if (loading || !playing) return
        try {
            val current = player.currentPosition.coerceAtLeast(0)
            val from = startMark
            val until = endMark
            if (loopEnabled && from != null && until != null && current >= until) {
                player.seekTo(from)
                positionMs = from
            } else {
                positionMs = current
            }
        } catch (_: Exception) {
            fail("Playback stopped")
        }
    }

    private fun fail(message: String) {
        stop()
        error = message
    }

    fun stop() {
        media?.let { runCatching { it.reset(); it.release() } }
        media = null
        activeKey = null
        playing = false
        loading = false
        positionMs = 0
        durationMs = 0
        clearLoop()
    }

    fun release() = stop()
}
