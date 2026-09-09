package com.night.mirrorchess.data

import android.content.Context
import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.squareFromName
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StoredGame(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val playerSide: Side = Side.WHITE,
    val opponentId: String = "CLUB",
    val playerElo: Int = 1800,
    val opponentElo: Int = 1800,
    val result: String = "*",
    val moves: List<String> = emptyList(),
) {
    fun replayStates(): List<GameState> {
        val states = mutableListOf(GameState.initial())
        var state = states.first()
        for (uci in moves) {
            val move = moveFromUci(state, uci) ?: break
            state = ChessRules.applyLegalMove(state, move)
            states += state
        }
        return states
    }
}

class GameRepository(context: Context) {
    private val archiveFile = AtomicTextFile(File(context.filesDir, "game_archive.json"))
    private val activeFile = AtomicTextFile(File(context.filesDir, "active_game.json"))

    fun loadArchive(): List<StoredGame> = runCatching {
        val text = archiveFile.readTextOrNull() ?: return@runCatching emptyList()
        val array = JSONArray(text)
        buildList {
            for (i in 0 until array.length()) {
                runCatching { fromJson(array.getJSONObject(i)) }.getOrNull()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    fun saveArchive(game: StoredGame): Boolean = runCatching {
        val games = (listOf(game) + loadArchive().filterNot { it.id == game.id }).take(50)
        val array = JSONArray()
        games.forEach { array.put(toJson(it)) }
        archiveFile.writeText(array.toString())
    }.isSuccess

    fun saveActive(game: StoredGame): Boolean = runCatching { activeFile.writeText(toJson(game).toString()) }.isSuccess
    fun loadActive(): StoredGame? = runCatching { activeFile.readTextOrNull()?.let { fromJson(JSONObject(it)) } }.getOrNull()
    fun clearActive() = activeFile.delete()

    private fun toJson(game: StoredGame) = JSONObject().apply {
        put("id", game.id)
        put("startedAt", game.startedAt)
        if (game.endedAt != null) put("endedAt", game.endedAt)
        put("playerSide", game.playerSide.name)
        put("opponentId", game.opponentId)
        put("playerElo", game.playerElo)
        put("opponentElo", game.opponentElo)
        put("result", game.result)
        put("moves", JSONArray(game.moves))
    }

    private fun fromJson(json: JSONObject): StoredGame {
        val movesArray = json.optJSONArray("moves") ?: JSONArray()
        val moves = buildList {
            for (i in 0 until movesArray.length()) {
                movesArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return StoredGame(
            id = json.optString("id", UUID.randomUUID().toString()),
            startedAt = json.optLong("startedAt", System.currentTimeMillis()),
            endedAt = if (json.has("endedAt")) json.optLong("endedAt") else null,
            playerSide = runCatching { Side.valueOf(json.optString("playerSide", Side.WHITE.name)) }.getOrDefault(Side.WHITE),
            opponentId = json.optString("opponentId", "CLUB"),
            playerElo = json.optInt("playerElo", 1800),
            opponentElo = json.optInt("opponentElo", 1800),
            result = json.optString("result", "*"),
            moves = moves,
        )
    }
}

fun moveFromUci(state: GameState, uci: String): Move? {
    if (uci.length !in 4..5) return null
    val from = squareFromName(uci.take(2)) ?: return null
    val to = squareFromName(uci.substring(2, 4)) ?: return null
    val promotion = uci.getOrNull(4)?.let { c ->
        when (c.lowercaseChar()) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> return null
        }
    }
    return ChessRules.findLegalMove(state, from, to, promotion)
}
