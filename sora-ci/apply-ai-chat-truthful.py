from pathlib import Path

# Models: messages can carry real persisted document attachments.
models = Path('sora-overlay/app/src/main/java/com/night/sora/model/Models.kt')
text = models.read_text()
old = '''data class AiMessage(
    val id: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}
'''
new = '''data class AiAttachment(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
)

data class AiMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val attachments: List<AiAttachment> = emptyList(),
) {
    enum class Role { USER, ASSISTANT }
}
'''
if text.count(old) != 1:
    raise SystemExit('AiMessage model marker mismatch')
models.write_text(text.replace(old, new, 1))

# Repository: persist attachments and per-conversation text drafts.
repo = Path('sora-overlay/app/src/main/java/com/night/sora/data/CoreRepository.kt')
text = repo.read_text()
text = text.replace(
    'import androidx.compose.runtime.mutableLongStateOf\n',
    'import androidx.compose.runtime.mutableLongStateOf\nimport androidx.compose.runtime.mutableStateMapOf\n',
    1,
)
text = text.replace(
    'import com.night.sora.model.AiConversation\n',
    'import com.night.sora.model.AiAttachment\nimport com.night.sora.model.AiConversation\n',
    1,
)
old = '''    val aiConversations = mutableStateListOf<AiConversation>()
    var activeAiConversationId by mutableLongStateOf(0L)
        private set
'''
new = '''    val aiConversations = mutableStateListOf<AiConversation>()
    private val aiDrafts = mutableStateMapOf<Long, String>()
    var activeAiConversationId by mutableLongStateOf(0L)
        private set
'''
if text.count(old) != 1:
    raise SystemExit('AI repository property marker mismatch')
text = text.replace(old, new, 1)
old = '''    val activeAiMessages: List<AiMessage>
        get() = aiConversations.firstOrNull { it.id == activeAiConversationId }?.messages.orEmpty()
'''
new = '''    val activeAiMessages: List<AiMessage>
        get() = aiConversations.firstOrNull { it.id == activeAiConversationId }?.messages.orEmpty()

    val activeAiDraft: String
        get() = aiDrafts[activeAiConversationId].orEmpty()
'''
if text.count(old) != 1:
    raise SystemExit('active AI getter marker mismatch')
text = text.replace(old, new, 1)
text = text.replace(
    '        loadConversationsOrMigrateMessages()\n        loadLibrary()\n',
    '        loadConversationsOrMigrateMessages()\n        loadAiDrafts()\n        loadLibrary()\n',
    1,
)
old = '''    fun sendAiText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        appendAiMessage(AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, clean))
    }

    fun sendVoicePlaceholder() {
        appendAiMessage(AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, "Voice message"))
    }
'''
new = '''    fun setActiveAiDraft(text: String) {
        val id = activeAiConversationId
        if (id == 0L) return
        if (text.isBlank()) aiDrafts.remove(id) else aiDrafts[id] = text
        persistAiDrafts()
    }

    fun sendAiText(text: String, attachments: List<AiAttachment> = emptyList()) {
        val clean = text.trim()
        if (clean.isEmpty() && attachments.isEmpty()) return
        appendAiMessage(AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, clean, attachments))
        aiDrafts.remove(activeAiConversationId)
        persistAiDrafts()
    }
'''
if text.count(old) != 1:
    raise SystemExit('sendAiText marker mismatch')
text = text.replace(old, new, 1)
old = '''        val firstUserText = (current.messages + message)
            .firstOrNull { it.role == AiMessage.Role.USER && it.text.isNotBlank() }
            ?.text
            ?.take(42)
        aiConversations[index] = current.copy(
            title = firstUserText ?: current.title,
'''
new = '''        val firstUserTitle = (current.messages + message)
            .firstOrNull { it.role == AiMessage.Role.USER }
            ?.let { userMessage ->
                userMessage.text.takeIf(String::isNotBlank)
                    ?: userMessage.attachments.firstOrNull()?.name
            }
            ?.take(42)
        aiConversations[index] = current.copy(
            title = firstUserTitle ?: current.title,
'''
if text.count(old) != 1:
    raise SystemExit('AI conversation title marker mismatch')
