package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test

class NativeTicTacToeTest {
    @Test fun cpuNeverAllowsHumanVictory() {
        var leaves = 0
        fun explore(s: NativeTicState) {
            assertNotEquals("Perfect computer permitted a win", 1, s.result)
            if (s.result != 0) { leaves++; return }
            for (position in s.squares.indices) {
                if (s.squares[position] != 0) continue
                val next = NativeTicTacToe.move(s,position)
                assertNotSame(s,next)
                explore(next)
            }
        }
        explore(NativeTicState())
        assertTrue("Should examine many full legal human continuations",leaves>15)
    }

    @Test fun tapsRejectOccupiedAndFinishedTiles() {
        val start = NativeTicState()
        val moved = NativeTicTacToe.move(start,0)
        assertEquals(1,moved.squares[0])
        assertEquals(2,moved.squares.count { it != 0 })
        assertEquals(moved,NativeTicTacToe.move(moved,0))
        assertEquals(moved,NativeTicTacToe.move(moved,-1))
    }

    @Test fun saveRoundTrips() {
        val played = NativeTicTacToe.move(NativeTicState(),4)
        assertEquals(played,NativeTicTacToe.decode(NativeTicTacToe.encode(played)))
        assertNull(NativeTicTacToe.decode("bad"))
    }
}
