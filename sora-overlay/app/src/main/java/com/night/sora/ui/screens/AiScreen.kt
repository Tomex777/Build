@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.night.sora.ui.theme.*
import kotlinx.coroutines.launch

private enum class AiDrawerTarget { CHAT, GENERATED_IMAGES, FILES }

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
    var target by remember { mutableStateOf(AiDrawerTarget.CHAT) }
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(containerColor = Color(0xFF161614), modifier = Modifier.widthIn(max = 340.dp)) {
                Text("Sora AI", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 14.dp))
                NavigationDrawerItem(
                    label = { Text("New chat") }, selected = false,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { onNewConversation(); target = AiDrawerTarget.CHAT; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Text("SORA", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 7.dp))
                NavigationDrawerItem(
                    label = { Text("Generated images") },
                    selected = target == AiDrawerTarget.GENERATED_IMAGES,
                    icon = { Icon(Icons.Rounded.Image, null) },
                    onClick = { target = AiDrawerTarget.GENERATED_IMAGES; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Files") },
                    selected = target == AiDrawerTarget.FILES,
                    icon = { Icon(Icons.Rounded.Folder, null) },
                    onClick = { target = AiDrawerTarget.FILES; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Text("RECENT", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 7.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations.sortedByDescending { it.updatedAt }, key = { it.id }) { conversation ->
                        NavigationDrawerItem(
                            label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = target == AiDrawerTarget.CHAT && conversation.id == activeConversationId,
                            onClick = { onSelectConversation(conversation.id); target = AiDrawerTarget.CHAT; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = SoraBg,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Rounded.Menu, "Open menu") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (target) { AiDrawerTarget.CHAT -> "Sora AI"; AiDrawerTarget.GENERATED_IMAGES -> "Generated images"; AiDrawerTarget.FILES -> "Files" },
                            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        if (target == AiDrawerTarget.CHAT) Text("DeepSeek · automatic tools", color = SoraMuted, fontSize = 8.sp)
                    }
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close") }
                }
            },
            bottomBar = {
                if (target == AiDrawerTarget.CHAT) {
                    if (recording) VoiceComposer(
                        stopped = stopped,
                        onCancel = { recording = false; stopped = false },
                        onStop = { stopped = true },
                        onSend = { recording = false; stopped = false; onSendVoice() },
                    ) else ChatComposer(
                        draft = draft,
                        onDraft = { draft = it },
                        onAttach = { },
                        onMic = { recording = true },
                        onSend = {
                            val clean = draft.trim()
                            if (clean.isNotEmpty()) { onSendText(clean); draft = "" }
                        },
                    )
                }
            },
        ) { padding ->
            when (target) {
                AiDrawerTarget.CHAT -> ChatThread(
                    modifier = Modifier.padding(padding),
                    messages = messages,
                    onSuggestion = { draft = it },
                )
                AiDrawerTarget.GENERATED_IMAGES -> AiLibraryEmpty(Modifier.padding(padding), Icons.Rounded.Image, "Generated images", "Images you create with Sora stay here, beside your AI history.")
                AiDrawerTarget.FILES -> AiLibraryEmpty(Modifier.padding(padding), Icons.Rounded.Folder, "Files", "Files you attach or keep for Sora will appear here.")
            }
        }
    }
}

@Composable
private fun ChatThread(modifier: Modifier, messages: List<AiMessage>, onSuggestion: (String) -> Unit) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (messages.size <= 1) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(31.dp)) {
                        Box(Modifier.align(Alignment.TopCenter).width(24.dp).height(11.dp).background(SoraText, RoundedCornerShape(12.dp, 12.dp, 3.dp, 3.dp)))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).width(24.dp).height(2.dp).background(SoraAccent, RoundedCornerShape(99.dp)))
                    }
                    Text("What do you need?", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp))
                    Text("Talk normally. Sora decides what capability is needed.", color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        SuggestionChip("Find a manga") { onSuggestion("Find this manga for me.") }
                        SuggestionChip("Create something") { onSuggestion("Create an image for me.") }
                        SuggestionChip("Search something") { onSuggestion("Search this for me.") }
                        SuggestionChip("Explain my last passage") { onSuggestion("Explain what I last read in the Bible.") }
                    }
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == AiMessage.Role.USER) Arrangement.End else Arrangement.Start) {
                if (message.role == AiMessage.Role.USER) {
                    Surface(color = SoraSurfaceRaised, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)) {
                        Text(message.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.widthIn(max = 310.dp).padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                } else {
                    Text(message.text, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFFDFDCD4), modifier = Modifier.widthIn(max = 720.dp).padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        color = SoraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(text, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) }
}

@Composable
private fun ChatComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = SoraBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Surface(
                color = Color(0xFF181816),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Add, "Attach", modifier = Modifier.size(20.dp)) }
                    Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraft,
                            minLines = 1,
                            maxLines = 5,
                            textStyle = TextStyle(color = SoraText, fontSize = 14.sp, lineHeight = 20.sp),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (draft.isEmpty()) Text("Message Sora…", color = SoraMuted, fontSize = 14.sp); inner() },
                        )
                    }
                    if (draft.isBlank()) IconButton(onClick = onMic, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Mic, "Voice", modifier = Modifier.size(20.dp)) }
                    else FilledIconButton(onClick = onSend, modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black)) { Icon(Icons.Rounded.ArrowUpward, "Send", modifier = Modifier.size(19.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(SoraAccent, CircleShape))
                Text("Sora decides what capability is needed", color = SoraMuted, fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun VoiceComposer(stopped: Boolean, onCancel: () -> Unit, onStop: () -> Unit, onSend: () -> Unit) {
    Surface(color = SoraBg) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = SoraAccent)
                Text(if (stopped) "Ready to send" else "Recording", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            if (!stopped) FilledTonalIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop") }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = onSend, colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black)) { Icon(Icons.Rounded.ArrowUpward, "Send") }
        }
    }
}

@Composable
private fun AiLibraryEmpty(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(34.dp)) {
            Icon(icon, null, tint = SoraMuted, modifier = Modifier.size(42.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
            Text(body, color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}
