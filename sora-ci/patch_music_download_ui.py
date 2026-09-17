from pathlib import Path

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')

def rep(path, old, new):
    p=Path(path); text=p.read_text()
    if old not in text: raise SystemExit(f'missing block in {p}: {old[:100]!r}')
    p.write_text(text.replace(old,new,1))

# One persisted Music source only. Catalog/search no longer aggregate multiple music providers.
media=ROOT/'ui/screens/MediaScreen.kt'
rep(media,'import android.net.ConnectivityManager\n','import android.content.Context\nimport android.net.ConnectivityManager\n')
rep(media,'''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
''','''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    val sourcePrefs = remember(context) { context.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE) }
''')
rep(media,'''        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val cached = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)
        rows = cached.map { it.toBrowseCard() }

        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
        if (providers.isEmpty()) return
''','''        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val key = typeKey(requestType)
        val allProviders = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
        val providers = if (requestType == ContentType.MUSIC) {
            val saved = sourcePrefs.getString("preferred_music", null)
            val chosen = allProviders.firstOrNull { (ext, source) -> "${ext.packageName}|${source.id}" == saved }
                ?: allProviders.sortedWith(compareBy({ it.first.declaredName.lowercase() }, { it.second.name.lowercase() })).firstOrNull()
            if (chosen != null) {
                val value = "${chosen.first.packageName}|${chosen.second.id}"
                if (saved != value) sourcePrefs.edit().putString("preferred_music", value).apply()
                listOf(chosen)
            } else emptyList()
        } else allProviders
        val cachedAll = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)
        val cached = if (requestType == ContentType.MUSIC && providers.isNotEmpty()) {
            val (ext, source) = providers.first()
            cachedAll.filter { it.extensionPackage == ext.packageName && it.sourceId == source.id }
        } else cachedAll
        rows = cached.map { it.toBrowseCard() }
        if (providers.isEmpty()) return
''')

# Default source settings include Music; unlike watch/read, Music must always have one active source if available.
settings=ROOT/'ui/screens/PlayerReaderSettingsScreen.kt'
rep(settings,'''            DefaultSourceTarget(ContentType.TV, "Series", "Default extension used to resolve episodes", "episodes", "tv"),
        )
''','''            DefaultSourceTarget(ContentType.TV, "Series", "Default extension used to resolve episodes", "episodes", "tv"),
            DefaultSourceTarget(ContentType.MUSIC, "Music", "Default extension used for playback and downloads", "streams", "music"),
        )
''')
rep(settings,'''    var pickerTarget by remember { mutableStateOf<DefaultSourceTarget?>(null) }
    var preferenceEpoch by remember { mutableIntStateOf(0) }

    Scaffold(
''','''    var pickerTarget by remember { mutableStateOf<DefaultSourceTarget?>(null) }
    var preferenceEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(extensions) {
        val target = targets.first { it.type == ContentType.MUSIC }
        val options = compatibleSources(extensions, target)
        val selected = prefs.getString(preferenceKey(target), null)
        if (options.isNotEmpty() && options.none { it.persistedValue == selected }) {
            prefs.edit().putString(preferenceKey(target), options.first().persistedValue).apply()
            preferenceEpoch++
        }
    }

    Scaffold(
''')
rep(settings,'''                        subtitle = selected?.let { "${it.source.name} · ${it.extension.declaredName}" }
                            ?: if (options.isEmpty()) "No compatible source installed" else "Ask each title",
''','''                        subtitle = selected?.let { "${it.source.name} · ${it.extension.declaredName}" }
                            ?: if (options.isEmpty()) "No compatible source installed" else if (target.type == ContentType.MUSIC) "Select a music source" else "Ask each title",
''')
rep(settings,'''                SourceChoiceRow(
                    title = "Ask each title",
                    subtitle = "Do not force a default source",
                    selected = selectedValue == null,
                    onClick = {
                        prefs.edit().remove(preferenceKey(target)).apply()
                        preferenceEpoch++
                        pickerTarget = null
                    },
                )
                options.forEach { option ->
''','''                if (target.type != ContentType.MUSIC) {
                    SourceChoiceRow(
                        title = "Ask each title",
                        subtitle = "Do not force a default source",
                        selected = selectedValue == null,
                        onClick = {
                            prefs.edit().remove(preferenceKey(target)).apply()
                            preferenceEpoch++
                            pickerTarget = null
                        },
                    )
                }
                options.forEach { option ->
''')
rep(settings,'''                    "These defaults use the same source preference Sora remembers when you choose Change source on a title. Clearing one makes Sora ask per title again.",
''','''                    "Music always uses one selected source at a time. Anime, Manga, Movies and Series can still ask per title when their default is cleared.",
''')

