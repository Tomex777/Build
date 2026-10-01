package com.tomex777.annie

import java.net.URLDecoder
import java.util.Locale

/** Filename and MIME decisions never decide whether bytes may be downloaded. */
internal object DownloadFileMetadata {
    private val mimeByExtension = mapOf(
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "ts" to "video/mp2t", "mov" to "video/quicktime", "m4v" to "video/mp4",
        "avi" to "video/x-msvideo", "3gp" to "video/3gpp", "3g2" to "video/3gpp2",
        "ogv" to "video/ogg", "flv" to "video/x-flv",
        "mp3" to "audio/mpeg", "aac" to "audio/aac", "m4a" to "audio/mp4",
        "ogg" to "audio/ogg", "opus" to "audio/opus", "flac" to "audio/flac",
        "wav" to "audio/wav", "aiff" to "audio/aiff", "wma" to "audio/x-ms-wma",
        "pdf" to "application/pdf", "zip" to "application/zip", "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar", "cbz" to "application/vnd.comicbook+zip",
        "epub" to "application/epub+zip", "apk" to "application/vnd.android.package-archive",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "js" to "text/javascript", "json" to "application/json", "txt" to "text/plain",
        "srt" to "application/x-subrip", "vtt" to "text/vtt", "ttf" to "font/ttf", "otf" to "font/otf",
    )

    fun extension(filename: String?): String? = filename?.substringAfterLast('.', "")
        ?.lowercase(Locale.ROOT)?.takeIf { it.matches(Regex("[a-z0-9][a-z0-9_-]{0,31}")) }

    fun extensionForMime(mime: String?): String? = when (normalizedMime(mime)) {
        "audio/webm" -> "webm"
        "audio/x-aac" -> "aac"
        "audio/x-m4a" -> "m4a"
        "audio/x-flac" -> "flac"
        "audio/x-wav", "audio/wave" -> "wav"
        "audio/x-aiff" -> "aiff"
        "application/ogg" -> "ogg"
        "video/mkv" -> "mkv"
        "video/mpegts" -> "ts"
        else -> mimeByExtension.entries.firstOrNull { it.value == normalizedMime(mime) }?.key
    }

    fun normalizedMime(value: String?): String? = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) }

    fun mime(server: String?, supplied: String?, filename: String): String {
        val candidates = listOfNotNull(normalizedMime(server), normalizedMime(supplied))
        return candidates.firstOrNull { it !in setOf("application/octet-stream", "binary/octet-stream") }
            ?: mimeByExtension[extension(filename)] ?: "application/octet-stream"
    }

    fun filename(disposition: String?, supplied: String?, url: String, mime: String?): String {
        val extended = Regex("""(?:^|;)\s*filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition.orEmpty())?.groupValues?.get(1)?.trim()?.trim('"')
            ?.let { raw ->
                val parts = raw.split('\'', limit = 3)
                if (parts.size != 3 || parts[0].uppercase(Locale.ROOT) !in setOf("UTF-8", "ISO-8859-1")) null
                else runCatching { URLDecoder.decode(parts[2].replace("+", "%2B"), parts[0]) }.getOrNull()
            }
        val plain = Regex("""(?:^|;)\s*filename\s*=\s*(?:"([^"]*)"|([^;]*))""", RegexOption.IGNORE_CASE)
            .find(disposition.orEmpty())?.let { it.groupValues[1].ifBlank { it.groupValues[2].trim() } }
        val name = listOf(extended, plain, supplied, AnnieDownloadNaming.urlFilename(url))
            .firstOrNull { !it.isNullOrBlank() }
        val safe = name?.let(AnnieDownloadNaming::sanitize)
        // Preserve arbitrary extensions, including formats Annie has never heard of.
        if (safe != null && safe.substringAfterLast('.', "").isNotEmpty() && '.' in safe) return boundedFilename(safe)
        val inferred = extensionForMime(mime)
        return boundedFilename(if (safe != null) safe + (inferred?.let { ".$it" } ?: "")
            else "download.${inferred ?: "bin"}")
    }

    private fun boundedFilename(name: String): String {
        // Android filesystems limit a component to 255 bytes, not 255 characters.
        if (name.toByteArray(Charsets.UTF_8).size <= 240) return name
        val suffix = name.substringAfterLast('.', "").takeIf { '.' in name && it.toByteArray(Charsets.UTF_8).size <= 80 }
            ?.let { ".$it" }.orEmpty()
        val budget = 240 - suffix.toByteArray(Charsets.UTF_8).size
        val stem = if (suffix.isNotEmpty()) name.dropLast(suffix.length) else name
        var end = 0
        var bytes = 0
        while (end < stem.length) {
            val count = Character.charCount(stem.codePointAt(end))
            val length = stem.substring(end, end + count).toByteArray(Charsets.UTF_8).size
            if (bytes + length > budget) break
            bytes += length
            end += count
        }
        return stem.substring(0, end).trimEnd() + suffix
    }
}
