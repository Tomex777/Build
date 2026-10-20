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

    @Test fun siblingConnectorsStayVisibleThroughDeeperBranches() {
        val rows = JourneyGraph.rows(journey)
        assertTrue("B needs a branch connector down to D", rows[1].hasNextSibling)
        assertEquals(listOf(true), rows[2].ancestorContinues)
        assertFalse(rows[3].hasNextSibling)
        // Search should not draw connectors to siblings filtered out of the tree.
        val filtered = JourneyGraph.rows(journey, "Third")
        assertEquals(listOf(false), filtered[2].ancestorContinues)
    }

    @Test fun collapsingOneBranchKeepsItsRootAndUnrelatedBranches() {
        val rows = JourneyGraph.rows(journey, collapsedIds = setOf("B"))
        assertEquals(listOf("A", "B", "D"), rows.map { it.node.id })
        assertEquals(listOf(0, 1, 1), rows.map { it.depth })
        assertTrue(rows.first { it.node.id == "B" }.hasChildren)
        assertFalse(rows.first { it.node.id == "D" }.hasChildren)
        assertTrue(rows.first { it.node.id == "B" }.hasNextSibling)
    }

    @Test fun searchRevealsMatchesBehindCollapsedAncestors() {
        val rows = JourneyGraph.rows(
            journey, query = "Third", collapsedIds = setOf("A", "B")
        )
        assertEquals(listOf("A", "B", "C"), rows.map { it.node.id })
        assertEquals(listOf(false, false, true), rows.map { it.matchesSearch })
    }

    @Test fun completelyCollapsedRootDoesNotResurfaceChildrenAsExtraRoots() {
        val rows = JourneyGraph.rows(journey, collapsedIds = setOf("A"))
        assertEquals(listOf("A"), rows.map { it.node.id })
        assertTrue(rows.single().hasChildren)
    }

    @Test fun manyNodesCanBeHiddenAndRevealedWithoutChangingSavedTree() {
        val nodes = (0 until 800).map { i ->
            JourneyNode("n$i", "https://night.test/$i", "$i",
                if (i == 0) null else "n${i - 1}", i.toLong())
        }
        val normal = JourneyGraph.rows(Journey("large", "Deep", nodes))
        assertEquals(800, normal.size)
        val collapsed = JourneyGraph.rows(
            Journey("large", "Deep", nodes), collapsedIds = setOf("n5")
        )
        assertEquals(6, collapsed.size)
        val matched = JourneyGraph.rows(
            Journey("large", "Deep", nodes),
            query = "799", collapsedIds = setOf("n5")
        )
        assertEquals(800, matched.size)
        assertEquals("n799", matched.last().node.id)
    }

    @Test fun largeTreeRetainsValidParentsAndAvoidsRecursionOverflow() {
        val input = (0 until 1000).map { index ->
            JourneyNode(
                "node$index", "https://night.test/$index", "$index",
                if (index == 0) null else "node${index - 1}", index.toLong()
            )
        }
        val kept = JourneyGraph.repair(input)
        assertEquals(600, kept.size)
        val ids = kept.mapTo(HashSet()) { it.id }
        assertNull(kept.first().parent)
        assertTrue(kept.all { it.parent == null || it.parent in ids })
        assertEquals(600, JourneyGraph.rows(Journey("deep", "Deep", kept)).size)
    }

    @Test fun singleVisitDeletionSplicesChildrenWithoutLosingHistory() {
        val removedB = JourneyGraph.removeVisit(journey.nodes, "B")
        assertEquals(listOf("A", "C", "D"), removedB.map { it.id })
        assertEquals("A", removedB.single { it.id == "C" }.parent)
        assertEquals("A", removedB.single { it.id == "D" }.parent)
        val removedRoot = JourneyGraph.removeVisit(journey.nodes, "A")
        assertNull(removedRoot.single { it.id == "B" }.parent)
        assertNull(removedRoot.single { it.id == "D" }.parent)
        assertEquals("B", removedRoot.single { it.id == "C" }.parent)
        assertEquals(journey.nodes, JourneyGraph.removeVisit(journey.nodes, "missing"))
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
