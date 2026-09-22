package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.runtime.NightInstalledExtensionSummary
import com.example.whatsapp.extensions.runtime.asNightIntegration

private val ExtensionBg = Color(0xFF0B0F11)
private val ExtensionText = Color(0xFFE7EAEC)
private val ExtensionMuted = Color(0xFF9CA5A9)
private val ExtensionAccent = Color(0xFF21C063)

@Composable
fun NightExtensionsScreen(
    extensions: List<NightInstalledExtensionSummary>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetEnabled: (
        NightInstalledExtensionSummary,
        Boolean,
    ) -> Unit,
) {
    var enableConfirm by remember {
        mutableStateOf<NightInstalledExtensionSummary?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ExtensionBg)
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
                    tint = ExtensionText,
                )
            }
            Text(
                text = "Extensions",
                color = ExtensionText,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh extensions",
                    tint = ExtensionMuted,
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
                        "Installed extension APKs are discovered automatically but stay disabled until you enable them here. Enabled extensions can expose tools and rich message types to Night's agent runtime.",
                    color = ExtensionMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (extensions.isEmpty()) {
                item {
                    Text(
                        text =
                            "No Night extension APKs were found on this device.",
                        color = ExtensionMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            items(
                items = extensions,
                key = {
                    it.packageName + "::" +
                        it.serviceName + "::" +
                        it.extensionId
                },
            ) { extension ->
                ExtensionRow(
                    extension = extension,
                    onToggle = {
                        if (extension.enabled) {
                            onSetEnabled(extension, false)
                        } else if (extension.error == null) {
                            enableConfirm = extension
                        }
                    },
                )
            }
        }
    }

    enableConfirm?.let { extension ->
        AlertDialog(
            onDismissRequest = {
                enableConfirm = null
            },
            containerColor = Color(0xFF151B1E),
            title = {
                Text(
                    "Enable extension?",
                    color = ExtensionText,
                )
            },
            text = {
                Text(
                    buildString {
                        append(extension.displayName)
                        append(" can expose ")
                        append(extension.toolCount)
                        append(
                            if (extension.toolCount == 1) {
                                " tool"
                            } else {
                                " tools"
                            }
                        )
                        append(" and ")
                        append(extension.messageTypeCount)
                        append(
                            if (extension.messageTypeCount == 1) {
                                " message type"
                            } else {
                                " message types"
                            }
                        )
                        append(
                            " to Night. Tool calls receive only their arguments and the current chat id, not provider API keys or the full conversation."
                        )
                    },
                    color = ExtensionMuted,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enableConfirm = null
                        onSetEnabled(extension, true)
                    }
                ) {
                    Text(
                        "Enable",
                        color = ExtensionAccent,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        enableConfirm = null
                    }
                ) {
                    Text(
                        "Cancel",
                        color = ExtensionMuted,
                    )
                }
            },
        )
    }
}

@Composable
private fun ExtensionRow(
    extension: NightInstalledExtensionSummary,
    onToggle: () -> Unit,
) {
    val toolLabel =
        extension.toolCount.toString() +
            if (extension.toolCount == 1) " tool" else " tools"
    val messageLabel =
        extension.messageTypeCount.toString() +
            if (extension.messageTypeCount == 1) " message type" else " message types"

    NightIntegrationRow(
        integration = extension.asNightIntegration(),
        secondaryText = extension.packageName,
        statusText =
            toolLabel + " • " + messageLabel +
                if (extension.enabled) " • enabled" else " • disabled",
        onToggle = onToggle,
        toggleEnabled = extension.enabled || extension.error == null,
        statusAccent = extension.enabled,
    )
}
