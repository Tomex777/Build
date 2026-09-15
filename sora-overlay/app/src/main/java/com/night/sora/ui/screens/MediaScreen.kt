package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.ListeningSignal
import com.night.sora.recommendation.MusicTasteEngine
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private enum class MediaWorld(val label: String) {
    ANIME_MANGA("Anime & Manga"),
    MOVIES_TV("Movies & TV"),
    MUSIC("Music"),
    MEMES("Memes"),
}

private data class BrowseCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val sourceId: String,
    val extensionPackage: String,
)

@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    extensions: List<InstalledExtension>,
    extensionScanDone: Boolean,
    manager: ExtensionManager,
    listeningSignals: List<ListeningSignal>,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
) {
    var world by remember { mutableStateOf(MediaWorld.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(ContentType.ANIME) }
    var rows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sourceMissing by remember { mutableStateOf(false) }
    val engine = remember { MusicTasteEngine() }
    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }

    fun typeKey(type: ContentType): String = when (type) {
        ContentType.MOVIE -> "movie"
        ContentType.MEME -> "memes"
        else -> type.name.lowercase()
    }

    fun selection(card: BrowseCard) = ExtensionMediaSelection(
        id = card.id,
        sourceId = card.sourceId,
        extensionPackage = card.extensionPackage,
        type = selectedType,
        title = card.title,
        subtitle = card.subtitle,
        artworkUrl = card.artworkUrl,
    )

    fun load(search: String) {
        val key = typeKey(selectedType)
        val sourceExtension = extensions.firstOrNull { ext ->
            ext.error == null && ext.descriptor?.sources?.any { key in it.contentTypes } == true
        }
        if (sourceExtension == null) {
            rows = emptyList()
            loading = false
            sourceMissing = extensionScanDone
            return
        }
        val source = sourceExtension.descriptor!!.sources.first { key in it.contentTypes }
        sourceMissing = false
        loading = true
        val method = if (search.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val payload = JSONObject()
            .put("sourceId", source.id)
            .put("type", key)
            .put("query", search.trim())
            .toString()
        manager.call(sourceExtension, method, payload) { result ->
            rows = result.getOrNull()?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            add(
                                BrowseCard(
                                    id = item.getString("id"),
                                    title = item.getString("title"),
                                    subtitle = item.optString("subtitle", source.name),
                                    artworkUrl = artworkFrom(item),
                                    sourceId = source.id,
                                    extensionPackage = sourceExtension.packageName,
                                )
                            )
                        }
                    }
                }.getOrDefault(emptyList())
            } ?: emptyList()
            loading = false
        }
    }

    LaunchedEffect(selectedType, extensions, query) {
        if (query.isNotBlank()) delay(250)
        load(query)
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MediaHeader(
                searchOpen = searchOpen,
                query = query,
                selectedType = selectedType,
                onQuery = { query = it },
                onOpenSearch = { searchOpen = true },
                onCloseSearch = { searchOpen = false; query = "" },
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MediaWorld.entries) { item ->
                    FilterChip(
                        selected = world == item,
                        onClick = {
                            world = item
                            selectedType = when (item) {
                                MediaWorld.ANIME_MANGA -> ContentType.ANIME
                                MediaWorld.MOVIES_TV -> ContentType.MOVIE
                                MediaWorld.MUSIC -> ContentType.MUSIC
                                MediaWorld.MEMES -> ContentType.MEME
                            }
                            query = ""
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        }

        if (world == MediaWorld.ANIME_MANGA || world == MediaWorld.MOVIES_TV) {
            item {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) {
                    val options = if (world == MediaWorld.ANIME_MANGA) {
                        listOf(ContentType.ANIME, ContentType.MANGA)
                    } else listOf(ContentType.MOVIE, ContentType.TV)
                    options.forEach { type ->
                        TextButton(onClick = { selectedType = type; query = "" }) {
                            Text(
                                type.label,
                                color = if (selectedType == type) MaterialTheme.colorScheme.onBackground else SoraMuted,
                                fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        when {
            !extensionScanDone -> item {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp))
            }
            loading -> item {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp))
            }
            sourceMissing -> item {
                MissingMediaSource(selectedType.label, onOpenExtensions)
            }
            rows.isEmpty() && query.isNotBlank() -> item {
                EmptyMediaMessage("No results for “${query.trim()}”")
            }
            rows.isEmpty() -> item {
                EmptyMediaMessage("Nothing is available from this source yet.")
            }
            selectedType == ContentType.MUSIC -> {
                if (rankedTaste.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text("Made for you", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Because you listen to ${rankedTaste.first().artistName}", color = SoraMuted, fontSize = 12.sp)
                        }
                    }
                }
                items(rows, key = { it.id }) { card ->
                    MusicRow(card, onClick = { onOpenDetails(selection(card)) })
                }
            }
            selectedType == ContentType.MEME -> {
                item { PosterRail("Fresh memes", rows, selectedType, ::selection, onOpenDetails) }
                item { PosterRail("Keep scrolling", rows.reversed(), selectedType, ::selection, onOpenDetails) }
            }
            query.isNotBlank() -> {
                item { PosterRail("Search results", rows, selectedType, ::selection, onOpenDetails) }
            }
            else -> {
                item {
                    MediaHero(
                        card = rows.first(),
                        selection = selection(rows.first()),
                        saved = isSaved(selection(rows.first())),
                        onToggleSaved = onToggleSaved,
                        onOpen = onOpenDetails,
                    )
                }
                item { PosterRail("Trending now", rows, selectedType, ::selection, onOpenDetails) }
                item { RankedPosterRail("Top picks", rows, ::selection, onOpenDetails) }
                item { PosterRail("New & popular", rows.drop(1) + rows.take(1), selectedType, ::selection, onOpenDetails) }
            }
        }
    }
}

@Composable
private fun MediaHeader(
    searchOpen: Boolean,
    query: String,
    selectedType: ContentType,
    onQuery: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    if (searchOpen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCloseSearch) { Icon(Icons.Rounded.ArrowBack, "Close search") }
            Box(
                Modifier.weight(1f).background(SoraSurface, RoundedCornerShape(8.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search ${selectedType.label}", color = SoraMuted)
                        inner()
                    },
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Media", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search media") }
        }
    }
}

