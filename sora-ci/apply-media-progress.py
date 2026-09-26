from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    path.write_text(text.replace(old, new, 1))

# Models: Core-owned progress record + session identity/resume fields.
models = Path('sora-overlay/app/src/main/java/com/night/sora/model/Models.kt')
old = '''data class ListeningSignal(
    val artistId: String,
'''
new = '''data class MediaProgressEntry(
    val mediaId: String,
    val sourceId: String,
    val extensionPackage: String,
    val contentType: ContentType,
    val title: String,
    val itemId: String,
    val itemLabel: String,
    val position: Long,
    val total: Long,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (total > 0L) (position.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    fun toMediaSelection() = ExtensionMediaSelection(
        id = mediaId,
        sourceId = sourceId,
        extensionPackage = extensionPackage,
        type = contentType,
        title = title,
        subtitle = subtitle,
        artworkUrl = artworkUrl,
    )
}

data class ListeningSignal(
    val artistId: String,
'''
replace_once(models, old, new, 'progress model insertion')

old = '''data class ReaderSession(
    val title: String,
    val chapterTitle: String,
    val sourceName: String,
    val pages: List<ReaderPage>,
    val initialPage: Int = 0,
)
'''
new = '''data class ReaderSession(
    val title: String,
    val chapterTitle: String,
    val sourceName: String,
    val pages: List<ReaderPage>,
    val initialPage: Int = 0,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
)
'''
replace_once(models, old, new, 'reader session progress fields')

old = '''data class PlaybackSession(
    val title: String,
    val episodeTitle: String,
    val sourceName: String,
    val streams: List<PlaybackStream>,
    val initialStream: Int = 0,
)
'''
new = '''data class PlaybackSession(
    val title: String,
    val episodeTitle: String,
    val sourceName: String,
    val streams: List<PlaybackStream>,
    val initialStream: Int = 0,
    val initialPositionMs: Long = 0L,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
)
'''
replace_once(models, old, new, 'playback session progress fields')

# Repository: persist one progress entry per parent title.
repo = Path('sora-overlay/app/src/main/java/com/night/sora/data/CoreRepository.kt')
replace_once(repo,
'''import com.night.sora.model.ListeningSignal
''',
'''import com.night.sora.model.ListeningSignal
import com.night.sora.model.MediaProgressEntry
''',
'repository progress import')
replace_once(repo,
'''    val listeningSignals = mutableStateListOf<ListeningSignal>()
    val downloads = mutableStateListOf<DownloadEntry>()
''',
'''    val listeningSignals = mutableStateListOf<ListeningSignal>()
    val mediaProgress = mutableStateListOf<MediaProgressEntry>()
    val downloads = mutableStateListOf<DownloadEntry>()
''',
'repository progress state')
replace_once(repo,
'''        loadListeningSignals()
        loadDownloads()
''',
'''        loadListeningSignals()
        loadMediaProgress()
        loadDownloads()
''',
'repository progress load')

marker = '''    fun recordActivity(selection: ExtensionMediaSelection, action: String) {
        val now = System.currentTimeMillis()
        activitySignals.add(0, ActivitySignal(now, selection.type, selection.title, action, now))
        while (activitySignals.size > 500) activitySignals.removeLast()
        persistActivitySignals()
    }
'''
addition = marker + '''
    fun recordMediaProgress(
        selection: ExtensionMediaSelection,
        itemId: String,
        itemLabel: String,
        position: Long,
        total: Long,
    ) {
        if (selection.type !in setOf(ContentType.ANIME, ContentType.MANGA, ContentType.MOVIE, ContentType.TV)) return
        if (total <= 0L) return
        val safePosition = position.coerceIn(0L, total)
        if (safePosition <= 0L) return
        val entry = MediaProgressEntry(
            mediaId = selection.id,
            sourceId = selection.sourceId,
            extensionPackage = selection.extensionPackage,
            contentType = selection.type,
            title = selection.title,
            itemId = itemId.ifBlank { selection.id },
            itemLabel = itemLabel.ifBlank { selection.title },
            position = safePosition,
            total = total,
            subtitle = selection.subtitle,
            artworkUrl = selection.artworkUrl,
            updatedAt = System.currentTimeMillis(),
        )
        val index = mediaProgress.indexOfFirst {
            it.mediaId == selection.id && it.sourceId == selection.sourceId && it.extensionPackage == selection.extensionPackage
        }
        if (index >= 0) mediaProgress.removeAt(index)
        mediaProgress.add(0, entry)
        while (mediaProgress.size > 100) mediaProgress.removeLast()
        persistMediaProgress()
    }
'''
replace_once(repo, marker, addition, 'repository progress recorder')

