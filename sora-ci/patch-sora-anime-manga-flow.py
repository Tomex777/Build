from pathlib import Path

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    (ROOT / rel).write_text(text)


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{rel}: expected one match, found {count}: {old[:120]!r}')
    write(rel, text.replace(old, new, 1))


def replace_span(rel, start, end, new):
    text = read(rel)
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'{rel}: start marker not found: {start[:120]!r}')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'{rel}: end marker not found: {end[:120]!r}')
    write(rel, text[:a] + new + text[b:])


# ---------------------------------------------------------------------------
# Catalog cache: retain real live Jikan rows, track freshness, and support
# feed-specific last-good snapshots without breaking the old v1 default key.
# ---------------------------------------------------------------------------
cache = '''package com.night.sora.data

import android.content.Context
import com.night.sora.model.ContentType
import org.json.JSONArray
import org.json.JSONObject

data class CachedMediaRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val sourceId: String,
    val extensionPackage: String,
)

data class CachedMediaSnapshot(
    val rows: List<CachedMediaRecord>,
    val fetchedAt: Long = 0L,
) {
    fun isStale(now: Long = System.currentTimeMillis(), maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        fetchedAt <= 0L || now - fetchedAt > maxAgeMs

    companion object {
        const val DEFAULT_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}

/**
 * Sora-owned last-good catalog cache.
 *
 * Only structured catalog metadata already returned by a real provider is
 * stored here. Feed timestamps let the UI say when it is rendering saved data
 * instead of implying a stale snapshot was freshly fetched.
 */
class MediaCatalogCache(context: Context) {
    private val prefs = context.getSharedPreferences("sora_media_catalog_v1", Context.MODE_PRIVATE)

    fun read(type: ContentType): List<CachedMediaRecord> = readSnapshot(type).rows

    fun readSnapshot(type: ContentType, feed: String = DEFAULT_FEED): CachedMediaSnapshot =
        decodeSnapshot(prefs.getString(key(type, feed), null))

    fun search(type: ContentType, query: String): List<CachedMediaRecord> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return read(type)
        return read(type).filter { row ->
            row.title.lowercase().contains(q) || row.subtitle.lowercase().contains(q)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun write(type: ContentType, rows: List<CachedMediaRecord>) = write(type, DEFAULT_FEED, rows)

    fun write(type: ContentType, feed: String, rows: List<CachedMediaRecord>, fetchedAt: Long = System.currentTimeMillis()) {
        val clean = rows.filterNot(::isLegacyDiagnosticRecord)
        if (clean.isEmpty()) return
        val array = JSONArray()
        clean.take(MAX_ROWS).forEach { row ->
            array.put(JSONObject().apply {
                put("id", row.id)
                put("title", row.title)
                put("subtitle", row.subtitle)
                put("artworkUrl", row.artworkUrl ?: JSONObject.NULL)
                put("sourceId", row.sourceId)
                put("extensionPackage", row.extensionPackage)
            })
        }
        val root = JSONObject()
            .put("fetchedAt", fetchedAt)
            .put("items", array)
        prefs.edit().putString(key(type, feed), root.toString()).apply()
    }

    private fun decodeSnapshot(raw: String?): CachedMediaSnapshot {
        if (raw.isNullOrBlank()) return CachedMediaSnapshot(emptyList())
        return runCatching {
            if (raw.trimStart().startsWith("[")) {
                // Backward compatibility with the old untimestamped v1 cache.
                CachedMediaSnapshot(decodeRows(JSONArray(raw)).filterNot(::isLegacyDiagnosticRecord), 0L)
            } else {
                val root = JSONObject(raw)
                CachedMediaSnapshot(
                    rows = decodeRows(root.optJSONArray("items") ?: JSONArray()).filterNot(::isLegacyDiagnosticRecord),
                    fetchedAt = root.optLong("fetchedAt", 0L),
                )
            }
        }.getOrDefault(CachedMediaSnapshot(emptyList()))
    }

    private fun decodeRows(array: JSONArray): List<CachedMediaRecord> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            add(
                CachedMediaRecord(
                    id = id,
                    title = title,
                    subtitle = item.optString("subtitle"),
                    artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                    sourceId = item.optString("sourceId"),
                    extensionPackage = item.optString("extensionPackage"),
                )
            )
        }
    }

    private fun isLegacyDiagnosticRecord(row: CachedMediaRecord): Boolean =
        row.extensionPackage.contains(".demo", ignoreCase = true)

    private fun key(type: ContentType, feed: String): String {
        val cleanFeed = feed.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { DEFAULT_FEED }
        val base = "catalog_${type.name.lowercase()}"
        return if (cleanFeed == DEFAULT_FEED) base else "${base}_$cleanFeed"
    }

    private companion object {
        const val MAX_ROWS = 80
        const val DEFAULT_FEED = "default"
    }
}
'''
write('data/MediaCatalogCache.kt', cache)