# Spotui-style Now Playing download control becomes real.
now=ROOT/'ui/screens/NowPlayingScreen.kt'
rep(now,'import com.night.sora.model.ExtensionMediaSelection\n','import com.night.sora.model.DownloadEntry\nimport com.night.sora.model.DownloadStatus\nimport com.night.sora.model.ExtensionMediaSelection\n')
rep(now,'''    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onBack: () -> Unit,
) {
    val track = player.currentTrack
    var queueOpen by remember { mutableStateOf(false) }
''','''    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    downloadEntry: (ExtensionMediaSelection) -> DownloadEntry?,
    onDownload: (ExtensionMediaSelection) -> Unit,
    onRemoveDownload: (ExtensionMediaSelection) -> Unit,
    onBack: () -> Unit,
) {
    val track = player.currentTrack
    var queueOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
''')
rep(now,'''    val saved = isSaved(track)

    Column(
''','''    val saved = isSaved(track)
    val download = downloadEntry(track)
    val downloaded = download?.status == DownloadStatus.COMPLETED && !download.filePath.isNullOrBlank()
    val downloadBusy = download?.status == DownloadStatus.DOWNLOADING || download?.status == DownloadStatus.QUEUED

    Column(
''')
rep(now,'''            IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "Track options", tint = SoraText) }
''','''            Box {
                IconButton(onClick = { optionsOpen = true }) { Icon(Icons.Rounded.MoreVert, "Track options", tint = SoraText) }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (downloaded) "Remove download" else if (downloadBusy) "Downloading" else if (download?.status == DownloadStatus.FAILED) "Retry download" else "Download") },
                        leadingIcon = { Icon(if (downloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download, null) },
                        enabled = !downloadBusy,
                        onClick = { optionsOpen = false; if (downloaded) onRemoveDownload(track) else onDownload(track) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (saved) "Remove from Your Music" else "Save to Your Music") },
                        leadingIcon = { Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                        onClick = { optionsOpen = false; onToggleSaved(track) },
                    )
                }
            }
''')
rep(now,'''            PlayerSecondary(Icons.Rounded.Lyrics, "Lyrics")
            PlayerSecondary(Icons.Rounded.QueueMusic, "Queue", onClick = { queueOpen = true })
            PlayerSecondary(Icons.Rounded.Download, "Download")
''','''            PlayerSecondary(Icons.Rounded.Lyrics, "Lyrics")
            PlayerSecondary(Icons.Rounded.QueueMusic, "Queue", onClick = { queueOpen = true })
            PlayerSecondary(
                if (downloaded) Icons.Rounded.OfflinePin else Icons.Rounded.Download,
                when {
                    downloaded -> "Downloaded"
                    downloadBusy -> "Downloading"
                    download?.status == DownloadStatus.FAILED -> "Retry"
                    else -> "Download"
                },
                enabled = !downloadBusy,
                onClick = { if (downloaded) onRemoveDownload(track) else onDownload(track) },
            )
''')
rep(now,'''private fun PlayerSecondary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = SoraMuted)
        Text(label, color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
''','''private fun PlayerSecondary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = if (enabled) SoraMuted else SoraFaint)
        Text(label, color = if (enabled) SoraMuted else SoraFaint, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
''')

