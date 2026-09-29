package com.tomex777.cubic

import kotlin.math.abs
import kotlin.random.Random

enum class Axis { X, Y, Z }
enum class Direction { POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z }

enum class StickerColor(val rgba: FloatArray) {
    WHITE(floatArrayOf(0.96f, 0.97f, 1.00f, 1f)),
    YELLOW(floatArrayOf(1.00f, 0.84f, 0.16f, 1f)),
    RED(floatArrayOf(0.90f, 0.18f, 0.20f, 1f)),
    ORANGE(floatArrayOf(1.00f, 0.43f, 0.12f, 1f)),
    BLUE(floatArrayOf(0.12f, 0.38f, 0.95f, 1f)),
    GREEN(floatArrayOf(0.12f, 0.72f, 0.38f, 1f))
}

data class Move(val axis: Axis, val layer: Int, val quarterTurns: Int, val label: String) {
    fun inverse(): Move {
        val base = label.removeSuffix("'").removeSuffix("2")
        val turns = if (abs(quarterTurns) == 2) 2 else -quarterTurns
        return copy(
            quarterTurns = turns,
            label = when {
                abs(turns) == 2 -> base + "2"
                turns < 0 -> base + "'"
                else -> base
            }
        )
    }
}

data class CubieSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
    val stickers: Map<Direction, StickerColor>
)

data class PuzzleSnapshot(
    val width: Int,
    val height: Int,
    val depth: Int,
    val cubies: List<CubieSnapshot>
)

private data class Cubie(
    var x: Int,
    var y: Int,
    var z: Int,
    var stickers: MutableMap<Direction, StickerColor>
)

class PuzzleState(width: Int = 3, height: Int = 3, depth: Int = 3) {
    var width: Int = width.coerceIn(2, 9)
        private set
    var height: Int = height.coerceIn(2, 9)
        private set
    var depth: Int = depth.coerceIn(2, 9)
        private set

    private val cubies = mutableListOf<Cubie>()
    private val history = mutableListOf<Move>()
    private var solvedFingerprint = ""

    init { rebuild() }

    fun resize(width: Int, height: Int, depth: Int) {
        this.width = width.coerceIn(2, 9)
        this.height = height.coerceIn(2, 9)
        this.depth = depth.coerceIn(2, 9)
        rebuild()
    }

    fun reset() = rebuild()
    fun canUndo(): Boolean = history.isNotEmpty()
    fun moveCount(): Int = history.size
    fun isSolved(): Boolean = fingerprint() == solvedFingerprint
    fun nextSolutionMove(): Move? = history.lastOrNull()?.inverse()

    fun solveNextStep(): Move? {
        val last = history.removeLastOrNull() ?: return null
        val solutionMove = last.inverse()
        applyMove(solutionMove, recordHistory = false)
        return solutionMove
    }

    fun snapshot(): PuzzleSnapshot = PuzzleSnapshot(
        width, height, depth,
        cubies.map { CubieSnapshot(it.x, it.y, it.z, it.stickers.toMap()) }
    )

    fun turnOuter(axis: Axis, positive: Boolean = true): Move {
        val layer = when (axis) {
            Axis.X -> width - 1
            Axis.Y -> height - 1
            Axis.Z -> depth - 1
        }
        val label = when (axis) {
            Axis.X -> "R"
            Axis.Y -> "U"
            Axis.Z -> "F"
        }
        return applyMove(Move(axis, layer, if (positive) 1 else -1, label))
    }

    fun applyMove(move: Move, recordHistory: Boolean = true): Move {
        val turns = normalizeTurns(move.axis, move.quarterTurns)
        val baseLabel = move.label.removeSuffix("'").removeSuffix("2")
        val effective = move.copy(
            quarterTurns = turns,
            label = when {
                abs(turns) == 2 -> baseLabel + "2"
                turns < 0 -> baseLabel + "'"
                turns > 0 -> baseLabel
                else -> move.label
            }
        )

        cubies.filter { belongsToLayer(it, move.axis, move.layer) }.forEach { cubie ->
            rotatePosition(cubie, move.axis, turns)
            cubie.stickers = cubie.stickers
                .mapKeys { (direction, _) -> rotateDirection(direction, move.axis, turns) }
                .toMutableMap()
        }
        if (recordHistory) history += effective
        return effective
    }

    fun undo(): Move? {
        val last = history.removeLastOrNull() ?: return null
        applyMove(last.inverse(), recordHistory = false)
        return last
    }

    fun scramble(moveCount: Int = 18, random: Random = Random.Default): List<Move> {
        val moves = mutableListOf<Move>()
        repeat(moveCount.coerceIn(1, 100)) {
            val axis = Axis.entries[random.nextInt(Axis.entries.size)]
            val dimension = when (axis) {
                Axis.X -> width
                Axis.Y -> height
                Axis.Z -> depth
            }
            val layer = random.nextInt(dimension)
            val turn = if (random.nextBoolean()) 1 else -1
            val prefix = when (axis) {
                Axis.X -> "X${layer + 1}"
                Axis.Y -> "Y${layer + 1}"
                Axis.Z -> "Z${layer + 1}"
            }
            moves += applyMove(Move(axis, layer, turn, prefix))
        }
        return moves
    }

    fun solutionFromHistory(): List<Move> = history.asReversed().map { it.inverse() }

