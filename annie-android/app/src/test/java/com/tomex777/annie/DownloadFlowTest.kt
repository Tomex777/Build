package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFlowTest {
    @Test fun nextBatchStartsAtOneAndAdvancesInTwentyChapterPages() {
        assertEquals(ChapterBatch(1, 20), nextChapterBatch(0))
        assertEquals(ChapterBatch(21, 40), nextChapterBatch(20))
        assertEquals(ChapterBatch(41, 60), nextChapterBatch(40))
    }

    @Test fun finalBatchStopsAtKnownChapterCount() {
        assertEquals(ChapterBatch(241, 247), nextChapterBatch(240, knownTotal = 247))
        assertNull(nextChapterBatch(247, knownTotal = 247))
    }

    @Test fun sourceMatchingUsesAliasOverlapAndLeavesUnrelatedTitlesLow() {
        assertEquals(100, matchSourceTitle(listOf("The Greatest Estate Developer"), listOf("The Greatest Estate Developer")))
        assertEquals(80, matchSourceTitle(listOf("The Greatest Estate Developer"), listOf("Greatest Estate Developer")))
        assertEquals(0, matchSourceTitle(listOf("Blue Lock"), listOf("Frieren Beyond Journey's End")))
    }
}
