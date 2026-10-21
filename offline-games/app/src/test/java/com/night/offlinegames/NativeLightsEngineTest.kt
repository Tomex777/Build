package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test

class NativeLightsEngineTest {
    @Test fun pressesToggleSelfAndOrthogonalNeighbors() {
        assertEquals(setOf(0, 1, 5), indices(NativeLightsEngine.pressMask(0)))
        assertEquals(setOf(7, 11, 12, 13, 17), indices(NativeLightsEngine.pressMask(12)))
        assertEquals(0, NativeLightsEngine.pressMask(-1))
        assertEquals(0, NativeLightsEngine.pressMask(25))
    }

    @Test fun thousandGeneratedPuzzlesHaveRealSolutions() {
        for (level in 1..50) for (seed in 0..19) {
            var state = NativeLightsEngine.newGame(level, seed)
            assertTrue(state.litCount > 0)
            val solution = NativeLightsEngine.solve(state.board)
            assertNotNull("Unsolvable level $level seed $seed", solution)
            for (press in solution!!) state = NativeLightsEngine.tap(state, press)
            assertTrue("Solution failed at level $level seed $seed", state.won)
            assertEquals(0, state.litCount)
            assertEquals(state, NativeLightsEngine.tap(state, 12))
        }
    }

    @Test fun undoAndSaveRestoreKeepRealBoard() {
        val start = NativeLightsEngine.newGame(3, 42)
        val moved = NativeLightsEngine.tap(start, 12)
        assertEquals(1, moved.moves)
        assertEquals(start.board xor NativeLightsEngine.pressMask(12), moved.board)
        assertEquals(start, NativeLightsEngine.undo(moved))
        assertEquals(start, NativeLightsEngine.decode(NativeLightsEngine.encode(start)))
        assertEquals(moved, NativeLightsEngine.decode(NativeLightsEngine.encode(moved)))
        assertEquals(start, NativeLightsEngine.tap(start, -1))
        assertNull(NativeLightsEngine.decode("broken"))
        assertNull(NativeLightsEngine.decode(null))
        assertNull(NativeLightsEngine.decode("1;0;0;0;0;"))
        assertNull(NativeLightsEngine.decode("1;1;0;0;33554432;"))
    }

    private fun indices(mask: Int) = (0 until NativeLightsEngine.count)
        .filter { mask and (1 shl it) != 0 }.toSet()
}
