package com.night.mirrorchess.chess

import kotlin.math.abs

enum class Side {
    WHITE, BLACK;
    fun opposite(): Side = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

data class Piece(val side: Side, val type: PieceType)

data class CastleRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true,
)

data class Move(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastle: Boolean = false,
) {
    fun uci(): String = buildString {
        append(squareName(from))
        append(squareName(to))
        promotion?.let {
            append(
                when (it) {
                    PieceType.QUEEN -> 'q'
                    PieceType.ROOK -> 'r'
                    PieceType.BISHOP -> 'b'
                    PieceType.KNIGHT -> 'n'
                    else -> 'q'
                },
            )
        }
    }
}

data class GameState(
    val board: List<Piece?>,
    val turn: Side = Side.WHITE,
    val castleRights: CastleRights = CastleRights(),
    val enPassantSquare: Int? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
    val lastMove: Move? = null,
) {
    companion object {
        fun initial(): GameState {
            val board = MutableList<Piece?>(64) { null }
            val backRank = listOf(
                PieceType.ROOK,
                PieceType.KNIGHT,
                PieceType.BISHOP,
                PieceType.QUEEN,
                PieceType.KING,
                PieceType.BISHOP,
                PieceType.KNIGHT,
                PieceType.ROOK,
            )
            for (file in 0..7) {
                board[index(file, 0)] = Piece(Side.WHITE, backRank[file])
                board[index(file, 1)] = Piece(Side.WHITE, PieceType.PAWN)
                board[index(file, 6)] = Piece(Side.BLACK, PieceType.PAWN)
                board[index(file, 7)] = Piece(Side.BLACK, backRank[file])
            }
            return GameState(board = board)
        }
    }
}

enum class GameResult {
    ACTIVE,
    CHECKMATE,
    STALEMATE,
    DRAW_THREEFOLD,
    DRAW_50_MOVE,
    DRAW_INSUFFICIENT,
}

data class PositionStatus(val result: GameResult, val inCheck: Boolean)

object ChessRules {
    fun legalMoves(state: GameState): List<Move> {
        val side = state.turn
        return pseudoLegalMoves(state, side).filter { move ->
            val next = applyUnchecked(state, move)
            !isInCheck(next, side)
        }
    }

    fun legalMovesFrom(state: GameState, from: Int): List<Move> =
        legalMoves(state).filter { it.from == from }

    fun applyLegalMove(state: GameState, move: Move): GameState {
        require(legalMoves(state).contains(move)) { "Illegal move: ${move.uci()}" }
        return applyUnchecked(state, move)
    }

    fun findLegalMove(state: GameState, from: Int, to: Int, promotion: PieceType? = null): Move? {
        val candidates = legalMoves(state).filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return null
        if (promotion != null) return candidates.firstOrNull { it.promotion == promotion }
        return candidates.firstOrNull { it.promotion == PieceType.QUEEN } ?: candidates.first()
    }

    fun status(state: GameState, repetitionCount: Int = 1): PositionStatus {
        val legal = legalMoves(state)
        val check = isInCheck(state, state.turn)
        if (legal.isEmpty()) {
            return if (check) PositionStatus(GameResult.CHECKMATE, true)
            else PositionStatus(GameResult.STALEMATE, false)
        }
        if (repetitionCount >= 3) return PositionStatus(GameResult.DRAW_THREEFOLD, check)
        if (state.halfmoveClock >= 100) return PositionStatus(GameResult.DRAW_50_MOVE, check)
        if (isInsufficientMaterial(state)) return PositionStatus(GameResult.DRAW_INSUFFICIENT, check)
        return PositionStatus(GameResult.ACTIVE, check)
    }

