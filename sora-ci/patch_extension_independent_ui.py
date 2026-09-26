from pathlib import Path
import re

ROOT = Path('sora-overlay/app/src/main')


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing patch anchor: {label}')
    return text.replace(old, new, 1)


def patch_media():
    path = ROOT / 'java/com/night/sora/ui/screens/MediaScreen.kt'
    text = path.read_text()

    text = must_replace(
        text,
        'package com.night.sora.ui.screens\n\n',
        'package com.night.sora.ui.screens\n\nimport android.net.ConnectivityManager\nimport android.net.Network\nimport android.os.Handler\nimport android.os.Looper\n',
        'media android imports',
    )
    text = must_replace(
        text,
        'import androidx.compose.ui.layout.ContentScale\n',
        'import androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.platform.LocalContext\n',
        'media LocalContext import',
    )
    text = must_replace(
        text,
        'import com.night.sora.extension.api.ExtensionContract\n',
        'import com.night.sora.extension.api.ExtensionContract\nimport com.night.sora.data.CachedMediaRecord\nimport com.night.sora.data.MediaCatalogCache\n',
        'media cache imports',
    )

    card_anchor = '''private data class BrowseCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val sourceId: String,
    val extensionPackage: String,
)
'''
    card_new = card_anchor + '''
private fun CachedMediaRecord.toBrowseCard() = BrowseCard(id, title, subtitle, artworkUrl, sourceId, extensionPackage)
private fun BrowseCard.toCachedRecord() = CachedMediaRecord(id, title, subtitle, artworkUrl, sourceId, extensionPackage)
'''
    text = must_replace(text, card_anchor, card_new, 'media cache adapters')

    old_vars = '''    var destination by remember { mutableStateOf(MediaDestination.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(ContentType.ANIME) }
    var musicLocal by remember { mutableStateOf(MusicLocal.HOME) }
    var rows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var switchOpen by remember { mutableStateOf(false) }
    var sourceMissing by remember { mutableStateOf(false) }
'''
    new_vars = '''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    var destination by remember { mutableStateOf(MediaDestination.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(ContentType.ANIME) }
    var musicLocal by remember { mutableStateOf(MusicLocal.HOME) }
    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var switchOpen by remember { mutableStateOf(false) }
    var networkEpoch by remember { mutableIntStateOf(0) }
'''
    text = must_replace(text, old_vars, new_vars, 'media state block')

    taste_anchor = '''    val engine = remember { MusicTasteEngine() }
    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }
'''
    taste_new = taste_anchor + '''
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { networkEpoch++ }
            }
        }
        val registered = runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)
        onDispose {
            if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }
'''
    text = must_replace(text, taste_anchor, taste_new, 'media network refresh')

    pattern = re.compile(r'''    fun load\(search: String\) \{.*?\n    \}\n\n    LaunchedEffect\(selectedType, extensions, query, destination\) \{''', re.S)
    replacement = '''    fun load(search: String) {
        if (destination == MediaDestination.BIBLE) {
            rows = emptyList()
            return
        }

        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val cached = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)
        rows = cached.map { it.toBrowseCard() }

        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.error == null && key in source.contentTypes }
                .map { source -> ext to source }
        }
        if (providers.isEmpty()) return

        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }
        var completed = 0

        providers.forEachIndexed { index, (ext, source) ->
            val payload = JSONObject()
                .put("sourceId", source.id)
                .put("type", key)
                .put("query", requestQuery)
                .toString()
            manager.call(ext, method, payload) { result ->
                collected[index] = result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty()
                completed++
                if (completed == providers.size) {
                    val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                    if (requestQuery.isBlank() && fresh.isNotEmpty()) {
                        mediaCache.write(requestType, fresh.map { it.toCachedRecord() })
                    }
                    if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery) {
                        if (fresh.isNotEmpty()) rows = fresh
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch) {'''
    text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit('missing patch anchor: media load function')

    text = must_replace(
        text,
        '''        when {
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize())
            !extensionScanDone || loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sourceMissing -> MissingMediaSource(selectedType.label, onOpenExtensions)
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
''',
        '''        when {
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize())
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
''',
        'media nonblocking rendering',
    )

    replacements = {
        '"More from your enabled anime sources"': '"More you might like"',
        '"More from your enabled manga sources"': '"More you might like"',
        '"Trending across your enabled sources"': '"Trending now"',
        '"Popular across your manga sources"': '"Popular today"',
        '"Fresh from your sources"': '"Fresh episodes"',
        '"Popular across your enabled sources"': '"Popular today"',
        '"From your enabled sources"': '"Ready when you are"',
        'HintLine("Install a meme source to build this feed.")': 'HintLine("Your feed will appear here as soon as new posts are available.")',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)

    path.write_text(text)


