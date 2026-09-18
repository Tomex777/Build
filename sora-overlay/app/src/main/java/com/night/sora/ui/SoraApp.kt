@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.night.sora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.night.sora.data.CoreRepository
import com.night.sora.data.MediaCatalogCache
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.PlaybackSession
import com.night.sora.model.ReaderSession
import com.night.sora.playback.MusicListeningEventType
import com.night.sora.playback.MusicPlaybackController
import com.night.sora.playback.MusicPlaybackRuntime
import com.night.sora.ui.screens.*
import com.night.sora.ui.theme.*

enum class RootTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home), MEDIA("Media", Icons.Rounded.PlayCircle), LIBRARY("Library", Icons.Rounded.LocalLibrary), GAMES("Games", Icons.Rounded.SportsEsports), MORE("More", Icons.Rounded.MoreHoriz),
}

sealed interface AppScreen {
    data object Ai : AppScreen
    data object Extensions : AppScreen
    data object Bible : AppScreen
    data object Downloads : AppScreen
    data object Statistics : AppScreen
    data object DataStorage : AppScreen
    data object PlayerReaderSettings : AppScreen
    data class ExtensionDetail(val extension: InstalledExtension) : AppScreen
    data class MediaDetails(val selection: ExtensionMediaSelection) : AppScreen
    data class Reader(val session: ReaderSession) : AppScreen
    data class VideoPlayer(val session: PlaybackSession) : AppScreen
    data object NowPlaying : AppScreen
}

