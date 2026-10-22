package com.nice.juicewrld

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Explicitly approved/licensed direct-audio files only. No search-engine,
 * YouTube, Spotify or DRM extraction. Catalog ships empty until a rights
 * holder grants permission for an individual master recording.
 */
internal data class LicensedAudio(val songId: String, val url: String, val sha256: String)

internal object LicensedAudioCatalog {
    fun find(context: Context, id: String): LicensedAudio? = try {
        if (FanSongs.find(id) == null) null else {
            val raw = context.assets.open("licensed_audio.json")
                .bufferedReader().use { it.readText() }
            val items = JSONArray(raw)
            (0 until items.length()).asSequence().mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val song = item.optString("songId")
                val url = item.optString("httpsUrl")
                val digest = item.optString("sha256")
                if (song != id || !url.startsWith("https://") ||
                    !digest.matches(Regex("[a-fA-F0-9]{64}"))) null
                else LicensedAudio(song, url, digest.lowercase())
            }.firstOrNull()
        }
    } catch (_: Exception) { null }

    fun download(context: Context, item: LicensedAudio): Result<File> = runCatching {
        val connection = URL(item.url).openConnection() as HttpURLConnection
        val dir = File(context.filesDir, "licensed_music").apply { mkdirs() }
        val temporary = File(dir, item.songId + ".part")
        val destination = File(dir, item.songId + ".audio")
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.connect()
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Audio download HTTP ${connection.responseCode}"
            }
            val contentType = connection.contentType.orEmpty().substringBefore(';')
            require(contentType.startsWith("audio/") ||
                contentType == "application/octet-stream") { "Not an audio recording" }
            require(connection.contentLengthLong in 1..(100L * 1024L * 1024L)) {
                "Audio size unavailable or too large"
            }
            val sha = MessageDigest.getInstance("SHA-256")
            var readTotal = 0L
            connection.inputStream.use { source ->
                temporary.outputStream().use { dest ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        readTotal += n
                        require(readTotal <= 100L * 1024L * 1024L) { "Audio too large" }
                        sha.update(buffer, 0, n)
                        dest.write(buffer, 0, n)
                    }
                }
            }
            require(readTotal == connection.contentLengthLong) { "Incomplete audio" }
            require(sha.digest().joinToString("") { "%02x".format(it) } == item.sha256) {
                "Audio integrity check failed"
            }
            // App-private only; nothing is leaked to publicly shared Downloads.
            require(temporary.renameTo(destination)) { "Could not save audio" }
            destination
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }
}