def patch_home():
    path = ROOT / 'java/com/night/sora/ui/screens/HomeScreen.kt'
    text = path.read_text()

    text = must_replace(
        text,
        'package com.night.sora.ui.screens\n\n',
        'package com.night.sora.ui.screens\n\nimport android.net.ConnectivityManager\nimport android.net.Network\nimport android.os.Handler\nimport android.os.Looper\n',
        'home android imports',
    )
    text = must_replace(
        text,
        'import androidx.compose.ui.layout.ContentScale\n',
        'import androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.platform.LocalContext\n',
        'home LocalContext import',
    )
    text = must_replace(
        text,
        'import com.night.sora.extension.api.ExtensionContract\n',
        'import com.night.sora.extension.api.ExtensionContract\nimport com.night.sora.data.CachedMediaRecord\nimport com.night.sora.data.MediaCatalogCache\n',
        'home cache imports',
    )

    old_state = '''    var recommendations by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }
    var music by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }
    var memes by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }

    LaunchedEffect(extensions) {
        if (extensions.isEmpty()) return@LaunchedEffect
        val result = mutableStateListOf<HomeBrowseCard>()
'''
    new_state = '''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    var networkEpoch by remember { mutableIntStateOf(0) }
    var recommendations by remember {
        mutableStateOf(
            listOf(ContentType.ANIME, ContentType.MANGA, ContentType.MOVIE)
                .flatMap { cachedHomeType(mediaCache, it).take(4) }
        )
    }
    var music by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MUSIC).take(8)) }
    var memes by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MEME).take(4)) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { mainHandler.post { networkEpoch++ } }
        }
        val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback); true }.getOrDefault(false)
        onDispose { if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    LaunchedEffect(extensions, networkEpoch) {
        val result = mutableStateListOf<HomeBrowseCard>().apply { addAll(recommendations) }
'''
    text = must_replace(text, old_state, new_state, 'home cached state')

    for type_name in ['ANIME', 'MANGA', 'MOVIE', 'MUSIC', 'MEME']:
        text = text.replace(
            f'loadHomeType(extensions, manager, ContentType.{type_name})',
            f'loadHomeType(extensions, manager, mediaCache, ContentType.{type_name})',
        )

    text = text.replace('"Mixed from your library and sources"', '"Picked from across Sora"')
    text = text.replace('"From your meme source"', '"Something from your feed"')
    text = text.replace('"Recommendations appear when sources are ready."', '"Your recommendations will fill in as Sora learns what you like."')
    text = text.replace('if (meme != null) "source · now" else "meme source · waiting"', 'if (meme != null) "Sora · now" else "Sora · ready offline"')

    pattern = re.compile(r'''private fun loadHomeType\(extensions: List<InstalledExtension>, manager: ExtensionManager, type: ContentType, callback: \(List<HomeBrowseCard>\) -> Unit\) \{.*?\n\}\n\nprivate fun homeArtwork''', re.S)
    replacement = '''private fun cachedHomeType(cache: MediaCatalogCache, type: ContentType): List<HomeBrowseCard> =
    cache.read(type).map { row -> HomeBrowseCard(row.id, row.sourceId, row.extensionPackage, type, row.title, row.subtitle, row.artworkUrl) }

private fun loadHomeType(
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    cache: MediaCatalogCache,
    type: ContentType,
    callback: (List<HomeBrowseCard>) -> Unit,
) {
    val key = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
    val cached = cachedHomeType(cache, type)
    val providers = extensions.flatMap { ext ->
        ext.descriptor?.sources.orEmpty()
            .filter { source -> ext.error == null && key in source.contentTypes }
            .map { source -> ext to source }
    }
    if (providers.isEmpty()) return callback(cached)

    val collected = MutableList(providers.size) { emptyList<HomeBrowseCard>() }
    var completed = 0
    providers.forEachIndexed { index, (ext, source) ->
        manager.call(ext, ExtensionContract.Method.BROWSE, JSONObject().put("sourceId", source.id).put("type", key).toString()) { result ->
            collected[index] = result.getOrNull()?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    buildList {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            add(HomeBrowseCard(item.optString("id"), source.id, ext.packageName, type, item.optString("title", "Untitled"), item.optString("subtitle"), homeArtwork(item)))
                        }
                    }
                }.getOrDefault(emptyList())
            }.orEmpty()
            completed++
            if (completed == providers.size) {
                val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                if (fresh.isNotEmpty()) {
                    cache.write(type, fresh.map { CachedMediaRecord(it.id, it.title, it.subtitle, it.artworkUrl, it.sourceId, it.extensionPackage) })
                    callback(fresh)
                } else callback(cached)
            }
        }
    }
}

private fun homeArtwork'''
    text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit('missing patch anchor: home provider loader')

    path.write_text(text)