@Composable
fun SoraApp() {
    val context = LocalContext.current
    val repository = remember { CoreRepository(context.applicationContext) }
    val mediaCatalogCache = remember { MediaCatalogCache(context.applicationContext) }
    val extensionManager = remember { ExtensionManager(context.applicationContext) }
    val musicPlayer = remember { MusicPlaybackRuntime.get(context.applicationContext, extensionManager) }
    var tab by remember { mutableStateOf(RootTab.HOME) }
    val screenStack = remember { mutableStateListOf<AppScreen>() }
    var extensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    var extensionScanDone by remember { mutableStateOf(false) }
    var showAiQuick by remember { mutableStateOf(false) }

    fun refreshExtensions() {
        extensionManager.discover { extensions = it; extensionScanDone = true }
    }
    fun push(screen: AppScreen) { screenStack += screen }
    fun pop() { if (screenStack.isNotEmpty()) screenStack.removeAt(screenStack.lastIndex) }
    fun openMedia(selection: ExtensionMediaSelection) {
        repository.recordActivity(selection, "opened")
        push(AppScreen.MediaDetails(selection))
    }

    LaunchedEffect(Unit) { refreshExtensions() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshExtensions() }
    LaunchedEffect(extensions) { musicPlayer.updateExtensions(extensions) }
    LaunchedEffect(musicPlayer.listeningEvent?.serial) {
        val event = musicPlayer.listeningEvent ?: return@LaunchedEffect
        val track = event.track
        val artistName = track.subtitle.substringBefore(" · ").ifBlank { track.title }
        val artistId = artistName.trim().lowercase()
        when (event.type) {
            MusicListeningEventType.STARTED -> {
                repository.recordActivity(track, "played")
                repository.recordListening(artistId, artistName)
            }
            MusicListeningEventType.COMPLETED -> repository.recordListening(
                artistId = artistId,
                artistName = artistName,
                completed = true,
                countPlay = false,
            )
            MusicListeningEventType.SKIPPED -> repository.recordListening(
                artistId = artistId,
                artistName = artistName,
                skipped = true,
                countPlay = false,
            )
        }
    }
    BackHandler(enabled = screenStack.isNotEmpty()) { pop() }
    BackHandler(enabled = screenStack.isEmpty() && tab != RootTab.HOME) { tab = RootTab.HOME }

    val current = screenStack.lastOrNull()
    if (current == null) {
        Scaffold(
            containerColor = SoraBg,
            bottomBar = {
                Column(Modifier.background(SoraBg)) {
                    musicPlayer.currentTrack?.let { MiniPlayer(musicPlayer, onOpen = { push(AppScreen.NowPlaying) }) }
                    NavigationBar(containerColor = SoraBg, tonalElevation = 0.dp) {
                        RootTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SoraText, selectedTextColor = SoraText,
                                    unselectedIconColor = SoraFaint, unselectedTextColor = SoraFaint,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAiQuick = true },
                    containerColor = SoraAccent,
                    contentColor = SoraAccentInk,
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Rounded.AutoAwesome, "Open Sora AI") }
            },
        ) { padding ->
            when (tab) {
                RootTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    progressEntries = repository.mediaProgress,
                    listeningSignals = repository.listeningSignals,
                    extensions = extensions,
                    manager = extensionManager,
                    onOpenSelection = ::openMedia,
                    onResumeProgress = { entry ->
                        resumeMediaProgress(
                            entry = entry,
                            extensions = extensions,
                            manager = extensionManager,
                            onOpenReader = { push(AppScreen.Reader(it)) },
                            onOpenPlayer = { push(AppScreen.VideoPlayer(it)) },
                            onFallback = ::openMedia,
                        )
                    },
                    onOpenBible = { push(AppScreen.Bible) },
                    onSearch = { tab = RootTab.MEDIA },
                )
                RootTab.MEDIA -> MediaScreen(
                    modifier = Modifier.padding(padding), extensions = extensions, extensionScanDone = extensionScanDone,
                    manager = extensionManager, libraryEntries = repository.library, listeningSignals = repository.listeningSignals,
                    progressEntries = repository.mediaProgress,
                    isSaved = repository::isSaved, onToggleSaved = repository::toggleSaved,
                    onOpenExtensions = { push(AppScreen.Extensions) }, onOpenDetails = ::openMedia,
                    onResumeProgress = { entry ->
                        resumeMediaProgress(
                            entry = entry,
                            extensions = extensions,
                            manager = extensionManager,
                            onOpenReader = { push(AppScreen.Reader(it)) },
                            onOpenPlayer = { push(AppScreen.VideoPlayer(it)) },
                            onFallback = ::openMedia,
                        )
                    },
                    onPlayMusic = { track, queue -> musicPlayer.play(track, queue, extensions) },
                )
                RootTab.LIBRARY -> LibraryScreen(
                    modifier = Modifier.padding(padding), entries = repository.library,
                    onOpenMedia = ::openMedia, onSearch = { tab = RootTab.MEDIA },
                )
                RootTab.GAMES -> GamesScreen(Modifier.padding(padding))
                RootTab.MORE -> MoreScreen(
                    modifier = Modifier.padding(padding),
                    extensionCount = extensions.count {
                        it.error == null && !(it.packageName == "com.night.sora" && it.declaredId == "sora.core.anilist")
                    },
                    activeDownloadCount = repository.downloads.count { it.status != com.night.sora.model.DownloadStatus.COMPLETED },
                    completedDownloadCount = repository.downloads.count { it.status == com.night.sora.model.DownloadStatus.COMPLETED },
                    onExtensions = { push(AppScreen.Extensions) },
                    onDownloads = { push(AppScreen.Downloads) },
                    onStatistics = { push(AppScreen.Statistics) },
                    onDataStorage = { push(AppScreen.DataStorage) },
                    onPlayerReader = { push(AppScreen.PlayerReaderSettings) },
                )
            }
        }

        if (showAiQuick) {
            AiQuickSheet(
                onDismiss = { showAiQuick = false },
                onSend = { repository.sendAiText(it) },
                onExpand = { showAiQuick = false; push(AppScreen.Ai) },
            )
        }
    } else {
        when (current) {
            AppScreen.Ai -> AiScreen(
                conversations = repository.aiConversations, activeConversationId = repository.activeAiConversationId,
                messages = repository.activeAiMessages, draft = repository.activeAiDraft,
                onDraft = repository::setActiveAiDraft, onSelectConversation = repository::selectAiConversation,
                onNewConversation = repository::newAiConversation,
                onSendText = { text, attachments -> repository.sendAiText(text, attachments) },
                onBack = ::pop,
            )
            AppScreen.Extensions -> ExtensionsScreen(extensions = extensions, onBack = ::pop, onRefresh = ::refreshExtensions, onOpen = { push(AppScreen.ExtensionDetail(it)) })
            AppScreen.Bible -> BibleScreen(onBack = ::pop)
            AppScreen.Downloads -> DownloadsScreen(
                downloads = repository.downloads,
                onBack = ::pop,
                onStatus = repository::setDownloadStatus,
                onRemove = repository::removeDownload,
                onClearCompleted = repository::clearCompletedDownloads,
            )
            AppScreen.Statistics -> StatisticsScreen(
                library = repository.library,
                activity = repository.activitySignals,
                listeningSignals = repository.listeningSignals,
                onBack = ::pop,
            )
            AppScreen.DataStorage -> DataStorageScreen(
                downloads = repository.downloads,
                onClearCatalogCache = mediaCatalogCache::clear,
                onBack = ::pop,
            )
            AppScreen.PlayerReaderSettings -> PlayerReaderSettingsScreen(
                extensions = extensions,
                onBack = ::pop,
            )
            is AppScreen.ExtensionDetail -> ExtensionDetailScreen(current.extension, onBack = ::pop)
            is AppScreen.MediaDetails -> MediaDetailScreen(
                selection = current.selection, extensions = extensions, manager = extensionManager,
                isSaved = repository::isSaved, onToggleSaved = repository::toggleSaved,
                onOpenReader = { push(AppScreen.Reader(it)) },
                onOpenPlayer = { push(AppScreen.VideoPlayer(it)) },
                onBack = ::pop,
            )
            is AppScreen.Reader -> ReaderScreen(
                current.session,
                onBack = ::pop,
                onProgress = { session, page, total ->
                    session.media?.let { media ->
                        repository.recordMediaProgress(
                            media,
                            session.itemId,
                            session.chapterTitle,
                            page.toLong(),
                            total.toLong(),
                            session.consumptionSourceId,
                            session.consumptionExtensionPackage,
                        )
                    }
                },
            )
            is AppScreen.VideoPlayer -> VideoPlayerScreen(
                current.session,
                onBack = ::pop,
                onProgress = { session, position, total ->
                    session.media?.let { media ->
                        repository.recordMediaProgress(
                            media,
                            session.itemId,
                            session.episodeTitle,
                            position,
                            total,
                            session.consumptionSourceId,
                            session.consumptionExtensionPackage,
                        )
                    }
                },
            )
            AppScreen.NowPlaying -> NowPlayingScreen(player = musicPlayer, isSaved = repository::isSaved, onToggleSaved = repository::toggleSaved, onBack = ::pop)
        }
    }
}

