Warning: truncated output (original token count: 41434)
Total output lines: 4017

package com.example.whatsapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.whatsapp.data.browser.NightBrowserVerification
import com.example.whatsapp.data.scripts.NightScriptRuntime
import com.example.whatsapp.presentation.browser.NightBrowserActivity
import com.example.whatsapp.data.night.NightAgentToolExecutor
import com.example.whatsapp.data.night.NightAiGateway
import com.example.whatsapp.data.night.NightAppearanceController
import com.example.whatsapp.data.night.NightAppearanceEntity
import com.example.whatsapp.data.night.NightAppearanceFontRegistry
import com.example.whatsapp.data.night.NightCapabilityRouteEntity
import com.example.whatsapp.data.night.NightExtensionMessageEmitter
import com.example.whatsapp.data.night.NightLibraryItemEntity
import com.example.whatsapp.data.night.NightLinkPreviewService
import com.example.whatsapp.data.night.NightLiveVoiceClient
import com.example.whatsapp.data.night.NightMediaCollectionStore
import com.example.whatsapp.data.night.NightMediaDownloadQueue
import com.example.whatsapp.data.night.NightMessageEntity
import com.example.whatsapp.data.night.NightProviderManager
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.data.night.NightScheduleManager
import com.example.whatsapp.data.night.NightSpeechService
import com.example.whatsapp.data.night.NightStructuredReplyParser
import com.example.whatsapp.data.night.NightSummaryCoordinator
import com.example.whatsapp.data.night.NightSummaryCheckpointEntity
import com.example.whatsapp.data.night.NightSummaryPolicy
import com.example.whatsapp.data.night.NightToolInvocation
import com.example.whatsapp.data.night.NightVoiceRecorder
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionConfigurationActionCodec
import com.example.whatsapp.extensions.messages.ExtensionMessageCodec
import com.example.whatsapp.extensions.messages.NightExtensionConfigurationStore
import com.example.whatsapp.extensions.messages.NightExtensionMessageActionRegistry
import com.example.whatsapp.extensions.messages.NightExtensionStandardActions
import com.example.whatsapp.extensions.messages.withConfigurationValues
import com.example.whatsapp.extensions.runtime.NightExternalExtensionManager
import com.example.whatsapp.extensions.runtime.NightIntegrationCapability
import com.example.whatsapp.extensions.tools.NightMcpManager
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.chatscreen.AudioPlaybackUiState
import com.example.whatsapp.presentation.chatscreen.ChoiceResultMessage
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.ExtensionResultMessage
import com.example.whatsapp.presentation.chatscreen.MangaResultMessage
import com.example.whatsapp.presentation.chatscreen.LyricsResultMessage
import com.example.whatsapp.presentation.chatscreen.NightBlockMessage
import com.example.whatsapp.presentation.chatscreen.NightMessageBlockCodec
import com.example.whatsapp.presentation.chatscreen.NightRichMessageCodec
import com.example.whatsapp.presentation.chatscreen.NightChatAppearance
import com.example.whatsapp.presentation.chatscreen.NightEmojiRecents
import com.example.whatsapp.presentation.chatscreen.NightChoiceDialog
import com.example.whatsapp.presentation.chatscreen.NightChatMediaItem
import com.example.whatsapp.presentation.chatscreen.NightAudioPickerScreen
import com.example.whatsapp.presentation.chatscreen.NightMediaViewerScreen
import com.example.whatsapp.presentation.chatscreen.NightPdfViewerScreen
import com.example.whatsapp.presentation.chatscreen.NightPdfEditorScreen
import com.example.whatsapp.presentation.chatscreen.NightMediaComposerScreen
import com.example.whatsapp.presentation.chatscreen.ReplyKind
import com.example.whatsapp.presentation.chatscreen.ReplyPreview
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.files.NightFilesTab
import com.example.whatsapp.presentation.files.NightLibraryAudioScreen
import com.example.whatsapp.presentation.files.NightLibraryTextEditorScreen
import com.example.whatsapp.presentation.profile.NightAiSelectorScreen
import com.example.whatsapp.presentation.profile.NightAppearanceScreen
import com.example.whatsapp.presentation.profile.NightCapabilityRoutesScreen
import com.example.whatsapp.presentation.profile.NightChatMemoryScreen
import com.example.whatsapp.presentation.profile.NightChatFilesScreen
import com.example.whatsapp.presentation.profile.NightChatSearchScreen
import com.example.whatsapp.presentation.profile.NightMemoryScreen
import com.example.whatsapp.presentation.profile.NightMediaLibraryScreen
import com.example.whatsapp.presentation.profile.NightExtensionsScreen
import com.example.whatsapp.presentation.profile.NightIntegrationsScreen
import com.example.whatsapp.presentation.profile.NightMcpServersScreen
import com.example.whatsapp.presentation.profile.NightLiveVoiceScreen
import com.example.whatsapp.presentation.profile.NightProfileScreen
import com.example.whatsapp.presentation.profile.NightPrivacyScreen
import com.example.whatsapp.presentation.profile.NightProvidersScreen
import com.example.whatsapp.presentation.profile.NightScheduleDialog
import com.example.whatsapp.presentation.profile.NightScheduledTasksScreen
import com.example.whatsapp.presentation.profile.NightYouTab
import com.example.whatsapp.presentation.reader.mihon.NightMihonArchiveLoader
import com.example.whatsapp.presentation.scripts.NightScriptsScreen
import com.example.whatsapp.presentation.reader.mihon.decodeMihonPages
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.shell.ModernAppScaffold
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
            NightThemeRoot(initialChatId = initialChatId)
        }
    }
}

