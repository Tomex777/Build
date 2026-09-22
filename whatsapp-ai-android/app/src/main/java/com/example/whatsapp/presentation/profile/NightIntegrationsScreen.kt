package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.runtime.NightInstalledExtensionSummary
import com.example.whatsapp.extensions.runtime.NightIntegrationCapability
import com.example.whatsapp.extensions.runtime.NightIntegrationSummary
import com.example.whatsapp.extensions.runtime.asNightIntegration
import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState

private val IntegrationBg = Color(0xFF0B0F11)
private val IntegrationSurface = Color(0xFF151B1E)
private val IntegrationText = Color(0xFFE7EAEC)
private val IntegrationMuted = Color(0xFF9CA5A9)
private val IntegrationAccent = Color(0xFF21C063)

private sealed interface NightIntegrationUiItem {
    val summary: NightIntegrationSummary
    val key: String

    data class Extension(
        val value: NightInstalledExtensionSummary,
    ) : NightIntegrationUiItem {
        override val summary = value.asNightIntegration()
        override val key = "extension:" + value.packageName + ":" + value.extensionId
    }

    data class Mcp(
        val value: NightMcpServerRuntimeState,
    ) : NightIntegrationUiItem {
        override val summary = value.asNightIntegration()
        override val key = "mcp:" + value.config.id
    }
}

@Composable
fun NightIntegrationsScreen(
    extensions: List<NightInstalledExtensionSummary>,
    servers: List<NightMcpServerRuntimeState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetExtensionEnabled: (NightInstalledExtensionSummary, Boolean) -> Unit,
    preferredExtensionIds: Map<NightIntegrationCapability, String> = emptyMap(),
    onSetPreferredExtension: (NightIntegrationCapability, NightInstalledExtensionSummary) -> Unit =
        { _, _ -> },
    onSaveMcp: (
        existingId: String?,
        displayName: String,
        endpoint: String,
        bearerToken: String?,
        clearBearerToken: Boolean,
        enabled: Boolean,
    ) -> Unit,
    onSetMcpEnabled: (NightMcpServerRuntimeState, Boolean) -> Unit,
    onReconnectMcp: (NightMcpServerRuntimeState) -> Unit,
    onDeleteMcp: (NightMcpServerRuntimeState) -> Unit,
) {
    var enableExtension by remember {
        mutableStateOf<NightInstalledExtensionSummary?>(null)
    }
    var editingMcp by remember {
        mutableStateOf<NightMcpServerRuntimeState?>(null)
    }
    var deletingMcp by remember {
        mutableStateOf<NightMcpServerRuntimeState?>(null)
    }
    var showAddMcp by remember { mutableStateOf(false) }

    val enabledProviderCountByCapability =
        remember(extensions) {
            extensions
                .asSequence()
                .filter { it.enabled }
                .flatMap { it.capabilities.asSequence() }
                .distinct()
                .associateWith { capability ->
                    extensions.count {
                        it.enabled && capability in it.capabilities
                    }
                }
        }

    val integrations =
        remember(extensions, servers) {
            buildList<NightIntegrationUiItem> {
                extensions.forEach { add(NightIntegrationUiItem.Extension(it)) }
                servers.forEach { add(NightIntegrationUiItem.Mcp(it)) }
            }.sortedWith(
                compareByDescending<NightIntegrationUiItem> { it.summary.enabled }
                    .thenBy { it.summary.displayName.lowercase() }
                    .thenBy { it.summary.kind.wireName }
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IntegrationBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = IntegrationText,
                )
            }
            Text(
                text = "Integrations",
                color = IntegrationText,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh integrations",
                    tint = IntegrationMuted,
                )
            }
            IconButton(onClick = { showAddMcp = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add MCP server",
                    tint = IntegrationAccent,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 40.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text =
                        "Extensions and MCP servers share one Night integration system. " +
                            "The small EXT or MCP marker only identifies which runtime handles it.",
                    color = IntegrationMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (integrations.isEmpty()) {
                item {
                    Text(
                        text =
                            "No integrations yet. Install a Night extension APK or add an MCP server.",
                        color = IntegrationMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            items(
                items = integrations,
                key = { it.key },
            ) { item ->
                when (item) {
                    is NightIntegrationUiItem.Extension -> {
                        val extension = item.value
                        val toolLabel =
                            extension.toolCount.toString() +
                                if (extension.toolCount == 1) " tool" else " tools"
                        val messageLabel =
                            extension.messageTypeCount.toString() +
                                if (extension.messageTypeCount == 1) {
                                    " message type"
                                } else {
                                    " message types"
                                }

                        NightIntegrationRow(
                            integration = item.summary,
                            secondaryText = extension.packageName,
                            statusText =
                                toolLabel + " • " + messageLabel +
                                    if (extension.enabled) " • enabled" else " • disabled",
                            onToggle = {
                                if (extension.enabled) {
                                    onSetExtensionEnabled(extension, false)
                                } else if (extension.error == null) {
                                    enableExtension = extension
                                }
                            },
                            toggleEnabled = extension.enabled || extension.error == null,
                            statusAccent = extension.enabled,
                            footer = {
                                if (extension.capabilities.isNotEmpty()) {
                                    Text(
                                        text =
                                            extension.capabilities
                                                .sortedBy { it.wireName }
                                                .joinToString(" • ") { it.label },
                                        color = IntegrationMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            bottom = 2.dp,
                                        ),
                                    )
                                }

                                extension.capabilities
                                    .sortedBy { it.wireName }
                                    .forEach { capability ->
                                        val providerCount =
                                            enabledProviderCountByCapability[
                                                capability
                                            ] ?: 0
                                        if (extension.enabled && providerCount > 1) {
                                            val preferred =
                                                preferredExtensionIds[
                                                    capability
                                                ] == extension.extensionId
                                            TextButton(
                                                onClick = {
                                                    if (!preferred) {
                                                        onSetPreferredExtension(
                                                            capability,
                                                            extension,
                                                        )
                                                    }
                                                },
                                            ) {
                                                Text(
                                                    text =
                                                        if (preferred) {
                                                            "Preferred for " +
                                                                capability.label
                                                        } else {
                                                            "Use for " +
                                                                capability.label
                                                        },
                                                    color =
                                                        if (preferred) {
                                                            IntegrationAccent
                                                        } else {
                                                            IntegrationMuted
                                                        },
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }
                                    }
                            },
                        )
                    }

                    is NightIntegrationUiItem.Mcp -> {
                        val state = item.value
                        val status =
                            when {
                                !state.config.enabled -> "Disabled"
                                state.connected ->
                                    "Connected • " +
                                        state.toolCount +
                                        if (state.toolCount == 1) " tool" else " tools"
                                else -> "Disconnected"
                            }

                        NightIntegrationRow(
                            integration = item.summary,
                            secondaryText = state.config.endpoint,
                            statusText = status,
                            onToggle = {
                                onSetMcpEnabled(
                                    state,
                                    !state.config.enabled,
                                )
                            },
                            statusAccent = state.connected,
                            trailingActions = {
                                IconButton(onClick = { editingMcp = state }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit MCP server",
                                        tint = IntegrationMuted,
                                    )
                                }
                                IconButton(onClick = { deletingMcp = state }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete MCP server",
                                        tint = Color(0xFFFF6B78),
                                    )
                                }
                            },
                            footer = {
                                if (state.config.enabled || state.hasBearerToken) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (state.config.enabled) {
                                            TextButton(
                                                onClick = {
                                                    onReconnectMcp(state)
                                                },
                                            ) {
                                                Text(
                                                    "Reconnect",
                                                    color = IntegrationAccent,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }

                                        if (
                                            state.config.enabled &&
                                            state.hasBearerToken
                                        ) {
                                            Spacer(
                                                modifier = Modifier.width(8.dp),
                                            )
                                        }

                                        if (state.hasBearerToken) {
                                            Text(
                                                text = "Encrypted token saved",
                                                color = IntegrationMuted,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddMcp) {
        McpServerDialog(
            title = "Add MCP server",
            initialName = "",
            initialEndpoint = "",
            hasExistingToken = false,
            initialEnabled = true,
            onDismiss = { showAddMcp = false },
            onSave = { name, endpoint, token, clearToken, enabled ->
                onSaveMcp(
                    null,
                    name,
                    endpoint,
                    token,
                    clearToken,
                    enabled,
                )
                showAddMcp = false
            },
        )
    }

    editingMcp?.let { state ->
        McpServerDialog(
            title = "Edit MCP server",
            initialName = state.config.displayName,
            initialEndpoint = state.config.endpoint,
            hasExistingToken = state.hasBearerToken,
            initialEnabled = state.config.enabled,
            onDismiss = { editingMcp = null },
            onSave = { name, endpoint, token, clearToken, enabled ->
                onSaveMcp(
                    state.config.id,
                    name,
                    endpoint,
                    token,
                    clearToken,
                    enabled,
                )
                editingMcp = null
            },
        )
    }

    enableExtension?.let { extension ->
        AlertDialog(
            onDismissRequest = { enableExtension = null },
            containerColor = IntegrationSurface,
            title = {
                Text(
                    "Enable integration?",
                    color = IntegrationText,
                )
            },
            text = {
                Text(
                    text =
                        extension.displayName + " can expose " +
                            extension.toolCount +
                            if (extension.toolCount == 1) {
                                " tool"
                            } else {
                                " tools"
                            } +
                            " and " +
                            extension.messageTypeCount +
                            if (extension.messageTypeCount == 1) {
                                " message type"
                            } else {
                                " message types"
                            } +
                            " to Night.",
                    color = IntegrationMuted,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enableExtension = null
                        onSetExtensionEnabled(extension, true)
                    },
                ) {
                    Text(
                        "Enable",
                        color = IntegrationAccent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { enableExtension = null }) {
                    Text(
                        "Cancel",
                        color = IntegrationMuted,
                    )
                }
            },
        )
    }

    deletingMcp?.let { state ->
        AlertDialog(
            onDismissRequest = { deletingMcp = null },
            containerColor = IntegrationSurface,
            title = {
                Text(
                    "Delete integration?",
                    color = IntegrationText,
                )
            },
            text = {
                Text(
                    text =
                        "Delete " + state.config.displayName +
                            "? Night will disconnect it, remove its tools, and delete its encrypted bearer token.",
                    color = IntegrationMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMcp = null
                        onDeleteMcp(state)
                    },
                ) {
                    Text(
                        "Delete",
                        color = Color(0xFFFF6B78),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMcp = null }) {
                    Text(
                        "Cancel",
                        color = IntegrationMuted,
                    )
                }
            },
        )
    }
}