# Downloads screen only exposes operations that really exist: progress, retry and delete.
more=ROOT/'ui/screens/MoreScreen.kt'
rep(more,'''    onBack: () -> Unit,
    onStatus: (String, DownloadStatus) -> Unit,
    onRemove: (String) -> Unit,
    onClearCompleted: () -> Unit,
''','''    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCompleted: () -> Unit,
''')
rep(more,'ActiveDownloadRow(entry, onStatus, onRemove)','ActiveDownloadRow(entry, onRetry, onRemove)')
rep(more,'private fun ActiveDownloadRow(entry: DownloadEntry, onStatus: (String, DownloadStatus) -> Unit, onRemove: (String) -> Unit) {','private fun ActiveDownloadRow(entry: DownloadEntry, onRetry: (String) -> Unit, onRemove: (String) -> Unit) {')
rep(more,'''        when (entry.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> IconButton(onClick = { onStatus(entry.id, DownloadStatus.PAUSED) }) { Icon(Icons.Rounded.Pause, "Pause") }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> IconButton(onClick = { onStatus(entry.id, DownloadStatus.QUEUED) }) { Icon(Icons.Rounded.PlayArrow, "Resume") }
            DownloadStatus.COMPLETED -> Unit
        }
''','''        if (entry.status == DownloadStatus.FAILED) {
            IconButton(onClick = { onRetry(entry.id) }) { Icon(Icons.Rounded.Refresh, "Retry") }
        }
''')

# Wire manager through SoraApp and make offline resolver process-wide playback truth.
app=ROOT/'ui/SoraApp.kt'
rep(app,'import com.night.sora.extension.ExtensionManager\n','import com.night.sora.download.MusicDownloadManager\nimport com.night.sora.extension.ExtensionManager\n')
rep(app,'''    val musicPlayer = remember { MusicPlaybackRuntime.get(context.applicationContext, extensionManager) }
    var tab by remember { mutableStateOf(RootTab.HOME) }
''','''    val musicPlayer = remember { MusicPlaybackRuntime.get(context.applicationContext, extensionManager) }
    val musicDownloads = remember { MusicDownloadManager(context.applicationContext, repository, extensionManager) }
    var tab by remember { mutableStateOf(RootTab.HOME) }
''')
rep(app,'''    LaunchedEffect(extensions) { musicPlayer.updateExtensions(extensions) }
''','''    LaunchedEffect(extensions) {
        musicPlayer.updateExtensions(extensions)
        musicDownloads.updateExtensions(extensions)
        musicPlayer.setOfflineResolver(musicDownloads::localFileFor)
    }
''')
rep(app,'''            AppScreen.Downloads -> DownloadsScreen(
                downloads = repository.downloads,
                onBack = ::pop,
                onStatus = repository::setDownloadStatus,
                onRemove = repository::removeDownload,
                onClearCompleted = repository::clearCompletedDownloads,
            )
''','''            AppScreen.Downloads -> DownloadsScreen(
                downloads = repository.downloads,
                onBack = ::pop,
                onRetry = musicDownloads::retry,
                onRemove = musicDownloads::remove,
                onClearCompleted = musicDownloads::clearCompleted,
            )
''')
rep(app,'''            AppScreen.NowPlaying -> NowPlayingScreen(player = musicPlayer, isSaved = repository::isSaved, onToggleSaved = repository::toggleSaved, onBack = ::pop)
''','''            AppScreen.NowPlaying -> NowPlayingScreen(
                player = musicPlayer,
                isSaved = repository::isSaved,
                onToggleSaved = repository::toggleSaved,
                downloadEntry = musicDownloads::entryFor,
                onDownload = musicDownloads::download,
                onRemoveDownload = musicDownloads::remove,
                onBack = ::pop,
            )
''')

# Manual extension install button was already implemented; expose it in the actual Extensions toolbar.
ext=ROOT/'ui/screens/ExtensionsScreen.kt'
rep(ext,'''actions = { IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh") } },''','''actions = {
                    ExtensionInstallButton()
                    IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
                },''')
