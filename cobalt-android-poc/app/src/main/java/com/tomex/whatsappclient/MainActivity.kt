package com.tomex.whatsappclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppDark = Color(0xFF075E54)
private val BubbleMine = Color(0xFFD9FDD3)
private val BubbleOther = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    private lateinit var controller: CobaltController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = CobaltController(this)
        controller.start()

        setContent {
            val scheme = lightColorScheme(
                primary = WhatsAppDark,
                secondary = WhatsAppGreen,
                surface = Color(0xFFF7F8FA),
                background = Color(0xFFF7F8FA)
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WhatsAppClientApp(controller)
                }
            }
        }
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

@Composable
private fun WhatsAppClientApp(controller: CobaltController) {
    val state = controller.state

    DisposableEffect(Unit) {
        onDispose { }
    }

    when {
        state.selectedJid != null && state.phase == ClientPhase.CONNECTED -> {
            ConversationScreen(controller, state)
        }
        state.phase == ClientPhase.CONNECTED -> {
            ChatsScreen(controller, state)
        }
        else -> {
            LinkScreen(controller, state)
        }
    }
}

@Composable
private fun LinkScreen(controller: CobaltController, state: ClientUiState) {
    var phone by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state.phase == ClientPhase.LOADING || state.phase == ClientPhase.CONNECTING) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(18.dp))
                    Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("W", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "WhatsApp Client",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Link this app as a companion device. The session stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(28.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone number") },
                        placeholder = { Text("234…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { controller.pair(phone) }
                        )
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { controller.pair(phone) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.phase != ClientPhase.PAIRING
                    ) {
                        Text(if (state.phase == ClientPhase.PAIRING) "Connecting…" else "Link with phone number")
                    }

                    if (state.hasSavedSession || state.phase == ClientPhase.DISCONNECTED) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = controller::reconnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reconnect saved session")
                        }
                    }

                    state.pairingCode?.takeIf { it.isNotBlank() }?.let { code ->
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "PAIRING CODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(code))
                                }
                        ) {
                            Text(
                                code.chunked(4).joinToString(" "),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap the code to copy it.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Text(
                        state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.phase == ClientPhase.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(controller: CobaltController, state: ClientUiState) {
    var search by rememberSaveable { mutableStateOf("") }
    var showNewChat by remember { mutableStateOf(false) }

    val visibleChats = remember(state.chats, search) {
        if (search.isBlank()) state.chats
        else state.chats.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.preview.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text("WhatsApp", fontWeight = FontWeight.Bold)
                },
                actions = {
                    TextButton(onClick = { showNewChat = true }) {
                        Text("+", fontSize = 26.sp)
                    }
                    TextButton(onClick = controller::disconnect) {
                        Text("Disconnect")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChat = true },
                containerColor = WhatsAppGreen
            ) {
                Text("+", fontSize = 28.sp, color = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                placeholder = { Text("Search chats") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            if (visibleChats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state.chats.isEmpty()) "No chats synced yet" else "No matching chats",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.chats.isEmpty()) "Keep the app connected while WhatsApp history syncs." else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleChats, key = { it.jid }) { chat ->
                        ChatRow(
                            chat = chat,
                            onClick = { controller.openChat(chat.jid, chat.title) }
                        )
                    }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatDialog(
            onDismiss = { showNewChat = false },
            onOpen = {
                showNewChat = false
                controller.openNumber(it)
            }
        )
    }
}

@Composable
private fun ChatRow(chat: ChatUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.title.take(1).uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatListTime(chat.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unread > 0) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.preview,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (chat.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WhatsAppGreen)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (chat.unread > 99) "99+" else chat.unread.toString(),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(controller: CobaltController, state: ClientUiState) {
    var draft by rememberSaveable(state.selectedJid) { mutableStateOf("") }
    val listState = rememberLazyListState()

    BackHandler { controller.closeChat() }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.selectedTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = controller::closeChat) {
                        Text("‹", fontSize = 32.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(Color(0xFFF3EEE7))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }

            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        maxLines = 5,
                        shape = RoundedCornerShape(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                draft = ""
                                controller.sendText(text)
                            }
                        },
                        modifier = Modifier.size(50.dp),
                        containerColor = WhatsAppGreen
                    ) {
                        Text("➤", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.fromMe) BubbleMine else BubbleOther,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                if (!message.fromMe && message.sender.isNotBlank()) {
                    Text(
                        message.sender.substringBefore("@"),
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    var number by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter(Char::isDigit) },
                label = { Text("International phone number") },
                placeholder = { Text("234…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onOpen(number) },
                enabled = number.length in 8..15
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatListTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return runCatching {
        val instant = Instant.ofEpochMilli(timestamp)
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault("")
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return formatListTime(timestamp)
}
