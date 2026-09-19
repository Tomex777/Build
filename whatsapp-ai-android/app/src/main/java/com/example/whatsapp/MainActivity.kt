// Night real-core validation 2
package com.example.whatsapp

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.whatsapp.data.NightFileLibrary
import com.example.whatsapp.data.night.NightAiGateway
import com.example.whatsapp.data.night.NightAppearanceController
import com.example.whatsapp.data.night.NightAppearanceEntity
import com.example.whatsapp.data.night.NightCapabilityRouteEntity
import com.example.whatsapp.data.night.NightLibraryItemEntity
import com.example.whatsapp.data.night.NightMessageEntity
import com.example.whatsapp.data.night.NightProviderManager
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.data.night.NightScheduleManager
import com.example.whatsapp.data.night.NightVoiceRecorder
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.NightChatAppearance
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.files.NightFilesTab
import com.example.whatsapp.presentation.profile.NightAiSelectorScreen
import com.example.whatsapp.presentation.profile.NightAppearanceScreen
import com.example.whatsapp.presentation.profile.NightCapabilityRoutesScreen
import com.example.whatsapp.presentation.profile.NightChatMemoryScreen
import com.example.whatsapp.presentation.profile.NightMemoryScreen
import com.example.whatsapp.presentation.profile.NightProfileScreen
import com.example.whatsapp.presentation.profile.NightProvidersScreen
import com.example.whatsapp.presentation.profile.NightScheduleDialog
import com.example.whatsapp.presentation.profile.NightScheduledTasksScreen
import com.example.whatsapp.presentation.profile.NightYouTab
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.shell.ModernSettingsScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightApp()
            }
        }
    }
}

