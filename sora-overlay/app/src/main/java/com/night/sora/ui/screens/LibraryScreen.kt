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
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.*

private enum class LibraryType(val label: String) {
    ALL("All"), ANIME("Anime"), MANGA("Manga"), MOVIE("Movies"), SERIES("Series"), MUSIC("Music"), MEMES("Memes")
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    onOpenMedia: (ExtensionMediaSelection) -> Unit,
    onSearch: () -> Unit = {},
) {
    var filter by remember { mutableStateOf(LibraryType.ALL) }
    var activeOnly by remember { mutableStateOf(false) }
    var sortTitle by remember { mutableStateOf(false) }

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
            val activeMatch = !activeOnly || entry.detail.contains("Episode", true) || entry.detail.contains("Chapter", true) || entry.detail.contains("left", true) || entry.detail.contains("new", true)
            typeMatch && activeMatch
        }.let { list -> if (sortTitle) list.sortedBy { it.label.lowercase() } else list }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search library") }
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
                items(visible, key = { it.id }) { entry -> LibraryGridItem(entry) { entry.toMediaSelection()?.let(onOpenMedia) } }
            }
        }
    }
}

@Composable
private fun LibraryGridItem(entry: LibraryEntry, onClick: () -> Unit) {
    val squareArtwork = entry.contentType == ContentType.MUSIC || entry.contentType == ContentType.MEME
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (squareArtwork) 1f else 2f / 3f)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF24231F)),
        ) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(color = Color(0xE611110F), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(7.dp).align(Alignment.TopStart)) {
                Text(
                    when (entry.contentType) { ContentType.TV -> "SERIES"; ContentType.MUSIC -> if (entry.kind.contains("playlist", true)) "PLAYLIST" else "MUSIC"; ContentType.MEME -> "MEME"; else -> entry.kind.uppercase() },
                    color = SoraText, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (entry.detail.contains("new", true)) {
                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(21.dp).background(SoraAccent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("1", color = SoraAccentInk, fontSize = 8.sp, fontWeight = FontWeight.Black) }
            }
            if (entry.detail.contains("left", true) || entry.detail.contains("Episode", true) || entry.detail.contains("Chapter", true)) {
                LinearProgressIndicator(progress = { .58f }, modifier = Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF5B5850))
            }
        }
        Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 1.dp, top = 7.dp))
        Text(entry.detail, color = SoraMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 1.dp))
    }
}