    fun describe(move: Move): String {
        val dimension = when (move.axis) {
            Axis.X -> width
            Axis.Y -> height
            Axis.Z -> depth
        }
        val face = when {
            move.axis == Axis.X && move.layer == width - 1 -> "right layer"
            move.axis == Axis.X && move.layer == 0 -> "left layer"
            move.axis == Axis.Y && move.layer == height - 1 -> "top layer"
            move.axis == Axis.Y && move.layer == 0 -> "bottom layer"
            move.axis == Axis.Z && move.layer == depth - 1 -> "front layer"
            move.axis == Axis.Z && move.layer == 0 -> "back layer"
            else -> "${move.axis.name} layer ${move.layer + 1} of $dimension"
        }
        val turn = when {
            abs(move.quarterTurns) == 2 -> "a half-turn"
            move.quarterTurns > 0 -> "a quarter-turn"
            else -> "a reverse quarter-turn"
        }
        return "Turn the $face $turn."
    }

    private fun normalizeTurns(axis: Axis, requested: Int): Int {
        val sign = if (requested < 0) -1 else 1
        val magnitude = abs(requested) % 4
        if (magnitude == 0) return 0
        if (magnitude == 2) return 2 * sign
        val quarterTurnFits = when (axis) {
            Axis.X -> height == depth
            Axis.Y -> width == depth
            Axis.Z -> width == height
        }
        return if (quarterTurnFits) sign else 2
    }

    private fun belongsToLayer(cubie: Cubie, axis: Axis, layer: Int): Boolean = when (axis) {
        Axis.X -> cubie.x == layer
        Axis.Y -> cubie.y == layer
        Axis.Z -> cubie.z == layer
    }

    private fun rotatePosition(cubie: Cubie, axis: Axis, turns: Int) {
        if (turns == 0) return
        if (abs(turns) == 2) {
            when (axis) {
                Axis.X -> { cubie.y = height - 1 - cubie.y; cubie.z = depth - 1 - cubie.z }
                Axis.Y -> { cubie.x = width - 1 - cubie.x; cubie.z = depth - 1 - cubie.z }
                Axis.Z -> { cubie.x = width - 1 - cubie.x; cubie.y = height - 1 - cubie.y }
            }
            return
        }
        when (axis) {
            Axis.X -> {
                val oldY = cubie.y; val oldZ = cubie.z
                if (turns > 0) { cubie.y = height - 1 - oldZ; cubie.z = oldY }
                else { cubie.y = oldZ; cubie.z = depth - 1 - oldY }
            }
            Axis.Y -> {
                val oldX = cubie.x; val oldZ = cubie.z
                if (turns > 0) { cubie.x = oldZ; cubie.z = depth - 1 - oldX }
                else { cubie.x = width - 1 - oldZ; cubie.z = oldX }
            }
            Axis.Z -> {
                val oldX = cubie.x; val oldY = cubie.y
                if (turns > 0) { cubie.x = width - 1 - oldY; cubie.y = oldX }
                else { cubie.x = oldY; cubie.y = height - 1 - oldX }
            }
        }
    }

    private fun rotateDirection(direction: Direction, axis: Axis, turns: Int): Direction {
        val steps = ((turns % 4) + 4) % 4
        var result = direction
        repeat(steps) {
            result = when (axis) {
                Axis.X -> when (result) {
                    Direction.POS_Y -> Direction.POS_Z
                    Direction.POS_Z -> Direction.NEG_Y
                    Direction.NEG_Y -> Direction.NEG_Z
                    Direction.NEG_Z -> Direction.POS_Y
                    else -> result
                }
                Axis.Y -> when (result) {
                    Direction.POS_X -> Direction.NEG_Z
                    Direction.NEG_Z -> Direction.NEG_X
                    Direction.NEG_X -> Direction.POS_Z
                    Direction.POS_Z -> Direction.POS_X
                    else -> result
                }
                Axis.Z -> when (result) {
                    Direction.POS_X -> Direction.POS_Y
                    Direction.POS_Y -> Direction.NEG_X
                    Direction.NEG_X -> Direction.NEG_Y
                    Direction.NEG_Y -> Direction.POS_X
                    else -> result
                }
            }
        }
        return result
    }

    private fun rebuild() {
        history.clear()
        cubies.clear()
        for (x in 0 until width) for (y in 0 until height) for (z in 0 until depth) {
            val stickers = mutableMapOf<Direction, StickerColor>()
            if (x == width - 1) stickers[Direction.POS_X] = StickerColor.RED
            if (x == 0) stickers[Direction.NEG_X] = StickerColor.ORANGE
            if (y == height - 1) stickers[Direction.POS_Y] = StickerColor.WHITE
            if (y == 0) stickers[Direction.NEG_Y] = StickerColor.YELLOW
            if (z == depth - 1) stickers[Direction.POS_Z] = StickerColor.GREEN
            if (z == 0) stickers[Direction.NEG_Z] = StickerColor.BLUE
            cubies += Cubie(x, y, z, stickers)
        }
        solvedFingerprint = fingerprint()
    }

    private fun fingerprint(): String = cubies
        .sortedWith(compareBy<Cubie> { it.x }.thenBy { it.y }.thenBy { it.z })
        .joinToString("|") { cubie ->
            buildString {
                append(cubie.x).append(',').append(cubie.y).append(',').append(cubie.z).append(':')
                Direction.entries.forEach { direction ->
                    append(direction.name).append('=')
                    append(cubie.stickers[direction]?.name ?: "_").append(';')
                }
            }
        }
}
