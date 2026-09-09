package com.night.mirrorchess.ai

import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Move

data class MovePrediction(
    val move: Move,
    val probability: Float,
    val label: String,
    val explanation: String,
)

data class OutcomePrediction(
    val loss: Float,
    val draw: Float,
    val win: Float,
)

data class PredictionBundle(
    val candidates: List<MovePrediction>,
    val source: String,
    val outcome: OutcomePrediction? = null,
)

interface MovePredictor {
    val isRealModel: Boolean
    suspend fun predict(
        state: GameState,
        selfElo: Int,
        opponentElo: Int,
        limit: Int = 3,
    ): PredictionBundle
}
