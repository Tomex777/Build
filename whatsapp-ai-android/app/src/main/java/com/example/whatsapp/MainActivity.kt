package com.example.whatsapp

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.whatsapp.data.NightFileLibrary
import com.example.whatsapp.data.night.NightAgentToolExecutor
import com.example.whatsapp.data.night.NightAiGateway
import com.example.whatsapp.data.night.NightAppearanceController
import com.example.whatsapp.data.night.NightAppearanceEntity
import com.example.whatsapp.data.night.NightCapabilityRouteEntity
import com.example.whatsapp.data.night.NightLibraryItemEntity
import com.example.whatsapp.data.night.NightLinkPreviewService
import com.example.whatsapp.data.night.NightLiveVoiceClient
import com.example.whatsapp.data.night.NightMessageEntity
import com.example.whatsapp.data.night.NightProviderManager
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.data.night.NightScheduleManager
import com.example.whatsapp.data.night.NightSpeechService
import com.example.whatsapp.data.night.NightStructuredReplyParser
import com.example.whatsapp.data.night.NightToolInvocation
import com.example.whatsapp.data.night.NightVoiceRecorder
import com.example.whatsapp.extensions.messages.ExtensionMessageCodec
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.chatscreen.AudioPlaybackUiState
import com.example.whatsapp.presentation.chatscreen.ChoiceResultMessage
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.ExtensionResultMessage
import com.example.whatsapp.presentation.chatscreen.MangaResultMessage
import com.example.whatsapp.presentation.chatscreen.NightChatAppearance
import com.example.whatsapp.presentation.chatscreen.NightChoiceDialog
import com.example.whatsapp.presentation.chatscreen.NightChatMediaItem
import com.example.whatsapp.presentation.chatscreen.NightMediaViewerScreen
import com.example.whatsapp.presentation.chatscreen.NightPdfViewerScreen
import com.example.whatsapp.presentation.chatscreen.NightPdfEditorScreen
import com.example.whatsapp.presentation.chatscreen.NightMediaComposerScreen
import com.example.whatsapp.presentation.chatscreen.ReplyKind
import com.example.whatsapp.presentation.chatscreen.ReplyPreview
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.files.NightFilesTab
import com.example.whatsapp.presentation.profile.NightAiSelectorScreen
import com.example.whatsapp.presentation.profile.NightAppearanceScreen
import com.example.whatsapp.presentation.profile.NightCapabilityRoutesScreen
import com.example.whatsapp.presentation.profile.NightChatMemoryScreen
import com.example.whatsapp.presentation.profile.NightChatFilesScreen
import com.example.whatsapp.presentation.profile.NightChatSearchScreen
import com.example.whatsapp.presentation.profile.NightMemoryScreen
import com.example.whatsapp.presentation.profile.NightLiveVoiceScreen
import com.example.whatsapp.presentation.profile.NightProfileScreen
import com.example.whatsapp.presentation.profile.NightProvidersScreen
import com.example.whatsapp.presentation.profile.NightScheduleDialog
import com.example.whatsapp.presentation.profile.NightScheduledTasksScreen
import com.example.whatsapp.presentation.profile.NightYouTab
import com.example.whatsapp.presentation.reader.mihon.NightMihonArchiveLoader
import com.example.whatsapp.presentation.reader.mihon.decodeMihonPages
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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val initialChatId = intent?.getStringExtra("night_chat_id")
        setContent {
            WhatsappTheme(darkTheme = true) {
                NightApp(initialChatId = initialChatId)
            }
        }
    }
}

