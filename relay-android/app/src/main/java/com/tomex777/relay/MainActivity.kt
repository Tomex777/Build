package com.tomex777.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.tomex.relay.data.AndroidAtomicRelayPersistence
import com.tomex.relay.data.DefaultRelayDataSource
import com.tomex.relay.data.RelayDataSource
import com.tomex.relay.data.RelaySubscription
import com.tomex777.relay.ui.RelayActions
import com.tomex777.relay.ui.RelayApp
import com.tomex777.relay.ui.RelayUiState
import com.tomex777.relay.ui.toDataThemeMode
import com.tomex777.relay.ui.toUiState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val uiState = mutableStateOf(RelayUiState(isLoading = true))
    private var dataSource: RelayDataSource? = null

    @Volatile
    private var subscription: RelaySubscription? = null

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

        worker.execute {
            runCatching {
                DefaultRelayDataSource(
                    persistence = AndroidAtomicRelayPersistence(applicationContext),
                )
            }.onSuccess { source ->
                dataSource = source
                val activeSubscription = source.observe { snapshot ->
                    if (!destroyed) {
                        runOnUiThread {
                            if (!destroyed) {
                                uiState.value = snapshot.toUiState()
                            }
                        }
                    }
                }
                if (destroyed) {
                    activeSubscription.close()
                } else {
                    subscription = activeSubscription
                }
            }.onFailure(::reportError)
        }
    }

    private fun mutate(block: RelayDataSource.() -> Unit) {
        worker.execute {
            val source = dataSource ?: return@execute
            runCatching {
                source.block()
            }.onFailure(::reportError)
        }
    }

    private fun reportError(error: Throwable) {
        if (destroyed) return
        runOnUiThread {
            if (!destroyed) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Relay couldn't save that change.",
                )
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        subscription?.close()
        subscription = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
