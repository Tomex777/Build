package com.night.offlinegames

import kotlin.random.Random

/** A deterministic, offline 5x5 Lights Out puzzle. */
data class NativeLightsState(
    val board: Int, val level: Int, val seed: Int,
    val moves: Int = 0, val history: List<Int> = emptyList()
) {
    val won get() = board == 0
    val litCount get() = Integer.bitCount(board)
}

object NativeLightsEngine {
    const val side = 5
    const val count = 25
    private const val mask = (1 shl count) - 1

    fun pressMask(index: Int): Int {
        if (index !in 0 until count) return 0
        val r = index / side
        val c = index % side
        var bits = 1 shl index
        if (r > 0) bits = bits or (1 shl (index - side))
        if (r < 4) bits = bits or (1 shl (index + side))
        if (c > 0) bits = bits or (1 shl (index - 1))
        if (c < 4) bits = bits or (1 shl (index + 1))
        return bits
    }

    /** Scramble from the solved state, never from an arbitrary unsolvable board. */
    fun newGame(level: Int = 1, seed: Int = 0): NativeLightsState {
        require(level in 1..10000)
        var board = 0
        for (index in (0 until count).shuffled(Random(seed xor (level * 73129)))
            .take((5 + level / 2).coerceIn(5, 16))) board = board xor pressMask(index)
        return NativeLightsState(if (board == 0) pressMask(12) else board, level, seed)
    }

    fun tap(s: NativeLightsState, index: Int): NativeLightsState =
        if (s.won || index !in 0 until count) s else s.copy(
            board = s.board xor pressMask(index), moves = s.moves + 1,
            history = (s.history + s.board).takeLast(100))

    fun undo(s: NativeLightsState): NativeLightsState =
        if (s.history.isEmpty()) s else s.copy(
            board = s.history.last(), moves = (s.moves - 1).coerceAtLeast(0),
            history = s.history.dropLast(1))

    fun encode(s: NativeLightsState): String =
        listOf("1", s.level, s.seed, s.moves, s.board,
            s.history.joinToString(",")).joinToString(";")

    fun decode(raw: String?): NativeLightsState? = try {
        val p = raw?.split(';')
        if (p == null || p.size != 6 || p[0] != "1") null else {
            val history = if (p[5].isEmpty()) emptyList() else p[5].split(',').map { it.toInt() }
            val s = NativeLightsState(p[4].toInt(), p[1].toInt(),
                p[2].toInt(), p[3].toInt(), history)
            if (s.level !in 1..10000 || s.board !in 0..mask ||
                s.moves !in 0..100000 || history.size > 100 ||
                history.size > s.moves || history.any { it !in 0..mask } ||
                NativeLightsSolver.solve(s.board) == null) null else s
        }
    } catch (_: RuntimeException) { null }
}
