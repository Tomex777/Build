package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test

class NativeSlidingEngineTest {
    @Test fun generatedBoardsAreReachableAndNontrivial() {
        for (level in 1..20) for (seed in 0..19) {
            val state = NativeSlidingEngine.newGame(level, seed)
            assertTrue("Unsolvable $level/$seed", NativeSlidingEngine.solvable(state.tiles))
            assertFalse(state.won)
            assertTrue("Trivial $level/$seed", NativeSlidingEngine.distance(state.tiles) >= 7)
            assertEquals(state, NativeSlidingEngine.decode(NativeSlidingEngine.encode(state)))
        }
    }

    @Test fun tapsMustBeAdjacentAndUndoIsExact() {
        val state = NativeSlidingEngine.newGame(4, 13)
        assertSame(state, NativeSlidingEngine.move(state, -1))
        assertSame(state, NativeSlidingEngine.move(state, state.empty))
        val illegal = (0..15).first { it !in NativeSlidingEngine.adjacent(state.empty) }
        assertSame(state, NativeSlidingEngine.move(state, illegal))
        val index = NativeSlidingEngine.adjacent(state.empty).first()
        val moved = NativeSlidingEngine.move(state, index)
        assertEquals(1, moved.moves)
        assertEquals(index, moved.empty)
        assertEquals(state, NativeSlidingEngine.undo(moved))
        assertEquals(moved, NativeSlidingEngine.decode(NativeSlidingEngine.encode(moved)))
    }

    @Test fun solvedBoardRequiresFinalLegalMove() {
        val tiles = NativeSlidingEngine.solved.toMutableList()
        tiles[14] = 0
        tiles[15] = 15
        val state = NativeSlidingState(tiles)
        assertFalse(state.won)
        val won = NativeSlidingEngine.move(state, 15)
        assertTrue(won.won)
        assertSame(won, NativeSlidingEngine.move(won, 14))
        assertEquals(state, NativeSlidingEngine.undo(won))
    }

    @Test fun invalidSaveAndImpossibleShuffleAreRejected() {
        assertNull(NativeSlidingEngine.decode(null))
        assertNull(NativeSlidingEngine.decode("broken"))
        val impossible = NativeSlidingEngine.solved.toMutableList()
        impossible[0] = 2
        impossible[1] = 1
        assertFalse(NativeSlidingEngine.solvable(impossible))
        assertNull(NativeSlidingEngine.decode("1;1;0;0;${impossible.joinToString(",")};"))
    }
}
