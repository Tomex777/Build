package com.night.jigsaw

import org.junit.Assert.*
import org.junit.Test

class PuzzleEngineTest {
    @Test fun countsAndScatterAreReal() {
        for ((c, r) in listOf(6 to 4, 8 to 6, 12 to 12)) {
            val p = PuzzleGame.fresh(c, r, seed = 42)
            assertEquals(c * r, p.count)
            assertEquals(c * r, p.pieces.map { it.id }.distinct().size)
            assertEquals(0, p.completed)
            assertTrue(p.pieces.all { it.x != p.targetX(it) || it.y != p.targetY(it) })
        }
    }
    @Test fun scatterMasksDoNotOverlapEvenAt144() {
        for ((cols, rows) in listOf(6 to 4, 8 to 6, 12 to 12)) {
            val game=PuzzleGame.fresh(cols,rows,seed=1234)
            val depth=kotlin.math.min(game.pieceWidth,game.pieceHeight)*.30f
            for (i in game.pieces.indices) for (j in i+1 until game.pieces.size) {
                val a=game.pieces[i]
                val b=game.pieces[j]
                val horizontal=kotlin.math.abs(a.x-b.x) >= game.pieceWidth+2f*depth
                val vertical=kotlin.math.abs(a.y-b.y) >= game.pieceHeight+2f*depth
                assertTrue("Overlap at "+cols+"x"+rows+" piece "+i+"/"+j, horizontal || vertical)
            }
        }
    }

    @Test fun largerPuzzleSizesAndLandscapePreserveCompletedProgress() {
        for ((cols,rows) in listOf(3 to 2,4 to 3,9 to 8,
            12 to 8,12 to 12,20 to 12,24 to 16,30 to 20)) {
            val g=PuzzleGame.fresh(cols,rows,seed=927)
            assertEquals(cols*rows,g.count)
            assertFalse(g.landscapeMode)
            assertTrue(g.hint())
            val placed=g.completed
            assertTrue(g.setLandscape(true))
            assertTrue(g.landscapeMode)
            assertEquals(placed,g.completed)
            assertTrue(g.boardLeft>80f)
            assertEquals(g.targetX(g.pieces[0]),g.pieces[0].x)
            assertTrue(g.setLandscape(false))
            assertEquals(placed,g.completed)
            val data=g.pieces.map { Triple(it.x,it.y,it.placed) }
            val restored=PuzzleGame.restore(cols,rows,g.seed,g.imageKey,
                data,g.hintsRemaining,g.moves,g.preview,g.landscapeMode)!!
            assertEquals(g.completed,restored.completed)
            assertEquals(g.worldWidth,restored.worldWidth)
        }
    }

    @Test fun sixWorkZonesBound600PiecesAndKeepProgress() {
        for ((cols,rows,expected) in listOf(
            Triple(20,12,4), Triple(24,16,6), Triple(30,20,6)
        )) {
            val game=PuzzleGame.fresh(cols,rows,seed=238)
            assertEquals(expected,game.zoneCount)
            assertEquals(game.count, (0 until game.zoneCount).sumOf{zone->
                game.zoneRemaining(zone)})
            val remaining=(0 until game.zoneCount).map{zone->game.zoneRemaining(zone)}
            assertTrue(remaining.all{it in 1..100})
            for(zone in 0 until game.zoneCount) {
                game.selectZone(zone)
                val visible=game.pieces.filter{ !it.placed && game.visible(it) }
                assertEquals(game.zoneRemaining(zone),visible.size)
                val depth=kotlin.math.min(game.pieceWidth,game.pieceHeight)*.30f
                for(i in visible.indices)for(j in i+1 until visible.size) {
                    val a=visible[i];val b=visible[j]
                    assertTrue("Zone scatter overlaps",kotlin.math.abs(a.x-b.x)>=
                        game.pieceWidth+2f*depth ||
                        kotlin.math.abs(a.y-b.y)>=game.pieceHeight+2f*depth)
                }
            }
            game.selectZone(1)
            assertTrue(game.hint())
            assertEquals(1,game.completed)
            assertEquals(game.count-1,(0 until game.zoneCount).sumOf{game.zoneRemaining(it)})
            val positions=game.pieces.map {Triple(it.x,it.y,it.placed)}
            val restored=PuzzleGame.restore(cols,rows,game.seed,game.imageKey,positions,
                game.hintsRemaining,game.moves,game.preview,game.landscapeMode,game.activeZone)!!
            assertEquals(1,restored.activeZone)
            assertEquals(1,restored.completed)
            assertTrue(restored.setLandscape(true))
            assertEquals(1,restored.completed)
            assertEquals(expected,restored.zoneCount)
        }
    }

