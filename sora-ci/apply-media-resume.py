from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    path.write_text(text.replace(old, new, 1))

# Keep catalog identity and consumption identity separate in persisted progress.
models = Path('sora-overlay/app/src/main/java/com/night/sora/model/Models.kt')
replace_once(
    models,
'''    val itemLabel: String,
    val position: Long,
    val total: Long,
''',
'''    val itemLabel: String,
    val position: Long,
    val total: Long,
    val resumeSourceId: String = sourceId,
    val resumeExtensionPackage: String = extensionPackage,
''',
    'progress consumption identity',
)
replace_once(
    models,
'''    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
)

/** One concrete stream resolved by a watch source extension. */
''',
'''    val media: ExtensionMediaSelection? = null,
    val itemId: String = "",
    val consumptionSourceId: String = "",
    val consumptionExtensionPackage: String = "",
)

/** One concrete stream resolved by a watch source extension. */
''',
    'reader consumption identity',
)
replace_once(
    models,
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
''',
    'player consumption identity',
)

# Persist consumption identity alongside metadata identity.
repo = Path('sora-overlay/app/src/main/java/com/night/sora/data/CoreRepository.kt')
replace_once(
    repo,
'''        itemLabel: String,
        position: Long,
        total: Long,
    ) {
''',
'''        itemLabel: String,
        position: Long,
        total: Long,
        resumeSourceId: String = selection.sourceId,
        resumeExtensionPackage: String = selection.extensionPackage,
    ) {
''',
    'record progress consumption args',
)
replace_once(
    repo,
'''            position = safePosition,
            total = total,
            subtitle = selection.subtitle,
''',
'''            position = safePosition,
            total = total,
            resumeSourceId = resumeSourceId.ifBlank { selection.sourceId },
            resumeExtensionPackage = resumeExtensionPackage.ifBlank { selection.extensionPackage },
            subtitle = selection.subtitle,
''',
    'record progress consumption values',
)
replace_once(
    repo,
'''                    position = item.optLong("position"),
                    total = item.optLong("total"),
                    subtitle = item.optString("subtitle"),
''',
'''                    position = item.optLong("position"),
                    total = item.optLong("total"),
                    resumeSourceId = item.optString("resumeSourceId").ifBlank { item.getString("sourceId") },
                    resumeExtensionPackage = item.optString("resumeExtensionPackage").ifBlank { item.getString("extensionPackage") },
                    subtitle = item.optString("subtitle"),
''',
    'load progress consumption values',
)
replace_once(
    repo,
'''                put("position", entry.position)
                put("total", entry.total)
                put("subtitle", entry.subtitle)
''',
'''                put("position", entry.position)
                put("total", entry.total)
                put("resumeSourceId", entry.resumeSourceId)
                put("resumeExtensionPackage", entry.resumeExtensionPackage)
                put("subtitle", entry.subtitle)
''',
    'persist progress consumption values',
)

# Every session remembers the actual source that resolved pages/streams.
detail = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt')
replace_once(
    detail,
'''    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
''',
'''    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val target = consumption ?: active.takeIf { selectionCanConsume(it, extensions) } ?: return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
''',
    'movie target identity',
)
replace_once(
    detail,
'''                initialStream = index,
                media = active,
                itemId = active.id,
''',
'''                initialStream = index,
                media = active,
                itemId = target.id,
                consumptionSourceId = target.sourceId,
                consumptionExtensionPackage = target.extensionPackage,
''',
    'movie session identity',
)
replace_once(
    detail,
'''                            media = active,
                            itemId = row.id,
                        )
''',
'''                            media = active,
                            itemId = row.id,
                            consumptionSourceId = target.sourceId,
                            consumptionExtensionPackage = target.extensionPackage,
                        )
''',
    'reader session identity',
)
# There are now two remaining media/itemId session snippets: anime/tv player only.
replace_once(
    detail,
'''                            streams = streams,
                            media = active,
                            itemId = row.id,
                        )
''',
'''                            streams = streams,
                            media = active,
                            itemId = row.id,
                            consumptionSourceId = target.sourceId,
                            consumptionExtensionPackage = target.extensionPackage,
                        )
''',
    'episode session identity',
)

# Make the already-tested parsers reusable by the resume resolver in the same module.
replace_once(
    detail,
'private fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {',
'internal fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {',
    'playback parser visibility',
)
replace_once(
    detail,
'private fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {',
'internal fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {',
    'reader parser visibility',
)

# Core callback persists the source that actually served the child content.
app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
replace_once(
    app,
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
''',
    'reader progress source persistence',
)
replace_once(
    app,
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
''',
    'video progress source persistence',
)

# Expose a dedicated resume callback from Media instead of treating Continue as Details.
media = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
replace_once(
    media,
'''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
''',
'''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
''',
    'media resume callback',
)
replace_once(
    media,
'''                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'''                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,
''',
    'anime resume pass',
)
replace_once(
    media,
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
''',
'''                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress,
''',
    'movie resume pass',
)
replace_once(
    media,
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
''',
    'anime resume signature',
)
replace_once(
    media,
'''            ProgressLandscapeRail(continued, onOpen)
''',
'''            ProgressLandscapeRail(continued, onResume)
''',
    'anime progress action',
)
replace_once(
    media,
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
''',
    'movie resume signature',
)
# Second ProgressLandscapeRail occurrence belongs to Movies/TV.
text = media.read_text()
old = '            ProgressLandscapeRail(continued, onOpen)\n'
if text.count(old) != 1:
    raise SystemExit(f'movie progress action: expected 1 remaining match, found {text.count(old)}')
media.write_text(text.replace(old, '            ProgressLandscapeRail(continued, onResume)\n', 1))
replace_once(
    media,
'''private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onOpen: (ExtensionMediaSelection) -> Unit) {
''',
'''private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onResume: (MediaProgressEntry) -> Unit) {
''',
    'progress rail callback type',
)
replace_once(
    media,
'''            Column(Modifier.width(190.dp).clickable { onOpen(entry.toMediaSelection()) }) {
''',
'''            Column(Modifier.width(190.dp).clickable { onResume(entry) }) {
''',
    'progress rail click',
)

# App-level resume resolver keeps extensions provider-neutral and falls back to Details if source vanished.
replace_once(
    app,
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
''',
    'app resume callback',
)

# Filter fully-complete entries out of Continue; saved Library still retains the title.
text = media.read_text()
old = 'val continued = progressEntries.filter { it.contentType == type }.sortedByDescending { it.updatedAt }'
new = 'val continued = progressEntries.filter { it.contentType == type && it.progress < .999f }.sortedByDescending { it.updatedAt }'
if text.count(old) != 2:
    raise SystemExit(f'continue completion filter: expected 2 matches, found {text.count(old)}')
media.write_text(text.replace(old, new))

# Dedicated resolver file; created separately by the workflow after this script executes.
