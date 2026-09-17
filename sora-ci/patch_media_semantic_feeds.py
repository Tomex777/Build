from pathlib import Path
import re

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')
client_path = ROOT / 'catalog/JikanCatalogClient.kt'
service_path = ROOT / 'catalog/JikanCatalogService.kt'
media_path = ROOT / 'ui/screens/MediaScreen.kt'

client = client_path.read_text()
service = service_path.read_text()
media = media_path.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return updated

# Built-in Jikan remains catalog-only, but its generic BROWSE payload can now ask
# for a truthful feed instead of forcing the UI to relabel one list repeatedly.
client = replace_once(
    client,
    '''    fun browse(type: ContentType, callback: (Result<List<CatalogItem>>) -> Unit) {\n        requireJikanType(type)\n        val endpoint = when (type) {\n            ContentType.ANIME -> "$BASE_URL/top/anime?filter=airing&limit=25&sfw=true"\n            ContentType.MANGA -> "$BASE_URL/top/manga?limit=25"\n            else -> error("Unsupported Jikan type: $type")\n        }\n        request(endpoint) { result ->\n            callback(result.mapCatching { root -> parseList(root, type) })\n        }\n    }''',
    '''    fun browse(type: ContentType, feed: String = "", callback: (Result<List<CatalogItem>>) -> Unit) {\n        requireJikanType(type)\n        val cleanFeed = feed.trim().lowercase()\n        val endpoint = when (type) {\n            ContentType.ANIME -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/anime?filter=bypopularity&limit=25&sfw=true"\n                "upcoming" -> "$BASE_URL/top/anime?filter=upcoming&limit=25&sfw=true"\n                "top" -> "$BASE_URL/top/anime?limit=25&sfw=true"\n                else -> "$BASE_URL/top/anime?filter=airing&limit=25&sfw=true"\n            }\n            ContentType.MANGA -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/manga?filter=bypopularity&limit=25"\n                "upcoming" -> "$BASE_URL/top/manga?filter=upcoming&limit=25"\n                "top" -> "$BASE_URL/top/manga?limit=25"\n                else -> "$BASE_URL/top/manga?filter=publishing&limit=25"\n            }\n            else -> error("Unsupported Jikan type: $type")\n        }\n        request(endpoint) { result ->\n            callback(result.mapCatching { root -> parseList(root, type) })\n        }\n    }''',
    'feed-aware Jikan browse',
)

service = replace_once(
    service,
    'else client.browse(type) { result -> sendCatalogResult(reply, requestId, result) }',
    'else client.browse(type, payload.optString("feed")) { result -> sendCatalogResult(reply, requestId, result) }',
    'Jikan service feed forwarding',
)

media = replace_once(
    media,
    '''    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }\n    var searchOpen by remember { mutableStateOf(false) }''',
    '''    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }\n    var popularRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var upcomingRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var topRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var searchOpen by remember { mutableStateOf(false) }''',
    'semantic feed state',
)

media = replace_once(
    media,
    '''    fun selection(card: BrowseCard, type: ContentType = selectedType) = ExtensionMediaSelection(\n        id = card.id,\n        sourceId = card.sourceId,\n        extensionPackage = card.extensionPackage,\n        type = type,\n        title = card.title,\n        subtitle = card.subtitle,\n        artworkUrl = card.artworkUrl,\n    )\n\n    fun load(search: String) {''',
    '''    fun selection(card: BrowseCard, type: ContentType = selectedType) = ExtensionMediaSelection(\n        id = card.id,\n        sourceId = card.sourceId,\n        extensionPackage = card.extensionPackage,\n        type = type,\n        title = card.title,\n        subtitle = card.subtitle,\n        artworkUrl = card.artworkUrl,\n    )\n\n    fun loadJikanFeed(feed: String, requestType: ContentType, onResult: (List<BrowseCard>) -> Unit) {\n        if (requestType != ContentType.ANIME && requestType != ContentType.MANGA) {\n            onResult(emptyList())\n            return\n        }\n        val key = typeKey(requestType)\n        val ext = extensions.firstOrNull { it.error == null && it.declaredId == "sora.core.jikan" }\n        val source = ext?.descriptor?.sources?.firstOrNull { key in it.contentTypes }\n        if (ext == null || source == null) {\n            onResult(emptyList())\n            return\n        }\n        val payload = JSONObject()\n            .put("sourceId", source.id)\n            .put("type", key)\n            .put("feed", feed)\n            .toString()\n        manager.call(ext, ExtensionContract.Method.BROWSE, payload) { result ->\n            onResult(result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty())\n        }\n    }\n\n    fun load(search: String) {''',
    'Jikan feed loader',
)

media = replace_once(
    media,
    '''    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch) {\n        if (destination == MediaDestination.BIBLE) return@LaunchedEffect\n        if (query.isNotBlank()) delay(250)\n        load(query)\n    }\n\n    Column(modifier.fillMaxSize()) {''',
    '''    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch) {\n        if (destination == MediaDestination.BIBLE) return@LaunchedEffect\n        if (query.isNotBlank()) delay(250)\n        load(query)\n    }\n\n    LaunchedEffect(selectedType, extensions, destination, networkEpoch) {\n        popularRows = emptyList()\n        upcomingRows = emptyList()\n        topRows = emptyList()\n        if (destination != MediaDestination.ANIME_MANGA) return@LaunchedEffect\n        if (selectedType != ContentType.ANIME && selectedType != ContentType.MANGA) return@LaunchedEffect\n        val requestType = selectedType\n        loadJikanFeed("popular", requestType) { result ->\n            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) popularRows = result\n        }\n        loadJikanFeed("upcoming", requestType) { result ->\n            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) upcomingRows = result\n        }\n        loadJikanFeed("top", requestType) { result ->\n            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) topRows = result\n        }\n    }\n\n    Column(modifier.fillMaxSize()) {''',
    'semantic feed effect',
)