@Composable
private fun MediaHero(
    card: BrowseCard,
    selection: ExtensionMediaSelection,
    saved: Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        PosterArt(card.artworkUrl, card.title, Modifier.fillMaxSize(), radius = 0)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .12f),
                    .55f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(card.title, fontSize = 31.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, color = Color.White.copy(alpha = .82f), fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroAction(
                    icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    label = "My List",
                    modifier = Modifier.weight(1f),
                    onClick = { onToggleSaved(selection) },
                )
                Button(
                    onClick = { onOpen(selection) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.weight(1.25f).height(44.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                HeroAction(Icons.Rounded.Info, "Info", Modifier.weight(1f)) { onOpen(selection) }
            }
        }
    }
}

@Composable
private fun HeroAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(25.dp), tint = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PosterRail(
    title: String,
    rows: List<BrowseCard>,
    type: ContentType,
    selection: (BrowseCard) -> ExtensionMediaSelection,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(rows, key = { "${title}-${it.id}" }) { card ->
                Column(Modifier.width(if (type == ContentType.MEME) 164.dp else 112.dp).clickable { onOpen(selection(card)) }) {
                    PosterArt(
                        card.artworkUrl,
                        card.title,
                        Modifier.fillMaxWidth().aspectRatio(if (type == ContentType.MEME) 1f else 2f / 3f),
                    )
                    Text(card.title, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun RankedPosterRail(
    title: String,
    rows: List<BrowseCard>,
    selection: (BrowseCard) -> ExtensionMediaSelection,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows.take(10).withIndex().toList(), key = { "rank-${it.value.id}" }) { ranked ->
                Row(Modifier.clickable { onOpen(selection(ranked.value)) }, verticalAlignment = Alignment.Bottom) {
                    Text("${ranked.index + 1}", fontSize = 50.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF57575D))
                    PosterArt(ranked.value.artworkUrl, ranked.value.title, Modifier.width(104.dp).aspectRatio(2f / 3f))
                }
            }
        }
    }
}

@Composable
private fun MusicRow(card: BrowseCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterArt(card.artworkUrl, card.title, Modifier.size(54.dp), radius = 7)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(card.subtitle, color = SoraMuted, fontSize = 11.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = SoraMuted)
    }
}

@Composable
private fun PosterArt(
    url: String?,
    title: String,
    modifier: Modifier,
    radius: Int = 5,
) {
    Box(
        modifier.clip(RoundedCornerShape(radius.dp)).background(SoraSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(title.take(1).uppercase(), color = SoraMuted, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MissingMediaSource(label: String, onOpenExtensions: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ExtensionOff, null, tint = SoraMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text("No $label source installed", fontWeight = FontWeight.SemiBold)
        Text("Add a compatible source from More → Extensions.", color = SoraMuted, fontSize = 12.sp)
        TextButton(onClick = onOpenExtensions) { Text("Manage extensions") }
    }
}

@Composable
private fun EmptyMediaMessage(text: String) {
    Text(text, color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
}

private fun artworkFrom(item: JSONObject): String? = listOf(
    "artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl",
).firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }
