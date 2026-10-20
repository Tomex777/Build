package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class JourneyGraphTest {
    private val a = JourneyNode("A", "https://a.test/", "Start", null, 1)
    private val b = JourneyNode("B", "https://a.test/b", "Second", "A", 2)
    private val c = JourneyNode("C", "https://a.test/c", "Third", "B", 3)
    private val d = JourneyNode("D", "https://a.test/d", "Fork", "A", 4)
    private val journey = Journey("j", "My browsing", listOf(a, b, c, d))

    @Test fun preorderPreservesBranches() {
        val rows = JourneyGraph.rows(journey)
        assertEquals(listOf("A", "B", "C", "D"), rows.map { it.node.id })
        assertEquals(listOf(0, 1, 2, 1), rows.map { it.depth })
    }

    @Test fun searchRetainsAncestorsWithoutUnrelatedBranches() {
        val rows = JourneyGraph.rows(journey, "third")
        assertEquals(listOf("A", "B", "C"), rows.map { it.node.id })
        assertEquals(listOf(false, false, true), rows.map { it.matchesSearch })
    }

    @Test fun deletingSubtreeDoesNotDeleteSibling() {
        assertEquals(setOf("B", "C"), JourneyGraph.subtreeIds(journey.nodes, "B"))
        assertFalse("D" in JourneyGraph.subtreeIds(journey.nodes, "B"))
    }

    @Test fun pruningReparentsOrphanAndKeepsValidDescendants() {
        val result = JourneyGraph.repair(journey.nodes, 2)
        assertEquals(listOf("C", "D"), result.map { it.id })
        assertEquals(listOf(null, null), result.map { it.parent })
    }

    @Test fun sensitiveAndUnsupportedUrlsAreNotPersistedVerbatim() {
        assertEquals(
            "https://example.com/path?q=browser&lang=en",
            JourneyGraph.safeUrl(
                "https://user:password@EXAMPLE.COM/path?q=browser&access_token=abc&lang=en#private"
            )
        )
        assertEquals("https://example.com/", JourneyGraph.safeUrl("https://example.com"))
        assertNull(JourneyGraph.safeUrl("file:///storage/emulated/0/private.txt"))
        assertNull(JourneyGraph.safeUrl("javascript:alert(1)"))
    }

    @Test fun selfCycleDoesNotLoop() {
        val cyclic = Journey("x", "cycle", listOf(
            a.copy(parent = "B"),
            b.copy(parent = "A")
        ))
        assertEquals(2, JourneyGraph.rows(cyclic).size)
    }
}
