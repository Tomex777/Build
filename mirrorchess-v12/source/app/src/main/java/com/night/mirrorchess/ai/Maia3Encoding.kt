package com.night.mirrorchess.ai

import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.fileOf
import com.night.mirrorchess.chess.index
import com.night.mirrorchess.chess.rankOf
import com.night.mirrorchess.chess.squareFromName
import com.night.mirrorchess.chess.squareName

/**
 * Exact board/move convention used by the official Maia-3 inference code:
 * 64 squares x 12 one-hot piece channels. Black-to-move positions are vertically
 * mirrored and colors are swapped so the side to move is always represented as White.
 */
object Maia3Encoding {
    const val MOVE_VOCAB_SIZE = 4352

    fun boardTokens(state: GameState): FloatArray {
        val out = FloatArray(64 * 12)
        state.board.forEachIndexed { originalSquare, piece ->
            if (piece == null) return@forEachIndexed

            val square: Int
            val side: Side
            if (state.turn == Side.BLACK) {
                square = index(fileOf(originalSquare), 7 - rankOf(originalSquare))
                side = piece.side.opposite()
            } else {
                square = originalSquare
                side = piece.side
            }

            val mappedPiece = when (piece.type) {
                PieceType.PAWN -> 1
                PieceType.KNIGHT -> 2
                PieceType.BISHOP -> 3
                PieceType.ROOK -> 4
                PieceType.QUEEN -> 5
                PieceType.KING -> 6
            }
            val channel = mappedPiece + if (side == Side.BLACK) 6 else 0
            out[square * 12 + channel - 1] = 1f
        }
        return out
    }

    fun vocabularyIndex(move: Move, sideToMove: Side): Int? {
        val canonical = if (sideToMove == Side.BLACK) mirrorUci(move.uci()) else move.uci()
        if (canonical.length == 4) {
            val from = squareFromName(canonical.substring(0, 2)) ?: return null
            val to = squareFromName(canonical.substring(2, 4)) ?: return null
            return from * 64 + to
        }
        if (canonical.length == 5) {
            val fromName = canonical.substring(0, 2)
            val toName = canonical.substring(2, 4)
            // Maia promotion vocabulary contains every file-to-file move from rank 7 to 8.
            if (fromName[1] != '7' || toName[1] != '8') return null
            val fromFile = fromName[0] - 'a'
            val toFile = toName[0] - 'a'
            val promo = when (canonical[4]) {
                'q' -> 0
                'r' -> 1
                'b' -> 2
                'n' -> 3
                else -> return null
            }
            return 4096 + fromFile * 8 * 4 + toFile * 4 + promo
        }
        return null
    }

    fun uciAt(index: Int, originalSideToMove: Side): String? {
        if (index !in 0 until MOVE_VOCAB_SIZE) return null
        val canonical = if (index < 4096) {
            val from = index / 64
            val to = index % 64
            squareName(from) + squareName(to)
        } else {
            val p = index - 4096
            val fromFile = p / 32
            val remainder = p % 32
            val toFile = remainder / 4
            val promo = when (remainder % 4) {
                0 -> 'q'
                1 -> 'r'
                2 -> 'b'
                else -> 'n'
            }
            "${('a'.code + fromFile).toChar()}7${('a'.code + toFile).toChar()}8$promo"
        }
        return if (originalSideToMove == Side.BLACK) mirrorUci(canonical) else canonical
    }

    fun mirrorUci(uci: String): String {
        if (uci.length !in 4..5) return uci
        fun mirrorSquare(name: String): String {
            val rank = name[1].digitToInt()
            return "${name[0]}${9 - rank}"
        }
        return mirrorSquare(uci.substring(0, 2)) +
            mirrorSquare(uci.substring(2, 4)) +
            if (uci.length == 5) uci[4] else ""
    }
}
