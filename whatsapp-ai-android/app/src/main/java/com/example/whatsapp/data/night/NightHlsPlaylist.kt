package com.example.whatsapp.data.night

import java.net.URI

data class NightHlsSegment(
    val url: String,
    val keyUrl: String?,
    val iv: ByteArray?,
    val durationSeconds: Double,
)

object NightHlsPlaylist {
    fun masterVariants(
        content: String,
        baseUrl: String,
    ): List<String> {
        val lines = content.lines()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (
                line.trim().startsWith("#EXT-X-STREAM-INF") &&
                index + 1 < lines.size
            ) {
                val next = lines[index + 1].trim()
                if (next.isNotBlank() && !next.startsWith("#")) {
                    out += resolveUrl(baseUrl, next)
                }
            }
        }
        return out
    }

    fun segments(
        content: String,
        baseUrl: String,
    ): List<NightHlsSegment> {
        val out = mutableListOf<NightHlsSegment>()
        var keyUrl: String? = null
        var explicitIv: ByteArray? = null
        var sequence = 0L
        var pendingDuration = 0.0

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequence =
                        line.substringAfter(":")
                            .toLongOrNull()
                            ?: sequence
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration =
                        line.substringAfter(":")
                            .substringBefore(",")
                            .toDoubleOrNull()
                            ?.coerceAtLeast(0.0)
                            ?: 0.0
                }

                line.startsWith("#EXT-X-KEY:") -> {
                    if (line.contains("METHOD=AES-128", true)) {
                        val uri =
                            Regex("""URI="([^"]+)"""")
                                .find(line)
                                ?.groupValues
                                ?.getOrNull(1)
                        keyUrl = uri?.let { resolveUrl(baseUrl, it) }
                        val ivHex =
                            Regex("""IV=0x([0-9a-fA-F]+)""")
                                .find(line)
                                ?.groupValues
                                ?.getOrNull(1)
                        explicitIv = ivHex?.let(::hexIv)
                    } else {
                        keyUrl = null
                        explicitIv = null
                    }
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val iv =
                        if (keyUrl != null) {
                            explicitIv ?: sequenceIv(sequence)
                        } else {
                            null
                        }
                    out +=
                        NightHlsSegment(
                            url = resolveUrl(baseUrl, line),
                            keyUrl = keyUrl,
                            iv = iv,
                            durationSeconds = pendingDuration,
                        )
                    pendingDuration = 0.0
                    sequence++
                }
            }
        }

        return out
    }

    private fun resolveUrl(
        base: String,
        child: String,
    ): String =
        URI(base).resolve(child).toString()

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (index in 15 downTo 0) {
            iv[index] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }

    private fun hexIv(hex: String): ByteArray {
        val padded = hex.padStart(32, '0').takeLast(32)
        return ByteArray(16) { index ->
            padded.substring(index * 2, index * 2 + 2)
                .toInt(16)
                .toByte()
        }
    }
}
