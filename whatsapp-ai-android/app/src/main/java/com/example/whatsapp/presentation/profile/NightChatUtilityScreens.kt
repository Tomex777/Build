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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightMessageEntity
import java.text.DateFormat
import java.util.Date

private val UtilityBg = Color(0xFF0B0F11)
private val UtilityText = Color(0xFFE7EAEC)
private val UtilityMuted = Color(0xFF9CA5A9)
private val UtilityAccent = Color(0xFF21C063)

@Composable
fun NightChatSearchScreen(
    title: String,
    messages: List<NightMessageEntity>,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(messages, query) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else messages
            .filter { it.text.contains(q, ignoreCase = true) }
            .asReversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UtilityBg)
            .statusBarsPadding(),
    ) {
        UtilityHeader("Search " + title, onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search messages") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = UtilityText,
                unfocusedTextColor = UtilityText,
                cursorColor = UtilityAccent,
            ),
        )

        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                "No matching messages.",
                color = UtilityMuted,
                modifier = Modifier.padding(22.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            ) {
                items(results, key = { it.id }) { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            if (message.role == "assistant") "Night" else "You",
                            color = UtilityAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            message.text.ifBlank { "[" + message.type + "]" },
                            color = UtilityText,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(message.createdAt)),
                            color = UtilityMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NightChatFilesScreen(
    title: String,
    messages: List<NightMessageEntity>,
    onBack: () -> Unit,
) {
    val media = remember(messages) {
        messages.filter { it.type in setOf("image", "file", "voice") }
            .asReversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UtilityBg)
            .statusBarsPadding(),
    ) {
        UtilityHeader("Files in " + title, onBack)

        if (media.isEmpty()) {
            Text(
                "No files in this chat yet.",
                color = UtilityMuted,
                modifier = Modifier.padding(22.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                items(media, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (message.type) {
                                "image" -> Icons.Default.Image
                                "voice" -> Icons.Default.Mic
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = UtilityMuted,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                        ) {
                            Text(
                                message.text.ifBlank {
                                    when (message.type) {
                                        "image" -> "Image"
                                        "voice" -> "Voice note"
                                        else -> "File"
                                    }
                                },
                                color = UtilityText,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT,
                                ).format(Date(message.createdAt)),
                                color = UtilityMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityHeader(
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
            Icon(Icons.Default.ArrowBack, "Back", tint = UtilityText)
        }
        Text(
            title,
            color = UtilityText,
            fontSize = 21.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
