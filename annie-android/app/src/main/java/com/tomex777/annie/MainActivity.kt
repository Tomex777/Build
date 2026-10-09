package com.tomex777.annie

import androidx.compose.ui.semantics.disabled

import androidx.compose.ui.semantics.setProgress

import androidx.compose.ui.semantics.progressBarRangeInfo

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.content.ComponentName
import android.content.ServiceConnection
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognizerIntent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF07111E)
private val Panel = Color(0xFF0D1B2C)
private val Bubble = Color(0xFF13243A)
private val Blue = Color(0xFF168EEA)
private val SoftText = Color(0xFF9CB2CC)
private val BrightText = Color(0xFFEEF5FF)
private val Teal = Color(0xFF54D6AE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { AnnieTheme { AnnieChat() } }
    }

    override fun onResume() {
        super.onResume()
        AnnieForegroundGate.onMainActivityResumed()
    }

    override fun onPause() {
        AnnieForegroundGate.onMainActivityPaused()
        super.onPause()
    }
}

@Composable
internal fun AnnieTheme(content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        var owner = view.context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        (owner as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowInsetsControllerCompat(activity.window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF36A8F4),
            onPrimary = BrightText,
            background = Night,
            surface = Panel,
            onSurface = BrightText,
            secondary = Teal
        ),
        content = content
    )
}

