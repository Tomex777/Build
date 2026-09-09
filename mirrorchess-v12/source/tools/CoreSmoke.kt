import com.night.mirrorchess.chess.*
import com.night.mirrorchess.mirror.*
import com.night.mirrorchess.ai.*

fun perft(state: GameState, depth: Int): Long {
    if (depth == 0) return 1
    var sum = 0L
    for (m in ChessRules.legalMoves(state)) sum += perft(ChessRules.applyLegalMove(state, m), depth - 1)
    return sum
}

fun play(vararg ucis: String): GameState {
    var s = GameState.initial()
    for (u in ucis) {
        val from = squareFromName(u.take(2))!!
        val to = squareFromName(u.substring(2,4))!!
        val p = u.getOrNull(4)?.let { when(it) {'q'->PieceType.QUEEN;'r'->PieceType.ROOK;'b'->PieceType.BISHOP;'n'->PieceType.KNIGHT;else->null} }
        val m = ChessRules.findLegalMove(s, from, to, p) ?: error("bad $u")
        s = ChessRules.applyLegalMove(s,m)
    }
    return s
}

fun main() {
    val initial = GameState.initial()
    check(perft(initial,1)==20L)
    check(perft(initial,2)==400L)
    check(perft(initial,3)==8902L)
    check(perft(initial,4)==197281L)

    val mate = play("f2f3","e7e5","g2g4","d8h4")
    check(ChessRules.status(mate).result == GameResult.CHECKMATE)

    val bareKings = gameStateFromFen("8/8/8/8/8/5k2/8/7K w - - 0 1")!!
    check(ChessRules.isInsufficientMaterial(bareKings))
    val knights = gameStateFromFen("8/8/8/8/8/5k2/6n1/6NK w - - 0 1")!!
    check(!ChessRules.isInsufficientMaterial(knights))


    // Threefold identity: the initial position occurs at start, after 4 plies, and after 8 plies.
    var rep = GameState.initial()
    val repStates = mutableListOf(rep)
    for (u in listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")) {
        val from = squareFromName(u.take(2))!!
        val to = squareFromName(u.substring(2,4))!!
        rep = ChessRules.applyLegalMove(rep, ChessRules.findLegalMove(rep, from, to)!!)
        repStates += rep
    }
    val repCount = repStates.count { ChessRules.repetitionKey(it) == ChessRules.repetitionKey(rep) }
    check(repCount == 3)
    check(ChessRules.status(rep, repCount).result == GameResult.DRAW_THREEFOLD)

    val fifty = gameStateFromFen("8/8/8/8/8/5k2/8/R6K w - - 100 1")!!
    check(ChessRules.status(fifty).result == GameResult.DRAW_50_MOVE)

    // Special-move coverage: both castling sides, en passant, and promotion.
    val castlePosition = gameStateFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")!!
    val whiteCastle = ChessRules.findLegalMove(castlePosition, squareFromName("e1")!!, squareFromName("g1")!!)!!
    check(whiteCastle.isCastle)
    val afterWhiteCastle = ChessRules.applyLegalMove(castlePosition, whiteCastle)
    check(afterWhiteCastle.board[squareFromName("f1")!!] == Piece(Side.WHITE, PieceType.ROOK))

    val epPosition = gameStateFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")!!
    val ep = ChessRules.findLegalMove(epPosition, squareFromName("e5")!!, squareFromName("d6")!!)!!
    check(ep.isEnPassant)
    val afterEp = ChessRules.applyLegalMove(epPosition, ep)
    check(afterEp.board[squareFromName("d5")!!] == null)
    val falseEp = gameStateFromFen("4k3/8/8/3nP3/8/8/8/4K3 w - d6 0 1")!!
    check(ChessRules.findLegalMove(falseEp, squareFromName("e5")!!, squareFromName("d6")!!) == null)

    val promotionPosition = gameStateFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")!!
    val promotion = ChessRules.findLegalMove(promotionPosition, squareFromName("a7")!!, squareFromName("a8")!!, PieceType.KNIGHT)!!
    check(ChessRules.applyLegalMove(promotionPosition, promotion).board[squareFromName("a8")!!] == Piece(Side.WHITE, PieceType.KNIGHT))

    // Malformed FENs must fail closed.
    check(gameStateFromFen("8/8/8/8/8/8/8/8 w - - 0 1") == null)
    check(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 x - - 0 1") == null)
    check(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 w - - -1 1") == null)
    check(gameStateFromFen("8/8/8/8/8/8/4k3/4K3 w - - 0 1 ignored") == null)

    val pgnText = """
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
""".trimIndent()
    val parsed = Pgn.parseMany(pgnText)
    check(parsed.errors.isEmpty()) { parsed.errors.joinToString() }
    check(parsed.games.size == 2)
    val profile = MirrorProfileBuilder.build(parsed.games, "Alice")
    check(profile.gamesImported == 2)
    check(profile.movesLearned == 5) { "moves=${profile.movesLearned}" }
    val restored = MirrorProfile.fromStorageText(profile.toStorageText())!!
    check(restored.movesLearned == profile.movesLearned)
    check(restored.positionMemory == profile.positionMemory)

    val semicolonPgn = Pgn.parseOne(
        """
[Event "Comments"]
[White "Alice"]
[Black "Bob"]

1. e4 ; this comment ends here
1... e5 2. Nf3 Nc6 *
""".trimIndent(),
    )
    check(semicolonPgn.moves.size == 4)
    check(runCatching { Pgn.parseOne("1. e2e4x *") }.isFailure)

    val s = play("e2e4")
    val blackMove = ChessRules.findLegalMove(s, squareFromName("e7")!!, squareFromName("e5")!!)!!
    val idx = Maia3Encoding.vocabularyIndex(blackMove, Side.BLACK)!!
    check(idx in 0 until 4352)

    val fen = play("e2e4","c7c5","g1f3").toFen()
    check(gameStateFromFen(fen)?.toFen() == fen)
    println("PURE_SMOKE_OK perft4=197281 specialMoves=ok fenValidation=ok pgnComments=ok threefold=$repCount games=${parsed.games.size} mirrorMoves=${profile.movesLearned} maiaIndex=$idx")
}
