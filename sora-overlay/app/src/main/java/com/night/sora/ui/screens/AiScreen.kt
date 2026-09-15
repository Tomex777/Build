@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.model.AiConversation
import com.night.sora.model.AiMessage
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface
import kotlinx.coroutines.launch

@Composable
fun AiScreen(
    conversations: List<AiConversation>,
    activeConversationId: Long,
    messages: List<AiMessage>,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onSendText: (String) -> Unit,
    onSendVoice: () -> Unit,
    onBack: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showGeneratedImages by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 330.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sora", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        onNewConversation()
                        showGeneratedImages = false
                        scope.launch { drawerState.close() }
                    }) { Icon(Icons.Rounded.Edit, "New chat") }
                }

                NavigationDrawerItem(
                    label = { Text("Generated images") },
                    selected = showGeneratedImages,
                    icon = { Icon(Icons.Rounded.Image, null) },
                    onClick = {
                        showGeneratedImages = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )

                Text(
                    "Chats",
                    color = SoraMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations.sortedByDescending { it.updatedAt }, key = { it.id }) { conversation ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    conversation.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            selected = !showGeneratedImages && conversation.id == activeConversationId,
                            onClick = {
                                onSelectConversation(conversation.id)
                                showGeneratedImages = false
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (showGeneratedImages) "Generated images" else "Sora AI")
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, "Open history")
                        }
                    },
                    actions = {
                        if (!showGeneratedImages) {
                            IconButton(onClick = onNewConversation) { Icon(Icons.Rounded.Edit, "New chat") }
                        }
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close Sora AI") }
                    },
                )
            },
            bottomBar = {
                if (!showGeneratedImages) {
                    if (recording) {
                        RecordingComposer(
                            stopped = stopped,
                            onCancel = { recording = false; stopped = false },
                            onStop = { stopped = true },
                            onSend = {
                                recording = false
                                stopped = false
                                onSendVoice()
                            },
                        )
                    } else {
                        MessageComposer(
                            draft = draft,
                            onDraft = { draft = it },
                            onMic = { recording = true },
                            onSend = {
                                val clean = draft.trim()
                                if (clean.isNotEmpty()) {
                                    onSendText(clean)
                                    draft = ""
                                }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            if (showGeneratedImages) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(40.dp), tint = SoraMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("Generated images will appear here", fontWeight = FontWeight.SemiBold)
                        Text("They stay with Sora AI, not in Library.", color = SoraMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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
                                    .then(
                                        if (message.role == AiMessage.Role.USER) {
                                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                                                .padding(horizontal = 14.dp, vertical = 11.dp)
                                        } else Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                                    ),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier.weight(1f).background(SoraSurface, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
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
            if (draft.isBlank()) {
                IconButton(onClick = onMic) { Icon(Icons.Rounded.Mic, "Record") }
            } else {
                FilledIconButton(onClick = onSend, modifier = Modifier.padding(start = 6.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, "Send")
                }
            }
        }
    }
}

@Composable
private fun RecordingComposer(
    stopped: Boolean,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
) {
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
                Text(if (stopped) "Ready to send" else "Recording", fontSize = 14.sp)
            }
            if (!stopped) FilledTonalIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop") }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = onSend) { Icon(Icons.Rounded.ArrowUpward, "Send") }
        }
    }
}
