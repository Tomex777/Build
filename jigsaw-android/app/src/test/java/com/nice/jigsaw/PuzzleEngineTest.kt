package com.nice.jigsaw

import org.junit.Assert.*
import org.junit.Test

class PuzzleEngineTest {
    @Test fun countsAndScatterAreReal() {
        for ((c, r) in listOf(6 to 4, 8 to 6, 12 to 12)) {
            val p = PuzzleGame.fresh(c, r, seed = 42)
            assertEquals(c * r, p.count)
            assertEquals(c * r, p.pieces.map { it.id }.distinct().size)
            assertEquals(0, p.completed)
            assertTrue(p.pieces.all { it.x != p.targetX(it) || it.y != p.targetY(it) })
        }
    }
    @Test fun neighborTabsAreComplementaryAndEdgesFlat() {
        val g = PuzzleGame.fresh(12, 12, seed = 782)
        for (r in 0 until 12) for (c in 0 until 12) {
            if (r == 0) assertEquals(0, g.edge(r,c,0))
            if (c == 0) assertEquals(0, g.edge(r,c,3))
            if (r == 11) assertEquals(0, g.edge(r,c,2))
            if (c == 11) assertEquals(0, g.edge(r,c,1))
            if (r < 11) assertEquals(-g.edge(r,c,2),g.edge(r+1,c,0))
            if (c < 11) assertEquals(-g.edge(r,c,1),g.edge(r,c+1,3))
        }
    }
    @Test fun wrongPositionCannotSnapButNearbyCorrectPositionCan() {
        val g = PuzzleGame.fresh(6,4, seed=9)
        g.movePiece(0, 800f, 200f)
        assertFalse(g.release(0))
        assertFalse(g.pieces[0].placed)
        g.movePiece(0, g.targetX(g.pieces[0]) + 1f, g.targetY(g.pieces[0]) - 1f)
        assertTrue(g.release(0))
        assertEquals(1,g.completed)
        assertEquals(g.targetX(g.pieces[0]),g.pieces[0].x)
        assertFalse(g.release(0))
    }
    @Test fun hintsAreLimitedAndRestorationIsExact() {
        val g = PuzzleGame.fresh(8,6, "custom", 739)
        repeat(3) { assertTrue(g.hint()) }
        assertFalse(g.hint())
        assertEquals(3,g.completed)
        val state = g.pieces.map { Triple(it.x,it.y,it.placed) }
        val restored = PuzzleGame.restore(8,6,739,"custom",state, g.hintsRemaining,g.moves,g.preview)!!
        assertEquals(3,restored.completed)
        assertEquals(0,restored.hintsRemaining)
        assertEquals("custom",restored.imageKey)
        assertEquals(state, restored.pieces.map { Triple(it.x,it.y,it.placed) })
    }
    @Test fun invalidSavesCannotCrashOrAllocateMassiveBoards() {
        assertNull(PuzzleGame.restore(100,100,1,"starter",emptyList(),3,0,false))
        assertNull(PuzzleGame.restore(6,4,1,"starter",emptyList(),3,0,false))
    }
}
