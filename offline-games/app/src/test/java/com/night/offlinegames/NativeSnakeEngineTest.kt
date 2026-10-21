package com.night.offlinegames

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class NativeSnakeEngineTest {
    @Test fun startsWithThreeSegmentsAndFoodOutsideBody() {
        val state = NativeSnakeEngine.newGame(Random(4))
        assertEquals(3, state.snake.size)
        assertEquals(3, state.snake.toSet().size)
        assertFalse(state.fruit in state.snake)
    }

    @Test fun immediateReverseIsRejectedButRightAnglesWork() {
        val state = NativeSnakeEngine.newGame(Random(4))
        assertEquals(state, NativeSnakeEngine.requestTurn(state, SnakeHeading.LEFT))
        val turned = NativeSnakeEngine.requestTurn(state, SnakeHeading.UP)
        assertEquals(SnakeHeading.UP, turned.pending)
        val advanced = NativeSnakeEngine.tick(turned, Random(4))
        assertEquals(SnakeCell(8, 11), advanced.snake.first())
        assertEquals(SnakeHeading.UP, advanced.heading)
    }

    @Test fun eatingGrowsAndGivesPoints() {
        val start = NativeSnakeEngine.newGame(Random(4))
        val fruit = SnakeCell(9, 12)
        val next = NativeSnakeEngine.tick(start.copy(fruit = fruit), Random(6))
        assertEquals(4, next.snake.size)
        assertEquals(10, next.score)
        assertFalse(next.over)
        assertFalse(next.fruit in next.snake)
    }

    @Test fun collisionWithWallTerminatesRun() {
        var s = NativeSnakeEngine.newGame(Random(4))
        repeat(NativeSnakeEngine.columns) { s = NativeSnakeEngine.tick(s, Random(7)) }
        assertTrue(s.over)
        assertEquals(s, NativeSnakeEngine.tick(s))
    }

    @Test fun movingIntoVacatedTailDoesNotFalselyCollide() {
        val state = NativeSnakeState(
            snake = listOf(SnakeCell(3, 3), SnakeCell(3, 4), SnakeCell(2, 4), SnakeCell(2, 3)),
            heading = SnakeHeading.UP, pending = SnakeHeading.LEFT,
            fruit = SnakeCell(0, 0)
        )
        val moved = NativeSnakeEngine.tick(state, Random(1))
        assertFalse(moved.over)
        assertEquals(SnakeCell(2, 3), moved.snake.first())
        assertEquals(4, moved.snake.size)
    }

    @Test fun saveRoundTripsAndRejectsCorruptedBoards() {
        val state = NativeSnakeEngine.tick(NativeSnakeEngine.newGame(Random(8)), Random(9))
        assertEquals(state, NativeSnakeEngine.decode(NativeSnakeEngine.encode(state)))
        assertNull(NativeSnakeEngine.decode(null))
        assertNull(NativeSnakeEngine.decode("not a real board"))
        assertNull(NativeSnakeEngine.decode(NativeSnakeEngine.encode(state).replace(";false;", ";bad;")))
    }
}
