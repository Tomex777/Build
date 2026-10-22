package com.night.jigsaw

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** View-independent puzzle rules in a 1000-unit world; no Android or browser dependency. */
enum class PuzzleMode(val label: String, val allowedHints: Int,
                      val allowedPreviews: Int, val peekMillis: Long) {
    RELAXED("Relaxed",3,3,6000L),
    CHALLENGE("Challenge",1,1,4000L),
    MEMORY("Memory",0,1,5000L)
}

data class PuzzlePiece(
    val id: Int, val row: Int, val col: Int,
    var x: Float, var y: Float, var placed: Boolean = false,
    var groupId: Int = id
)

class PuzzleGame private constructor(
    val cols: Int, val rows: Int, val seed: Long,
    val imageKey: String, val pieces: MutableList<PuzzlePiece>,
    var hintsRemaining: Int = 3,
    var moves: Int = 0,
    var preview: Boolean = false,
    var landscapeMode: Boolean = false,
    var activeZone: Int = 0,
    var edgesOnly: Boolean = false,
    var mode: PuzzleMode = PuzzleMode.RELAXED,
    var previewsRemaining: Int = mode.allowedPreviews,
    var elapsedSeconds: Int = 0
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

        // 240 pieces: 4 sections; 384 and 600: 6 sections.
        // The full puzzle stays intact while only the active loose-piece
        // group occupies the visible sorting space.
        private fun zoneColumns(cols: Int, rows: Int): Int =
            if(cols*rows >= 384) 3 else if(cols*rows >= 240) 2 else 1
        private fun zoneRows(cols: Int, rows: Int): Int =
            if(cols*rows >= 240) 2 else 1
        private fun zoneOf(id: Int, cols: Int, rows: Int): Int {
            val column=id%cols
            val row=id/cols
            val zx=min(zoneColumns(cols,rows)-1,
                column*zoneColumns(cols,rows)/cols)
            val zy=min(zoneRows(cols,rows)-1,
                row*zoneRows(cols,rows)/rows)
            return zy*zoneColumns(cols,rows)+zx
        }

        // Reserve enough tabletop for every real silhouette, including protruding tabs.
        // Packing uses non-overlapping hit footprints rather than pile-on-top scatter.
        private fun scatterLayout(cols: Int, rows: Int, landscape: Boolean): ScatterLayout {
            val pw=BOARD_WIDTH/cols
            val ph=BOARD_HEIGHT/rows
            val depth=min(pw,ph)*.30f
            val stepX=pw+2f*depth+10f
            val stepY=ph+2f*depth+10f
            val zoneCount=zoneColumns(cols,rows)*zoneRows(cols,rows)
            val visiblePieces=(cols*rows+zoneCount-1)/zoneCount
            val half=(visiblePieces+1)/2
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
            val lowerRows=(visiblePieces-half+perRow-1)/perRow
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
                  seed: Long = System.nanoTime(), landscape: Boolean = false,
                  mode: PuzzleMode = PuzzleMode.RELAXED): PuzzleGame {
            require(cols in 2..32 && rows in 2..32 && cols * rows <= 600)
            val rng = Random(seed)
            val pw = BOARD_WIDTH / cols
            val ph = BOARD_HEIGHT / rows
            val layout=scatterLayout(cols,rows,landscape)
            val slotsPerRow = layout.perRow
            val total = cols * rows
            val positions = Array(total) { 0f to 0f }
            val zones=zoneColumns(cols,rows)*zoneRows(cols,rows)
            // All six groups reuse the same physical sorting table without
            // overlapping hit targets, because only one group is visible.
            for (zone in 0 until zones) {
                val ids=(0 until total).filter{ zoneOf(it,cols,rows)==zone }
                    .shuffled(rng)
                val half=(ids.size+1)/2
                val slots=(0 until ids.size).map{ i ->
                    if(i<half) i to true else (i-half) to false
                }.shuffled(rng)
                ids.forEachIndexed { index,id ->
                    val (slot,top)=slots[index]
                    val px=(if(top)layout.firstX else layout.secondX) +
                        (slot%slotsPerRow)*layout.stepX
                    val py=(if(top)layout.firstY else layout.secondY) +
                        (slot/slotsPerRow)*layout.stepY
                    positions[id]=px to py
                }
            }
            val pieces=(0 until total).map { i ->
                PuzzlePiece(i, i/cols, i%cols, positions[i].first, positions[i].second)
            }.toMutableList()
            return PuzzleGame(cols, rows, seed, imageKey, pieces,
                hintsRemaining=mode.allowedHints, landscapeMode=landscape,
                mode=mode, previewsRemaining=mode.allowedPreviews)
        }

        fun restore(cols: Int, rows: Int, seed: Long, imageKey: String,
                    positions: List<Triple<Float, Float, Boolean>>,
                    hints: Int, moves: Int, preview: Boolean, landscape: Boolean = false,
                    activeZone: Int = 0, groupIds: List<Int>? = null,
                    edgesOnly: Boolean = false,
                    mode: PuzzleMode = PuzzleMode.RELAXED,
                    previewsRemaining: Int = mode.allowedPreviews,
                    elapsedSeconds: Int = 0): PuzzleGame? {
            if (cols !in 2..32 || rows !in 2..32 || cols * rows != positions.size ||
                cols * rows > 600 || hints !in 0..3 || moves < 0) return null
            if (groupIds!=null &&
                (groupIds.size!=positions.size || groupIds.any{it !in positions.indices}))
                return null
            if(hints > mode.allowedHints ||
                previewsRemaining !in 0..mode.allowedPreviews ||
                elapsedSeconds !in 0..2_592_000)return null
            val game = fresh(cols, rows, imageKey, seed, landscape,mode)
            for ((i, data) in positions.withIndex()) {
                if (!data.first.isFinite() || !data.second.isFinite() ||
                    abs(data.first) > 10000f || abs(data.second) > 10000f) return null
                game.pieces[i].apply {
                    x = if (data.third) game.targetX(this) else data.first
                    y = if (data.third) game.targetY(this) else data.second
                    placed = data.third
                    groupId = if (data.third) id else groupIds?.get(i) ?: id
                }
            }
            if(activeZone !in 0 until game.zoneCount) return null
            game.activeZone=activeZone
            game.edgesOnly=edgesOnly
            game.hintsRemaining = hints
            game.moves = moves
            game.preview = preview && mode != PuzzleMode.MEMORY
            game.previewsRemaining = previewsRemaining
            game.elapsedSeconds = elapsedSeconds
            return game
        }
    }

    private var layout=scatterLayout(cols,rows,landscapeMode)
    val boardLeft: Float get() = layout.left
    val worldWidth: Float get() = layout.worldWidth
    val boardTop: Float get() = layout.top
    val worldHeight: Float get() = layout.end
    val zonesAcross: Int get() = zoneColumns(cols,rows)
    val zonesDown: Int get() = zoneRows(cols,rows)
    val zoneCount: Int get() = zonesAcross*zonesDown
    fun zoneFor(piece: PuzzlePiece): Int = zoneOf(piece.id,cols,rows)
    fun isEdge(piece: PuzzlePiece): Boolean = piece.row==0 ||
        piece.row==rows-1 || piece.col==0 || piece.col==cols-1
    fun visible(piece: PuzzlePiece): Boolean {
        if(piece.placed)return true
        if(zoneCount>1 && zoneFor(piece)!=activeZone)return false
        if(!edgesOnly)return true
        // Keep the entire assembly visible when one member is an edge.
        return isEdge(piece) || pieces.any {
            !it.placed && it.groupId==piece.groupId && isEdge(it)
        }
    }
    fun zoneRemaining(zone: Int): Int {
        require(zone in 0 until zoneCount)
        return pieces.count{!it.placed && zoneFor(it)==zone}
    }
    fun selectZone(zone: Int): Boolean {
        if(zone !in 0 until zoneCount || zone==activeZone) return false
        activeZone=zone
        return true
    }
    fun zoneName(zone: Int): String {
        require(zone in 0 until zoneCount)
        val row=zone/zonesAcross
        val col=zone%zonesAcross
        val vertical=if(row==0) "Top" else "Bottom"
        val horizontal=when(zonesAcross) {
            2 -> if(col==0) "left" else "right"
            3 -> when(col){0->"left";1->"middle";else->"right"}
            else -> ""
        }
        return vertical + " " + horizontal
    }
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
        val replacement = fresh(cols, rows, imageKey, seed, enabled,mode)
        landscapeMode=enabled
        layout=scatterLayout(cols,rows,enabled)
        pieces.filter{it.placed}.forEach{p->
            p.x=targetX(p);p.y=targetY(p)
        }
        // Retain connected assemblies when rotating, instead of splitting
        // all loose groups into unrelated pieces.
        val groups=pieces.filter{!it.placed}.groupBy{it.groupId}
        for(group in groups.values) {
            val anchor=group.first()
            val proposed=replacement.pieces[anchor.id]
            for(p in group) {
                p.x=proposed.x+(p.col-anchor.col)*pieceWidth
                p.y=proposed.y+(p.row-anchor.row)*pieceHeight
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

    fun groupMembers(id: Int): List<PuzzlePiece> {
        val p=pieces.getOrNull(id) ?: return emptyList()
        if(p.placed)return listOf(p)
        return pieces.filter{!it.placed && it.groupId==p.groupId}
    }

    /** Dragging any piece in a joined assembly translates all its members. */
    fun movePiece(id: Int, x: Float, y: Float) {
        val p=pieces.getOrNull(id) ?: return
        if(p.placed || !x.isFinite() || !y.isFinite())return
        val group=groupMembers(id)
        val dx=x-p.x
        val dy=y-p.y
        if(group.any{
            it.x+dx !in -250f..(worldWidth+250f) ||
            it.y+dy !in -250f..(worldHeight+250f)
        }) return
        for(member in group) {
            member.x+=dx
            member.y+=dy
        }
    }

    /** Returns true for an actual board placement. Joining loose pieces
     * also updates the model but returns false (they remain draggable). */
    fun release(id: Int): Boolean {
        val p=pieces.getOrNull(id) ?: return false
        if(p.placed) return false
        moves++
        val members=groupMembers(id)
        val tolerance=(min(pieceWidth,pieceHeight)*.33f).coerceIn(7f,26f)
        val px=p.x-targetX(p)
        val py=p.y-targetY(p)
        // Prevent malformed/inconsistent groups from jumping to the board.
        val relativeOk=members.all{
            hypot(((it.x-targetX(it))-px).toDouble(),
                ((it.y-targetY(it))-py).toDouble()) < 1.25
        }
        if(relativeOk && hypot(px.toDouble(),py.toDouble())<=tolerance) {
            for(piece in members) {
                piece.x=targetX(piece)
                piece.y=targetY(piece)
                piece.placed=true
                piece.groupId=piece.id
            }
            return true
        }
        // Exact row/column neighbor matches, regardless of group size.
        // This lets a 600-piece jigsaw be assembled a section at a time.
        for(member in members) {
            // Check only the four true photo-neighbors, not the entire
            // 600-piece collection for each member in a dragged assembly.
            val neighbors=intArrayOf(
                if(member.row>0)member.id-cols else -1,
                if(member.col>0)member.id-1 else -1,
                if(member.col+1<cols)member.id+1 else -1,
                if(member.row+1<rows)member.id+cols else -1
            )
            for(otherId in neighbors) {
                if(otherId<0)continue
                val other=pieces[otherId]
                if(other.placed || other.groupId==p.groupId ||
                    !visible(other))continue
                val offsetX=other.x-member.x-(other.col-member.col)*pieceWidth
                val offsetY=other.y-member.y-(other.row-member.row)*pieceHeight
                if(hypot(offsetX.toDouble(),offsetY.toDouble())>tolerance)continue
                val targetGroup=other.groupId
                for(piece in members){
                    piece.x+=offsetX
                    piece.y+=offsetY
                    piece.groupId=targetGroup
                }
                return false
            }
        }
        return false
    }

    /** Explicit image peek: callers render for peekMillis and then hide.
     * Persistent ghost guide is disallowed in Memory mode. */
    fun usePreview(): Boolean {
        if(solved || previewsRemaining<=0)return false
        previewsRemaining--
        return true
    }

    fun hint(): Boolean {
        if (solved || hintsRemaining <= 0 || mode==PuzzleMode.MEMORY)return false
        val p = pieces.firstOrNull { !it.placed && visible(it) } ?: return false
        p.x = targetX(p); p.y = targetY(p); p.placed = true
        hintsRemaining--
        return true
    }
}
