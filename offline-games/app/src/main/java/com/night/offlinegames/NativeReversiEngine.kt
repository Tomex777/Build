package com.night.offlinegames

/** Traditional 8×8 Othello/Reversi against an offline Kotlin AI. */
data class NativeReversiState(
    val cells: List<Int>,
    val turns: Int = 0,
    val finished: Boolean = false
) {
    init { require(cells.size == 64) }
    val black: Int get() = cells.count { it == 1 }
    val white: Int get() = cells.count { it == 2 }
}

object NativeReversiEngine {
    private val rays = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1
    )

    fun newGame(): NativeReversiState {
        val board = MutableList(64) { 0 }
        board[3 * 8 + 3] = 2
        board[4 * 8 + 4] = 2
        board[3 * 8 + 4] = 1
        board[4 * 8 + 3] = 1
        return NativeReversiState(board)
    }

    /** Indices to flip for a legal placement. Empty means forbidden. */
    fun flips(board: List<Int>, player: Int, at: Int): List<Int> {
        if (player !in 1..2 || board.size != 64 || at !in 0..63 || board[at] != 0) return emptyList()
        val x = at % 8
        val y = at / 8
        val other = 3 - player
        val result = ArrayList<Int>(12)
        for ((dx, dy) in rays) {
            var cx = x + dx
            var cy = y + dy
            val between = ArrayList<Int>()
            while (cx in 0..7 && cy in 0..7 && board[cy * 8 + cx] == other) {
                between.add(cy * 8 + cx)
                cx += dx
                cy += dy
            }
            if (between.isNotEmpty() && cx in 0..7 && cy in 0..7 &&
                board[cy * 8 + cx] == player
            ) result.addAll(between)
        }
        return result
    }

    fun legal(board: List<Int>, player: Int): List<Int> =
        board.indices.filter { flips(board, player, it).isNotEmpty() }

    private fun put(board: List<Int>, player: Int, at: Int): List<Int> {
        val flipped = flips(board, player, at)
        if (flipped.isEmpty()) return board
        val next = board.toMutableList()
        next[at] = player
        flipped.forEach { next[it] = player }
        return next
    }

    fun play(state: NativeReversiState, index: Int): NativeReversiState {
        if (state.finished || flips(state.cells, 1, index).isEmpty()) return state
        var board = put(state.cells, 1, index)
        // Computer may get consecutive turns when the human cannot move.
        var remaining = 64 - board.count { it != 0 }
        while (remaining > 0 && legal(board, 2).isNotEmpty()) {
            val choice = chooseComputerMove(board)
            board = put(board, 2, choice)
            remaining--
            if (legal(board, 1).isNotEmpty()) break
        }
        val end = legal(board, 1).isEmpty() && legal(board, 2).isEmpty()
        return NativeReversiState(board, state.turns + 1, end)
    }

    fun chooseComputerMove(board: List<Int>): Int {
        val choices = legal(board, 2)
        if (choices.isEmpty()) return -1
        val corners = setOf(0, 7, 56, 63)
        val danger = mapOf(
            9 to 0, 14 to 7, 49 to 56, 54 to 63
        )
        return choices.maxWithOrNull(compareBy<Int> { at ->
            val next = put(board, 2, at)
            val x = at % 8
            val y = at / 8
            val cornerValue = if (at in corners) 120 else 0
            val edgeValue = if (x == 0 || x == 7 || y == 0 || y == 7) 15 else 0
            val xPenalty = if (danger[at]?.let { board[it] == 0 } == true) -70 else 0
            val mobility = -legal(next, 1).size * 5
            val flips = (next.count { it == 2 } - board.count { it == 2 }) * 2
            cornerValue + edgeValue + xPenalty + mobility + flips
        }.thenByDescending { -it }) ?: choices.first()
    }

    fun encode(s: NativeReversiState): String =
        s.cells.joinToString("") + ";${s.turns};${s.finished}"

    fun decode(raw: String?): NativeReversiState? = try {
        if (raw == null) null else {
            val a = raw.split(";")
            if (a.size != 3 || a[0].length != 64) null else {
                val board = a[0].map { it.digitToInt() }
                val turns = a[1].toInt()
                val finished = a[2].toBooleanStrict()
                if (turns < 0 || board.any { it !in 0..2 } ||
                    board.count { it != 0 } < 4 ||
                    (finished && (legal(board, 1).isNotEmpty() || legal(board, 2).isNotEmpty()))
                ) null else NativeReversiState(board, turns, finished)
            }
        }
    } catch (_: RuntimeException) { null }
}