@Composable
private fun NightApp() {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context) }
    val providerManager = remember { NightProviderManager.get(context) }
    val aiGateway = remember { NightAiGateway.get(context) }
    val appearanceController = remember { NightAppearanceController(repository) }
    val voiceRecorder = remember { NightVoiceRecorder(context.applicationContext) }
    val scheduleManager = remember { NightScheduleManager.get(context) }
    val scope = rememberCoroutineScope()

    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.Chats.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var screen by rememberSaveable { mutableStateOf("tabs") }
    var activeChatId by rememberSaveable { mutableStateOf("night-core") }
    var messageText by rememberSaveable { mutableStateOf("") }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var scheduleOpen by remember { mutableStateOf(false) }

    val chats by repository.observeChats().collectAsState(initial = emptyList())
    val profiles by repository.observeProviderProfiles().collectAsState(initial = emptyList())
    val providerModels by repository.observeAllProviderModels().collectAsState(initial = emptyList())
    val profile by repository.observeProfile().collectAsState(initial = null)
    val appearanceEntity by repository.observeAppearance().collectAsState(initial = null)
    val scheduledTasks by repository.observeScheduledTasks().collectAsState(initial = emptyList())
    val capabilityRoutes by repository.observeCapabilityRoutes().collectAsState(initial = emptyList())

    val messageFlow = remember(activeChatId) { repository.observeMessages(activeChatId) }
    val messageEntities by messageFlow.collectAsState(initial = emptyList())

    val displayName = profile?.displayName ?: "Dawson"
    val appearance = (appearanceEntity ?: NightAppearanceEntity()).toChatAppearance()
    val activeChat = chats.firstOrNull { it.id == activeChatId }
    val activeModel = providerModels.firstOrNull { it.id == activeChat?.selectedModel }
    val activeProfile = profiles.firstOrNull { it.id == activeChat?.selectedProviderProfileId }

    val visualMessages = remember(messageEntities) {
        messageEntities.map { it.toVisualMessage() }
    }

    val chatRows = remember(chats) {
        chats.map { chat ->
            ChatListModel(
                name = chat.title,
                phoneNumber = chat.id,
                userId = chat.id,
                time = formatChatListTime(chat.updatedAt),
                message = chat.lastMessagePreview.ifBlank {
                    if (chat.latestSummary.isNotBlank()) "Summary ready" else "New chat"
                },
            )
        }
    }

    suspend fun checkpoint(chatId: String) {
        val pending = repository.unsummarizedMessages(chatId)
        if (pending.isEmpty()) return

        val summary = aiGateway.summarize(chatId, displayName)
            .getOrElse {
                pending
                    .filter { it.text.isNotBlank() }
                    .takeLast(12)
                    .joinToString(" • ") { message ->
                        (if (message.role == "assistant") "Night" else displayName) +
                            ": " + message.text.take(180)
                    }
                    .take(2400)
            }

        if (summary.isBlank()) return
        repository.commitSummary(
            chatId = chatId,
            summary = summary,
            fromMessageAt = pending.first().createdAt,
            toMessageAt = pending.last().createdAt,
        )
    }

    LaunchedEffect(Unit) {
        repository.ensureProfile()
        repository.ensureAppearance()
        val root = repository.ensureChat("night-core", "Night")
        if (repository.getMessages(root.id).isEmpty()) {
            repository.appendText(
                chatId = root.id,
                role = "assistant",
                text = "Night is ready. Choose an AI when you want a live model, or keep using Night Core for local actions.",
            )
        }
    }

    val lastMessageAt = messageEntities.lastOrNull()?.createdAt ?: 0L

    LaunchedEffect(screen, activeChatId, lastMessageAt) {
        if (screen != "chat" || lastMessageAt == 0L) return@LaunchedEffect
        val snapshot = lastMessageAt
        delay(2 * 60 * 1000L)
        val latest = repository.getMessages(activeChatId).lastOrNull()?.createdAt ?: 0L
        if (screen == "chat" && latest == snapshot) {
            checkpoint(activeChatId)
        }
    }

    LaunchedEffect(screen, activeChatId) {
        if (screen != "chat") return@LaunchedEffect
        while (true) {
            delay(5 * 60 * 1000L)
            if (repository.unsummarizedMessages(activeChatId).isNotEmpty()) {
                checkpoint(activeChatId)
            }
        }
    }

    suspend fun persistVoiceRecording() {
        val recorded = voiceRecorder.stop()
        isRecording = false
        if (recorded == null) {
            Toast.makeText(context, "Voice recording was too short.", Toast.LENGTH_SHORT).show()
            return
        }

        val saved = withContext(Dispatchers.IO) {
            NightFileLibrary.registerLocalFile(
                context = context,
                source = recorded.file,
                name = "Voice " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date()) + ".wav",
                mimeType = "audio/wav",
            )
        } ?: return

        repository.addLibraryItem(
            NightLibraryItemEntity(
                id = saved.id,
                name = saved.name,
                mimeType = saved.mimeType,
                sizeBytes = saved.sizeBytes,
                localPath = saved.localPath,
                createdAt = saved.createdAt,
                sourceChatId = activeChatId,
            )
        )

        repository.appendMessage(
            NightMessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                chatId = activeChatId,
                role = "user",
                type = "voice",
                text = saved.name,
                createdAt = System.currentTimeMillis(),
                libraryFileId = saved.id,
                payloadJson = JSONObject()
                    .put("localPath", saved.localPath)
                    .put("duration", formatDuration(recorded.durationMs))
                    .put("durationMs", recorded.durationMs)
                    .toString(),
            )
        )
    }

    fun startVoiceRecording() {
        runCatching {
            voiceRecorder.start()
            isRecording = true
        }.onFailure {
            isRecording = false
            Toast.makeText(
                context,
                it.message ?: "Could not start recording.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice notes.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                NightFileLibrary.importUri(context, uri)
            } ?: return@launch

            repository.addLibraryItem(
                NightLibraryItemEntity(
                    id = saved.id,
                    name = saved.name,
                    mimeType = saved.mimeType,
                    sizeBytes = saved.sizeBytes,
                    localPath = saved.localPath,
                    createdAt = saved.createdAt,
                    sourceChatId = activeChatId,
                )
            )

            val payload = JSONObject()
                .put("localPath", saved.localPath)
                .put("mimeType", saved.mimeType)
                .put("sizeBytes", saved.sizeBytes)
                .toString()

            repository.appendMessage(
                NightMessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = activeChatId,
                    role = "user",
                    type = if (saved.mimeType.startsWith("image/")) "image" else "file",
                    text = saved.name,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = saved.id,
                    payloadJson = payload,
                )
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val source = File(context.cacheDir, "night_camera_" + System.currentTimeMillis() + ".jpg")
                FileOutputStream(source).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                }
                NightFileLibrary.registerLocalFile(
                    context = context,
                    source = source,
                    name = "Photo " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date()) + ".jpg",
                    mimeType = "image/jpeg",
                )
            } ?: return@launch

            repository.addLibraryItem(
                NightLibraryItemEntity(
                    id = saved.id,
                    name = saved.name,
                    mimeType = saved.mimeType,
                    sizeBytes = saved.sizeBytes,
                    localPath = saved.localPath,
                    createdAt = saved.createdAt,
                    sourceChatId = activeChatId,
                )
            )

            repository.appendMessage(
                NightMessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = activeChatId,
                    role = "user",
                    type = "image",
                    text = saved.name,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = saved.id,
                    payloadJson = JSONObject()
                        .put("localPath", saved.localPath)
                        .put("mimeType", saved.mimeType)
                        .put("sizeBytes", saved.sizeBytes)
                        .toString(),
                )
            )
        }
    }

    fun leaveChat() {
        val leavingId = activeChatId
        scope.launch { checkpoint(leavingId) }
        screen = "tabs"
    }

    BackHandler(enabled = screen != "tabs") {
        if (screen == "chat") {
            leaveChat()
        } else {
            screen = if (selectedTab == MainTab.You) "tabs" else "chat"
        }
    }

    when (screen) {
        "profile" -> NightProfileScreen(
            displayName = displayName,
            onBack = { screen = "tabs" },
            onSaveName = { value ->
                scope.launch {
                    repository.setDisplayName(value)
                    screen = "tabs"
                }
            },
        )

        "appearance" -> NightAppearanceScreen(
            appearance = appearanceEntity ?: NightAppearanceEntity(),
            onBack = { screen = "tabs" },
            onUpdate = { updated ->
                scope.launch { repository.setAppearance(updated) }
            },
        )

        "providers" -> NightProvidersScreen(
            profiles = profiles,
            models = providerModels,
            onBack = { screen = "tabs" },
            onCapabilityRoutingClick = { screen = "capability_routes" },
            onAddProfile = { provider, service, name, key, endpoint, region, makeDefault ->
                scope.launch {
                    runCatching {
                        providerManager.addProfile(
                            providerType = provider,
                            serviceKind = service,
                            displayName = name,
                            apiKey = key,
                            endpoint = endpoint,
                            region = region,
                            makeDefault = makeDefault,
                        )
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Could not save provider.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onAddModel = { providerProfile, modelId, name, deployment, capabilities, makeDefault ->
                scope.launch {
                    runCatching {
                        providerManager.addModel(
                            profile = providerProfile,
                            modelId = modelId,
                            displayName = name,
                            deploymentName = deployment,
                            capabilities = capabilities,
                            makeDefault = makeDefault,
                        )
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Could not save model.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteProfile = { providerProfile ->
                scope.launch { providerManager.deleteProfile(providerProfile) }
            },
            onDeleteModel = { model ->
                scope.launch { providerManager.deleteModel(model) }
            },
        )

        "choose_ai" -> NightAiSelectorScreen(
            profiles = profiles,
            models = providerModels,
            selectedProfileId = activeChat?.selectedProviderProfileId,
            selectedModelId = activeChat?.selectedModel,
            onBack = { screen = "chat" },
            onSelect = { providerProfile, model ->
                scope.launch {
                    repository.setChatModel(
                        chatId = activeChatId,
                        provider = providerProfile.providerType,
                        profileId = providerProfile.id,
                        model = model.id,
                    )
                    screen = "chat"
                }
            },
        )

        "memory" -> NightMemoryScreen(
            chats = chats,
            onBack = { screen = "tabs" },
            onChatClick = {
                activeChatId = it.id
                screen = "chat_memory"
            },
        )

        "chat_memory" -> NightChatMemoryScreen(
            chat = activeChat,
            onBack = { screen = "chat" },
            onRefresh = {
                scope.launch { checkpoint(activeChatId) }
            },
        )

        "capability_routes" -> NightCapabilityRoutesScreen(
            profiles = profiles,
            models = providerModels,
            routes = capabilityRoutes,
            onBack = { screen = "providers" },
            onSetRoute = { capability, providerProfile, model, useSelectedFirst ->
                scope.launch {
                    repository.setCapabilityRoute(
                        NightCapabilityRouteEntity(
                            id = capability,
                            capability = capability,
                            providerProfileId = providerProfile.id,
                            modelId = model?.id,
                            useSelectedChatModelFirst = useSelectedFirst,
                            isEnabled = true,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            },
        )

        "scheduled_tasks" -> NightScheduledTasksScreen(
            tasks = scheduledTasks,
            onBack = { screen = "tabs" },
            onDelete = { task ->
                scope.launch { scheduleManager.cancel(task) }
            },
        )

        "settings" -> ModernSettingsScreen(
            onBack = { screen = "tabs" },
        )

        "chat" -> CurrentWhatsAppConversation(
            contactName = activeChat?.title ?: "Night",
            subtitle = when {
                activeModel != null && activeProfile != null ->
                    activeModel.displayName + " • " + activeProfile.providerType.replaceFirstChar { it.uppercase() }
                else -> "Choose AI"
            },
            messages = visualMessages,
            messageText = messageText,
            onMessageTextChange = { messageText = it },
            onBackClick = { leaveChat() },
            appearance = appearance,
            onSendClick = {
                val text = messageText.trim()
                if (text.isEmpty()) return@CurrentWhatsAppConversation
                messageText = ""

                scope.launch {
                    repository.appendText(
                        chatId = activeChatId,
                        role = "user",
                        text = text,
                    )

                    val localAppearanceResult = appearanceController.handleNaturalRequest(text)
                    if (localAppearanceResult != null) {
                        repository.appendText(
                            chatId = activeChatId,
                            role = "assistant",
                            text = localAppearanceResult,
                        )
                        return@launch
                    }

                    val result = aiGateway.reply(activeChatId, displayName)
                    repository.appendText(
                        chatId = activeChatId,
                        role = "assistant",
                        text = result.getOrElse { error ->
                            when {
                                error.message?.contains("No chat AI") == true ->
                                    "No AI is selected for this chat yet. Tap Choose AI to pick a configured model."
                                else ->
                                    "I couldn't reach the selected AI. " + (error.message ?: "Check the provider settings.")
                            }
                        },
                    )
                }
            },
            onCallClick = {
                Toast.makeText(
                    context,
                    "Configure Azure Live Voice in You → AI & providers.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onMenuAction = { action ->
                when (action) {
                    "Memory & summary" -> screen = "chat_memory"
                    "Files in chat" -> {
                        selectedTabName = MainTab.Updates.name
                        screen = "tabs"
                    }
                    "Rename chat" -> {
                        renameValue = activeChat?.title.orEmpty()
                        renameOpen = true
                    }
                    "Choose AI" -> screen = "choose_ai"
                    "Clear chat" -> scope.launch { repository.clearChat(activeChatId) }
                    "Delete chat" -> scope.launch {
                        repository.deleteChat(activeChatId)
                        activeChatId = "night-core"
                        repository.ensureChat("night-core", "Night")
                        screen = "tabs"
                    }
                    "Export chat" -> scope.launch {
                        exportChat(context, activeChat?.title ?: "Night", messageEntities)
                    }
                    "Search chat" -> Toast.makeText(
                        context,
                        "Chat search is being wired to the persistent message index.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onMessageButtonClick = { _, actionId ->
                when {
                    actionId == "choose_ai" || actionId.startsWith("ai_") -> screen = "choose_ai"
                    actionId == "open_library" -> {
                        selectedTabName = MainTab.Updates.name
                        screen = "tabs"
                    }
                    actionId.contains("summary") -> screen = "chat_memory"
                }
            },
            onAttachmentClick = {},
            onAttachmentAction = { action ->
                when (action) {
                    "Gallery" -> attachmentPicker.launch(arrayOf("image/*"))
                    "Document" -> attachmentPicker.launch(arrayOf("*/*"))
                    "Camera" -> cameraLauncher.launch(null)
                    "Choose AI" -> screen = "choose_ai"
                    "Schedule" -> scheduleOpen = true
                    "Location" -> repositoryActionToast(context, "Location is a Night message type; the location capability is not configured yet.")
                    "Poll" -> repositoryActionToast(context, "Poll UI is available; persistent poll data is next.")
                    "AI images" -> repositoryActionToast(context, "Choose an image-capable provider or extension first.")
                }
            },
            onCameraClick = { cameraLauncher.launch(null) },
            isRecording = isRecording,
            onMicClick = {
                if (isRecording) {
                    scope.launch { persistVoiceRecording() }
                } else if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    startVoiceRecording()
                } else {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onVoiceClick = { path ->
                runCatching {
                    activePlayer?.release()
                    activePlayer = MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                        setOnCompletionListener { player ->
                            player.release()
                            if (activePlayer === player) activePlayer = null
                        }
                        start()
                    }
                }.onFailure {
                    Toast.makeText(context, "Could not play this voice note.", Toast.LENGTH_SHORT).show()
                }
            },
        )

        else -> when (selectedTab) {
            MainTab.Chats -> ModernChatsTab(
                chats = chatRows,
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onChatClick = { row ->
                    activeChatId = row.userId ?: "night-core"
                    messageText = ""
                    screen = "chat"
                },
                onNewChat = {
                    scope.launch {
                        val chat = repository.createChat("New chat")
                        activeChatId = chat.id
                        messageText = ""
                        screen = "chat"
                    }
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.Updates -> NightFilesTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.You -> NightYouTab(
                displayName = displayName,
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onProfileClick = { screen = "profile" },
                onProvidersClick = { screen = "providers" },
                onMemoryClick = { screen = "memory" },
                onSchedulesClick = { screen = "scheduled_tasks" },
                onLibraryStorageClick = {
                    selectedTabName = MainTab.Updates.name
                    screen = "tabs"
                },
                onAppearanceClick = { screen = "appearance" },
                onPrivacyClick = { screen = "settings" },
                onSettingsClick = { screen = "settings" },
            )

            else -> {
                selectedTabName = MainTab.Chats.name
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancel()
            activePlayer?.release()
        }
    }

    if (scheduleOpen) {
        NightScheduleDialog(
            initialPrompt = messageText,
            onDismiss = { scheduleOpen = false },
            onSchedule = { prompt, delayMs, repeatMinutes ->
                scope.launch {
                    runCatching {
                        scheduleManager.create(
                            chatId = activeChatId,
                            prompt = prompt,
                            runAt = System.currentTimeMillis() + delayMs,
                            repeatMinutes = repeatMinutes,
                        )
                    }.onSuccess {
                        Toast.makeText(context, "Scheduled with Night.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Could not create schedule.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                scheduleOpen = false
            },
        )
    }

    if (renameOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { androidx.compose.material3.Text("Rename chat") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(80) },
                    singleLine = true,
                    label = { androidx.compose.material3.Text("Chat name") },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val value = renameValue.trim()
                        if (value.isNotBlank()) {
                            scope.launch { repository.renameChat(activeChatId, value) }
                        }
                        renameOpen = false
                    }
                ) { androidx.compose.material3.Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { renameOpen = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }
}


private fun NightAppearanceEntity.toChatAppearance(): NightChatAppearance =
    NightChatAppearance(
        userBubbleColor = ComposeColor(userBubbleColor.toInt()),
        aiBubbleColor = ComposeColor(aiBubbleColor.toInt()),
        wallpaperTopColor = ComposeColor(wallpaperTopColor.toInt()),
        wallpaperMiddleColor = ComposeColor(wallpaperMiddleColor.toInt()),
        wallpaperBottomColor = ComposeColor(wallpaperBottomColor.toInt()),
        accentColor = ComposeColor(accentColor.toInt()),
        fontFamilyKey = fontFamilyKey,
        messageFontScale = messageFontScale,
    )

private fun NightMessageEntity.toVisualMessage(): WhatsAppVisualMessage {
    val mine = role == "user"
    val time = nightTime(createdAt)
    val payload = runCatching { JSONObject(payloadJson) }.getOrNull()

    return when (type) {
        "image" -> WhatsAppVisualMessage.PhotoMessage(
            id = id,
            caption = text,
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
        )

        "file" -> WhatsAppVisualMessage.FileMessage(
            id = id,
            name = text,
            detail = buildString {
                val mime = payload?.optString("mimeType").orEmpty()
                if (mime.isNotBlank()) append(mime)
                val bytes = payload?.optLong("sizeBytes", 0L) ?: 0L
                if (bytes > 0L) {
                    if (isNotEmpty()) append(" • ")
                    append(formatBytes(bytes))
                }
            }.ifBlank { "File" },
            time = time,
            mine = mine,
            read = mine,
        )

        "voice" -> WhatsAppVisualMessage.VoiceMessage(
            id = id,
            duration = payload?.optString("duration").orEmpty().ifBlank { "0:00" },
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
        )

        else -> WhatsAppVisualMessage.TextMessage(
            id = id,
            text = text,
            time = time,
            mine = mine,
            read = mine,
        )
    }
}

private fun nightTime(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatChatListTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else nightTime(timestamp)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> bytes.toString() + " B"
}

private fun repositoryActionToast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private suspend fun exportChat(
    context: android.content.Context,
    title: String,
    messages: List<NightMessageEntity>,
) = withContext(Dispatchers.IO) {
    val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Night" }
    val file = File(context.cacheDir, safeTitle + "_chat.txt")
    file.writeText(
        messages.joinToString("\n") {
            val speaker = if (it.role == "assistant") "Night" else "You"
            "[" + nightTime(it.createdAt) + "] " + speaker + ": " + it.text
        }
    )

    withContext(Dispatchers.Main) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".files",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export chat"))
    }
}
