package com.night.mirrorchess.chess

data class PgnGame(
    val headers: Map<String, String>,
    val moves: List<Move>,
    val sanMoves: List<String>,
    val result: String = "*",
) {
    val white: String get() = headers["White"].orEmpty()
    val black: String get() = headers["Black"].orEmpty()

    fun toPgn(): String = buildString {
        val ordered = linkedMapOf(
            "Event" to (headers["Event"] ?: "Mirror Chess"),
            "Site" to (headers["Site"] ?: "Mirror Chess Android"),
            "Date" to (headers["Date"] ?: "????.??.??"),
            "Round" to (headers["Round"] ?: "-"),
            "White" to (headers["White"] ?: "White"),
            "Black" to (headers["Black"] ?: "Black"),
            "Result" to result,
        )
        headers.forEach { (k, v) -> if (k !in ordered) ordered[k] = v }
        ordered.forEach { (k, v) -> append('[').append(k).append(" \"").append(v.replace("\"", "\\\"")).append("\"]\n") }
        append('\n')
        sanMoves.chunked(2).forEachIndexed { index, pair ->
            append(index + 1).append(". ").append(pair[0])
            pair.getOrNull(1)?.let { append(' ').append(it) }
            append(' ')
        }
        append(result)
        append('\n')
    }
}

data class PgnParseResult(
    val games: List<PgnGame>,
    val errors: List<String>,
)

object Pgn {
    private val tagRegex = Regex("^\\[([A-Za-z0-9_]+)\\s+\"(.*)\"\\]$")
    private val resultTokens = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun parseMany(text: String): PgnParseResult {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .removePrefix("\uFEFF")
            .trimStart()
        if (normalized.isBlank()) return PgnParseResult(emptyList(), listOf("The PGN file is empty."))

        val starts = Regex("(?m)^\\[Event\\s+").findAll(normalized).map { it.range.first }.toList()
        val chunks = if (starts.size <= 1) listOf(normalized) else starts.mapIndexed { i, start ->
            normalized.substring(start, starts.getOrNull(i + 1) ?: normalized.length).trim()
        }

        val games = mutableListOf<PgnGame>()
        val errors = mutableListOf<String>()
        chunks.filter { it.isNotBlank() }.forEachIndexed { i, chunk ->
            runCatching { parseOne(chunk) }
                .onSuccess { games += it }
                .onFailure { errors += "Game ${i + 1}: ${it.message ?: "could not be parsed"}" }
        }
        return PgnParseResult(games, errors)
    }

    fun parseOne(text: String): PgnGame {
        val headers = linkedMapOf<String, String>()
        val moveText = StringBuilder()
        var readingTags = true
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (readingTags && line.startsWith("[")) {
                val m = tagRegex.matchEntire(line)
                if (m != null) headers[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"")
            } else {
                if (line.isNotBlank()) {
                    readingTags = false
                    // Preserve line endings so semicolon comments stop at the end of
                    // their original PGN line instead of swallowing the rest of a game.
                    moveText.append(line).append('\n')
                }
            }
        }

        val cleaned = stripCommentsAndVariations(moveText.toString())
        val rawTokens = cleaned
            .replace(Regex("\\$\\d+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var state = headers["FEN"]?.let(::gameStateFromFen) ?: GameState.initial()
        val moves = mutableListOf<Move>()
        val sans = mutableListOf<String>()
        var result = headers["Result"] ?: "*"

        for (raw in rawTokens) {
            val token = raw
                .replace(Regex("^\\d+\\.(?:\\.\\.)?"), "")
                .trim()
            if (token.isBlank()) continue
            if (token in resultTokens) { result = token; break }
            if (token.matches(Regex("\\d+\\.+"))) continue

            val move = resolveToken(state, token)
                ?: error("Unrecognized move '$token' at ply ${moves.size + 1}")
            val san = move.san(state)
            state = ChessRules.applyLegalMove(state, move)
            moves += move
            sans += san
        }

        return PgnGame(headers = headers, moves = moves, sanMoves = sans, result = result)
    }

    fun fromMoves(
        statesBeforeMoves: List<GameState>,
        moves: List<Move>,
        white: String,
        black: String,
        result: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): PgnGame {
        require(statesBeforeMoves.size == moves.size)
        val sans = moves.indices.map { i -> moves[i].san(statesBeforeMoves[i]) }
        return PgnGame(
            headers = linkedMapOf(
                "Event" to "Mirror Chess",
                "Site" to "Mirror Chess Android",
                "White" to white,
                "Black" to black,
                "Result" to result,
            ) + extraHeaders,
            moves = moves,
            sanMoves = sans,
            result = result,
        )
    }

    private fun resolveToken(state: GameState, raw: String): Move? {
        val token = normalizeSan(raw)
        val legal = ChessRules.legalMoves(state)
        if (raw.length in 4..5 && squareFromName(raw.take(2)) != null && squareFromName(raw.substring(2, 4)) != null) {
            val from = squareFromName(raw.take(2)) ?: return null
            val to = squareFromName(raw.substring(2, 4)) ?: return null
            val promo = raw.getOrNull(4)?.let(::promotionFromChar)
            if (raw.length == 5 && promo == null) return null
            return ChessRules.findLegalMove(state, from, to, promo)
        }
        return legal.firstOrNull { normalizeSan(it.san(state)) == token }
            ?: legal.firstOrNull { normalizeSan(it.san(state)).removeSuffix("+").removeSuffix("#") == token.removeSuffix("+").removeSuffix("#") }
    }

    private fun promotionFromChar(c: Char): PieceType? = when (c.lowercaseChar()) {
        'q' -> PieceType.QUEEN
        'r' -> PieceType.ROOK
        'b' -> PieceType.BISHOP
        'n' -> PieceType.KNIGHT
        else -> null
    }

    private fun normalizeSan(san: String): String = san
        .replace('0', 'O')
        .replace(Regex("[!?]+"), "")
        .replace("e.p.", "", ignoreCase = true)
        .trim()

    private fun stripCommentsAndVariations(text: String): String {
        val out = StringBuilder(text.length)
        var braceDepth = 0
        var parenDepth = 0
        var semicolon = false
        for (c in text) {
            if (semicolon) {
                if (c == '\n') semicolon = false
                continue
            }
            if (c == ';' && braceDepth == 0 && parenDepth == 0) { semicolon = true; continue }
            if (c == '{') { braceDepth++; continue }
            if (c == '}' && braceDepth > 0) { braceDepth--; continue }
            if (braceDepth > 0) continue
            if (c == '(') { parenDepth++; continue }
            if (c == ')' && parenDepth > 0) { parenDepth--; continue }
            if (parenDepth == 0) out.append(c)
        }
        return out.toString()
    }
}
