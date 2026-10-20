package com.night.offlinegames

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class NativeMinesEngineTest {
    @Test fun firstTapAndItsNeighborsAreAlwaysSafeAcrossSeeds() {
        for (seed in 0 until 150) {
            val chosen = seed % NativeMinesEngine.count
            val state = NativeMinesEngine.reveal(NativeMinesEngine.newGame(), chosen, Random(seed))
            assertTrue(state.started)
            assertEquals(NativeMinesEngine.mineCount, state.mines.size)
            assertFalse(chosen in state.mines)
            assertTrue(NativeMinesEngine.neighbors(chosen).none { it in state.mines })
            assertTrue(chosen in state.revealed)
            assertEquals(0, NativeMinesEngine.adjacent(state, chosen))
            assertFalse(state.lost)
        }
    }

    @Test fun revealedZeroAreaFloodsWithoutCrossingBombs() {
        val start = NativeMinesEngine.reveal(NativeMinesEngine.newGame(), 40, Random(19))
        assertTrue(start.revealed.size >= 9)
        assertTrue(start.revealed.none { it in start.mines })
        assertEquals(start, NativeMinesEngine.reveal(start, 40))
    }

    @Test fun flagsBlockRevealAndCanBeRemoved() {
        val flagged = NativeMinesEngine.toggleFlag(NativeMinesEngine.newGame(), 5)
        assertTrue(5 in flagged.flags)
        assertEquals(flagged, NativeMinesEngine.reveal(flagged, 5, Random(2)))
        assertFalse(NativeMinesEngine.toggleFlag(flagged, 5).flags.contains(5))
        var capped = NativeMinesEngine.newGame()
        for (i in 0..NativeMinesEngine.mineCount) capped = NativeMinesEngine.toggleFlag(capped, i)
        assertEquals(NativeMinesEngine.mineCount, capped.flags.size)
    }

    @Test fun mineTapLosesAndStopsFurtherMoves() {
        val start = NativeMinesEngine.reveal(NativeMinesEngine.newGame(), 40, Random(23))
        val bomb = start.mines.first()
        val lost = NativeMinesEngine.reveal(start, bomb)
        assertTrue(lost.lost)
        assertTrue(bomb in lost.revealed)
        assertEquals(lost, NativeMinesEngine.reveal(lost, 0))
        assertEquals(lost, NativeMinesEngine.toggleFlag(lost, 0))
    }

    @Test fun fullSafeBoardWinsAndChordingRequiresMatchingFlags() {
        var board = NativeMinesEngine.reveal(NativeMinesEngine.newGame(), 40, Random(44))
        val numbered = board.revealed.firstOrNull { NativeMinesEngine.adjacent(board, it) > 0 }
        if (numbered != null) assertEquals(board, NativeMinesEngine.chord(board, numbered))
        for (i in 0 until NativeMinesEngine.count) {
            if (i !in board.mines) board = NativeMinesEngine.reveal(board, i)
        }
        assertTrue(board.won)
        assertEquals(NativeMinesEngine.count - NativeMinesEngine.mineCount, board.revealed.size)
        assertFalse(board.lost)
    }

    @Test fun persistenceRoundTripRejectsInvalidBoards() {
        val board = NativeMinesEngine.reveal(NativeMinesEngine.newGame(), 20, Random(4))
        val marked = NativeMinesEngine.toggleFlag(board, board.mines.first())
        assertEquals(marked, NativeMinesEngine.decode(NativeMinesEngine.encode(marked)))
        assertEquals(NativeMinesEngine.newGame(),
            NativeMinesEngine.decode(NativeMinesEngine.encode(NativeMinesEngine.newGame())))
        assertNull(NativeMinesEngine.decode(null))
        assertNull(NativeMinesEngine.decode("invalid"))
        assertNull(NativeMinesEngine.decode(NativeMinesEngine.encode(marked).replace(";true;", ";what;")))
    }
}
