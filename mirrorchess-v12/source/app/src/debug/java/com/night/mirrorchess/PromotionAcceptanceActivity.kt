package com.night.mirrorchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import com.night.mirrorchess.chess.gameStateFromFen
import com.night.mirrorchess.ui.GameScreen
import com.night.mirrorchess.ui.MirrorChessTheme
import com.night.mirrorchess.viewmodel.GameUiState
import com.night.mirrorchess.viewmodel.GameViewModel

/** Debug-only fixture for exercising the real promotion route against the selected piece set. */
class PromotionAcceptanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = requireNotNull(gameStateFromFen(intent.getStringExtra("fen").orEmpty()))
        val viewModel = ViewModelProvider(this)[GameViewModel::class.java]
        val delegate = GameViewModel::class.java.getDeclaredField("uiState\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val mutableState = delegate.get(viewModel) as MutableState<GameUiState>
        mutableState.value = mutableState.value.copy(
            game = state,
            selectedSquare = null,
            legalTargets = emptySet(),
            thinking = false,
            flipped = false,
            playerSide = state.turn,
            pendingPromotion = null,
            gameOver = false,
            reviewing = false,
            message = "Your move",
        )
        setContent {
            MirrorChessTheme {
                GameScreen(viewModel = viewModel, onExit = ::finish, onExport = {})
            }
        }
    }
}
