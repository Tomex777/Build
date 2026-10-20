package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class TabPreviewBudgetTest {
    @Test fun prioritizesActiveTabThenRecentCards() {
        val slots = (1..30).map { TabPreviewBudget.Preview("t$it", 1_000_000L) }
        val kept = TabPreviewBudget.retainedIds(slots, "t1")
        assertTrue("active image should remain", "t1" in kept)
        assertTrue("recent image should remain", "t30" in kept)
        assertFalse("least-recent image should be evicted", "t2" in kept)
        assertTrue(kept.size <= TabPreviewBudget.MAX_CARDS)
        assertTrue(kept.size <= 14)
    }

    @Test fun rejectsOversizedOrInvalidImages() {
        val items = listOf(
            TabPreviewBudget.Preview("huge", TabPreviewBudget.MAX_BYTES + 1),
            TabPreviewBudget.Preview("zero", 0),
            TabPreviewBudget.Preview("normal", 2000)
        )
        assertEquals(setOf("normal"), TabPreviewBudget.retainedIds(items, "normal"))
    }

    @Test fun noImagesMeansNoMemory() {
        assertTrue(TabPreviewBudget.retainedIds(emptyList(), null).isEmpty())
    }
}
