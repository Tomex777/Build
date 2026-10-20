package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class JourneyVisitTrackerTest {
    private class Harness(seed: String? = null) {
        val nodes = linkedMapOf<String, JourneyNode>()
        private var counter = 0
        val tracker = JourneyVisitTracker(
            lookup = { nodes[it] },
            saveVisit = { url, title, parent ->
                val id = (++counter).toString()
                nodes[id] = JourneyNode(id, url, title, parent, counter.toLong())
                id
            },
            seedNodeId = seed
        )
        fun visit(path: String): String? =
            tracker.visited("https://night.test/$path", path)
    }

    @Test fun backToAThenDIsSiblingOfBNotChildOfC() {
        val h = Harness()
        val a = h.visit("a")
        h.visit("b")
        h.visit("c")
        h.tracker.expectBack()
        h.visit("b")
        h.tracker.expectBack()
        assertEquals(a, h.visit("a"))
        val d = h.visit("d")
        assertEquals(a, h.nodes[d]?.parent)
        assertEquals(4, h.nodes.size)
        assertEquals(listOf(0, 1, 2, 1),
            JourneyGraph.rows(Journey("j", "test", h.nodes.values.toList()))
                .map { it.depth })
    }

    @Test fun forwardAndReloadReuseNodes() {
        val h = Harness()
        h.visit("a")
        val b = h.visit("b")
        val c = h.visit("c")
        h.tracker.expectBack()
        assertEquals(b, h.visit("b"))
        h.tracker.expectForward()
        assertEquals(c, h.visit("c"))
        h.tracker.expectReload()
        assertEquals(c, h.visit("c"))
        assertEquals(3, h.nodes.size)
    }

    @Test fun urlFragmentsDoNotCreateNoise() {
        val h = Harness()
        val one = h.tracker.visited("https://night.test/docs#part-one", "Docs")
        val two = h.tracker.visited("https://night.test/docs#part-two", "Docs")
        assertEquals(one, two)
        assertEquals(1, h.nodes.size)
    }

    @Test fun nonHttpNavigationsNeverRecord() {
        val h = Harness()
        assertNull(h.tracker.visited("about:blank", "Private"))
        assertNull(h.tracker.visited("data:text/html;base64,abc", "Private"))
        assertTrue(h.nodes.isEmpty())
    }

    @Test fun resumedNodeBecomesParentForNewBranchWithoutDuplicateLanding() {
        val h = Harness()
        val a = h.visit("a")
        val b = h.visit("b")
        val resumed = JourneyVisitTracker(
            lookup = { h.nodes[it] },
            saveVisit = { url, title, parent ->
                val id = "third"
                h.nodes[id] = JourneyNode(id, url, title, parent, 9)
                id
            },
            seedNodeId = b
        )
        assertEquals(b, resumed.visited("https://night.test/b", "b"))
        assertEquals("third", resumed.visited("https://night.test/branch", "branch"))
        assertEquals(b, h.nodes["third"]?.parent)
        assertNotEquals(a, h.nodes["third"]?.parent)
    }
}
