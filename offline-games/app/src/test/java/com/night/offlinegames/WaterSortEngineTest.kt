package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

class WaterSortEngineTest {
    @Test fun seededLevelsHaveFourOfEachColourAndTwoEmptyTubes() {
        for (level in 1..9) {
            val state = WaterSortEngine.newGame(level)
            val histogram = state.tubes.flatten().groupingBy { it }.eachCount()
            assertEquals(state.tubes.size - 2, histogram.size)
            assertTrue(histogram.values.all { it == 4 })
            assertEquals(2, state.tubes.count { it.isEmpty() })
            assertFalse(state.solved)
        }
    }

    /** Tests win-reachability, not just the presence of a game tile or script. */
    @Test fun everyShippedPuzzleHasPathToSolved() {
        for (level in 1..9) {
            val initial = WaterSortEngine.newGame(level)
            val queue = ArrayDeque<WaterSortState>()
            val visited = HashSet<String>()
            fun key(s: WaterSortState) =
                s.tubes.map { it.joinToString(",") }.sorted().joinToString("|")
            queue.add(initial)
            visited.add(key(initial))
            var found = false
            while (!queue.isEmpty() && visited.size < 40_000) {
                val current = queue.removeFirst()
                if (current.solved) { found = true; break }
                for (from in current.tubes.indices) for (to in current.tubes.indices) {
                    val next = WaterSortEngine.pour(current, from, to) ?: continue
                    if (visited.add(key(next))) queue.add(next)
                }
            }
            assertTrue("Unsolvable level $level, visited ${visited.size}", found)
        }
    }

    @Test fun poursOnlyMatchingTopSegmentsAndNeverOverflow() {
        val state = WaterSortState(
            listOf(listOf(0,1,1), listOf(1,1), emptyList(), listOf(0,0,0,0), emptyList()),
            1
        )
        assertNull(WaterSortEngine.pour(state, 0, 3))
        assertNull(WaterSortEngine.pour(state, 0, 0))
        assertNull(WaterSortEngine.pour(state, 4, 2))
        val poured = WaterSortEngine.pour(state, 0, 1)
        assertNotNull(poured)
        assertEquals(listOf(0), poured!!.tubes[0])
        assertEquals(listOf(1,1,1,1), poured.tubes[1])
        assertEquals(1, poured.moves)
        assertEquals(listOf(0,1,1), state.tubes[0])
    }

    @Test fun progressionSaveRestoresAndBadDataIsRejected() {
        val start = WaterSortEngine.newGame(2)
        assertEquals(start, WaterSortEngine.decode(WaterSortEngine.encode(start)))
        assertNull(WaterSortEngine.decode("corrupt"))
        assertNull(WaterSortEngine.decode("2;-8;1,2|"))
    }
}