    fun isInsufficientMaterial(state: GameState): Boolean {
        val nonKings = state.board.mapIndexedNotNull { square, piece ->
            if (piece == null || piece.type == PieceType.KING) null else square to piece
        }
        if (nonKings.any { (_, p) -> p.type == PieceType.PAWN || p.type == PieceType.ROOK || p.type == PieceType.QUEEN }) return false
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1 && nonKings[0].second.type in setOf(PieceType.BISHOP, PieceType.KNIGHT)) return true
        if (nonKings.all { it.second.type == PieceType.BISHOP }) {
            val colors = nonKings.map { (sq, _) -> (fileOf(sq) + rankOf(sq)) and 1 }.toSet()
            return colors.size == 1
        }
        return false
    }

    fun repetitionKey(state: GameState): String {
        val boardKey = buildString(64) {
            state.board.forEach { piece -> append(piece?.keyChar() ?: '.') }
        }
        val castles = buildString {
            if (state.castleRights.whiteKingSide) append('K')
            if (state.castleRights.whiteQueenSide) append('Q')
            if (state.castleRights.blackKingSide) append('k')
            if (state.castleRights.blackQueenSide) append('q')
            if (isEmpty()) append('-')
        }
        val ep = effectiveEnPassantSquare(state)?.let(::squareName) ?: "-"
        return "$boardKey ${if (state.turn == Side.WHITE) 'w' else 'b'} $castles $ep"
    }

    private fun effectiveEnPassantSquare(state: GameState): Int? {
        val ep = state.enPassantSquare ?: return null
        // Repetition identity includes an en-passant target only when the side to move
        // can actually make a legal en-passant capture (important for pinned pawns).
        return ep.takeIf { target -> legalMoves(state).any { it.isEnPassant && it.to == target } }
    }

    fun isInCheck(state: GameState, side: Side): Boolean {
        val kingSquare = state.board.indexOfFirst { it == Piece(side, PieceType.KING) }
        if (kingSquare < 0) return true
        return isSquareAttacked(state, kingSquare, side.opposite())
    }

    fun isSquareAttacked(state: GameState, square: Int, bySide: Side): Boolean {
        val file = fileOf(square)
        val rank = rankOf(square)

        val pawnSourceRank = rank + if (bySide == Side.WHITE) -1 else 1
        for (df in listOf(-1, 1)) {
            val sf = file + df
            if (inside(sf, pawnSourceRank)) {
                val p = state.board[index(sf, pawnSourceRank)]
                if (p?.side == bySide && p.type == PieceType.PAWN) return true
            }
        }

        val knightSteps = arrayOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )
        for ((df, dr) in knightSteps) {
            val f = file + df
            val r = rank + dr
            if (inside(f, r)) {
                val p = state.board[index(f, r)]
                if (p?.side == bySide && p.type == PieceType.KNIGHT) return true
            }
        }

        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val f = file + df
            val r = rank + dr
            if (inside(f, r)) {
                val p = state.board[index(f, r)]
                if (p?.side == bySide && p.type == PieceType.KING) return true
            }
        }

        if (rayAttacked(state, square, bySide, diagonal = false)) return true
        if (rayAttacked(state, square, bySide, diagonal = true)) return true
        return false
    }

    private fun rayAttacked(state: GameState, square: Int, bySide: Side, diagonal: Boolean): Boolean {
        val directions = if (diagonal) {
            arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        } else {
            arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        }
        for ((df, dr) in directions) {
            var f = fileOf(square) + df
            var r = rankOf(square) + dr
            while (inside(f, r)) {
                val p = state.board[index(f, r)]
                if (p != null) {
                    if (p.side == bySide) {
                        if (p.type == PieceType.QUEEN) return true
                        if (diagonal && p.type == PieceType.BISHOP) return true
                        if (!diagonal && p.type == PieceType.ROOK) return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }
        return false
    }

    private fun pseudoLegalMoves(state: GameState, side: Side): List<Move> {
        val moves = mutableListOf<Move>()
        state.board.forEachIndexed { square, piece ->
            if (piece?.side != side) return@forEachIndexed
            when (piece.type) {
                PieceType.PAWN -> pawnMoves(state, square, side, moves)
                PieceType.KNIGHT -> knightMoves(state, square, side, moves)
                PieceType.BISHOP -> slidingMoves(state, square, side, moves, diagonal = true, straight = false)
                PieceType.ROOK -> slidingMoves(state, square, side, moves, diagonal = false, straight = true)
                PieceType.QUEEN -> slidingMoves(state, square, side, moves, diagonal = true, straight = true)
                PieceType.KING -> kingMoves(state, square, side, moves)
            }
        }
        return moves
    }

    private fun pawnMoves(state: GameState, from: Int, side: Side, moves: MutableList<Move>) {
        val file = fileOf(from)
        val rank = rankOf(from)
        val dir = if (side == Side.WHITE) 1 else -1
        val startRank = if (side == Side.WHITE) 1 else 6
        val promotionRank = if (side == Side.WHITE) 7 else 0
        val oneRank = rank + dir

        if (inside(file, oneRank) && state.board[index(file, oneRank)] == null) {
            addPawnMove(from, index(file, oneRank), oneRank == promotionRank, moves)
            val twoRank = rank + 2 * dir
            if (rank == startRank && state.board[index(file, twoRank)] == null) {
                moves += Move(from, index(file, twoRank))
            }
        }

        for (df in listOf(-1, 1)) {
            val f = file + df
            val r = rank + dir
            if (!inside(f, r)) continue
            val target = index(f, r)
            val occupant = state.board[target]
            if (occupant != null && occupant.side != side) {
                addPawnMove(from, target, r == promotionRank, moves)
            } else if (state.enPassantSquare == target) {
                val capturedPawn = state.board[index(f, rank)]
                if (capturedPawn == Piece(side.opposite(), PieceType.PAWN)) {
                    moves += Move(from, target, isEnPassant = true)
                }
            }
        }
    }

    private fun addPawnMove(from: Int, to: Int, promotion: Boolean, moves: MutableList<Move>) {
        if (!promotion) {
            moves += Move(from, to)
        } else {
            moves += Move(from, to, PieceType.QUEEN)
            moves += Move(from, to, PieceType.ROOK)
            moves += Move(from, to, PieceType.BISHOP)
            moves += Move(from, to, PieceType.KNIGHT)
        }
    }

    private fun knightMoves(state: GameState, from: Int, side: Side, moves: MutableList<Move>) {
        val steps = arrayOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )
        val file = fileOf(from)
        val rank = rankOf(from)
        for ((df, dr) in steps) {
            val f = file + df
            val r = rank + dr
            if (!inside(f, r)) continue
            val to = index(f, r)
            if (state.board[to]?.side != side) moves += Move(from, to)
        }
    }

    private fun slidingMoves(
        state: GameState,
        from: Int,
        side: Side,
        moves: MutableList<Move>,
        diagonal: Boolean,
        straight: Boolean,
    ) {
        val directions = mutableListOf<Pair<Int, Int>>()
        if (straight) directions += listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        if (diagonal) directions += listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((df, dr) in directions) {
            var f = fileOf(from) + df
            var r = rankOf(from) + dr
            while (inside(f, r)) {
                val to = index(f, r)
                val occupant = state.board[to]
                if (occupant == null) {
                    moves += Move(from, to)
                } else {
                    if (occupant.side != side) moves += Move(from, to)
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun kingMoves(state: GameState, from: Int, side: Side, moves: MutableList<Move>) {
        val file = fileOf(from)
        val rank = rankOf(from)
        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val f = file + df
            val r = rank + dr
            if (!inside(f, r)) continue
            val to = index(f, r)
            if (state.board[to]?.side != side) moves += Move(from, to)
        }

        if (isInCheck(state, side)) return
        val enemy = side.opposite()
        if (side == Side.WHITE && from == index(4, 0)) {
            if (state.castleRights.whiteKingSide &&
                state.board[index(5, 0)] == null && state.board[index(6, 0)] == null &&
                state.board[index(7, 0)] == Piece(Side.WHITE, PieceType.ROOK) &&
                !isSquareAttacked(state, index(5, 0), enemy) &&
                !isSquareAttacked(state, index(6, 0), enemy)
            ) moves += Move(from, index(6, 0), isCastle = true)
            if (state.castleRights.whiteQueenSide &&
                state.board[index(1, 0)] == null && state.board[index(2, 0)] == null && state.board[index(3, 0)] == null &&
                state.board[index(0, 0)] == Piece(Side.WHITE, PieceType.ROOK) &&
                !isSquareAttacked(state, index(3, 0), enemy) &&
                !isSquareAttacked(state, index(2, 0), enemy)
            ) moves += Move(from, index(2, 0), isCastle = true)
        }
        if (side == Side.BLACK && from == index(4, 7)) {
            if (state.castleRights.blackKingSide &&
                state.board[index(5, 7)] == null && state.board[index(6, 7)] == null &&
                state.board[index(7, 7)] == Piece(Side.BLACK, PieceType.ROOK) &&
                !isSquareAttacked(state, index(5, 7), enemy) &&
                !isSquareAttacked(state, index(6, 7), enemy)
            ) moves += Move(from, index(6, 7), isCastle = true)
            if (state.castleRights.blackQueenSide &&
                state.board[index(1, 7)] == null && state.board[index(2, 7)] == null && state.board[index(3, 7)] == null &&
                state.board[index(0, 7)] == Piece(Side.BLACK, PieceType.ROOK) &&
                !isSquareAttacked(state, index(3, 7), enemy) &&
                !isSquareAttacked(state, index(2, 7), enemy)
            ) moves += Move(from, index(2, 7), isCastle = true)
        }
    }

    private fun applyUnchecked(state: GameState, move: Move): GameState {
        val board = state.board.toMutableList()
        val piece = requireNotNull(board[move.from])
        val captured = if (move.isEnPassant) {
            val captureSquare = index(fileOf(move.to), rankOf(move.from))
            val c = board[captureSquare]
            board[captureSquare] = null
            c
        } else board[move.to]

        board[move.from] = null
        board[move.to] = if (move.promotion != null) Piece(piece.side, move.promotion) else piece

        if (move.isCastle) {
            val rank = rankOf(move.from)
            when (fileOf(move.to)) {
                6 -> {
                    board[index(5, rank)] = board[index(7, rank)]
                    board[index(7, rank)] = null
                }
                2 -> {
                    board[index(3, rank)] = board[index(0, rank)]
                    board[index(0, rank)] = null
                }
            }
        }

        var rights = state.castleRights
        if (piece.type == PieceType.KING) {
            rights = if (piece.side == Side.WHITE) {
                rights.copy(whiteKingSide = false, whiteQueenSide = false)
            } else {
                rights.copy(blackKingSide = false, blackQueenSide = false)
            }
        }
        if (piece.type == PieceType.ROOK) rights = revokeRookRight(rights, piece.side, move.from)
        if (captured?.type == PieceType.ROOK) rights = revokeRookRight(rights, captured.side, move.to)

        val enPassant = if (piece.type == PieceType.PAWN && abs(rankOf(move.to) - rankOf(move.from)) == 2) {
            index(fileOf(move.from), (rankOf(move.from) + rankOf(move.to)) / 2)
        } else null

        val halfmove = if (piece.type == PieceType.PAWN || captured != null) 0 else state.halfmoveClock + 1
        val fullmove = state.fullmoveNumber + if (state.turn == Side.BLACK) 1 else 0
        return state.copy(
            board = board,
            turn = state.turn.opposite(),
            castleRights = rights,
            enPassantSquare = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove,
            lastMove = move,
        )
    }

    private fun revokeRookRight(rights: CastleRights, side: Side, square: Int): CastleRights = when (side) {
        Side.WHITE -> when (square) {
            index(0, 0) -> rights.copy(whiteQueenSide = false)
            index(7, 0) -> rights.copy(whiteKingSide = false)
            else -> rights
        }
        Side.BLACK -> when (square) {
            index(0, 7) -> rights.copy(blackQueenSide = false)
            index(7, 7) -> rights.copy(blackKingSide = false)
            else -> rights
        }
    }
}

fun index(file: Int, rank: Int): Int = rank * 8 + file
fun fileOf(square: Int): Int = square % 8
fun rankOf(square: Int): Int = square / 8
fun inside(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
fun squareName(square: Int): String = "${('a'.code + fileOf(square)).toChar()}${rankOf(square) + 1}"
fun squareFromName(name: String): Int? {
    if (name.length != 2) return null
    val file = name[0].lowercaseChar() - 'a'
    val rank = name[1] - '1'
    return if (inside(file, rank)) index(file, rank) else null
}

fun Piece.keyChar(): Char = when (type) {
    PieceType.KING -> if (side == Side.WHITE) 'K' else 'k'
    PieceType.QUEEN -> if (side == Side.WHITE) 'Q' else 'q'
    PieceType.ROOK -> if (side == Side.WHITE) 'R' else 'r'
    PieceType.BISHOP -> if (side == Side.WHITE) 'B' else 'b'
    PieceType.KNIGHT -> if (side == Side.WHITE) 'N' else 'n'
    PieceType.PAWN -> if (side == Side.WHITE) 'P' else 'p'
}

fun Move.san(stateBefore: GameState): String {
    val piece = stateBefore.board[from] ?: return uci()
    if (isCastle) {
        val base = if (fileOf(to) == 6) "O-O" else "O-O-O"
        val after = runCatching { ChessRules.applyLegalMove(stateBefore, this) }.getOrNull()
        return if (after != null) base + checkSuffix(after) else base
    }

    val capture = isEnPassant || stateBefore.board[to] != null
    val pieceLetter = when (piece.type) {
        PieceType.PAWN -> ""
        PieceType.KNIGHT -> "N"
        PieceType.BISHOP -> "B"
        PieceType.ROOK -> "R"
        PieceType.QUEEN -> "Q"
        PieceType.KING -> "K"
    }

    val disambiguation = if (piece.type == PieceType.PAWN) {
        if (capture) squareName(from)[0].toString() else ""
    } else {
        val alternatives = ChessRules.legalMoves(stateBefore).filter { candidate ->
            candidate != this && candidate.to == to && stateBefore.board[candidate.from] == piece
        }
        when {
            alternatives.isEmpty() -> ""
            alternatives.none { fileOf(it.from) == fileOf(from) } -> squareName(from)[0].toString()
            alternatives.none { rankOf(it.from) == rankOf(from) } -> squareName(from)[1].toString()
            else -> squareName(from)
        }
    }

    val promotionText = promotion?.let {
        "=" + when (it) {
            PieceType.QUEEN -> "Q"
            PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"
            PieceType.KNIGHT -> "N"
            else -> "Q"
        }
    }.orEmpty()

    val after = runCatching { ChessRules.applyLegalMove(stateBefore, this) }.getOrNull()
    return pieceLetter + disambiguation + (if (capture) "x" else "") + squareName(to) + promotionText +
        (after?.let(::checkSuffix) ?: "")
}

private fun checkSuffix(after: GameState): String {
    if (!ChessRules.isInCheck(after, after.turn)) return ""
    return if (ChessRules.legalMoves(after).isEmpty()) "#" else "+"
}

fun Move.displayName(stateBefore: GameState): String = san(stateBefore)

fun GameState.toFen(): String {
    val placement = (7 downTo 0).joinToString("/") { rank ->
        buildString {
            var empty = 0
            for (file in 0..7) {
                val piece = board[index(file, rank)]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) { append(empty); empty = 0 }
                    append(piece.keyChar())
                }
            }
            if (empty > 0) append(empty)
        }
    }
    val castles = buildString {
        if (castleRights.whiteKingSide) append('K')
        if (castleRights.whiteQueenSide) append('Q')
        if (castleRights.blackKingSide) append('k')
        if (castleRights.blackQueenSide) append('q')
        if (isEmpty()) append('-')
    }
    return "$placement ${if (turn == Side.WHITE) "w" else "b"} $castles ${enPassantSquare?.let(::squareName) ?: "-"} $halfmoveClock $fullmoveNumber"
}

fun gameStateFromFen(fen: String): GameState? = runCatching {
    val parts = fen.trim().split(Regex("\\s+"))
    require(parts.size in 4..6)
    val ranks = parts[0].split('/')
    require(ranks.size == 8)
    val board = MutableList<Piece?>(64) { null }
    ranks.forEachIndexed { fenRank, row ->
        var file = 0
        for (c in row) {
            if (c in '1'..'8') {
                file += c.digitToInt()
            } else {
                require(file in 0..7)
                val side = if (c.isUpperCase()) Side.WHITE else Side.BLACK
                val type = when (c.lowercaseChar()) {
                    'k' -> PieceType.KING
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    'p' -> PieceType.PAWN
                    else -> error("Bad FEN piece")
                }
                board[index(file, 7 - fenRank)] = Piece(side, type)
                file++
            }
        }
        require(file == 8)
    }
    require(parts[1] == "w" || parts[1] == "b")
    require(board.count { it == Piece(Side.WHITE, PieceType.KING) } == 1)
    require(board.count { it == Piece(Side.BLACK, PieceType.KING) } == 1)
    val turn = if (parts[1] == "b") Side.BLACK else Side.WHITE
    val castles = parts[2]
    require(castles == "-" || (castles.all { it in "KQkq" } && castles.toSet().size == castles.length))
    val enPassant = parts[3].takeUnless { it == "-" }?.let(::squareFromName)
    require(parts[3] == "-" || enPassant != null)
    require(enPassant == null || rankOf(enPassant) in setOf(2, 5))
    val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
    val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1
    require(halfmove >= 0)
    require(fullmove >= 1)
    val rights = CastleRights(
        whiteKingSide = 'K' in castles,
        whiteQueenSide = 'Q' in castles,
        blackKingSide = 'k' in castles,
        blackQueenSide = 'q' in castles,
    )
    GameState(
        board = board,
        turn = turn,
        castleRights = rights,
        enPassantSquare = enPassant,
        halfmoveClock = halfmove,
        fullmoveNumber = fullmove,
    )
}.getOrNull()
