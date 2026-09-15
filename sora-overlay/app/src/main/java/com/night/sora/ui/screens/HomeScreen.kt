package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

private data class HomeBrowseCard(
    val id: String,
    val sourceId: String,
    val extensionPackage: String,
    val type: ContentType,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
) {
    fun selection() = ExtensionMediaSelection(id, sourceId, extensionPackage, type, title, subtitle, artworkUrl)
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    onOpenSelection: (ExtensionMediaSelection) -> Unit,
    onOpenBible: () -> Unit,
    onSearch: () -> Unit,
) {
    var recommendations by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }
    var music by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }
    var memes by remember { mutableStateOf<List<HomeBrowseCard>>(emptyList()) }

    LaunchedEffect(extensions) {
        if (extensions.isEmpty()) return@LaunchedEffect
        val result = mutableStateListOf<HomeBrowseCard>()
        loadHomeType(extensions, manager, ContentType.ANIME) { cards ->
            result.removeAll { it.type == ContentType.ANIME }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, ContentType.MANGA) { cards ->
            result.removeAll { it.type == ContentType.MANGA }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, ContentType.MOVIE) { cards ->
            result.removeAll { it.type == ContentType.MOVIE }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, ContentType.MUSIC) { cards -> music = cards.take(8) }
        loadHomeType(extensions, manager, ContentType.MEME) { cards -> memes = cards.take(4) }
    }

    val continueEntries = entries.filter { it.contentType != null }.take(8)
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 128.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SoraMark()
                    Spacer(Modifier.width(10.dp))
                    Text("Sora", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
            }
        }

        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 2.dp)) {
                Text(greeting.uppercase(), color = SoraMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("Your stuff,\nwhere you left it.", fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
            }
        }

        item { HomeSectionHeader("Continue", "Pick up exactly where you stopped", "History") }
        item {
            if (continueEntries.isEmpty()) {
                Text("Nothing in progress yet.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(continueEntries, key = { it.id }) { entry ->
                        ContinueCard(entry) { entry.toMediaSelection()?.let(onOpenSelection) }
                    }
                }
            }
        }

        item { HomeSectionHeader("For you", "Mixed from your library and sources", "Refresh") }
        item {
            if (recommendations.isEmpty()) {
                Text("Recommendations appear when sources are ready.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recommendations.take(10), key = { "${it.extensionPackage}:${it.sourceId}:${it.id}" }) { card ->
                        HomePosterTile(card) { onOpenSelection(card.selection()) }
                    }
                }
            }
        }

        item { HomeSectionHeader("Recently played", "Music stays with you across Sora", "Library") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(music.take(8), key = { "music-${it.id}" }) { card ->
                    HomeSquareTile(card) { onOpenSelection(card.selection()) }
                }
            }
        }

        item { HomeSectionHeader("You probably needed this", "From your meme source", "More") }
        item { MemeStrip(memes.firstOrNull()) }

        item { HomeSectionHeader("Bible", "Continue your reading", "Open", onSee = onOpenBible) }
        item {
            Surface(
                color = Color(0xFF151513),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onOpenBible),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("John 1 · verse 5", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                    Text("“The light shines in the darkness, and the darkness has not overcome it.”", fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp))
                    Text("Last read · John 1:1–5", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun SoraMark() {
    Box(Modifier.size(26.dp)) {
        Box(Modifier.align(Alignment.TopCenter).width(20.dp).height(10.dp).background(SoraText, RoundedCornerShape(10.dp, 10.dp, 3.dp, 3.dp)))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).width(20.dp).height(2.dp).background(SoraAccent, RoundedCornerShape(99.dp)))
    }
}

@Composable
private fun HomeSectionHeader(title: String, subtitle: String, see: String, onSee: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        TextButton(onClick = onSee, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(see, color = SoraMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun ContinueCard(entry: LibraryEntry, onClick: () -> Unit) {
    Row(Modifier.width(286.dp).height(154.dp).clip(RoundedCornerShape(18.dp)).background(SoraSurface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxHeight().width(118.dp).background(Color(0xFF2A2925)), contentAlignment = Alignment.Center) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(entry.label.take(1), fontSize = 36.sp, fontWeight = FontWeight.Black, color = SoraMuted)
        }
        Column(Modifier.fillMaxHeight().weight(1f).padding(13.dp)) {
            Text(entry.kind.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Text(entry.label, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(entry.detail, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { .62f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF49473F))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(SoraAccent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = SoraAccentInk, modifier = Modifier.size(13.dp)) }
                Text("Resume", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun HomePosterTile(card: HomeBrowseCard, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(126f / 178f).clip(RoundedCornerShape(8.dp)).background(SoraSurface)) {
            if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(7.dp).align(Alignment.TopStart)) {
                Text(card.type.label.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeSquareTile(card: HomeBrowseCard, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(126.dp).clip(RoundedCornerShape(8.dp)).background(SoraSurface)) {
            if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MemeStrip(meme: HomeBrowseCard?) {
    Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (meme != null) "source · now" else "meme source · waiting", color = Color(0xFF656158), fontSize = 11.sp)
                Icon(Icons.Rounded.MoreHoriz, null, tint = Color(0xFF656158), modifier = Modifier.size(18.dp))
            }
            Text(meme?.title ?: "me opening Sora to continue one manga and somehow starting four things", fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFC5C0B3)), contentAlignment = Alignment.Center) {
                if (!meme?.artworkUrl.isNullOrBlank()) AsyncImage(meme?.artworkUrl, meme?.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.TagFaces, null, modifier = Modifier.size(66.dp), tint = Color(0xFF5C594F))
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("♡ Save", color = Color(0xFF6D685D), fontSize = 12.sp)
                Text("↗ Share", color = Color(0xFF6D685D), fontSize = 12.sp)
                Text("Less like this", color = Color(0xFF6D685D), fontSize = 12.sp)
            }
        }
    }
}

private fun loadHomeType(extensions: List<InstalledExtension>, manager: ExtensionManager, type: ContentType, callback: (List<HomeBrowseCard>) -> Unit) {
    val key = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
    val ext = extensions.firstOrNull { it.error == null && it.descriptor?.sources?.any { source -> key in source.contentTypes } == true } ?: return callback(emptyList())
    val source = ext.descriptor!!.sources.first { key in it.contentTypes }
    manager.call(ext, ExtensionContract.Method.BROWSE, JSONObject().put("sourceId", source.id).put("type", key).toString()) { result ->
        val cards = result.getOrNull()?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(HomeBrowseCard(item.optString("id"), source.id, ext.packageName, type, item.optString("title", "Untitled"), item.optString("subtitle"), homeArtwork(item)))
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        callback(cards)
    }
}

private fun homeArtwork(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl")
    .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }
