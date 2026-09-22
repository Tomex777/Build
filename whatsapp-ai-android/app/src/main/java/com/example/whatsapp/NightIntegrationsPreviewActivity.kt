package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.whatsapp.extensions.runtime.NightInstalledExtensionSummary
import com.example.whatsapp.extensions.tools.NightMcpServerConfig
import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState
import com.example.whatsapp.presentation.profile.NightIntegrationsScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightIntegrationsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val now = 1_797_000_000_000L
        var extensions by mutableStateOf(
            listOf(
                NightInstalledExtensionSummary(
                    extensionId = "animepahe",
                    displayName = "AnimePahe",
                    packageName = "com.night.extensions.animepahe",
                    serviceName = "com.night.extensions.animepahe.NightExtensionService",
                    toolCount = 4,
                    messageTypeCount = 2,
                    enabled = true,
                ),
                NightInstalledExtensionSummary(
                    extensionId = "music",
                    displayName = "Music",
                    packageName = "com.night.extensions.music",
                    serviceName = "com.night.extensions.music.NightExtensionService",
                    toolCount = 3,
                    messageTypeCount = 1,
                    enabled = false,
                ),
            ),
        )
        var servers by mutableStateOf(
            listOf(
                NightMcpServerRuntimeState(
                    config = NightMcpServerConfig(
                        id = "github",
                        displayName = "GitHub",
                        endpoint = "https://mcp.example.com/github",
                        secretAlias = "preview-github-secret",
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    hasBearerToken = true,
                    connected = true,
                    toolCount = 12,
                ),
            ),
        )

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightIntegrationsScreen(
                    extensions = extensions,
                    servers = servers,
                    onBack = {},
                    onRefresh = {},
                    onSetExtensionEnabled = { extension, enabled ->
                        extensions =
                            extensions.map {
                                if (
                                    it.packageName == extension.packageName &&
                                    it.extensionId == extension.extensionId
                                ) {
                                    it.copy(enabled = enabled)
                                } else {
                                    it
                                }
                            }
                    },
                    onSaveMcp = { existingId, name, endpoint, _, _, enabled ->
                        val safeName = name.trim().ifBlank { "Preview MCP" }
                        val safeEndpoint =
                            endpoint.trim().ifBlank {
                                "https://mcp.example.com/preview"
                            }
                        val id = existingId ?: "preview-added"
                        val existing =
                            servers.firstOrNull { it.config.id == id }
                        val state =
                            NightMcpServerRuntimeState(
                                config = NightMcpServerConfig(
                                    id = id,
                                    displayName = safeName,
                                    endpoint = safeEndpoint,
                                    secretAlias =
                                        existing?.config?.secretAlias
                                            ?: "preview-" + id + "-secret",
                                    enabled = enabled,
                                    createdAt =
                                        existing?.config?.createdAt ?: now,
                                    updatedAt = now + 1L,
                                ),
                                hasBearerToken =
                                    existing?.hasBearerToken ?: false,
                                connected = false,
                                toolCount = 0,
                            )
                        servers =
                            servers.filterNot { it.config.id == id } + state
                    },
                    onSetMcpEnabled = { state, enabled ->
                        servers =
                            servers.map {
                                if (it.config.id == state.config.id) {
                                    it.copy(
                                        config =
                                            it.config.copy(
                                                enabled = enabled,
                                                updatedAt = now + 2L,
                                            ),
                                        connected = enabled && it.connected,
                                    )
                                } else {
                                    it
                                }
                            }
                    },
                    onReconnectMcp = { state ->
                        servers =
                            servers.map {
                                if (it.config.id == state.config.id) {
                                    it.copy(
                                        connected = true,
                                        toolCount =
                                            it.toolCount.coerceAtLeast(1),
                                        error = null,
                                    )
                                } else {
                                    it
                                }
                            }
                    },
                    onDeleteMcp = { state ->
                        servers =
                            servers.filterNot {
                                it.config.id == state.config.id
                            }
                    },
                )
            }
        }
    }
}
