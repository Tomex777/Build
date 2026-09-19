package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightChatEntity

private val MemoryBg = Color(0xFF0B0F11)
private val MemoryText = Color(0xFFE7EAEC)
private val MemoryMuted = Color(0xFF9CA5A9)
private val MemoryAccent = Color(0xFF21C063)

@Composable
fun NightMemoryScreen(
    chats: List<NightChatEntity>,
    onBack: () -> Unit,
    onChatClick: (NightChatEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoryBg)
            .statusBarsPadding(),
    ) {
        MemoryHeader("Memory", onBack)

        if (chats.isEmpty()) {
            Text(
                "No chat summaries yet.",
                color = MemoryMuted,
                modifier = Modifier.padding(22.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            chat.title,
                            color = MemoryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            chat.latestSummary.ifBlank {
                                if (chat.summaryDirty) "Waiting for the next summary checkpoint…" else "No summary yet."
                            },
                            color = MemoryMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NightChatMemoryScreen(
    chat: NightChatEntity?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoryBg)
            .statusBarsPadding(),
    ) {
        MemoryHeader(chat?.title ?: "Memory & summary", onBack)

        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                "Latest summary",
                color = MemoryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                chat?.latestSummary?.ifBlank { "No summary checkpoint has been created yet." }
                    ?: "Chat not found.",
                color = MemoryMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 9.dp),
            )

            if (chat?.summaryDirty == true) {
                Text(
                    "There are newer unsummarized messages.",
                    color = MemoryAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = onRefresh,
                enabled = chat != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MemoryAccent,
                    contentColor = Color(0xFF07110B),
                ),
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Text("Refresh summary")
            }
        }
    }
}

@Composable
private fun MemoryHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Back", tint = MemoryText)
        }
        Text(
            title,
            color = MemoryText,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
