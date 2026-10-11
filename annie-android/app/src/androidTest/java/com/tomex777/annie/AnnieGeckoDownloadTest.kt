package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieGeckoDownloadTest {
    @Test fun downloadNameUsesContentDispositionAndSanitizesTraversal() {
        assertEquals("report.pdf", AnnieGeckoDownloadStore.filename(
            "https://files.example/download", "attachment; filename=\"../report.pdf\"", "application/pdf"))
    }

    @Test fun encodedUnicodeNamePreservesExpectedExtension() {
        assertEquals("my report.pdf", AnnieGeckoDownloadStore.filename(
            "blob:https://files.example/123", "attachment; filename*=UTF-8''my%20report.pdf", "application/pdf"))
    }

    @Test fun filenameFallsBackToUriOrMimetype() {
        assertEquals("picture.png", AnnieGeckoDownloadStore.filename(
            "https://files.example/picture", null, "image/png"))
        assertEquals("file.txt", AnnieGeckoDownloadStore.filename(
            "https://files.example/file.txt", null, "text/plain"))
    }

    @Test fun maliciousFilenameDoesNotContainPathSeparators() {
        assertEquals("file.txt", AnnieGeckoDownloadStore.filename(
            "https://files.example/get", "attachment; filename=\"secret\\\\file.txt\"", "text/plain"))
    }
}