internal data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val catalog: List<CatalogItem> = emptyList(),
    val menuTitle: String? = null,
    val actions: List<String> = emptyList(),
    val searchMedia: String? = null,
    val searchInitial: String = "",
    val selectedItem: CatalogItem? = null,
    val selectedStage: String? = null,
    val scriptMessageJson: String? = null,
    val scriptId: String? = null,
    val scriptCommandName: String? = null,
    val voiceNotePath: String? = null,
    val voiceNoteDurationMs: Long = 0L,
    val scriptHandleId: String? = null,
    val scriptHandleCreatedAt: Long = 0L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AnnieChat() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val chatView = androidx.compose.ui.platform.LocalView.current
    val scriptWorkspace = remember(context) { ScriptWorkspace(context) }
    var scriptCommands by remember { mutableStateOf<List<ScriptCommand>>(emptyList()) }
    var commandUsage by remember(context) { mutableStateOf(CommandUsageStore.read(context)) }
    LaunchedEffect(scriptWorkspace) { scriptCommands = scriptWorkspace.reload() }
    androidx.compose.runtime.DisposableEffect(scriptWorkspace) {
        onDispose { scriptWorkspace.close() }
    }
    val chats = remember {
        mutableStateListOf<ChatSession>().apply {
            addAll(ChatHistoryStore.read(context))
            if (isEmpty()) add(newWelcomeChat())
        }
    }
    var activeChatId by remember { mutableStateOf(chats.first().id) }
    val activeChat = chats.firstOrNull { it.id == activeChatId } ?: chats.first()
    val character = AnnieCharacters.byId(activeChat.characterId)
    val messages = activeChat.messages
    val activeScriptId = scriptWorkspace.activeScriptId(activeChatId)
    val activeScriptCommand = scriptCommands.firstOrNull { it.scriptId == activeScriptId }
    val latestMediaType = messages.asReversed().firstNotNullOfOrNull { entry ->
        entry.searchMedia ?: entry.selectedItem?.mediaType?.lowercase()
    }
    val conversationContext = ConversationContext(
        activeScriptId = activeScriptId,
        activeCommand = activeScriptCommand?.name,
        mediaType = latestMediaType,
        capabilities = activeScriptCommand?.capabilities?.toSet().orEmpty(),
        suggestedActions = activeScriptCommand?.suggestedActions.orEmpty(),
    )
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var lastSentMessageId by remember { mutableStateOf<Long?>(null) }
    val animatedMessageIds = remember { mutableStateMapOf<Long, Boolean>() }
    var activeSheet by remember { mutableStateOf<String?>(null) }
    var navigationDrawerOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = navigationDrawerOpen) { navigationDrawerOpen = false }
    var scriptStudioProjectId by remember { mutableStateOf<String?>(null) }
    var scriptStudioOpenEnvironment by remember { mutableStateOf(false) }
    var scriptStudioOpenPackageImport by remember { mutableStateOf(false) }
    var scriptStudioImportFile by remember { mutableStateOf<File?>(null) }
    androidx.compose.runtime.DisposableEffect(navigationDrawerOpen, chatView) {
        var owner = chatView.context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        val window = (owner as? android.app.Activity)?.window
        val imeFlag = android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        val alreadyExcluded = ((window?.attributes?.flags ?: 0) and imeFlag) != 0
        val ownsImeFlag = navigationDrawerOpen && !alreadyExcluded
        if (navigationDrawerOpen) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            window?.let {
                androidx.core.view.WindowInsetsControllerCompat(it, chatView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                // API 26 can restore an external activity's IME after a hide
                // request. The drawer has no editor: exclude this window from
                // IME targeting for its entire lifetime, then restore typing.
                it.addFlags(imeFlag)
            }
        }
        onDispose {
            if (ownsImeFlag) window?.clearFlags(imeFlag)
        }
    }
    val listState = remember(activeChatId) { LazyListState() }
    val scope = rememberCoroutineScope()
    var pendingMangaItem by remember { mutableStateOf<CatalogItem?>(null) }
    var activeMangaReader by remember { mutableStateOf<Pair<CatalogItem, File>?>(null) }
    LaunchedEffect(Unit) { ChatHistoryStore.write(context, chats) }
    LaunchedEffect(activeChatId) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** Merges messages appended or updated outside the UI (handles, background workers) into the live lists. */
    fun mergeExternalChanges(onlyChatId: String? = null): Int {
        var added = 0
        ChatHistoryStore.read(context).forEach { stored ->
            if (onlyChatId != null && stored.id != onlyChatId) return@forEach
            val live = chats.firstOrNull { it.id == stored.id } ?: return@forEach
            stored.messages.forEach { entry ->
                val index = live.messages.indexOfFirst { it.id == entry.id }
                if (index < 0) {
                    animatedMessageIds[entry.id] = true
                    live.messages.add(entry)
                    added++
                } else {
                    val existing = live.messages[index]
                    if (entry.scriptHandleId != null &&
                        (existing.scriptMessageJson != entry.scriptMessageJson || existing.text != entry.text)
                    ) live.messages[index] = entry
                }
            }
        }
        return added
    }

    fun persistHistory() {
        mergeExternalChanges()
        val activeIndex = chats.indexOfFirst { it.id == activeChatId }
        if (activeIndex > 0) chats.add(0, chats.removeAt(activeIndex))
        ChatHistoryStore.write(context, chats)
    }

    LaunchedEffect(Unit) {
        ChatHistoryStore.changes.collect { changedChatId ->
            val added = mergeExternalChanges(changedChatId)
            if (added > 0 && changedChatId == activeChatId && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    fun addAnnie(
        text: String,
        catalog: List<CatalogItem> = emptyList(),
        menuTitle: String? = null,
        actions: List<String> = emptyList(),
        searchMedia: String? = null,
        searchInitial: String = "",
        selectedItem: CatalogItem? = null,
        selectedStage: String? = null,
        scriptMessageJson: String? = null,
        scriptId: String? = null,
        scriptCommandName: String? = null,
    ) {
        val entry = ChatEntry(System.nanoTime(), false, text, catalog, menuTitle, actions, searchMedia, searchInitial, selectedItem, selectedStage, scriptMessageJson, scriptId, scriptCommandName)
        animatedMessageIds[entry.id] = true
        messages.add(entry)
        persistHistory()
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    val mangaArchivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val item = pendingMangaItem
        pendingMangaItem = null
        if (uri != null && item != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { AnnieMangaArchive.copyAndValidate(context, uri, item) }
                }.onSuccess { archive -> activeMangaReader = item to archive }
                    .onFailure { error -> addAnnie("Could not open manga archive · ${error.message ?: "invalid CBZ/ZIP"}") }
            }
        }
    }

    fun openMangaReader(item: CatalogItem) {
        val existing = AnnieMangaArchive.existing(context, item)
        if (existing.isFile) activeMangaReader = item to existing
        else {
            pendingMangaItem = item
            mangaArchivePicker.launch(arrayOf("application/zip", "application/vnd.comicbook+zip", "*/*"))
        }
    }

    fun openSavedManga() {
        val saved = AnnieMangaArchive.savedItems(context)
        when (saved.size) {
            0 -> addAnnie("No saved manga yet.", menuTitle = "Continue reading")
            1 -> openMangaReader(saved.single())
            else -> addAnnie("Choose a manga.", menuTitle = "Saved manga", actions = saved.map { it.title })
        }
    }

    fun openContinueWatching(mediaTypes: Set<String>? = null) {
        val entries = WatchHistoryStore.continueWatching(context, mediaTypes).take(8)
        if (entries.isEmpty()) {
            addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
        } else {
            addAnnie(
                "Pick up where you left off.",
                menuTitle = "Continue watching",
                actions = entries.map(WatchHistoryStore::actionLabel),
            )
        }
    }

    val downloads = remember {
        mutableStateListOf<DownloadItem>().apply {
            addAll(DownloadStore.read(context))
        }
    }
    LaunchedEffect(context.applicationContext) {
        DownloadTransferService.restore(context)
        while (true) {
            val latest = DownloadStore.read(context)
            if (latest != downloads.toList()) {
                downloads.clear()
                downloads.addAll(latest)
            }
            delay(500)
        }
    }

    var exportDownloadItem by remember { mutableStateOf<DownloadItem?>(null) }
    val exportDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = exportDownloadItem
        exportDownloadItem = null
        if (uri != null && item != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        File(item.localPath).inputStream().use { it.copyTo(output) }
                    } ?: error("Destination unavailable")
                }.isSuccess
            }
            android.widget.Toast.makeText(context, if (saved) "File saved" else "Unable to save this file.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun queueScriptVideoDownload(data: org.json.JSONObject, scriptId: String?) {
        val source = ScriptVideoDownloadSource.from(data)
        if (source == null) {
            addAnnie("No downloadable file was provided.")
            return
        }
        val isFile = data.optString("type").equals("file", true)
        val packageProject = scriptId?.let { id -> scriptWorkspace.files.listProjects().firstOrNull { it.id == id } }
        if (packageProject != null && (!packageProject.enabled || packageProject.hasPackageManifest &&
                source.url.startsWith("http", true) &&
                (NETWORK_ACCESS_CAPABILITY !in packageProject.manifest.capabilities ||
                    NETWORK_ACCESS_PERMISSION !in scriptWorkspace.files.grantedPermissions(packageProject.id)))) {
            addAnnie("Enable this package and allow network access in Extensions before downloading.")
            return
        }
        val title = data.optString("title").ifBlank { source.filename ?: if (isFile) "File" else "Video" }
        val mediaType = data.optString("mediaType").ifBlank { data.optString("type") }.uppercase()
        val kind = if (isFile) DownloadMediaKind.FILE else when (mediaType) {
            "ANIME" -> DownloadMediaKind.ANIME
            "TV", "SERIES" -> DownloadMediaKind.TV
            "MUSIC" -> DownloadMediaKind.MUSIC
            else -> DownloadMediaKind.MOVIE
        }
        val sourceId = data.optString("sourceId")
            .ifBlank { scriptId.orEmpty() }
            .ifBlank { "script" }
        val item = DownloadItem(
            id = "script-video-" + System.nanoTime(),
            canonicalTitleId = data.optString("canonicalTitleId")
                .ifBlank { sourceId + ":" + title.lowercase() },
            sourceId = sourceId,
            sourceName = data.optString("sourceName").ifBlank { scriptId ?: "Script" },
            kind = kind,
            title = title,
            artworkUrl = data.optString("thumbnail"),
            unitTitle = data.optString("episodeTitle").ifBlank { title },
            unitNumber = data.optString("episodeNumber"),
            state = DownloadState.QUEUED,
            quality = source.quality,
            sourceUrl = source.url,
            headersJson = org.json.JSONObject(source.headers).toString(),
            sourceMimeType = source.mimeType,
            browserSessionId = source.browserSessionId,
            filename = source.filename,
            ownerScriptId = scriptId,
            refreshAction = data.optString("refreshAction").takeIf(String::isNotBlank),
            refreshPayloadJson = data.optJSONObject("refreshPayload")?.toString() ?: "{}",
        )
        downloads.add(item)
        DownloadTransferService.enqueue(context, item)
        addAnnie(
            buildString {
                append("Downloading ").append(title)
                source.quality.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
        )
    }

    fun openDownloads(mediaFilter: String = "All") {
        downloads.clear()
        downloads.addAll(DownloadStore.read(context))
        activeSheet = "Downloads:$mediaFilter"
    }

    fun openSelectedTitle(item: CatalogItem) {
        selectedDetailsStage(item)?.let { addAnnie("", selectedItem = item, selectedStage = it) }
    }

    fun handleMenuAction(category: String, action: String) {
        when (category) {
            "Continue watching" -> {
                val entry = WatchHistoryStore.continueWatching(context)
                    .firstOrNull { WatchHistoryStore.actionLabel(it) == action }
                if (entry == null) {
                    addAnnie("That playback entry is no longer available.")
                } else {
                    launchPlayer(
                        context = context,
                        item = entry.catalogItem(),
                        mediaUri = entry.mediaUri,
                        mode = entry.playerMode(),
                        videoConfigJson = entry.videoConfigJson,
                    )
                }
            }
            "Saved manga" -> {
                val item = AnnieMangaArchive.savedItems(context).firstOrNull { it.title == action }
                if (item == null) addAnnie("That saved manga is no longer available.")
                else openMangaReader(item)
            }
        }
    }

    fun addScriptResult(resultJson: String?, scriptId: String, channel: String) {
        val result = resultJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        if (result == null) {
            addAnnie("The script returned a result Annie could not read.")
        } else if (result.optString("type") == "error") {
            addAnnie("Script error\n${result.optString("text", "Script failed").take(300)}")
        } else if (result.optString("type") == "text") {
            addAnnie(
                result.optString("text"),
                scriptMessageJson = resultJson,
                scriptId = scriptId,
                scriptCommandName = channel,
            )
        } else {
            addAnnie(
                "",
                scriptMessageJson = resultJson,
                scriptId = scriptId,
                scriptCommandName = channel,
            )
        }
    }

    fun submit() {
        val value = draft.text.trim()
        if (value.isEmpty()) return
        val sentMessage = ChatEntry(System.nanoTime(), true, value)
        lastSentMessageId = sentMessage.id
        animatedMessageIds[sentMessage.id] = true
        messages.add(sentMessage)
        persistHistory()
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
        draft = TextFieldValue("")
        val parts = value.split(Regex("\\s+"), limit = 2)
        val command = parts.firstOrNull()?.lowercase().orEmpty()
        val commandName = command.removePrefix("/")
        // A command only runs when explicitly invoked with '/'. Plain chat text is
        // reserved for an active script session, never interpreted as a command name.
        val dynamicCommand = if (command.startsWith("/")) scriptCommands.firstOrNull { script ->
            commandName == script.name.lowercase() || script.aliases.any { commandName == it.removePrefix("/").lowercase() }
        } else null
        if (command.startsWith("/")) {
            val canonical = dynamicCommand?.let { "/${it.name}" } ?: command
            commandUsage = CommandUsageStore.record(context, commandUsage, canonical)
        }
        if (dynamicCommand != null) {
            val chatId = activeChatId
            scope.launch {
                val resultJson = scriptWorkspace.execute(dynamicCommand.name, value, chatId, sentMessage.id)
                addScriptResult(resultJson, dynamicCommand.scriptId, dynamicCommand.name)
            }
            return
        }
        if (!value.startsWith("/")) {
            val chatId = activeChatId
            scope.launch {
                val dispatch = scriptWorkspace.executeSession(value, chatId, sentMessage.id)
                if (dispatch != null) {
                    addScriptResult(dispatch.resultJson, dispatch.scriptId, dispatch.channel)
                } else {
                    addAnnie("Use /help to see Annie commands, or type / to browse commands supplied by installed packages.")
                }
            }
            return
        }
        when (command) {
            "/downloads" -> openDownloads()
            "/continue" -> openContinueWatching()
            "/extensions", "/settings" -> activeSheet = "Extensions"
            "/library" -> activeSheet = "Library"
            "/scripts" -> {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                scriptStudioProjectId = null
                scriptStudioOpenEnvironment = false
                activeSheet = "Scripts"
            }
            "/help" -> addAnnie("Built-in commands: /library, /continue, /downloads, /scripts and /extensions. Media commands are discovered from installed packages.")
            else -> addAnnie("That command is not installed. Type / to see built-ins and commands from installed packages.")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Box(Modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("chat_root")) {
            AnnieTopBar(
                character = character,
                onHistory = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    navigationDrawerOpen = true
                },
                onScriptStudio = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    scriptStudioProjectId = null
                    scriptStudioOpenEnvironment = false
                    scriptStudioOpenPackageImport = false
                    activeSheet = "Scripts"
                },
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("conversation"),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Top)
            ) {
                items(messages, key = { it.id }) { entry ->
                    val bubbleContent: @Composable () -> Unit = {
                        ChatBubble(
                            entry,
                            character = character,
                            onCatalogClick = ::openSelectedTitle,
                            onActionClick = ::handleMenuAction,
                            onOpenSource = { sourceUrl ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
                            },
                            onSeriesAction = { item, stage, season ->
                                when (stage) {
                                    "play" -> {
                                        val playbackItem = season?.asCatalogItem() ?: item
                                        val history = WatchHistoryStore.latestFor(context, playbackItem)
                                            ?.takeIf { !it.completed && it.positionMs >= WatchHistoryStore.MIN_RESUME_MS && it.mediaUri.isNotBlank() }
                                        if (history != null) {
                                            launchPlayer(
                                                context = context,
                                                item = playbackItem,
                                                mediaUri = history.mediaUri,
                                                mode = history.playerMode(),
                                                videoConfigJson = history.videoConfigJson,
                                            )
                                        } else {
                                            launchPlayer(context, playbackItem)
                                        }
                                    }
                                    "reader" -> openMangaReader(item)
                                    else -> addAnnie("", selectedItem = season?.asCatalogItem() ?: item, selectedStage = stage)
                                }
                            },
                            onScriptAction = { actionId, payloadJson, complete ->
                                val owner = entry.scriptId
                                if (owner != null) {
                                    scope.launch {
                                        val dispatch = runCatching {
                                            scriptWorkspace.executeAction(
                                                scriptId = owner,
                                                actionId = actionId,
                                                payloadJson = payloadJson,
                                                chatId = activeChatId,
                                                messageId = entry.id,
                                            )
                                        }.getOrNull()
                                        if (dispatch != null) addScriptResult(dispatch.resultJson, dispatch.scriptId, dispatch.channel)
                                        complete(dispatch?.resultJson)
                                    }
                                } else complete(null)
                            },
                            onScriptInlineAction = { actionId, payloadJson, complete ->
                                val owner = entry.scriptId
                                if (owner != null) {
                                    scope.launch {
                                        val dispatch = runCatching {
                                            scriptWorkspace.executeAction(
                                                scriptId = owner,
                                                actionId = actionId,
                                                payloadJson = payloadJson,
                                                chatId = activeChatId,
                                                messageId = entry.id,
                                            )
                                        }.getOrNull()
                                        complete(dispatch?.resultJson)
                                    }
                                } else complete(null)
                            },
                            onScriptVideoDownload = { data, owner ->
                                queueScriptVideoDownload(data, owner)
                            },
                        )
                    }
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = tween(180),
                            fadeOutSpec = null,
                        )
                    ) {
                        MessageArrivalAnimation(
                            fromUser = entry.fromUser,
                            animate = animatedMessageIds[entry.id] == true,
                            onFinished = { animatedMessageIds.remove(entry.id) },
                        ) {
                            bubbleContent()
                        }
                    }
                }
            }
            Composer(
                value = draft,
                onValueChange = {
                    draft = it
                },
                onSuggestionSelected = {
                    val selected = "$it "
                    draft = TextFieldValue(selected, selection = TextRange(selected.length))
                },
                onContextActionSelected = { input ->
                    draft = TextFieldValue(input, selection = TextRange(input.length))
                },
                onSend = { submit() },
                onVoiceNote = { path, durationMs ->
                    val note = ChatEntry(System.nanoTime(), true, "", voiceNotePath = path, voiceNoteDurationMs = durationMs)
                    animatedMessageIds[note.id] = true
                    messages.add(note)
                    persistHistory()
                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                    addAnnie("I got your voice note, but I can't listen to audio yet. Use the speech button to dictate text instead.")
                },
                onMenu = { activeSheet = "Tools" },
                scriptCommands = scriptCommands,
                commandUsage = commandUsage,
                conversationContext = conversationContext,
                inputEnabled = !navigationDrawerOpen && activeSheet == null,
            )
          }
          AnimatedVisibility(
              visible = navigationDrawerOpen,
              enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it },
              exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { -it },
          ) {
              AnnieNavigationDrawer(
                  chats = chats,
                  activeChatId = activeChatId,
                  onDismiss = { navigationDrawerOpen = false },
                  onNewChat = {
                      val newChat = newWelcomeChat(excludeCharacterId = activeChat.characterId)
                      chats.add(0, newChat)
                      activeChatId = newChat.id
                      draft = TextFieldValue("")
                      persistHistory()
                      navigationDrawerOpen = false
                  },
                  onSelectChat = { id ->
                      activeChatId = id
                      draft = TextFieldValue("")
                      navigationDrawerOpen = false
                  },
                  onOpen = { destination ->
                      navigationDrawerOpen = false
                      if (destination == "Scripts") {
                          scriptStudioProjectId = null
                          scriptStudioOpenEnvironment = false
                      }
                      activeSheet = destination
                  },
              )
          }
        }
    }

    if (activeSheet == "Scripts") {
        ScriptStudioSheet(
            workspace = scriptWorkspace,
            onCommandsReloaded = { commands -> scriptCommands = commands },
            onClose = {
                activeSheet = null
                scriptStudioProjectId = null
                scriptStudioOpenEnvironment = false
            },
            initialProjectId = scriptStudioProjectId,
            openEnvironment = scriptStudioOpenEnvironment,
            openPackageImport = scriptStudioOpenPackageImport,
            onPackageImportOpened = { scriptStudioOpenPackageImport = false },
            importFile = scriptStudioImportFile,
            onFileImportOpened = { scriptStudioImportFile = null },
        )
    } else if (activeSheet != null) {
        val category = activeSheet!!
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = Panel,
            contentColor = BrightText,
        ) {
            if (category == "Chat history") {
                ChatHistoryContent(
                    chats = chats,
                    activeChatId = activeChatId,
                    onNewChat = {
                        val newChat = newWelcomeChat(excludeCharacterId = activeChat.characterId)
                        chats.add(0, newChat)
                        activeChatId = newChat.id
                        draft = TextFieldValue("")
                        persistHistory()
                        activeSheet = null
                    },
                    onSelectChat = { id ->
                        activeChatId = id
                        draft = TextFieldValue("")
                        activeSheet = null
                    },
                    onExtensions = {
                        activeSheet = "Extensions"
                    },
                    onRenameChat = { id, title ->
                        val index = chats.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            chats[index] = chats[index].copy(customTitle = title.trim().take(64).ifBlank { null })
                            persistHistory()
                        }
                    },
                    onDeleteChat = { id ->
                        chats.removeAll { it.id == id }
                        if (chats.isEmpty()) chats.add(newWelcomeChat())
                        if (activeChatId == id) {
                            activeChatId = chats.first().id
                            draft = TextFieldValue("")
                        }
                        persistHistory()
                    },
                )
            } else if (category == "Extensions") {
                var extensionProjects by remember(category, scriptCommands) {
                    mutableStateOf(scriptWorkspace.files.listProjects())
                }
                ExtensionsManagerContent(
                    projects = extensionProjects,
                    commandWarnings = { project ->
                        scriptCommands.filter { it.scriptId == project.id && it.collision }.map { command ->
                            if (command.collidesWith != null) {
                                "/${command.handlerName} is also defined by ${command.collidesWith}. Use /${command.name} for this package."
                            } else {
                                "/${command.name} is also defined by a newer package, which now uses a prefixed name."
                            }
                        }
                    },
                    onToggle = { project, enabled ->
                        scriptWorkspace.files.setEnabled(project.id, enabled)
                        extensionProjects = scriptWorkspace.files.listProjects()
                        scope.launch {
                            runCatching { scriptWorkspace.reload() }
                                .onSuccess { commands ->
                                    scriptCommands = commands
                                    extensionProjects = scriptWorkspace.files.listProjects()
                                }
                                .onFailure { error ->
                                    addAnnie(error.message ?: "Could not update that extension.")
                                }
                        }
                    },
                    onConfigure = { project ->
                        scriptStudioProjectId = project.id
                        scriptStudioOpenEnvironment = true
                        activeSheet = "Scripts"
                    },
                    onOpenStudio = { project ->
                        scriptStudioProjectId = project?.id
                        scriptStudioOpenEnvironment = false
                        scriptStudioOpenPackageImport = false
                        activeSheet = "Scripts"
                    },
                    onLearn = { activeSheet = "Learn" },
                    onInstallExtension = {
                        scriptStudioProjectId = null
                        scriptStudioOpenEnvironment = false
                        scriptStudioOpenPackageImport = true
                        activeSheet = "Scripts"
                    },
                    onUninstall = { project ->
                        runCatching {
                            scriptWorkspace.files.deleteProject(project.id)
                        }.onSuccess {
                            extensionProjects = scriptWorkspace.files.listProjects()
                            scope.launch {
                                runCatching { scriptWorkspace.reload() }
                                    .onSuccess { commands ->
                                        scriptCommands = commands
                                        extensionProjects = scriptWorkspace.files.listProjects()
                                    }
                                    .onFailure { error ->
                                        addAnnie(error.message ?: "Could not finish removing that extension.")
                                    }
                            }
                        }.onFailure { error ->
                            addAnnie(error.message ?: "Could not remove that extension.")
                        }
                    },
                    grantedPermissions = { project -> scriptWorkspace.files.grantedPermissions(project.id) },
                )
            } else if (category == "About") {
                AnnieAboutContent { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { android.widget.Toast.makeText(context, "No browser is available.", android.widget.Toast.LENGTH_SHORT).show() }
                }
            } else if (category == "Learn") {
                AnnieScriptLearningContent(
                    onCreateScript = {
                        scriptStudioProjectId = null
                        scriptStudioOpenEnvironment = false
                        activeSheet = "Scripts"
                    },
                    onExtensions = { activeSheet = "Extensions" },
                )
            } else if (category == "Library") {
                AnnieLibraryContent(
                    downloads = downloads,
                    onDownloads = { activeSheet = "Downloads:All" },
                    onManga = {
                        activeSheet = null
                        openSavedManga()
                    },
                )
            } else if (category.startsWith("Downloads:")) {
                DownloadsManagerContent(
                    items = downloads,
                    onPlay = { item ->
                        when (DownloadedFileRouter.route(item)) {
                            DownloadOpenRoute.VIDEO -> launchPlayer(context, item.toPlayerCatalogItem(), Uri.fromFile(File(item.localPath)).toString(), PlayerMode.OFFLINE)
                            DownloadOpenRoute.MUSIC -> {
                                activeSheet = null
                                addAnnie("", scriptMessageJson = org.json.JSONObject().put("type", "music")
                                    .put("title", item.title).put("artist", item.sourceName).put("artwork", item.artworkUrl)
                                    .put("streamUrl", Uri.fromFile(File(item.localPath)).toString()).toString())
                                MusicPlaybackService.start(context, MusicPlaybackService.ACTION_PLAY) {
                                putExtra(MusicPlaybackService.EXTRA_STREAM, Uri.fromFile(File(item.localPath)).toString())
                                putExtra(MusicPlaybackService.EXTRA_TITLE, item.title)
                                putExtra(MusicPlaybackService.EXTRA_ARTIST, item.sourceName)
                                putExtra(MusicPlaybackService.EXTRA_ARTWORK, item.artworkUrl)
                                }
                            }
                            DownloadOpenRoute.SCRIPT, DownloadOpenRoute.PACKAGE -> {
                                scriptStudioImportFile = File(item.localPath)
                                scriptStudioProjectId = null
                                scriptStudioOpenEnvironment = false
                                activeSheet = "Scripts"
                            }
                            DownloadOpenRoute.MANGA -> scope.launch {
                                val catalog = item.toPlayerCatalogItem().copy(id = item.id.hashCode(), mediaType = "MANGA")
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val uri = DownloadedFileRouter.viewIntent(context, item).data!!
                                        AnnieMangaArchive.copyAndValidate(context, uri, catalog)
                                    }
                                }.onSuccess { activeSheet = null; activeMangaReader = catalog to it }
                                    .onFailure { addAnnie("Unable to open this manga archive.") }
                            }
                            DownloadOpenRoute.EXTERNAL -> DownloadedFileRouter.openExternal(context, item)
                        }
                    },
                    onShare = { item -> DownloadedFileRouter.share(context, item) },
                    onExport = { item -> exportDownloadItem = item; exportDownload.launch(item.filename ?: File(item.localPath).name) },
                    onRemove = { item ->
                        DownloadTransferService.remove(context, item)
                        downloads.removeAll { it.id == item.id }
                    },
                    onStateChange = { item, state ->
                        when (state) {
                            DownloadState.PAUSED -> DownloadTransferService.pause(context, item)
                            DownloadState.QUEUED -> DownloadTransferService.resume(context, item)
                            else -> {
                                val index = downloads.indexOfFirst { it.id == item.id }
                                if (index >= 0) {
                                    downloads[index] = downloads[index].copy(state = state)
                                    DownloadStore.update(context, downloads[index])
                                }
                            }
                        }
                    },
                    initialMediaFilter = category.substringAfter(":", "All"),
                )
            } else QuickActionsSheet { action ->
                when (action) {
                    "Library" -> activeSheet = "Library"
                    "Downloads" -> openDownloads()
                    "Extensions" -> activeSheet = "Extensions"
                    "Browser" -> {
                        activeSheet = null
                        context.startActivity(AnnieBrowserTabsActivity.intent(context))
                    }
                    "Manage Chat" -> activeSheet = "Chat history"
                    else -> activeSheet = null
                }
            }
        }
    }

    activeMangaReader?.let { (item, archive) ->
        AnnieMangaReaderDialog(item, archive) { activeMangaReader = null }
    }
}