# ---------------------------------------------------------------------------
# Jikan: richer factual details, current-season Anime, recently-started Manga,
# and only exact Adaptation relations for Anime <-> Manga counterpart links.
# ---------------------------------------------------------------------------
replace_once(
    'catalog/JikanCatalogClient.kt',
    '''    data class CatalogDetails(\n        val id: String,\n        val type: ContentType,\n        val title: String,\n        val subtitle: String,\n        val synopsis: String,\n        val artworkUrl: String?,\n        val score: Double?,\n        val status: String,\n        val genres: List<String>,\n    )''',
    '''    data class CatalogDetails(\n        val id: String,\n        val type: ContentType,\n        val title: String,\n        val alternateTitle: String,\n        val subtitle: String,\n        val synopsis: String,\n        val artworkUrl: String?,\n        val score: Double?,\n        val status: String,\n        val genres: List<String>,\n        val year: Int?,\n        val season: String,\n        val episodes: Int?,\n        val chapters: Int?,\n        val volumes: Int?,\n    )''',
)
replace_once(
    'catalog/JikanCatalogClient.kt',
    '''            ContentType.ANIME -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/anime?filter=bypopularity&limit=25&sfw=true"\n                "upcoming" -> "$BASE_URL/top/anime?filter=upcoming&limit=25&sfw=true"\n                "top" -> "$BASE_URL/top/anime?limit=25&sfw=true"\n                else -> "$BASE_URL/top/anime?filter=airing&limit=25&sfw=true"\n            }\n            ContentType.MANGA -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/manga?filter=bypopularity&limit=25"\n                "upcoming" -> "$BASE_URL/top/manga?filter=upcoming&limit=25"\n                "top" -> "$BASE_URL/top/manga?limit=25"\n                else -> "$BASE_URL/top/manga?filter=publishing&limit=25"\n            }''',
    '''            ContentType.ANIME -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/anime?filter=bypopularity&limit=25&sfw=true"\n                "season" -> "$BASE_URL/seasons/now?limit=25&sfw=true"\n                "upcoming" -> "$BASE_URL/top/anime?filter=upcoming&limit=25&sfw=true"\n                "top" -> "$BASE_URL/top/anime?limit=25&sfw=true"\n                else -> "$BASE_URL/top/anime?filter=airing&limit=25&sfw=true"\n            }\n            ContentType.MANGA -> when (cleanFeed) {\n                "popular" -> "$BASE_URL/top/manga?filter=bypopularity&limit=25"\n                "recent" -> "$BASE_URL/manga?order_by=start_date&sort=desc&limit=25"\n                "upcoming" -> "$BASE_URL/top/manga?filter=upcoming&limit=25"\n                "top" -> "$BASE_URL/top/manga?limit=25"\n                else -> "$BASE_URL/top/manga?filter=publishing&limit=25"\n            }''',
)
replace_span(
    'catalog/JikanCatalogClient.kt',
    '    private fun findOppositeRelation(data: JSONArray, opposite: ContentType): Pair<Int, String>? {',
    '    private fun parseList(root: JSONObject, type: ContentType): List<CatalogItem> {',
    '''    private fun findOppositeRelation(data: JSONArray, opposite: ContentType): Pair<Int, String>? {\n        val targetType = if (opposite == ContentType.ANIME) "anime" else "manga"\n        for (i in 0 until data.length()) {\n            val relation = data.optJSONObject(i) ?: continue\n            if (!relation.optString("relation").equals("Adaptation", ignoreCase = true)) continue\n            val entries = relation.optJSONArray("entry") ?: continue\n            for (j in 0 until entries.length()) {\n                val entry = entries.optJSONObject(j) ?: continue\n                if (!entry.optString("type").equals(targetType, ignoreCase = true)) continue\n                val id = entry.optInt("mal_id", -1)\n                if (id > 0) return id to entry.optString("name")\n            }\n        }\n        return null\n    }\n\n''',
)
replace_span(
    'catalog/JikanCatalogClient.kt',
    '    private fun parseDetails(item: JSONObject, type: ContentType): CatalogDetails {',
    '    private fun preferredTitle(item: JSONObject): String {',
    '''    private fun parseDetails(item: JSONObject, type: ContentType): CatalogDetails {\n        val id = item.optInt("mal_id", -1).toString()\n        val genres = buildList {\n            val array = item.optJSONArray("genres") ?: JSONArray()\n            for (i in 0 until array.length()) {\n                array.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)\n            }\n        }\n        val title = preferredTitle(item)\n        val originalTitle = item.optString("title").takeUnless { it == "null" }.orEmpty().trim()\n        val japaneseTitle = item.optString("title_japanese").takeUnless { it == "null" }.orEmpty().trim()\n        val alternateTitle = listOf(originalTitle, japaneseTitle)\n            .firstOrNull { it.isNotBlank() && !it.equals(title, ignoreCase = true) }\n            .orEmpty()\n        val directYear = item.optInt("year", 0).takeIf { it > 0 }\n        val publishedYear = item.optJSONObject("published")\n            ?.optJSONObject("prop")\n            ?.optJSONObject("from")\n            ?.optInt("year", 0)\n            ?.takeIf { it > 0 }\n        return CatalogDetails(\n            id = id,\n            type = type,\n            title = title,\n            alternateTitle = alternateTitle,\n            subtitle = subtitle(item, type),\n            synopsis = item.optString("synopsis").takeUnless { it == "null" }.orEmpty(),\n            artworkUrl = image(item),\n            score = item.optDouble("score").takeUnless { it.isNaN() || it <= 0.0 },\n            status = item.optString("status").takeUnless { it == "null" }.orEmpty(),\n            genres = genres,\n            year = directYear ?: publishedYear,\n            season = item.optString("season").takeUnless { it == "null" }.orEmpty(),\n            episodes = item.optInt("episodes", 0).takeIf { it > 0 },\n            chapters = item.optInt("chapters", 0).takeIf { it > 0 },\n            volumes = item.optInt("volumes", 0).takeIf { it > 0 },\n        )\n    }\n\n''',
)

