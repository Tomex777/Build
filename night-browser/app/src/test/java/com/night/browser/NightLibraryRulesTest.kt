package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLibraryRulesTest {
    @Test fun onlyHttpWebURLsAccepted() {
        assertTrue(LibraryRules.validWebUrl("https://example.org/a.pdf"))
        assertTrue(LibraryRules.validWebUrl("http://example.org"))
        assertFalse(LibraryRules.validWebUrl("javascript:alert(1)"))
        assertFalse(LibraryRules.validWebUrl("file:///private"))
    }

    @Test fun bookmarkSearchMatchesFolderTitleOrAddress() {
        assertTrue(LibraryRules.matches("Science", "Space", "https://example.org", "Science"))
        assertTrue(LibraryRules.matches("EXAMPLE", "Space", "https://example.org", "Science"))
        assertFalse(LibraryRules.matches("music", "Space", "https://example.org", "Science"))
    }

    @Test fun fileNamesAreSafeAndMimeFallbackIsAccurate() {
        assertEquals("report.pdf", DownloadFileNames.safe("../../report.pdf", "application/pdf"))
        assertEquals("image.png", DownloadFileNames.safe("image.png", "image/png"))
        assertEquals("download.pdf", DownloadFileNames.safe("..", "application/pdf"))
        assertEquals("download", DownloadFileNames.safe(null, null))
        assertEquals("report.pdf", DownloadFileNames.safe("report" + 0x202E.toChar() + ".pdf"))
    }

    @Test fun downloadDispositionAndRedactionDoNotLeakSecrets() {
        assertEquals("my report.pdf", GeckoDownloadNames.fromDisposition(
            "attachment; filename*=UTF-8''my%20report.pdf"))
        assertEquals("home page.txt", GeckoDownloadNames.fromDisposition(
            "attachment; filename=\"home page.txt\""))
        assertEquals("a+b.pdf", GeckoDownloadNames.fromDisposition(
            "attachment; filename*=UTF-8''a+b.pdf"))
        assertEquals("café.pdf", GeckoDownloadNames.fromDisposition(
            "attachment; filename*=ISO-8859-1''caf%E9.pdf"))
        assertEquals("https://example.com/folder/file.pdf",
            GeckoDownloadNames.redactUrl(
                "https://user:password@example.com/folder/file.pdf?access_token=secret#fragment"))
    }

    @Test fun signedAndAuthenticatedDownloadsCannotBeReplayedFromRedactedHistory() {
        assertTrue(DownloadFileNames.canSafelyRetry("https://example.org/file.pdf"))
        assertFalse(DownloadFileNames.canSafelyRetry(
            "https://example.org/file.pdf?token=ONE_TIME_SIGNED_KEY"))
        assertFalse(DownloadFileNames.canSafelyRetry(
            "https://user:password@example.org/file.pdf"))
        assertFalse(DownloadFileNames.canSafelyRetry("https://example.org/file.pdf#secret"))
        assertFalse(DownloadFileNames.canSafelyRetry("https://example.org/file.pdf", true))
        assertFalse(DownloadFileNames.canSafelyRetry("javascript:alert(1)"))
        assertEquals("https://example.org/file.pdf",
            GeckoDownloadNames.redactUrl("https://example.org/file.pdf?token=secret"))
    }

    @Test fun urlsWithEmbeddedCredentialsAreNotBookmarkable() {
        assertFalse(LibraryRules.validWebUrl("https://user:password@example.com/private"))
        assertFalse(LibraryRules.validWebUrl("https:///empty-host"))
    }

    @Test fun folderAndTitleAreBounded() {
        assertEquals("Untitled", LibraryRules.cleanTitle(" Untitled  ", "https://a.org"))
        assertEquals("https://a.org", LibraryRules.cleanTitle("", "https://a.org"))
        assertTrue(LibraryRules.cleanFolder("x".repeat(100)).length <= 50)
    }
}
