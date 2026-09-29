package com.tomex777.cubic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun teachingDescriptionUsesFaceRelativeDirection() {
        val left = PuzzleState(3, 3, 3)
        val clockwise = left.turnFace(Face.L, clockwise = true)
        assertTrue(left.describe(clockwise).contains("clockwise by 90 degrees"))

        val inner = PuzzleState(5, 5, 5)
        val innerMove = inner.turnFaceLayer(Face.R, depthFromFace = 2, clockwise = false)
        assertTrue(inner.describe(innerMove).contains("layer 2 from the right"))
        assertTrue(inner.describe(innerMove).contains("counterclockwise by 90 degrees"))
    }

    @Test
    fun returningToSolvedClearsStaleHistory() {
        val state = PuzzleState(3, 3, 3)
        state.turnFace(Face.R)
        state.turnFace(Face.R, clockwise = false)
        assertTrue(state.isSolved())
        assertEquals(0, state.moveCount())
        assertNull(state.nextSolutionMove())
    }

    @Test
    fun cuboidDescriptionExplainsHalfTurn() {
        val state = PuzzleState(3, 3, 5)
        val move = state.turnOuter(Axis.X)
        assertTrue(state.describe(move).contains("180 degrees"))
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

    @Test
    fun representativeDimensionsRemainReversibleAndGuideBackToSolved() {
        val sizes = listOf(
            Triple(2, 2, 2),
            Triple(3, 3, 3),
            Triple(4, 4, 4),
            Triple(3, 3, 5),
            Triple(2, 4, 6),
            Triple(7, 7, 7)
        )

        sizes.forEachIndexed { index, (width, height, depth) ->
            val state = PuzzleState(width, height, depth)
            assertEquals(width * height * depth, state.snapshot().cubies.size)
            assertTrue(state.isSolved())

            Face.entries.forEach { face ->
                val depths = setOf(1, minOf(2, state.layersFor(face)))
                depths.forEach { layerDepth ->
                    state.turnFaceLayer(face, layerDepth, clockwise = true)
                    assertFalse(
                        "$width x $height x $depth ${face.label} layer $layerDepth should move",
                        state.isSolved()
                    )
                    state.turnFaceLayer(face, layerDepth, clockwise = false)
                    assertTrue(
                        "$width x $height x $depth ${face.label} layer $layerDepth should reverse",
                        state.isSolved()
                    )
                }
            }

            state.scramble(24, Random(100 + index))
            assertFalse(state.isSolved())

            var steps = 0
            while (state.nextSolutionMove() != null) {
                state.solveNextStep()
                steps++
                assertTrue("guided solve should terminate", steps <= 24)
            }

            assertTrue("$width x $height x $depth should guide back to solved", state.isSolved())
            assertEquals(0, state.moveCount())
        }
    }

    @Test
    fun scrambleUsesFaceRelativeTeachingNotationAndAvoidsImmediateLayerRepeats() {
        val state = PuzzleState(5, 5, 5)
        val moves = state.scramble(40, Random(42))

        assertTrue(
            moves.all { move ->
                val notation = move.label.removeSuffix("'").removeSuffix("2")
                notation.lastOrNull()?.let { it in "RLUDFB" } == true
            }
        )

        moves.zipWithNext().forEach { (first, second) ->
            assertFalse(
                "scramble should not immediately repeat the same physical layer",
                first.axis == second.axis && first.layer == second.layer
            )
        }
    }

    @Test
    fun maximumSupportedNineCubeStateRemainsValid() {
        val state = PuzzleState(9, 9, 9)
        assertEquals(729, state.snapshot().cubies.size)
        state.turnFaceLayer(Face.F, depthFromFace = 5, clockwise = true)
        assertFalse(state.isSolved())
        state.turnFaceLayer(Face.F, depthFromFace = 5, clockwise = false)
        assertTrue(state.isSolved())
    }
}
