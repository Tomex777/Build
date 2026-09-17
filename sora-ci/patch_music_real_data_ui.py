from pathlib import Path
import re

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')
media_path = ROOT / 'ui/screens/MediaScreen.kt'
app_path = ROOT / 'ui/SoraApp.kt'

media = media_path.read_text()
app = app_path.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


# Keep listening taste keyed by artist, not by individual track id.
app = replace_once(
    app,
    'repository.recordListening(track.id, track.subtitle.substringBefore(" · ").ifBlank { track.title })',
    'val artistName = track.subtitle.substringBefore(" · ").ifBlank { track.title }\n            repository.recordListening(artistName.trim().lowercase(), artistName)',
    'artist listening identity',
)

# Give Music access to its existing extensions route and local panel selector.
media = replace_once(
    media,
    '''            destination == MediaDestination.MUSIC -> MusicSurface(\n                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,\n                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,\n            )''',
    '''            destination == MediaDestination.MUSIC -> MusicSurface(\n                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,\n                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,\n                onOpenExtensions = onOpenExtensions, onSelectPanel = { musicLocal = it },\n            )''',
    'MusicSurface call',
)

old_surface = '''@Composable\nprivate fun MusicSurface(\n    panel: MusicLocal,\n    rows: List<BrowseCard>,\n    libraryEntries: List<LibraryEntry>,\n    rankedTaste: List<ListeningSignal>,\n    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,\n    onPlay: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,\n    onOpen: (ExtensionMediaSelection) -> Unit,\n) {\n    val queue = remember(rows) { rows.map { selection(it, ContentType.MUSIC) } }\n    val playFromQueue: (ExtensionMediaSelection) -> Unit = { track -> onPlay(track, queue) }\n    when (panel) {\n        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, playFromQueue, onOpen)\n        MusicLocal.DISCOVER -> MusicDiscover(rows, selection, playFromQueue)\n        MusicLocal.LIBRARY -> MusicLibrary(rows, libraryEntries, selection, playFromQueue)\n    }\n}'''
new_surface = '''@Composable\nprivate fun MusicSurface(\n    panel: MusicLocal,\n    rows: List<BrowseCard>,\n    libraryEntries: List<LibraryEntry>,\n    rankedTaste: List<ListeningSignal>,\n    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,\n    onPlay: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,\n    onOpen: (ExtensionMediaSelection) -> Unit,\n    onOpenExtensions: () -> Unit,\n    onSelectPanel: (MusicLocal) -> Unit,\n) {\n    val queue = remember(rows) { rows.map { selection(it, ContentType.MUSIC) } }\n    val playFromQueue: (ExtensionMediaSelection) -> Unit = { track -> onPlay(track, queue) }\n    when (panel) {\n        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, playFromQueue, onOpen, onOpenExtensions, onSelectPanel)\n        MusicLocal.DISCOVER -> MusicDiscover(rows, selection, playFromQueue)\n        MusicLocal.LIBRARY -> MusicLibrary(libraryEntries, playFromQueue)\n    }\n}'''
media = replace_once(media, old_surface, new_surface, 'MusicSurface declaration')

home_pattern = re.compile(r'@Composable\nprivate fun MusicHome\(.*?\n}\n\n@Composable\nprivate fun MusicDiscover', re.S)
new_home = '''@Composable\nprivate fun MusicHome(\n    rows: List<BrowseCard>, rankedTaste: List<ListeningSignal>,\n    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,\n    onPlay: (ExtensionMediaSelection) -> Unit, onOpen: (ExtensionMediaSelection) -> Unit,\n    onOpenExtensions: () -> Unit, onSelectPanel: (MusicLocal) -> Unit,\n) {\n    var optionsOpen by remember { mutableStateOf(false) }\n    val rankedRows = remember(rows, rankedTaste) {\n        val order = rankedTaste.mapIndexed { index, signal -> signal.artistName.trim().lowercase() to index }.toMap()\n        rows.sortedBy { card -> order[card.subtitle.substringBefore(" · ").trim().lowercase()] ?: Int.MAX_VALUE }\n    }\n    val recentRows = remember(rows, rankedTaste) {\n        rankedTaste\n            .sortedByDescending { it.lastPlayedEpochMs }\n            .flatMap { signal ->\n                rows.filter { card -> card.subtitle.substringBefore(" · ").trim().equals(signal.artistName.trim(), ignoreCase = true) }\n            }\n            .distinctBy { it.id }\n    }\n\n    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {\n        item {\n            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {\n                Column(Modifier.weight(1f)) {\n                    Text("Music", fontSize = 28.sp, fontWeight = FontWeight.Black)\n                    Text("Your listening space", color = SoraMuted, fontSize = 11.sp)\n                }\n                Box {\n                    IconButton(onClick = { optionsOpen = true }) { Icon(Icons.Rounded.MoreVert, "Music options") }\n                    DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {\n                        DropdownMenuItem(\n                            text = { Text("Discover") },\n                            leadingIcon = { Icon(Icons.Rounded.Explore, null) },\n                            onClick = { optionsOpen = false; onSelectPanel(MusicLocal.DISCOVER) },\n                        )\n                        DropdownMenuItem(\n                            text = { Text("Your Music") },\n                            leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },\n                            onClick = { optionsOpen = false; onSelectPanel(MusicLocal.LIBRARY) },\n                        )\n                        HorizontalDivider()\n                        DropdownMenuItem(\n                            text = { Text("Music sources") },\n                            leadingIcon = { Icon(Icons.Rounded.Extension, null) },\n                            onClick = { optionsOpen = false; onOpenExtensions() },\n                        )\n                    }\n                }\n            }\n        }\n        item { MusicQuickGrid(rows.take(6), selection, onPlay) }\n        if (rankedRows.isNotEmpty()) {\n            item { MusicSectionTitle("Made for you", if (rankedTaste.isEmpty()) "Fresh picks from your music source" else "Ordered from your listening history", null) }\n            item { MusicSquareRail(rankedRows.take(8), selection, onPlay) }\n        }\n        if (recentRows.isNotEmpty()) {\n            item { MusicSectionTitle("From artists you played recently", "Pulled from your actual listening history", null) }\n            item { MusicSquareRail(recentRows.take(8), selection, onPlay) }\n        }\n        if (rankedTaste.isNotEmpty()) {\n            item { MusicSectionTitle("Your top artists", "Based on your listening history", null); ArtistRail(rankedRows) }\n            item { MusicSectionTitle("Your rotation", "Artists and songs you return to", null); MusicTrackList(rankedRows.take(8), selection, onPlay) }\n        }\n    }\n}\n\n@Composable\nprivate fun MusicDiscover'''
media, count = home_pattern.subn(new_home, media, count=1)
if count != 1:
    raise SystemExit(f'MusicHome replacement: expected 1 match, found {count}')