for rel in ['catalog/JikanCatalogService.kt', 'extension/ExtensionManager.kt']:
    replace_once(
        rel,
        '''                                        put("title", details.title)\n                                        put("subtitle", details.subtitle)\n                                        put("description", details.synopsis)''',
        '''                                        put("title", details.title)\n                                        put("alternateTitle", details.alternateTitle)\n                                        put("subtitle", details.subtitle)\n                                        put("description", details.synopsis)''',
    )
    replace_once(
        rel,
        '''                                        put("status", details.status)\n                                        put("genres", JSONArray(details.genres))''',
        '''                                        put("status", details.status)\n                                        put("genres", JSONArray(details.genres))\n                                        put("year", details.year ?: JSONObject.NULL)\n                                        put("season", details.season)\n                                        put("episodes", details.episodes ?: JSONObject.NULL)\n                                        put("chapters", details.chapters ?: JSONObject.NULL)\n                                        put("volumes", details.volumes ?: JSONObject.NULL)''',
    )


# ---------------------------------------------------------------------------
# Detail screen: factual metadata, source controls out of primary content,
# strict title resolution, and Jikan-verified counterpart only.
# ---------------------------------------------------------------------------
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''private data class DetailMetadata(\n    val description: String,\n    val status: String = "",\n    val score: String = "",\n    val genres: List<String> = emptyList(),\n)''',
    '''private data class DetailMetadata(\n    val description: String,\n    val alternateTitle: String = "",\n    val status: String = "",\n    val score: String = "",\n    val genres: List<String> = emptyList(),\n    val year: Int? = null,\n    val season: String = "",\n    val episodes: Int? = null,\n    val chapters: Int? = null,\n    val volumes: Int? = null,\n)''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '    var sourceSearchError by remember { mutableStateOf<String?>(null) }\n',
    '    var sourceSearchError by remember { mutableStateOf<String?>(null) }\n    var sourceResolutionMessage by remember { mutableStateOf<String?>(null) }\n',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''            if (found == null) {\n                sourceSearchError = "${source.name} did not return a match for ${active.title}."\n            } else {\n                consumption = found\n                sourcePrefs.edit().putString(preferredSourceKey(active.type), "${ext.packageName}|${source.id}").apply()\n                if (dismissOnSuccess) sourcePickerOpen = false\n            }''',
    '''            if (found == null) {\n                sourceSearchError = "${source.name} did not return an exact title match for ${active.title}."\n                sourceResolutionMessage = "That source could not verify an exact match for this title. Choose another source manually."\n            } else {\n                consumption = found\n                sourceResolutionMessage = null\n                sourcePrefs.edit().putString(preferredSourceKey(active.type), "${ext.packageName}|${source.id}").apply()\n                if (dismissOnSuccess) sourcePickerOpen = false\n            }''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''        browserError = null\n        readerError = null\n        playbackError = null\n''',
    '''        browserError = null\n        readerError = null\n        playbackError = null\n        sourceResolutionMessage = null\n''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                searchSourceSelection(requested, option.first, option.second, manager) { found ->\n                    if (found != null && active.id == requested.id && active.type == requested.type) consumption = found\n                }''',
    '''                searchSourceSelection(requested, option.first, option.second, manager) { found ->\n                    if (active.id == requested.id && active.type == requested.type) {\n                        if (found != null) {\n                            consumption = found\n                            sourceResolutionMessage = null\n                        } else {\n                            sourceResolutionMessage = "Your default source (${option.second.name}) could not verify this title. Choose a source manually."\n                        }\n                    }\n                }''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                DetailActionRow(\n                    saved = isSaved(active),\n                    browserEnabled = webViewAvailable && !browserBusy,\n                    browserBusy = browserBusy,\n                    type = active.type,\n                    counterpart = counterpart,\n                    onLibrary = { onToggleSaved(active) },\n                    onAdaptation = { counterpart?.let { active = it } },\n                    onWebView = ::openBrowser,\n                )''',
    '''                DetailActionRow(\n                    saved = isSaved(active),\n                    type = active.type,\n                    counterpart = counterpart,\n                    onLibrary = { onToggleSaved(active) },\n                    onAdaptation = { counterpart?.let { active = it } },\n                )''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                        NoConsumptionSource(\n                            type = active.type,\n                            hasOptions = sourceOptions.isNotEmpty(),\n                            onChooseSource = { sourcePickerOpen = true },\n                        )''',
    '''                        NoConsumptionSource(\n                            type = active.type,\n                            hasOptions = sourceOptions.isNotEmpty(),\n                            message = sourceResolutionMessage,\n                            onChooseSource = { sourcePickerOpen = true },\n                        )''',
)
replace_span(
    'ui/screens/MediaDetailScreen.kt',
    'private fun DetailActionRow(',
    '@Composable\nprivate fun RowScope.DetailActionButton(',
    '''private fun DetailActionRow(\n    saved: Boolean,\n    type: ContentType,\n    counterpart: ExtensionMediaSelection?,\n    onLibrary: () -> Unit,\n    onAdaptation: () -> Unit,\n) {\n    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp)) {\n        DetailActionButton(\n            title = if (saved) "In library" else "Add to library",\n            icon = if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,\n            highlighted = saved,\n            onClick = onLibrary,\n        )\n        if (type == ContentType.ANIME || type == ContentType.MANGA) {\n            DetailActionButton(\n                title = if (type == ContentType.ANIME) "Manga" else "Anime",\n                icon = Icons.Rounded.SwapHoriz,\n                highlighted = counterpart != null,\n                enabled = counterpart != null,\n                onClick = onAdaptation,\n            )\n        } else {\n            Spacer(Modifier.weight(1f))\n        }\n    }\n}\n\n@Composable\n''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                if (selection.subtitle.isNotBlank()) Text(selection.subtitle, color = SoraMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)''',
    '''                if (metadata.alternateTitle.isNotBlank()) {\n                    Text(metadata.alternateTitle, color = SoraMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)\n                }\n                val facts = buildList {\n                    metadata.year?.let { add(it.toString()) }\n                    metadata.season.takeIf(String::isNotBlank)?.replaceFirstChar { it.uppercase() }?.let(::add)\n                    when (selection.type) {\n                        ContentType.ANIME -> metadata.episodes?.let { add("$it episodes") }\n                        ContentType.MANGA -> {\n                            metadata.chapters?.let { add("$it chapters") }\n                            metadata.volumes?.let { add("$it volumes") }\n                        }\n                        else -> Unit\n                    }\n                }\n                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoraMuted, fontSize = 12.sp, maxLines = 2)''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                Row(verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Rounded.Public, null, tint = SoraMuted, modifier = Modifier.size(16.dp))\n                    Text(sourceName, color = SoraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))\n                }\n''',
    '',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''                items(metadata.genres, key = { it }) { genre ->\n                    SuggestionChip(onClick = {}, label = { Text(genre, fontSize = 11.sp) })\n                }''',
    '''                items(metadata.genres, key = { it }) { genre ->\n                    Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(999.dp)) {\n                        Text(genre, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))\n                    }\n                }''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''private fun NoConsumptionSource(type: ContentType, hasOptions: Boolean, onChooseSource: () -> Unit) {\n    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {\n        Text("No ${consumptionNoun(type)} source selected", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)\n        Text(\n            if (hasOptions) "The catalog supplies the title metadata. Choose an installed source for the actual ${consumptionVerb(type)} data."\n            else "The catalog supplies the title metadata. Install a compatible source extension to load actual ${consumptionVerb(type)} data.",''',
    '''private fun NoConsumptionSource(type: ContentType, hasOptions: Boolean, message: String? = null, onChooseSource: () -> Unit) {\n    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {\n        Text("No ${consumptionNoun(type)} source selected", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)\n        Text(\n            message ?: if (hasOptions) "Choose an installed source for the actual ${consumptionVerb(type)} data."\n            else "Install a compatible source extension to load actual ${consumptionVerb(type)} data.",''',
)
replace_once(
    'ui/screens/MediaDetailScreen.kt',
    '''    DetailMetadata(\n        description = obj.optString("description", fallback),\n        status = obj.optString("status"),\n        score = score,\n        genres = genres,\n    )''',
    '''    DetailMetadata(\n        description = obj.optString("description", fallback),\n        alternateTitle = obj.optString("alternateTitle"),\n        status = obj.optString("status"),\n        score = score,\n        genres = genres,\n        year = obj.optInt("year", 0).takeIf { it > 0 },\n        season = obj.optString("season"),\n        episodes = obj.optInt("episodes", 0).takeIf { it > 0 },\n        chapters = obj.optInt("chapters", 0).takeIf { it > 0 },\n        volumes = obj.optInt("volumes", 0).takeIf { it > 0 },\n    )''',
)
replace_span(
    'ui/screens/MediaDetailScreen.kt',
    'private fun parseBestSourceSelection(',
    'private fun findCounterpart(',
    '''private fun parseBestSourceSelection(raw: String, sourceId: String, packageName: String, active: ExtensionMediaSelection): ExtensionMediaSelection? = runCatching {\n    val array = JSONArray(raw)\n    if (array.length() == 0) return@runCatching null\n    val target = normalizeTitle(active.title)\n    if (target.isBlank()) return@runCatching null\n\n    fun names(item: JSONObject): List<String> = buildList {\n        listOf("title", "alternateTitle", "englishTitle", "romajiTitle").forEach { key ->\n            item.optString(key).takeIf(String::isNotBlank)?.let(::add)\n        }\n        val aliases = item.optJSONArray("aliases")\n        if (aliases != null) for (index in 0 until aliases.length()) {\n            aliases.optString(index).takeIf(String::isNotBlank)?.let(::add)\n        }\n    }\n\n    var exact: JSONObject? = null\n    for (i in 0 until array.length()) {\n        val item = array.optJSONObject(i) ?: continue\n        if (names(item).any { normalizeTitle(it) == target }) {\n            exact = item\n            break\n        }\n    }\n    val item = exact ?: return@runCatching null\n    ExtensionMediaSelection(\n        id = item.optString("id"),\n        sourceId = sourceId,\n        extensionPackage = packageName,\n        type = active.type,\n        title = item.optString("title", active.title),\n        subtitle = item.optString("subtitle", active.subtitle),\n        artworkUrl = detailArtwork(item) ?: active.artworkUrl,\n    )\n}.getOrNull()\n\n''',
)
replace_span(
    'ui/screens/MediaDetailScreen.kt',
    'private fun findCounterpart(',
    'private fun parseRows(type: ContentType, raw: String): List<DetailRow> = runCatching {',
    '''private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {\n    if (active.type != ContentType.ANIME && active.type != ContentType.MANGA) return callback(null)\n    // Only Jikan's explicit Adaptation relation is trusted. A title search is not\n    // evidence that two works are counterparts and can link the wrong series.\n    if (!manager.findBuiltInJikanCounterpart(active) { result -> callback(result.getOrNull()) }) {\n        callback(null)\n    }\n}\n\n''',
)


