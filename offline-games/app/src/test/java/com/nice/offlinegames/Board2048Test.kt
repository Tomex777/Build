package com.nice.offlinegames

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class Board2048Test {
    private fun board(vararg row: Int) = BoardState(row.toList())

    @Test fun newGameStartsWithExactlyTwoTiles() {
        val state = Board2048.newGame(Random(7))
        assertEquals(2, state.values.count { it != 0 })
        assertEquals(0, state.score)
    }

    @Test fun mergeOncePerTileAndSpawnOneTile() {
        val state = board(
            2, 2, 2, 2,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        val moved = Board2048.move(state, Direction.LEFT, Random(11))
        assertEquals(4, moved.values[0])
        assertEquals(4, moved.values[1])
        assertEquals(8, moved.score)
        assertEquals(3, moved.values.count { it > 0 })
    }

    @Test fun impossibleSwipeDoesNotSpawnOrChangeScore() {
        val state = board(
            2, 4, 8, 16,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        assertEquals(state, Board2048.move(state, Direction.LEFT, Random(1)))
    }

    @Test fun fullBoardWithoutAdjacentMatchesIsOver() {
        val state = board(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2
        )
        assertTrue(state.gameOver)
        assertEquals(state, Board2048.move(state, Direction.RIGHT, Random(1)))
    }

    @Test fun downwardsMergeUsesColumns() {
        val state = board(
            2, 0, 0, 0,
            2, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        val moved = Board2048.move(state, Direction.DOWN, Random(2))
        assertEquals(4, moved.values[12])
        assertEquals(4, moved.score)
    }

    @Test fun savesRoundTripAndRejectsCorruption() {
        val state = Board2048.newGame(Random(4)).copy(score = 76)
        assertEquals(state, Board2048.decode(Board2048.encode(state)))
        assertNull(Board2048.decode("not;a;board"))
        assertNull(Board2048.decode(null))
    }
}
