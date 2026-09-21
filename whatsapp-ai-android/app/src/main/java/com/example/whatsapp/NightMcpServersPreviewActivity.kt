package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.extensions.tools.NightMcpServerConfig
import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState
import com.example.whatsapp.presentation.profile.NightMcpServersScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightMcpServersPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val now = 1_797_000_000_000L
        val connected = NightMcpServerRuntimeState(
            config = NightMcpServerConfig(
                id = "preview-github",
                displayName = "GitHub MCP",
                endpoint = "https://mcp.example.com/github",
                secretAlias = "preview-github-secret",
                enabled = true,
                createdAt = now,
                updatedAt = now,
            ),
            hasBearerToken = true,
            connected = true,
            toolCount = 12,
        )
        val failed = NightMcpServerRuntimeState(
            config = NightMcpServerConfig(
                id = "preview-files",
                displayName = "Files MCP",
                endpoint = "https://mcp.example.com/files",
                secretAlias = "preview-files-secret",
                enabled = true,
                createdAt = now,
                updatedAt = now - 1L,
            ),
            hasBearerToken = false,
            connected = false,
            toolCount = 0,
            error = "Connection refused",
        )
        val disabled = NightMcpServerRuntimeState(
            config = NightMcpServerConfig(
                id = "preview-disabled",
                displayName = "Local tools",
                endpoint = "https://mcp.example.com/local",
                secretAlias = "preview-disabled-secret",
                enabled = false,
                createdAt = now,
                updatedAt = now - 2L,
            ),
            hasBearerToken = false,
            connected = false,
        )

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMcpServersScreen(
                    servers = listOf(
                        connected,
                        failed,
                        disabled,
                    ),
                    onBack = {},
                    onRefresh = {},
                    onSave = { _, _, _, _, _, _ -> },
                    onSetEnabled = { _, _ -> },
                    onReconnect = {},
                    onDelete = {},
                )
            }
        }
    }
}