# ---------------------------------------------------------------------------
# Anime/Manga browse/search: Jikan-only discovery, timestamped cached rails,
# honest loading/offline states, and a cleaner Netflix-like hierarchy.
# ---------------------------------------------------------------------------
replace_span(
    'ui/screens/MediaScreen.kt',
    '    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }',
    '    Column(modifier.fillMaxSize()) {',
    '''    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }\n    var popularRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var seasonRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var upcomingRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var topRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var discoverRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }\n    var searchOpen by remember { mutableStateOf(false) }\n    var query by remember { mutableStateOf("") }\n    var switchOpen by remember { mutableStateOf(false) }\n    var networkEpoch by remember { mutableIntStateOf(0) }\n    var refreshEpoch by remember { mutableIntStateOf(0) }\n    var primaryLoading by remember { mutableStateOf(false) }\n    var primaryError by remember { mutableStateOf<String?>(null) }\n    var primaryCacheFetchedAt by remember { mutableLongStateOf(0L) }\n    var feedFailures by remember { mutableIntStateOf(0) }\n\n    val engine = remember { MusicTasteEngine() }\n    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }\n\n    DisposableEffect(context) {\n        val connectivity = context.getSystemService(ConnectivityManager::class.java)\n        val mainHandler = Handler(Looper.getMainLooper())\n        val callback = object : ConnectivityManager.NetworkCallback() {\n            override fun onAvailable(network: Network) {\n                mainHandler.post { networkEpoch++ }\n            }\n        }\n        val registered = runCatching {\n            connectivity.registerDefaultNetworkCallback(callback)\n            true\n        }.getOrDefault(false)\n        onDispose {\n            if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }\n        }\n    }\n\n    fun setDestination(next: MediaDestination) {\n        destination = next\n        query = ""\n        selectedType = when (next) {\n            MediaDestination.ANIME_MANGA -> ContentType.ANIME\n            MediaDestination.MOVIES_TV -> ContentType.MOVIE\n            MediaDestination.MUSIC -> ContentType.MUSIC\n            MediaDestination.MEMES -> ContentType.MEME\n            MediaDestination.BIBLE -> ContentType.ANIME\n        }\n    }\n\n    fun selection(card: BrowseCard, type: ContentType = selectedType) = ExtensionMediaSelection(\n        id = card.id,\n        sourceId = card.sourceId,\n        extensionPackage = card.extensionPackage,\n        type = type,\n        title = card.title,\n        subtitle = card.subtitle,\n        artworkUrl = card.artworkUrl,\n    )\n\n    fun jikanSource(requestType: ContentType): Pair<InstalledExtension, com.night.sora.extension.api.SourceDescriptor>? {\n        if (requestType != ContentType.ANIME && requestType != ContentType.MANGA) return null\n        val key = typeKey(requestType)\n        val ext = extensions.firstOrNull { it.error == null && it.declaredId == "sora.core.jikan" } ?: return null\n        val source = ext.descriptor?.sources?.firstOrNull { key in it.contentTypes } ?: return null\n        return ext to source\n    }\n\n    fun loadJikanFeed(feed: String, requestType: ContentType, onResult: (Result<List<BrowseCard>>) -> Unit) {\n        val pair = jikanSource(requestType)\n        if (pair == null) {\n            onResult(Result.failure(IllegalStateException("Sora Anime & Manga catalog is unavailable")))\n            return\n        }\n        val (ext, source) = pair\n        val payload = JSONObject()\n            .put("sourceId", source.id)\n            .put("type", typeKey(requestType))\n            .put("feed", feed)\n            .toString()\n        manager.call(ext, ExtensionContract.Method.BROWSE, payload) { result ->\n            onResult(result.mapCatching { parseBrowse(it, source.id, ext.packageName) })\n        }\n    }\n\n    fun load(search: String) {\n        if (destination == MediaDestination.BIBLE) {\n            rows = emptyList()\n            primaryLoading = false\n            primaryError = null\n            return\n        }\n\n        val requestType = selectedType\n        val requestDestination = destination\n        val requestQuery = search.trim()\n\n        if (requestType == ContentType.ANIME || requestType == ContentType.MANGA) {\n            val pair = jikanSource(requestType)\n            val cached = if (requestQuery.isBlank()) {\n                val current = mediaCache.readSnapshot(requestType, "current")\n                if (current.rows.isNotEmpty()) current else mediaCache.readSnapshot(requestType)\n            } else null\n\n            if (requestQuery.isBlank()) {\n                rows = cached.orEmptySnapshot().rows.map { it.toBrowseCard() }\n                primaryCacheFetchedAt = cached?.fetchedAt ?: 0L\n            } else {\n                rows = emptyList()\n                primaryCacheFetchedAt = 0L\n            }\n            primaryLoading = true\n            primaryError = null\n\n            if (pair == null) {\n                primaryLoading = false\n                primaryError = "The built-in Jikan catalog is unavailable."\n                return\n            }\n            val (ext, source) = pair\n            val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH\n            val payload = JSONObject()\n                .put("sourceId", source.id)\n                .put("type", typeKey(requestType))\n                .put("query", requestQuery)\n                .toString()\n            manager.call(ext, method, payload) { result ->\n                if (selectedType != requestType || destination != requestDestination || query.trim() != requestQuery) return@call\n                primaryLoading = false\n                result.mapCatching { parseBrowse(it, source.id, ext.packageName) }\n                    .onSuccess { fresh ->\n                        rows = fresh\n                        primaryError = null\n                        if (requestQuery.isBlank() && fresh.isNotEmpty()) {\n                            mediaCache.write(requestType, "current", fresh.map { it.toCachedRecord() })\n                            primaryCacheFetchedAt = System.currentTimeMillis()\n                        }\n                    }\n                    .onFailure { error ->\n                        primaryError = error.message ?: "Could not refresh the catalog."\n                    }\n            }\n            return\n        }\n\n        primaryLoading = false\n        primaryError = null\n        val cached = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)\n        rows = cached.map { it.toBrowseCard() }\n        val key = typeKey(requestType)\n        val providers = extensions.flatMap { ext ->\n            ext.descriptor?.sources.orEmpty()\n                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }\n                .map { source -> ext to source }\n        }\n        if (providers.isEmpty()) return\n\n        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH\n        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }\n        var completed = 0\n        providers.forEachIndexed { index, (ext, source) ->\n            val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", requestQuery).toString()\n            manager.call(ext, method, payload) { result ->\n                collected[index] = result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty()\n                completed++\n                if (completed == providers.size) {\n                    val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }\n                    if (requestQuery.isBlank() && fresh.isNotEmpty()) mediaCache.write(requestType, fresh.map { it.toCachedRecord() })\n                    if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery && fresh.isNotEmpty()) rows = fresh\n                }\n            }\n        }\n    }\n\n    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch, refreshEpoch) {\n        if (destination == MediaDestination.BIBLE) return@LaunchedEffect\n        if (query.isNotBlank()) delay(250)\n        load(query)\n    }\n\n    LaunchedEffect(selectedType, extensions, destination, networkEpoch, refreshEpoch) {\n        popularRows = emptyList()\n        seasonRows = emptyList()\n        upcomingRows = emptyList()\n        topRows = emptyList()\n        discoverRows = emptyList()\n        feedFailures = 0\n        if (destination != MediaDestination.ANIME_MANGA) return@LaunchedEffect\n        if (selectedType != ContentType.ANIME && selectedType != ContentType.MANGA) return@LaunchedEffect\n        val requestType = selectedType\n\n        fun refreshFeed(feed: String, setRows: (List<BrowseCard>) -> Unit) {\n            val snapshot = mediaCache.readSnapshot(requestType, feed)\n            if (snapshot.rows.isNotEmpty()) setRows(snapshot.rows.map { it.toBrowseCard() })\n            loadJikanFeed(feed, requestType) { result ->\n                if (destination != MediaDestination.ANIME_MANGA || selectedType != requestType) return@loadJikanFeed\n                result.onSuccess { fresh ->\n                    if (fresh.isNotEmpty()) {\n                        setRows(fresh)\n                        mediaCache.write(requestType, feed, fresh.map { it.toCachedRecord() })\n                    }\n                }.onFailure { feedFailures++ }\n            }\n        }\n\n        refreshFeed("popular") { popularRows = it }\n        refreshFeed("top") { topRows = it }\n        if (requestType == ContentType.ANIME) {\n            refreshFeed("season") { seasonRows = it }\n            refreshFeed("upcoming") { upcomingRows = it }\n        } else {\n            refreshFeed("recent") { discoverRows = it }\n        }\n    }\n\n    Column(modifier.fillMaxSize()) {''',
)

