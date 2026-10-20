package com.night.browser

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** A cheap, bounded file check; PDF rendering is separately validated by PdfRenderer. */
internal enum class ArchiveFileHealth { READY, MISSING, CORRUPT }

internal object TimeMachinePdfHealth {
    fun inspect(file: File?, expectedSize: Long = 0): ArchiveFileHealth {
        if (file?.isFile != true) return ArchiveFileHealth.MISSING
        return try {
            val size = file.length()
            if (size < 16 || size > TimeMachineLimits.MAX_PDF_BYTES ||
                (expectedSize > 0 && expectedSize != size)
            ) return ArchiveFileHealth.CORRUPT
            RandomAccessFile(file, "r").use { pdf ->
                val header = ByteArray(5)
                pdf.readFully(header)
                if (String(header, StandardCharsets.US_ASCII) != "%PDF-") {
                    return ArchiveFileHealth.CORRUPT
                }
                val tail = ByteArray(minOf(4096L, size).toInt())
                pdf.seek(size - tail.size)
                pdf.readFully(tail)
                if (!String(tail, StandardCharsets.US_ASCII).contains("%%EOF")) {
                    return ArchiveFileHealth.CORRUPT
                }
            }
            ArchiveFileHealth.READY
        } catch (_: Exception) {
            ArchiveFileHealth.CORRUPT
        }
    }
}