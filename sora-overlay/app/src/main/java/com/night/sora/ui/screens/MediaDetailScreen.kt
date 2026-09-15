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
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface
import org.json.JSONArray
import org.json.JSONObject

private data class DetailRow(val id: String, val title: String, val subtitle: String)

@Composable
fun MediaDetailScreen(
    selection: ExtensionMediaSelection,
    extension: InstalledExtension,
    manager: ExtensionManager,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
) {
    var description by remember { mutableStateOf(selection.subtitle) }
    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }

    fun effectiveBack() {
        if (secondaryRows.isNotEmpty()) {
            secondaryRows = emptyList()
            secondaryTitle = ""
        } else onBack()
    }

    BackHandler { effectiveBack() }

    LaunchedEffect(selection.id, extension.packageName) {
        manager.call(
            extension,
            ExtensionContract.Method.DETAILS,
            JSONObject().put("sourceId", selection.sourceId).put("id", selection.id).toString(),
        ) { result ->
            result.getOrNull()?.let { raw ->
                runCatching { JSONObject(raw).optString("description", selection.subtitle) }
                    .onSuccess { description = it }
            }
        }

        val method = when (selection.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.EPISODES
            ContentType.MANGA -> ExtensionContract.Method.CHAPTERS
            ContentType.MUSIC -> ExtensionContract.Method.LYRICS
            ContentType.MEME -> ExtensionContract.Method.FEED
            ContentType.MOVIE -> ExtensionContract.Method.STREAMS
        }
        manager.call(
            extension,
            method,
            JSONObject().put("sourceId", selection.sourceId).put("id", selection.id).toString(),
        ) { result ->
            childRows = result.getOrNull()?.let { raw -> parseRows(selection.type, raw) } ?: emptyList()
            loading = false
        }
    }

    fun openChild(row: DetailRow) {
        val method = when (selection.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.STREAMS
            ContentType.MANGA -> ExtensionContract.Method.PAGES
            else -> return
        }
        secondaryTitle = if (selection.type == ContentType.MANGA) row.title else "${row.title} · Streams"
        secondaryRows = listOf(DetailRow("loading", "Loading…", ""))
        manager.call(
            extension,
            method,
            JSONObject().put("sourceId", selection.sourceId).put("id", row.id).toString(),
        ) { result ->
            secondaryRows = result.getOrNull()?.let { raw -> parseSecondary(method, raw) } ?: emptyList()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(410.dp)) {
                Box(Modifier.fillMaxSize().background(SoraSurface)) {
                    if (!selection.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = selection.artworkUrl,
                            contentDescription = selection.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .14f),
                            .45f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                IconButton(
                    onClick = ::effectiveBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = .68f), CircleShape),
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = .68f), CircleShape),
                    ) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Source: ${extension.declaredName}") },
                            leadingIcon = { Icon(Icons.Rounded.Source, null) },
                            onClick = { menuOpen = false },
                        )
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(selection.title, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(selection.subtitle, color = Color.White.copy(alpha = .78f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Button(
                    onClick = {
                        when (selection.type) {
                            ContentType.ANIME, ContentType.TV, ContentType.MANGA -> childRows.firstOrNull()?.let(::openChild)
                            ContentType.MOVIE -> {
                                secondaryTitle = "Streams"
                                secondaryRows = childRows
                            }
                            else -> Unit
                        }
                    },
                    enabled = childRows.isNotEmpty() && selection.type != ContentType.MUSIC && selection.type != ContentType.MEME,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(5.dp),
                ) {
                    Icon(if (selection.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (selection.type == ContentType.MANGA) "Read" else "Play", fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DetailAction(if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add, "My List", onToggleSaved)
                }
                Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .86f), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        if (secondaryRows.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::effectiveBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Text(secondaryTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(secondaryRows, key = { it.id }) { row ->
                EpisodeStyleRow(row, icon = if (secondaryTitle.contains("Streams")) Icons.Rounded.HighQuality else Icons.Rounded.Image)
            }
        } else {
            item {
                Text(
                    primarySectionTitle(selection.type),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) }
            } else {
                items(childRows, key = { it.id }) { row ->
                    EpisodeStyleRow(
                        row,
                        icon = childIcon(selection.type),
                        onClick = when (selection.type) {
                            ContentType.ANIME, ContentType.TV, ContentType.MANGA -> ({ openChild(row) })
                            else -> null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(25.dp))
        Text(label, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EpisodeStyleRow(
    row: DetailRow,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(5.dp)).background(SoraSurface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SoraMuted)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, color = SoraMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MissingExtensionScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source unavailable") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("The source that supplied this title is no longer installed.")
        }
    }
}

private fun parseRows(type: ContentType, raw: String): List<DetailRow> = runCatching {
    when (type) {
        ContentType.MUSIC -> {
            val obj = JSONObject(raw)
            listOf(DetailRow(obj.optString("trackId", "lyrics"), "Lyrics", obj.optString("text", "No lyrics returned")))
        }
        else -> {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("id", "item-$i")
                    val title = item.optString("title").ifBlank { item.optString("label").ifBlank { "Item ${i + 1}" } }
                    val subtitle = when {
                        item.has("number") -> "#${item.optInt("number")}"
                        item.has("url") -> item.optString("url")
                        else -> ""
                    }
                    add(DetailRow(id, title, subtitle))
                }
            }
        }
    }
}.getOrDefault(emptyList())

private fun parseSecondary(method: String, raw: String): List<DetailRow> = runCatching {
    val arr = JSONArray(raw)
    buildList {
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            when (method) {
                ExtensionContract.Method.PAGES -> add(DetailRow("page-$i", "Page ${i + 1}", item.optString("url")))
                else -> add(DetailRow("stream-$i", item.optString("label", "Stream ${i + 1}"), item.optString("url")))
            }
        }
    }
}.getOrDefault(emptyList())

private fun primarySectionTitle(type: ContentType) = when (type) {
    ContentType.ANIME, ContentType.TV -> "Episodes"
    ContentType.MANGA -> "Chapters"
    ContentType.MOVIE -> "Streams"
    ContentType.MUSIC -> "Track"
    ContentType.MEME -> "Feed"
}

private fun childIcon(type: ContentType) = when (type) {
    ContentType.MANGA -> Icons.Rounded.Article
    ContentType.MUSIC -> Icons.Rounded.Lyrics
    ContentType.MEME -> Icons.Rounded.Image
    else -> Icons.Rounded.PlayArrow
}
