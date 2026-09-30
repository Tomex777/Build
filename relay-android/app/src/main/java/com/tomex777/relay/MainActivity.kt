package com.tomex777.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tomex777.relay.ui.RelayActions
import com.tomex777.relay.ui.RelayApp
import com.tomex777.relay.ui.RelayUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RelayApp(
                state = RelayUiState(isLoading = true),
                actions = RelayActions(),
            )
        }
    }
}
