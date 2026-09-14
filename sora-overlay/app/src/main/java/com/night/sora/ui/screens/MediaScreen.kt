package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.ListeningSignal
import com.night.sora.recommendation.MusicTasteEngine
import com.night.sora.ui.components.DenseRow
import com.night.sora.ui.components.Pill
import com.night.sora.ui.components.SectionHeader
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val mediaTypes = listOf("Anime", "Manga", "Movies", "TV", "Music", "Memes")

private data class BrowseRow(
    val id: String,
    val title: String,
    val subtitle: String,
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
    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
) {
    var selected by remember { mutableStateOf("Anime") }
    var rows by remember { mutableStateOf<List<BrowseRow>>(emptyList()) }
    var relatedArtists by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val engine = remember { MusicTasteEngine() }
    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }

    fun contentType(): ContentType = when (selected) {
        "Anime" -> ContentType.ANIME
        "Manga" -> ContentType.MANGA
        "Movies" -> ContentType.MOVIE
        "TV" -> ContentType.TV
        "Music" -> ContentType.MUSIC
        else -> ContentType.MEME
    }

    fun typeKey(): String = selected.lowercase().let { if (it == "movies") "movie" else it }

    fun load(search: String) {
        val typeKey = typeKey()
        val sourceExtension = extensions.firstOrNull { ext ->
            ext.error == null && ext.descriptor?.sources?.any { typeKey in it.contentTypes } == true
        }
        if (sourceExtension == null) {
            rows = emptyList()
            relatedArtists = emptyList()
            loading = false
            return
        }
        val source = sourceExtension.descriptor!!.sources.first { typeKey in it.contentTypes }
        loading = true
        val method = if (search.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val payload = JSONObject()
            .put("sourceId", source.id)
            .put("type", typeKey)
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
                                BrowseRow(
                                    id = item.getString("id"),
                                    title = item.getString("title"),
                                    subtitle = item.optString("subtitle", source.name),
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

        if (selected == "Music" && search.isBlank() && "relatedArtists" in source.capabilities && rankedTaste.isNotEmpty()) {
            manager.call(
                sourceExtension,
                ExtensionContract.Method.RELATED_ARTISTS,
                JSONObject().put("sourceId", source.id).put("id", rankedTaste.first().artistId).toString(),
            ) { result ->
                relatedArtists = result.getOrNull()?.let { raw ->
                    runCatching {
                        val arr = JSONArray(raw)
                        buildList { for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("name")) }
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }
        } else {
            relatedArtists = emptyList()
        }
    }

    LaunchedEffect(selected, extensions, query) {
        if (query.isNotBlank()) delay(250)
        load(query)
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            if (searchOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp, 18.dp, 12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { searchOpen = false; query = "" }) {
                        Icon(Icons.Rounded.ArrowBack, "Close search")
                    }
                    Box(
                        Modifier.weight(1f)
                            .background(SoraSurface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Search $selected", color = SoraMuted)
                                inner()
                            },
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(20.dp, 24.dp, 12.dp, 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Media", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Sources plug in. Sora stays consistent.", color = SoraMuted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Rounded.Search, "Search") }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mediaTypes.take(3).forEach { type -> Pill(type, selected == type) { selected = type } }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mediaTypes.drop(3).forEach { type -> Pill(type, selected == type) { selected = type } }
            }
        }

        if (selected == "Music" && query.isBlank() && rankedTaste.isNotEmpty()) {
            item { SectionHeader("Made for you") }
            item {
                DenseRow(
                    "Because you listen to ${rankedTaste.first().artistName}",
                    "Sora ranked this from your own listening history.",
                    Icons.Rounded.AutoAwesome,
                )
            }
            if (relatedArtists.isNotEmpty()) {
                item { SectionHeader("Similar artists") }
                items(relatedArtists) { artist ->
                    DenseRow(artist, "Suggested by the active source; ranked by Sora Core", Icons.Rounded.Person)
                }
            }
            item { SectionHeader("From your source") }
        } else {
            item { SectionHeader(if (query.isBlank()) "Browse" else "Search results") }
        }

        when {
            !extensionScanDone -> item { Text("Checking installed sources…", Modifier.padding(20.dp), color = SoraMuted) }
            loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            rows.isEmpty() && query.isNotBlank() -> item {
                DenseRow("No results", "Nothing in the active $selected source matched “${query.trim()}”.", Icons.Rounded.SearchOff)
            }
            rows.isEmpty() -> item {
                DenseRow(
                    "No $selected source installed",
                    "Install an extension that supports ${selected.lowercase()}.",
                    Icons.Rounded.Extension,
                    onClick = onOpenExtensions,
                )
            }
            else -> items(rows) { row ->
                DenseRow(
                    row.title,
                    row.subtitle,
                    iconFor(selected),
                    onClick = {
                        onOpenDetails(
                            ExtensionMediaSelection(
                                id = row.id,
                                sourceId = row.sourceId,
                                extensionPackage = row.extensionPackage,
                                type = contentType(),
                                title = row.title,
                                subtitle = row.subtitle,
                            )
                        )
                    },
                )
            }
        }
    }
}

private fun iconFor(type: String) = when (type) {
    "Music" -> Icons.Rounded.MusicNote
    "Memes" -> Icons.Rounded.TagFaces
    "Manga" -> Icons.Rounded.MenuBook
    else -> Icons.Rounded.PlayCircle
}
