package com.night.browser.search

import com.night.browser.Bookmark
import com.night.browser.Journey
import com.night.browser.JourneyNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSourceCatalogTest {
    @Test fun selectionIdsStayBoundToUrlsAfterRecordReorder() {
        val one = Bookmark("https://example.com/first", "First page")
        val two = Bookmark("https://example.com/second", "Second page")
        val initial = SearchSourceCatalog.collect(listOf(one, two), emptyList())
        val reordered = SearchSourceCatalog.collect(listOf(two, one), emptyList())
        val firstSelectedId = initial.first { it.url == one.url }.id

        assertEquals(firstSelectedId, reordered.first { it.url == one.url }.id)
        assertFalse(firstSelectedId == reordered.first { it.url == two.url }.id)
    }

    @Test fun bookmarkedAndVisitedCopiesOfSameUrlShareIdentityButAreNotDuplicated() {
        val bookmark = Bookmark("https://docs.example.com/start", "Documentation")
        val visit = JourneyNode(
            "node-1", bookmark.url, "Documentation", null, 1L
        )
        val journey = Journey("journey-1", "My browsing", listOf(visit))
        val sources = SearchSourceCatalog.collect(listOf(bookmark), listOf(journey))

        assertEquals(1, sources.size)
        assertEquals(SearchSourceCatalog.stableId(bookmark.url), sources.single().id)
    }

    @Test fun sensitiveRecordsCannotCrowdInAnIneligibleEntry() {
        val bookmarks = (1..8).map {
            Bookmark("https://example.com/login/" + it, "Log in page " + it)
        }
        val visit = JourneyNode("node", "https://example.com/docs", "Documentation", null, 2L)
        val sources = SearchSourceCatalog.collect(
            bookmarks,
            listOf(Journey("journey", "Saved visits", listOf(visit))),
        )
        assertEquals(1, sources.size)
        assertEquals(visit.url, sources.single().url)
    }

    @Test fun selectionIsFailClosedAfterSelectedUrlIsDeleted() {
        val old = Bookmark("https://example.com/old", "Old page")
        val replacement = Bookmark("https://example.com/new", "New page")
        val oldId = SearchSourceCatalog.collect(listOf(old), emptyList()).single().id
        val newCatalog = SearchSourceCatalog.collect(listOf(replacement), emptyList())
        assertTrue(
            UniversalSearchPolicy.prepare(
                "Where is my old page?", newCatalog, setOf(oldId), true, false
            ) is SearchPreparation.Rejected
        )
    }
}
