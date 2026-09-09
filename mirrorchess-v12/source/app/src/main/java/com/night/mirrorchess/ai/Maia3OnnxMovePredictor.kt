package com.night.mirrorchess.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.night.mirrorchess.chess.ChessRules
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.exp

class Maia3OnnxMovePredictor(context: Context) : MovePredictor, AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = ModelManager(appContext)
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var options: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null
    private var sessionSource: String? = null

    override val isRealModel: Boolean get() = modelExists()

    fun modelExists(): Boolean = manager.isVerifiedInstalled() || manager.bundledAssetExists()

    fun reload() {
        session?.close(); session = null
        options?.close(); options = null
        sessionSource = null
    }

    private fun ensureSession(): OrtSession {
        val source = when {
            manager.isVerifiedInstalled() -> manager.modelFile.absolutePath
            manager.bundledAssetExists() -> "asset:${ModelManager.MODEL_ASSET}"
            else -> error("Maia-3 model is not installed")
        }
        if (session != null && sessionSource == source) return requireNotNull(session)
        reload()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            setIntraOpNumThreads(2)
        }
        options = opts
        val created = if (source.startsWith("asset:")) {
            val bytes = appContext.assets.open(ModelManager.MODEL_ASSET).use { it.readBytes() }
            environment.createSession(bytes, opts)
        } else {
            environment.createSession(source, opts)
        }
        session = created
        sessionSource = source
        return created
    }

    override suspend fun predict(
        state: GameState,
        selfElo: Int,
        opponentElo: Int,
        limit: Int,
    ): PredictionBundle = withContext(Dispatchers.Default) {
        val legal = ChessRules.legalMoves(state)
        if (legal.isEmpty()) return@withContext PredictionBundle(emptyList(), "Maia-3 5M")

        val ortSession = ensureSession()
        val tokens = Maia3Encoding.boardTokens(state)

        OnnxTensor.createTensor(environment, FloatBuffer.wrap(tokens), longArrayOf(1, 64, 12)).use { tokenTensor ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(selfElo.toFloat())), longArrayOf(1)).use { selfTensor ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(opponentElo.toFloat())), longArrayOf(1)).use { oppTensor ->
                    val inputs = mapOf("tokens" to tokenTensor, "elo_self" to selfTensor, "elo_oppo" to oppTensor)
                    ortSession.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = (result.get("logits_move").orElseThrow().value as Array<FloatArray>)[0]
                        @Suppress("UNCHECKED_CAST")
                        val valueLogits = (result.get("logits_value").orElseThrow().value as Array<FloatArray>)[0]

                        val indexed = legal.mapNotNull { move ->
                            Maia3Encoding.vocabularyIndex(move, state.turn)?.let { idx -> Triple(move, idx, logits[idx]) }
                        }
                        val maxLogit = indexed.maxOfOrNull { it.third } ?: 0f
                        val weights = indexed.map { exp((it.third - maxLogit).toDouble()) }
                        val total = weights.sum().coerceAtLeast(1e-12)
                        val candidates = indexed.indices
                            .map { i -> indexed[i] to (weights[i] / total).toFloat() }
                            .sortedByDescending { it.second }
                            .take(limit)
                            .map { (triple, probability) ->
                                val (move, _, _) = triple
                                MovePrediction(
                                    move = move,
                                    probability = probability,
                                    label = move.displayName(state),
                                    explanation = "A move a ${selfElo}-rated human is likely to choose in this position.",
                                )
                            }
                        val outcomeP = softmax(valueLogits)
                        PredictionBundle(
                            candidates = candidates,
                            source = "Maia-3 5M · on-device",
                            outcome = OutcomePrediction(
                                loss = outcomeP.getOrElse(0) { 0f },
                                draw = outcomeP.getOrElse(1) { 0f },
                                win = outcomeP.getOrElse(2) { 0f },
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        val e = DoubleArray(values.size) { i -> exp((values[i] - max).toDouble()) }
        val total = e.sum().coerceAtLeast(1e-12)
        return FloatArray(values.size) { i -> (e[i] / total).toFloat() }
    }

    override fun close() = reload()
}
