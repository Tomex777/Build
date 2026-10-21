package com.night.offlinegames

import kotlin.random.Random

/** Pure Kotlin Minesweeper rules; the first reveal is guaranteed to have no adjacent mines. */
data class NativeMinesState(
    val mines: Set<Int> = emptySet(),
    val revealed: Set<Int> = emptySet(),
    val flags: Set<Int> = emptySet(),
    val started: Boolean = false,
    val lost: Boolean = false,
    val won: Boolean = false
)

object NativeMinesEngine {
    const val side = 9
    const val count = side * side
    const val mineCount = 10

    fun newGame() = NativeMinesState()

    fun neighbors(index: Int): List<Int> {
        if (index !in 0 until count) return emptyList()
        val x = index % side
        val y = index / side
        return (-1..1).flatMap { dy ->
            (-1..1).mapNotNull { dx ->
                val nx = x + dx
                val ny = y + dy
                if ((dx == 0 && dy == 0) || nx !in 0 until side || ny !in 0 until side) null
                else ny * side + nx
            }
        }
    }

    fun adjacent(state: NativeMinesState, index: Int): Int =
        neighbors(index).count { it in state.mines }

    fun toggleFlag(state: NativeMinesState, index: Int): NativeMinesState {
        if (index !in 0 until count || state.lost || state.won || index in state.revealed) return state
        return if (index in state.flags) state.copy(flags = state.flags - index)
        else if (state.flags.size < mineCount) state.copy(flags = state.flags + index)
        else state
    }

    fun reveal(state: NativeMinesState, index: Int, random: Random = Random.Default): NativeMinesState {
        if (index !in 0 until count || state.lost || state.won ||
            index in state.flags || index in state.revealed) return state

        val bombs = if (state.started) state.mines else {
            val safe = neighbors(index).toSet() + index
            (0 until count).filterNot { it in safe }.shuffled(random).take(mineCount).toSet()
        }
        if (index in bombs) {
            return state.copy(mines = bombs, started = true, revealed = state.revealed + index, lost = true)
        }
        val opened = state.revealed.toMutableSet()
        val queue = ArrayDeque<Int>()
        queue.addLast(index)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell in opened || cell in state.flags || cell in bombs) continue
            opened.add(cell)
            if (neighbors(cell).none { it in bombs }) {
                neighbors(cell).filterTo(queue) { it !in opened && it !in state.flags && it !in bombs }
            }
        }
        return state.copy(
            mines = bombs, started = true, revealed = opened,
            won = opened.size == count - mineCount
        )
    }

    /** A numbered revealed square opens its unflagged neighbors when flags match the number. */
    fun chord(state: NativeMinesState, index: Int): NativeMinesState {
        if (!state.started || state.lost || state.won || index !in state.revealed) return state
        val around = neighbors(index)
        if (around.count { it in state.flags } != adjacent(state, index)) return state
        var next = state
        for (cell in around) {
            if (cell !in next.flags && cell !in next.revealed) {
                next = reveal(next, cell)
                if (next.lost || next.won) break
            }
        }
        return next
    }

    fun encode(state: NativeMinesState): String {
        fun bits(set: Set<Int>) = (0 until count).joinToString("") { if (it in set) "1" else "0" }
        return listOf("1", state.started, state.lost, state.won,
            bits(state.mines), bits(state.revealed), bits(state.flags)).joinToString(";")
    }

    fun decode(raw: String?): NativeMinesState? = try {
        if (raw == null) null else {
            val parts = raw.split(";")
            if (parts.size != 7 || parts[0] != "1") null else {
                fun parseBits(s: String): Set<Int> {
                    require(s.length == count && s.all { it == '0' || it == '1' })
                    return s.indices.filterTo(mutableSetOf()) { s[it] == '1' }
                }
                val state = NativeMinesState(
                    mines = parseBits(parts[4]), revealed = parseBits(parts[5]),
                    flags = parseBits(parts[6]), started = parts[1].toBooleanStrict(),
                    lost = parts[2].toBooleanStrict(), won = parts[3].toBooleanStrict()
                )
                if (state.flags.size > mineCount || state.flags.any { it in state.revealed } ||
                    (state.started && state.mines.size != mineCount) ||
                    (!state.started && (state.mines.isNotEmpty() || state.revealed.isNotEmpty() ||
                        state.lost || state.won)) ||
                    (state.lost && state.revealed.none { it in state.mines }) ||
                    (!state.lost && state.revealed.any { it in state.mines }) ||
                    (state.won != (state.started && !state.lost &&
                        state.revealed.size == count - mineCount))
                ) null else state
            }
        }
    } catch (_: RuntimeException) { null }
}
