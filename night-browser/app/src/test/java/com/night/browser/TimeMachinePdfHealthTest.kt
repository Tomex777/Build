package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class TimeMachinePdfHealthTest {
    private fun pdf(bytes: String): File {
        val file = File.createTempFile("night-archive-", ".pdf")
        file.writeBytes(bytes.toByteArray(StandardCharsets.US_ASCII))
        file.deleteOnExit()
        return file
    }

    @Test fun completePdfIsReadable() {
        val input = pdf("%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n")
        assertEquals(ArchiveFileHealth.READY, TimeMachinePdfHealth.inspect(input))
        assertEquals(ArchiveFileHealth.READY,
            TimeMachinePdfHealth.inspect(input, input.length()))
    }

    @Test fun missingPdfIsFlaggedWithoutCrashing() {
        assertEquals(ArchiveFileHealth.MISSING,
            TimeMachinePdfHealth.inspect(File("/nonexistent/night-browser-copy.pdf")))
        assertEquals(ArchiveFileHealth.MISSING, TimeMachinePdfHealth.inspect(null))
    }

    @Test fun rejectedHeaderFooterAndTruncatedFiles() {
        val wrongHeader = pdf("NOT-A-PDF\n%%EOF\n")
        val noFooter = pdf("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n")
        val empty = pdf("")
        assertEquals(ArchiveFileHealth.CORRUPT, TimeMachinePdfHealth.inspect(wrongHeader))
        assertEquals(ArchiveFileHealth.CORRUPT, TimeMachinePdfHealth.inspect(noFooter))
        assertEquals(ArchiveFileHealth.CORRUPT, TimeMachinePdfHealth.inspect(empty))
    }

    @Test fun changedFileLengthIsDetectedBeforeExportOrOpen() {
        val input = pdf("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n")
        assertEquals(ArchiveFileHealth.CORRUPT,
            TimeMachinePdfHealth.inspect(input, input.length() + 10))
    }
}