@Composable
private fun NightThemeRoot(initialChatId: String? = null) {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context) }
    var fontCatalogRevision by remember { mutableIntStateOf(0) }
    val appearance by
        repository.observeAppearance()
            .collectAsState(initial = null)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            NightAppearanceFontRegistry.load(context.applicationContext)
            repository.ensureAppearance()
        }
        fontCatalogRevision += 1
    }

    val current = remember(appearance, fontCatalogRevision) {
        appearance ?: NightAppearanceEntity()
    }
    WhatsappTheme(
        darkTheme = true,
        accentColor = ComposeColor(current.accentColor.toInt()),
    ) {
        NightApp(initialChatId = initialChatId)
    }
}

@Composable
private fun NightApp(initialChatId: String? = null) {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context) }
    val providerManager = remember { NightProviderManager.get(context) }
    val aiGateway = remember { NightAiGateway.get(context) }
    val agentTools = remember { NightAgentToolExecutor.get(context) }
    val scriptRuntime = remember { NightScriptRuntime.get(context) }
    val extensionManager = remember { NightExternalExtensionManager.get(context) }
    val mcpManager = remember { NightMcpManager.get(context) }
    val appearanceController = remember { NightAppearanceController(repository) }
    val voiceRecorder = remember { NightVoiceRecorder(context.applicationContext) }
    val scheduleManager = remember { NightScheduleManager.get(context) }
    val speechService = remember { NightSpeechService.get(context) }
    val liveVoiceClient = remember { NightLiveVoiceClient.get(context) }
    val linkPreviewService = remember { NightLinkPreviewService.get(context) }
    val summaryCoordinator = remember { NightSummaryCoordinator.get(context) }
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
    var recordingChatId by remember { mutableStateOf<String?>(null) }
    var recordingReplyToId by remember { mutableStateOf<String?>(null) }
    var recordingDraftMetadata by remember { mutableStateOf<NightDraftChatMetadata?>(null) }
    var recordingLevels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeAudioPath by remember { mutableStateOf<String?>(null) }
    var audioIsPlaying by remember { mutableStateOf(false) }
    var audioProgress by remember { mutableFloatStateOf(0f) }
    var audioPositionLabel by remember { mutableStateOf("0:00") }
    var scheduleOpen by remember { mutableStateOf(false) }
    var liveVoiceState by remember { mutableStateOf(NightLiveVoiceClient.State.ENDED) }
    var liveVoiceError by remember { mutableStateOf<String?>(null) }
    var liveVoiceMuted by rememberSaveable { mutableStateOf(false) }
    var liveVoiceSpeaker by rememberSaveable { mutableStateOf(false) }
    var replyingToId by rememberSaveable { mutableStateOf<String?>(null) }
    var choiceOpen by remember { mutableStateOf(false) }
    var mediaViewerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteMediaItem by remember { mutableStateOf<NightChatMediaItem?>(null) }
    var pdfSheetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfViewerName by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryTextFile by remember { mutableStateOf<NightLibraryItemEntity?>(null) }
    var libraryAudioFile by remember { mutableStateOf<NightLibraryItemEntity?>(null) }
    var mediaViewerReturnScreen by rememberSaveable { mutableStateOf("chat") }
    var mediaDraft by remember { mutableStateOf<NightMediaDraft?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraChatId by remember { mutableStateOf<String?>(null) }
    var pendingCameraReplyId by remember { mutableStateOf<String?>(null) }
    var pendingCameraMetadata by remember { mutableStateOf<NightDraftChatMetadata?>(null) }
    var mediaCaption by rememberSaveable { mutableStateOf("") }
    var pdfDraft by remember { mutableStateOf<NightPdfDraft?>(null) }
    var pdfCaption by rememberSaveable { mutableStateOf("") }
    var draftChatTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var draftProviderType by rememberSaveable { mutableStateOf<String?>(null) }
    var draftProviderProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var memoryCheckpoints by remember {
        mutableStateOf<List<NightSummaryCheckpointEntity>>(emptyList())
    }
    var summaryRefreshing by remember { mutableStateOf(false) }
    val profileUiPrefs = remember {
        context.getSharedPreferences("night_profile_ui", android.content.Context.MODE_PRIVATE)
    }
    var profileAvatarPath by rememberSaveable {
        mutableStateOf(profileUiPrefs.getString("avatar_path", null))
    }

    var integrationPreferenceRevision by remember { mutableStateOf(0) }

    val chats by repository.observeChats().collectAsState(initial = emptyList())
    val profiles by repository.observeProviderProfiles().collectAsState(initial = emptyList())
    val providerModels by repository.observeAllProviderModels().collectAsState(initial = emptyList())
    val profile by repository.observeProfile().collectAsState(initial = null)
    val appearanceEntity by repository.observeAppearance().collectAsState(initial = null)
    val scheduledTasks by repository.observeScheduledTasks().collectAsState(initial = emptyList())
    val capabilityRoutes by repository.observeCapabilityRoutes().collectAsState(initial = emptyList())
    val mcpServers by mcpManager.states.collectAsState()
    val extensions by extensionManager.extensions.collectAsState()

    val preferredExtensionIds =
        remember(extensions, integrationPreferenceRevision) {
            extensions
                .asSequence()
                .filter { it.enabled }
                .flatMap { it.capabilities.asSequence() }
                .distinct()
                .mapNotNull { capability ->
                    extensionManager.preferredProvider(capability)
                        ?.extensionId
                        ?.let { capability to it }
                }
                .toMap()
        }

    val messageFlow = remember(activeChatId) { repository.observeMessages(activeChatId) }
    val messageEntities by messageFlow.collectAsState(initial = emptyList())

    LaunchedEffect(activeChatId) {
        directImageMode = false
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        if (!isRecording || recordingStartedAt <= 0L) return@LaunchedEffect
        while (isRecording) {
            recordingElapsedMs =
                (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            delay(100L)
        }
    }

    val displayName = profile?.displayName ?: "Dawson"
    val appearance = (appearanceEntity ?: NightAppearanceEntity()).toChatAppearance()
    val activeChat = chats.firstOrNull { it.id == activeChatId }
    val enabledChatProfiles = profiles.filter { it.serviceKind == "chat" && it.isEnabled }
    val selectedProfileId =
        activeChat?.selectedProviderProfileId
            ?: draftProviderProfileId.takeIf { activeChat == null && activeChatId != "night-core" }
    val activeProfile =
        profiles.firstOrNull {
            it.id == selectedProfileId &&
                it.serviceKind == "chat" &&
                it.isEnabled
        }
            ?: enabledChatProfiles.firstOrNull { it.isDefault }
            ?: enabledChatProfiles.singleOrNull()
    val enabledActiveModels = providerModels.filter {
        it.profileId == activeProfile?.id && it.isEnabled
    }
    val selectedModelId =
        activeChat?.selectedModel
            ?: draftModelId.takeIf { activeChat == null && activeChatId != "night-core" }
    val activeModel =
        providerModels.firstOrNull {
            it.id == selectedModelId &&
                it.profileId == activeProfile?.id &&
                it.isEnabled
        }
            ?: enabledActiveModels.firstOrNull { it.isDefault }
            ?: enabledActiveModels.singleOrNull()

    LaunchedEffect(screen, activeChatId, activeChat?.summaryUpdatedAt) {
        if (screen == "chat_memory") {
            memoryCheckpoints = repository.summaryCheckpoints(activeChatId)
        }
    }

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
        summaryCoordinator.checkpoint(
            chatId = chatId,
            displayName = displayName,
        )
    }

    suspend fun commitDraftChatMetadata(chatId: String) {
        val draft = NightDraftChatMetadata(
            providerType = draftProviderType,
            profileId = draftProviderProfileId,
            modelId = draftModelId,
            title = draftChatTitle,
        )
        commitDraftChatMetadataSnapshot(chatId, draft)
    }

    suspend fun commitDraftChatMetadataSnapshot(chatId: String, draft: NightDraftChatMetadata) {
        val profileId = draft.profileId
        val modelId = draft.modelId
        val providerType = draft.providerType
        val title = draft.title
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "New chat" }

        if (!profileId.isNullOrBlank() && !modelId.isNullOrBlank()) {
            repository.setChatModel(
                chatId = chatId,
                provider = providerType,
                profileId = profileId,
                model = modelId,
            )
        }
        title?.let { repository.renameChat(chatId, it) }

        if (
            title != null ||
            !profileId.isNullOrBlank() ||
            !modelId.isNullOrBlank() ||
            !providerType.isNullOrBlank()
        ) {
            if (
                draftChatTitle == draft.title &&
                draftProviderType == draft.providerType &&
                draftProviderProfileId == draft.profileId &&
                draftModelId == draft.modelId
            ) {
                draftChatTitle = null
                draftProviderType = null
                draftProviderProfileId = null
                draftModelId = null
            }
        }
    }

    suspend fun persistExtensionActionResult(
        extensionId: String,
        chatId: String,
        result: JSONObject?,
    ) {
        if (result == null) return
        NightExtensionMessageEmitter.persistFromToolResult(
            repository = repository,
            chatId = chatId,
            ownerExtensionId = extensionId,
            result = result,
        )
    }

    LaunchedEffect(Unit) {
        repository.ensureProfile()
        repository.ensureAppearance()
        runCatching { providerManager.ensureProviderOnboardingDefaults() }
        runCatching { extensionManager.refreshInstalledExtensions() }
        runCatching { mcpManager.refresh() }
        val root = repository.ensureChat("night-core", "Night")
        if (repository.getMessages(root.id).isEmpty()) {
            repository.appendText(
                chatId = root.id,
                role = "assistant",
                text = "Night is ready. Choose an AI when you want a live model, or keep using Night Core for local actions.",
            )
        }
    }

    DisposableEffect(extensionManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: android.content.Context?,
                intent: Intent?,
            ) {
                scope.launch {
                    runCatching {
                        extensionManager.refreshInstalledExtensions()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        onDispose {
            runCatching {
                context.unregisterReceiver(receiver)
            }
        }
    }

    LaunchedEffect(screen, activeChatId) {
        if (screen != "chat") return@LaunchedEffect
        while (true) {
            delay(NightSummaryPolicy.CHECKPOINT_INTERVAL_MS)
            if (screen == "chat" && repository.unsummarizedMessages(activeChatId).isNotEmpty()) {
                checkpoint(activeChatId)
            }
        }
    }

    suspend fun persistVoiceRecording() {
        val chatId = recordingChatId ?: activeChatId
        val replyId = recordingReplyToId
        val draftMetadata = recordingDraftMetadata
        val recorded = voiceRecorder.stop()
        isRecording = false
        recordingChatId = null
        recordingReplyToId = null
        recordingDraftMetadata = null
        recordingStartedAt = 0L
        recordingElapsedMs = 0L
        recordingLevels = emptyList()
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
                sourceChatId = chatId,
                sourceMessageId = messageId,
            )
        )
        val waveformJson = JSONArray().apply {
            recorded.waveform.forEach { put(it.toDouble()) }
        }
        val initialPayload = JSONObject()
            .put("localPath", saved.localPath)
            .put("duration", formatDuration(recorded.durationMs))
            .put("durationMs", recorded.durationMs)
            .put("waveform", waveformJson)

        val voiceMessage = NightMessageEntity(
            id = messageId,
            chatId = chatId,
            role = "user",
            type = "voice",
            text = "",
            createdAt = System.currentTimeMillis(),
            libraryFileId = saved.id,
            payloadJson = initialPayload.toString(),
            replyToMessageId = replyId,
        )
        repository.appendMessage(voiceMessage)
        if (draftMetadata != null) {
            commitDraftChatMetadataSnapshot(chatId, draftMetadata)
        } else {
            commitDraftChatMetadata(chatId)
        }
        if (replyingToId == replyId) replyingToId = null

        val transcription = speechService.transcribe(saved.localPath)
        val transcript = transcription.getOrNull()?.trim()
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
                chatId = chatId,
                displayName = displayName,
                noAiMessage = "I transcribed the voice note, but no AI is selected for this chat yet.",
                failurePrefix = "I transcribed the voice note, but I couldn't reach an available AI. ",
            )
        } else {
            val detail = transcription.exceptionOrNull()?.message
                ?.takeIf { it.isNotBlank() }
            Toast.makeText(
                context,
                if (detail == null) {
                    "Voice note saved and still playable, but no transcript was returned."
                } else {
                    "Voice note saved and still playable, but transcription failed: " + detail
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun startVoiceRecording() {
        runCatching {
            recordingChatId = activeChatId
            recordingReplyToId = replyingToId
            recordingDraftMetadata = NightDraftChatMetadata(
                providerType = draftProviderType,
                profileId = draftProviderProfileId,
                modelId = draftModelId,
                title = draftChatTitle,
            )
            recordingLevels = emptyList()
            recordingElapsedMs = 0L
            recordingStartedAt = System.currentTimeMillis()
            voiceRecorder.start { level ->
                scope.launch(Dispatchers.Main) {
                    if (isRecording) {
                        recordingLevels = (recordingLevels + level).takeLast(48)
                    }
                }
            }
            isRecording = true
        }.onFailure {
            recordingChatId = null
            recordingReplyToId = null
            recordingDraftMetadata = null
            recordingStartedAt = 0L
            recordingElapsedMs = 0L
            recordingLevels = emptyList()
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
        liveVoiceMuted = false
        liveVoiceSpeaker = false
        liveVoiceClient.setMicrophoneMuted(false)
        liveVoiceClient.setSpeakerEnabled(false)
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

    fun importAttachmentUri(uri: Uri) {
        val chatId = activeChatId
        val replyId = replyingToId
        val draftMetadata = NightDraftChatMetadata(
            providerType = draftProviderType,
            profileId = draftProviderProfileId,
            modelId = draftModelId,
            title = draftChatTitle,
        )
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                NightFileLibrary.importUri(context, uri)
            } ?: return@launch

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
                    chatId = chatId,
                    draftMetadata = draftMetadata,
                )
                mediaCaption = ""
                if (replyingToId == replyId) replyingToId = null
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
                    chatId = chatId,
                    draftMetadata = draftMetadata,
                )
                pdfCaption = ""
                if (replyingToId == replyId) replyingToId = null
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
                    sourceChatId = chatId,
                    sourceMessageId = messageId,
                )
            )

            repository.appendMessage(
                NightMessageEntity(
                    id = messageId,
                    chatId = chatId,
                    role = "user",
                    type = messageType,
                    text = messageTextValue,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = saved.id,
                    payloadJson = payload.toString(),
                    replyToMessageId = replyId,
                )
            )
            commitDraftChatMetadataSnapshot(chatId, draftMetadata)
            if (replyingToId == replyId) replyingToId = null
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importAttachmentUri)
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(::importAttachmentUri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val source = pendingCameraFile
        pendingCameraFile = null
        if (!captured || source == null || !source.isFile || source.length() <= 0L) {
            source?.delete()
            pendingCameraChatId = null
            pendingCameraReplyId = null
            pendingCameraMetadata = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                NightFileLibrary.registerLocalFile(
                    context = context,
                    source = source,
                    name = "Photo " +
                        SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date()) +
                        ".jpg",
                    mimeType = "image/jpeg",
                )
            }
            source.delete()
            if (saved == null) return@launch

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
                replyToMessageId = pendingCameraReplyId,
                chatId = pendingCameraChatId.orEmpty(),
                draftMetadata = pendingCameraMetadata,
            )
            mediaCaption = ""
            if (replyingToId == pendingCameraReplyId) replyingToId = null
            pendingCameraChatId = null
            pendingCameraReplyId = null
            pendingCameraMetadata = null
            screen = "media_compose"
        }
    }

    fun launchCameraCapture() {
        val source = File(
            context.cacheDir,
            "night_camera_" + System.currentTimeMillis() + ".jpg",
        )
        pendingCameraFile?.delete()
        pendingCameraFile = source
        pendingCameraChatId = activeChatId
        pendingCameraReplyId = replyingToId
        pendingCameraMetadata = NightDraftChatMetadata(
            providerType = draftProviderType,
            profileId = draftProviderProfileId,
            modelId = draftModelId,
            title = draftChatTitle,
        )
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".files",
            source,
        )
        cameraLauncher.launch(uri)
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
        val chatId = draft.chatId.ifBlank { activeChatId }
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
            draft.draftMetadata?.let { commitDraftChatMetadataSnapshot(chatId, it) }
                ?: commitDraftChatMetadata(chatId)

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

    fun cancelPdfDraft() {
        val draft = pdfDraft ?: return
        NightFileLibrary.remove(context, draft.libraryId)
        pdfDraft = null
        pdfCaption = ""
        screen = "chat"
    }

    fun sendPdfDraft(
        preparedPath: String,
        preparedName: String,
    ) {
        val draft = pdfDraft ?: return
        val chatId = draft.chatId.ifBlank { activeChatId }
        val caption = pdfCaption.trim()
        val messageId = java.util.UUID.randomUUID().toString()

        scope.launch {
            val preparedFile = File(preparedPath)
            if (!preparedFile.isFile || preparedFile.length() <= 0L) {
                Toast.makeText(context, "The edited PDF is not available.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val finalFile = if (preparedFile.absolutePath == draft.localPath) {
                preparedFile
            } else {
                preparedFile.copyTo(File(draft.localPath), overwrite = true)
            }
            val finalName = preparedName.ifBlank { draft.name }

            val payload = JSONObject()
                .put("localPath", finalFile.absolutePath)
                .put("mimeType", "application/pdf")
                .put("sizeBytes", finalFile.length())
                .put("displayName", finalName)
                .put("caption", caption)

            repository.addLibraryItem(
                NightLibraryItemEntity(
                    id = draft.libraryId,
                    name = finalName,
                    mimeType = "application/pdf",
                    sizeBytes = finalFile.length(),
                    localPath = finalFile.absolutePath,
                    createdAt = draft.createdAt,
                    sourceChatId = chatId,
                    sourceMessageId = messageId,
                )
            )

            repository.appendMessage(
                NightMessageEntity(
                    id = messageId,
                    chatId = chatId,
                    role = "user",
                    type = "file",
                    text = finalName,
                    createdAt = System.currentTimeMillis(),
                    libraryFileId = draft.libraryId,
                    payloadJson = payload.toString(),
                    replyToMessageId = draft.replyToMessageId,
                )
            )
            draft.draftMetadata?.let { commitDraftChatMetadataSnapshot(chatId, it) }
                ?: commitDraftChatMetadata(chatId)

            pdfDraft = null
            pdfCaption = ""
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
                audioProgress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)…16434 tokens truncated…                   spec = browser,
                                        outcome = outcome,
                                    )
                                val verifiedSnapshot = snapshot.copy(
                                    status = if (outcome.verified) {
                                        "Verified"
                                    } else {
                                        "Verification failed"
                                    },
                                    browser = verifiedBrowser,
                                )
                                repository.replaceMessage(
                                    existing.copy(
                                        payloadJson =
                                            ExtensionMessageCodec.encode(verifiedSnapshot)
                                    )
                                )
                                return@launch
                            }

                            val actionResult =
                                NightExtensionMessageActionRegistry.execute(
                                    extensionId = snapshot.extensionId,
                                    chatId = existing.chatId,
                                    messageId = messageId,
                                    messageType = snapshot.messageType,
                                    actionId = actionId,
                                    payload = runCatching {
                                        JSONObject(snapshot.extensionPayloadJson)
                                    }.getOrElse { JSONObject() },
                                )
                            persistExtensionActionResult(
                                extensionId = snapshot.extensionId,
                                chatId = existing.chatId,
                                result = actionResult,
                            )
                        }
                    }
                }
            },
            onAttachmentClick = {},
            onAttachmentAction = { action ->
                directImageMode = false
                when (action) {
                    "Gallery" -> galleryPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                    "Document" -> attachmentPicker.launch(arrayOf("*/*"))
                    "Audio" -> screen = "audio_picker"
                    "Camera" -> launchCameraCapture()
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
                mediaViewerReturnScreen = "chat"
                screen = "media_viewer"
            },
            onVideoClick = { path ->
                mediaViewerPath = path
                mediaViewerReturnScreen = "chat"
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
                        val messageFile = visualMessages
                            .filterIsInstance<WhatsAppVisualMessage.FileMessage>()
                            .firstOrNull { it.localPath == path }
                        pdfViewerName = messageFile?.name ?: File(path).name
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
            onCameraClick = { launchCameraCapture() },
            isRecording = isRecording,
            recordingLevels = recordingLevels,
            recordingDuration = formatDuration(recordingElapsedMs),
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
                    draftChatTitle = null
                    draftProviderType = null
                    draftProviderProfileId = null
                    draftModelId = null
                    messageText = ""
                    screen = "chat"
                },
                onRenameChat = { row ->
                    activeChatId = row.userId ?: "night-core"
                    renameValue = row.name.orEmpty()
                    renameOpen = true
                },
                onNewChat = {
                    activeChatId = java.util.UUID.randomUUID().toString()
                    draftChatTitle = null
                    draftProviderType = null
                    draftProviderProfileId = null
                    draftModelId = null
                    messageText = ""
                    replyingToId = null
                    directImageMode = false
                    screen = "chat"
                },
                onSettingsClick = {
                    selectedTabName = MainTab.You.name
                    screen = "tabs"
                },
                accentColor = appearance.accentColor,
            )

            MainTab.Updates -> NightFilesTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                accentColor = appearance.accentColor,
                onFileOpen = { file ->
                    val mime = file.mimeType.lowercase()
                    val name = file.name.lowercase()
                    when {
                        mime.startsWith("image/") || mime.startsWith("video/") -> {
                            remoteMediaItem = NightChatMediaItem(
                                id = "library:" + file.id,
                                localPath = file.localPath,
                                mimeType = file.mimeType,
                                caption = file.name,
                                sender = "Library",
                            )
                            mediaViewerPath = file.localPath
                            mediaViewerReturnScreen = "tabs"
                            screen = "media_viewer"
                        }
                        mime.contains("pdf") || name.endsWith(".pdf") -> {
                            pdfSheetPath = file.localPath
                            pdfViewerName = file.name
                        }
                        mime.startsWith("audio/") -> {
                            libraryAudioFile = file
                            screen = "library_audio"
                        }
                        mime.startsWith("text/") ||
                            name.endsWith(".txt") ||
                            name.endsWith(".md") ||
                            name.endsWith(".js") ||
                            name.endsWith(".jsx") ||
                            name.endsWith(".css") ||
                            name.endsWith(".html") ||
                            name.endsWith(".json") ||
                            name.endsWith(".kt") ||
                            name.endsWith(".java") -> {
                            libraryTextFile = file
                            screen = "library_text"
                        }
                        else -> {
                            val source = File(file.localPath)
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".files",
                                    source,
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, file.mimeType)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "No viewer is available for this file type.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )

            MainTab.Scripts -> ModernAppScaffold(
                selectedTab = MainTab.Scripts,
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                title = "Scripts & Projects",
                showCamera = false,
                showSearch = false,
                showMenu = false,
                accentColor = appearance.accentColor,
            ) { _ ->
                NightScriptsScreen(
                    onBack = {
                        selectedTabName = MainTab.Chats.name
                        screen = "tabs"
                    },
                    tabMode = true,
                )
            }

            MainTab.You -> NightYouTab(
                displayName = displayName,
                avatarPath = profileAvatarPath,
                accentColor = appearance.accentColor,
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onProfileClick = { screen = "profile" },
                onProvidersClick = { screen = "providers" },
                onMemoryClick = { screen = "memory" },
                onSchedulesClick = { screen = "scheduled_tasks" },
                onMediaLibraryClick = {
                    screen = "media_library"
                },
                onBrowserClick = {
                    context.startActivity(
                        NightBrowserActivity.createGeneralIntent(context)
                    )
                },
                onAppearanceClick = { screen = "appearance" },
                onPrivacyClick = { screen = "privacy" },
            )

            else -> {
                selectedTabName = MainTab.Chats.name
            }
        }
    }


    pdfSheetPath?.let { pdfPath ->
        NightPdfViewerScreen(
            localPath = pdfPath,
            displayName = pdfViewerName,
            onBack = {
                pdfSheetPath = null
                pdfViewerName = null
            },
            onEdit = {
                val source = File(pdfPath)
                if (!source.isFile) {
                    Toast.makeText(context, "This PDF is not available locally.", Toast.LENGTH_SHORT).show()
                } else {
                    val sourceMessage = visualMessages
                        .filterIsInstance<WhatsAppVisualMessage.FileMessage>()
                        .firstOrNull { it.localPath == pdfPath }
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            NightFileLibrary.registerLocalFile(
                                context = context,
                                source = source,
                                name = "Edited " + (pdfViewerName ?: sourceMessage?.name ?: source.name),
                                mimeType = "application/pdf",
                            )
                        }
                        if (saved != null) {
                            pdfDraft = NightPdfDraft(
                                libraryId = saved.id,
                                name = saved.name,
                                localPath = saved.localPath,
                                createdAt = saved.createdAt,
                                replyToMessageId = null,
                                chatId = activeChatId,
                                draftMetadata = NightDraftChatMetadata(
                                    providerType = draftProviderType,
                                    profileId = draftProviderProfileId,
                                    modelId = draftModelId,
                                    title = draftChatTitle,
                                ),
                            )
                            pdfCaption = sourceMessage?.caption.orEmpty()
                            pdfSheetPath = null
                            pdfViewerName = null
                            screen = "pdf_compose"
                        }
                    }
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancel()
            liveVoiceClient.stop()
            mcpManager.disconnectAll()
            activePlayer?.release()
        }
    }

    if (choiceOpen) {
        NightChoiceDialog(
            onDismiss = { choiceOpen = false },
            onCreate = { title, options, multiple ->
                val chatId = activeChatId
                val replyId = replyingToId
                val draftMetadata = NightDraftChatMetadata(
                    providerType = draftProviderType,
                    profileId = draftProviderProfileId,
                    modelId = draftModelId,
                    title = draftChatTitle,
                )
                scope.launch {
                    val messageId = java.util.UUID.randomUUID().toString()
                    val payload = JSONObject()
                        .put("options", JSONArray(options))
                        .put("multiple", multiple)
                    repository.appendMessage(
                        NightMessageEntity(
                            id = messageId,
                            chatId = chatId,
                            role = "user",
                            type = "choice",
                            text = title,
                            createdAt = System.currentTimeMillis(),
                            payloadJson = payload.toString(),
                            replyToMessageId = replyId,
                        )
                    )
                    commitDraftChatMetadataSnapshot(chatId, draftMetadata)
                    if (replyingToId == replyId) replyingToId = null
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
                            if (chats.any { it.id == activeChatId }) {
                                scope.launch { repository.renameChat(activeChatId, value) }
                            } else if (activeChatId != "night-core") {
                                draftChatTitle = value
                            }
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
            name = payload?.optString("displayName")?.takeIf { it.isNotBlank() } ?: text,
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
            caption = payload?.optString("caption").orEmpty(),
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

        "voice" -> {
            val waveform = buildList {
                payload?.optJSONArray("waveform")?.let { raw ->
                    for (index in 0 until raw.length()) {
                        add(raw.optDouble(index, 0.03).toFloat().coerceIn(0.03f, 1f))
                    }
                }
            }
            WhatsAppVisualMessage.VoiceMessage(
                id = id,
                duration = payload?.optString("duration").orEmpty().ifBlank { "0:00" },
                time = time,
                mine = mine,
                read = mine,
                localPath = payload?.optString("localPath")?.takeIf { it.isNotBlank() },
                transcript = payload?.optString("transcript")?.takeIf { it.isNotBlank() },
                waveform = waveform,
                reply = reply,
            )
        }

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

            val selectedIndices = buildSet {
                payload?.optJSONArray("selectedIndices")?.let { raw ->
                    for (position in 0 until raw.length()) {
                        val selected = raw.optInt(position, -1)
                        if (selected >= 0) add(selected)
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
                selectedIndices = selectedIndices,
                multiple = payload?.optBoolean("multiple", false) ?: false,
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

        "lyrics" -> LyricsResultMessage(
            id = id,
            title = payload?.optString("title").orEmpty().ifBlank {
                text.ifBlank { "Lyrics" }
            },
            artist = payload?.optString("artist").orEmpty(),
            lyrics = payload?.optString("lyrics").orEmpty().ifBlank { text },
            source = payload?.optString("source").orEmpty(),
            time = time,
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

        "blocks" -> {
            val blocks = NightMessageBlockCodec.decode(payloadJson)
            if (blocks.isNotEmpty()) {
                NightBlockMessage(
                    id = id,
                    blocks = blocks,
                    time = time,
                    mine = mine,
                    read = mine,
                    reply = reply,
                )
            } else {
                WhatsAppVisualMessage.TextMessage(
                    id = id,
                    text = text.ifBlank { "Structured message" },
                    time = time,
                    mine = mine,
                    read = mine,
                    reply = reply,
                )
            }
        }

        else -> NightRichMessageCodec.decode(
            type = type,
            id = id,
            text = text,
            payloadJson = payloadJson,
            time = time,
            mine = mine,
        ) ?: WhatsAppVisualMessage.TextMessage(
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

        "lyrics" -> ReplyPreview(
            messageId = id,
            author = author,
            text = payload?.optString("title").orEmpty().ifBlank { text.ifBlank { "Lyrics" } },
            kind = ReplyKind.Rich,
            meta = payload?.optString("artist")?.takeIf { it.isNotBlank() },
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

        "blocks" -> {
            val blocks = NightMessageBlockCodec.decode(payloadJson)
            ReplyPreview(
                messageId = id,
                author = author,
                text = NightMessageBlockCodec.previewText(blocks),
                kind = ReplyKind.Rich,
                meta = "Structured message",
            )
        }

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

        else -> {
            val rich = NightRichMessageCodec.decode(
                type = type,
                id = id,
                text = text,
                payloadJson = payloadJson,
                time = nightTime(createdAt),
                mine = role == "user",
            )
            if (rich != null) {
                ReplyPreview(
                    messageId = id,
                    author = author,
                    text = NightRichMessageCodec.previewText(rich),
                    kind = ReplyKind.Rich,
                    meta = type.replace('_', ' ').replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    },
                )
            } else {
                ReplyPreview(
                    messageId = id,
                    author = author,
                    text = text,
                    kind = ReplyKind.Text,
                )
            }
        }
    }
}

private fun nightTime(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatChatListTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else nightTime(timestamp)

private data class NightDraftChatMetadata(
    val providerType: String?,
    val profileId: String?,
    val modelId: String?,
    val title: String?,
)

private data class NightPdfDraft(
    val libraryId: String,
    val name: String,
    val localPath: String,
    val createdAt: Long,
    val replyToMessageId: String?,
    val chatId: String = "",
    val draftMetadata: NightDraftChatMetadata? = null,
)

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
    val chatId: String = "",
    val draftMetadata: NightDraftChatMetadata? = null,
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
): NightMessageEntity {
    val persisted = repository.appendText(
        chatId = chatId,
        role = role,
        text = text,
        replyToMessageId = replyToMessageId,
    )

    val preview = runCatching {
        linkPreviewService.resolveFromText(text)
    }.getOrNull() ?: return persisted

    val body = text
        .replace(preview.url, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    val enriched = persisted.copy(
        type = "link",
        text = body,
        payloadJson = JSONObject()
            .put("url", preview.url)
            .put("title", preview.title)
            .put("description", preview.description)
            .put("site", preview.site)
            .put("imageUrl", preview.imageUrl ?: "")
            .toString(),
    )
    repository.replaceMessage(enriched)
    return enriched
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
        repository.finishStreamingMessage(
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
