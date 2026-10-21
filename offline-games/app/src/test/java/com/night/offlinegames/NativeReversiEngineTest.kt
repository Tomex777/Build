package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class NativeReversiEngineTest {
    @Test fun openingBoardAndLegalHumanMovesAreAuthentic() {
        val initial = NativeReversiEngine.newGame()
        assertEquals(2, initial.black)
        assertEquals(2, initial.white)
        assertEquals(setOf(19, 26, 37, 44), NativeReversiEngine.legal(initial.cells, 1).toSet())
        assertEquals(emptyList<Int>(), NativeReversiEngine.flips(initial.cells, 1, 0))
        assertEquals(listOf(27), NativeReversiEngine.flips(initial.cells, 1, 19))
    }

    @Test fun computerAnswersAndInvalidTouchesNeverChangeBoard() {
        val state = NativeReversiEngine.newGame()
        assertEquals(state, NativeReversiEngine.play(state, 0))
        assertEquals(state, NativeReversiEngine.play(state, 27))
        val played = NativeReversiEngine.play(state, 19)
        assertEquals(1, played.turns)
        assertTrue(played.cells.count { it != 0 } >= 5)
        assertTrue(played.black > 0)
        assertTrue(played.white > 0)
        assertTrue(NativeReversiEngine.legal(played.cells, 1).isNotEmpty())
    }

    @Test fun cpuAlwaysChoosesLegalMovesAndCanFinishCompleteMatches() {
        for (seed in 0..8) {
            val rng = Random(seed)
            var state = NativeReversiEngine.newGame()
            repeat(62) {
                if (state.finished) return@repeat
                val choices = NativeReversiEngine.legal(state.cells, 1)
                assertTrue("Human must have a legal move after auto-pass", choices.isNotEmpty())
                val next = NativeReversiEngine.play(state, choices[rng.nextInt(choices.size)])
                assertNotEquals(state, next)
                assertTrue(next.cells.all { it in 0..2 })
                assertTrue(next.cells.count { it != 0 } > state.cells.count { it != 0 })
                state = next
            }
            assertTrue("Game should end, not hang, for seed $seed", state.finished)
            assertEquals(0, NativeReversiEngine.legal(state.cells, 1).size)
            assertEquals(0, NativeReversiEngine.legal(state.cells, 2).size)
        }
    }

    @Test fun savesRestoreAndRejectCorruptBoard() {
        val state = NativeReversiEngine.play(NativeReversiEngine.newGame(), 19)
        assertEquals(state, NativeReversiEngine.decode(NativeReversiEngine.encode(state)))
        assertNull(NativeReversiEngine.decode("x"))
        assertNull(NativeReversiEngine.decode(null))
    }
}