media = replace_once(
    media,
    '''            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(\n                type = selectedType, rows = rows, libraryEntries = libraryEntries,\n                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,\n            )''',
    '''            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(\n                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,\n                libraryEntries = libraryEntries, selection = ::selection, isSaved = isSaved,\n                onToggleSaved = onToggleSaved, onOpen = onOpenDetails,\n            )''',
    'Anime/Manga semantic feed arguments',
)

anime_surface = '''@Composable
private fun AnimeMangaSurface(
    type: ContentType,
    rows: List<BrowseCard>,
    popularRows: List<BrowseCard>,
    upcomingRows: List<BrowseCard>,
    topRows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    val currentLabel = if (type == ContentType.ANIME) "Airing now" else "Publishing now"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (selected == null) item { EmptyFeatureShell(type) }
        if (selected != null) item {
            val selectedMedia = selection(selected, type)
            StreamFeature(
                card = selected,
                kicker = currentLabel,
                body = selected.subtitle.ifBlank {
                    if (type == ContentType.ANIME) "Currently airing in the Sora catalog." else "Currently publishing in the Sora catalog."
                },
                primaryLabel = "Open",
                selection = selectedMedia,
                isSaved = isSaved(selectedMedia),
                onToggleSaved = onToggleSaved,
                onOpen = onOpen,
            )
        }
        item {
            MediaSectionTitle("In your library", if (type == ContentType.ANIME) "Anime you saved in Sora" else "Manga you saved in Sora")
            if (saved.isNotEmpty()) ContinueLandscapeRail(saved, onOpen)
            else HintLine(if (type == ContentType.ANIME) "Saved anime will appear here." else "Saved manga will appear here.")
        }
        if (popularRows.isNotEmpty()) item {
            MediaSectionTitle("Popular now", if (type == ContentType.ANIME) "Popular anime from the catalog" else "Popular manga from the catalog")
            PortraitRail(popularRows, type, selection, onOpen)
        }
        if (upcomingRows.isNotEmpty()) item {
            MediaSectionTitle(if (type == ContentType.ANIME) "Upcoming anime" else "Upcoming manga", "Titles coming next")
            NewHotStack(upcomingRows.take(4), type, selection, onOpen)
        }
        if (topRows.isNotEmpty()) item {
            MediaSectionTitle("Top 10 ${type.label.lowercase()}", "Highest-ranked titles from the catalog")
            TopTenRail(topRows.take(10), type, selection, onOpen)
        }
        if (rows.isNotEmpty()) item {
            MediaSectionTitle(currentLabel, if (type == ContentType.ANIME) "Anime currently airing" else "Manga currently publishing")
            PortraitRail(rows, type, selection, onOpen)
        }
        item {
            MediaSectionTitle("Browse by mood", "Explore a genre from Search")
            GenreRail(if (type == ContentType.ANIME) listOf("Dark", "Funny", "Psychological", "Adventure", "Romance", "Slice of life") else listOf("Drama", "Psychological", "Action", "Romance", "Mystery", "Slice of life"))
        }
    }
}
'''

media = regex_once(
    media,
    r'@Composable\nprivate fun AnimeMangaSurface\(.*?\n}\n\n(?=@Composable\nprivate fun MovieTvSurface)',
    anime_surface + '\n',
    'Anime/Manga surface replacement',
)

movie_surface = '''@Composable
private fun MovieTvSurface(
    type: ContentType,
    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (selected == null) item { EmptyFeatureShell(type) }
        if (selected != null) item {
            StreamFeature(
                card = selected,
                kicker = if (type == ContentType.MOVIE) "Featured movie" else "Featured series",
                body = selected.subtitle.ifBlank { "From your active catalog source" },
                primaryLabel = "Open",
                selection = selection(selected, type), isSaved = isSaved(selection(selected, type)), onToggleSaved = onToggleSaved, onOpen = onOpen,
            )
        }
        item {
            MediaSectionTitle("In your library", "${if (type == ContentType.MOVIE) "Movies" else "Series"} you saved in Sora")
            if (saved.isNotEmpty()) ContinueLandscapeRail(saved, onOpen) else HintLine("Saved titles will appear here.")
        }
        if (rows.isNotEmpty()) {
            item { MediaSectionTitle("Featured picks", "From your active catalog source"); PortraitRail(rows, type, selection, onOpen) }
            item { MediaSectionTitle("More to watch", "More titles from the same source"); PortraitRail(rows.drop(6).ifEmpty { rows }, type, selection, onOpen) }
            item { MediaSectionTitle("10 picks", "A quick shortlist from your source"); TopTenRail(rows.take(10), type, selection, onOpen) }
        }
        item { MediaSectionTitle("Browse by mood", "Explore a genre from Search"); GenreRail(listOf("Thriller", "Drama", "Comedy", "Sci-fi", "Crime", "Documentary")) }
    }
}
'''

media = regex_once(
    media,
    r'@Composable\nprivate fun MovieTvSurface\(.*?\n}\n\n(?=@Composable\nprivate fun MusicSurface)',
    movie_surface + '\n',
    'Movies/TV surface replacement',
)

client_path.write_text(client)
service_path.write_text(service)
media_path.write_text(media)
print('Patched truthful Jikan feed variants and removed fake media ranking/progress labels.')