discover_pattern = re.compile(r'@Composable\nprivate fun MusicDiscover\(.*?\n}\n\n@Composable\nprivate fun MusicLibrary', re.S)
new_discover = '''@Composable\nprivate fun MusicDiscover(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {\n    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {\n        item {\n            Surface(color = Color(0xFF242118), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(18.dp)) {\n                Column(Modifier.padding(20.dp)) {\n                    Text("DISCOVER", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)\n                    Text("Something new for tonight.", fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))\n                    Text("Fresh music from your installed source, ready to explore.", color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))\n                    Button(onClick = { rows.firstOrNull()?.let { onPlay(selection(it, ContentType.MUSIC)) } }, enabled = rows.isNotEmpty(), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 18.dp)) {\n                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play from Discover")\n                    }\n                }\n            }\n        }\n        if (rows.isEmpty()) {\n            item { HintLine("Install or refresh a Music source to fill Discover.") }\n        } else {\n            item { MusicSectionTitle("Fresh picks", "Music returned by your active source", null); MusicSquareRail(rows, selection, onPlay) }\n            item { MusicSectionTitle("Top songs", "From the current source feed", null); MusicTrackList(rows.take(10), selection, onPlay, numbered = true) }\n        }\n    }\n}\n\n@Composable\nprivate fun MusicLibrary'''
media, count = discover_pattern.subn(new_discover, media, count=1)
if count != 1:
    raise SystemExit(f'MusicDiscover replacement: expected 1 match, found {count}')

library_pattern = re.compile(r'@Composable\nprivate fun MusicLibrary\(.*?\n}\n\n@Composable\nprivate fun MemeSurface', re.S)
new_library = '''@Composable\nprivate fun MusicLibrary(\n    libraryEntries: List<LibraryEntry>,\n    onPlay: (ExtensionMediaSelection) -> Unit,\n) {\n    val savedMusic = libraryEntries.filter { it.contentType == ContentType.MUSIC }\n    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {\n        item {\n            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {\n                Text("YOUR MUSIC", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)\n                Text("Everything you kept.", fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))\n                Text(\n                    if (savedMusic.isEmpty()) "Saved songs will appear here." else "${savedMusic.size} saved ${if (savedMusic.size == 1) "song" else "songs"}",\n                    color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),\n                )\n            }\n        }\n        if (savedMusic.isEmpty()) {\n            item { HintLine("Save a song from its player or details screen to keep it in Your Music.") }\n        } else {\n            item { MusicSectionTitle("Saved songs", "Stored in your Sora library", null) }\n            items(savedMusic, key = { it.id }) { entry ->\n                val track = entry.toMediaSelection()\n                Row(\n                    Modifier.fillMaxWidth()\n                        .then(if (track != null) Modifier.clickable { onPlay(track) } else Modifier)\n                        .padding(horizontal = 18.dp, vertical = 7.dp),\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    Poster(entry.artworkUrl, entry.label, Modifier.size(48.dp), 5)\n                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {\n                        Text(entry.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                        Text(entry.mediaSubtitle.ifBlank { entry.detail }, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                    }\n                    if (track != null) Icon(Icons.Rounded.PlayArrow, "Play ${entry.label}", tint = SoraMuted, modifier = Modifier.size(18.dp))\n                }\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun MemeSurface'''
media, count = library_pattern.subn(new_library, media, count=1)
if count != 1:
    raise SystemExit(f'MusicLibrary replacement: expected 1 match, found {count}')

# A bare overflow glyph on every track implied an options menu that did not exist.
media = media.replace(
    '                    Icon(Icons.Rounded.MoreVert, null, tint = SoraMuted, modifier = Modifier.size(18.dp))\n',
    '',
)

media_path.write_text(media)
app_path.write_text(app)

print('Patched Music UI to use real saved/listening data and removed dead controls.')
