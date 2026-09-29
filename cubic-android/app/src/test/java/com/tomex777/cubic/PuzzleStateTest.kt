package com.tomex777.cubic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleStateTest {
    @Test
    fun startsSolvedAndResizesToCuboid() {
        val state = PuzzleState(3, 3, 3)
        assertTrue(state.isSolved())
        state.resize(3, 3, 5)
        assertEquals(3, state.width)
        assertEquals(3, state.height)
        assertEquals(5, state.depth)
        assertTrue(state.isSolved())
        assertEquals(45, state.snapshot().cubies.size)
    }

    @Test
    fun incompatibleQuarterTurnBecomesHalfTurn() {
        val state = PuzzleState(3, 3, 5)
        val move = state.turnOuter(Axis.X)
        assertEquals(2, kotlin.math.abs(move.quarterTurns))
        assertTrue(move.label.endsWith("2"))
        assertFalse(state.isSolved())
    }

    @Test
    fun scrambleCanBeUndoneBackToSolved() {
        val state = PuzzleState(4, 4, 4)
        val moves = state.scramble(12, Random(7))
        assertEquals(12, moves.size)
        assertFalse(state.isSolved())
        repeat(12) { state.undo() }
        assertTrue(state.isSolved())
    }
}
