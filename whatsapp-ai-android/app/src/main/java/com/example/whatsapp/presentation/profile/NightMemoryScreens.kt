package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.example.whatsapp.data.night.NightSummaryCheckpointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MemoryBg = Color(0xFF0B0F11)
private val MemoryText = Color(0xFFE7EAEC)
private val MemoryMuted = Color(0xFF9CA5A9)

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
            .statusBarsPadding().navigationBarsPadding(),
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
    checkpoints: List<NightSummaryCheckpointEntity>,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoryBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        MemoryHeader(chat?.title ?: "Memory & summary", onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
        ) {
            item {
                Text(
                    "Latest summary",
                    color = MemoryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                chat?.summaryUpdatedAt?.let { updatedAt ->
                    Text(
                        text = "Updated " + formatMemoryTime(updatedAt),
                        color = MemoryMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                Text(
                    chat?.latestSummary?.ifBlank {
                        "No summary checkpoint has been created yet."
                    } ?: "Chat not found.",
                    color = MemoryMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 9.dp),
                )

                if (chat?.summaryDirty == true) {
                    Text(
                        "There are newer unsummarized messages.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else if (!chat?.latestSummary.isNullOrBlank()) {
                    Text(
                        "Up to date",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Button(
                    onClick = onRefresh,
                    enabled = chat != null && !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color(0xFF07110B),
                    ),
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text(if (isRefreshing) "Refreshing…" else "Refresh summary")
                }
            }

            if (checkpoints.isNotEmpty()) {
                item {
                    Text(
                        text = "Summary history",
                        color = MemoryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
                    )
                    Text(
                        text = checkpoints.size.toString() + " checkpoint" +
                            if (checkpoints.size == 1) "" else "s",
                        color = MemoryMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                items(
                    items = checkpoints,
                    key = { it.id },
                ) { checkpoint ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = formatMemoryTime(checkpoint.createdAt),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = checkpoint.summary,
                            color = MemoryMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMemoryTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        .format(Date(timestamp))

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