@Composable
private fun MessageArrivalAnimation(
    fromUser: Boolean,
    animate: Boolean,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val duration = if (fromUser) 190 else 210
    LaunchedEffect(animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(duration))
            onFinished()
        } else if (progress.value < 1f) {
            progress.snapTo(1f)
        }
    }
    Box(
        Modifier
            .testTag(if (fromUser) "sent_message_animation" else "received_message_animation")
            .graphicsLayer {
                val p = progress.value
                alpha = 0.70f + (0.30f * p)
                translationY = (1f - p) * 10.dp.toPx()
                val startScale = if (fromUser) 0.975f else 0.985f
                val scale = startScale + ((1f - startScale) * p)
                scaleX = scale
                scaleY = scale
            },
    ) {
        content()
    }
}
private fun launchPlayer(
    context: Context,
    item: CatalogItem,
    mediaUri: String? = null,
    mode: PlayerMode = PlayerMode.STREAMING,
    videoConfigJson: String? = null,
) {
    context.startActivity(
        Intent(context, AnniePlayerActivity::class.java)
            .putExtra(AnniePlayerActivity.EXTRA_ID, item.id)
            .putExtra(AnniePlayerActivity.EXTRA_MEDIA_TYPE, item.mediaType)
            .putExtra(AnniePlayerActivity.EXTRA_TITLE, item.title)
            .putExtra(AnniePlayerActivity.EXTRA_IMAGE, item.image)
            .putExtra(AnniePlayerActivity.EXTRA_YEAR, item.year ?: -1)
            .putExtra(AnniePlayerActivity.EXTRA_MODE, mode.name)
            .putExtra(AnniePlayerActivity.EXTRA_MEDIA_URI, mediaUri)
            .putExtra(AnniePlayerActivity.EXTRA_VIDEO_CONFIG, videoConfigJson),
    )
}

private fun DownloadItem.toPlayerCatalogItem(): CatalogItem = CatalogItem(
    id = 0,
    mediaType = when (kind) {
        DownloadMediaKind.ANIME -> "ANIME"
        DownloadMediaKind.MOVIE -> "MOVIE"
        DownloadMediaKind.TV -> "TV"
        DownloadMediaKind.MANGA -> "MANGA"
        DownloadMediaKind.MUSIC -> "MUSIC"
        DownloadMediaKind.FILE -> "MOVIE"
    },
    title = title,
    image = artworkUrl,
    year = null,
    status = "",
    episodes = null,
    chapters = null,
)

private fun newWelcomeChat(excludeCharacterId: String? = null): ChatSession {
    val characterId = AnnieCharacters.randomId(excludeCharacterId)
    val character = AnnieCharacters.byId(characterId)
    val welcome = ChatEntry(
        id = System.nanoTime(),
        fromUser = false,
        text = character.greeting,
    )
    return ChatSession(System.nanoTime().toString(), mutableStateListOf(welcome), characterId)
}