marker = '''    private fun loadDownloads() {
'''
addition = '''    private fun loadMediaProgress() {
        val raw = prefs.getString(KEY_MEDIA_PROGRESS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                mediaProgress += MediaProgressEntry(
                    mediaId = item.getString("mediaId"),
                    sourceId = item.getString("sourceId"),
                    extensionPackage = item.getString("extensionPackage"),
                    contentType = ContentType.valueOf(item.getString("contentType")),
                    title = item.getString("title"),
                    itemId = item.optString("itemId"),
                    itemLabel = item.optString("itemLabel"),
                    position = item.optLong("position"),
                    total = item.optLong("total"),
                    subtitle = item.optString("subtitle"),
                    artworkUrl = item.optNullableString("artworkUrl"),
                    updatedAt = item.optLong("updatedAt"),
                )
            }
        }
    }

    private fun persistMediaProgress() {
        val array = JSONArray()
        mediaProgress.take(100).forEach { entry ->
            array.put(JSONObject().apply {
                put("mediaId", entry.mediaId)
                put("sourceId", entry.sourceId)
                put("extensionPackage", entry.extensionPackage)
                put("contentType", entry.contentType.name)
                put("title", entry.title)
                put("itemId", entry.itemId)
                put("itemLabel", entry.itemLabel)
                put("position", entry.position)
                put("total", entry.total)
                put("subtitle", entry.subtitle)
                putNullable("artworkUrl", entry.artworkUrl)
                put("updatedAt", entry.updatedAt)
            })
        }
        prefs.edit().putString(KEY_MEDIA_PROGRESS, array.toString()).apply()
    }

''' + marker
replace_once(repo, marker, addition, 'repository progress persistence')
replace_once(repo,
'''        const val KEY_LISTENING = "listening_v1"
        const val KEY_DOWNLOADS = "downloads_v1"
''',
'''        const val KEY_LISTENING = "listening_v1"
        const val KEY_MEDIA_PROGRESS = "media_progress_v1"
        const val KEY_DOWNLOADS = "downloads_v1"
''',
'repository progress key')

# Detail hand-offs include parent identity and child item identity.
detail = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt')
replace_once(detail,
'''                streams = movieStreams,
                initialStream = index,
''',
'''                streams = movieStreams,
                initialStream = index,
                media = active,
                itemId = active.id,
''',
'movie progress identity')
replace_once(detail,
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
''',
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
                            media = active,
                            itemId = row.id,
''',
'manga progress identity')
replace_once(detail,
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
''',
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
                            media = active,
                            itemId = row.id,
''',
'episode progress identity')

# Reader emits real page progress.
reader = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/ReaderScreen.kt')
replace_once(reader,
'''fun ReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit,
) {
''',
'''fun ReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit,
    onProgress: (ReaderSession, Int, Int) -> Unit = { _, _, _ -> },
) {
''',
'reader progress callback')
marker = '''    LaunchedEffect(pagerState.currentPage, mode) {
        if (mode == ReaderMode.PAGED) currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex)
    }
'''
addition = marker + '''    LaunchedEffect(currentPage, pages.size, session) {
        if (pages.isNotEmpty()) onProgress(session, currentPage + 1, pages.size)
    }
'''
replace_once(reader, marker, addition, 'reader progress reporting')

# Video player resumes and emits time progress once per second.
video = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/VideoPlayerScreen.kt')
replace_once(video,
'''fun VideoPlayerScreen(
    session: PlaybackSession,
    onBack: () -> Unit,
) {
''',
'''fun VideoPlayerScreen(
    session: PlaybackSession,
    onBack: () -> Unit,
    onProgress: (PlaybackSession, Long, Long) -> Unit = { _, _, _ -> },
) {
''',
'video progress callback')
replace_once(video,
'''    var positionMs by remember { mutableLongStateOf(0L) }
''',
'''    var positionMs by remember(session) { mutableLongStateOf(session.initialPositionMs.coerceAtLeast(0L)) }
''',
'video initial progress')
marker = '''    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            bufferedPercent = player.bufferedPercentage.coerceIn(0, 100)
            isPlaying = player.isPlaying
            playbackState = player.playbackState
            playbackError = player.playerError?.message ?: playbackError
            delay(250)
        }
    }
'''
addition = marker + '''
    LaunchedEffect(player, session) {
        while (isActive) {
            delay(1_000)
            val current = player.currentPosition.coerceAtLeast(0L)
            val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (current > 0L && total > 0L) onProgress(session, current, total)
        }
    }
'''
replace_once(video, marker, addition, 'video progress reporting')

