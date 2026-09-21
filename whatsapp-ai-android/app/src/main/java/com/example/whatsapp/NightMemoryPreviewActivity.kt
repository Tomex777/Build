package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.whatsapp.data.night.NightChatEntity
import com.example.whatsapp.data.night.NightSummaryCheckpointEntity
import com.example.whatsapp.presentation.profile.NightChatMemoryScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightMemoryPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                var refreshed by remember { mutableStateOf(false) }
                val baseTime = 1_797_000_000_000L

                val chat = NightChatEntity(
                    id = "memory-preview",
                    title = "Night",
                    createdAt = baseTime - 600_000L,
                    updatedAt = baseTime,
                    latestSummary = if (refreshed) {
                        "Browser verification is complete. Memory checkpoints are now hardened and the next phase is Library + Tools integration."
                    } else {
                        "Browser verification is complete. Memory & Summary hardening is in progress."
                    },
                    summaryUpdatedAt = if (refreshed) baseTime + 300_000L else baseTime,
                    lastSummarizedMessageAt = if (refreshed) baseTime + 300_000L else baseTime,
                    summaryDirty = !refreshed,
                )

                val checkpoints = buildList {
                    if (refreshed) {
                        add(
                            NightSummaryCheckpointEntity(
                                id = "checkpoint-3",
                                chatId = chat.id,
                                summary = chat.latestSummary,
                                fromMessageAt = baseTime + 1L,
                                toMessageAt = baseTime + 300_000L,
                                createdAt = baseTime + 300_000L,
                            )
                        )
                    }
                    add(
                        NightSummaryCheckpointEntity(
                            id = "checkpoint-2",
                            chatId = chat.id,
                            summary = "Browser verification is complete. Memory & Summary hardening is in progress.",
                            fromMessageAt = baseTime - 300_000L,
                            toMessageAt = baseTime,
                            createdAt = baseTime,
                        )
                    )
                    add(
                        NightSummaryCheckpointEntity(
                            id = "checkpoint-1",
                            chatId = chat.id,
                            summary = "Message types and Extension Configuration are complete.",
                            fromMessageAt = baseTime - 600_000L,
                            toMessageAt = baseTime - 300_001L,
                            createdAt = baseTime - 300_000L,
                        )
                    )
                }

                NightChatMemoryScreen(
                    chat = chat,
                    checkpoints = checkpoints,
                    isRefreshing = false,
                    onBack = {},
                    onRefresh = { refreshed = true },
                )
            }
        }
    }
}
