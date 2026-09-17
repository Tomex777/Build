from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label} marker mismatch: {count}")
    return text.replace(old, new, 1)

media = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt")
text = media.read_text()
text = replace_once(
    text,
    '''    var switchOpen by remember { mutableStateOf(false) }
    var networkEpoch by remember { mutableIntStateOf(0) }
''',
    '''    var switchOpen by remember { mutableStateOf(false) }
    var networkEpoch by remember { mutableIntStateOf(0) }
    var memeLoading by remember { mutableStateOf(false) }
    var memeError by remember { mutableStateOf<String?>(null) }
''',
    "meme state vars",
)
text = replace_once(
    text,
    '''        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val cached = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)
        rows = cached.map { it.toBrowseCard() }
''',
    '''        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val cached = if (requestType == ContentType.MEME) {
            emptyList()
        } else if (requestQuery.isBlank()) {
            mediaCache.read(requestType)
        } else {
            mediaCache.search(requestType, requestQuery)
        }
        rows = cached.map { it.toBrowseCard() }
        if (requestType == ContentType.MEME) {
            memeLoading = true
            memeError = null
        }
''',
    "meme no persistent cache",
)
text = replace_once(
    text,
    '''        if (providers.isEmpty()) return

        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }
        var completed = 0
''',
    '''        if (providers.isEmpty()) {
            if (requestType == ContentType.MEME) {
                memeLoading = false
                memeError = if (extensionScanDone) "No meme source is installed." else null
            }
            return
        }

        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }
        val failures = MutableList<String?>(providers.size) { null }
        var completed = 0
''',
    "meme no-provider state",
)
text = replace_once(
    text,
    '''            manager.call(ext, method, payload) { result ->
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
''',
    '''            manager.call(ext, method, payload) { result ->
                collected[index] = result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty()
                failures[index] = result.exceptionOrNull()?.message
                completed++
                if (completed == providers.size) {
                    val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                    if (requestType != ContentType.MEME && requestQuery.isBlank() && fresh.isNotEmpty()) {
                        mediaCache.write(requestType, fresh.map { it.toCachedRecord() })
                    }
                    if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery) {
                        if (requestType == ContentType.MEME) {
                            memeLoading = false
                            memeError = if (fresh.isEmpty()) {
                                failures.firstOrNull { !it.isNullOrBlank() }
                                    ?: if (requestQuery.isBlank()) "No image posts are available from this source right now." else "No meme results matched your search."
                            } else null
                            rows = fresh
                        } else if (fresh.isNotEmpty()) {
                            rows = fresh
                        }
                    }
                }
            }
''',
    "meme provider completion state",
)
text = replace_once(
    text,
    '''            destination == MediaDestination.MEMES -> MemeSurface(rows, ::selection, onOpenDetails)
''',
    '''            destination == MediaDestination.MEMES -> MemeSurface(
                rows = rows,
                selection = ::selection,
                onOpen = onOpenDetails,
                loading = memeLoading,
                error = memeError,
                onOpenExtensions = onOpenExtensions,
            )
''',
    "MemeSurface call",
)
old_surface = '''@Composable
private fun MemeSurface(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        if (rows.isEmpty()) item { HintLine("Your feed will appear here as soon as new posts are available.") }
        items(rows, key = { it.id }) { card ->
            Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onOpen(selection(card, ContentType.MEME)) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(card.subtitle.ifBlank { "meme source" }, fontSize = 11.sp, color = Color(0xFF656158)); Icon(Icons.Rounded.MoreHoriz, null) }
                    Text(card.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFC5C0B3))) { if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Open post", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
'''
new_surface = '''@Composable
private fun MemeSurface(
    rows: List<BrowseCard>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onOpen: (ExtensionMediaSelection) -> Unit,
    loading: Boolean,
    error: String?,
    onOpenExtensions: () -> Unit,
) {
    if (rows.isEmpty() && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SoraAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Text("Loading meme feed…", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        return
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                Icon(
                    if (error?.contains("installed", ignoreCase = true) == true) Icons.Rounded.ExtensionOff else Icons.Rounded.CloudOff,
                    null,
                    tint = SoraMuted,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    if (error?.contains("installed", ignoreCase = true) == true) "No meme source installed" else "Meme feed unavailable",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    error ?: "No posts are available right now.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                if (error?.contains("installed", ignoreCase = true) == true) {
                    TextButton(onClick = onOpenExtensions, modifier = Modifier.padding(top = 8.dp)) { Text("Manage extensions") }
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        items(rows, key = { it.id }) { card ->
            Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onOpen(selection(card, ContentType.MEME)) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(card.subtitle.ifBlank { "meme source" }, fontSize = 11.sp, color = Color(0xFF656158)); Icon(Icons.Rounded.MoreHoriz, null) }
                    Text(card.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFC5C0B3))) { if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Open post", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
'''
text = replace_once(text, old_surface, new_surface, "MemeSurface truthful state")
media.write_text(text)

home = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/HomeScreen.kt")
text = home.read_text()
text = replace_once(
    text,
    '''    var music by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MUSIC).take(8)) }
    var memes by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MEME).take(4)) }
''',
    '''    var music by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MUSIC).take(8)) }
    var memes by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }
''',
    "Home meme initial cache",
)
text = replace_once(
    text,
    '''                if (fresh.isNotEmpty()) {
                    cache.write(type, fresh.map { CachedMediaRecord(it.id, it.title, it.subtitle, it.artworkUrl, it.sourceId, it.extensionPackage) })
                    callback(fresh)
                } else callback(cached)
''',
    '''                if (fresh.isNotEmpty()) {
                    if (type != ContentType.MEME) {
                        cache.write(type, fresh.map { CachedMediaRecord(it.id, it.title, it.subtitle, it.artworkUrl, it.sourceId, it.extensionPackage) })
                    }
                    callback(fresh)
                } else callback(if (type == ContentType.MEME) emptyList() else cached)
''',
    "Home meme cache write",
)
home.write_text(text)
