package com.night.mirrorchess.audio

import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.fileOf

/** Short, unobtrusive cues. Determine the move before queuing the landing sound. */
enum class ChessSoundCue { MOVE, CAPTURE, CASTLE, CHECK, GAME_END }

/**
 * Returns null for resets, arbitrary imported positions, and undo/review backwards.
 * The caller also suppresses audio while reviewing historical moves.
 */
fun soundForMove(previous: GameState, next: GameState, gameEnded: Boolean = false): ChessSoundCue? {
    val move = next.lastMove ?: return null
    val movingPiece = previous.board.getOrNull(move.from) ?: return null
    if (next.board.getOrNull(move.to)?.side != movingPiece.side) return null
    if (gameEnded) return ChessSoundCue.GAME_END
    if (ChessRules.isInCheck(next, next.turn)) return ChessSoundCue.CHECK
    if (move.isCastle) return ChessSoundCue.CASTLE
    // En passant is a diagonal pawn capture onto an originally empty square.
    val capture = previous.board.getOrNull(move.to) != null ||
        (movingPiece.type == PieceType.PAWN && fileOf(move.from) != fileOf(move.to))
    return if (capture) ChessSoundCue.CAPTURE else ChessSoundCue.MOVE
}
