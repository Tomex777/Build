@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.night.sora.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.model.AiAttachment
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
    draft: String,
    onDraft: (String) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onSendText: (String, List<AiAttachment>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(AiDrawerTarget.CHAT) }
    var pendingAttachments by remember(activeConversationId) { mutableStateOf<List<AiAttachment>>(emptyList()) }
    val files = remember(conversations) {
        conversations.flatMap { it.messages }.flatMap { it.attachments }.distinctBy { it.uri }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mimeType = context.contentResolver.getType(uri)
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: "Attachment" }
            val attachment = AiAttachment(uri.toString(), name, mimeType)
            if (pendingAttachments.none { it.uri == attachment.uri }) pendingAttachments = pendingAttachments + attachment
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF161614), modifier = Modifier.widthIn(max = 340.dp)) {
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
                        if (target == AiDrawerTarget.CHAT) Text("Local history · AI provider not connected", color = SoraMuted, fontSize = 8.sp)
                    }
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close") }
                }
            },
            bottomBar = {
                if (target == AiDrawerTarget.CHAT) {
                    ChatComposer(
                        draft = draft,
                        attachments = pendingAttachments,
                        onDraft = onDraft,
                        onAttach = { attachmentLauncher.launch(arrayOf("*/*")) },
                        onRemoveAttachment = { remove -> pendingAttachments = pendingAttachments.filterNot { it.uri == remove.uri } },
                        onSend = {
                            if (draft.isNotBlank() || pendingAttachments.isNotEmpty()) {
                                onSendText(draft, pendingAttachments)
                                pendingAttachments = emptyList()
                            }
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
                AiDrawerTarget.FILES -> AiFilesSurface(Modifier.padding(padding), files)
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
                        SuggestionChip("Explain a Bible passage") { onSuggestion("Explain this Bible passage for me.") }
                    }
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == AiMessage.Role.USER) Arrangement.End else Arrangement.Start) {
                if (message.role == AiMessage.Role.USER) {
                    Surface(color = SoraSurfaceRaised, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)) {
                        Column(Modifier.widthIn(max = 310.dp).padding(horizontal = 14.dp, vertical = 11.dp)) {
                            if (message.text.isNotBlank()) Text(message.text, fontSize = 14.sp, lineHeight = 20.sp)
                            if (message.attachments.isNotEmpty()) {
                                Column(Modifier.padding(top = if (message.text.isNotBlank()) 8.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    message.attachments.forEach { attachment -> MessageAttachmentRow(attachment) }
                                }
                            }
                        }
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
    attachments: List<AiAttachment>,
    onDraft: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (AiAttachment) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = SoraBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (attachments.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    attachments.forEach { attachment ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = { IconButton(onClick = { onRemoveAttachment(attachment) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, "Remove ${attachment.name}", modifier = Modifier.size(15.dp)) } },
                        )
                    }
                }
            }
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
                    FilledIconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() || attachments.isNotEmpty(),
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black),
                    ) { Icon(Icons.Rounded.ArrowUpward, "Save message", modifier = Modifier.size(19.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(SoraAccent, CircleShape))
                Text("Messages are stored locally until an AI provider is connected", color = SoraMuted, fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun MessageAttachmentRow(attachment: AiAttachment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AttachFile, null, tint = SoraMuted, modifier = Modifier.size(17.dp))
        Column(Modifier.padding(start = 7.dp)) {
            Text(attachment.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            attachment.mimeType?.takeIf(String::isNotBlank)?.let { Text(it, color = SoraMuted, fontSize = 8.sp) }
        }
    }
}

@Composable
private fun AiFilesSurface(modifier: Modifier, files: List<AiAttachment>) {
    if (files.isEmpty()) {
        AiLibraryEmpty(modifier, Icons.Rounded.Folder, "Files", "Documents you attach to a chat will appear here.")
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files, key = { it.uri }) { attachment ->
            Surface(color = SoraSurface, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.InsertDriveFile, null, tint = SoraMuted)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(attachment.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(attachment.mimeType ?: "Document", color = SoraMuted, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
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
