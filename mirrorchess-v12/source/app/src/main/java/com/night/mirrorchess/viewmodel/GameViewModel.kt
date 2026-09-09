package com.night.mirrorchess.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.mirrorchess.ai.HybridMovePredictor
import com.night.mirrorchess.ai.ModelManager
import com.night.mirrorchess.ai.PredictionBundle
import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameResult
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move
import com.night.mirrorchess.chess.Pgn
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.san
import com.night.mirrorchess.data.AppPreferences
import com.night.mirrorchess.data.AppSettings
import com.night.mirrorchess.data.GameRepository
import com.night.mirrorchess.data.MirrorRepository
import com.night.mirrorchess.data.StoredGame
import com.night.mirrorchess.data.moveFromUci
import com.night.mirrorchess.mirror.MirrorProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class MoveLogEntry(val ply: Int, val side: Side, val notation: String)

enum class OpponentProfile(val label: String, val description: String, val elo: Int, val mirror: Boolean = false) {
    CASUAL("Maia 1200", "Relaxed human-style play", 1200),
    CLUB("Maia 1800", "Solid club-player decisions", 1800),
    EXPERT("Maia 2200", "Sharper human-style play", 2200),
    MIRROR("Mirror Me", "A Maia opponent shaped by the moves you play", 1800, mirror = true),
}

enum class PlayerColorChoice { WHITE, BLACK, RANDOM }

data class PromotionRequest(val from: Int, val to: Int, val options: List<PieceType>)

