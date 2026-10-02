package com.night.mirrorchess.data

import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceSetTest {
    @Test fun builtInSetsMapAllTwelvePieces() {
        PieceSetId.entries.forEach { id ->
            val set = id.asPieceSet()
            assertTrue("${id.name} should be complete", set.isComplete())
            assertEquals(12, set.mapping.size)
            Side.entries.forEach { side -> PieceType.entries.forEach { type -> assertNotNull(set.visualFor(side, type)) } }
        }
    }

    @Test fun customSetCanRepresentMissingPiecesExplicitly() {
        val partial = PieceSet("custom-test", "Test", builtIn = false, mapping = mapOf(PieceKey(Side.WHITE, PieceType.KNIGHT) to "custom-test/white-knight"))
        assertFalse(partial.isComplete())
        assertNotNull(partial.visualFor(Side.WHITE, PieceType.KNIGHT))
        assertEquals(null, partial.visualFor(Side.BLACK, PieceType.KNIGHT))
    }

    @Test fun unknownSavedPresetSafelyFallsBackToClassic() {
        assertEquals(PieceSetId.CLASSIC, PieceSetId.fromId("removed-theme"))
        assertEquals(PieceSetId.MODERN, PieceSetId.fromId("modern"))
    }
}
