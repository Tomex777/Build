from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    path.write_text(text.replace(old, new, 1))

models = Path('sora-overlay/app/src/main/java/com/night/sora/model/Models.kt')
replace_once(models,
'''    val position: Long,
    val total: Long,
    val subtitle: String = "",
''',
'''    val position: Long,
    val total: Long,
    val resumeSourceId: String = sourceId,
    val resumeExtensionPackage: String = extensionPackage,
    val subtitle: String = "",
''', 'progress resume identity')
replace_once(models,
'''    val initialPage: Int = 0,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
)
''',
'''    val initialPage: Int = 0,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
    val consumptionSourceId: String = "",
    val consumptionExtensionPackage: String = "",
)
''', 'reader consumption identity')
replace_once(models,
'''    val initialPositionMs: Long = 0L,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
)
''',
'''    val initialPositionMs: Long = 0L,
    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
    val consumptionSourceId: String = "",
    val consumptionExtensionPackage: String = "",
)
''', 'player consumption identity')

repo = Path('sora-overlay/app/src/main/java/com/night/sora/data/CoreRepository.kt')
replace_once(repo,
'''        position: Long,
        total: Long,
    ) {
''',
'''        position: Long,
        total: Long,
        resumeSourceId: String = selection.sourceId,
        resumeExtensionPackage: String = selection.extensionPackage,
    ) {
''', 'record progress args')
replace_once(repo,
'''            position = safePosition,
            total = total,
            subtitle = selection.subtitle,
''',
'''            position = safePosition,
            total = total,
            resumeSourceId = resumeSourceId.ifBlank { selection.sourceId },
            resumeExtensionPackage = resumeExtensionPackage.ifBlank { selection.extensionPackage },
            subtitle = selection.subtitle,
''', 'record progress resume identity')
replace_once(repo,
'''                    position = item.optLong("position"),
                    total = item.optLong("total"),
                    subtitle = item.optString("subtitle"),
''',
'''                    position = item.optLong("position"),
                    total = item.optLong("total"),
                    resumeSourceId = item.optString("resumeSourceId").ifBlank { item.getString("sourceId") },
                    resumeExtensionPackage = item.optString("resumeExtensionPackage").ifBlank { item.getString("extensionPackage") },
                    subtitle = item.optString("subtitle"),
''', 'load resume identity')
replace_once(repo,
'''                put("position", entry.position)
                put("total", entry.total)
                put("subtitle", entry.subtitle)
''',
'''                put("position", entry.position)
                put("total", entry.total)
                put("resumeSourceId", entry.resumeSourceId)
                put("resumeExtensionPackage", entry.resumeExtensionPackage)
                put("subtitle", entry.subtitle)
''', 'persist resume identity')

detail = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt')
replace_once(detail,
'''    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
''',
'''    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val target = consumption ?: active.takeIf { selectionCanConsume(it, extensions) } ?: return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
''', 'movie consumption target')
replace_once(detail,
'''                streams = movieStreams,
                initialStream = index,
                media = active,
                itemId = active.id,
''',
'''                streams = movieStreams,
                initialStream = index,
                media = active,
                itemId = target.id,
                consumptionSourceId = target.sourceId,
                consumptionExtensionPackage = target.extensionPackage,
''', 'movie resume identity')
replace_once(detail,
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
                            media = active,
                            itemId = row.id,
''',
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
                            media = active,
                            itemId = row.id,
                            consumptionSourceId = target.sourceId,
                            consumptionExtensionPackage = target.extensionPackage,
''', 'manga resume identity')
replace_once(detail,
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
                            media = active,
                            itemId = row.id,
''',
'''                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
                            media = active,
                            itemId = row.id,
                            consumptionSourceId = target.sourceId,
                            consumptionExtensionPackage = target.extensionPackage,
''', 'episode resume identity')
replace_once(detail,
'private fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {',
'internal fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {', 'playback parser visibility')
replace_once(detail,
'private fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {',
'internal fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {', 'reader parser visibility')

app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
replace_once(app,
'''                        repository.recordMediaProgress(media, session.itemId, session.chapterTitle, page.toLong(), total.toLong())
''',
'''                        repository.recordMediaProgress(
                            media,
                            session.itemId,
                            session.chapterTitle,
                            page.toLong(),
                            total.toLong(),
                            session.consumptionSourceId,
                            session.consumptionExtensionPackage,
                        )
''', 'reader progress source')
replace_once(app,
'''                        repository.recordMediaProgress(media, session.itemId, session.episodeTitle, position, total)
''',
'''                        repository.recordMediaProgress(
                            media,
                            session.itemId,
                            session.episodeTitle,
                            position,
                            total,
                            session.consumptionSourceId,
                            session.consumptionExtensionPackage,
                        )
''', 'video progress source')
replace_once(app,
'''                    onOpenExtensions = { push(AppScreen.Extensions) }, onOpenDetails = ::openMedia,
                    onPlayMusic = { track, queue -> musicPlayer.play(track, queue, extensions) },
''',
'''                    onOpenExtensions = { push(AppScreen.Extensions) }, onOpenDetails = ::openMedia,
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
''', 'app resume callback')

media = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
replace_once(media,
'''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
''',
'''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
''', 'media resume callback')
replace_once(media,
'''                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'''                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,
''', 'anime resume pass')
replace_once(media,
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress,
''', 'movie resume pass')
replace_once(media,
'''    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
''',
'''    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
    onResume: (MediaProgressEntry) -> Unit,
) {
    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
''', 'anime resume signature')
replace_once(media,
'''    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull()
''',
'''    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
    onResume: (MediaProgressEntry) -> Unit,
) {
    val selected = rows.firstOrNull()
''', 'movie resume signature')
text = media.read_text()
old = '            ProgressLandscapeRail(continued, onOpen)\n'
if text.count(old) != 2:
    raise SystemExit(f'progress rail actions: expected 2 matches, found {text.count(old)}')
text = text.replace(old, '            ProgressLandscapeRail(continued, onResume)\n')
old_complete = 'val continued = progressEntries.filter { it.contentType == type }.sortedByDescending { it.updatedAt }'
if text.count(old_complete) != 2:
    raise SystemExit(f'continue completion filters: expected 2 matches, found {text.count(old_complete)}')
text = text.replace(old_complete, 'val continued = progressEntries.filter { it.contentType == type && it.progress < .999f }.sortedByDescending { it.updatedAt }')
media.write_text(text)
replace_once(media,
'''private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onOpen: (ExtensionMediaSelection) -> Unit) {
''',
'''private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onResume: (MediaProgressEntry) -> Unit) {
''', 'progress rail signature')
replace_once(media,
'''            Column(Modifier.width(190.dp).clickable { onOpen(entry.toMediaSelection()) }) {
''',
'''            Column(Modifier.width(190.dp).clickable { onResume(entry) }) {
''', 'progress rail click')
