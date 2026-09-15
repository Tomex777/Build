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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    ANIME_MANGA("Anime & Manga"),
    MOVIES_TV("Movies & TV"),
    MUSIC("Music"),
    FILES("Files"),
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    onOpenMedia: (ExtensionMediaSelection) -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    val mediaEntries = entries.filter { entry ->
        when (filter) {
            LibraryFilter.ALL -> entry.contentType != null
            LibraryFilter.ANIME_MANGA -> entry.contentType == ContentType.ANIME || entry.contentType == ContentType.MANGA
            LibraryFilter.MOVIES_TV -> entry.contentType == ContentType.MOVIE || entry.contentType == ContentType.TV
            LibraryFilter.MUSIC -> entry.contentType == ContentType.MUSIC
            LibraryFilter.FILES -> entry.contentType == null && entry.kind.equals("File", true)
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp)) {
            Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Everything you chose to keep.", color = SoraMuted, fontSize = 13.sp)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries.size) { index ->
                val item = LibraryFilter.entries[index]
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (mediaEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) {
                    Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(42.dp), tint = SoraMuted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (filter == LibraryFilter.FILES) "No files yet" else "Nothing saved here yet",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (filter == LibraryFilter.FILES) "Files you add to Sora will live here."
                        else "Add titles from Media and they will show up here.",
                        color = SoraMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(mediaEntries, key = { it.id }) { entry ->
                    LibraryCard(entry) { entry.toMediaSelection()?.let(onOpenMedia) }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(entry: LibraryEntry, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(SoraSurface, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!entry.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.artworkUrl,
                    contentDescription = entry.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(entry.label.take(1).uppercase(), fontSize = 28.sp, color = SoraMuted, fontWeight = FontWeight.Bold)
            }
        }
        Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Text(entry.kind, color = SoraMuted, fontSize = 9.sp, maxLines = 1)
    }
}
