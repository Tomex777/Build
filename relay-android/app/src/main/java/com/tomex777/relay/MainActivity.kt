package com.tomex777.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.tomex.relay.data.RelayDataSource
import com.tomex777.relay.ui.RelayActions
import com.tomex777.relay.ui.RelayApp
import com.tomex777.relay.ui.RelayUiState
import com.tomex777.relay.ui.toDataThemeMode
import com.tomex777.relay.ui.toUiState

class MainActivity : ComponentActivity() {
    private val uiState = mutableStateOf(RelayUiState(isLoading = true))
    private var connection: RelayRuntime.Connection? = null

    @Volatile
    private var destroyed = false

    private val actions = RelayActions(
        onCreateTask = { title, note ->
            mutate { createTask(title, note) }
        },
        onEditTask = { id, title, note ->
            mutate { editTask(id, title, note) }
        },
        onDeleteTask = { id ->
            mutate { deleteTask(id) }
        },
        onSetTaskCompleted = { id, completed ->
            mutate { setTaskCompleted(id, completed) }
        },
        onShowCompletedChanged = { showCompleted ->
            mutate {
                val current = snapshot().settings
                updateSettings(current.copy(showCompleted = showCompleted))
            }
        },
        onThemeModeChanged = { themeMode ->
            mutate {
                val current = snapshot().settings
                updateSettings(current.copy(themeMode = themeMode.toDataThemeMode()))
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RelayApp(
                state = uiState.value,
                actions = actions,
            )
        }

        connection = RelayRuntime.connect(
            context = applicationContext,
            onSnapshot = { snapshot ->
                if (!destroyed) {
                    uiState.value = snapshot.toUiState()
                }
            },
            onError = ::reportError,
        )
    }

    private fun mutate(block: RelayDataSource.() -> Unit) {
        RelayRuntime.mutate(
            context = applicationContext,
            onError = ::reportError,
            block = block,
        )
    }

    private fun reportError(error: Throwable) {
        if (destroyed) return
        uiState.value = uiState.value.copy(
            isLoading = false,
            errorMessage = error.message ?: "Relay couldn't save that change.",
        )
    }

    override fun onDestroy() {
        destroyed = true
        connection?.close()
        connection = null
        super.onDestroy()
    }
}
