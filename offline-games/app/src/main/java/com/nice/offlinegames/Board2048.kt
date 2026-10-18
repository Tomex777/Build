package com.nice.offlinegames

import kotlin.random.Random

enum class Direction { LEFT, RIGHT, UP, DOWN }

data class BoardState(val values: List<Int>, val score: Int = 0) {
    init { require(values.size == 16) }
    val won: Boolean get() = values.any { it >= 2048 }
    val gameOver: Boolean get() = !Board2048.canMove(this)
}

/** Pure game engine; deterministic when passed a seeded Random in tests. */
object Board2048 {
    fun newGame(random: Random = Random.Default): BoardState =
        addTile(addTile(BoardState(List(16) { 0 }), random), random)

    fun canMove(state: BoardState): Boolean {
        val a = state.values
        if (a.any { it == 0 }) return true
        for (y in 0 until 4) for (x in 0 until 4) {
            if (x < 3 && a[y * 4 + x] == a[y * 4 + x + 1]) return true
            if (y < 3 && a[y * 4 + x] == a[(y + 1) * 4 + x]) return true
        }
        return false
    }

    fun move(state: BoardState, direction: Direction, random: Random = Random.Default): BoardState {
        val output = state.values.toMutableList()
        var gain = 0
        for (line in 0 until 4) {
            val indices = (0 until 4).map { step ->
                when (direction) {
                    Direction.LEFT -> line * 4 + step
                    Direction.RIGHT -> line * 4 + (3 - step)
                    Direction.UP -> step * 4 + line
                    Direction.DOWN -> (3 - step) * 4 + line
                }
            }
            val nonzero = indices.map { state.values[it] }.filter { it != 0 }
            val combined = ArrayList<Int>(4)
            var cursor = 0
            while (cursor < nonzero.size) {
                if (cursor + 1 < nonzero.size && nonzero[cursor] == nonzero[cursor + 1]) {
                    val merged = nonzero[cursor] * 2
                    combined.add(merged)
                    gain += merged
                    cursor += 2
                } else {
                    combined.add(nonzero[cursor])
                    cursor++
                }
            }
            indices.forEachIndexed { at, index -> output[index] = combined.getOrElse(at) { 0 } }
        }
        // A blocked swipe is a no-op: it must not create a random tile or lose points.
        if (output == state.values) return state
        return addTile(BoardState(output, state.score + gain), random)
    }

    private fun addTile(state: BoardState, random: Random): BoardState {
        val free = state.values.indices.filter { state.values[it] == 0 }
        if (free.isEmpty()) return state
        val values = state.values.toMutableList()
        values[free[random.nextInt(free.size)]] = if (random.nextInt(10) == 0) 4 else 2
        return state.copy(values = values)
    }

    fun encode(state: BoardState): String =
        state.values.joinToString(",") + ";" + state.score

    fun decode(serialized: String?): BoardState? {
        return try {
        if (serialized == null) return null
        val parts = serialized.split(";")
        if (parts.size != 2) return null
        val values = parts[0].split(",").map { it.toInt() }
        if (values.size != 16 || values.any { it < 0 || (it != 0 && (it and (it - 1)) != 0) }) return null
        val score = parts[1].toInt()
        if (score < 0) return null
        BoardState(values, score)
        } catch (_: Exception) { null }
    }
}
