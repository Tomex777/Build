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
    fun guidedSolutionConsumesHistoryAndSolves() {
        val state = PuzzleState(3, 3, 3)
        state.turnOuter(Axis.X)
        state.turnOuter(Axis.Y, positive = false)
        assertEquals(2, state.moveCount())
        assertEquals("U", state.nextSolutionMove()?.label)

        val first = state.solveNextStep()
        assertEquals("U", first?.label)
        assertEquals(1, state.moveCount())
        assertFalse(state.isSolved())

        val second = state.solveNextStep()
        assertEquals("R'", second?.label)
        assertEquals(0, state.moveCount())
        assertTrue(state.isSolved())
    }

    @Test
    fun everyOuterFaceCanBeTurnedAndReversed() {
        Face.entries.forEach { face ->
            val state = PuzzleState(3, 3, 3)
            state.turnFace(face, clockwise = true)
            assertFalse("${face.label} should change the puzzle", state.isSolved())
            state.turnFace(face, clockwise = false)
            assertTrue("${face.label} followed by its inverse should solve", state.isSolved())
        }
    }

    @Test
    fun innerLayerCanBeTurnedAndReversed() {
        val state = PuzzleState(5, 5, 5)
        val move = state.turnFaceLayer(Face.R, depthFromFace = 2, clockwise = true)
        assertEquals("2R", move.label)
        assertFalse(state.isSolved())
        val inverse = state.turnFaceLayer(Face.R, depthFromFace = 2, clockwise = false)
        assertEquals("2R'", inverse.label)
        assertTrue(state.isSolved())
    }

    @Test
    fun oppositeFaceNotationMatchesClockwiseIntent() {
        val state = PuzzleState(3, 3, 3)
        assertEquals("L", state.turnFace(Face.L, clockwise = true).label)
        assertEquals("L'", state.nextSolutionMove()?.label)
    }

    @Test
    fun cuboidDescriptionExplainsHalfTurn() {
        val state = PuzzleState(3, 3, 5)
        val move = state.turnOuter(Axis.X)
        assertTrue(state.describe(move).contains("half-turn"))
        assertTrue(state.describe(move).contains("right layer"))
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
