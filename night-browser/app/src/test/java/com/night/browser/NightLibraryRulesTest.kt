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
    }

    @Test fun folderAndTitleAreBounded() {
        assertEquals("Untitled", LibraryRules.cleanTitle(" Untitled  ", "https://a.org"))
        assertEquals("https://a.org", LibraryRules.cleanTitle("", "https://a.org"))
        assertTrue(LibraryRules.cleanFolder("x".repeat(100)).length <= 50)
    }
}