text = text.replace(old, new, 1)
old = '''                        messages += AiMessage(
                            id = message.getLong("id"),
                            role = AiMessage.Role.valueOf(message.getString("role")),
                            text = message.getString("text"),
                        )
'''
new = '''                        val attachments = buildList {
                            val attachmentArray = message.optJSONArray("attachments") ?: JSONArray()
                            for (k in 0 until attachmentArray.length()) {
                                val attachment = attachmentArray.optJSONObject(k) ?: continue
                                val uri = attachment.optString("uri")
                                val name = attachment.optString("name")
                                if (uri.isNotBlank() && name.isNotBlank()) {
                                    add(AiAttachment(uri, name, attachment.optNullableString("mimeType")))
                                }
                            }
                        }
                        messages += AiMessage(
                            id = message.getLong("id"),
                            role = AiMessage.Role.valueOf(message.getString("role")),
                            text = message.getString("text"),
                            attachments = attachments,
                        )
'''
if text.count(old) != 1:
    raise SystemExit('AI conversation load marker mismatch')
text = text.replace(old, new, 1)
old = '''            conversation.messages.forEach { message ->
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                })
            }
'''
new = '''            conversation.messages.forEach { message ->
                val attachments = JSONArray()
                message.attachments.forEach { attachment ->
                    attachments.put(JSONObject().apply {
                        put("uri", attachment.uri)
                        put("name", attachment.name)
                        putNullable("mimeType", attachment.mimeType)
                    })
                }
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                    put("attachments", attachments)
                })
            }
'''
if text.count(old) != 1:
    raise SystemExit('AI conversation persist marker mismatch')
text = text.replace(old, new, 1)
marker = '''    private fun loadLibrary() {
'''
addition = '''    private fun loadAiDrafts() {
        val raw = prefs.getString(KEY_AI_DRAFTS, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = key.toLongOrNull() ?: continue
                root.optString(key).takeIf(String::isNotBlank)?.let { aiDrafts[id] = it }
            }
        }
    }

    private fun persistAiDrafts() {
        val root = JSONObject()
        aiDrafts.forEach { (id, draft) -> if (draft.isNotBlank()) root.put(id.toString(), draft) }
        prefs.edit().putString(KEY_AI_DRAFTS, root.toString()).apply()
    }

''' + marker
if text.count(marker) != 1:
    raise SystemExit('AI draft persistence insertion marker mismatch')
text = text.replace(marker, addition, 1)
text = text.replace(
    '        const val KEY_CONVERSATIONS = "ai_conversations_v2"\n',
    '        const val KEY_CONVERSATIONS = "ai_conversations_v2"\n        const val KEY_AI_DRAFTS = "ai_drafts_v1"\n',
    1,
)
repo.write_text(text)

# AI screen: real OpenDocument attachment picker, persistent draft supplied by Core,
# no fake microphone recording, no fake provider status, and Files shows actual history attachments.
ai = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/AiScreen.kt')
text = ai.read_text()
text = text.replace(
    'package com.night.sora.ui.screens\n\n',
    'package com.night.sora.ui.screens\n\nimport android.content.Intent\nimport android.provider.OpenableColumns\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n',
    1,
)
text = text.replace(
    'import androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalContext\n',
    1,
)
text = text.replace(
    'import com.night.sora.model.AiConversation\n',
    'import com.night.sora.model.AiAttachment\nimport com.night.sora.model.AiConversation\n',
    1,
)
old = '''    messages: List<AiMessage>,
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
'''
new = '''    messages: List<AiMessage>,
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
'''
if text.count(old) != 1:
    raise SystemExit('AiScreen signature/state marker mismatch')
text = text.replace(old, new, 1)
text = text.replace(
    'if (target == AiDrawerTarget.CHAT) Text("DeepSeek · automatic tools", color = SoraMuted, fontSize = 8.sp)',
    'if (target == AiDrawerTarget.CHAT) Text("Local history · AI provider not connected", color = SoraMuted, fontSize = 8.sp)',
    1,
)
old = '''                if (target == AiDrawerTarget.CHAT) {
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
'''
new = '''                if (target == AiDrawerTarget.CHAT) {
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
'''
if text.count(old) != 1:
    raise SystemExit('AI bottom bar marker mismatch')
