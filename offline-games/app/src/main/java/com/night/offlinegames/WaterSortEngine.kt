package com.night.offlinegames

import kotlin.random.Random

/** Pure Kotlin water sorting. The final list element in a tube is its visible top. */
data class WaterSortState(
    val tubes: List<List<Int>>,
    val level: Int = 1,
    val moves: Int = 0
) {
    val solved: Boolean get() = tubes.all { it.isEmpty() || (it.size == 4 && it.all { colour -> colour == it.first() }) }
}

object WaterSortEngine {
    const val capacity = 4

    // Each hand-checked seed has a verified path to victory with two empty tubes.
    // Renaming colours and permuting tubes do not alter solvability.
    private val seeds = listOf(
        listOf(listOf(1,2,0,2), listOf(2,1,2,0), listOf(0,1,0,1)),
        listOf(listOf(3,0,2,1), listOf(1,0,1,3), listOf(2,0,1,3), listOf(2,3,2,0)),
        listOf(listOf(3,0,2,1), listOf(4,1,3,1), listOf(1,0,4,4), listOf(0,3,0,2), listOf(2,4,3,2))
    )

    fun newGame(level: Int = 1): WaterSortState {
        val safeLevel = level.coerceAtLeast(1)
        val puzzle = seeds[(safeLevel - 1).coerceAtMost(seeds.lastIndex)]
        val rng = Random(7301 + safeLevel * 997)
        val palette = (puzzle.indices).shuffled(rng)
        val shuffled = (puzzle.map { tube -> tube.map { palette[it] } } + listOf(emptyList(), emptyList())).shuffled(rng)
        return WaterSortState(shuffled, safeLevel)
    }

    /** Correct puzzle rule: pour the largest contiguous top-colour segment that fits. */
    fun pour(state: WaterSortState, from: Int, into: Int): WaterSortState? {
        if (state.solved || from == into || from !in state.tubes.indices || into !in state.tubes.indices) return null
        val a = state.tubes[from]
        val b = state.tubes[into]
        if (a.isEmpty() || b.size >= capacity) return null
        val colour = a.last()
        if (b.isNotEmpty() && b.last() != colour) return null
        val available = a.asReversed().takeWhile { it == colour }.size
        val units = minOf(available, capacity - b.size)
        if (units == 0) return null
        val next = state.tubes.mapIndexed { index, tube ->
            when (index) {
                from -> tube.dropLast(units)
                into -> tube + List(units) { colour }
                else -> tube
            }
        }
        return state.copy(tubes = next, moves = state.moves + 1)
    }

    fun encode(state: WaterSortState): String =
        "${state.level};${state.moves};" + state.tubes.joinToString("|") { it.joinToString(",") }

    /** Reject corrupt or stale saved games; never turn persistence failures into crashes. */
    fun decode(raw: String?): WaterSortState? = try {
        if (raw == null) null else {
            val fields = raw.split(";", limit = 3)
            if (fields.size != 3) null else {
                val level = fields[0].toInt()
                val moves = fields[1].toInt()
                val tubes = fields[2].split("|").map {
                    if (it.isEmpty()) emptyList() else it.split(",").map(String::toInt)
                }
                val colours = tubes.flatten().groupingBy { it }.eachCount()
                if (level < 1 || moves < 0 || tubes.size !in 5..7 ||
                    tubes.any { it.size > capacity } || colours.size != tubes.size - 2 ||
                    colours.values.any { it != capacity } || colours.keys.any { it !in 0..4 }
                ) null else WaterSortState(tubes, level, moves)
            }
        }
    } catch (_: RuntimeException) { null }
}