    @Test fun piecesJoinMoveAndSnapAsOneAssembly() {
        val g=PuzzleGame.fresh(6,4,seed=404)
        val first=g.pieces[0]
        val second=g.pieces[1]
        val width=g.pieceWidth
        g.movePiece(0,430f,1400f)
        g.movePiece(1,430f+width+1f,1400f)
        assertFalse(g.release(1))
        assertEquals(first.groupId,second.groupId)
        assertEquals(2,g.groupMembers(0).size)
        val beforeX=second.x
        g.movePiece(0,first.x+25f,first.y+14f)
        assertEquals(beforeX+25f,second.x,0.01f)
        g.movePiece(0,g.targetX(first)+1f,g.targetY(first)-1f)
        assertTrue(g.release(0))
        assertTrue(first.placed)
        assertTrue(second.placed)
        assertEquals(2,g.completed)
        assertEquals(g.targetX(second),second.x,0.01f)
        assertEquals(g.targetY(second),second.y,0.01f)
    }

    @Test fun joinedAssemblySurvivesSavedStateAndRotation() {
        val g=PuzzleGame.fresh(8,6,seed=8765)
        val a=g.pieces[0]
        val b=g.pieces[1]
        g.movePiece(0,400f,1400f)
        g.movePiece(1,400f+g.pieceWidth,1400f)
        assertFalse(g.release(1))
        val groups=g.pieces.map{it.groupId}
        val locations=g.pieces.map{Triple(it.x,it.y,it.placed)}
        val restored=PuzzleGame.restore(g.cols,g.rows,g.seed,g.imageKey,
            locations,g.hintsRemaining,g.moves,g.preview,g.landscapeMode,g.activeZone,groups)!!
        assertEquals(2,restored.groupMembers(0).size)
        restored.setLandscape(true)
        val xDelta=restored.pieces[1].x-restored.pieces[0].x
        val yDelta=restored.pieces[1].y-restored.pieces[0].y
        assertEquals(restored.pieceWidth,xDelta,0.001f)
        assertEquals(0f,yDelta,0.001f)
        val badGroups=groups.toMutableList().apply{set(2,9999)}
        assertNull(PuzzleGame.restore(g.cols,g.rows,g.seed,g.imageKey,
            locations,g.hintsRemaining,g.moves,g.preview,g.landscapeMode,g.activeZone,badGroups))
    }

    @Test fun edgesOnlyHidesInteriorsWithoutBreakingJoinedGroups() {
        val g=PuzzleGame.fresh(8,6,seed=7788)
        g.edgesOnly=true
        val edgeCount=g.pieces.count{g.isEdge(it)}
        assertEquals(8*2+6*2-4,edgeCount)
        assertEquals(edgeCount,g.pieces.count{g.visible(it)})
        val edge=g.pieces[1]
        val inner=g.pieces[9]
        g.movePiece(1,420f,1400f)
        g.movePiece(9,420f,1400f+g.pieceHeight)
        assertFalse(g.release(9))
        assertEquals(edge.groupId,inner.groupId)
        assertTrue(g.visible(inner))
        val positions=g.pieces.map{Triple(it.x,it.y,it.placed)}
        val groups=g.pieces.map{it.groupId}
        val restored=PuzzleGame.restore(g.cols,g.rows,g.seed,g.imageKey,
            positions,g.hintsRemaining,g.moves,g.preview,g.landscapeMode,
            g.activeZone,groups,g.edgesOnly)!!
        assertTrue(restored.edgesOnly)
        assertTrue(restored.visible(restored.pieces[9]))
    }

