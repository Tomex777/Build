@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.night.sora.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

private data class DetailRow(val id: String, val title: String, val subtitle: String)

@Composable
fun MediaDetailScreen(
    selection: ExtensionMediaSelection,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onBack: () -> Unit,
) {
    var active by remember(selection) { mutableStateOf(selection) }
    var description by remember { mutableStateOf(active.subtitle) }
    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var sourcePickerOpen by remember { mutableStateOf(false) }
    var counterpart by remember { mutableStateOf<ExtensionMediaSelection?>(null) }
    var mangaLatestFirst by remember { mutableStateOf(true) }

    val extension = extensions.firstOrNull { it.packageName == active.extensionPackage }

    fun effectiveBack() {
        if (secondaryRows.isNotEmpty()) {
            secondaryRows = emptyList(); secondaryTitle = ""
        } else onBack()
    }

    BackHandler { effectiveBack() }

    LaunchedEffect(active.id, active.sourceId, active.extensionPackage) {
        secondaryRows = emptyList(); secondaryTitle = ""; childRows = emptyList(); loading = true; description = active.subtitle; counterpart = null
        val ext = extensions.firstOrNull { it.packageName == active.extensionPackage }
        if (ext == null) { loading = false; return@LaunchedEffect }

        manager.call(ext, ExtensionContract.Method.DETAILS, JSONObject().put("sourceId", active.sourceId).put("id", active.id).toString()) { result ->
            result.getOrNull()?.let { raw -> runCatching { JSONObject(raw).optString("description", active.subtitle) }.onSuccess { description = it } }
        }

        val method = when (active.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.EPISODES
            ContentType.MANGA -> ExtensionContract.Method.CHAPTERS
            ContentType.MUSIC -> ExtensionContract.Method.LYRICS
            ContentType.MEME -> ExtensionContract.Method.FEED
            ContentType.MOVIE -> ExtensionContract.Method.STREAMS
        }
        manager.call(ext, method, JSONObject().put("sourceId", active.sourceId).put("id", active.id).toString()) { result ->
            childRows = result.getOrNull()?.let { parseRows(active.type, it) } ?: emptyList(); loading = false
        }

        if (active.type == ContentType.ANIME || active.type == ContentType.MANGA) {
            findCounterpart(active, extensions, manager) { found -> counterpart = found }
        }
    }

    fun openChild(row: DetailRow) {
        val ext = extensions.firstOrNull { it.packageName == active.extensionPackage } ?: return
        val method = when (active.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.STREAMS
            ContentType.MANGA -> ExtensionContract.Method.PAGES
            else -> return
        }
        secondaryTitle = if (active.type == ContentType.MANGA) row.title else "${row.title} · Sources"
        secondaryRows = listOf(DetailRow("loading", "Loading…", ""))
        manager.call(ext, method, JSONObject().put("sourceId", active.sourceId).put("id", row.id).toString()) { result ->
            secondaryRows = result.getOrNull()?.let { parseSecondary(method, it) } ?: emptyList()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 42.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(390.dp)) {
                Box(Modifier.fillMaxSize().background(SoraSurface)) {
                    if (!active.artworkUrl.isNullOrBlank()) AsyncImage(active.artworkUrl, active.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f), Color.Transparent, SoraBg))))
                IconButton(onClick = ::effectiveBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = .64f), CircleShape)) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.background(Color.Black.copy(alpha = .64f), CircleShape)) { Icon(Icons.Rounded.MoreVert, "Title options", tint = Color.White) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
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
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(if (active.type == ContentType.ANIME || active.type == ContentType.MANGA) "ANIME + MANGA" else active.type.label.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(active.title, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(active.subtitle, color = Color.White.copy(alpha = .78f), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if ((active.type == ContentType.ANIME || active.type == ContentType.MANGA) && counterpart != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdaptationButton("Anime", active.type == ContentType.ANIME, Modifier.weight(1f)) {
                        if (active.type != ContentType.ANIME) counterpart?.takeIf { it.type == ContentType.ANIME }?.let { active = it }
                    }
                    AdaptationButton("Manga", active.type == ContentType.MANGA, Modifier.weight(1f)) {
                        if (active.type != ContentType.MANGA) counterpart?.takeIf { it.type == ContentType.MANGA }?.let { active = it }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                if (active.type != ContentType.MUSIC && active.type != ContentType.MEME) {
                    Button(
                        onClick = { childRows.firstOrNull()?.let(::openChild) },
                        enabled = childRows.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Icon(if (active.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp)); Text(if (active.type == ContentType.MANGA) "Read" else "Play", fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    DetailAction(if (isSaved(active)) Icons.Rounded.Check else Icons.Rounded.Add, "Library") { onToggleSaved(active) }
                    DetailAction(Icons.Rounded.Download, "Download") { }
                    DetailAction(Icons.Rounded.Share, "Share") { }
                }
                Text(description, color = Color(0xFFD8D5CD), fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        if (secondaryRows.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::effectiveBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Text(secondaryTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(secondaryRows, key = { it.id }) { row -> DetailListRow(row, if (active.type == ContentType.MANGA) Icons.Rounded.Image else Icons.Rounded.Public, null) }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sectionTitle(active.type), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (active.type == ContentType.MANGA) TextButton(onClick = { mangaLatestFirst = !mangaLatestFirst }) { Text(if (mangaLatestFirst) "Latest first⌄" else "Oldest first⌃", color = SoraMuted, fontSize = 10.sp) }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) }
            else {
                val rows = if (active.type == ContentType.MANGA && !mangaLatestFirst) childRows.reversed() else childRows
                items(rows, key = { it.id }) { row ->
                    DetailListRow(row, childIcon(active.type), when (active.type) { ContentType.ANIME, ContentType.TV, ContentType.MANGA -> ({ openChild(row) }); else -> null })
                }
            }
        }

        if (active.type == ContentType.ANIME || active.type == ContentType.MANGA) {
            item {
                Text("You may also like", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))
                Text("Sora keeps anime and manga linked when a matching adaptation is available.", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }

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
private fun AdaptationButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (active) SoraAccent else SoraSurfaceHigh, contentColor = if (active) SoraAccentInk else SoraText),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, modifier = Modifier.size(24.dp)); Text(label, fontSize = 9.sp, color = SoraMuted, modifier = Modifier.padding(top = 5.dp)) }
}

@Composable
private fun DetailListRow(row: DetailRow, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)).background(SoraSurfaceHigh), contentAlignment = Alignment.Center) { Icon(icon, null, tint = SoraMuted) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(row.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1); if (row.subtitle.isNotBlank()) Text(row.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint)
    }
}

@Composable
fun MissingExtensionScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Source unavailable") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { Text("The source that supplied this title is no longer installed.") }
    }
}

