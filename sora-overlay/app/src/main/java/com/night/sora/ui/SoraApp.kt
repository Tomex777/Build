package com.night.sora.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    data object Root : AppScreen
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
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Root) }
    var extensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    var extensionScanDone by remember { mutableStateOf(false) }

    fun refreshExtensions() {
        extensionManager.discover {
            extensions = it
            extensionScanDone = true
        }
    }

    LaunchedEffect(Unit) { refreshExtensions() }

    when (val current = screen) {
        AppScreen.Root -> {
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
                }
            ) { padding ->
                when (tab) {
                    RootTab.HOME -> HomeScreen(
                        modifier = Modifier.padding(padding),
                        extensions = extensions,
                        onOpenAi = { screen = AppScreen.Ai },
                        onOpenMedia = { tab = RootTab.MEDIA },
                    )
                    RootTab.MEDIA -> MediaScreen(
                        modifier = Modifier.padding(padding),
                        extensions = extensions,
                        extensionScanDone = extensionScanDone,
                        manager = extensionManager,
                        listeningSignals = repository.listeningSignals,
                        onOpenExtensions = { screen = AppScreen.Extensions },
                        onOpenDetails = { selection -> screen = AppScreen.MediaDetails(selection) },
                    )
                    RootTab.LIBRARY -> LibraryScreen(
                        modifier = Modifier.padding(padding),
                        entries = repository.library,
                        onOpenMedia = { selection -> screen = AppScreen.MediaDetails(selection) },
                    )
                    RootTab.GAMES -> GamesScreen(Modifier.padding(padding))
                    RootTab.MORE -> MoreScreen(
                        modifier = Modifier.padding(padding),
                        extensionCount = extensions.count { it.error == null },
                        onExtensions = { screen = AppScreen.Extensions },
                    )
                }
            }
        }
        AppScreen.Ai -> AiScreen(
            messages = repository.aiMessages,
            onSendText = repository::sendAiText,
            onSendVoice = repository::sendVoicePlaceholder,
            onBack = { screen = AppScreen.Root },
        )
        AppScreen.Extensions -> ExtensionsScreen(
            extensions = extensions,
            onBack = { screen = AppScreen.Root },
            onRefresh = ::refreshExtensions,
            onOpen = { screen = AppScreen.ExtensionDetail(it) },
        )
        is AppScreen.ExtensionDetail -> ExtensionDetailScreen(current.extension, onBack = { screen = AppScreen.Extensions })
        is AppScreen.MediaDetails -> {
            val extension = extensions.firstOrNull { it.packageName == current.selection.extensionPackage }
            if (extension == null) {
                MissingExtensionScreen(onBack = { screen = AppScreen.Root })
            } else {
                MediaDetailScreen(
                    selection = current.selection,
                    extension = extension,
                    manager = extensionManager,
                    isSaved = repository.isSaved(current.selection),
                    onToggleSaved = { repository.toggleSaved(current.selection) },
                    onBack = { screen = AppScreen.Root },
                )
            }
        }
    }
}