@Composable
private fun ChatHistoryContent(
    chats: List<ChatSession>,
    activeChatId: String,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onExtensions: () -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Chats", color = BrightText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = Blue,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNewChat).testTag("new_chat_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AnnieIcons.NewChat, contentDescription = null, tint = BrightText, modifier = Modifier.size(20.dp))
                Text("New chat", color = BrightText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (chats.isEmpty()) {
            Text("No saved chats", color = SoftText, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).testTag("chat_history_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    Surface(
                        color = if (chat.id == activeChatId) Color(0xFF1A3554) else Bubble,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (chat.id == activeChatId) Blue else Color(0xFF294562)),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectChat(chat.id) }
                            .testTag("chat_history_${chat.id}"),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Text(chat.title, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (chat.preview.isNotBlank()) {
                                Text(chat.preview, color = SoftText, fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { renameDraft = chat.customTitle ?: chat.title; renameTarget = chat },
                                    modifier = Modifier.testTag("rename_chat_${chat.id}"),
                                ) { Text("Rename", color = SoftText) }
                                TextButton(onClick = { deleteTarget = chat }, modifier = Modifier.testTag("delete_chat_${chat.id}")) {
                                    Text("Delete", color = Color(0xFFFF9B91))
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("Extensions", color = SoftText, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onExtensions)
                .testTag("chat_history_extensions").padding(horizontal = 12.dp, vertical = 10.dp))
    }
    renameTarget?.let { chat ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(64) },
                    singleLine = true,
                    label = { Text("Chat name") },
                    modifier = Modifier.testTag("rename_chat_input"),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.isNotBlank(),
                    onClick = { onRenameChat(chat.id, renameDraft); renameTarget = null },
                    modifier = Modifier.testTag("rename_chat_confirm"),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { chat ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete chat?") },
            text = { Text("Delete \"${chat.title}\" and its conversation history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteChat(chat.id); deleteTarget = null },
                    modifier = Modifier.testTag("delete_chat_confirm"),
                ) { Text("Delete", color = Color(0xFFFF9B91)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AnnieNavigationDrawer(
    chats: List<ChatSession>,
    activeChatId: String,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var profilePickerOpen by remember { mutableStateOf(false) }
    val profilePreferences = LocalContext.current.getSharedPreferences(
        AnnieProfileAvatars.PREFERENCES,
        Context.MODE_PRIVATE,
    )
    val selectedProfileResource = profilePreferences.getInt(AnnieProfileAvatars.KEY, 0)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("annie_navigation_drawer")) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable(onClick = onDismiss)
            .testTag("drawer_scrim"))
        Surface(
            modifier = Modifier.fillMaxHeight().width(maxWidth * 0.82f).align(Alignment.CenterStart)
                .pointerInput(onDismiss) {
                    var horizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f) {
                                change.consume()
                                horizontalDrag += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (horizontalDrag < -72.dp.toPx()) onDismiss()
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f },
                    )
                }
                .testTag("navigation_drawer_panel"),
            color = Panel,
            shadowElevation = 18.dp,
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { profilePickerOpen = true }.testTag("drawer_profile"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnnieBrandAvatar(size = 42.dp)
                    Text("Annie", color = BrightText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("drawer_brand_title"))
                }
                DrawerAction("New chat", AnnieIcons.NewChat, onNewChat,
                    modifier = Modifier.padding(top = 10.dp).testTag("drawer_new_chat"), highlighted = true)
                DrawerAction("Library", AnnieIcons.Library, { onOpen("Library") },
                    modifier = Modifier.testTag("drawer_library"))
                DrawerAction("Downloads", AnnieIcons.Download, { onOpen("Downloads:All") },
                    modifier = Modifier.testTag("drawer_downloads"))
                DrawerAction("Extensions", AnnieIcons.Package, { onOpen("Extensions") },
                    modifier = Modifier.testTag("drawer_extensions"))
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent chats", color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("All", color = Color(0xFF7CC8FF), fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen("Chat history") }
                            .testTag("drawer_all_chats").padding(horizontal = 8.dp, vertical = 5.dp))
                }
                if (chats.isEmpty()) {
                    Text("Your conversations will appear here.", color = SoftText, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).testTag("chat_history_list"),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            val active = chat.id == activeChatId
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                    .background(if (active) Color(0xFF183451) else Color.Transparent)
                                    .clickable { onSelectChat(chat.id) }
                                    .testTag("drawer_chat_${chat.id}")
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(AnnieIcons.Menu, contentDescription = null,
                                    tint = if (active) Color(0xFF83CAFF) else SoftText, modifier = Modifier.size(17.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (chat.preview.isNotBlank()) Text(chat.preview, color = SoftText, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
                DrawerAction("About", AnnieIcons.File, { onOpen("About") },
                    modifier = Modifier.testTag("drawer_about"))
            }
        }
        if (profilePickerOpen) {
            Dialog(onDismissRequest = { profilePickerOpen = false }) {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF294562)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Choose a profile image", color = BrightText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        AnnieProfileAvatars.options.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { option ->
                                    AsyncImage(
                                        model = option.resource,
                                        contentDescription = option.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(54.dp).clip(CircleShape)
                                            .then(if (selectedProfileResource == option.resource) Modifier.border(2.dp, Color(0xFF42B9F5), CircleShape) else Modifier)
                                            .clickable {
                                                profilePreferences.edit().putInt(AnnieProfileAvatars.KEY, option.resource).apply()
                                                profilePickerOpen = false
                                            }
                                            .testTag("profile_avatar_${option.id}"),
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.size(54.dp)) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Command mark",
                                color = Color(0xFF82C9FF),
                                fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    profilePreferences.edit().remove(AnnieProfileAvatars.KEY).apply()
                                    profilePickerOpen = false
                                }.testTag("profile_avatar_default").padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                            Text(
                                "Done",
                                color = BrightText,
                                fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    profilePickerOpen = false
                                }.padding(vertical = 8.dp, horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (highlighted) Color(0xFF126FB5) else Color(0xFF102237),
        border = if (highlighted) null else BorderStroke(1.dp, Color(0xFF1D3853)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Icon(icon, contentDescription = null, tint = if (highlighted) BrightText else Color(0xFF8DCFFF), modifier = Modifier.size(20.dp))
            Text(title, color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AnnieLibraryContent(
    downloads: List<DownloadItem>,
    onDownloads: () -> Unit,
    onManga: () -> Unit,
) {
    val context = LocalContext.current
    val mangaCount = remember { AnnieMangaArchive.savedItems(context).size }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("library_content"),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text("Library", color = BrightText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LibraryRow("Downloads", downloads.size.takeIf { it > 0 }?.let { "$it items" }, AnnieIcons.Download, onDownloads,
            Modifier.testTag("library_downloads"))
        LibraryRow("Manga", mangaCount.takeIf { it > 0 }?.let { "$it titles" }, AnnieIcons.Library, onManga,
            Modifier.testTag("library_manga"))
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), color = Color(0xFF102237),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1D3853))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = Color(0xFF82C9FF), modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun AnnieTopBar(
    character: AnnieCharacter,
    onHistory: () -> Unit,
    onScriptStudio: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp).background(Panel).padding(horizontal = 16.dp).testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onHistory)
                .semantics { contentDescription = "Open navigation" }
                .testTag("chat_history_button"),
            contentAlignment = Alignment.Center,
        ) {
            // Shows the ACTIVE character, so avatar and name switch together with the chat.
            AnnieCharacterAvatar(character = character, size = 42.dp)
        }
        Text(
            character.name,
            color = BrightText,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        IconButton(onClick = onScriptStudio, modifier = Modifier.testTag("topbar_script_studio")) {
            Icon(AnnieIcons.Package, contentDescription = "Open Script Studio", tint = Color(0xFF82C9FF))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatBubble(
    entry: ChatEntry,
    character: AnnieCharacter = AnnieCharacters.default,
    onCatalogClick: (CatalogItem) -> Unit,
    onActionClick: (String, String) -> Unit,
    onOpenSource: (String) -> Unit,
    onSeriesAction: (CatalogItem, String, SeasonItem?) -> Unit,
    onScriptAction: (String, String, (String?) -> Unit) -> Unit = { _, _, done -> done(null) },
    onScriptInlineAction: (String, String, (String?) -> Unit) -> Unit = onScriptAction,
    onScriptVideoDownload: (org.json.JSONObject, String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("chat_message"),
        horizontalArrangement = if (entry.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!entry.fromUser) {
            AnnieCharacterAvatar(
                character = character,
                size = 32.dp,
                modifier = Modifier.padding(end = 9.dp, top = 18.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalAlignment = if (entry.fromUser) Alignment.End else Alignment.Start
        ) {
            if (!entry.fromUser) {
                Text(character.name, color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
            }
            if (entry.voiceNotePath != null) {
                VoiceNoteBubble(entry.voiceNotePath, entry.voiceNoteDurationMs, entry.fromUser)
            } else if (entry.searchMedia != null) {
                SearchMessage(entry.searchMedia, entry.searchInitial, onCatalogClick)
            } else if (entry.selectedItem != null) {
                when (entry.selectedStage) {
                    "series" -> SeriesCardMessage(entry.selectedItem) { stage ->
                        onSeriesAction(entry.selectedItem, stage, null)
                    }
                    "movie" -> MediaMetadataMessage(entry.selectedItem, "Movie", onOpenSource)
                    "tv" -> MediaMetadataMessage(entry.selectedItem, "TV series", onOpenSource)
                    "seasons" -> SeasonListMessage(entry.selectedItem) { season ->
                        onSeriesAction(entry.selectedItem, "episodes", season)
                    }
                    "episodes" -> EpisodeListMessage(entry.selectedItem)
                    "chapters" -> MangaChapterListMessage(entry.selectedItem) { onSeriesAction(entry.selectedItem, "reader", null) }
                    "reader" -> MangaReaderImportMessage(entry.selectedItem) { onSeriesAction(entry.selectedItem, "reader", null) }
                    else -> MangaResultMessage(entry.selectedItem) { stage ->
                        onSeriesAction(entry.selectedItem, stage, null)
                    }
                }
            } else if (entry.scriptMessageJson != null) {
                ScriptMessageCard(
                    payload = entry.scriptMessageJson,
                    scriptId = entry.scriptId.orEmpty(),
                    onAction = onScriptAction,
                    onInlineAction = onScriptInlineAction,
                    onVideoDownload = { data -> onScriptVideoDownload(data, entry.scriptId) },
                )
            } else if (entry.menuTitle != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
                        .background(Bubble).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(entry.menuTitle, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(entry.text, color = SoftText, fontSize = 14.sp, lineHeight = 20.sp)
                    entry.actions.forEach { action ->
                        val icon = when {
                            action.startsWith("Search") -> "search"
                            action.contains("aired") || action.contains("released") || action.contains("updated") || action == "Today" || action == "This week" || action == "All" -> "history"
                            action.contains("Continue") -> "play"
                            action == "Downloads" -> "download"
                            else -> "music"
                        }
                        Surface(
                            color = Color(0xFF10263D),
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, Color(0xFF294562)),
                            modifier = Modifier.fillMaxWidth().clickable { onActionClick(entry.menuTitle, action) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ActionGlyph(icon, actionColor(action))
                                Text(action, color = BrightText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else if (entry.catalog.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp)).background(Bubble).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(entry.text, color = BrightText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    entry.catalog.forEach { item -> CatalogCard(item) { onCatalogClick(item) } }
                }
            } else {
                Surface(
                    color = if (entry.fromUser) Color(0xFF0865A7) else Bubble,
                    shape = if (entry.fromUser) RoundedCornerShape(22.dp, 8.dp, 22.dp, 22.dp) else RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.035f)),
                    modifier = Modifier
                        .testTag("text_message_bubble")
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (entry.text.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Annie message", entry.text))
                                }
                            },
                        ),
                ) {
                    Text(entry.text, color = BrightText, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                }
            }
        }
    }
}

@Composable
private fun ScriptMessageCard(
    payload: String,
    scriptId: String,
    onAction: (String, String, (String?) -> Unit) -> Unit,
    onInlineAction: (String, String, (String?) -> Unit) -> Unit,
    onVideoDownload: (org.json.JSONObject) -> Unit,
) {
    val data = remember(payload) { runCatching { org.json.JSONObject(payload) }.getOrNull() }
        ?: run {
            ScriptTextMessage("Script response could not be read.", muted = true)
            return
        }

    when (MessageTypeRegistry.resolve(data).kind) {
        ScriptMessageKind.IMAGE -> ScriptImageMessage(data, scriptId)
        ScriptMessageKind.MUSIC -> ScriptMusicMessage(data, scriptId, onVideoDownload)
        ScriptMessageKind.VIDEO -> ScriptVideoMessage(data, scriptId, onVideoDownload)
        ScriptMessageKind.FILE -> ScriptFileMessage(data, onVideoDownload)
        ScriptMessageKind.SEASON_LIST -> ScriptSeasonListMessage(data, scriptId) { action, payloadJson ->
            onAction(action, payloadJson) {}
        }
        ScriptMessageKind.EPISODE_LIST -> ScriptEpisodeListMessage(data, scriptId) { action, payloadJson ->
            onAction(action, payloadJson) {}
        }
        ScriptMessageKind.CONTINUE_WATCHING -> ScriptContinueWatchingMessage(data, scriptId) { action, payloadJson ->
            onAction(action, payloadJson) {}
        }
        ScriptMessageKind.MATCHES -> ScriptMatchesMessage(data, scriptId) { action, payloadJson ->
            onAction(action, payloadJson) {}
        }
        ScriptMessageKind.OPTIONS -> ScriptOptionsMessage(data) { action, payloadJson -> onAction(action, payloadJson) {} }
        ScriptMessageKind.BROWSER -> AnnieBrowserSpec.decode(data)?.let { spec ->
            AnnieBrowserMessage(spec, onInlineAction)
        } ?: ScriptTextMessage("Browser request could not be opened safely.", muted = true)
        ScriptMessageKind.PROGRESS -> ScriptProgressMessage(data)
        ScriptMessageKind.FORM -> ScriptFormMessage(data) { action, payloadJson ->
            onAction(action, payloadJson) {}
        }
        ScriptMessageKind.TEXT -> ScriptTextMessage(data.optString("text"))
        ScriptMessageKind.CODE -> ScriptCodeBlockMessage(data)
        ScriptMessageKind.COPY -> ScriptCopyBlockMessage(data)
        ScriptMessageKind.UNKNOWN -> ScriptTextMessage(
            data.optString("text").takeIf(String::isNotBlank) ?: "Script response"
        )
    }
}

@Composable
private fun ScriptFileMessage(data: org.json.JSONObject, onDownload: (org.json.JSONObject) -> Unit) {
    val name = data.optString("filename").ifBlank { data.optString("fileName") }
        .ifBlank { AnnieDownloadNaming.urlFilename(data.optString("url").ifBlank { data.optString("uri") }) ?: "File" }
    Surface(color = Bubble, shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp), modifier = Modifier.testTag("script_file_message")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AnnieIcons.File, contentDescription = null, tint = Color(0xFF168EEA), modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(data.optString("title").ifBlank { name }, color = BrightText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (data.optString("title").isNotBlank() && data.optString("title") != name) Text(name, color = SoftText, fontSize = 12.sp)
                val size = data.optLong("size", -1)
                if (size >= 0) Text(if (size >= 1024) "${size / 1024} KB" else "$size B", color = SoftText, fontSize = 12.sp)
            }
            TextButton(onClick = { onDownload(data) }, modifier = Modifier.testTag("script_file_download")) { Text("Download") }
        }
    }
}

@Composable
private fun ScriptTextMessage(text: String, muted: Boolean = false) {
    Surface(
        color = Bubble,
        shape = RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.035f)),
        modifier = Modifier.testTag("script_text_message"),
    ) {
        Text(
            text,
            color = if (muted) SoftText else BrightText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
        )
    }
}

@Composable
private fun ScriptImageMessage(data: org.json.JSONObject, scriptId: String) {
    val context = LocalContext.current
    val uri = data.optString("uri").takeIf(String::isNotBlank)
    val imageModel = remember(uri, scriptId) {
        uri?.let { value ->
            resolvePackageAsset(context, scriptId, value) ?: value.takeUnless { it.startsWith("annie-asset://") }
        }
    }
    var expanded by remember(uri) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp)).background(Bubble).padding(8.dp)
    ) {
        if (imageModel == null) {
            Text("Image could not be loaded", color = SoftText, modifier = Modifier.padding(12.dp))
        } else {
            AsyncImage(
                model = imageModel,
                contentDescription = data.optString("caption").ifBlank { "Image message" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = true }.testTag("script_image_message"),
            )
        }
        data.optString("caption").takeIf(String::isNotBlank)?.let {
            Text(it, color = BrightText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
    if (expanded && imageModel != null) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF030811)).clickable { expanded = false }, contentAlignment = Alignment.Center) {
                AsyncImage(model = imageModel, contentDescription = data.optString("caption"), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().fillMaxSize().testTag("script_image_fullscreen"))
            }
        }
    }
}

