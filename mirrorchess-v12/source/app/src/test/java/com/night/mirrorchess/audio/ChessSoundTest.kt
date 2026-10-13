package com.night.mirrorchess.audio

import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.index
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSoundTest {
    private fun step(state: GameState, from: Int, to: Int): GameState {
        val move = ChessRules.findLegalMove(state, from, to)
        assertNotNull(move)
        return ChessRules.applyLegalMove(state, move!!)
    }

    @Test fun ordinaryAndCaptureMovesHaveDistinctSounds() {
        val start = GameState.initial()
        val white = step(start, index(4, 1), index(4, 3)) // e4
        assertEquals(ChessSoundCue.MOVE, soundForMove(start, white))
        val black = step(white, index(3, 6), index(3, 4)) // ...d5
        val capture = step(black, index(4, 3), index(3, 4)) // exd5
        assertEquals(ChessSoundCue.CAPTURE, soundForMove(black, capture))
        assertEquals(ChessSoundCue.GAME_END, soundForMove(black, capture, gameEnded = true))
        assertNull(soundForMove(white, start)) // navigating backwards is silent
    }

    @Test fun allCuesAreWellFormedAndNonSilent() {
        ChessSoundCue.entries.forEach { cue ->
            val bytes = chessCueWav(cue)
            assertTrue(bytes.size > 44)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
            assertTrue(bytes.drop(44).any { it != 0.toByte() })
        }
    }
}
