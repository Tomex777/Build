package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.model.MediaProgressEntry
import com.night.sora.ui.theme.*

private enum class LibraryType(val label: String) {
    ALL("All"), ANIME("Anime"), MANGA("Manga"), MOVIE("Movies"), SERIES("Series"), MUSIC("Music"), MEMES("Memes")
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    progressEntries: List<MediaProgressEntry>,
    onOpenMedia: (ExtensionMediaSelection) -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryType.ALL) }
    var activeOnly by remember { mutableStateOf(false) }
    var sortTitle by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val visible = remember(entries, filter, activeOnly, sortTitle) {
        entries.filter { entry ->
            val typeMatch = when (filter) {
                LibraryType.ALL -> entry.contentType != null
                LibraryType.ANIME -> entry.contentType == ContentType.ANIME
                LibraryType.MANGA -> entry.contentType == ContentType.MANGA
                LibraryType.MOVIE -> entry.contentType == ContentType.MOVIE
                LibraryType.SERIES -> entry.contentType == ContentType.TV
                LibraryType.MUSIC -> entry.contentType == ContentType.MUSIC
                LibraryType.MEMES -> entry.contentType == ContentType.MEME
            }
            val activeMatch = !activeOnly || progressEntries.any { progress ->
                progress.mediaId == entry.mediaId && progress.sourceId == entry.sourceId &&
                    progress.extensionPackage == entry.extensionPackage && progress.progress < .999f
            }
            val queryMatch = searchQuery.isBlank() || listOf(entry.label, entry.detail, entry.mediaSubtitle, entry.kind)
                .any { it.contains(searchQuery.trim(), ignoreCase = true) }
            typeMatch && activeMatch && queryMatch
        }.let { list -> if (sortTitle) list.sortedBy { it.label.lowercase() } else list }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchOpen) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SoraText, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text("Search saved items", color = SoraMuted, fontSize = 15.sp)
                        inner()
                    },
                )
                IconButton(onClick = { if (searchQuery.isNotEmpty()) searchQuery = "" else searchOpen = false }) {
                    Icon(if (searchQuery.isNotEmpty()) Icons.Rounded.Close else Icons.Rounded.ArrowBack, "Close library search")
                }
            } else {
                Text("Library", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { searchOpen = true }) { Icon(Icons.Rounded.Search, "Search library") }
            }
            IconButton(onClick = { activeOnly = !activeOnly }) { Icon(Icons.Rounded.FilterList, "Filter library", tint = if (activeOnly) SoraAccent else SoraText) }
            IconButton(onClick = { sortTitle = !sortTitle }) { Icon(Icons.Rounded.Sort, "Sort library", tint = if (sortTitle) SoraAccent else SoraText) }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(LibraryType.entries.size) { index ->
                val item = LibraryType.entries[index]
                Column(Modifier.clickable { filter = item }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.label, color = if (filter == item) SoraText else SoraMuted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    if (filter == item) Box(Modifier.padding(top = 7.dp).height(2.dp).width(24.dp).background(SoraAccent, RoundedCornerShape(99.dp)))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .045f))

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${visible.size} item${if (visible.size == 1) "" else "s"}", color = SoraMuted, fontSize = 9.sp)
            Text(if (sortTitle) "Title A–Z" else "Last updated", color = SoraMuted, fontSize = 9.sp)
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved items in this filter.", color = SoraMuted, fontSize = 11.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(visible, key = { it.id }) {
                    entry ->
                    val progress = progressEntries.firstOrNull { saved ->
                        saved.mediaId == entry.mediaId && saved.sourceId == entry.sourceId &&
                            saved.extensionPackage == entry.extensionPackage && saved.progress < .999f
                    }
                    LibraryGridItem(entry, progress) { entry.toMediaSelection()?.let(onOpenMedia) }
                }
            }
        }
    }
}

@Composable
private fun LibraryGridItem(entry: LibraryEntry, progress: MediaProgressEntry?, onClick: () -> Unit) {
    val isMusic = entry.contentType == ContentType.MUSIC
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (isMusic) 1f else 2f / 3f)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF24231F)),
        ) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(color = Color(0xE611110F), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(7.dp).align(Alignment.TopStart)) {
                Text(
                    when (entry.contentType) { ContentType.TV -> "SERIES"; ContentType.MUSIC -> if (entry.kind.contains("playlist", true)) "PLAYLIST" else "MUSIC"; else -> entry.kind.uppercase() },
                    color = SoraText, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (progress != null && progress.total > 0L) {
                LinearProgressIndicator(progress = { progress.progress }, modifier = Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF5B5850))
            }
        }
        Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 1.dp, top = 7.dp))
        Text(entry.detail, color = SoraMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 1.dp))
    }
}
