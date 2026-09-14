@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.ui.components.DenseRow
import com.night.sora.ui.components.SectionHeader
import org.json.JSONArray
import org.json.JSONObject

private data class DetailRow(val id: String, val title: String, val subtitle: String)

@Composable
fun MediaDetailScreen(
    selection: ExtensionMediaSelection,
    extension: InstalledExtension,
    manager: ExtensionManager,
    onBack: () -> Unit,
) {
    var description by remember { mutableStateOf(selection.subtitle) }
    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

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
        secondaryTitle = if (selection.type == ContentType.MANGA) "Pages" else "Streams"
        secondaryRows = listOf(DetailRow("loading", "Loading…", ""))
        manager.call(
            extension,
            method,
            JSONObject().put("sourceId", selection.sourceId).put("id", row.id).toString(),
        ) { result ->
            secondaryRows = result.getOrNull()?.let { raw -> parseSecondary(method, raw) } ?: emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selection.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "More") } },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 10.dp)) {
            item { DenseRow(selection.title, description, iconForType(selection.type)) }
            item { DenseRow("Add to Library", "Keep this in Sora", Icons.Rounded.BookmarkAdd) }
            if (selection.type != ContentType.MEME) {
                item { DenseRow("Download", "Sora owns download state; the source supplies media data.", Icons.Rounded.Download) }
            }
            item { DenseRow("Source", "${extension.declaredName} · ${selection.sourceId}", Icons.Rounded.Source) }
            item { SectionHeader(primarySectionTitle(selection.type)) }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            } else {
                items(childRows) { row ->
                    DenseRow(row.title, row.subtitle, childIcon(selection.type), onClick = { openChild(row) })
                }
            }
            if (secondaryRows.isNotEmpty()) {
                item { SectionHeader(secondaryTitle) }
                items(secondaryRows) { row ->
                    DenseRow(row.title, row.subtitle, if (secondaryTitle == "Pages") Icons.Rounded.Image else Icons.Rounded.HighQuality)
                }
            }
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("The extension that supplied this item is no longer installed.")
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
                    val title = item.optString("title").ifBlank {
                        item.optString("label").ifBlank { "Item ${i + 1}" }
                    }
                    val subtitle = when {
                        item.has("number") -> "#${item.optInt("number")}"
                        item.has("url") -> item.optString("url")
                        else -> "From extension"
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
                ExtensionContract.Method.PAGES -> add(
                    DetailRow("page-$i", "Page ${i + 1}", item.optString("url"))
                )
                else -> add(
                    DetailRow("stream-$i", item.optString("label", "Stream ${i + 1}"), item.optString("url"))
                )
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

private fun iconForType(type: ContentType) = when (type) {
    ContentType.MANGA -> Icons.Rounded.MenuBook
    ContentType.MUSIC -> Icons.Rounded.MusicNote
    ContentType.MEME -> Icons.Rounded.TagFaces
    else -> Icons.Rounded.PlayCircle
}

private fun childIcon(type: ContentType) = when (type) {
    ContentType.MANGA -> Icons.Rounded.Article
    ContentType.MUSIC -> Icons.Rounded.Lyrics
    ContentType.MEME -> Icons.Rounded.Image
    else -> Icons.Rounded.PlayArrow
}
