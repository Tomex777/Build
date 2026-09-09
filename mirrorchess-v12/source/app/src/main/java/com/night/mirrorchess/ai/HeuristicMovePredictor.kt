package com.night.mirrorchess.ai

import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.displayName
import com.night.mirrorchess.chess.fileOf
import com.night.mirrorchess.chess.rankOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

class HeuristicMovePredictor : MovePredictor {
    override val isRealModel: Boolean = false

    override suspend fun predict(
        state: GameState,
        selfElo: Int,
        opponentElo: Int,
        limit: Int,
    ): PredictionBundle = withContext(Dispatchers.Default) {
        val legal = ChessRules.legalMoves(state)
        if (legal.isEmpty()) return@withContext PredictionBundle(emptyList(), "Preview engine")

        val raw = legal.map { move -> move to score(state, move) }
        val max = raw.maxOf { it.second }
        val weights = raw.map { (_, s) -> exp((s - max).coerceIn(-20.0, 20.0)) }
        val total = weights.sum().coerceAtLeast(1e-9)

        val ranked = raw.indices
            .map { i -> Triple(raw[i].first, raw[i].second, (weights[i] / total).toFloat()) }
            .sortedByDescending { it.third }
            .take(limit)

        PredictionBundle(
            candidates = ranked.map { (move, score, probability) ->
                MovePrediction(
                    move = move,
                    probability = probability,
                    label = move.displayName(state),
                    explanation = explanation(state, move, score),
                )
            },
            source = "Preview engine",
        )
    }

    private fun score(state: GameState, move: Move): Double {
        val piece = state.board[move.from] ?: return -99.0
        val captured = if (move.isEnPassant) {
            PieceType.PAWN
        } else state.board[move.to]?.type

        var score = 0.0
        if (captured != null) {
            score += pieceValue(captured) * 0.8 - pieceValue(piece.type) * 0.05
        }
        if (move.promotion != null) score += 7.0
        if (move.isCastle) score += 1.8

        val f = fileOf(move.to)
        val r = rankOf(move.to)
        val centerDistance = kotlin.math.abs(f - 3.5) + kotlin.math.abs(r - 3.5)
        score += (3.5 - centerDistance * 0.45).coerceAtLeast(0.0)

        if (piece.type == PieceType.KNIGHT || piece.type == PieceType.BISHOP) {
            val startingRank = if (piece.side == Side.WHITE) 0 else 7
            if (rankOf(move.from) == startingRank) score += 0.9
        }

        val next = runCatching { ChessRules.applyLegalMove(state, move) }.getOrNull()
        if (next != null && ChessRules.isInCheck(next, next.turn)) score += 1.2

        // Stable tiny tie-breaker: avoids identical-looking percentages without randomness.
        score += ((move.from * 67 + move.to * 13) % 17) / 100.0
        return score
    }

    private fun explanation(state: GameState, move: Move, score: Double): String {
        val captured = if (move.isEnPassant) PieceType.PAWN else state.board[move.to]?.type
        return when {
            move.promotion != null -> "Promotes immediately and creates a major material threat."
            move.isCastle -> "Gets the king safer and connects the rooks."
            captured != null -> "Wins or exchanges material while keeping the position active."
            score > 2.0 -> "Improves activity toward the center and keeps several plans available."
            else -> "A natural developing move that keeps the position flexible."
        }
    }

    private fun pieceValue(type: PieceType): Double = when (type) {
        PieceType.PAWN -> 1.0
        PieceType.KNIGHT, PieceType.BISHOP -> 3.0
        PieceType.ROOK -> 5.0
        PieceType.QUEEN -> 9.0
        PieceType.KING -> 20.0
    }
}