@Composable
private fun MiniPlayer(player: MusicPlaybackController, onOpen: () -> Unit) {
    val track = player.currentTrack ?: return
    val progress = if (player.durationMs > 0L) {
        (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D1D1A)),
    ) {
        if (player.durationMs > 0L) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(SoraSurfaceRaised)) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(SoraAccent))
            }
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Mini player" }
                .clickable(onClick = onOpen).padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFD8C38D))) {
                if (!track.artworkUrl.isNullOrBlank()) AsyncImage(track.artworkUrl, track.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = player::skipPrevious) { Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(20.dp)) }
            IconButton(onClick = player::togglePlayPause) {
                if (player.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiQuickSheet(onDismiss: () -> Unit, onSend: (String) -> Unit, onExpand: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF161614)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Sora AI", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("Local chat · AI provider not connected", color = SoraMuted, fontSize = 9.sp) }
                TextButton(onClick = onExpand) { Text("Full chat") }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(SoraAccent, RoundedCornerShape(99.dp))); Text("Messages stay local until a provider is connected", color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 7.dp)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                QuickSuggestion("What should I continue tonight?") { draft = "What should I continue tonight?" }
                QuickSuggestion("Find me another manga") { draft = "Find me a manga based on what I like." }
                QuickSuggestion("Give me something funny") { draft = "Show me something funny." }
                QuickSuggestion("Explain a Bible passage") { draft = "Explain this Bible passage for me." }
            }
            Surface(color = SoraSurface, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
                        BasicTextField(
                            value = draft, onValueChange = { draft = it }, minLines = 1, maxLines = 4,
                            textStyle = TextStyle(color = SoraText, fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (draft.isEmpty()) Text("Ask Sora anything…", color = SoraMuted, fontSize = 14.sp); inner() },
                        )
                    }
                    FilledIconButton(
                        onClick = { val clean = draft.trim(); if (clean.isNotEmpty()) { onSend(clean); draft = ""; onExpand() } },
                        modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black),
                    ) { Icon(Icons.Rounded.ArrowUpward, "Send") }
                }
            }
        }
    }
}

@Composable
private fun QuickSuggestion(text: String, onClick: () -> Unit) {
    Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(999.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Text(text, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
    }
}
