package com.night.mirrorchess.chess

import com.night.mirrorchess.ai.Maia3Encoding
import com.night.mirrorchess.mirror.MirrorProfile
import com.night.mirrorchess.mirror.MirrorProfileBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessCoreTest {
    private fun perft(state: GameState, depth: Int): Long {
        if (depth == 0) return 1
        return ChessRules.legalMoves(state).sumOf { perft(ChessRules.applyLegalMove(state, it), depth - 1) }
    }

    private fun play(vararg ucis: String): GameState {
        var state = GameState.initial()
        ucis.forEach { uci ->
            val promotion = uci.getOrNull(4)?.let {
                when (it.lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> error("Invalid promotion in test move: $uci")
                }
            }
            val move = ChessRules.findLegalMove(
                state,
                requireNotNull(squareFromName(uci.take(2))),
                requireNotNull(squareFromName(uci.substring(2, 4))),
                promotion,
            ) ?: error("Illegal test move: $uci")
            state = ChessRules.applyLegalMove(state, move)
        }
        return state
    }

    @Test
    fun startingPositionPerftThroughDepthFour() {
        val initial = GameState.initial()
        assertEquals(20L, perft(initial, 1))
        assertEquals(400L, perft(initial, 2))
        assertEquals(8_902L, perft(initial, 3))
        assertEquals(197_281L, perft(initial, 4))
    }

    @Test
    fun detectsMateAndDrawConditions() {
        assertEquals(GameResult.CHECKMATE, ChessRules.status(play("f2f3", "e7e5", "g2g4", "d8h4")).result)

        val bareKings = requireNotNull(gameStateFromFen("8/8/8/8/8/5k2/8/7K w - - 0 1"))
        assertTrue(ChessRules.isInsufficientMaterial(bareKings))

        val fifty = requireNotNull(gameStateFromFen("8/8/8/8/8/5k2/8/R6K w - - 100 1"))
        assertEquals(GameResult.DRAW_50_MOVE, ChessRules.status(fifty).result)

        var repeated = GameState.initial()
        val history = mutableListOf(repeated)
        listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8").forEach { uci ->
            val from = requireNotNull(squareFromName(uci.take(2)))
            val to = requireNotNull(squareFromName(uci.substring(2, 4)))
            repeated = ChessRules.applyLegalMove(repeated, requireNotNull(ChessRules.findLegalMove(repeated, from, to)))
            history += repeated
        }
        val count = history.count { ChessRules.repetitionKey(it) == ChessRules.repetitionKey(repeated) }
        assertEquals(3, count)
        assertEquals(GameResult.DRAW_THREEFOLD, ChessRules.status(repeated, count).result)
    }

    @Test
    fun handlesCastlingEnPassantAndPromotion() {
        val castleState = requireNotNull(gameStateFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        val castle = requireNotNull(ChessRules.findLegalMove(castleState, requireNotNull(squareFromName("e1")), requireNotNull(squareFromName("g1"))))
        assertTrue(castle.isCastle)
        val castled = ChessRules.applyLegalMove(castleState, castle)
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), castled.board[requireNotNull(squareFromName("f1"))])

        val enPassantState = requireNotNull(gameStateFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"))
        val enPassant = requireNotNull(ChessRules.findLegalMove(enPassantState, requireNotNull(squareFromName("e5")), requireNotNull(squareFromName("d6"))))
        assertTrue(enPassant.isEnPassant)
        assertNull(ChessRules.applyLegalMove(enPassantState, enPassant).board[requireNotNull(squareFromName("d5"))])

        val fakeEnPassantState = requireNotNull(gameStateFromFen("4k3/8/8/3nP3/8/8/8/4K3 w - d6 0 1"))
        assertNull(ChessRules.findLegalMove(fakeEnPassantState, requireNotNull(squareFromName("e5")), requireNotNull(squareFromName("d6"))))

        val promotionState = requireNotNull(gameStateFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
        val promotion = requireNotNull(ChessRules.findLegalMove(promotionState, requireNotNull(squareFromName("a7")), requireNotNull(squareFromName("a8")), PieceType.KNIGHT))
        assertEquals(Piece(Side.WHITE, PieceType.KNIGHT), ChessRules.applyLegalMove(promotionState, promotion).board[requireNotNull(squareFromName("a8"))])
    }

    @Test
    fun rejectsMalformedFen() {
        assertNull(gameStateFromFen("8/8/8/8/8/8/8/8 w - - 0 1"))
        assertNull(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 x - - 0 1"))
        assertNull(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 w - - -1 1"))
        assertNull(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 w - - 0 1 ignored"))
    }

    @Test
    fun parsesPgnCommentsAndLearnsOnlyNamedPlayersMoves() {
        val commented = Pgn.parseOne(
            """
            [Event "Comments"]
            [White "Alice"]
            [Black "Bob"]

            1. e4 ; this ends at the newline
            1... e5 2. Nf3 Nc6 *
            """.trimIndent(),
        )
        assertEquals(4, commented.moves.size)
        assertTrue(runCatching { Pgn.parseOne("1. e2e4x *") }.isFailure)
        assertEquals(1, Pgn.parseMany("\uFEFF[Event \"BOM\"]\n\n1. e4 *").games.size)

        val result = Pgn.parseMany(
            """
            [Event "One"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0

            [Event "Two"]
            [White "Bob"]
            [Black "Alice"]
            [Result "1/2-1/2"]

            1. d4 d5 2. c4 e6 1/2-1/2
            """.trimIndent(),
        )
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.games.size)
        val profile = MirrorProfileBuilder.build(result.games, "Alice")
        assertEquals(5, profile.movesLearned)
        val restored = MirrorProfile.fromStorageText(profile.toStorageText())
        assertNotNull(restored)
        assertEquals(profile.positionMemory, restored?.positionMemory)
    }

    @Test
    fun maiaVocabularyAndFenRoundTripStayStable() {
        val state = play("e2e4")
        val blackMove = requireNotNull(ChessRules.findLegalMove(state, requireNotNull(squareFromName("e7")), requireNotNull(squareFromName("e5"))))
        val index = requireNotNull(Maia3Encoding.vocabularyIndex(blackMove, Side.BLACK))
        assertTrue(index in 0 until 4_352)

        val whitePromotion = Move(requireNotNull(squareFromName("a7")), requireNotNull(squareFromName("b8")), PieceType.KNIGHT)
        val whitePromotionIndex = requireNotNull(Maia3Encoding.vocabularyIndex(whitePromotion, Side.WHITE))
        assertEquals("a7b8n", Maia3Encoding.uciAt(whitePromotionIndex, Side.WHITE))

        val blackPromotion = Move(requireNotNull(squareFromName("a2")), requireNotNull(squareFromName("b1")), PieceType.QUEEN)
        val blackPromotionIndex = requireNotNull(Maia3Encoding.vocabularyIndex(blackPromotion, Side.BLACK))
        assertEquals("a2b1q", Maia3Encoding.uciAt(blackPromotionIndex, Side.BLACK))

        val whiteTokens = Maia3Encoding.boardTokens(GameState.initial())
        assertEquals(1f, whiteTokens[requireNotNull(squareFromName("a1")) * 12 + 3])
        val blackToMove = play("e2e4")
        val blackTokens = Maia3Encoding.boardTokens(blackToMove)
        assertEquals(1f, blackTokens[requireNotNull(squareFromName("e2")) * 12])

        val fen = play("e2e4", "c7c5", "g1f3").toFen()
        assertEquals(fen, gameStateFromFen(fen)?.toFen())
        assertFalse(fen.isBlank())
    }
}
