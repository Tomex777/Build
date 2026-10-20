package com.night.offlinegames

/** Native, deterministic, unbeatable offline Tic-Tac-Toe opponent. */
data class NativeTicState(val squares: List<Int> = List(9) { 0 }, val result: Int = 0) {
    init { require(squares.size == 9) }
}

object NativeTicTacToe {
    private val lines = arrayOf(
        intArrayOf(0,1,2),intArrayOf(3,4,5),intArrayOf(6,7,8),
        intArrayOf(0,3,6),intArrayOf(1,4,7),intArrayOf(2,5,8),
        intArrayOf(0,4,8),intArrayOf(2,4,6)
    )
    /** 0 ongoing, 1 human, 2 CPU, 3 draw. */
    fun result(squares: List<Int>): Int {
        for (line in lines) {
            val mark = squares[line[0]]
            if (mark != 0 && line.all { squares[it] == mark }) return mark
        }
        return if (squares.none { it == 0 }) 3 else 0
    }

    fun move(state: NativeTicState, position: Int): NativeTicState {
        if (state.result != 0 || position !in 0..8 || state.squares[position] != 0) return state
        val board = state.squares.toMutableList()
        board[position] = 1
        var outcome = result(board)
        if (outcome == 0) {
            val pick = bestCpuMove(board)
            if (pick >= 0) board[pick] = 2
            outcome = result(board)
        }
        return NativeTicState(board, outcome)
    }

    /** Alpha-beta on only nine squares; cached states keep taps instant. */
    fun bestCpuMove(current: List<Int>): Int {
        if (result(current) != 0) return -1
        val board = current.toMutableList()
        val cached = HashMap<String, Int>()
        fun score(cpuTurn: Boolean, alphaInit: Int, betaInit: Int): Int {
            val terminal = result(board)
            if (terminal != 0) return when (terminal) {
                2 -> 10 + board.count { it == 0 }
                1 -> -10 - board.count { it == 0 }
                else -> 0
            }
            val key = board.joinToString("") + if (cpuTurn) "O" else "X"
            cached[key]?.let { return it }
            var alpha = alphaInit
            var beta = betaInit
            var best = if (cpuTurn) -100 else 100
            for (index in listOf(4,0,2,6,8,1,3,5,7)) {
                if (board[index] != 0) continue
                board[index] = if (cpuTurn) 2 else 1
                val value = score(!cpuTurn, alpha, beta)
                board[index] = 0
                best = if (cpuTurn) maxOf(best, value) else minOf(best, value)
                if (cpuTurn) alpha = maxOf(alpha, best) else beta = minOf(beta, best)
                if (alpha >= beta) break
            }
            // Cache only when no pruning occurred (otherwise result may be a bound).
            if (alpha < beta) cached[key] = best
            return best
        }
        var choice = -1
        var best = -100
        for (i in listOf(4,0,2,6,8,1,3,5,7)) {
            if (board[i] != 0) continue
            board[i] = 2
            val value = score(false, -100, 100)
            board[i] = 0
            if (value > best) { best = value; choice = i }
        }
        return choice
    }

    fun encode(s: NativeTicState) = s.squares.joinToString("") + ";" + s.result
    fun decode(raw: String?): NativeTicState? = try {
        if (raw == null) null else {
            val parts = raw.split(";")
            if (parts.size != 2 || parts[0].length != 9) null else {
                val cells = parts[0].map { it.digitToInt() }
                val outcome = parts[1].toInt()
                if (cells.any { it !in 0..2 } || outcome !in 0..3 ||
                    result(cells) != outcome ||
                    cells.count { it == 1 } !in cells.count { it == 2 }..cells.count { it == 2 } + 1
                ) null else NativeTicState(cells,outcome)
            }
        }
    } catch (_: RuntimeException) { null }
}