data class GameUiState(
    val game: GameState = GameState.initial(),
    val selectedSquare: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val moveLog: List<MoveLogEntry> = emptyList(),
    val moveUcis: List<String> = emptyList(),
    val coachPrediction: PredictionBundle? = null,
    val lastUserMove: Move? = null,
    val lastUserMoveProbability: Float? = null,
    val userMoveWasTopFive: Boolean = false,
    val thinking: Boolean = false,
    val flipped: Boolean = false,
    val coachElo: Int = 1800,
    val opponentElo: Int = 1800,
    val opponentProfile: OpponentProfile = OpponentProfile.CLUB,
    val playerSide: Side = Side.WHITE,
    val modelInstalled: Boolean = false,
    val message: String = "Your move",
    val pendingPromotion: PromotionRequest? = null,
    val gameOver: Boolean = false,
    val result: String = "*",
    val resultTitle: String = "",
    val reviewing: Boolean = false,
    val reviewIndex: Int = 0,
    val reviewCount: Int = 0,
    val mirrorProfile: MirrorProfile? = null,
    val mirrorImportMessage: String? = null,
    val recentGames: List<StoredGame> = emptyList(),
    val resumableGame: StoredGame? = null,
    val settings: AppSettings = AppSettings(),
    val modelDownloadProgress: Float? = null,
    val modelMessage: String? = null,
    val modelBusy: Boolean = false,
    val engineSource: String = "Preview engine",
    val gameSaved: Boolean = true,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val predictor = HybridMovePredictor(application)
    private val modelManager = ModelManager(application)
    private val gameRepository = GameRepository(application)
    private val mirrorRepository = MirrorRepository(application)
    private val preferences = AppPreferences(application)
    private val initialSettings = preferences.load()
    private var aiJob: Job? = null
    private var modelJob: Job? = null
    private var stateHistory = mutableListOf(GameState.initial())
    private var activeId: String = java.util.UUID.randomUUID().toString()
    private var startedAt: Long = System.currentTimeMillis()

    var uiState: GameUiState by mutableStateOf(
        GameUiState(
            modelInstalled = predictor.isRealModel,
            mirrorProfile = mirrorRepository.load(),
            recentGames = gameRepository.loadArchive(),
            resumableGame = gameRepository.loadActive(),
            settings = initialSettings,
            coachElo = initialSettings.playerElo,
            engineSource = if (predictor.isRealModel) "Maia-3 5M · on-device" else "Preview engine",
        ),
    )
        private set

    private fun publish(update: GameUiState.() -> GameUiState) { uiState = uiState.update() }

    fun startGame(choice: PlayerColorChoice, profile: OpponentProfile = uiState.opponentProfile) {
        aiJob?.cancel()
        val side = when (choice) {
            PlayerColorChoice.WHITE -> Side.WHITE
            PlayerColorChoice.BLACK -> Side.BLACK
            PlayerColorChoice.RANDOM -> if (Random.nextBoolean()) Side.WHITE else Side.BLACK
        }
        val actualProfile = if (profile.mirror && uiState.mirrorProfile?.isReady != true) OpponentProfile.CLUB else profile
        activeId = java.util.UUID.randomUUID().toString()
        startedAt = System.currentTimeMillis()
        stateHistory = mutableListOf(GameState.initial())
        publish {
            GameUiState(
                playerSide = side,
                opponentProfile = actualProfile,
                opponentElo = if (actualProfile.mirror) settings.playerElo else actualProfile.elo,
                coachElo = settings.playerElo,
                flipped = side == Side.BLACK,
                modelInstalled = predictor.isRealModel,
                mirrorProfile = mirrorProfile,
                recentGames = recentGames,
                resumableGame = null,
                settings = settings,
                message = if (side == Side.WHITE) "Your move" else "Opponent is thinking…",
                thinking = side == Side.BLACK,
                engineSource = if (predictor.isRealModel) "Maia-3 5M · on-device" else "Preview engine",
                gameSaved = true,
            )
        }
        persistActive()
        if (side == Side.BLACK) launchAiMove()
    }

    fun resumeActiveGame() {
        val stored = uiState.resumableGame ?: return
        restoreStored(stored, reviewing = false)
        if (uiState.gameOver) return
        if (finishIfNeeded(uiState.game)) return
        persistActive()
        if (uiState.game.turn != uiState.playerSide && !uiState.gameOver) launchAiMove()
    }

    fun reviewGame(stored: StoredGame) { restoreStored(stored, reviewing = true) }

    private fun restoreStored(stored: StoredGame, reviewing: Boolean) {
        aiJob?.cancel()
        activeId = stored.id
        startedAt = stored.startedAt
        val replay = replayMoves(stored.moves)
        val states = replay.states
        val logs = replay.logs
        val state = states.last()
        val validUcis = replay.ucis
        stateHistory = states
        val profile = runCatching { OpponentProfile.valueOf(stored.opponentId) }.getOrDefault(OpponentProfile.CLUB)
        val gameIsOver = stored.result != "*"
        publish {
            copy(
                game = if (reviewing) states.last() else state,
                moveLog = logs,
                moveUcis = validUcis,
                playerSide = stored.playerSide,
                opponentProfile = profile,
                opponentElo = stored.opponentElo,
                coachElo = stored.playerElo,
                flipped = stored.playerSide == Side.BLACK,
                selectedSquare = null,
                legalTargets = emptySet(),
                thinking = false,
                reviewing = reviewing,
                reviewIndex = states.lastIndex,
                reviewCount = states.size,
                gameOver = gameIsOver,
                result = stored.result,
                resultTitle = if (gameIsOver) resultTitle(stored.result, stored.playerSide, null) else "",
                message = if (reviewing) "Reviewing saved game" else turnMessage(state),
                resumableGame = if (reviewing) resumableGame else null,
                // Finished games opened from Recent are archived; a finished active
                // game exists only when archiving failed and should still offer export.
                gameSaved = reviewing && gameIsOver,
            )
        }
    }

    fun onSquareTap(square: Int) {
        if (!canHumanMove()) return
        val selected = uiState.selectedSquare
        val piece = uiState.game.board[square]
        if (selected == null) {
            if (piece?.side == uiState.playerSide) select(square)
            return
        }
        if (square == selected) { clearSelection(); return }
        attemptMove(selected, square)
        if (uiState.pendingPromotion == null && uiState.selectedSquare != null && piece?.side == uiState.playerSide) select(square)
    }

    fun onMoveAttempt(from: Int, to: Int) {
        if (!canHumanMove()) return
        attemptMove(from, to)
    }

    private fun attemptMove(from: Int, to: Int) {
        val candidates = ChessRules.legalMoves(uiState.game).filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) { clearSelection(); return }
        val promotions = candidates.mapNotNull { it.promotion }.distinct()
        if (promotions.size > 1) {
            publish { copy(pendingPromotion = PromotionRequest(from, to, promotions), selectedSquare = null, legalTargets = emptySet()) }
            return
        }
        playHumanMove(candidates.first())
    }

    fun choosePromotion(type: PieceType) {
        val request = uiState.pendingPromotion ?: return
        val move = ChessRules.findLegalMove(uiState.game, request.from, request.to, type) ?: return
        publish { copy(pendingPromotion = null) }
        playHumanMove(move)
    }

    fun cancelPromotion() { publish { copy(pendingPromotion = null) } }

    private fun canHumanMove(): Boolean = !uiState.thinking && !uiState.gameOver && !uiState.reviewing && uiState.game.turn == uiState.playerSide

    private fun select(square: Int) {
        val targets = ChessRules.legalMovesFrom(uiState.game, square).map { it.to }.toSet()
        publish { copy(selectedSquare = square, legalTargets = targets) }
    }

    private fun clearSelection() { publish { copy(selectedSquare = null, legalTargets = emptySet()) } }

    private fun playHumanMove(move: Move) {
        aiJob?.cancel()
        val before = uiState.game
        val notation = move.san(before)
        val after = ChessRules.applyLegalMove(before, move)
        val learnedProfile = (uiState.mirrorProfile ?: MirrorProfile.empty()).observeMove(before, move)
        mirrorRepository.save(learnedProfile)
        appendMove(after, move, notation)
        publish {
            copy(
                mirrorProfile = learnedProfile,
                selectedSquare = null,
                legalTargets = emptySet(),
                pendingPromotion = null,
                lastUserMove = move,
                coachPrediction = null,
                lastUserMoveProbability = null,
                userMoveWasTopFive = false,
                thinking = true,
                message = "Reading the position…",
            )
        }
        persistActive()

        aiJob = viewModelScope.launch {
            if (uiState.settings.coachEnabled) {
                val coach = predictor.predict(before, uiState.coachElo, uiState.opponentElo, limit = 5)
                val match = coach.candidates.firstOrNull { it.move == move }
                publish {
                    copy(
                        coachPrediction = coach,
                        lastUserMoveProbability = match?.probability,
                        userMoveWasTopFive = match != null,
                        engineSource = coach.source,
                    )
                }
            }
            if (finishIfNeeded(after)) return@launch
            launchAiMoveInternal()
        }
    }

    private fun launchAiMove() {
        aiJob?.cancel()
        publish { copy(thinking = true, message = "Opponent is thinking…") }
        aiJob = viewModelScope.launch { launchAiMoveInternal() }
    }

    private suspend fun launchAiMoveInternal() {
        val before = uiState.game
        if (finishIfNeeded(before)) return
        var bundle = predictor.predict(before, uiState.opponentElo, uiState.coachElo, limit = if (uiState.opponentProfile.mirror) 64 else 12)
        if (uiState.opponentProfile.mirror) {
            uiState.mirrorProfile?.let { profile ->
                bundle = bundle.copy(
                    candidates = profile.personalize(before, bundle.candidates),
                    source = "Mirror me · ${bundle.source}",
                )
            }
        }
        publish { copy(engineSource = bundle.source) }
        val move = sampleMove(bundle) ?: ChessRules.legalMoves(before).firstOrNull()
        if (move == null) { finishIfNeeded(before); return }
        val notation = move.san(before)
        val after = ChessRules.applyLegalMove(before, move)
        appendMove(after, move, notation)
        persistActive()
        if (!finishIfNeeded(after)) {
            publish { copy(thinking = false, message = turnMessage(after)) }
        }
    }

    private fun appendMove(after: GameState, move: Move, notation: String) {
        stateHistory += after
        val side = after.turn.opposite()
        publish {
            copy(
                game = after,
                moveLog = moveLog + MoveLogEntry(moveLog.size + 1, side, notation),
                moveUcis = moveUcis + move.uci(),
                reviewIndex = stateHistory.lastIndex,
                reviewCount = stateHistory.size,
            )
        }
    }

    private fun repetitionCount(state: GameState): Int {
        val key = ChessRules.repetitionKey(state)
        return stateHistory.count { ChessRules.repetitionKey(it) == key }
    }

    private fun finishIfNeeded(state: GameState): Boolean {
        val status = ChessRules.status(state, repetitionCount(state))
        if (status.result == GameResult.ACTIVE) return false
        val result = when (status.result) {
            GameResult.CHECKMATE -> if (state.turn == Side.WHITE) "0-1" else "1-0"
            else -> "1/2-1/2"
        }
        finishGame(result, resultTitle(result, uiState.playerSide, status.result))
        return true
    }

    fun resign() {
        if (uiState.gameOver || uiState.reviewing) return
        aiJob?.cancel()
        val result = if (uiState.playerSide == Side.WHITE) "0-1" else "1-0"
        finishGame(result, "You resigned")
    }

    private fun finishGame(result: String, title: String) {
        aiJob?.cancel()
        val completedProfile = uiState.mirrorProfile?.takeIf { uiState.moveUcis.isNotEmpty() }?.markGamePlayed()
        if (completedProfile != null) mirrorRepository.save(completedProfile)
        publish {
            copy(
                thinking = false,
                gameOver = true,
                result = result,
                resultTitle = title,
                message = title,
                selectedSquare = null,
                legalTargets = emptySet(),
                mirrorProfile = completedProfile ?: mirrorProfile,
            )
        }
        val stored = currentStoredGame(result = result, endedAt = System.currentTimeMillis())
        val archived = gameRepository.saveArchive(stored)
        if (archived) gameRepository.clearActive() else gameRepository.saveActive(stored)
        publish {
            copy(
                recentGames = gameRepository.loadArchive(),
                resumableGame = if (archived) null else stored,
                gameSaved = archived,
            )
        }
    }

    fun undo() {
        aiJob?.cancel()
        if (uiState.reviewing || uiState.moveUcis.isEmpty()) return
        val lastHumanIndex = uiState.moveLog.indexOfLast { it.side == uiState.playerSide }
        if (lastHumanIndex < 0) return
        val keep = uiState.moveUcis.take(lastHumanIndex)
        replayCurrentMoves(keep)
        publish { copy(gameOver = false, result = "*", resultTitle = "", thinking = false, coachPrediction = null, lastUserMove = null, lastUserMoveProbability = null, userMoveWasTopFive = false, message = turnMessage(game)) }
        persistActive()
        if (uiState.game.turn != uiState.playerSide) launchAiMove()
    }

    private fun replayCurrentMoves(ucis: List<String>) {
        val replay = replayMoves(ucis)
        stateHistory = replay.states
        publish {
            copy(
                game = replay.states.last(),
                moveLog = replay.logs,
                moveUcis = replay.ucis,
                selectedSquare = null,
                legalTargets = emptySet(),
                reviewIndex = replay.states.lastIndex,
                reviewCount = replay.states.size,
            )
        }
    }

    private data class ReplayData(
        val states: MutableList<GameState>,
        val logs: List<MoveLogEntry>,
        val ucis: List<String>,
    )

    private fun replayMoves(ucis: List<String>): ReplayData {
        val states = mutableListOf(GameState.initial())
        val logs = mutableListOf<MoveLogEntry>()
        val valid = mutableListOf<String>()
        var state = states.first()
        for (uci in ucis) {
            val move = moveFromUci(state, uci) ?: break
            logs += MoveLogEntry(logs.size + 1, state.turn, move.san(state))
            state = ChessRules.applyLegalMove(state, move)
            states += state
            valid += uci
        }
        return ReplayData(states, logs, valid)
    }

    fun rematch() = startGame(if (uiState.playerSide == Side.WHITE) PlayerColorChoice.WHITE else PlayerColorChoice.BLACK, uiState.opponentProfile)

    fun flipBoard() { publish { copy(flipped = !flipped) } }
    fun setCoachElo(elo: Int) { updateSettings(uiState.settings.copy(playerElo = elo.coerceIn(600, 2800))); publish { copy(coachElo = elo.coerceIn(600, 2800), coachPrediction = null) } }
    fun setOpponentProfile(profile: OpponentProfile) { if (!uiState.thinking && (!profile.mirror || uiState.mirrorProfile?.isReady == true)) publish { copy(opponentProfile = profile, opponentElo = if (profile.mirror) settings.playerElo else profile.elo) } }

    fun reviewPrevious() {
        if (!uiState.reviewing || uiState.reviewIndex <= 0) return
        val index = uiState.reviewIndex - 1
        publish { copy(reviewIndex = index, game = stateHistory[index]) }
    }
    fun reviewNext() {
        if (!uiState.reviewing || uiState.reviewIndex >= stateHistory.lastIndex) return
        val index = uiState.reviewIndex + 1
        publish { copy(reviewIndex = index, game = stateHistory[index]) }
    }
    fun exitReview() { publish { copy(reviewing = false) } }

    fun exportCurrentPgn(): String = pgnForGame(currentStoredGame(uiState.result, if (uiState.gameOver) System.currentTimeMillis() else null))

    fun exportSavedGamesPgn(): String = uiState.recentGames
        .asReversed()
        .joinToString("\n\n") { pgnForGame(it) }

    private fun pgnForGame(stored: StoredGame): String {
        val moves = mutableListOf<Move>()
        val statesBefore = mutableListOf<GameState>()
        var state = GameState.initial()
        for (uci in stored.moves) {
            val move = moveFromUci(state, uci) ?: break
            statesBefore += state
            moves += move
            state = ChessRules.applyLegalMove(state, move)
        }
        val profile = runCatching { OpponentProfile.valueOf(stored.opponentId) }.getOrDefault(OpponentProfile.CLUB)
        val opponent = profile.label
        val white = if (stored.playerSide == Side.WHITE) "You" else opponent
        val black = if (stored.playerSide == Side.BLACK) "You" else opponent
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(stored.startedAt))
        return Pgn.fromMoves(statesBefore, moves, white, black, stored.result, mapOf("Date" to date)).toPgn()
    }

    fun importMirrorPgn(uri: Uri, playerName: String) {
        viewModelScope.launch {
            publish { copy(mirrorImportMessage = "Reading PGN…") }
            val textResult = runCatching { requireNotNull(app.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() } }
            if (textResult.isFailure) { publish { copy(mirrorImportMessage = textResult.exceptionOrNull()?.message ?: "Could not read PGN") }; return@launch }
            val result = mirrorRepository.importPgn(textResult.getOrThrow(), playerName)
            result.onSuccess { profile -> publish { copy(mirrorProfile = profile, mirrorImportMessage = "Mirror now knows ${profile.movesLearned} of your moves · ${profile.readinessPercent}% learning progress.") } }
                .onFailure { error -> publish { copy(mirrorImportMessage = error.message ?: "Import failed") } }
        }
    }

    fun deleteMirrorProfile() { mirrorRepository.delete(); publish { copy(mirrorProfile = null, mirrorImportMessage = "Mirror profile deleted.", opponentProfile = if (opponentProfile.mirror) OpponentProfile.CLUB else opponentProfile) } }

    fun downloadModel() {
        if (modelJob?.isActive == true) return
        modelJob = viewModelScope.launch {
            aiJob?.cancel()
            publish { copy(modelBusy = true, modelDownloadProgress = 0f, modelMessage = "Downloading Maia-3…") }
            try {
                val result = modelManager.download { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        publish { copy(modelDownloadProgress = progress) }
                    }
                }
                finishModelInstall(result, "Maia-3 downloaded, verified, and ready.")
            } finally {
                publish { copy(modelBusy = false, modelDownloadProgress = null) }
            }
        }
    }

    fun importModel(uri: Uri) {
        if (modelJob?.isActive == true) return
        modelJob = viewModelScope.launch {
            aiJob?.cancel()
            publish { copy(modelBusy = true, modelMessage = "Importing and verifying model…") }
            try {
                finishModelInstall(modelManager.importFrom(uri), "Maia-3 imported, verified, and ready.")
            } finally {
                publish { copy(modelBusy = false, modelDownloadProgress = null) }
            }
        }
    }

    private suspend fun finishModelInstall(result: Result<java.io.File>, successMessage: String) {
        if (result.isFailure) {
            publish {
                copy(
                    modelInstalled = predictor.isRealModel,
                    modelMessage = result.exceptionOrNull()?.message ?: "Model installation failed.",
                )
            }
            return
        }
        predictor.reloadModel()
        val validation = predictor.validateModel()
        if (validation.isSuccess) {
            publish {
                copy(
                    modelInstalled = true,
                    modelMessage = successMessage,
                    engineSource = "Maia-3 5M · on-device",
                )
            }
        } else {
            predictor.close()
            modelManager.delete()
            predictor.reloadModel()
            publish {
                copy(
                    modelInstalled = false,
                    modelMessage = "The file checksum passed, but Maia could not start on this device: ${validation.exceptionOrNull()?.message ?: "unknown error"}",
                    engineSource = "Preview engine",
                )
            }
        }
    }

    fun deleteModel() {
        aiJob?.cancel()
        modelJob?.cancel()
        predictor.close()
        modelManager.delete()
        predictor.reloadModel()
        publish {
            copy(
                modelInstalled = false,
                modelBusy = false,
                modelDownloadProgress = null,
                modelMessage = "Maia model removed. Preview engine is active.",
                engineSource = "Preview engine",
            )
        }
    }

    fun updateSettings(settings: AppSettings) { preferences.save(settings); publish { copy(settings = settings, coachElo = settings.playerElo) } }

    private fun persistActive() {
        if (uiState.gameOver || uiState.reviewing) return
        gameRepository.saveActive(currentStoredGame(result = "*", endedAt = null))
        publish { copy(resumableGame = gameRepository.loadActive()) }
    }

    private fun currentStoredGame(result: String, endedAt: Long?): StoredGame = StoredGame(
        id = activeId,
        startedAt = startedAt,
        endedAt = endedAt,
        playerSide = uiState.playerSide,
        opponentId = uiState.opponentProfile.name,
        playerElo = uiState.coachElo,
        opponentElo = uiState.opponentElo,
        result = result,
        moves = uiState.moveUcis,
    )

    private fun sampleMove(bundle: PredictionBundle): Move? {
        val candidates = bundle.candidates
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.probability.toDouble() }.coerceAtLeast(1e-9)
        var roll = Random.nextDouble() * total
        for (candidate in candidates) { roll -= candidate.probability; if (roll <= 0.0) return candidate.move }
        return candidates.first().move
    }

    private fun turnMessage(state: GameState): String {
        val check = ChessRules.isInCheck(state, state.turn)
        return when {
            state.turn == uiState.playerSide && check -> "Check — your move"
            state.turn == uiState.playerSide -> "Your move"
            check -> "Opponent is in check"
            else -> "Opponent is thinking…"
        }
    }

    private fun resultTitle(result: String, playerSide: Side, reason: GameResult?): String = when {
        reason == GameResult.DRAW_THREEFOLD -> "Draw by repetition"
        reason == GameResult.DRAW_50_MOVE -> "Draw by fifty-move rule"
        reason == GameResult.DRAW_INSUFFICIENT -> "Draw by insufficient material"
        reason == GameResult.STALEMATE -> "Draw by stalemate"
        result == "1/2-1/2" -> "Draw"
        (result == "1-0" && playerSide == Side.WHITE) || (result == "0-1" && playerSide == Side.BLACK) -> "You won"
        else -> "You lost"
    }

    override fun onCleared() {
        aiJob?.cancel()
        modelJob?.cancel()
        predictor.close()
        super.onCleared()
    }
}