text = text.replace(old, new, 1)
text = text.replace(
    'AiDrawerTarget.FILES -> AiLibraryEmpty(Modifier.padding(padding), Icons.Rounded.Folder, "Files", "Files you attach or keep for Sora will appear here.")',
    'AiDrawerTarget.FILES -> AiFilesSurface(Modifier.padding(padding), files)',
    1,
)
text = text.replace(
    'SuggestionChip("Explain my last passage") { onSuggestion("Explain what I last read in the Bible.") }',
    'SuggestionChip("Explain a Bible passage") { onSuggestion("Explain this Bible passage for me.") }',
    1,
)
old = '''                if (message.role == AiMessage.Role.USER) {
                    Surface(color = SoraSurfaceRaised, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)) {
                        Text(message.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.widthIn(max = 310.dp).padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                } else {
                    Text(message.text, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFFDFDCD4), modifier = Modifier.widthIn(max = 720.dp).padding(vertical = 4.dp))
                }
'''
new = '''                if (message.role == AiMessage.Role.USER) {
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
'''
if text.count(old) != 1:
    raise SystemExit('AI message rendering marker mismatch')
text = text.replace(old, new, 1)
old = '''private fun ChatComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = SoraBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Surface(
'''
new = '''private fun ChatComposer(
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
'''
if text.count(old) != 1:
    raise SystemExit('ChatComposer signature marker mismatch')
text = text.replace(old, new, 1)
old = '''                    if (draft.isBlank()) IconButton(onClick = onMic, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Mic, "Voice", modifier = Modifier.size(20.dp)) }
                    else FilledIconButton(onClick = onSend, modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black)) { Icon(Icons.Rounded.ArrowUpward, "Send", modifier = Modifier.size(19.dp)) }
'''
new = '''                    FilledIconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() || attachments.isNotEmpty(),
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black),
                    ) { Icon(Icons.Rounded.ArrowUpward, "Save message", modifier = Modifier.size(19.dp)) }
'''
if text.count(old) != 1:
    raise SystemExit('AI composer mic marker mismatch')
text = text.replace(old, new, 1)
text = text.replace(
    'Text("Sora decides what capability is needed", color = SoraMuted, fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))',
    'Text("Messages are stored locally until an AI provider is connected", color = SoraMuted, fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))',
    1,
)
# Remove the fake voice composer entirely.
voice_start = text.find('@Composable\nprivate fun VoiceComposer(')
if voice_start == -1:
    raise SystemExit('VoiceComposer start marker missing')
voice_end = text.find('@Composable\nprivate fun AiLibraryEmpty', voice_start)
if voice_end == -1:
    raise SystemExit('VoiceComposer end marker missing')
text = text[:voice_start] + '''@Composable
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

''' + text[voice_end:]
ai.write_text(text)

# Wire persistent draft + attachments in app shell, and make quick sheet truthful/non-dead.
app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
text = app.read_text()
old = '''                conversations = repository.aiConversations, activeConversationId = repository.activeAiConversationId,
                messages = repository.activeAiMessages, onSelectConversation = repository::selectAiConversation,
                onNewConversation = repository::newAiConversation, onSendText = repository::sendAiText,
                onSendVoice = repository::sendVoicePlaceholder, onBack = ::pop,
'''
new = '''                conversations = repository.aiConversations, activeConversationId = repository.activeAiConversationId,
                messages = repository.activeAiMessages, draft = repository.activeAiDraft,
                onDraft = repository::setActiveAiDraft, onSelectConversation = repository::selectAiConversation,
                onNewConversation = repository::newAiConversation,
                onSendText = { text, attachments -> repository.sendAiText(text, attachments) },
                onBack = ::pop,
'''
if text.count(old) != 1:
    raise SystemExit('SoraApp AiScreen wiring marker mismatch')
text = text.replace(old, new, 1)
text = text.replace('onSend = repository::sendAiText,', 'onSend = { repository.sendAiText(it) },', 1)
text = text.replace('Text("DeepSeek · context on", color = SoraMuted, fontSize = 9.sp)', 'Text("Local chat · AI provider not connected", color = SoraMuted, fontSize = 9.sp)', 1)
text = text.replace('Text("Looking at Sora · library available", color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 7.dp))', 'Text("Messages stay local until a provider is connected", color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 7.dp))', 1)
text = text.replace('QuickSuggestion("Explain my last passage") { draft = "Explain what I last read in the Bible." }', 'QuickSuggestion("Explain a Bible passage") { draft = "Explain this Bible passage for me." }', 1)
text = text.replace('                    IconButton(onClick = {}, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Add, "Attach") }\n', '', 1)
text = text.replace('                    IconButton(onClick = {}, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Mic, "Voice") }\n', '', 1)
app.write_text(text)