private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {
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

private fun parseRows(type: ContentType, raw: String): List<DetailRow> = runCatching {
    when (type) {
        ContentType.MUSIC -> { val obj = JSONObject(raw); listOf(DetailRow(obj.optString("trackId", "lyrics"), "Lyrics", obj.optString("text", "No lyrics returned"))) }
        else -> { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val item = arr.getJSONObject(i); val id = item.optString("id", "item-$i"); val title = item.optString("title").ifBlank { item.optString("label").ifBlank { "Item ${i + 1}" } }; val subtitle = when { item.has("number") -> "#${item.optInt("number")}"; item.has("url") -> item.optString("url"); else -> "" }; add(DetailRow(id, title, subtitle)) } } }
    }
}.getOrDefault(emptyList())

private fun parseSecondary(method: String, raw: String): List<DetailRow> = runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val item = arr.getJSONObject(i); if (method == ExtensionContract.Method.PAGES) add(DetailRow("page-$i", "Page ${i + 1}", item.optString("url"))) else add(DetailRow("stream-$i", item.optString("label", "Source ${i + 1}"), item.optString("url"))) } } }.getOrDefault(emptyList())
private fun sectionTitle(type: ContentType) = when (type) { ContentType.ANIME, ContentType.TV -> "Episodes"; ContentType.MANGA -> "Chapters"; ContentType.MOVIE -> "Sources"; ContentType.MUSIC -> "Track"; ContentType.MEME -> "Post" }
private fun childIcon(type: ContentType) = when (type) { ContentType.MANGA -> Icons.Rounded.Article; ContentType.MUSIC -> Icons.Rounded.Article; ContentType.MEME -> Icons.Rounded.Image; else -> Icons.Rounded.PlayArrow }
private fun detailArtwork(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl").firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }


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
