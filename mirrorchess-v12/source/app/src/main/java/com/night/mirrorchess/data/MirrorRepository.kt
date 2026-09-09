package com.night.mirrorchess.data

import android.content.Context
import com.night.mirrorchess.chess.Pgn
import com.night.mirrorchess.mirror.MirrorProfile
import com.night.mirrorchess.mirror.MirrorProfileBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MirrorRepository(context: Context) {
    private val file = AtomicTextFile(File(context.filesDir, "mirror_profile.dat"))

    fun load(): MirrorProfile? = runCatching {
        file.readTextOrNull()?.let(MirrorProfile::fromStorageText)
    }.getOrNull()

    fun save(profile: MirrorProfile): Boolean = runCatching { file.writeText(profile.toStorageText()) }.isSuccess

    suspend fun importPgn(text: String, playerName: String): Result<MirrorProfile> = withContext(Dispatchers.Default) {
        runCatching {
            require(playerName.isNotBlank()) { "Enter your exact username so Mirror learns only your moves." }
            val parsed = Pgn.parseMany(text)
            require(parsed.games.isNotEmpty()) { parsed.errors.firstOrNull() ?: "No chess games were found in that PGN." }
            val imported = MirrorProfileBuilder.build(parsed.games, playerName)
            require(imported.movesLearned > 0) {
                if (playerName.isBlank()) "No moves could be learned from the imported games."
                else "No games matched '$playerName'. Check the exact Chess.com/Lichess username in the PGN."
            }
            val merged = (load() ?: MirrorProfile.empty()).merge(imported)
            check(save(merged)) { "Mirror learning could not be saved. Please try again." }
            merged
        }
    }

    fun delete() { file.delete() }
}
