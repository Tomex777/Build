package com.night.mirrorchess.mirror

import com.night.mirrorchess.ai.MovePrediction
import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move
import com.night.mirrorchess.chess.PgnGame
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.fileOf
import com.night.mirrorchess.chess.gameStateFromFen
import com.night.mirrorchess.chess.rankOf
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lightweight personal style layer. Maia's weights stay fixed; this profile continuously
 * learns the human player's decisions and reweights Maia's move distribution toward them.
 */
data class MirrorProfile(
    val playerName: String,
    val gamesImported: Int,
    val gamesPlayed: Int,
    val movesLearned: Int,
    val positionMemory: Map<String, Map<String, Int>>,
    val captureRate: Float,
    val checkRate: Float,
    val castleRate: Float,
    val centerRate: Float,
    val pieceRates: Map<PieceType, Float>,
) {
    val totalGames: Int get() = gamesImported + gamesPlayed

    /** Data-coverage indicator, not a claim of percent personality identity. */
    val readinessPercent: Int
        get() {
            val moveCoverage = (movesLearned / 600f).coerceIn(0f, 1f)
            val gameCoverage = (totalGames / 20f).coerceIn(0f, 1f)
            return ((moveCoverage * .75f + gameCoverage * .25f) * 100f).roundToInt().coerceIn(0, 100)
        }

    val isReady: Boolean get() = readinessPercent >= 25

    fun memoryCount(state: GameState, move: Move): Int =
        positionMemory[ChessRules.repetitionKey(state)]?.get(move.uci()) ?: 0

    fun observeMove(state: GameState, move: Move): MirrorProfile {
        val oldMoves = movesLearned.coerceAtLeast(0)
        val newMoves = oldMoves + 1
        val piece = state.board[move.from]
        val capture = move.isEnPassant || state.board[move.to] != null
        val after = ChessRules.applyLegalMove(state, move)
        val check = ChessRules.isInCheck(after, after.turn)
        val center = fileOf(move.to) in 2..5 && rankOf(move.to) in 2..5

        val memory = positionMemory.mapValues { it.value.toMutableMap() }.toMutableMap()
        val key = ChessRules.repetitionKey(state)
        val counts = memory.getOrPut(key) { linkedMapOf() }
        counts[move.uci()] = (counts[move.uci()] ?: 0) + 1

        val rates = pieceRates.toMutableMap()
        PieceType.entries.forEach { type ->
            val previousCount = (rates[type] ?: 0f) * oldMoves
            rates[type] = (previousCount + if (piece?.type == type) 1f else 0f) / newMoves
        }

        return copy(
            movesLearned = newMoves,
            positionMemory = memory.mapValues { it.value.toMap() },
            captureRate = runningRate(captureRate, oldMoves, capture),
            checkRate = runningRate(checkRate, oldMoves, check),
            castleRate = runningRate(castleRate, oldMoves, move.isCastle),
            centerRate = runningRate(centerRate, oldMoves, center),
            pieceRates = rates,
        )
    }

    fun markGamePlayed(): MirrorProfile = copy(gamesPlayed = gamesPlayed + 1)

    fun merge(other: MirrorProfile): MirrorProfile {
        val leftMoves = movesLearned.coerceAtLeast(0)
        val rightMoves = other.movesLearned.coerceAtLeast(0)
        val totalMoves = leftMoves + rightMoves
        if (totalMoves == 0) return copy(
            gamesImported = gamesImported + other.gamesImported,
            gamesPlayed = gamesPlayed + other.gamesPlayed,
        )

        val memory = positionMemory.mapValues { it.value.toMutableMap() }.toMutableMap()
        other.positionMemory.forEach { (key, otherCounts) ->
            val counts = memory.getOrPut(key) { linkedMapOf() }
            otherCounts.forEach { (uci, count) -> counts[uci] = (counts[uci] ?: 0) + count }
        }
        fun blend(a: Float, b: Float): Float = (a * leftMoves + b * rightMoves) / totalMoves

        return copy(
            playerName = if (playerName.isBlank() || playerName == "You") other.playerName.ifBlank { playerName } else playerName,
            gamesImported = gamesImported + other.gamesImported,
            gamesPlayed = gamesPlayed + other.gamesPlayed,
            movesLearned = totalMoves,
            positionMemory = memory.mapValues { it.value.toMap() },
            captureRate = blend(captureRate, other.captureRate),
            checkRate = blend(checkRate, other.checkRate),
            castleRate = blend(castleRate, other.castleRate),
            centerRate = blend(centerRate, other.centerRate),
            pieceRates = PieceType.entries.associateWith { type -> blend(pieceRates[type] ?: 0f, other.pieceRates[type] ?: 0f) },
        )
    }

    fun personalize(state: GameState, candidates: List<MovePrediction>): List<MovePrediction> {
        if (candidates.isEmpty()) return emptyList()
        val memory = positionMemory[ChessRules.repetitionKey(state)].orEmpty()
        val maxMemory = memory.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val weighted = candidates.map { candidate ->
            val move = candidate.move
            val piece = state.board[move.from]
            val capture = move.isEnPassant || state.board[move.to] != null
            val after = runCatching { ChessRules.applyLegalMove(state, move) }.getOrNull()
            val check = after?.let { ChessRules.isInCheck(it, it.turn) } == true
            val center = fileOf(move.to) in 2..5 && rankOf(move.to) in 2..5
            val style = 1f +
                similarity(capture, captureRate) * 0.22f +
                similarity(check, checkRate) * 0.18f +
                similarity(move.isCastle, castleRate) * 0.22f +
                similarity(center, centerRate) * 0.12f +
                (piece?.type?.let { pieceRates[it] } ?: 0f) * 0.20f
            val memoryBoost = 1f + 2.6f * ((memory[move.uci()] ?: 0).toFloat() / maxMemory)
            candidate to (candidate.probability * style * memoryBoost)
        }
        val total = weighted.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1e-7f)
        return weighted
            .map { (candidate, weight) ->
                candidate.copy(
                    probability = weight / total,
                    explanation = if ((memory[candidate.move.uci()] ?: 0) > 0) {
                        "You have played this move from the same position before."
                    } else {
                        "Maia move probability adjusted toward your playing tendencies."
                    },
                )
            }
            .sortedByDescending { it.probability }
    }

    fun toStorageText(): String = buildString {
        appendLine("MIRROR2")
        appendLine("name=${enc(playerName)}")
        appendLine("gamesImported=$gamesImported")
        appendLine("gamesPlayed=$gamesPlayed")
        appendLine("moves=$movesLearned")
        appendLine("capture=$captureRate")
        appendLine("check=$checkRate")
        appendLine("castle=$castleRate")
        appendLine("center=$centerRate")
        appendLine("pieces=" + PieceType.entries.joinToString(",") { "${it.name}:${pieceRates[it] ?: 0f}" })
        positionMemory.forEach { (key, counts) ->
            append("P|").append(enc(key)).append('|')
            append(counts.entries.joinToString(",") { "${it.key}:${it.value}" })
            appendLine()
        }
    }

    companion object {
        fun empty(): MirrorProfile = MirrorProfile(
            playerName = "You",
            gamesImported = 0,
            gamesPlayed = 0,
            movesLearned = 0,
            positionMemory = emptyMap(),
            captureRate = 0f,
            checkRate = 0f,
            castleRate = 0f,
            centerRate = 0f,
            pieceRates = PieceType.entries.associateWith { 0f },
        )

        fun fromStorageText(text: String): MirrorProfile? = runCatching {
            val lines = text.lineSequence().toList()
            val version = lines.firstOrNull()
            require(version == "MIRROR1" || version == "MIRROR2")
            val fields = lines.drop(1).filter { !it.startsWith("P|") && '=' in it }.associate {
                it.substringBefore('=') to it.substringAfter('=')
            }
            val pieces = fields["pieces"].orEmpty().split(',').mapNotNull { item ->
                val name = item.substringBefore(':', "")
                val value = item.substringAfter(':', "").toFloatOrNull()
                PieceType.entries.firstOrNull { it.name == name }?.let { type -> value?.let { type to it } }
            }.toMap()
            val memory = linkedMapOf<String, Map<String, Int>>()
            lines.filter { it.startsWith("P|") }.forEach { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size == 3) {
                    val counts = parts[2].split(',').mapNotNull { token ->
                        val uci = token.substringBefore(':', "")
                        val count = token.substringAfter(':', "").toIntOrNull()
                        if (uci.isNotBlank() && count != null) uci to count else null
                    }.toMap()
                    memory[dec(parts[1])] = counts
                }
            }
            MirrorProfile(
                playerName = dec(fields.getValue("name")),
                gamesImported = if (version == "MIRROR1") fields.getValue("games").toInt() else fields.getValue("gamesImported").toInt(),
                gamesPlayed = if (version == "MIRROR1") 0 else fields["gamesPlayed"]?.toIntOrNull() ?: 0,
                movesLearned = fields.getValue("moves").toInt(),
                positionMemory = memory,
                captureRate = fields.getValue("capture").toFloat(),
                checkRate = fields.getValue("check").toFloat(),
                castleRate = fields.getValue("castle").toFloat(),
                centerRate = fields.getValue("center").toFloat(),
                pieceRates = PieceType.entries.associateWith { pieces[it] ?: 0f },
            )
        }.getOrNull()

        private fun runningRate(previous: Float, previousCount: Int, flag: Boolean): Float =
            (previous * previousCount + if (flag) 1f else 0f) / (previousCount + 1).coerceAtLeast(1)

        private fun enc(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        private fun dec(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        private fun similarity(flag: Boolean, rate: Float): Float = if (flag) rate else 1f - rate
    }
}

object MirrorProfileBuilder {
    fun build(games: List<PgnGame>, playerName: String): MirrorProfile {
        val normalizedName = playerName.trim()
        require(normalizedName.isNotBlank()) { "Enter your exact username so Mirror learns only your moves." }
        var profile = MirrorProfile.empty().copy(playerName = normalizedName)
        var matchedGames = 0

        games.forEach { game ->
            val side = when {
                game.white.equals(normalizedName, ignoreCase = true) -> Side.WHITE
                game.black.equals(normalizedName, ignoreCase = true) -> Side.BLACK
                else -> return@forEach
            }
            matchedGames++
            var state = game.headers["FEN"]?.let(::gameStateFromFen) ?: GameState.initial()
            game.moves.forEach { move ->
                val movingSide = state.turn
                if (side == movingSide) profile = profile.observeMove(state, move)
                state = ChessRules.applyLegalMove(state, move)
            }
        }

        return profile.copy(gamesImported = matchedGames, gamesPlayed = 0)
    }
}
