package com.night.mirrorchess.ai

import android.content.Context
import com.night.mirrorchess.chess.GameState
import kotlinx.coroutines.CancellationException

class HybridMovePredictor(context: Context) : MovePredictor, AutoCloseable {
    private val real = Maia3OnnxMovePredictor(context.applicationContext)
    private val fallback = HeuristicMovePredictor()
    @Volatile var lastModelError: String? = null
        private set

    override val isRealModel: Boolean get() = real.modelExists()

    override suspend fun predict(
        state: GameState,
        selfElo: Int,
        opponentElo: Int,
        limit: Int,
    ): PredictionBundle {
        if (!real.modelExists()) return fallback.predict(state, selfElo, opponentElo, limit)
        return try {
            real.predict(state, selfElo, opponentElo, limit).also { lastModelError = null }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lastModelError = error.message ?: error::class.java.simpleName
            fallback.predict(state, selfElo, opponentElo, limit).copy(
                source = "Preview engine · Maia unavailable",
            )
        }
    }

    fun reloadModel() {
        real.reload()
        lastModelError = null
    }

    suspend fun validateModel(): Result<Unit> {
        if (!real.modelExists()) return Result.failure(IllegalStateException("The verified Maia model is not installed."))
        return try {
            val probe = real.predict(GameState.initial(), 1800, 1800, limit = 1)
            check(probe.candidates.isNotEmpty()) { "Maia returned no legal opening moves." }
            lastModelError = null
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lastModelError = error.message ?: error::class.java.simpleName
            Result.failure(error)
        }
    }

    override fun close() = real.close()
}
