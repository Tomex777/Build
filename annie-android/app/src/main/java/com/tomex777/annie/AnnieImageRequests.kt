package com.tomex777.annie

/** Decode bounded inline image data into the byte model understood by Coil. */
internal fun annieImageRequestData(source: String?): Any? {
    source ?: return null
    if (!source.startsWith("data:", ignoreCase = true)) return source
    if (source.length > 8 * 1024 * 1024) return null
    val separator = source.indexOf(',')
    if (separator < 0) return null
    val metadata = source.substring(5, separator).split(';')
    val supported = setOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif",
        "image/bmp", "image/avif", "image/heic", "image/heif")
    if (metadata.first().lowercase() !in supported || !metadata.last().equals("base64", true)) return null
    return try {
        java.util.Base64.getDecoder().decode(source.substring(separator + 1)).takeIf { it.isNotEmpty() }
    } catch (_: IllegalArgumentException) {
        null
    }
}
