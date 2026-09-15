package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.components.SectionHeader
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    onOpenMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGames: () -> Unit,
) {
    val mediaEntries = entries.filter { it.contentType != null }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 6.dp)) {
                Text("Sora", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Your private space", color = SoraMuted, fontSize = 13.sp)
            }
        }

        item { SectionHeader("Continue") }
        if (mediaEntries.isEmpty()) {
            item {
                Text(
                    "Your watching, reading and listening progress will show here.",
                    color = SoraMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(mediaEntries.take(8), key = { it.id }) { entry ->
                        ContinueCard(entry, onClick = onOpenLibrary)
                    }
                }
            }
        }

        item { SectionHeader("For you") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HomeFeatureCard("Anime & Manga", "Pick up a series or find something new", Icons.Rounded.AutoStories, onOpenMedia) }
                item { HomeFeatureCard("Movies & TV", "A quieter place for long-form watching", Icons.Rounded.Movie, onOpenMedia) }
                item { HomeFeatureCard("Music", "Your taste, history and recommendations", Icons.Rounded.Headphones, onOpenMedia) }
            }
        }

        item { SectionHeader("Games") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeMiniCard("Would You Rather", Icons.Rounded.SwapHoriz, Modifier.weight(1f), onClick = onOpenGames)
                HomeMiniCard("Most Likely To", Icons.Rounded.Groups, Modifier.weight(1f), onClick = onOpenGames)
            }
        }

        item { SectionHeader("More for you") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeMiniCard("Bible", Icons.Rounded.MenuBook, Modifier.weight(1f), enabled = false) { }
                HomeMiniCard("Memes", Icons.Rounded.TagFaces, Modifier.weight(1f), onClick = onOpenMedia)
            }
        }
    }
}

@Composable
private fun ContinueCard(entry: LibraryEntry, onClick: () -> Unit) {
    Column(Modifier.width(156.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(SoraSurface, RoundedCornerShape(5.dp)),
        ) {
            if (!entry.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.artworkUrl,
                    contentDescription = entry.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(34.dp).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White) }
        }
        Text(entry.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(entry.kind, color = SoraMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun HomeFeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(214.dp).clickable(onClick = onClick).background(SoraSurface, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(27.dp))
        Spacer(Modifier.height(26.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SoraMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HomeMiniCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(SoraSurface, RoundedCornerShape(13.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}