@Composable
private fun ScriptMusicMessage(data: org.json.JSONObject, scriptId: String, onDownload: (org.json.JSONObject) -> Unit) {
    val context = LocalContext.current
    val rawStream = data.optString("streamUrl").takeIf(String::isNotBlank)
        ?: data.optString("uri").takeIf(String::isNotBlank)
    val stream = rawStream?.let { resolvePackageResourceUri(context, scriptId, it) }
    val title = data.optString("title", "Untitled track")
    val artist = data.optString("artist")
    val artwork = data.optString("artwork")
    var playbackService by remember { mutableStateOf<MusicPlaybackService?>(null) }
    var playbackSnapshot by remember { mutableStateOf(MusicPlaybackService.Snapshot()) }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val isActiveTrack = stream != null && playbackSnapshot.stream == stream
    val playing = isActiveTrack && playbackSnapshot.playing
    val duration = if (isActiveTrack) playbackSnapshot.durationMs
        else data.optInt("durationMs", 0).coerceAtLeast(0)
    val position = if (isActiveTrack) playbackSnapshot.positionMs else 0
    var showLyrics by remember(stream) { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(context.applicationContext) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? MusicPlaybackService.LocalBinder)?.service()
                playbackService = service
                playbackSnapshot = service?.currentSnapshot() ?: MusicPlaybackService.Snapshot()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }
        val bound = context.applicationContext.bindService(
            Intent(context, MusicPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE
        )
        onDispose { if (bound) runCatching { context.applicationContext.unbindService(connection) } }
    }
    LaunchedEffect(playbackService) {
        while (true) {
            playbackService?.let { playbackSnapshot = it.currentSnapshot() }
            delay(300)
        }
    }

    fun togglePlayback() {
        if (stream == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (playbackSnapshot.stream == stream) {
            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_TOGGLE)
        } else {
            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_PLAY) {
                putExtra(MusicPlaybackService.EXTRA_STREAM, stream)
                putExtra(MusicPlaybackService.EXTRA_TITLE, title)
                putExtra(MusicPlaybackService.EXTRA_ARTIST, artist)
                putExtra(MusicPlaybackService.EXTRA_ARTWORK, artwork)
            }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble)
            .padding(13.dp)
            .testTag("script_music_message"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0A1726)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = data.optString("artwork").takeIf(String::isNotBlank),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (data.optString("artwork").isBlank()) {
                    Icon(
                        imageVector = AnnieIcons.AudioTrack,
                        contentDescription = null,
                        tint = Color(0xFF7EC8FF),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    data.optString("title", "Untitled track"),
                    color = BrightText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                data.optString("artist").takeIf(String::isNotBlank)?.let {
                    Text(it, color = SoftText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (stream != null) Blue else Color(0xFF26384B))
                    .clickable(enabled = stream != null, onClick = ::togglePlayback)
                    .testTag("script_music_play"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) AnnieIcons.Pause else AnnieIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = BrightText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatMediaTime(position), color = SoftText, fontSize = 10.sp)
            MusicSeekBar(
                progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                enabled = isActiveTrack && playbackSnapshot.prepared && duration > 0,
                onSeek = { value ->
                    if (duration > 0) {
                        val seekPosition = (duration * value.coerceIn(0f, 1f)).toInt()
                        MusicPlaybackService.start(context, MusicPlaybackService.ACTION_SEEK) {
                            putExtra(MusicPlaybackService.EXTRA_POSITION, seekPosition)
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("script_music_seek"),
            )
            Text(if (duration > 0) formatMediaTime(duration) else "--:--", color = SoftText, fontSize = 10.sp)
        }

        val lyrics = data.optString("lyrics")
        if (ScriptVideoDownloadSource.from(data)?.url?.let { !it.startsWith("file:", true) } == true) {
            TextButton(onClick = { onDownload(data) }, modifier = Modifier.testTag("script_music_download")) { Text("Download") }
        }
        val timedLyrics = remember(lyrics) { parseTimedLyrics(lyrics) }
        val lyricsListState = remember(stream, lyrics) { LazyListState() }
        val activeLyricIndex = remember(timedLyrics, position) {
            timedLyrics.indexOfLast { it.startMs <= position }
        }
        LaunchedEffect(showLyrics, activeLyricIndex) {
            if (showLyrics && activeLyricIndex >= 0 && activeLyricIndex < timedLyrics.size) {
                lyricsListState.animateScrollToItem(activeLyricIndex)
            }
        }
        if (lyrics.isNotBlank()) {
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().clickable { showLyrics = !showLyrics },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Lyrics",
                            color = BrightText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (showLyrics) "Hide" else "Show",
                            color = Color(0xFF42B9F5),
                            fontSize = 12.sp,
                        )
                    }
                    if (showLyrics) {
                        if (timedLyrics.isEmpty()) {
                            Text(
                                lyrics,
                                color = BrightText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(top = 8.dp),
                                state = lyricsListState,
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                itemsIndexed(timedLyrics) { index, line ->
                                    val active = index == activeLyricIndex
                                    Text(
                                        line.text,
                                        color = if (active) Color(0xFF7ED0FF) else SoftText,
                                        fontSize = if (active) 14.sp else 13.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        lineHeight = 19.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = isActiveTrack && playbackSnapshot.prepared) {
                                                MusicPlaybackService.start(context, MusicPlaybackService.ACTION_SEEK) {
                                                    putExtra(MusicPlaybackService.EXTRA_POSITION, line.startMs)
                                                }
                                            }
                                            .padding(vertical = 2.dp)
                                            .testTag(if (active) "script_music_active_lyric" else "script_music_lyric_$index"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicSeekBar(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seek = androidx.compose.runtime.rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .height(28.dp)
            .semantics {
                contentDescription = "Track position"
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { target -> seek.value(target.coerceIn(0f, 1f)); true } else disabled()
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { point ->
                    if (size.width > 0) seek.value((point.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0) seek.value((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) seek.value((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                )
            }
    ) {
        val thumbRadius = 5.dp.toPx()
        val trackHeight = 3.dp.toPx()
        val left = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
        val x = left + trackWidth * progress.coerceIn(0f, 1f)
        val y = size.height / 2f
        drawRoundRect(
            color = Color(0xFF2A3C50),
            topLeft = Offset(left, y - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        if (x > left) {
            drawRoundRect(
                color = Blue,
                topLeft = Offset(left, y - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size((x - left).coerceAtLeast(0f), trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
            )
        }
        drawCircle(
            color = if (enabled) BrightText else SoftText,
            radius = thumbRadius,
            center = Offset(x, y),
        )
    }
}

internal data class TimedLyricLine(val startMs: Int, val text: String)

internal fun parseTimedLyrics(value: String): List<TimedLyricLine> {
    if (value.isBlank()) return emptyList()
    val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    return buildList {
        for (rawLine in value.lineSequence()) {
            val matches = timestamp.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = rawLine.replace(timestamp, "").trim()
            if (text.isBlank()) continue
            for (match in matches) {
                val minutes = match.groupValues[1].toIntOrNull() ?: continue
                val seconds = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: continue
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0
                    1 -> fraction.toIntOrNull()?.times(100) ?: 0
                    2 -> fraction.toIntOrNull()?.times(10) ?: 0
                    else -> fraction.take(3).toIntOrNull() ?: 0
                }
                add(TimedLyricLine(((minutes * 60 + seconds) * 1_000) + fractionMs, text))
            }
        }
    }.sortedBy { it.startMs }
}

private fun formatMediaTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun ScriptVideoMessage(
    data: org.json.JSONObject,
    scriptId: String,
    onDownload: (org.json.JSONObject) -> Unit,
) {
    val context = LocalContext.current
    val title = data.optString("title").ifBlank { "Video" }
    val mediaType = data.optString("mediaType").uppercase().let {
        if (it in setOf("ANIME", "MOVIE", "TV")) it else "VIDEO"
    }
    val mediaId = data.optInt("id").takeIf { it > 0 }
        ?: data.optString("canonicalTitleId").takeIf(String::isNotBlank)?.hashCode()
        ?: title.hashCode()
    val source = remember(data.toString()) { ScriptVideoDownloadSource.from(data) }
    val sourceUrl = source?.url
    val uri = sourceUrl?.let { resolvePackageResourceUri(context, scriptId, it) }
    val canDownload = sourceUrl?.startsWith("https://", true) == true || sourceUrl?.startsWith("http://", true) == true
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(8.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val previewHeight = ScriptVideoLayout.previewHeightDp(maxWidth.value, data).dp
            Box(
                Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF030811))
                    .clickable(enabled = uri != null) {
                        val item = CatalogItem(
                            mediaId,
                            mediaType,
                            title,
                            data.optString("thumbnail"),
                            data.optInt("year").takeIf { it > 0 },
                            "",
                            null,
                            null,
                        )
                        launchPlayer(context, item, uri, videoConfigJson = data.toString())
                    }
                    .testTag("script_video_message"),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = data.optString("thumbnail").takeIf(String::isNotBlank),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(0xBB07111E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AnnieIcons.Play,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = BrightText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    source?.quality?.takeIf(String::isNotBlank),
                    data.optString("duration").takeIf(String::isNotBlank),
                ).joinToString(" · ")
                if (details.isNotBlank()) Text(details, color = SoftText, fontSize = 12.sp)
            }
            Text(
                "Download",
                color = if (canDownload) Color(0xFF42B9F5) else SoftText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = canDownload) { onDownload(data) }
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .testTag("script_video_download"),
            )
        }
    }
}

private fun resolvePackageAsset(context: Context, scriptId: String, uri: String): File? {
    if (!uri.startsWith("annie-asset://")) return null
    val logicalId = Uri.parse(uri).host.orEmpty()
    if (logicalId.isBlank() || scriptId.isBlank()) return null
    return runCatching { ScriptFiles(context).resolveAssetFile(scriptId, logicalId) }.getOrNull()
}

internal fun resolvePackageResourceUri(context: Context, scriptId: String, uri: String): String? =
    if (uri.startsWith("annie-asset://")) {
        resolvePackageAsset(context, scriptId, uri)?.let { Uri.fromFile(it).toString() }
    } else uri

internal data class ScriptMediaQuality(val value: String, val label: String)

internal fun scriptMediaQualities(item: org.json.JSONObject): List<ScriptMediaQuality> {
    val values = item.optJSONArray("qualities") ?: return emptyList()
    return buildList {
        for (index in 0 until values.length()) {
            when (val raw = values.opt(index)) {
                is org.json.JSONObject -> {
                    val value = raw.optString("value").ifBlank { raw.optString("label") }.trim()
                    val label = raw.optString("label").ifBlank { value }.trim()
                    if (value.isNotBlank()) add(ScriptMediaQuality(value, label))
                }
                null, org.json.JSONObject.NULL -> Unit
                else -> raw.toString().trim().takeIf(String::isNotBlank)?.let { add(ScriptMediaQuality(it, it)) }
            }
        }
    }.distinctBy { it.value }
}

internal fun scriptMediaActionPayload(
    item: org.json.JSONObject,
    fallbackId: String,
    quality: String? = null,
): String {
    val payload = when (val raw = item.opt("payload")) {
        is org.json.JSONObject -> org.json.JSONObject(raw.toString())
        is org.json.JSONArray -> org.json.JSONObject().put("payload", raw)
        null, org.json.JSONObject.NULL -> org.json.JSONObject()
        else -> org.json.JSONObject().put("value", raw)
    }
    if (!payload.has("id")) {
        payload.put("id", item.optString("id").ifBlank { fallbackId })
    }
    item.optString("title").takeIf(String::isNotBlank)?.let { title ->
        if (!payload.has("title")) payload.put("title", title)
    }
    quality?.takeIf(String::isNotBlank)?.let { payload.put("quality", it) }
    return payload.toString()
}

private fun scriptMediaArtworkModel(context: Context, scriptId: String, value: String): Any? {
    if (value.isBlank()) return null
    return resolvePackageAsset(context, scriptId, value) ?: value.takeUnless { it.startsWith("annie-asset://") }
}

@Composable
private fun ScriptSeasonListMessage(
    data: org.json.JSONObject,
    scriptId: String,
    onAction: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val seasons = data.optJSONArray("seasons") ?: org.json.JSONArray()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("script_season_list"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(data.optString("title").ifBlank { "Seasons" }, color = BrightText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        data.optString("subtitle").takeIf(String::isNotBlank)?.let {
            Text(it, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        for (index in 0 until seasons.length()) {
            val season = seasons.optJSONObject(index) ?: continue
            val id = season.optString("id").ifBlank { index.toString() }
            val title = season.optString("title").ifBlank { "Season ${index + 1}" }
            val action = season.optString("action")
            val image = season.optString("image").ifBlank { season.optString("thumbnail") }
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = action.isNotBlank()) {
                        onAction(action, scriptMediaActionPayload(season, id))
                    }
                    .testTag("script_season_${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"),
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    scriptMediaArtworkModel(context, scriptId, image)?.let { model ->
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(58.dp).height(76.dp).clip(RoundedCornerShape(9.dp)),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val facts = listOfNotNull(
                            season.optString("year").takeIf(String::isNotBlank),
                            season.optInt("episodes").takeIf { it > 0 }?.let { "$it episodes" },
                            season.optString("subtitle").takeIf(String::isNotBlank),
                        )
                        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptEpisodeListMessage(
    data: org.json.JSONObject,
    scriptId: String,
    onAction: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val episodes = data.optJSONArray("episodes") ?: org.json.JSONArray()
    val selectedQuality = remember(data.toString()) {
        mutableStateMapOf<String, String>().apply {
            for (index in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(index) ?: continue
                val id = episode.optString("id").ifBlank { index.toString() }
                val choices = scriptMediaQualities(episode)
                val requested = episode.optString("quality")
                val initial = choices.firstOrNull { it.value == requested }?.value
                    ?: requested.takeIf(String::isNotBlank)
                    ?: choices.firstOrNull()?.value
                if (initial != null) this[id] = initial
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("script_episode_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(data.optString("title").ifBlank { "Episodes" }, color = BrightText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        data.optString("subtitle").takeIf(String::isNotBlank)?.let {
            Text(it, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        for (index in 0 until episodes.length()) {
            val episode = episodes.optJSONObject(index) ?: continue
            val id = episode.optString("id").ifBlank { index.toString() }
            val tagId = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val title = episode.optString("title").ifBlank { "Episode ${index + 1}" }
            val thumbnail = episode.optString("thumbnail").ifBlank { episode.optString("image") }
            val choices = scriptMediaQualities(episode)
            val fixedQuality = episode.optString("quality").takeIf(String::isNotBlank)
                ?: choices.singleOrNull()?.label
            val currentQuality = selectedQuality[id]
            val playAction = episode.optString("playAction").ifBlank { episode.optString("action") }
            val downloadAction = episode.optString("downloadAction")
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().testTag("script_episode_$tagId"),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        scriptMediaArtworkModel(context, scriptId, thumbnail)?.let { model ->
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(96.dp).height(62.dp).clip(RoundedCornerShape(9.dp)),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val facts = listOfNotNull(
                                episode.optString("episode").takeIf(String::isNotBlank),
                                episode.optString("duration").takeIf(String::isNotBlank),
                                episode.optString("subtitle").takeIf(String::isNotBlank),
                            )
                            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (choices.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            choices.forEachIndexed { qualityIndex, quality ->
                                val active = currentQuality == quality.value
                                Surface(
                                    color = if (active) Color(0xFF16446A) else Color(0xFF132D47),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (active) Blue else Color(0xFF294562)),
                                    modifier = Modifier.clickable { selectedQuality[id] = quality.value }
                                        .testTag("script_episode_quality_${tagId}_$qualityIndex"),
                                ) {
                                    Text(
                                        quality.label,
                                        color = if (active) BrightText else SoftText,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    } else if (!fixedQuality.isNullOrBlank()) {
                        Text(fixedQuality, color = SoftText, fontSize = 10.sp)
                    }
                    if (playAction.isNotBlank() || downloadAction.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            if (playAction.isNotBlank()) {
                                Surface(
                                    color = Blue,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).clickable {
                                        onAction(playAction, scriptMediaActionPayload(episode, id, selectedQuality[id] ?: currentQuality))
                                    }.testTag("script_episode_play_$tagId"),
                                ) {
                                    Text("Play", color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                                }
                            }
                            if (downloadAction.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF132D47),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF168EEA)),
                                    modifier = Modifier.weight(1f).clickable {
                                        onAction(downloadAction, scriptMediaActionPayload(episode, id, selectedQuality[id] ?: currentQuality))
                                    }.testTag("script_episode_download_$tagId"),
                                ) {
                                    Text("Download", color = Color(0xFF7CC8FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        data.optJSONObject("downloadAll")?.let { bulk ->
            val action = bulk.optString("action")
            if (action.isNotBlank()) {
                Surface(
                    color = Color(0xFF132D47),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF168EEA)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val base = runCatching {
                            val raw = bulk.opt("payload")
                            if (raw is org.json.JSONObject) org.json.JSONObject(raw.toString()) else org.json.JSONObject()
                        }.getOrDefault(org.json.JSONObject())
                        val qualities = org.json.JSONObject()
                        selectedQuality.forEach { (id, quality) -> qualities.put(id, quality) }
                        base.put("qualities", qualities)
                        onAction(action, base.toString())
                    }.testTag("script_episode_download_all"),
                ) {
                    Text(
                        bulk.optString("label").ifBlank { "Download all" },
                        color = Color(0xFF7CC8FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptContinueWatchingMessage(
    data: org.json.JSONObject,
    scriptId: String,
    onAction: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val items = data.optJSONArray("items") ?: org.json.JSONArray()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("script_continue_watching"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(data.optString("title").ifBlank { "Continue watching" }, color = BrightText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { index.toString() }
            val tagId = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val action = item.optString("action")
            val image = item.optString("image").ifBlank { item.optString("thumbnail") }
            val positionMs = item.optLong("positionMs").coerceAtLeast(0L)
            val durationMs = item.optLong("durationMs").coerceAtLeast(0L)
            val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().testTag("script_continue_$tagId"),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        scriptMediaArtworkModel(context, scriptId, image)?.let { model ->
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(96.dp).height(62.dp).clip(RoundedCornerShape(9.dp)),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.optString("title").ifBlank { "Untitled" }, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.optString("episode").takeIf(String::isNotBlank)?.let {
                                Text(it, color = SoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (action.isNotBlank()) {
                            Text(
                                item.optString("label").ifBlank { "Continue" },
                                color = Color(0xFF7CC8FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    onAction(action, scriptMediaActionPayload(item, id))
                                }.padding(horizontal = 7.dp, vertical = 6.dp)
                                    .testTag("script_continue_action_$tagId"),
                            )
                        }
                    }
                    if (durationMs > 0L) {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF2A3C50)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(progress).fillMaxHeight()
                                    .background(Color(0xFF42B9F5)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptOptionsMessage(data: org.json.JSONObject, onAction: (String, String) -> Unit) {
    val rows = data.optJSONArray("options") ?: org.json.JSONArray()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp)).background(Bubble).padding(14.dp).testTag("script_options_message"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.optString("title").takeIf(String::isNotBlank)?.let { Text(it, color = BrightText, fontWeight = FontWeight.SemiBold) }
        for (index in 0 until rows.length()) {
            val option = rows.optJSONObject(index) ?: continue
            val optionId = option.optString("id").ifBlank { index.toString() }
            val label = option.optString("label", optionId)
            val actionId = option.optString("action", optionId)
            val payloadValue = option.opt("payload")
            val payloadJson = when (payloadValue) {
                is org.json.JSONObject, is org.json.JSONArray -> payloadValue.toString()
                null, org.json.JSONObject.NULL -> org.json.JSONObject().put("id", optionId).put("label", label).toString()
                else -> org.json.JSONObject().put("id", optionId).put("label", label).put("value", payloadValue).toString()
            }
            Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF294562))) {
                Text(label, color = BrightText,
                    modifier = Modifier.fillMaxWidth().clickable { onAction(actionId, payloadJson) }
                        .padding(horizontal = 13.dp, vertical = 12.dp).testTag("script_option_$optionId"))
            }
        }
    }
}

@Composable
private fun ScriptFormMessage(
    data: org.json.JSONObject,
    onSubmit: (String, String) -> Unit,
) {
    val fields = data.optJSONArray("fields") ?: org.json.JSONArray()
    val values = remember(data.toString()) {
        mutableStateMapOf<String, Any?>().apply {
            for (index in 0 until fields.length()) {
                val field = fields.optJSONObject(index) ?: continue
                val id = field.optString("id").trim()
                if (id.isBlank()) continue
                val type = field.optString("type", "text").lowercase()
                val initial = field.opt("value").takeUnless { it == null || it == org.json.JSONObject.NULL }
                    ?: field.opt("default").takeUnless { it == null || it == org.json.JSONObject.NULL }
                this[id] = when (type) {
                    "switch" -> when (initial) {
                        is Boolean -> initial
                        is Number -> initial.toInt() != 0
                        is String -> initial.equals("true", true)
                        else -> false
                    }
                    "multi-select" -> {
                        val array = initial as? org.json.JSONArray
                        buildSet {
                            if (array != null) {
                                for (itemIndex in 0 until array.length()) {
                                    array.optString(itemIndex).takeIf(String::isNotBlank)?.let(::add)
                                }
                            }
                        }
                    }
                    else -> initial?.toString().orEmpty()
                }
            }
        }
    }
    var submitCount by remember(data.toString()) { mutableIntStateOf(0) }
    val submit = data.optJSONObject("submit")
    val submitLabel = submit?.optString("label").orEmpty().ifBlank { "Submit" }
    val submitAction = submit?.optString("action").orEmpty()
        .ifBlank { data.optString("action") }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble)
            .padding(14.dp)
            .testTag("script_form_message"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        data.optString("title").takeIf(String::isNotBlank)?.let {
            Text(it, color = BrightText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        data.optString("description").takeIf(String::isNotBlank)?.let {
            Text(it, color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
        }

        for (index in 0 until fields.length()) {
            val field = fields.optJSONObject(index) ?: continue
            val id = field.optString("id").trim()
            if (id.isBlank()) continue
            val type = field.optString("type", "text").lowercase()
            val label = field.optString("label").ifBlank { id }
            val description = field.optString("description")
            val options = field.optJSONArray("options") ?: org.json.JSONArray()

            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF10263D), RoundedCornerShape(13.dp))
                    .padding(12.dp)
                    .testTag("script_form_field_${id}"),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (description.isNotBlank()) {
                            Text(description, color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (type == "switch") {
                        val checked = values[id] as? Boolean ?: false
                        Switch(
                            checked = checked,
                            onCheckedChange = { values[id] = it },
                            modifier = Modifier.testTag("script_form_switch_${id}"),
                        )
                    }
                }

                when (type) {
                    "switch" -> Unit
                    "select" -> {
                        val selected = values[id]?.toString().orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (optionIndex in 0 until options.length()) {
                                val optionValue = options.optString(optionIndex)
                                val active = optionValue == selected
                                Surface(
                                    color = if (active) Color(0xFF16446A) else Color(0xFF132D47),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (active) Blue else Color(0xFF294562)),
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { values[id] = optionValue }
                                        .testTag("script_form_option_${id}_${optionIndex}"),
                                ) {
                                    Text(
                                        (if (active) "✓  " else "") + optionValue,
                                        color = BrightText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    )
                                }
                            }
                        }
                    }
                    "multi-select" -> {
                        val selected = (values[id] as? Set<*>)?.mapNotNull { it?.toString() }?.toSet().orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (optionIndex in 0 until options.length()) {
                                val optionValue = options.optString(optionIndex)
                                val active = optionValue in selected
                                Surface(
                                    color = if (active) Color(0xFF16446A) else Color(0xFF132D47),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (active) Blue else Color(0xFF294562)),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        values[id] = if (active) selected - optionValue else selected + optionValue
                                    }.testTag("script_form_option_${id}_${optionIndex}"),
                                ) {
                                    Text(
                                        (if (active) "✓  " else "") + optionValue,
                                        color = BrightText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val current = values[id]?.toString().orEmpty()
                        Surface(
                            color = Color(0xFF0A1726),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF294562)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BasicTextField(
                                value = current,
                                onValueChange = { next ->
                                    values[id] = if (type == "number") {
                                        next.filter { char -> char.isDigit() || char in ".-" }
                                    } else next
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = BrightText, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp)
                                    .testTag("script_form_input_${id}"),
                                decorationBox = { inner ->
                                    Box {
                                        if (current.isBlank()) {
                                            Text(field.optString("placeholder"), color = SoftText, fontSize = 12.sp)
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = if (submitAction.isNotBlank()) Blue else Color(0xFF26384B),
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = submitAction.isNotBlank()) {
                    val valuesJson = org.json.JSONObject()
                    values.forEach { (key, value) ->
                        when (value) {
                            is Set<*> -> valuesJson.put(key, org.json.JSONArray(value.mapNotNull { it?.toString() }))
                            else -> valuesJson.put(key, value ?: org.json.JSONObject.NULL)
                        }
                    }
                    val payload = org.json.JSONObject()
                        .put("formId", data.optString("id"))
                        .put("values", valuesJson)
                        .put("submitCount", submitCount + 1)
                        .toString()
                    submitCount += 1
                    onSubmit(submitAction, payload)
                }
                .testTag("script_form_submit"),
        ) {
            Text(
                submitLabel,
                color = BrightText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ScriptProgressMessage(data: org.json.JSONObject) {
    val state = data.optString("state", if (data.has("progress")) "running" else "indeterminate").lowercase()
    val tone = when (state) {
        "success", "completed" -> Teal
        "failed", "error", "cancelled" -> Color(0xFFFF8C86)
        "paused" -> Color(0xFFFFC86A)
        else -> Blue
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("script_progress_message"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                data.optString("text", "Working…"),
                color = BrightText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                when (state) {
                    "success", "completed" -> "DONE"
                    "failed", "error" -> "FAILED"
                    "cancelled" -> "CANCELLED"
                    "paused" -> "PAUSED"
                    "queued" -> "QUEUED"
                    else -> if (data.has("progress")) "${(data.optDouble("progress", 0.0).coerceIn(0.0, 1.0) * 100).toInt()}%" else "WORKING"
                },
                color = tone,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (data.has("progress")) {
            val value = data.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawRoundRect(Color(0xFF26384B), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                drawRoundRect(tone, size = androidx.compose.ui.geometry.Size(size.width * value, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            }
        } else if (state in setOf("running", "indeterminate")) {
            CircularProgressIndicator(color = tone, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun actionColor(action: String): Color = when {
    action.startsWith("Search", ignoreCase = true) || action == "Downloads" -> Color(0xFF42B9F5)
    action.contains("aired", ignoreCase = true) || action.contains("released", ignoreCase = true) || action.contains("updated", ignoreCase = true) || action == "Today" || action == "This week" || action == "All" -> Color(0xFFB68CFF)
    action.contains("Continue", ignoreCase = true) || action.contains("reading", ignoreCase = true) -> Teal
    else -> Color(0xFF42B9F5)
}

@Composable
private fun ActionGlyph(name: String, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        when (name) {
            "search" -> {
                drawCircle(color, 5.5.dp.toPx(), Offset(8.dp.toPx(), 8.dp.toPx()), style = Stroke(w))
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), w)
            }
            "history" -> {
                drawCircle(color, 7.dp.toPx(), Offset(size.width / 2, size.height / 2), style = Stroke(w))
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width / 2, 5.dp.toPx()), w)
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(14.dp.toPx(), 12.dp.toPx()), w)
            }
            "globe" -> {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(color, 8.dp.toPx(), c, style = Stroke(w))
                drawLine(color, Offset(2.dp.toPx(), c.y), Offset(18.dp.toPx(), c.y), w)
                drawOval(color, topLeft = Offset(6.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 16.dp.toPx()), style = Stroke(w))
            }
            "book" -> {
                drawRoundRect(color, topLeft = Offset(3.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 16.dp.toPx()), style = Stroke(w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawRoundRect(color, topLeft = Offset(11.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 16.dp.toPx()), style = Stroke(w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawLine(color, Offset(10.dp.toPx(), 4.dp.toPx()), Offset(10.dp.toPx(), 16.dp.toPx()), w)
            }
            "list" -> {
                for (y in listOf(4.dp, 10.dp, 16.dp)) { drawCircle(color, 1.dp.toPx(), Offset(3.dp.toPx(), y.toPx())); drawLine(color, Offset(7.dp.toPx(), y.toPx()), Offset(18.dp.toPx(), y.toPx()), w) }
            }
            "play" -> {
                val p = Path().apply {
                    moveTo(5.dp.toPx(), 2.dp.toPx())
                    lineTo(18.dp.toPx(), 10.dp.toPx())
                    lineTo(5.dp.toPx(), 18.dp.toPx())
                    close()
                }
                drawPath(p, color)
            }
            "download" -> {
                drawLine(color, Offset(10.dp.toPx(), 2.dp.toPx()), Offset(10.dp.toPx(), 14.dp.toPx()), w)
                drawLine(color, Offset(5.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), 15.dp.toPx()), w)
                drawLine(color, Offset(15.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), 15.dp.toPx()), w)
                drawLine(color, Offset(4.dp.toPx(), 18.dp.toPx()), Offset(16.dp.toPx(), 18.dp.toPx()), w)
            }
            "info" -> {
                drawCircle(color, 8.dp.toPx(), Offset(10.dp.toPx(), 10.dp.toPx()), style = Stroke(w))
                drawCircle(color, 1.1.dp.toPx(), Offset(10.dp.toPx(), 6.dp.toPx()))
                drawLine(color, Offset(10.dp.toPx(), 9.dp.toPx()), Offset(10.dp.toPx(), 15.dp.toPx()), w)
            }
            "code" -> {
                drawLine(color, Offset(8.dp.toPx(), 4.dp.toPx()), Offset(3.dp.toPx(), 10.dp.toPx()), w)
                drawLine(color, Offset(3.dp.toPx(), 10.dp.toPx()), Offset(8.dp.toPx(), 16.dp.toPx()), w)
                drawLine(color, Offset(12.dp.toPx(), 4.dp.toPx()), Offset(17.dp.toPx(), 10.dp.toPx()), w)
                drawLine(color, Offset(17.dp.toPx(), 10.dp.toPx()), Offset(12.dp.toPx(), 16.dp.toPx()), w)
            }
            "image" -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 14.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = Stroke(w),
                )
                drawCircle(color, 1.8.dp.toPx(), Offset(7.dp.toPx(), 8.dp.toPx()), style = Stroke(w))
                drawLine(color, Offset(4.dp.toPx(), 15.dp.toPx()), Offset(9.dp.toPx(), 10.dp.toPx()), w)
                drawLine(color, Offset(9.dp.toPx(), 10.dp.toPx()), Offset(16.dp.toPx(), 15.dp.toPx()), w)
            }
            "file" -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 16.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                    style = Stroke(w),
                )
                drawLine(color, Offset(7.dp.toPx(), 8.dp.toPx()), Offset(13.dp.toPx(), 8.dp.toPx()), w)
                drawLine(color, Offset(7.dp.toPx(), 12.dp.toPx()), Offset(13.dp.toPx(), 12.dp.toPx()), w)
            }
            else -> {
                drawCircle(color, 3.dp.toPx(), Offset(8.dp.toPx(), 7.dp.toPx()), style = Stroke(w))
                drawLine(color, Offset(11.dp.toPx(), 5.dp.toPx()), Offset(16.dp.toPx(), 3.dp.toPx()), w)
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx()), w)
            }
        }
    }
}

@Composable
internal fun SearchMessage(mediaType: String, initialQuery: String, onSelect: (CatalogItem) -> Unit) {
    var query by remember(mediaType, initialQuery) { mutableStateOf(initialQuery) }
    var results by remember(mediaType) { mutableStateOf(emptyList<CatalogItem>()) }
    var loading by remember(mediaType) { mutableStateOf(false) }
    var error by remember(mediaType) { mutableStateOf(false) }

    LaunchedEffect(mediaType, query) {
        results = emptyList()
        error = false
        if (query.trim().length < 2) {
            loading = false
            return@LaunchedEffect
        }
        if (mediaType !in setOf("anime", "manga", "movie", "tv")) {
            loading = false
            error = true
            return@LaunchedEffect
        }
        val searchedQuery = query.trim()
        delay(300)
        if (query.trim() != searchedQuery) return@LaunchedEffect
        loading = true
        try {
            val found = searchCatalog(mediaType, searchedQuery)
            if (query.trim() == searchedQuery) results = found
        } catch (_: Exception) {
            if (query.trim() == searchedQuery) error = true
        } finally {
            if (query.trim() == searchedQuery) loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when (mediaType) {
                "manga" -> "Search manga"
                "movie" -> "Search movies"
                "tv" -> "Search TV series"
                else -> "Search anime"
            },
            color = BrightText,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF0C1A2B))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionGlyph("search", Color(0xFF27A8F2))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Type a title to search…", color = SoftText, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrightText),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        when {
            error && mediaType !in setOf("anime", "manga", "movie", "tv") ->
                Text("Search isn’t available here.", color = SoftText, fontSize = 13.sp)
            error -> Text("Search is temporarily unavailable. Try again.", color = SoftText, fontSize = 13.sp)
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                Text("Finding matching titles…", color = SoftText, fontSize = 13.sp)
            }
            query.trim().length < 2 -> Unit
            results.isEmpty() -> Text("No matching titles found.", color = SoftText, fontSize = 13.sp)
            else -> results.forEach { item -> CatalogCard(item) { onSelect(item) } }
        }
    }
}

@Composable
internal fun SeriesCardMessage(item: CatalogItem, onAction: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var watchEntry by remember(item.id, item.mediaType, item.title) {
        mutableStateOf(WatchHistoryStore.latestFor(context, item))
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, item.id, item.mediaType, item.title) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                watchEntry = WatchHistoryStore.latestFor(context, item)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val resumeAvailable = watchEntry?.let {
        !it.completed && it.positionMs >= WatchHistoryStore.MIN_RESUME_MS && it.mediaUri.isNotBlank()
    } == true
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("anime_details_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (item.image.isNotBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = "${item.title} cover artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(112.dp).height(176.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D3550))
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    item.seasons.size.takeIf { it > 0 }?.let { "$it seasons" },
                    item.episodes?.let { "$it episodes" },
                    item.year?.toString()
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
                if (item.genres.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.genres.forEach { genre ->
                            Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                                Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                if (item.summary.isNotBlank()) Text(item.summary, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        watchEntry?.takeIf { it.positionMs >= WatchHistoryStore.MIN_RESUME_MS }?.let { history ->
            Text(
                WatchHistoryStore.lastWatchedLabel(history),
                color = Teal,
                fontSize = 12.sp,
                modifier = Modifier.testTag("anime_last_watched"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SeriesCardAction(if (resumeAvailable) "Resume" else "Play from the beginning", "play", Modifier.weight(1.2f)) { onAction("play") }
            SeriesCardAction("Seasons", "list", Modifier.weight(0.8f)) { onAction("seasons") }
        }
    }
}

@Composable
private fun SeriesCardAction(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val primaryAction = label.startsWith("Play") || label.startsWith("Resume")
    Surface(
        color = if (primaryAction) Blue else Color(0xFF10263D),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (primaryAction) Blue else Color(0xFF168EEA)),
        modifier = modifier.clickable(onClick = onClick).testTag("anime_action_${if (primaryAction) "play" else "seasons"}")
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ActionGlyph(icon, if (primaryAction) Color.White else Color(0xFF42B9F5))
            Text(label, color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SeasonListMessage(item: CatalogItem, onSelect: (SeasonItem) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (item.seasons.isEmpty()) {
            Text("No season data from this source.", color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Text("Choose a season", color = SoftText, fontSize = 13.sp)
        }
        item.seasons.forEachIndexed { index, season ->
            Surface(
                color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(season) }
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(season.image, season.title, Modifier.size(54.dp, 70.dp).clip(RoundedCornerShape(8.dp)))
                    Column {
                        Text("Season ${index + 1}", color = Color(0xFF77C5FF), fontSize = 11.sp)
                        Text(season.title, color = BrightText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(season.year?.toString(), season.episodes?.let { "$it episodes" }).joinToString(" · "), color = SoftText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListMessage(item: CatalogItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Episodes", color = Color(0xFF77C5FF), fontSize = 13.sp)
        Text("No episodes available from this extension.", color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
internal fun MangaResultMessage(item: CatalogItem, onAction: (String) -> Unit) {
    val context = LocalContext.current
    val hasLocalArchive = AnnieMangaArchive.existing(context, item).isFile
    val progress = AnnieMangaProgress.page(context, item.id)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(12.dp).testTag("manga_details_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (item.image.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth().height(208.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1D3550)).testTag("manga_cover_artwork")
            ) {
                AsyncImage(
                    model = item.image,
                    contentDescription = "${item.title} cover artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE607111E)))))
            }
        }
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 25.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        val metadata = listOfNotNull(
            item.creator?.takeIf(String::isNotBlank),
            item.year?.toString(),
            item.chapters?.let { "$it chapters" },
            item.status.takeIf { it in setOf("RELEASING", "FINISHED") }?.let(::mangaStatusLabel)
        )
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        if (item.genres.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.genres.forEach { genre ->
                    Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                        Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (item.summary.isNotBlank()) Text(item.summary, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = 5, overflow = TextOverflow.Ellipsis)
        if (hasLocalArchive) {
            Text(
                "Page ${progress + 1}",
                color = SoftText,
                fontSize = 12.sp,
                modifier = Modifier.testTag("last_read_chapter"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MangaCardAction(if (hasLocalArchive) "Continue reading" else "Start reading", "book", Modifier.weight(1f)) { onAction("reader") }
            MangaCardAction("Chapters", "list", Modifier.weight(1f)) { onAction("chapters") }
        }
    }
}

@Composable
private fun MangaCardAction(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val primaryAction = icon == "book"
    Surface(
        color = if (primaryAction) Blue else Color(0xFF10263D),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (primaryAction) Blue else Color(0xFF168EEA)),
        modifier = modifier.clickable(onClick = onClick).testTag("manga_action_$label")
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionGlyph(icon, if (primaryAction) Color.White else Color(0xFF42B9F5))
            Text(label, color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MediaMetadataMessage(item: CatalogItem, mediaLabel: String, onOpenSource: (String) -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.image.isNotBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier.width(96.dp).height(130.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D3550))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    item.year?.toString(),
                    item.runtimeMinutes?.let { "$it min" },
                    item.status.takeIf { it.isNotBlank() && it != "METADATA" }
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 11.sp, lineHeight = 16.sp)
                if (item.creator != null) Text("Directed by ${item.creator}", color = SoftText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.genres.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.genres.forEach { genre ->
                    Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                        Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (item.summary.isNotBlank()) {
            Text(item.summary, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (item.sourceUrl.isNotBlank()) {
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().clickable { onOpenSource(item.sourceUrl) }
            ) {
                Text("View on ${item.sourceLabel}", color = Color(0xFF9CD7FF), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun MangaChapterListMessage(item: CatalogItem, onOpenLocal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Chapters · ${item.title}", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "No chapter source connected. Open a CBZ or ZIP chapter instead.",
            color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
        )
        Surface(
            color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF168EEA)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLocal).testTag("manga_open_local_archive"),
        ) {
            Text("Open a CBZ or ZIP chapter", color = BrightText, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp))
        }
    }
}

@Composable
private fun MangaReaderImportMessage(item: CatalogItem, onOpenLocal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Reader", color = Color(0xFF77C5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose a CBZ or ZIP chapter.",
            color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
        )
        Surface(
            color = Blue, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLocal).testTag("manga_import_archive"),
        ) {
            Text("Choose chapter file", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp))
        }
    }
}

private fun mangaStatusLabel(status: String): String = when (status) {
    "RELEASING" -> "Ongoing"
    "FINISHED" -> "Completed"
    else -> "Status unknown"
}

@Composable
internal fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("catalog_result_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1A2A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF29425E))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(104.dp).height(78.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1D3550))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val mediaLabel = when (item.mediaType) {
                    "MANGA" -> "MANGA"
                    "MOVIE" -> "MOVIE"
                    "TV" -> "TV SERIES"
                    else -> if (item.format == "MOVIE") "ANIME MOVIE" else "ANIME"
                }
                Text(mediaLabel, color = Color(0xFF75BDF1), fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = BrightText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val count = if (item.mediaType == "MANGA") item.chapters?.let { "$it chapters" } else item.episodes?.let { "$it episodes" }
                val facts = listOfNotNull(item.year?.toString(), count, item.sourceLabel.takeIf { it.isNotBlank() })
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "RELEASING" -> "Ongoing"
    "FINISHED" -> "Completed"
    "NOT_YET_RELEASED" -> "Not yet released"
    else -> "Status unknown"
}

@Composable
internal fun CommandSuggestions(
    suggestions: List<RankedCommandSuggestion>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val progress = remember(suggestions.firstOrNull()?.candidate?.command) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(150)) }

    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Panel).padding(6.dp)
            .graphicsLayer {
                alpha = 0.62f + 0.38f * progress.value
                translationY = (1f - progress.value) * 8.dp.toPx()
            }
            .testTag("slash_suggestions"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            suggestions,
            key = { _, suggestion -> suggestion.candidate.command },
        ) { index, suggestion ->
            val candidate = suggestion.candidate
            Row(
                Modifier.animateItem(
                    fadeInSpec = tween(140),
                    placementSpec = tween(160),
                    fadeOutSpec = tween(100),
                ).fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .background(if (index == selectedIndex) Color(0xFF173854) else Color.Transparent)
                    .clickable {
                        onSelectedIndexChange(index)
                        onSelect(candidate.command)
                    }
                    .testTag("slash_command_${candidate.command}")
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(candidate.command, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(candidate.label, color = SoftText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    candidate.providerName?.let { provider ->
                        Text("Provided by $provider", color = Color(0xFF8AA5BD), fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextSuggestedActions(
    actions: List<ScriptSuggestedAction>,
    onSelect: (String) -> Unit,
) {
    if (actions.isEmpty()) return
    val progress = remember(actions) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(150)) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .graphicsLayer {
                alpha = 0.62f + 0.38f * progress.value
                translationY = (1f - progress.value) * 8.dp.toPx()
            }
            .testTag("context_suggestions"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.take(4).forEach { action ->
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.clickable { onSelect(action.input) }
                    .testTag("context_action_${action.label.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"),
            ) {
                Text(
                    action.label,
                    color = BrightText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun Composer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSuggestionSelected: (String) -> Unit = {},
    onContextActionSelected: (String) -> Unit = {},
    onSend: () -> Unit,
    onMenu: () -> Unit,
    onVoiceNote: (String, Long) -> Unit = { _, _ -> },
    scriptCommands: List<ScriptCommand> = emptyList(),
    commandUsage: Map<String, CommandUsage> = emptyMap(),
    conversationContext: ConversationContext = ConversationContext(),
    inputEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) onValueChange(TextFieldValue(spoken))
        }
    }
    val latestOnValueChange = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val voiceNotes = rememberVoiceNoteController(onVoiceNote)
    val dictation = remember(context) {
        AnnieDictation(
            context = context,
            onText = { text -> latestOnValueChange.value(TextFieldValue(text, selection = TextRange(text.length))) },
            onUnavailable = {
                // No in-app recognizer on this device: fall back to the system speech dialog.
                runCatching {
                    speechLauncher.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Annie")
                    )
                }
            },
        )
    }
    androidx.compose.runtime.DisposableEffect(dictation) { onDispose { dictation.release() } }
    val speechPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictation.start(value.text)
    }
    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
    fun startSpeechRecognition() {
        if (dictation.listening) { dictation.stop(); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            dictation.start(value.text)
        } else {
            speechPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val candidates = remember(scriptCommands) {
        builtInCommandCandidates() + scriptCommands.map { it.toCommandCandidate() }
    }
    val suggestions = remember(value.text, candidates, commandUsage, conversationContext) {
        CommandSuggestionEngine.rank(
            query = value.text,
            candidates = candidates,
            usage = commandUsage,
            context = conversationContext,
            limit = 6,
        )
    }
    var selectedSuggestionIndex by remember(value.text) { mutableIntStateOf(0) }
    if (selectedSuggestionIndex > suggestions.lastIndex) {
        selectedSuggestionIndex = suggestions.lastIndex.coerceAtLeast(0)
    }

    fun acceptSelectedSuggestion(): Boolean {
        val selected = suggestions.getOrNull(selectedSuggestionIndex) ?: return false
        onSuggestionSelected(selected.candidate.command)
        return true
    }

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().testTag("composer")) {
        if (!value.text.trimStart().startsWith("/")) {
            ContextSuggestedActions(conversationContext.suggestedActions, onContextActionSelected)
        }
        CommandSuggestions(
            suggestions = suggestions,
            selectedIndex = selectedSuggestionIndex,
            onSelectedIndexChange = { selectedSuggestionIndex = it },
            onSelect = onSuggestionSelected,
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Night).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!voiceNotes.recording) Surface(color = Bubble, shape = CircleShape, modifier = Modifier.size(44.dp).clickable(onClick = onMenu).testTag("composer_tools")) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AnnieIcons.Add,
                        contentDescription = "Open tools",
                        tint = SoftText,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (!voiceNotes.recording) Surface(color = if (dictation.listening) Color(0xFFFF4D4D) else Bubble, shape = CircleShape, modifier = Modifier.size(44.dp).clickable(onClick = ::startSpeechRecognition).testTag("composer_speech")) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AnnieIcons.AudioTrack,
                        contentDescription = if (dictation.listening) "Stop dictation" else "Dictate a message",
                        tint = if (dictation.listening) Color.White else SoftText,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (voiceNotes.recording) VoiceRecordingBar(voiceNotes, Modifier.weight(1f)) else             Row(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF102139))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    // Covered chat input must not reclaim the IME when an
                    // external viewer or modal sheet returns window focus.
                    enabled = inputEnabled,
                    modifier = Modifier.weight(1f).testTag("composer_input").onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || suggestions.isEmpty()) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                selectedSuggestionIndex = (selectedSuggestionIndex + 1).coerceAtMost(suggestions.lastIndex)
                                true
                            }
                            Key.DirectionUp -> {
                                selectedSuggestionIndex = (selectedSuggestionIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Tab, Key.Enter -> acceptSelectedSuggestion()
                            else -> false
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrightText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!acceptSelectedSuggestion()) onSend()
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) Text("Message Annie…", color = SoftText, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
            }
            if (value.text.isBlank()) {
                if (voiceNotes.locked) androidx.compose.foundation.layout.Spacer(Modifier.size(46.dp))
                else VoiceHoldButton(voiceNotes, onNeedPermission = { voicePermission.launch(Manifest.permission.RECORD_AUDIO) })
            } else Surface(
                color = Blue,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).clickable(onClick = onSend).testTag("send_message"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AnnieIcons.Send,
                        contentDescription = "Send message",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

private data class MenuAction(val icon: String, val label: String)

@Composable
internal fun QuickActionsSheet(onChoose: (String) -> Unit) {
    val actions = listOf(
        MenuAction("book", "Library"),
        MenuAction("download", "Downloads"),
        MenuAction("list", "Extensions"),
        MenuAction("globe", "Browser"),
        MenuAction("history", "Manage Chat"),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
            .testTag("quick_actions_sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", color = BrightText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        actions.forEach { action ->
            Surface(
                color = Color(0xFF11243A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF29425F)),
                modifier = Modifier.fillMaxWidth().clickable { onChoose(action.label) }
                    .testTag("quick_action_" + action.label.lowercase().replace(" ", "_"))
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF183553)), contentAlignment = Alignment.Center) {
                        ActionGlyph(action.icon, Color(0xFF5CB7F5))
                    }
                    Text(
                        action.label,
                        color = BrightText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