@Composable
private fun NightApp(initialChatId: String? = null) {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context) }
    val providerManager = remember { NightProviderManager.get(context) }
    val aiGateway = remember { NightAiGateway.get(context) }
    val agentTools = remember { NightAgentToolExecutor.get(context) }
    val appearanceController = remember { NightAppearanceController(repository) }
    val voiceRecorder = remember { NightVoiceRecorder(context.applicationContext) }
    val scheduleManager = remember { NightScheduleManager.get(context) }
    val speechService = remember { NightSpeechService.get(context) }
    val liveVoiceClient = remember { NightLiveVoiceClient.get(context) }
    val linkPreviewService = remember { NightLinkPreviewService.get(context) }
    val scope = rememberCoroutineScope()

    val notificationChatId = initialChatId?.takeIf { it.isNotBlank() }
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.Chats.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var screen by rememberSaveable(initialChatId) {
        mutableStateOf(if (notificationChatId == null) "tabs" else "chat")
    }
    var activeChatId by rememberSaveable(initialChatId) {
        mutableStateOf(notificationChatId ?: "night-core")
    }
    var messageText by rememberSaveable { mutableStateOf("") }
    var directImageMode by rememberSaveable { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeAudioPath by remember { mutableStateOf<String?>(null) }
    var audioIsPlaying by remember { mutableStateOf(false) }
    var audioProgress by remember { mutableFloatStateOf(0f) }
    var audioPositionLabel by remember { mutableStateOf("0:00") }
    var scheduleOpen by remember { mutableStateOf(false) }
    var liveVoiceState by remember { mutableStateOf(NightLiveVoiceClient.State.ENDED) }
    var liveVoiceError by remember { mutableStateOf<String?>(null) }
    var replyingToId by rememberSaveable { mutableStateOf<String?>(null) }
    var choiceOpen by remember { mutableStateOf(false) }
    var mediaViewerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfSheetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfViewerName by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaDraft by remember { mutableStateOf<NightMediaDraft?>(null) }
    var mediaCaption by rememberSaveable { mutableStateOf("") }
    var pdfDraft by remember { mutableStateOf<NightPdfDraft?>(null) }
    var pdfCaption by rememberSaveable { mutableStateOf("") }

    val chats by repository.observeChats().collectAsState(initial = emptyList())
    val profiles by repository.observeProviderProfiles().collectAsState(initial = emptyList())
    val providerModels by repository.observeAllProviderModels().collectAsState(initial = emptyList())
    val profile by repository.observeProfile().collectAsState(initial = null)
    val appearanceEntity by repository.observeAppearance().collectAsState(initial = null)
    val scheduledTasks by repository.observeScheduledTasks().collectAsState(initial = emptyList())
    val capabilityRoutes by repository.observeCapabilityRoutes().collectAsState(initial = emptyList())

    val messageFlow = remember(activeChatId) { repository.observeMessages(activeChatId) }
    val messageEntities by messageFlow.collectAsState(initial = emptyList())

    LaunchedEffect(activeChatId) {
        directImageMode = false
    }

    val displayName = profile?.displayName ?: "Dawson"
    val appearance = (appearanceEntity ?: NightAppearanceEntity()).toChatAppearance()
    val activeChat = chats.firstOrNull { it.id == activeChatId }
    val activeModel = providerModels.firstOrNull { it.id == activeChat?.selectedModel }
    val activeProfile = profiles.firstOrNull { it.id == activeChat?.selectedProviderProfileId }

    val messageById = remember(messageEntities) { messageEntities.associateBy { it.id } }
    val replyingTo = replyingToId?.let(messageById::get)
    val visualMessages = remember(messageEntities, messageById) {
        messageEntities.map { it.toVisualMessage(messageById) }
    }

    val chatMediaItems = remember(visualMessages, activeChat?.title) {
        visualMessages.mapNotNull { message ->
            when (message) {
                is WhatsAppVisualMessage.PhotoMessage -> message.localPath?.let { path ->
                    NightChatMediaItem(
                        id = message.id,
                        localPath = path,
                        mimeType = "image/*",
                        caption = message.caption,
                        time = message.time,
                        sender = if (message.mine) "You" else (activeChat?.title ?: "Night"),
                    )
                }
                is WhatsAppVisualMessage.VideoMessage -> message.localPath?.let { path ->
                    NightChatMediaItem(
                        id = message.id,
                        localPath = path,
                        mimeType = "video/*",
                        caption = message.caption,
                        time = message.time,
                        sender = if (message.mine) "You" else (activeChat?.title ?: "Night"),
                        thumbnailPath = message.thumbnailPath,
                        duration = message.duration,
                    )
                }
                else -> null
            }
        }
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

        val messageId = java.util.UUID.randomUUID().toString()

        repository.addLibraryItem(
            NightLibraryItemEntity(
                id = saved.id,
                name = saved.name,
                mimeType = saved.mimeType,
                sizeBytes = saved.sizeBytes,
                localPath = saved.localPath,
                createdAt = saved.createdAt,
                sourceChatId = activeChatId,
                sourceMessageId = messageId,
            )
        )
        val initialPayload = JSONObject()
            .put("localPath", saved.localPath)
            .put("duration", formatDuration(recorded.durationMs))
            .put("durationMs", recorded.durationMs)

        val voiceMessage = NightMessageEntity(
            id = messageId,
            chatId = activeChatId,
            role = "user",
            type = "voice",
            text = "",
            createdAt = System.currentTimeMillis(),
            libraryFileId = saved.id,
            payloadJson = initialPayload.toString(),
            replyToMessageId = replyingToId,
        )
        repository.appendMessage(voiceMessage)
        replyingToId = null

        val transcript = speechService.transcribe(saved.localPath).getOrNull()
        if (!transcript.isNullOrBlank()) {
            val updatedPayload = JSONObject(voiceMessage.payloadJson)
                .put("transcript", transcript)

            repository.appendMessage(
                voiceMessage.copy(
                    text = transcript,
                    payloadJson = updatedPayload.toString(),
                )
            )

            streamNightAssistantReply(
                repository = repository,
                aiGateway = aiGateway,
                chatId = activeChatId,
                displayName = displayName,
                noAiMessage = "I transcribed the voice note, but no AI is selected for this chat yet.",
                failurePrefix = "I transcribed the voice note, but I couldn't reach an available AI. ",
            )
        }
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

    fun startLiveVoiceCall() {
        val callChatId = activeChatId
        liveVoiceError = null
        liveVoiceState = NightLiveVoiceClient.State.CONNECTING
        screen = "live_voice"

        scope.launch {
            runCatching {
                liveVoiceClient.connect(
                    chatId = callChatId,
                    displayName = displayName,
                    listener = object : NightLiveVoiceClient.Listener {
                        override fun onState(state: NightLiveVoiceClient.State) {
                            scope.launch(Dispatchers.Main) {
                                liveVoiceState = state
                            }
                        }

                        override fun onUserTranscript(text: String) {
                            scope.launch {
                                repository.appendText(
                                    chatId = callChatId,
                                    role = "user",
                                    text = text,
                                )
                            }
                        }

                        override fun onAssistantTranscript(text: String) {
                            scope.launch {
                                repository.appendText(
                                    chatId = callChatId,
                                    role = "assistant",
                                    text = text,
                                )
                            }
                        }

                        override fun onError(message: String) {
                            scope.launch(Dispatchers.Main) {
                                liveVoiceError = message
                            }
                        }
                    },
                )
            }.onFailure {
                liveVoiceError = it.message ?: "Could not start Live Voice."
                liveVoiceState = NightLiveVoiceClient.State.ENDED
            }
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

    val liveVoicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startLiveVoiceCall()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for Live Voice.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(scheduledTasks.size) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            scheduledTasks.any { it.state == "scheduled" } &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

            val replyId = replyingToId
            val mime = saved.mimeType

            if (mime.startsWith("image/") || mime.startsWith("video/")) {
                val payload = JSONObject()
                    .put("localPath", saved.localPath)
                    .put("mimeType", mime)
                    .put("sizeBytes", saved.sizeBytes)

                var thumbnailPath: String? = null

                if (mime.startsWith("image/")) {
                    payload.put("aspectRatio", readImageAspectRatio(saved.localPath))
                } else {
                    val meta = withContext(Dispatchers.IO) {
                        extractVideoMeta(context, saved.localPath)
                    }
                    thumbnailPath = meta.thumbnailPath
                    payload
                        .put("duration", meta.duration)
                        .put("aspectRatio", meta.aspectRatio)
                    meta.thumbnailPath?.let { payload.put("thumbnailPath", it) }
                }

                mediaDraft = NightMediaDraft(
                    libraryId = saved.id,
                    name = saved.name,
                    mimeType = mime,
                    sizeBytes = saved.sizeBytes,
                    localPath = saved.localPath,
                    createdAt = saved.createdAt,
                    messageType = if (mime.startsWith("video/")) "video" else "image",
                    payloadJson = payload.toString(),
                    thumbnailPath = thumbnailPath,
                    replyToMessageId = replyId,
                )
                mediaCaption = ""
                replyingToId = null
                screen = "media_compose"
                return@launch
            }

            if (
                mime.equals("application/pdf", ignoreCase = true) ||
                saved.name.endsWith(".pdf", ignoreCase = true)
            ) {
                pdfDraft = NightPdfDraft(
                    libraryId = saved.id,
                    name = saved.name,
                    localPath = saved.localPath,
                    createdAt = saved.createdAt,
                    replyToMessageId = replyId,
                )
                pdfCaption = ""
                replyingToId = null
                screen = "pdf_compose"
                return@launch
            }

            val messageId = java.util.UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("localPath", saved.localPath)
                .put("mimeType", mime)
                .put("sizeBytes", saved.sizeBytes)

            val messageType: String
            val messageTextValue: String

            if (mime.startsWith("audio/")) {
                messageType = "audio"
                messageTextValue = saved.name
                val meta = withContext(Dispatchers.IO) {
                    extractAudioMeta(context, saved.localPath, saved.name)
                }
                payload
                    .put("title", meta.title)
                    .put("artist", meta.artist)
                    .put("duration", meta.duration)
                meta.artworkPath?.let { payload.put("artworkPath", it) }
            } else {
                messageType = "file"
                messageTextValue = saved.name
            }

            repository.addLibraryItem(
                NightLibraryItemEntity(
                    id = saved.id,
                    name = saved.name,
                    mimeType = mime,
                    sizeBytes = saved.sizeBytes,
                    localPath = saved.localPath,
                    createdAt = saved.createdAt,
                    sourceChatId = activeChatId,
                    sourceMessageId = messageId,
                )
            )

            repository.appendMessage(
                NightMessageEntity(
                    id = messageId,
                    chatId = activeChatId,
                    role = "user",
                    type = messageType,
                    text = messageTextValue,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = saved.id,
                    payloadJson = payload.toString(),
                    replyToMessageId = replyId,
                )
            )
            replyingToId = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val source = File(
                    context.cacheDir,
                    "night_camera_" + System.currentTimeMillis() + ".jpg",
                )
                FileOutputStream(source).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                }
                NightFileLibrary.registerLocalFile(
                    context = context,
                    source = source,
                    name = "Photo " +
                        SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date()) +
                        ".jpg",
                    mimeType = "image/jpeg",
                )
            } ?: return@launch

            val payload = JSONObject()
                .put("localPath", saved.localPath)
                .put("mimeType", saved.mimeType)
                .put("sizeBytes", saved.sizeBytes)
                .put("aspectRatio", readImageAspectRatio(saved.localPath))

            mediaDraft = NightMediaDraft(
                libraryId = saved.id,
                name = saved.name,
                mimeType = saved.mimeType,
                sizeBytes = saved.sizeBytes,
                localPath = saved.localPath,
                createdAt = saved.createdAt,
                messageType = "image",
                payloadJson = payload.toString(),
                thumbnailPath = null,
                replyToMessageId = replyingToId,
            )
            mediaCaption = ""
            replyingToId = null
            screen = "media_compose"
        }
    }

    fun cancelMediaDraft() {
        val draft = mediaDraft ?: return
        NightFileLibrary.remove(context, draft.libraryId)
        draft.thumbnailPath?.let { runCatching { File(it).delete() } }
        mediaDraft = null
        mediaCaption = ""
        screen = "chat"
    }

    fun sendMediaDraft(
        preparedPath: String? = null,
        preparedMimeType: String? = null,
        preparedName: String? = null,
    ) {
        val draft = mediaDraft ?: return
        val chatId = activeChatId
        val caption = mediaCaption.trim()
        val messageId = java.util.UUID.randomUUID().toString()

        scope.launch {
            val finalPath = preparedPath ?: draft.localPath
            val finalMimeType = preparedMimeType ?: draft.mimeType
            val finalName = preparedName ?: draft.name
            val finalFile = File(finalPath)

            val finalPayload = runCatching { JSONObject(draft.payloadJson) }
                .getOrElse { JSONObject() }
                .put("localPath", finalPath)
                .put("mimeType", finalMimeType)
                .put("sizeBytes", finalFile.length())

            var finalThumbnail = draft.thumbnailPath
            if (finalMimeType.startsWith("video/")) {
                val videoMeta = withContext(Dispatchers.IO) {
                    extractVideoMeta(context, finalPath)
                }
                finalPayload
                    .put("duration", videoMeta.duration)
                    .put("aspectRatio", videoMeta.aspectRatio)
                videoMeta.thumbnailPath?.let {
                    finalPayload.put("thumbnailPath", it)
                    finalThumbnail = it
                }
            } else if (finalMimeType.startsWith("image/")) {
                finalPayload.put("aspectRatio", readImageAspectRatio(finalPath))
                finalPayload.remove("thumbnailPath")
                finalThumbnail = null
            }

            val finalDraft = draft.copy(
                name = finalName,
                mimeType = finalMimeType,
                sizeBytes = finalFile.length(),
                localPath = finalPath,
                payloadJson = finalPayload.toString(),
                thumbnailPath = finalThumbnail,
            )

            repository.addLibraryItem(
                NightLibraryItemEntity(
                    id = finalDraft.libraryId,
                    name = finalDraft.name,
                    mimeType = finalDraft.mimeType,
                    sizeBytes = finalDraft.sizeBytes,
                    localPath = finalDraft.localPath,
                    createdAt = finalDraft.createdAt,
                    sourceChatId = chatId,
                    sourceMessageId = messageId,
                )
            )

            repository.appendMessage(
                NightMessageEntity(
                    id = messageId,
                    chatId = chatId,
                    role = "user",
                    type = finalDraft.messageType,
                    text = caption,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = finalDraft.libraryId,
                    payloadJson = finalDraft.payloadJson,
                    replyToMessageId = finalDraft.replyToMessageId,
                )
            )

            if (finalDraft.localPath != draft.localPath) {
                runCatching { File(draft.localPath).delete() }
                if (draft.thumbnailPath != finalDraft.thumbnailPath) {
                    draft.thumbnailPath?.let { runCatching { File(it).delete() } }
                }
            }

            mediaDraft = null
            mediaCaption = ""
            screen = "chat"
        }
    }

    fun playAudio(path: String) {
        runCatching {
            val current = activePlayer
            if (activeAudioPath == path && current != null) {
                if (current.isPlaying) {
                    current.pause()
                    audioIsPlaying = false
                } else {
                    current.start()
                    audioIsPlaying = true
                }
                return@runCatching
            }

            current?.release()
            val player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener { finished ->
                    finished.release()
                    if (activePlayer === finished) {
                        activePlayer = null
                        activeAudioPath = null
                        audioIsPlaying = false
                        audioProgress = 0f
                        audioPositionLabel = "0:00"
                    }
                }
                start()
            }
            activePlayer = player
            activeAudioPath = path
            audioIsPlaying = true
            audioProgress = 0f
            audioPositionLabel = "0:00"
        }.onFailure {
            Toast.makeText(context, "Could not play audio.", Toast.LENGTH_SHORT).show()
        }
    }

    fun seekAudio(path: String, progress: Float) {
        val player = activePlayer ?: return
        if (activeAudioPath != path) return
        val duration = runCatching { player.duration }.getOrDefault(0)
        if (duration <= 0) return

        val clamped = progress.coerceIn(0f, 1f)
        val target = (duration * clamped).toInt()
        runCatching { player.seekTo(target) }
        audioProgress = clamped
        audioPositionLabel = formatDuration(target.toLong())
    }

    LaunchedEffect(activeAudioPath, audioIsPlaying) {
        while (activeAudioPath != null) {
            val player = activePlayer ?: break
            val duration = runCatching { player.duration }.getOrDefault(0)
            val position = runCatching { player.currentPosition }.getOrDefault(0)

            if (duration > 0) {
                audioProgress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                audioPositionLabel = formatDuration(position.toLong())
            }

            if (!player.isPlaying) {
                audioIsPlaying = false
                break
            }
            delay(250)
        }
    }

    fun leaveChat() {
        val leavingId = activeChatId
        scope.launch { checkpoint(leavingId) }
        screen = "tabs"
    }

    BackHandler(enabled = screen != "tabs") {
        when (screen) {
            "chat" -> leaveChat()
            "live_voice" -> {
                liveVoiceClient.stop()
                screen = "chat"
            }
            "media_viewer" -> {
                mediaViewerPath = null
                screen = "chat"
            }
            "media_compose" -> cancelMediaDraft()
            else -> {
                screen = if (selectedTab == MainTab.You) "tabs" else "chat"
            }
        }
    }

    when (screen) {
        "media_compose" -> {
            val draft = mediaDraft
            if (draft != null) {
                NightMediaComposerScreen(
                    localPath = draft.localPath,
                    mimeType = draft.mimeType,
                    fileName = draft.name,
                    videoThumbnailPath = draft.thumbnailPath,
                    caption = mediaCaption,
                    onCaptionChange = { mediaCaption = it },
                    onCancel = ::cancelMediaDraft,
                    onPreparedSend = { path, mime, name ->
                        sendMediaDraft(
                            preparedPath = path,
                            preparedMimeType = mime,
                            preparedName = name,
                        )
                    },
                )
            } else {
                screen = "chat"
            }
        }

        "media_viewer" -> {
            val pathToShow = mediaViewerPath
            if (pathToShow != null && chatMediaItems.isNotEmpty()) {
                val initialIndex = chatMediaItems.indexOfFirst { it.localPath == pathToShow }
                    .takeIf { it >= 0 }
                    ?: 0
                NightMediaViewerScreen(
                    items = chatMediaItems,
                    initialIndex = initialIndex,
                    onBack = {
                        mediaViewerPath = null
                        screen = "chat"
                    },
                    onEdit = { item ->
                        val source = File(item.localPath)
                        if (!source.exists()) {
                            Toast.makeText(context, "This media is not available locally.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    NightFileLibrary.registerLocalFile(
                                        context = context,
                                        source = source,
                                        name = "Edited " + source.name,
                                        mimeType = item.mimeType,
                                    )
                                }
                                if (saved != null) {
                                    val payload = JSONObject()
                                        .put("localPath", saved.localPath)
                                        .put("mimeType", saved.mimeType)
                                        .put("sizeBytes", saved.sizeBytes)
                                    if (item.isVideo) {
                                        val meta = withContext(Dispatchers.IO) {
                                            extractVideoMeta(context, saved.localPath)
                                        }
                                        payload
                                            .put("duration", meta.duration)
                                            .put("aspectRatio", meta.aspectRatio)
                                        meta.thumbnailPath?.let { payload.put("thumbnailPath", it) }
                                    } else {
                                        payload.put("aspectRatio", readImageAspectRatio(saved.localPath))
                                    }
                                    mediaDraft = NightMediaDraft(
                                        libraryId = saved.id,
                                        name = saved.name,
                                        mimeType = saved.mimeType,
                                        sizeBytes = saved.sizeBytes,
                                        localPath = saved.localPath,
                                        createdAt = saved.createdAt,
                                        messageType = if (item.isVideo) "video" else "image",
                                        payloadJson = payload.toString(),
                                        thumbnailPath = if (item.isVideo) {
                                            payload.optString("thumbnailPath").takeIf { it.isNotBlank() }
                                        } else {
                                            null
                                        },
                                        replyToMessageId = null,
                                    )
                                    mediaCaption = item.caption
                                    mediaViewerPath = null
                                    screen = "media_compose"
                                }
                            }
                        }
                    },
                )
            } else {
                mediaViewerPath = null
                screen = "chat"
            }
        }

        "live_voice" -> NightLiveVoiceScreen(
            chatTitle = activeChat?.title ?: "Night",
            state = liveVoiceState,
            error = liveVoiceError,
            onEndCall = {
                liveVoiceClient.stop()
                screen = "chat"
            },
        )

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
            onAddProfile = { provider, service, name, key, endpoint, region, language, voiceName, makeDefault ->
                scope.launch {
                    runCatching {
                        providerManager.addProfile(
                            providerType = provider,
                            serviceKind = service,
                            displayName = name,
                            apiKey = key,
                            endpoint = endpoint,
                            region = region,
                            language = language,
                            voiceName = voiceName,
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
            onTestModel = { providerProfile, model ->
                scope.launch {
                    aiGateway.testModel(providerProfile, model)
                        .onSuccess { response ->
                            Toast.makeText(
                                context,
                                if (response.trim() == "NIGHT_OK") {
                                    model.displayName + " is connected."
                                } else {
                                    model.displayName + " replied: " + response.take(120)
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                model.displayName + " failed: " + (error.message ?: "Unknown provider error."),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                }
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

        "chat_search" -> NightChatSearchScreen(
            title = activeChat?.title ?: "Night",
            messages = messageEntities,
            onBack = { screen = "chat" },
        )

        "chat_files" -> NightChatFilesScreen(
            title = activeChat?.title ?: "Night",
            messages = messageEntities,
            onBack = { screen = "chat" },
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
                    appendTextWithLinkPreview(
                        repository = repository,
                        linkPreviewService = linkPreviewService,
                        chatId = activeChatId,
                        role = "user",
                        text = text,
                        replyToMessageId = replyingToId,
                    )
                    replyingToId = null

                    if (directImageMode) {
                        directImageMode = false
                        val prompt = text
                            .removePrefix("Generate an image of ")
                            .removePrefix("Generate an image of")
                            .trim()
                            .ifBlank { text.trim() }

                        val toolResult = agentTools.execute(
                            chatId = activeChatId,
                            invocation = NightToolInvocation(
                                id = "direct_image_" + java.util.UUID.randomUUID(),
                                name = "generate_image",
                                argumentsJson = JSONObject()
                                    .put("prompt", prompt)
                                    .put("size", "1024x1024")
                                    .toString(),
                            ),
                        )
                        val toolJson = runCatching { JSONObject(toolResult) }.getOrNull()
                        if (toolJson?.optBoolean("ok", false) != true) {
                            repository.appendText(
                                chatId = activeChatId,
                                role = "assistant",
                                text = "I couldn't generate that image. " +
                                    (toolJson?.optString("error")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "Check the image-generation route in AI & providers."),
                            )
                        }
                        return@launch
                    }

                    val activeHasTools = activeModel?.capabilities
                        ?.split(",")
                        ?.map { it.trim().lowercase() }
                        ?.contains("tools")
                        ?: false

                    if (!activeHasTools) {
                        val localAppearanceResult = appearanceController.handleNaturalRequest(text)
                        if (localAppearanceResult != null) {
                            repository.appendText(
                                chatId = activeChatId,
                                role = "assistant",
                                text = localAppearanceResult,
                            )
                            return@launch
                        }
                    }

                    streamNightAssistantReply(
                        repository = repository,
                        aiGateway = aiGateway,
                        chatId = activeChatId,
                        displayName = displayName,
                        noAiMessage = "No AI is selected for this chat yet. Tap Choose AI to pick a configured model.",
                        failurePrefix = "I couldn't reach an available AI. ",
                    )
                }
            },
            onCallClick = {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    startLiveVoiceCall()
                } else {
                    liveVoicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onMenuAction = { action ->
                when (action) {
                    "Memory & summary" -> screen = "chat_memory"
                    "Files in chat" -> screen = "chat_files"
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
                    "Search chat" -> screen = "chat_search"
                }
            },
            onMessageButtonClick = { messageId, actionId ->
                when {
                    actionId.startsWith("option_") -> {
                        val index = actionId.removePrefix("option_").toIntOrNull()
                        if (index != null) {
                            scope.launch {
                                val existing = repository.getMessage(messageId) ?: return@launch
                                if (existing.type == "choice") {
                                    val payload = runCatching { JSONObject(existing.payloadJson) }
                                        .getOrElse { JSONObject() }
                                        .put("selectedIndex", index)
                                        .put("selectedBy", "You")
                                    repository.appendMessage(existing.copy(payloadJson = payload.toString()))
                                }
                            }
                        }
                    }
                    actionId == "read" -> {
                        scope.launch {
                            val existing = repository.getMessage(messageId) ?: return@launch
                            if (existing.type != "manga") return@launch

                            val payload = runCatching { JSONObject(existing.payloadJson) }
                                .getOrElse { JSONObject() }
                            val title = payload.optString("title").ifBlank {
                                existing.text.ifBlank { "Manga" }
                            }
                            val chapter = payload.optString("chapter")
                            val archivePath = payload.optString("archivePath")
                                .takeIf { it.isNotBlank() }

                            if (archivePath != null && File(archivePath).exists()) {
                                context.startActivity(
                                    NightMihonReaderActivity.archiveIntent(
                                        context = context,
                                        localPath = archivePath,
                                        displayName = title,
                                    )
                                )
                                return@launch
                            }

                            val pagesRaw = when {
                                payload.optJSONArray("pages") != null ->
                                    payload.optJSONArray("pages")!!.toString()
                                payload.optString("pages").isNotBlank() ->
                                    payload.optString("pages")
                                else -> ""
                            }
                            val pages = decodeMihonPages(pagesRaw)
                            if (pages.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "This manga card has no readable chapter pages yet.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }

                            context.startActivity(
                                NightMihonReaderActivity.intent(
                                    context = context,
                                    title = title,
                                    chapter = chapter,
                                    pages = pages,
                                    readerKey = payload.optString("readerKey").ifBlank { title },
                                    progressKey = payload.optString("progressKey")
                                        .takeIf { it.isNotBlank() }
                                        ?: ("manga:" + existing.id),
                                )
                            )
                        }
                    }
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
                directImageMode = false
                when (action) {
                    "Gallery" -> attachmentPicker.launch(arrayOf("image/*", "video/*"))
                    "Document" -> attachmentPicker.launch(arrayOf("*/*"))
                    "Audio" -> attachmentPicker.launch(arrayOf("audio/*"))
                    "Camera" -> cameraLauncher.launch(null)
                    "Choose AI" -> screen = "choose_ai"
                    "Schedule" -> scheduleOpen = true
                    "Options" -> choiceOpen = true
                    "AI images" -> {
                        directImageMode = true
                        messageText = "Generate an image of "
                    }
                }
            },
            onReplyRequest = { messageId ->
                replyingToId = messageId
            },
            replyPreview = replyingTo?.toReplyPreview(),
            onCancelReply = { replyingToId = null },
            onImageClick = { path ->
                mediaViewerPath = path
                screen = "media_viewer"
            },
            onVideoClick = { path ->
                mediaViewerPath = path
                screen = "media_viewer"
            },
            onLinkClick = { url ->
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                    )
                }.onFailure {
                    Toast.makeText(context, "Could not open this link.", Toast.LENGTH_SHORT).show()
                }
            },
            onFileClick = { path, mimeType ->
                val fileName = File(path).name
                when {
                    NightMihonArchiveLoader.isSupportedArchive(
                        fileName = fileName,
                        mimeType = mimeType,
                    ) -> {
                        context.startActivity(
                            NightMihonReaderActivity.archiveIntent(
                                context = context,
                                localPath = path,
                                displayName = fileName,
                            )
                        )
                    }

                    mimeType.equals("application/pdf", ignoreCase = true) ||
                        path.endsWith(".pdf", ignoreCase = true) -> {
                        pdfSheetPath = path
                    }

                    else -> runCatching {
                        val file = File(path)
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            file,
                        )
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(
                                    uri,
                                    mimeType ?: "application/octet-stream",
                                )
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }.onFailure {
                        Toast.makeText(context, "Could not open this file.", Toast.LENGTH_SHORT).show()
                    }
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
                playAudio(path)
            },
            onAudioClick = { path ->
                playAudio(path)
            },
            audioPlaybackState = AudioPlaybackUiState(
                activePath = activeAudioPath,
                isPlaying = audioIsPlaying,
                progress = audioProgress,
                positionLabel = audioPositionLabel,
            ),
            onAudioSeek = { path, progress ->
                seekAudio(path, progress)
            },
            onTranscribeVoice = { messageId, path ->
                scope.launch {
                    val transcript = speechService.transcribe(path)
                    transcript.onSuccess { text ->
                        val existing = repository.getMessage(messageId) ?: return@onSuccess
                        val payload = runCatching { JSONObject(existing.payloadJson) }
                            .getOrElse { JSONObject() }
                            .put("transcript", text)
                            .put("localPath", path)
                        repository.appendMessage(
                            existing.copy(
                                text = text,
                                payloadJson = payload.toString(),
                            )
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Could not transcribe this voice note.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            onSpeakText = { text ->
                scope.launch {
                    speechService.synthesize(text)
                        .onSuccess { file -> playAudio(file.absolutePath) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Could not synthesize speech.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
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


    pdfSheetPath?.let { pdfPath ->
        NightPdfViewerScreen(
            localPath = pdfPath,
            onBack = { pdfSheetPath = null },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancel()
            liveVoiceClient.stop()
            activePlayer?.release()
        }
    }

    if (choiceOpen) {
        NightChoiceDialog(
            onDismiss = { choiceOpen = false },
            onCreate = { title, options ->
                scope.launch {
                    val messageId = java.util.UUID.randomUUID().toString()
                    val payload = JSONObject()
                        .put("options", JSONArray(options))
                    repository.appendMessage(
                        NightMessageEntity(
                            id = messageId,
                            chatId = activeChatId,
                            role = "user",
                            type = "choice",
                            text = title,
                            createdAt = System.currentTimeMillis(),
                            payloadJson = payload.toString(),
                            replyToMessageId = replyingToId,
                        )
                    )
                    replyingToId = null
                }
                choiceOpen = false
            },
        )
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

private fun NightMessageEntity.toVisualMessage(
    allMessages: Map<String, NightMessageEntity>,
): WhatsAppVisualMessage {
    val mine = role == "user"
    val time = nightTime(createdAt)
    val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
    val reply = replyToMessageId
        ?.let(allMessages::get)
        ?.toReplyPreview()

    return when (type) {
        "image" -> WhatsAppVisualMessage.PhotoMessage(
            id = id,
            caption = text,
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
            aspectRatio = payload?.optDouble("aspectRatio", 1.25)?.toFloat() ?: 1.25f,
            reply = reply,
        )

        "video" -> WhatsAppVisualMessage.VideoMessage(
            id = id,
            caption = text,
            duration = payload?.optString("duration").orEmpty().ifBlank { "0:00" },
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
            thumbnailPath = payload?.optString("thumbnailPath")?.takeIf { it.isNotBlank() },
            aspectRatio = payload?.optDouble("aspectRatio", 16.0 / 9.0)?.toFloat() ?: (16f / 9f),
            reply = reply,
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
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
            mimeType = payload?.optString("mimeType")?.takeIf { it.isNotBlank() },
            reply = reply,
        )

        "link" -> WhatsAppVisualMessage.LinkPreviewMessage(
            id = id,
            body = text,
            url = payload?.optString("url").orEmpty(),
            title = payload?.optString("title").orEmpty(),
            description = payload?.optString("description").orEmpty(),
            site = payload?.optString("site").orEmpty(),
            imageUrl = payload?.optString("imageUrl")?.takeIf { it.isNotBlank() },
            time = time,
            mine = mine,
            read = mine,
            reply = reply,
        )

        "audio" -> WhatsAppVisualMessage.AudioMessage(
            id = id,
            title = payload?.optString("title").orEmpty().ifBlank { text.ifBlank { "Audio" } },
            artist = payload?.optString("artist").orEmpty(),
            duration = payload?.optString("duration").orEmpty().ifBlank { "0:00" },
            detail = buildString {
                val mime = payload?.optString("mimeType").orEmpty()
                if (mime.isNotBlank()) append(mime.substringAfterLast('/').uppercase())
                val bytes = payload?.optLong("sizeBytes", 0L) ?: 0L
                if (bytes > 0L) {
                    if (isNotEmpty()) append(" • ")
                    append(formatBytes(bytes))
                }
            },
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
            artworkPath = payload?.optString("artworkPath")?.takeIf { it.isNotBlank() },
            caption = payload?.optString("caption").orEmpty(),
            reply = reply,
        )

        "voice" -> WhatsAppVisualMessage.VoiceMessage(
            id = id,
            duration = payload?.optString("duration").orEmpty().ifBlank { "0:00" },
            time = time,
            mine = mine,
            read = mine,
            localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
            transcript = payload?.optString("transcript")?.takeIf { it.isNotBlank() },
            reply = reply,
        )

        "choice" -> {
            val optionsArray = payload?.optJSONArray("options")
            val options = buildList {
                if (optionsArray != null) {
                    for (index in 0 until optionsArray.length()) {
                        val option = optionsArray.optString(index).trim()
                        if (option.isNotBlank()) add(option)
                    }
                }
            }

            ChoiceResultMessage(
                id = id,
                title = text,
                options = options,
                selectedIndex = payload
                    ?.takeIf { it.has("selectedIndex") && !it.isNull("selectedIndex") }
                    ?.optInt("selectedIndex"),
                selectedBy = payload?.optString("selectedBy")?.takeIf { it.isNotBlank() },
                mine = mine,
                time = time,
            )
        }

        "manga" -> MangaResultMessage(
            id = id,
            title = payload?.optString("title").orEmpty().ifBlank { text.ifBlank { "Manga" } },
            chapter = payload?.optString("chapter").orEmpty().ifBlank { "Chapter" },
            source = payload?.optString("source").orEmpty().ifBlank { "Night" },
            description = payload?.optString("description").orEmpty(),
            time = time,
            coverPath = payload?.optString("coverPath")?.takeIf { it.isNotBlank() },
            status = payload?.optString("status").orEmpty().ifBlank { "Ongoing" },
            primaryActionLabel = payload?.optString("primaryActionLabel")
                .orEmpty()
                .ifBlank { "Read" },
        )

        "extension" -> {
            val snapshot = ExtensionMessageCodec.decode(payloadJson)
            if (snapshot != null) {
                ExtensionResultMessage(
                    id = id,
                    snapshot = snapshot,
                    time = time,
                    extensionAvailable = true,
                )
            } else {
                WhatsAppVisualMessage.TextMessage(
                    id = id,
                    text = text.ifBlank { "Extension result" },
                    time = time,
                    mine = mine,
                    read = mine,
                    reply = reply,
                )
            }
        }

        else -> WhatsAppVisualMessage.TextMessage(
            id = id,
            text = text,
            time = time,
            mine = mine,
            read = mine,
            reply = reply,
        )
    }
}

private fun NightMessageEntity.toReplyPreview(): ReplyPreview {
    val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
    val author = if (role == "assistant") "Night" else "You"

    return when (type) {
        "image" -> ReplyPreview(
            messageId = id,
            author = author,
            text = text.ifBlank { "Photo" },
            kind = ReplyKind.Image,
            thumbnailPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
        )

        "video" -> ReplyPreview(
            messageId = id,
            author = author,
            text = text.ifBlank { "Video" },
            kind = ReplyKind.Video,
            thumbnailPath = payload?.optString("thumbnailPath")?.takeIf { it.isNotBlank() },
            meta = payload?.optString("duration")?.takeIf { it.isNotBlank() },
        )

        "link" -> ReplyPreview(
            messageId = id,
            author = author,
            text = payload?.optString("title").orEmpty().ifBlank { text.ifBlank { "Link" } },
            kind = ReplyKind.Rich,
            meta = payload?.optString("site")?.takeIf { it.isNotBlank() },
        )

        "audio" -> ReplyPreview(
            messageId = id,
            author = author,
            text = payload?.optString("title").orEmpty().ifBlank { text.ifBlank { "Audio" } },
            kind = ReplyKind.Audio,
            thumbnailPath = payload?.optString("artworkPath")?.takeIf { it.isNotBlank() },
            meta = payload?.optString("duration")?.takeIf { it.isNotBlank() },
        )

        "voice" -> ReplyPreview(
            messageId = id,
            author = author,
            text = "Voice message",
            kind = ReplyKind.Voice,
            meta = payload?.optString("duration")?.takeIf { it.isNotBlank() },
        )

        "file" -> ReplyPreview(
            messageId = id,
            author = author,
            text = text.ifBlank { "File" },
            kind = ReplyKind.File,
            meta = payload?.optString("mimeType")?.takeIf { it.isNotBlank() },
        )

        "choice" -> ReplyPreview(
            messageId = id,
            author = author,
            text = text.ifBlank { "Options" },
            kind = ReplyKind.Rich,
            meta = "Options",
        )

        "extension" -> {
            val snapshot = ExtensionMessageCodec.decode(payloadJson)
            if (snapshot != null) {
                ReplyPreview(
                    messageId = id,
                    author = snapshot.extensionName,
                    text = buildString {
                        append(snapshot.title)
                        if (snapshot.subtitle.isNotBlank()) {
                            append(" — ")
                            append(snapshot.subtitle)
                        }
                    },
                    kind = ReplyKind.Rich,
                    meta = snapshot.badge
                        .ifBlank { snapshot.status }
                        .takeIf { it.isNotBlank() },
                    iconText = snapshot.iconText.ifBlank {
                        snapshot.extensionName.take(1).uppercase()
                    },
                )
            } else {
                ReplyPreview(
                    messageId = id,
                    author = author,
                    text = text.ifBlank { "Extension result" },
                    kind = ReplyKind.Rich,
                )
            }
        }

        else -> ReplyPreview(
            messageId = id,
            author = author,
            text = text,
            kind = ReplyKind.Text,
        )
    }
}

private fun nightTime(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatChatListTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else nightTime(timestamp)

private data class NightMediaDraft(
    val libraryId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: Long,
    val messageType: String,
    val payloadJson: String,
    val thumbnailPath: String?,
    val replyToMessageId: String?,
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}

private data class AudioMeta(
    val title: String,
    val artist: String,
    val duration: String,
    val artworkPath: String?,
)

private fun extractAudioMeta(
    context: android.content.Context,
    path: String,
    fallbackName: String,
): AudioMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)

        val title = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: fallbackName.substringBeforeLast('.')

        val artist = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()
            .orEmpty()

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L

        val artworkPath = retriever.embeddedPicture?.let { bytes ->
            val dir = File(context.filesDir, "night_audio_art").apply { mkdirs() }
            val file = File(dir, "art_" + System.currentTimeMillis() + ".jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }

        AudioMeta(
            title = title,
            artist = artist,
            duration = formatDuration(durationMs),
            artworkPath = artworkPath,
        )
    } catch (_: Throwable) {
        AudioMeta(
            title = fallbackName.substringBeforeLast('.'),
            artist = "",
            duration = "0:00",
            artworkPath = null,
        )
    } finally {
        runCatching { retriever.release() }
    }
}

private data class VideoMeta(
    val thumbnailPath: String?,
    val duration: String,
    val aspectRatio: Float,
)

private fun readImageAspectRatio(path: String): Float {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)
    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        1.25f
    }
}

private fun extractVideoMeta(
    context: android.content.Context,
    path: String,
): VideoMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L

        val width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toFloatOrNull()
            ?: 16f

        val height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toFloatOrNull()
            ?: 9f

        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0

        val ratio = if (height > 0f) {
            if (rotation == 90 || rotation == 270) {
                height / width.coerceAtLeast(1f)
            } else {
                width / height
            }
        } else {
            16f / 9f
        }

        val thumbPath = retriever.getFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        )?.let { bitmap ->
            val dir = File(context.filesDir, "night_video_thumbs").apply { mkdirs() }
            val file = File(dir, "thumb_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            bitmap.recycle()
            file.absolutePath
        }

        VideoMeta(
            thumbnailPath = thumbPath,
            duration = formatDuration(durationMs),
            aspectRatio = ratio.coerceIn(0.70f, 1.85f),
        )
    } catch (_: Throwable) {
        VideoMeta(
            thumbnailPath = null,
            duration = "0:00",
            aspectRatio = 16f / 9f,
        )
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> bytes.toString() + " B"
}

private fun repositoryActionToast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private suspend fun appendTextWithLinkPreview(
    repository: NightRepository,
    linkPreviewService: NightLinkPreviewService,
    chatId: String,
    role: String,
    text: String,
    replyToMessageId: String? = null,
) {
    val preview = linkPreviewService.resolveFromText(text)

    if (preview == null) {
        repository.appendText(
            chatId = chatId,
            role = role,
            text = text,
            replyToMessageId = replyToMessageId,
        )
        return
    }

    val body = text
        .replace(preview.url, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    repository.appendMessage(
        NightMessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            chatId = chatId,
            role = role,
            type = "link",
            text = body,
            createdAt = System.currentTimeMillis(),
            payloadJson = JSONObject()
                .put("url", preview.url)
                .put("title", preview.title)
                .put("description", preview.description)
                .put("site", preview.site)
                .put("imageUrl", preview.imageUrl ?: "")
                .toString(),
            replyToMessageId = replyToMessageId,
        )
    )
}

private suspend fun streamNightAssistantReply(
    repository: NightRepository,
    aiGateway: NightAiGateway,
    chatId: String,
    displayName: String,
    noAiMessage: String,
    failurePrefix: String,
) {
    val messageId = java.util.UUID.randomUUID().toString()
    val createdAt = System.currentTimeMillis()
    val base = NightMessageEntity(
        id = messageId,
        chatId = chatId,
        role = "assistant",
        type = "text",
        text = "…",
        createdAt = createdAt,
        deliveryState = "sending",
    )
    repository.appendMessage(base)

    var lastPersisted = ""
    var lastPersistAt = 0L

    val result = aiGateway.replyStreaming(
        chatId = chatId,
        displayName = displayName,
    ) { partial ->
        val now = System.currentTimeMillis()
        val shouldPersist =
            partial.isBlank() ||
                partial.length - lastPersisted.length >= 12 ||
                now - lastPersistAt >= 90L

        if (shouldPersist) {
            repository.appendMessage(
                base.copy(
                    text = partial.ifBlank { "…" },
                    deliveryState = "sending",
                )
            )
            lastPersisted = partial
            lastPersistAt = now
        }
    }

    val rawReply = result.getOrElse { error ->
        when {
            error.message?.contains("No chat AI") == true -> noAiMessage
            else -> failurePrefix + (error.message ?: "Check the provider settings.")
        }
    }

    val parsed = NightStructuredReplyParser.parse(rawReply)

    parsed.choiceSelection?.let { selection ->
        val existing = repository.getMessage(selection.messageId)
        if (existing != null && existing.chatId == chatId && existing.type == "choice") {
            val payload = runCatching { JSONObject(existing.payloadJson) }
                .getOrElse { JSONObject() }
            val options = payload.optJSONArray("options")
            if (options != null && selection.index in 0 until options.length()) {
                payload
                    .put("selectedIndex", selection.index)
                    .put("selectedBy", "Night")
                repository.appendMessage(existing.copy(payloadJson = payload.toString()))
            }
        }
    }

    if (parsed.text.isBlank()) {
        repository.deleteMessage(messageId)
    } else {
        repository.appendMessage(
            base.copy(
                text = parsed.text,
                deliveryState = "sent",
            )
        )
    }

    parsed.choice?.let { choice ->
        repository.appendMessage(
            NightMessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                type = "choice",
                text = choice.title,
                createdAt = System.currentTimeMillis(),
                payloadJson = JSONObject()
                    .put("options", JSONArray(choice.options))
                    .toString(),
            )
        )
    }
}

private suspend fun appendParsedAssistantReply(
    repository: NightRepository,
    linkPreviewService: NightLinkPreviewService,
    chatId: String,
    rawReply: String,
) {
    val parsed = NightStructuredReplyParser.parse(rawReply)

    parsed.choiceSelection?.let { selection ->
        val existing = repository.getMessage(selection.messageId)
        if (existing != null && existing.chatId == chatId && existing.type == "choice") {
            val payload = runCatching { JSONObject(existing.payloadJson) }
                .getOrElse { JSONObject() }

            val options = payload.optJSONArray("options")
            if (options != null && selection.index in 0 until options.length()) {
                payload
                    .put("selectedIndex", selection.index)
                    .put("selectedBy", "Night")
                repository.appendMessage(
                    existing.copy(payloadJson = payload.toString())
                )
            }
        }
    }

    if (parsed.text.isNotBlank()) {
        appendTextWithLinkPreview(
            repository = repository,
            linkPreviewService = linkPreviewService,
            chatId = chatId,
            role = "assistant",
            text = parsed.text,
        )
    }

    parsed.choice?.let { choice ->
        repository.appendMessage(
            NightMessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                type = "choice",
                text = choice.title,
                createdAt = System.currentTimeMillis(),
                payloadJson = JSONObject()
                    .put("options", JSONArray(choice.options))
                    .toString(),
            )
        )
    }
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
