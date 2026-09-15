package com.night.sora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.night.sora.data.CoreRepository
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.ui.screens.*

enum class RootTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    MEDIA("Media", Icons.Rounded.PlayCircle),
    LIBRARY("Library", Icons.Rounded.Folder),
    GAMES("Games", Icons.Rounded.SportsEsports),
    MORE("More", Icons.Rounded.MoreHoriz),
}

sealed interface AppScreen {
    data object Ai : AppScreen
    data object Extensions : AppScreen
    data class ExtensionDetail(val extension: InstalledExtension) : AppScreen
    data class MediaDetails(val selection: ExtensionMediaSelection) : AppScreen
}

@Composable
fun SoraApp() {
    val context = LocalContext.current
    val repository = remember { CoreRepository(context.applicationContext) }
    val extensionManager = remember { ExtensionManager(context.applicationContext) }
    var tab by remember { mutableStateOf(RootTab.HOME) }
    val screenStack = remember { mutableStateListOf<AppScreen>() }
    var extensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    var extensionScanDone by remember { mutableStateOf(false) }
    var showAiQuick by remember { mutableStateOf(false) }

    fun refreshExtensions() {
        extensionManager.discover {
            extensions = it
            extensionScanDone = true
        }
    }

    fun push(screen: AppScreen) {
        screenStack += screen
    }

    fun pop() {
        if (screenStack.isNotEmpty()) screenStack.removeAt(screenStack.lastIndex)
    }

    LaunchedEffect(Unit) { refreshExtensions() }

    BackHandler(enabled = screenStack.isNotEmpty()) { pop() }
    BackHandler(enabled = screenStack.isEmpty() && tab != RootTab.HOME) { tab = RootTab.HOME }

    val current = screenStack.lastOrNull()
    if (current == null) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    RootTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == RootTab.HOME) {
                    FloatingActionButton(onClick = { showAiQuick = true }) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Ask Sora")
                    }
                }
            },
        ) { padding ->
            when (tab) {
                RootTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    entries = repository.library,
                    onOpenMedia = { tab = RootTab.MEDIA },
                    onOpenLibrary = { tab = RootTab.LIBRARY },
                    onOpenGames = { tab = RootTab.GAMES },
                )
                RootTab.MEDIA -> MediaScreen(
                    modifier = Modifier.padding(padding),
                    extensions = extensions,
                    extensionScanDone = extensionScanDone,
                    manager = extensionManager,
                    listeningSignals = repository.listeningSignals,
                    isSaved = repository::isSaved,
                    onToggleSaved = repository::toggleSaved,
                    onOpenExtensions = { push(AppScreen.Extensions) },
                    onOpenDetails = { push(AppScreen.MediaDetails(it)) },
                )
                RootTab.LIBRARY -> LibraryScreen(
                    modifier = Modifier.padding(padding),
                    entries = repository.library,
                    onOpenMedia = { push(AppScreen.MediaDetails(it)) },
                )
                RootTab.GAMES -> GamesScreen(Modifier.padding(padding))
                RootTab.MORE -> MoreScreen(
                    modifier = Modifier.padding(padding),
                    extensionCount = extensions.count { it.error == null },
                    onExtensions = { push(AppScreen.Extensions) },
                )
            }
        }

        if (showAiQuick) {
            AiQuickSheet(
                messages = repository.activeAiMessages,
                onDismiss = { showAiQuick = false },
                onSend = repository::sendAiText,
                onExpand = {
                    showAiQuick = false
                    push(AppScreen.Ai)
                },
            )
        }
    } else {
        when (current) {
            AppScreen.Ai -> AiScreen(
                conversations = repository.aiConversations,
                activeConversationId = repository.activeAiConversationId,
                messages = repository.activeAiMessages,
                onSelectConversation = repository::selectAiConversation,
                onNewConversation = repository::newAiConversation,
                onSendText = repository::sendAiText,
                onSendVoice = repository::sendVoicePlaceholder,
                onBack = ::pop,
            )
            AppScreen.Extensions -> ExtensionsScreen(
                extensions = extensions,
                onBack = ::pop,
                onRefresh = ::refreshExtensions,
                onOpen = { push(AppScreen.ExtensionDetail(it)) },
            )
            is AppScreen.ExtensionDetail -> ExtensionDetailScreen(current.extension, onBack = ::pop)
            is AppScreen.MediaDetails -> {
                val extension = extensions.firstOrNull { it.packageName == current.selection.extensionPackage }
                if (extension == null) {
                    MissingExtensionScreen(onBack = ::pop)
                } else {
                    MediaDetailScreen(
                        selection = current.selection,
                        extension = extension,
                        manager = extensionManager,
                        isSaved = repository.isSaved(current.selection),
                        onToggleSaved = { repository.toggleSaved(current.selection) },
                        onBack = ::pop,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiQuickSheet(
    messages: List<com.night.sora.model.AiMessage>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onExpand: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sora AI", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onExpand) { Text("Open") }
            }
            messages.takeLast(2).forEach { message ->
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.role == com.night.sora.model.AiMessage.Role.USER)
                        MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Message Sora") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSend(draft)
                            draft = ""
                        }
                    },
                ) { Icon(Icons.Rounded.ArrowUpward, "Send") }
            }
        }
    }
}