def patch_app():
    path = ROOT / 'java/com/night/sora/ui/SoraApp.kt'
    text = path.read_text()
    text = must_replace(
        text,
        'import coil3.compose.AsyncImage\n',
        'import coil3.compose.AsyncImage\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.compose.LifecycleEventEffect\n',
        'app lifecycle imports',
    )
    text = must_replace(
        text,
        '    LaunchedEffect(Unit) { refreshExtensions() }\n',
        '    LaunchedEffect(Unit) { refreshExtensions() }\n    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshExtensions() }\n',
        'app extension rediscovery',
    )
    path.write_text(text)


def patch_detail():
    path = ROOT / 'java/com/night/sora/ui/screens/MediaDetailScreen.kt'
    text = path.read_text()

    text = must_replace(
        text,
        '    var menuOpen by remember { mutableStateOf(false) }\n',
        '    var menuOpen by remember { mutableStateOf(false) }\n    var sourcePickerOpen by remember { mutableStateOf(false) }\n',
        'detail picker state',
    )
    text = text.replace('    if (extension == null) { MissingExtensionScreen(onBack); return }\n\n', '')

    old_menu = '''                        DropdownMenuItem(text = { Text("Change source") }, leadingIcon = { Icon(Icons.Rounded.Source, null) }, onClick = { menuOpen = false })
                        DropdownMenuItem(text = { Text("Use ${extension.declaredName} by default") }, leadingIcon = { Icon(Icons.Rounded.CheckCircleOutline, null) }, onClick = { menuOpen = false })
'''
    new_menu = '''                        DropdownMenuItem(
                            text = { Text("Change source") },
                            leadingIcon = { Icon(Icons.Rounded.Source, null) },
                            onClick = { menuOpen = false; sourcePickerOpen = true },
                        )
                        if (extension != null) {
                            DropdownMenuItem(
                                text = { Text("Using ${extension.declaredName}") },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircleOutline, null) },
                                enabled = false,
                                onClick = { menuOpen = false },
                            )
                        }
'''
    text = must_replace(text, old_menu, new_menu, 'detail source menu')

    insertion_anchor = '''    }
}

@Composable
private fun AdaptationButton'''
    insertion = '''    }

    if (sourcePickerOpen) {
        ModalBottomSheet(onDismissRequest = { sourcePickerOpen = false }, containerColor = SoraSurface) {
            Text("Choose source", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            val key = detailTypeKey(active.type)
            val options = extensions.flatMap { ext ->
                ext.descriptor?.sources.orEmpty()
                    .filter { source -> ext.error == null && key in source.contentTypes }
                    .map { source -> ext to source }
            }
            if (options.isEmpty()) {
                Text("No alternative provider is installed yet.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            } else {
                options.forEach { (ext, source) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            sourcePickerOpen = false
                            val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", active.title).toString()
                            manager.call(ext, ExtensionContract.Method.SEARCH, payload) { result ->
                                result.getOrNull()?.let { raw ->
                                    parseSourceSelection(raw, source.id, ext.packageName, active.type)?.let { active = it }
                                }
                            }
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Source, null, tint = if (ext.packageName == active.extensionPackage && source.id == active.sourceId) SoraAccent else SoraMuted)
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Text(source.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(ext.declaredName, color = SoraMuted, fontSize = 10.sp)
                        }
                        if (ext.packageName == active.extensionPackage && source.id == active.sourceId) Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun AdaptationButton'''
    text = must_replace(text, insertion_anchor, insertion, 'detail picker sheet')

    pattern = re.compile(r'''private fun findCounterpart\(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: \(ExtensionMediaSelection\?\) -> Unit\) \{.*?\n\}\n\nprivate fun parseRows''', re.S)
    replacement = '''private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {
    val opposite = if (active.type == ContentType.ANIME) ContentType.MANGA else if (active.type == ContentType.MANGA) ContentType.ANIME else return callback(null)
    val key = opposite.name.lowercase()
    val providers = extensions.flatMap { ext ->
        ext.descriptor?.sources.orEmpty()
            .filter { source -> ext.error == null && key in source.contentTypes }
            .map { source -> ext to source }
    }
    if (providers.isEmpty()) return callback(null)

    val normalized = active.title.lowercase().replace(Regex("[^a-z0-9]"), "")
    fun tryProvider(index: Int) {
        if (index >= providers.size) return callback(null)
        val (ext, source) = providers[index]
        val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", active.title).toString()
        manager.call(ext, ExtensionContract.Method.SEARCH, payload) { result ->
            val found = result.getOrNull()?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    var best: JSONObject? = null
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val name = item.optString("title").lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (name == normalized || name.contains(normalized) || normalized.contains(name)) { best = item; break }
                    }
                    (best ?: arr.optJSONObject(0))?.let {
                        ExtensionMediaSelection(it.optString("id"), source.id, ext.packageName, opposite, it.optString("title"), it.optString("subtitle"), detailArtwork(it))
                    }
                }.getOrNull()
            }
            if (found != null) callback(found) else tryProvider(index + 1)
        }
    }
    tryProvider(0)
}

private fun parseRows'''
    text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit('missing patch anchor: counterpart aggregator')

    text += '''

private fun detailTypeKey(type: ContentType): String = when (type) {
    ContentType.MOVIE -> "movie"
    ContentType.MEME -> "memes"
    else -> type.name.lowercase()
}

private fun parseSourceSelection(raw: String, sourceId: String, packageName: String, type: ContentType): ExtensionMediaSelection? = runCatching {
    val array = JSONArray(raw)
    val item = array.optJSONObject(0) ?: return@runCatching null
    ExtensionMediaSelection(
        id = item.optString("id"),
        sourceId = sourceId,
        extensionPackage = packageName,
        type = type,
        title = item.optString("title", "Untitled"),
        subtitle = item.optString("subtitle"),
        artworkUrl = detailArtwork(item),
    )
}.getOrNull()
'''

    path.write_text(text)


patch_media()
patch_home()
patch_app()
patch_detail()
print('patched extension-independent Sora UI')
