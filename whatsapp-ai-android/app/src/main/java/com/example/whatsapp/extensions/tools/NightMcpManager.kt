package com.example.whatsapp.extensions.tools

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class NightMcpServerRuntimeState(
    val config: NightMcpServerConfig,
    val hasBearerToken: Boolean,
    val connected: Boolean = false,
    val toolCount: Int = 0,
    val error: String? = null,
)

class NightMcpManager private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val store = NightMcpServerStore.get(app)
    private val bridges =
        ConcurrentHashMap<String, NightMcpHttpBridge>()

    private val _states =
        MutableStateFlow<List<NightMcpServerRuntimeState>>(emptyList())
    val states: StateFlow<List<NightMcpServerRuntimeState>> = _states

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val summaries = store.list()
        val knownIds = summaries.map { it.config.id }.toSet()

        bridges.keys
            .filterNot(knownIds::contains)
            .forEach { id ->
                bridges.remove(id)?.disconnect()
            }

        val next = mutableListOf<NightMcpServerRuntimeState>()

        for (summary in summaries) {
            val config = summary.config
            if (!config.enabled) {
                bridges.remove(config.id)?.disconnect()
                next += NightMcpServerRuntimeState(
                    config = config,
                    hasBearerToken = summary.hasBearerToken,
                    connected = false,
                )
                continue
            }

            val bridge =
                NightMcpHttpBridge(
                    serverId = config.id,
                    endpoint = config.endpoint,
                    bearerToken = store.bearerToken(config),
                )

            bridges.remove(config.id)?.disconnect()
            bridges[config.id] = bridge

            val result = bridge.connect()
            result.fold(
                onSuccess = { toolCount ->
                    next += NightMcpServerRuntimeState(
                        config = config,
                        hasBearerToken = summary.hasBearerToken,
                        connected = true,
                        toolCount = toolCount,
                    )
                },
                onFailure = { error ->
                    bridge.disconnect()
                    bridges.remove(config.id, bridge)
                    next += NightMcpServerRuntimeState(
                        config = config,
                        hasBearerToken = summary.hasBearerToken,
                        connected = false,
                        error = error.message ?: "Connection failed.",
                    )
                },
            )
        }

        _states.value = next
    }

    suspend fun save(
        existingId: String? = null,
        displayName: String,
        endpoint: String,
        bearerToken: String? = null,
        clearBearerToken: Boolean = false,
        enabled: Boolean = true,
    ): NightMcpServerConfig =
        withContext(Dispatchers.IO) {
            val saved = store.upsert(
                existingId = existingId,
                displayName = displayName,
                endpoint = endpoint,
                bearerToken = bearerToken,
                clearBearerToken = clearBearerToken,
                enabled = enabled,
            )
            refresh()
            saved
        }

    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        store.setEnabled(id, enabled)
        refresh()
    }

    suspend fun reconnect(id: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val summary =
                    store.get(id)
                        ?: error("MCP server was not found.")
                require(summary.config.enabled) {
                    "Enable this MCP server before reconnecting."
                }

                val bridge =
                    NightMcpHttpBridge(
                        serverId = summary.config.id,
                        endpoint = summary.config.endpoint,
                        bearerToken = store.bearerToken(summary.config),
                    )

                bridges.remove(id)?.disconnect()
                bridges[id] = bridge

                val count = bridge.connect().getOrThrow()
                refreshState(
                    id = id,
                    connected = true,
                    toolCount = count,
                    error = null,
                )
                count
            }.onFailure { error ->
                bridges.remove(id)?.disconnect()
                refreshState(
                    id = id,
                    connected = false,
                    toolCount = 0,
                    error = error.message ?: "Connection failed.",
                )
            }
        }

    suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            bridges.remove(id)?.disconnect()
            NightMcpToolRegistry.unregisterServer(id)
            store.delete(id)
            refresh()
        }

    fun disconnectAll() {
        bridges.values.forEach { it.disconnect() }
        bridges.clear()
        val current = _states.value
        _states.value =
            current.map {
                it.copy(
                    connected = false,
                    toolCount = 0,
                )
            }
    }

    private fun refreshState(
        id: String,
        connected: Boolean,
        toolCount: Int,
        error: String?,
    ) {
        val existing =
            _states.value
                .associateBy { it.config.id }
                .toMutableMap()
        val summary = store.get(id) ?: return
        existing[id] =
            NightMcpServerRuntimeState(
                config = summary.config,
                hasBearerToken = summary.hasBearerToken,
                connected = connected,
                toolCount = toolCount,
                error = error,
            )
        _states.value =
            existing.values.sortedWith(
                compareByDescending<NightMcpServerRuntimeState> {
                    it.config.enabled
                }.thenBy {
                    it.config.displayName.lowercase()
                }
            )
    }

    companion object {
        @Volatile
        private var instance: NightMcpManager? = null

        fun get(context: Context): NightMcpManager =
            instance ?: synchronized(this) {
                instance
                    ?: NightMcpManager(context)
                        .also { instance = it }
            }
    }
}
