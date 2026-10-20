package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeMachineLimitsTest {
    @Test fun multipleVersionsAreAllowedUntilTheVisibleLimit() {
        assertTrue(TimeMachineLimits.canAdd(0, 0, 4096))
        assertTrue(TimeMachineLimits.canAdd(119, 0, 4096))
        assertFalse(TimeMachineLimits.canAdd(120, 0, 4096))
    }

    @Test fun quotaAccountsForIncomingBytesAndRejectsEmptyOrLargeFiles() {
        assertTrue(TimeMachineLimits.canAdd(5, TimeMachineLimits.MAX_BYTES - 1024, 1024))
        assertFalse(TimeMachineLimits.canAdd(5, TimeMachineLimits.MAX_BYTES - 1023, 1024))
        assertFalse(TimeMachineLimits.canAdd(5, 0, 0))
        assertFalse(TimeMachineLimits.canAdd(5, 0, TimeMachineLimits.MAX_PDF_BYTES + 1))
    }

    @Test fun anchorsAreNotSeparateVersionsOfTheSameWebpage() {
        assertEquals(
            "https://example.com/story",
            TimeMachineLimits.pageKey("https://example.com/story#comments")
        )
        assertEquals(
            "https://example.com/story",
            TimeMachineLimits.pageKey("https://example.com/story/")
        )
    }

    @Test fun querySpecificPagesRemainSeparate() {
        assertEquals("https://example.com/?chapter=1",
            TimeMachineLimits.pageKey("https://example.com/?chapter=1"))
        assertEquals("https://example.com/?chapter=2",
            TimeMachineLimits.pageKey("https://example.com/?chapter=2"))
    }
}
