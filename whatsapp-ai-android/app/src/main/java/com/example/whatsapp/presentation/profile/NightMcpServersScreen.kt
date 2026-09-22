package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.runtime.asNightIntegration
import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState

private val McpBg = Color(0xFF0B0F11)
private val McpSurface = Color(0xFF151B1E)
private val McpText = Color(0xFFE7EAEC)
private val McpMuted = Color(0xFF9CA5A9)
private val McpAccent = Color(0xFF21C063)

@Composable
fun NightMcpServersScreen(
    servers: List<NightMcpServerRuntimeState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (
        existingId: String?,
        displayName: String,
        endpoint: String,
        bearerToken: String?,
        clearBearerToken: Boolean,
        enabled: Boolean,
    ) -> Unit,
    onSetEnabled: (NightMcpServerRuntimeState, Boolean) -> Unit,
    onReconnect: (NightMcpServerRuntimeState) -> Unit,
    onDelete: (NightMcpServerRuntimeState) -> Unit,
) {
    var editing by remember {
        mutableStateOf<NightMcpServerRuntimeState?>(null)
    }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember {
        mutableStateOf<NightMcpServerRuntimeState?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(McpBg)
            .statusBarsPadding(),
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
                    tint = McpText,
                )
            }
            Text(
                text = "MCP servers",
                color = McpText,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh MCP servers",
                    tint = McpMuted,
                )
            }
            IconButton(onClick = { showAdd = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add MCP server",
                    tint = McpAccent,
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
                        "Night connects to Streamable HTTP MCP servers and exposes their tools to the same agent loop as built-in and extension tools. Bearer tokens are encrypted on this device.",
                    color = McpMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (servers.isEmpty()) {
                item {
                    Text(
                        text = "No MCP servers configured yet.",
                        color = McpMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            items(
                items = servers,
                key = { it.config.id },
            ) { state ->
                McpServerRow(
                    state = state,
                    onEdit = { editing = state },
                    onSetEnabled = {
                        onSetEnabled(
                            state,
                            !state.config.enabled,
                        )
                    },
                    onReconnect = {
                        onReconnect(state)
                    },
                    onDelete = {
                        deleting = state
                    },
                )
            }
        }
    }

    if (showAdd) {
        McpServerDialog(
            title = "Add MCP server",
            initialName = "",
            initialEndpoint = "",
            hasExistingToken = false,
            initialEnabled = true,
            onDismiss = { showAdd = false },
            onSave = { name, endpoint, token, clearToken, enabled ->
                onSave(
                    null,
                    name,
                    endpoint,
                    token,
                    clearToken,
                    enabled,
                )
                showAdd = false
            },
        )
    }

    editing?.let { state ->
        McpServerDialog(
            title = "Edit MCP server",
            initialName = state.config.displayName,
            initialEndpoint = state.config.endpoint,
            hasExistingToken = state.hasBearerToken,
            initialEnabled = state.config.enabled,
            onDismiss = { editing = null },
            onSave = { name, endpoint, token, clearToken, enabled ->
                onSave(
                    state.config.id,
                    name,
                    endpoint,
                    token,
                    clearToken,
                    enabled,
                )
                editing = null
            },
        )
    }

    deleting?.let { state ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = McpSurface,
            title = {
                Text(
                    "Delete MCP server?",
                    color = McpText,
                )
            },
            text = {
                Text(
                    "Delete " + state.config.displayName +
                        "? Night will disconnect it, remove its registered tools, and delete its encrypted bearer token.",
                    color = McpMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(state)
                    }
                ) {
                    Text(
                        "Delete",
                        color = Color(0xFFFF6B78),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null }
                ) {
                    Text(
                        "Cancel",
                        color = McpMuted,
                    )
                }
            },
        )
    }
}

@Composable
private fun McpServerRow(
    state: NightMcpServerRuntimeState,
    onEdit: () -> Unit,
    onSetEnabled: () -> Unit,
    onReconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val integration = state.asNightIntegration()
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
        integration = integration,
        secondaryText = state.config.endpoint,
        statusText = status,
        onToggle = onSetEnabled,
        statusAccent = state.connected,
        trailingActions = {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit MCP server",
                    tint = McpMuted,
                )
            }
            IconButton(onClick = onDelete) {
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
                        TextButton(onClick = onReconnect) {
                            Text(
                                "Reconnect",
                                color = McpAccent,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (state.config.enabled && state.hasBearerToken) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (state.hasBearerToken) {
                        Text(
                            text = "Encrypted token saved",
                            color = McpMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun McpServerDialog(
    title: String,
    initialName: String,
    initialEndpoint: String,
    hasExistingToken: Boolean,
    initialEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        endpoint: String,
        bearerToken: String?,
        clearBearerToken: Boolean,
        enabled: Boolean,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var endpoint by remember {
        mutableStateOf(initialEndpoint)
    }
    var token by remember { mutableStateOf("") }
    var clearToken by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(initialEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = McpSurface,
        title = {
            Text(
                title,
                color = McpText,
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                McpField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Server name",
                )
                McpField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = "Streamable HTTP endpoint",
                )
                McpField(
                    value = token,
                    onValueChange = {
                        token = it
                        if (it.isNotBlank()) {
                            clearToken = false
                        }
                    },
                    label =
                        if (hasExistingToken) {
                            "New bearer token (leave blank to keep)"
                        } else {
                            "Bearer token (optional)"
                        },
                    secret = true,
                )

                if (hasExistingToken) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = clearToken,
                            onCheckedChange = {
                                clearToken = it
                                if (it) token = ""
                            },
                        )
                        Text(
                            "Remove saved bearer token",
                            color = McpText,
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                        },
                    )
                    Text(
                        "Connect automatically",
                        color = McpText,
                        fontSize = 12.sp,
                    )
                }

                Text(
                    text =
                        "Night sends only the selected tool arguments and current chat id to an MCP tool. Provider API keys and full chat history are not forwarded.",
                    color = McpMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        endpoint.trim(),
                        token.trim()
                            .ifBlank { null },
                        clearToken,
                        enabled,
                    )
                },
                enabled =
                    name.isNotBlank() &&
                        (
                            endpoint.startsWith("https://") ||
                                endpoint.startsWith("http://")
                        ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = McpAccent,
                        contentColor = Color(0xFF07110B),
                    ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = McpMuted,
                )
            }
        },
    )
}

@Composable
private fun McpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation =
            if (secret) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = McpText,
                unfocusedTextColor = McpText,
                cursorColor = McpAccent,
            ),
    )
}