# App connects player/reader callbacks to Core and passes progress into Media.
app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
replace_once(app,
'''                    manager = extensionManager, libraryEntries = repository.library, listeningSignals = repository.listeningSignals,
''',
'''                    manager = extensionManager, libraryEntries = repository.library, listeningSignals = repository.listeningSignals,
                    progressEntries = repository.mediaProgress,
''',
'app media progress state')
replace_once(app,
'''            is AppScreen.Reader -> ReaderScreen(current.session, onBack = ::pop)
            is AppScreen.VideoPlayer -> VideoPlayerScreen(current.session, onBack = ::pop)
''',
'''            is AppScreen.Reader -> ReaderScreen(
                current.session,
                onBack = ::pop,
                onProgress = { session, page, total ->
                    session.media?.let { media ->
                        repository.recordMediaProgress(media, session.itemId, session.chapterTitle, page.toLong(), total.toLong())
                    }
                },
            )
            is AppScreen.VideoPlayer -> VideoPlayerScreen(
                current.session,
                onBack = ::pop,
                onProgress = { session, position, total ->
                    session.media?.let { media ->
                        repository.recordMediaProgress(media, session.itemId, session.episodeTitle, position, total)
                    }
                },
            )
''',
'app progress callbacks')

# Media screen gets truthful Continue shelves backed by actual progress.
media = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
replace_once(media,
'''import com.night.sora.model.ListeningSignal
''',
'''import com.night.sora.model.ListeningSignal
import com.night.sora.model.MediaProgressEntry
''',
'media progress import')
replace_once(media,
'''    libraryEntries: List<LibraryEntry>,
    listeningSignals: List<ListeningSignal>,
''',
'''    libraryEntries: List<LibraryEntry>,
    listeningSignals: List<ListeningSignal>,
    progressEntries: List<MediaProgressEntry>,
''',
'media progress parameter')
replace_once(media,
'''                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,
                libraryEntries = libraryEntries, selection = ::selection, isSaved = isSaved,
''',
'''                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,
                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
''',
'anime progress pass')
replace_once(media,
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'movie progress pass')
replace_once(media,
'''    topRows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
''',
'''    topRows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    progressEntries: List<MediaProgressEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
''',
'anime progress signature')
replace_once(media,
'''    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
''',
'''    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    val continued = progressEntries.filter { it.contentType == type }.sortedByDescending { it.updatedAt }
''',
'anime progress rows')
anchor = '''        item {
            MediaSectionTitle("In your library", if (type == ContentType.ANIME) "Anime you saved in Sora" else "Manga you saved in Sora")
'''
insert = '''        if (continued.isNotEmpty()) item {
            MediaSectionTitle(
                if (type == ContentType.ANIME) "Continue watching" else "Continue reading",
                "Real progress from your last session",
            )
            ProgressLandscapeRail(continued, onOpen)
        }
''' + anchor
replace_once(media, anchor, insert, 'anime continue shelf')
replace_once(media,
'''    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
''',
'''    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    progressEntries: List<MediaProgressEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
''',
'movie progress signature')
replace_once(media,
'''    val selected = rows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
''',
'''    val selected = rows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    val continued = progressEntries.filter { it.contentType == type }.sortedByDescending { it.updatedAt }
''',
'movie progress rows')
anchor = '''        item {
            MediaSectionTitle("In your library", "${if (type == ContentType.MOVIE) "Movies" else "Series"} you saved in Sora")
'''
insert = '''        if (continued.isNotEmpty()) item {
            MediaSectionTitle("Continue watching", "Real playback progress from your last session")
            ProgressLandscapeRail(continued, onOpen)
        }
''' + anchor
replace_once(media, anchor, insert, 'movie continue shelf')

marker = '''@Composable
private fun ContinueLandscapeRail(entries: List<LibraryEntry>, onOpen: (ExtensionMediaSelection) -> Unit) {
'''
progress_rail = '''@Composable
private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(entries.take(8), key = { "progress-${it.extensionPackage}-${it.sourceId}-${it.mediaId}" }) { entry ->
            Column(Modifier.width(190.dp).clickable { onOpen(entry.toMediaSelection()) }) {
                Box(Modifier.fillMaxWidth().height(107.dp).clip(RoundedCornerShape(7.dp)).background(SoraSurface)) {
                    if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                        color = SoraAccent,
                        trackColor = Color(0xFF555248),
                    )
                }
                Text(entry.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "${entry.itemLabel} · ${(entry.progress * 100f).toInt().coerceIn(0, 100)}%",
                    color = SoraMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

''' + marker
replace_once(media, marker, progress_rail, 'progress landscape rail')
