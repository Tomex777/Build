package com.night.offlinegames

import kotlin.random.Random

/** A 5x5 Lights Out board encoded as 25 bits, independent of Android and WebView. */
data class NativeLightsState(
    val board: Int,
    val level: Int,
    val seed: Int,
    val moves: Int = 0,
    val history: List<Int> = emptyList()
) {
    val won: Boolean get() = board == 0
    val litCount: Int get() = Integer.bitCount(board)
}

object NativeLightsEngine {
    const val side = 5
    const val count = side * side
    private const val fullMask = (1 shl count) - 1

    fun pressMask(index: Int): Int {
        if (index !in 0 until count) return 0
        val row = index / side
        val col = index % side
        var mask = 1 shl index
        if (row > 0) mask = mask or (1 shl (index - side))
        if (row < side - 1) mask = mask or (1 shl (index + side))
        if (col > 0) mask = mask or (1 shl (index - 1))
        if (col < side - 1) mask = mask or (1 shl (index + 1))
        return mask
    }

    /** Guaranteed solvable: scramble the cleared board using valid moves. */
    fun newGame(level: Int = 1, seed: Int = 0): NativeLightsState {
        require(level in 1..10000)
        val random = Random(seed xor (level * 0x45d9f3b))
        var board = 0
        val presses = (0 until count).shuffled(random).take((5 + level / 2).coerceIn(5, 16))
        for (index in presses) board = board xor pressMask(index)
        if (board == 0) board = pressMask(12)
        return NativeLightsState(board, level, seed)
    }

    fun tap(state: NativeLightsState, index: Int): NativeLightsState {
        if (state.won || index !in 0 until count) return state
        return state.copy(
            board = state.board xor pressMask(index),
            moves = state.moves + 1,
            history = (state.history + state.board).takeLast(100)
        )
    }

    fun undo(state: NativeLightsState): NativeLightsState {
        if (state.history.isEmpty()) return state
        return state.copy(
            board = state.history.last(),
            moves = (state.moves - 1).coerceAtLeast(0),
            history = state.history.dropLast(1)
        )
    }

    /** Solve over GF(2) and enumerate free variables to minimize required presses. */
    fun solve(board: Int): List<Int>? {
        if (board !in 0..fullMask) return null
        val rows = LongArray(count) { cell ->
            var coefficients = 0L
            for (press in 0 until count) {
                if (pressMask(press) and (1 shl cell) != 0) {
                    coefficients = coefficients or (1L shl press)
                }
            }
            coefficients or (((board ushr cell) and 1).toLong() shl count)
        }
        val pivots = IntArray(count) { -1 }
        var rank = 0
        for (col in 0 until count) {
            val pivot = (rank until count).firstOrNull { rows[it] and (1L shl col) != 0L }
                ?: continue
            val old = rows[rank]
            rows[rank] = rows[pivot]
            rows[pivot] = old
            for (row in 0 until count) {
                if (row != rank && rows[row] and (1L shl col) != 0L) {
                    rows[row] = rows[row] xor rows[rank]
                }
            }
            pivots[rank] = col
            rank++
        }
        for (row in rank until count) {
            if (rows[row] and (1L shl count) != 0L) return null
        }
        val pivotColumns = pivots.take(rank).toSet()
        val free = (0 until count).filterNot { it in pivotColumns }
        if (free.size > 12) return null
        var best = -1
        var bestCount = Int.MAX_VALUE
        for (choice in 0 until (1 shl free.size)) {
            var solution = 0
            for (i in free.indices) if (choice and (1 shl i) != 0) {
                solution = solution or (1 shl free[i])
            }
            for (row in 0 until rank) {
                val coefficientMask = (rows[row] and fullMask.toLong()).toInt()
                val parity = Integer.bitCount(coefficientMask and solution) and 1
                val rhs = ((rows[row] ushr count) and 1L).toInt()
                if (parity xor rhs == 1) solution = solution or (1 shl pivots[row])
            }
            val weight = Integer.bitCount(solution)
            if (weight < bestCount) {
                best = solution
                bestCount = weight
            }
        }
        return (0 until count).filter { best and (1 shl it) != 0 }
    }

    fun encode(state: NativeLightsState): String =
        listOf("1", state.level, state.seed, state.moves, state.board,
            state.history.joinToString(",")).joinToString(";")

    fun decode(raw: String?): NativeLightsState? = try {
        if (raw == null) null else {
            val parts = raw.split(';')
            if (parts.size != 6 || parts[0] != "1") null else {
                val history = if (parts[5].isEmpty()) emptyList() else parts[5].split(',').map { it.toInt() }
                val state = NativeLightsState(parts[4].toInt(), parts[1].toInt(),
                    parts[2].toInt(), parts[3].toInt(), history)
                if (state.level !in 1..10000 || state.board !in 0..fullMask ||
                    state.moves !in 0..100000 || history.size > 100 || history.size > state.moves ||
                    history.any { it !in 0..fullMask } || solve(state.board) == null
                ) null else state
            }
        }
    } catch (_: RuntimeException) { null }
}