    @Test fun rulesFollowChosenModeAndSave() {
        for(mode in PuzzleMode.entries) {
            val g=PuzzleGame.fresh(6,4,seed=29,mode=mode)
            assertEquals(mode,g.mode)
            assertEquals(mode.allowedHints,g.hintsRemaining)
            assertEquals(mode.allowedPreviews,g.previewsRemaining)
            assertEquals(mode==PuzzleMode.MEMORY,g.hintsRemaining==0)
            repeat(mode.allowedPreviews) { assertTrue(g.usePreview()) }
            assertFalse(g.usePreview())
            repeat(mode.allowedHints) { assertTrue(g.hint()) }
            assertFalse(g.hint())
            g.elapsedSeconds=187
            val state=g.pieces.map{Triple(it.x,it.y,it.placed)}
            val restored=PuzzleGame.restore(g.cols,g.rows,g.seed,g.imageKey,
                state,g.hintsRemaining,g.moves,g.preview,g.landscapeMode,
                g.activeZone,g.pieces.map{it.groupId},g.edgesOnly,g.mode,
                g.previewsRemaining,g.elapsedSeconds)!!
            assertEquals(g.mode,restored.mode)
            assertEquals(187,restored.elapsedSeconds)
            assertEquals(0,restored.previewsRemaining)
            assertEquals(0,restored.hintsRemaining)
        }
    }

    @Test fun badModesDoNotRestoreOverbudgetHintsOrTime() {
        val g=PuzzleGame.fresh(8,6,seed=10)
        val state=g.pieces.map{Triple(it.x,it.y,it.placed)}
        assertNull(PuzzleGame.restore(8,6,g.seed,g.imageKey,state,3,0,false,
            mode=PuzzleMode.MEMORY))
        assertNull(PuzzleGame.restore(8,6,g.seed,g.imageKey,state,0,0,false,
            mode=PuzzleMode.CHALLENGE,previewsRemaining=17))
        assertNull(PuzzleGame.restore(8,6,g.seed,g.imageKey,state,0,0,false,
            elapsedSeconds=-20))
    }

    @Test fun neighborTabsAreComplementaryAndEdgesFlat() {
        val g = PuzzleGame.fresh(12, 12, seed = 782)
        for (r in 0 until 12) for (c in 0 until 12) {
            if (r == 0) assertEquals(0, g.edge(r,c,0))
            if (c == 0) assertEquals(0, g.edge(r,c,3))
            if (r == 11) assertEquals(0, g.edge(r,c,2))
            if (c == 11) assertEquals(0, g.edge(r,c,1))
            if (r < 11) assertEquals(-g.edge(r,c,2),g.edge(r+1,c,0))
            if (c < 11) assertEquals(-g.edge(r,c,1),g.edge(r,c+1,3))
        }
    }
    @Test fun wrongPositionCannotSnapButNearbyCorrectPositionCan() {
        val g = PuzzleGame.fresh(6,4, seed=9)
        g.movePiece(0, 800f, 200f)
        assertFalse(g.release(0))
        assertFalse(g.pieces[0].placed)
        g.movePiece(0, g.targetX(g.pieces[0]) + 1f, g.targetY(g.pieces[0]) - 1f)
        assertTrue(g.release(0))
        assertEquals(1,g.completed)
        assertEquals(g.targetX(g.pieces[0]),g.pieces[0].x)
        assertFalse(g.release(0))
    }
    @Test fun hintsAreLimitedAndRestorationIsExact() {
        val g = PuzzleGame.fresh(8,6, "custom", 739)
        repeat(3) { assertTrue(g.hint()) }
        assertFalse(g.hint())
        assertEquals(3,g.completed)
        val state = g.pieces.map { Triple(it.x,it.y,it.placed) }
        val restored = PuzzleGame.restore(8,6,739,"custom",state, g.hintsRemaining,g.moves,g.preview)!!
        assertEquals(3,restored.completed)
        assertEquals(0,restored.hintsRemaining)
        assertEquals("custom",restored.imageKey)
        assertEquals(state, restored.pieces.map { Triple(it.x,it.y,it.placed) })
    }
    @Test fun invalidSavesCannotCrashOrAllocateMassiveBoards() {
        assertNull(PuzzleGame.restore(100,100,1,"starter",emptyList(),3,0,false))
        assertNull(PuzzleGame.restore(6,4,1,"starter",emptyList(),3,0,false))
    }
}
