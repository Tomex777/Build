package com.night.mirrorchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.ui.GameScreen
import com.night.mirrorchess.ui.MirrorChessTheme
import com.night.mirrorchess.viewmodel.GameUiState
import com.night.mirrorchess.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debug-only fixture that drives the real result overlay through its enter transition. */
class ResultAcceptanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra("kind").orEmpty().lowercase()
        val viewModel = ViewModelProvider(this)[GameViewModel::class.java]
        val delegate = GameViewModel::class.java.getDeclaredField("uiState\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val mutableState = delegate.get(viewModel) as MutableState<GameUiState>

        val result = when (kind) {
            "win" -> "1-0"
            "loss" -> "0-1"
            else -> "1/2-1/2"
        }
        val title = when (kind) {
            "win" -> "You won"
            "loss" -> "You lost"
            else -> "Draw"
        }
        val reason = when (kind) {
            "win", "loss" -> "by checkmate"
            else -> "by repetition"
        }
        mutableState.value = mutableState.value.copy(
            playerSide = Side.WHITE,
            gameOver = false,
            reviewing = false,
            thinking = false,
            result = "*",
            resultTitle = "",
            resultReason = "",
            gameSaved = true,
            message = "Game complete",
        )

        setContent {
            MirrorChessTheme {
                GameScreen(viewModel = viewModel, onExit = ::finish, onExport = {})
            }
        }

        lifecycleScope.launch {
            delay(250)
            mutableState.value = mutableState.value.copy(
                gameOver = true,
                result = result,
                resultTitle = title,
                resultReason = reason,
                gameSaved = true,
                thinking = false,
                message = title,
            )
        }
    }
}