# helper extension used only inside MediaScreen's local load function
replace_once(
    'ui/screens/MediaScreen.kt',
    'private fun CachedMediaRecord.toBrowseCard() = BrowseCard(id, title, subtitle, artworkUrl, sourceId, extensionPackage)\n',
    'private fun CachedMediaRecord.toBrowseCard() = BrowseCard(id, title, subtitle, artworkUrl, sourceId, extensionPackage)\nprivate fun com.night.sora.data.CachedMediaSnapshot?.orEmptySnapshot() = this ?: com.night.sora.data.CachedMediaSnapshot(emptyList())\n',
)
replace_once(
    'ui/screens/MediaScreen.kt',
    '            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)\n',
    '''            query.isNotBlank() -> SearchResultsSurface(\n                rows = rows, type = selectedType, query = query, loading = primaryLoading, error = primaryError,\n                selection = ::selection, onOpen = onOpenDetails, onPlayMusic = onPlayMusic, onRetry = { refreshEpoch++ },\n            )\n''',
)
replace_once(
    'ui/screens/MediaScreen.kt',
    '''            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(\n                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,\n                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,\n                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,\n            )''',
    '''            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(\n                type = selectedType, rows = rows, popularRows = popularRows, seasonRows = seasonRows,\n                upcomingRows = upcomingRows, topRows = topRows, discoverRows = discoverRows,\n                loading = primaryLoading, error = primaryError, cacheFetchedAt = primaryCacheFetchedAt, feedFailures = feedFailures,\n                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,\n                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress, onRetry = { refreshEpoch++ },\n            )''',
)
replace_span(
    'ui/screens/MediaScreen.kt',
    'private fun AnimeMangaSurface(',
    '@Composable\nprivate fun MovieTvSurface(',
    '''private fun AnimeMangaSurface(\n    type: ContentType,\n    rows: List<BrowseCard>,\n    popularRows: List<BrowseCard>,\n    seasonRows: List<BrowseCard>,\n    upcomingRows: List<BrowseCard>,\n    topRows: List<BrowseCard>,\n    discoverRows: List<BrowseCard>,\n    loading: Boolean,\n    error: String?,\n    cacheFetchedAt: Long,\n    feedFailures: Int,\n    libraryEntries: List<LibraryEntry>,\n    progressEntries: List<MediaProgressEntry>,\n    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,\n    isSaved: (ExtensionMediaSelection) -> Boolean,\n    onToggleSaved: (ExtensionMediaSelection) -> Unit,\n    onOpen: (ExtensionMediaSelection) -> Unit,\n    onResume: (MediaProgressEntry) -> Unit,\n    onRetry: () -> Unit,\n) {\n    val selected = rows.firstOrNull() ?: seasonRows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()\n    val saved = libraryEntries.filter { it.contentType == type }\n    val continued = progressEntries\n        .filter { it.contentType == type && it.progress > 0f && it.progress < .999f }\n        .sortedByDescending { it.updatedAt }\n    val currentLabel = if (type == ContentType.ANIME) "Airing now" else "Publishing now"\n    val notice = when {\n        error != null && rows.isNotEmpty() -> "Could not refresh. Showing saved catalog data${cacheAgeSuffix(cacheFetchedAt)}."\n        loading && rows.isNotEmpty() -> "Refreshing saved catalog data${cacheAgeSuffix(cacheFetchedAt)}…"\n        feedFailures > 0 -> "Some discovery sections could not refresh. Saved catalog data is shown where available."\n        else -> null\n    }\n\n    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {\n        if (notice != null) item { CatalogNotice(notice) }\n        when {\n            selected != null -> item {\n                val selectedMedia = selection(selected, type)\n                StreamFeature(\n                    card = selected,\n                    kicker = currentLabel,\n                    body = selected.subtitle,\n                    primaryLabel = "Details",\n                    selection = selectedMedia,\n                    isSaved = isSaved(selectedMedia),\n                    onToggleSaved = onToggleSaved,\n                    onOpen = onOpen,\n                )\n            }\n            loading -> item { EmptyFeatureShell(type) }\n            error != null -> item { CatalogFailure(error, onRetry) }\n            else -> item { CatalogFailure("No live ${type.label.lowercase()} catalog items were returned.", onRetry) }\n        }\n\n        if (continued.isNotEmpty()) item {\n            MediaSectionTitle(if (type == ContentType.ANIME) "Continue watching" else "Continue reading", "Resume exactly where you stopped")\n            ProgressLandscapeRail(continued, onResume)\n        }\n\n        if (saved.isNotEmpty()) item {\n            MediaSectionTitle("In your library", if (type == ContentType.ANIME) "Anime you saved" else "Manga you saved")\n            ContinueLandscapeRail(saved, onOpen)\n        }\n\n        if (type == ContentType.ANIME && seasonRows.isNotEmpty()) item {\n            MediaSectionTitle("Popular this season", "Currently airing seasonal Anime from Jikan")\n            PortraitRail(seasonRows, type, selection, onOpen)\n        }\n        if (type == ContentType.MANGA && popularRows.isNotEmpty()) item {\n            MediaSectionTitle("Popular manga", "Popular Manga from the live catalog")\n            PortraitRail(popularRows, type, selection, onOpen)\n        }\n        if (type == ContentType.ANIME && popularRows.isNotEmpty()) item {\n            MediaSectionTitle("Popular anime", "Popular Anime from the live catalog")\n            PortraitRail(popularRows, type, selection, onOpen)\n        }\n        if (topRows.isNotEmpty()) item {\n            MediaSectionTitle("Top 10 ${type.label.lowercase()}", "Highest-ranked titles returned by Jikan")\n            TopTenRail(topRows.take(10), type, selection, onOpen)\n        }\n        if (type == ContentType.ANIME && upcomingRows.isNotEmpty()) item {\n            MediaSectionTitle("Upcoming anime", "Upcoming titles from Jikan")\n            NewHotStack(upcomingRows.take(5), type, selection, onOpen)\n        }\n        if (type == ContentType.MANGA && discoverRows.isNotEmpty()) item {\n            MediaSectionTitle("Recently started", "Manga ordered by start date from Jikan")\n            PortraitRail(discoverRows, type, selection, onOpen)\n        }\n        if (rows.isNotEmpty()) item {\n            MediaSectionTitle(currentLabel, if (type == ContentType.ANIME) "Anime currently airing" else "Manga currently publishing")\n            PortraitRail(rows, type, selection, onOpen)\n        }\n    }\n}\n\n@Composable\n''',
)
replace_span(
    'ui/screens/MediaScreen.kt',
    'private fun SearchResultsSurface(',
    '@Composable\nprivate fun EmptyFeatureShell(',
    '''private fun SearchResultsSurface(\n    rows: List<BrowseCard>,\n    type: ContentType,\n    query: String,\n    loading: Boolean,\n    error: String?,\n    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,\n    onOpen: (ExtensionMediaSelection) -> Unit,\n    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,\n    onRetry: () -> Unit,\n) {\n    val musicQueue = remember(rows, type) { if (type == ContentType.MUSIC) rows.map { selection(it, type) } else emptyList() }\n    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {\n        item { MediaSectionTitle("Search", "${type.label} · ${query.trim()}") }\n        when {\n            loading && rows.isEmpty() -> items(6) { SearchResultSkeleton(type) }\n            error != null && rows.isEmpty() -> item { CatalogFailure(error, onRetry) }\n            !loading && rows.isEmpty() -> item {\n                Text("No ${type.label.lowercase()} results for “${query.trim()}”.", color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp))\n            }\n        }\n        items(rows, key = { "search-${type.name}-${it.id}" }) { card ->\n            Row(\n                Modifier.fillMaxWidth().clickable {\n                    if (type == ContentType.MUSIC) onPlayMusic(selection(card, type), musicQueue) else onOpen(selection(card, type))\n                }.padding(horizontal = 18.dp, vertical = 8.dp),\n                verticalAlignment = Alignment.CenterVertically,\n            ) {\n                Poster(card.artworkUrl, card.title, Modifier.size(width = 58.dp, height = if (type == ContentType.MUSIC) 58.dp else 82.dp), if (type == ContentType.MUSIC) 7 else 6)\n                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {\n                    Text(type.label.uppercase(), color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)\n                    Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)\n                    if (card.subtitle.isNotBlank()) Text(card.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                }\n                Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint, modifier = Modifier.size(18.dp))\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun SearchResultSkeleton(type: ContentType) {\n    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {\n        Box(Modifier.size(width = 58.dp, height = if (type == ContentType.MUSIC) 58.dp else 82.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))\n        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {\n            Box(Modifier.width(52.dp).height(7.dp).background(SoraSurface, RoundedCornerShape(4.dp)))\n            Box(Modifier.padding(top = 8.dp).fillMaxWidth(.72f).height(11.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))\n            Box(Modifier.padding(top = 7.dp).fillMaxWidth(.44f).height(8.dp).background(SoraSurface, RoundedCornerShape(4.dp)))\n        }\n    }\n}\n\n@Composable\n''',
)
replace_span(
    'ui/screens/MediaScreen.kt',
    'private fun EmptyFeatureShell(type: ContentType) {',
    '@Composable\nprivate fun StreamFeature(',
    '''private fun EmptyFeatureShell(type: ContentType) {\n    Box(\n        Modifier.fillMaxWidth().height(360.dp)\n            .background(Brush.verticalGradient(listOf(Color(0xFF252520), Color(0xFF171714), SoraBg)))\n    ) {\n        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {\n            Box(Modifier.width(84.dp).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))\n            Box(Modifier.padding(top = 12.dp).width(244.dp).height(30.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))\n            Box(Modifier.padding(top = 10.dp).width(290.dp).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))\n            Box(Modifier.padding(top = 7.dp).width(214.dp).height(10.dp).background(SoraSurface, RoundedCornerShape(5.dp)))\n            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                Box(Modifier.width(106.dp).height(42.dp).background(Color.White.copy(alpha = .10f), RoundedCornerShape(7.dp)))\n                Box(Modifier.width(106.dp).height(42.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)))\n            }\n        }\n    }\n}\n\n@Composable\n''',
)
replace_once(
    'ui/screens/MediaScreen.kt',
    '''                Button(onClick = { onOpen(selection) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(6.dp)) { Icon(if (selection.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(primaryLabel, fontWeight = FontWeight.Bold) }''',
    '''                Button(onClick = { onOpen(selection) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(6.dp)) { Icon(Icons.Rounded.Info, null); Spacer(Modifier.width(5.dp)); Text(primaryLabel, fontWeight = FontWeight.Bold) }''',
)
replace_span(
    'ui/screens/MediaScreen.kt',
    'private fun NewHotStack(',
    '@Composable\nprivate fun GenreRail(',
    '''private fun NewHotStack(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {\n    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {\n        rows.forEach { card ->\n            Row(Modifier.fillMaxWidth().clickable { onOpen(selection(card, type)) }, verticalAlignment = Alignment.CenterVertically) {\n                Poster(card.artworkUrl, card.title, Modifier.size(width = 62.dp, height = 88.dp), 6)\n                Column(Modifier.weight(1f).padding(start = 12.dp)) {\n                    Text("UPCOMING", color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)\n                    Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))\n                    if (card.subtitle.isNotBlank()) Text(card.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))\n                }\n                Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint, modifier = Modifier.size(18.dp))\n            }\n        }\n    }\n}\n\n@Composable\n''',
)
# Add compact cache/error components before HintLine.
replace_once(
    'ui/screens/MediaScreen.kt',
    '''@Composable\nprivate fun HintLine(text: String) { Text(text, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }''',
    '''@Composable\nprivate fun CatalogNotice(text: String) {\n    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {\n        Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted, modifier = Modifier.size(16.dp))\n        Text(text, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 8.dp))\n    }\n}\n\n@Composable\nprivate fun CatalogFailure(message: String, onRetry: () -> Unit) {\n    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp)) {\n        Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted, modifier = Modifier.size(28.dp))\n        Text("Catalog unavailable", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))\n        Text(message, color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))\n        TextButton(onClick = onRetry, contentPadding = PaddingValues(vertical = 8.dp)) { Text("Retry") }\n    }\n}\n\nprivate fun cacheAgeSuffix(fetchedAt: Long): String {\n    if (fetchedAt <= 0L) return ""\n    val minutes = ((System.currentTimeMillis() - fetchedAt).coerceAtLeast(0L) / 60_000L)\n    return when {\n        minutes < 1 -> " from moments ago"\n        minutes < 60 -> " from ${minutes}m ago"\n        minutes < 1_440 -> " from ${minutes / 60}h ago"\n        else -> " from ${minutes / 1_440}d ago"\n    }\n}\n\n@Composable\nprivate fun HintLine(text: String) { Text(text, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }''',
)

# Scope guardrails: no fake Anime/Manga data or title-search counterpart fallback.
media = read('ui/screens/MediaScreen.kt')
detail = read('ui/screens/MediaDetailScreen.kt')
client = read('catalog/JikanCatalogClient.kt')
assert 'The Last Platform' not in media
assert 'After Rain' not in media
assert 'best ?: array.optJSONObject(0)' not in detail
assert 'name.contains(normalized)' not in detail
assert 'relationName.equals("Adaptation"' not in client  # old fallback implementation removed
assert 'equals("Adaptation", ignoreCase = true)' in client
assert 'Popular this season' in media
assert 'Recently started' in media
assert 'Showing saved catalog data' in media
print('Sora Anime/Manga flow patch applied successfully.')
