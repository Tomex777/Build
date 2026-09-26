from pathlib import Path

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')


def replace(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:100]!r}')
    path.write_text(text.replace(old, new, 1))

# MediaScreen: Jikan owns Anime/Manga catalog browsing and search.
media = ROOT / 'ui/screens/MediaScreen.kt'
replace(
    media,
    'import coil3.compose.AsyncImage\n',
    'import coil3.compose.AsyncImage\nimport com.night.sora.catalog.JikanCatalogClient\n',
)
replace(
    media,
    '    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }\n',
    '    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }\n    val jikan = remember { JikanCatalogClient() }\n',
)
old = '''        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
'''
new = '''        if (requestType == ContentType.ANIME || requestType == ContentType.MANGA) {
            val receiveJikan: (Result<List<JikanCatalogClient.CatalogItem>>) -> Unit = { result ->
                val fresh = result.getOrNull().orEmpty().map { item ->
                    BrowseCard(
                        id = item.id,
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.artworkUrl,
                        sourceId = JikanCatalogClient.sourceFor(requestType),
                        extensionPackage = JikanCatalogClient.ORIGIN_PACKAGE,
                    )
                }
                if (requestQuery.isBlank() && fresh.isNotEmpty()) {
                    mediaCache.write(requestType, fresh.map { it.toCachedRecord() })
                }
                if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery && fresh.isNotEmpty()) {
                    rows = fresh
                }
            }
            if (requestQuery.isBlank()) jikan.browse(requestType, receiveJikan)
            else jikan.search(requestType, requestQuery, receiveJikan)
            return
        }

        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
'''
replace(media, old, new)

# HomeScreen: the mixed recommendation shelf gets Anime/Manga from Core/Jikan.
home = ROOT / 'ui/screens/HomeScreen.kt'
replace(
    home,
    'import coil3.compose.AsyncImage\n',
    'import coil3.compose.AsyncImage\nimport com.night.sora.catalog.JikanCatalogClient\n',
)
replace(
    home,
    '    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }\n',
    '    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }\n    val jikan = remember { JikanCatalogClient() }\n',
)
text = home.read_text()
text = text.replace('loadHomeType(extensions, manager, mediaCache, ContentType.ANIME)', 'loadHomeType(extensions, manager, mediaCache, jikan, ContentType.ANIME)')
text = text.replace('loadHomeType(extensions, manager, mediaCache, ContentType.MANGA)', 'loadHomeType(extensions, manager, mediaCache, jikan, ContentType.MANGA)')
text = text.replace('loadHomeType(extensions, manager, mediaCache, ContentType.MOVIE)', 'loadHomeType(extensions, manager, mediaCache, jikan, ContentType.MOVIE)')
text = text.replace('loadHomeType(extensions, manager, mediaCache, ContentType.MUSIC)', 'loadHomeType(extensions, manager, mediaCache, jikan, ContentType.MUSIC)')
text = text.replace('loadHomeType(extensions, manager, mediaCache, ContentType.MEME)', 'loadHomeType(extensions, manager, mediaCache, jikan, ContentType.MEME)')
home.write_text(text)
replace(
    home,
    '''    cache: MediaCatalogCache,
    type: ContentType,
''',
    '''    cache: MediaCatalogCache,
    jikan: JikanCatalogClient,
    type: ContentType,
''',
)
old = '''    val key = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
    val cached = cachedHomeType(cache, type)
    val providers = extensions.flatMap { ext ->
'''
new = '''    val key = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
    val cached = cachedHomeType(cache, type)

    if (type == ContentType.ANIME || type == ContentType.MANGA) {
        callback(cached)
        jikan.browse(type) { result ->
            val fresh = result.getOrNull().orEmpty().map { item ->
                HomeBrowseCard(
                    id = item.id,
                    sourceId = JikanCatalogClient.sourceFor(type),
                    extensionPackage = JikanCatalogClient.ORIGIN_PACKAGE,
                    type = type,
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.artworkUrl,
                )
            }
            if (fresh.isNotEmpty()) {
                cache.write(type, fresh.map { CachedMediaRecord(it.id, it.title, it.subtitle, it.artworkUrl, it.sourceId, it.extensionPackage) })
                callback(fresh)
            }
        }
        return
    }

    val providers = extensions.flatMap { ext ->
'''
replace(home, old, new)

# MediaDetailScreen: Jikan supplies metadata and adaptation relationships even
# when no extension APK is installed.
detail = ROOT / 'ui/screens/MediaDetailScreen.kt'
replace(
    detail,
    'import coil3.compose.AsyncImage\n',
    'import coil3.compose.AsyncImage\nimport com.night.sora.catalog.JikanCatalogClient\n',
)
replace(
    detail,
    '''    var mangaLatestFirst by remember { mutableStateOf(true) }

    val extension = extensions.firstOrNull { it.packageName == active.extensionPackage }
''',
    '''    var mangaLatestFirst by remember { mutableStateOf(true) }
    val jikan = remember { JikanCatalogClient() }

    val extension = extensions.firstOrNull { it.packageName == active.extensionPackage }
''',
)
old = '''        val ext = extensions.firstOrNull { it.packageName == active.extensionPackage }
        if (ext == null) { loading = false; return@LaunchedEffect }

        manager.call(ext, ExtensionContract.Method.DETAILS, JSONObject().put("sourceId", active.sourceId).put("id", active.id).toString()) { result ->
'''
new = '''        if (JikanCatalogClient.isJikan(active.extensionPackage)) {
            jikan.details(active.type, active.id) { result ->
                result.onSuccess { details ->
                    description = details.synopsis.ifBlank { active.subtitle }
                    active = active.copy(
                        title = details.title.ifBlank { active.title },
                        subtitle = details.subtitle.ifBlank { active.subtitle },
                        artworkUrl = details.artworkUrl ?: active.artworkUrl,
                    )
                }
                loading = false
            }
            if (active.type == ContentType.ANIME || active.type == ContentType.MANGA) {
                jikan.counterpart(active.type, active.id) { result ->
                    counterpart = result.getOrNull()?.let { related ->
                        ExtensionMediaSelection(
                            id = related.id,
                            sourceId = JikanCatalogClient.sourceFor(related.type),
                            extensionPackage = JikanCatalogClient.ORIGIN_PACKAGE,
                            type = related.type,
                            title = related.title,
                            subtitle = related.subtitle,
                            artworkUrl = related.artworkUrl,
                        )
                    }
                }
            }
            return@LaunchedEffect
        }

        val ext = extensions.firstOrNull { it.packageName == active.extensionPackage }
        if (ext == null) { loading = false; return@LaunchedEffect }

        manager.call(ext, ExtensionContract.Method.DETAILS, JSONObject().put("sourceId", active.sourceId).put("id", active.id).toString()) { result ->
'''
replace(detail, old, new)

print('Built-in Jikan integration applied.')
