package com.night.offlinegames

import kotlin.math.abs
import kotlin.random.Random

data class NativeSlidingState(
    val tiles: List<Int>,
    val level: Int = 1,
    val seed: Int = 0,
    val moves: Int = 0,
    val history: List<List<Int>> = emptyList()
) {
    val won get() = tiles == NativeSlidingEngine.solved
    val empty get() = tiles.indexOf(0)
}

object NativeSlidingEngine {
    val solved = (1..15).toList() + 0
    fun adjacent(index: Int): List<Int> {
        if (index !in 0..15) return emptyList()
        val row = index / 4
        val col = index % 4
        return listOfNotNull(
            (index - 4).takeIf { row > 0 },
            (index + 4).takeIf { row < 3 },
            (index - 1).takeIf { col > 0 },
            (index + 1).takeIf { col < 3 }
        )
    }
    fun solvable(tiles: List<Int>): Boolean {
        if (tiles.size != 16 || tiles.sorted() != (0..15).toList()) return false
        var inversions = 0
        for (i in 0..15) for (j in i + 1..15) {
            if (tiles[i] > 0 && tiles[j] > 0 && tiles[i] > tiles[j]) inversions++
        }
        return (inversions + 4 - tiles.indexOf(0) / 4) % 2 == 1
    }
    fun distance(tiles: List<Int>) = tiles.indices.sumOf {
        val tile = tiles[it]
        if (tile == 0) 0 else abs(it / 4 - (tile - 1) / 4) +
            abs(it % 4 - (tile - 1) % 4)
    }
    fun newGame(level: Int = 1, seed: Int = 0): NativeSlidingState {
        require(level in 1..10000)
        val random = Random(seed xor (level * 0x45d9f3b))
        var tiles: List<Int>
        do {
            val board = solved.toMutableList()
            var empty = 15
            var previous = -1
            repeat((45 + level * 9).coerceAtMost(220)) {
                val options = adjacent(empty).filterNot { it == previous }
                val next = options[random.nextInt(options.size)]
                board[empty] = board[next]
                board[next] = 0
                previous = empty
                empty = next
            }
            tiles = board
        } while (tiles == solved || distance(tiles) < (7 + level / 2).coerceAtMost(18))
        return NativeSlidingState(tiles, level, seed)
    }
    fun move(state: NativeSlidingState, index: Int): NativeSlidingState {
        if (state.won || index !in adjacent(state.empty)) return state
        val tiles = state.tiles.toMutableList()
        tiles[state.empty] = tiles[index]
        tiles[index] = 0
        return state.copy(tiles = tiles, moves = state.moves + 1,
            history = (state.history + listOf(state.tiles)).takeLast(100))
    }
    fun undo(state: NativeSlidingState) = if (state.history.isEmpty()) state
        else state.copy(tiles = state.history.last(),
            moves = (state.moves - 1).coerceAtLeast(0),
            history = state.history.dropLast(1))

    fun encode(state: NativeSlidingState) =
        listOf("1", state.level, state.seed, state.moves,
            state.tiles.joinToString(","), state.history.joinToString("|") {
                it.joinToString(",")
            }).joinToString(";")

    fun decode(raw: String?): NativeSlidingState? = try {
        if (raw == null) null else {
            val parts = raw.split(';')
            if (parts.size != 6 || parts[0] != "1") null else {
                fun board(value: String) = value.split(',').map(String::toInt)
                val history = if (parts[5].isEmpty()) emptyList()
                    else parts[5].split('|').map(::board)
                val state = NativeSlidingState(board(parts[4]), parts[1].toInt(),
                    parts[2].toInt(), parts[3].toInt(), history)
                if (state.level !in 1..10000 || state.moves !in 0..100000 ||
                    history.size > 100 || history.size > state.moves ||
                    !solvable(state.tiles) || history.any { !solvable(it) }) null
                else state
            }
        }
    } catch (_: RuntimeException) { null }
}
