package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserHistoryTitleTest {
    @Test fun restoresOnlyTheTitleMatchingTheCommittedPage() {
        assertEquals("NIGHT QA BRANCH A", BrowserHistoryTitle.atCommittedUrl(
            "https://example.org/branch/a",
            "https://example.org/branch/a", "NIGHT QA BRANCH A"
        ))
        assertNull(BrowserHistoryTitle.atCommittedUrl(
            "https://example.org/branch/a",
            "https://example.org/branch/c", "NIGHT QA BRANCH C"
        ))
    }

    @Test fun neverPromotesEmptyOrUncommittedTitles() {
        assertNull(BrowserHistoryTitle.atCommittedUrl(
            "", "", "Stale"))
        assertNull(BrowserHistoryTitle.atCommittedUrl(
            "https://example.org", "https://example.org", "  "))
        assertNull(BrowserHistoryTitle.atCommittedUrl(
            "https://example.org", null, "Wrong"))
    }

    @Test fun boundsUnsafeLongHistoryTitles() {
        assertEquals(240, BrowserHistoryTitle.atCommittedUrl(
            "https://example.org", "https://example.org", "A".repeat(500)
        )?.length)
    }
}
