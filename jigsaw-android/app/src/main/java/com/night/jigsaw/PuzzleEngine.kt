package com.night.jigsaw

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** View-independent puzzle rules in a 1000-unit world; no Android or browser dependency. */
data class PuzzlePiece(
    val id: Int, val row: Int, val col: Int,
    var x: Float, var y: Float, var placed: Boolean = false
)

class PuzzleGame private constructor(
    val cols: Int, val rows: Int, val seed: Long,
    val imageKey: String, val pieces: MutableList<PuzzlePiece>,
    var hintsRemaining: Int = 3,
    var moves: Int = 0,
    var preview: Boolean = false,
    var landscapeMode: Boolean = false
) {
    companion object {
        const val WORLD_WIDTH = 1000f
        const val BOARD_LEFT = 80f
        const val BOARD_WIDTH = 840f
        const val BOARD_HEIGHT = 630f

        private data class ScatterLayout(
            val perRow: Int, val stepX: Float, val stepY: Float,
            val firstX: Float, val firstY: Float,
            val secondX: Float, val secondY: Float,
            val top: Float, val left: Float, val worldWidth: Float, val end: Float
        )

        // Reserve enough tabletop for every real silhouette, including protruding tabs.
        // Packing uses non-overlapping hit footprints rather than pile-on-top scatter.
        private fun scatterLayout(cols: Int, rows: Int, landscape: Boolean): ScatterLayout {
            val pw=BOARD_WIDTH/cols
            val ph=BOARD_HEIGHT/rows
            val depth=min(pw,ph)*.30f
            val stepX=pw+2f*depth+10f
            val stepY=ph+2f*depth+10f
            val half=(cols*rows+1)/2
            if (landscape) {
                val perRow=(350f/stepX).toInt().coerceIn(1,16)
                val zoneRows=(half+perRow-1)/perRow
                val boardTop=maxOf(80f, 80f + (zoneRows*stepY-BOARD_HEIGHT)/2f)
                val end=maxOf(boardTop+BOARD_HEIGHT+110f,80f+zoneRows*stepY+110f)
                return ScatterLayout(perRow,stepX,stepY,
                    32f+depth,80f+depth,1322f+depth,80f+depth,
                    boardTop,430f,1700f,end)
            }
            val perRow=(900f/stepX).toInt().coerceIn(1,16)
            val upperRows=(half+perRow-1)/perRow
            val lowerRows=(cols*rows-half+perRow-1)/perRow
            val firstX=(WORLD_WIDTH-perRow*stepX)/2f+depth+4f
            val firstY=76f+depth
            val boardTop=70f+upperRows*stepY+110f
            val bottom=boardTop+BOARD_HEIGHT+110f
            val otherY=bottom+depth
            val end=otherY+lowerRows*stepY+110f
            return ScatterLayout(perRow,stepX,stepY,
                firstX,firstY,firstX,otherY,boardTop,BOARD_LEFT,WORLD_WIDTH,end)
        }

        fun fresh(cols: Int, rows: Int, imageKey: String = "starter",
                  seed: Long = System.nanoTime(), landscape: Boolean = false): PuzzleGame {
            require(cols in 2..32 && rows in 2..32 && cols * rows <= 600)
            val rng = Random(seed)
            val pw = BOARD_WIDTH / cols
            val ph = BOARD_HEIGHT / rows
            val layout=scatterLayout(cols,rows,landscape)
            val slotsPerRow = layout.perRow
            val total = cols * rows
            val topCount = (total + 1) / 2
            val topSlots = (0 until topCount).map { it to true }
            val bottomSlots = (0 until total - topCount).map { it to false }
            val slots = (topSlots + bottomSlots).shuffled(rng)
            val pieces = (0 until total).map { i ->
                val (slot, top) = slots[i]
                val col = i % cols
                val row = i / cols
                val px=(if(top)layout.firstX else layout.secondX) +
                    (slot%slotsPerRow)*layout.stepX
                val py=(if(top)layout.firstY else layout.secondY) +
                    (slot/slotsPerRow)*layout.stepY
                PuzzlePiece(i, row, col, px, py)
            }.toMutableList()
            return PuzzleGame(cols, rows, seed, imageKey, pieces, landscapeMode=landscape)
        }

        fun restore(cols: Int, rows: Int, seed: Long, imageKey: String,
                    positions: List<Triple<Float, Float, Boolean>>,
                    hints: Int, moves: Int, preview: Boolean, landscape: Boolean = false): PuzzleGame? {
            if (cols !in 2..32 || rows !in 2..32 || cols * rows != positions.size ||
                cols * rows > 600 || hints !in 0..3 || moves < 0) return null
            val game = fresh(cols, rows, imageKey, seed, landscape)
            for ((i, data) in positions.withIndex()) {
                if (!data.first.isFinite() || !data.second.isFinite() ||
                    abs(data.first) > 10000f || abs(data.second) > 10000f) return null
                game.pieces[i].apply {
                    x = if (data.third) game.targetX(this) else data.first
                    y = if (data.third) game.targetY(this) else data.second
                    placed = data.third
                }
            }
            game.hintsRemaining = hints
            game.moves = moves
            game.preview = preview
            return game
        }
    }

    private var layout=scatterLayout(cols,rows,landscapeMode)
    val boardLeft: Float get() = layout.left
    val worldWidth: Float get() = layout.worldWidth
    val boardTop: Float get() = layout.top
    val worldHeight: Float get() = layout.end
    val count: Int get() = cols * rows
    val completed: Int get() = pieces.count { it.placed }
    val solved: Boolean get() = completed == count
    val pieceWidth: Float get() = BOARD_WIDTH / cols
    val pieceHeight: Float get() = BOARD_HEIGHT / rows

    fun targetX(piece: PuzzlePiece): Float = boardLeft + piece.col * pieceWidth
    fun targetY(piece: PuzzlePiece): Float = boardTop + piece.row * pieceHeight

    /**
     * Rotate the same puzzle between portrait tabletop (loose pieces above/below)
     * and landscape tabletop (loose pieces left/right). Placed progress is preserved.
     * Re-scatter unplaced pieces, rather than allowing stale portrait-only coordinates.
     */
    fun setLandscape(enabled: Boolean): Boolean {
        if (enabled == landscapeMode) return false
        val replacement = fresh(cols, rows, imageKey, seed, enabled)
        landscapeMode=enabled
        layout=scatterLayout(cols,rows,enabled)
        for (p in pieces) {
            if (p.placed) {
                p.x=targetX(p);p.y=targetY(p)
            } else {
                p.x=replacement.pieces[p.id].x
                p.y=replacement.pieces[p.id].y
            }
        }
        return true
    }

    /** 0 flat, +/-1: tabs on outward-facing edge. Neighbors must have opposite signs. */
    fun edge(row: Int, col: Int, direction: Int): Int {
        require(row in 0 until rows && col in 0 until cols && direction in 0..3)
        return when (direction) {
            0 -> if (row == 0) 0 else -edgeHash(row - 1, col, false)
            1 -> if (col == cols - 1) 0 else edgeHash(row, col, true)
            2 -> if (row == rows - 1) 0 else edgeHash(row, col, false)
            else -> if (col == 0) 0 else -edgeHash(row, col - 1, true)
        }
    }

    private fun edgeHash(row: Int, col: Int, horizontal: Boolean): Int {
        var h = seed xor (row.toLong() * 73856093L) xor
            (col.toLong() * 19349663L) xor (if (horizontal) 83492791L else 2654435761L)
        h = h xor (h ushr 31)
        return if ((h and 1L) == 0L) 1 else -1
    }

    fun movePiece(id: Int, x: Float, y: Float) {
        val p = pieces.getOrNull(id) ?: return
        if (!p.placed && x.isFinite() && y.isFinite()) {
            p.x = x.coerceIn(-250f, worldWidth + 250f)
            p.y = y.coerceIn(-250f, worldHeight + 250f)
        }
    }

    /** A piece is committed only when its intended position is near enough. */
    fun release(id: Int): Boolean {
        val p = pieces.getOrNull(id) ?: return false
        if (p.placed) return false
        moves++
        val tolerance = (min(pieceWidth, pieceHeight) * 0.33f).coerceIn(7f, 26f)
        if (hypot((p.x - targetX(p)).toDouble(),
                  (p.y - targetY(p)).toDouble()) > tolerance) return false
        p.x = targetX(p); p.y = targetY(p); p.placed = true
        return true
    }

    fun hint(): Boolean {
        if (solved || hintsRemaining <= 0) return false
        val p = pieces.firstOrNull { !it.placed } ?: return false
        p.x = targetX(p); p.y = targetY(p); p.placed = true
        hintsRemaining--
        return true
    }
}
