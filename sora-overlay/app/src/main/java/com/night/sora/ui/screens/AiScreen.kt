@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.model.AiMessage
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

@Composable
fun AiScreen(initialMessages: List<AiMessage>, onBack: () -> Unit) {
    var messages by remember { mutableStateOf(initialMessages) }
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sora AI") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (recording) {
                RecordingComposer(
                    stopped = stopped,
                    onCancel = { recording = false; stopped = false },
                    onStop = { stopped = true },
                    onSend = {
                        recording = false
                        stopped = false
                        messages = messages + AiMessage(System.nanoTime(), AiMessage.Role.USER, "Voice message")
                    },
                )
            } else {
                MessageComposer(
                    draft = draft,
                    onDraft = { draft = it },
                    onMic = { recording = true },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            messages = messages + AiMessage(System.nanoTime(), AiMessage.Role.USER, text)
                            draft = ""
                        }
                    },
                )
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == AiMessage.Role.USER) Arrangement.End else Arrangement.Start,
                ) {
                    Text(
                        message.text,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .background(
                                if (message.role == AiMessage.Role.USER) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                RoundedCornerShape(18.dp),
                            )
                            .padding(if (message.role == AiMessage.Role.USER) 13.dp else 2.dp),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(draft: String, onDraft: (String) -> Unit, onMic: () -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) { Icon(Icons.Rounded.Add, "Attach") }
            Box(
                Modifier.weight(1f).background(SoraSurface, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Message Sora", color = SoraMuted, fontSize = 15.sp)
                        inner()
                    },
                )
            }
            if (draft.isBlank()) IconButton(onClick = onMic) { Icon(Icons.Rounded.Mic, "Record") }
            else IconButton(onClick = onSend) { Icon(Icons.Rounded.ArrowUpward, "Send") }
        }
    }
}

@Composable
private fun RecordingComposer(stopped: Boolean, onCancel: () -> Unit, onStop: () -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (stopped) "Ready to send" else "Recording  0:08", fontSize = 14.sp)
            }
            if (!stopped) {
                FilledTonalIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop") }
            }
            FilledIconButton(onClick = onSend) { Icon(Icons.Rounded.ArrowUpward, "Send") }
        }
    }
}
