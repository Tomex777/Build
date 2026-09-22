package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.night.NightMediaCollectionItem
import com.example.whatsapp.data.night.NightMediaCollectionStore

private val MediaLibraryBg = Color(0xFF0B0F11)
private val MediaLibrarySurface = Color(0xFF171C1F)
private val MediaLibraryText = Color(0xFFE7EAEC)
private val MediaLibraryMuted = Color(0xFF9CA5A9)
private val MediaLibraryAccent = Color(0xFFD44368)

@Composable
fun NightMediaLibraryScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }

    val saved =
        remember(revision) {
            NightMediaCollectionStore.library(context)
        }
    val playlistNames =
        remember(revision) {
            NightMediaCollectionStore.playlistNames(context)
        }
    val playlists =
        remember(revision, playlistNames) {
            playlistNames.associateWith {
                NightMediaCollectionStore.playlist(context, it)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MediaLibraryBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MediaLibraryText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Media library",
                    color = MediaLibraryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Anime, manga and music saved by extensions",
                    color = MediaLibraryMuted,
                    fontSize = 11.sp,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Saved media",
                    color = MediaLibraryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }

            if (saved.isEmpty()) {
                item {
                    EmptyMediaCollection(
                        text =
                            "Nothing saved yet. Anime, manga and music extensions can add items here.",
                    )
                }
            } else {
                items(
                    items = saved,
                    key = { "library:" + it.key },
                ) { item ->
                    MediaCollectionRow(
                        item = item,
                        trailingLabel = "Saved",
                        onRemove = {
                            NightMediaCollectionStore.removeFromLibrary(
                                context = context,
                                itemKey = item.key,
                            )
                            revision += 1
                        },
                    )
                }
            }

            playlistNames.forEach { playlistName ->
                item(key = "playlist-header:" + playlistName) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = MediaLibraryAccent,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playlistName,
                            color = MediaLibraryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                val playlistItems = playlists[playlistName].orEmpty()
                if (playlistItems.isEmpty()) {
                    item(key = "playlist-empty:" + playlistName) {
                        EmptyMediaCollection("This playlist is empty.")
                    }
                } else {
                    items(
                        items = playlistItems,
                        key = { "playlist:" + playlistName + ":" + it.key },
                    ) { item ->
                        MediaCollectionRow(
                            item = item,
                            trailingLabel = playlistName,
                            onRemove = {
                                NightMediaCollectionStore.removeFromPlaylist(
                                    context = context,
                                    name = playlistName,
                                    itemKey = item.key,
                                )
                                revision += 1
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCollectionRow(
    item: NightMediaCollectionItem,
    trailingLabel: String,
    onRemove: () -> Unit,
) {
    Surface(
        color = MediaLibrarySurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 13.dp,
                top = 12.dp,
                bottom = 12.dp,
                end = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF242A2D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            when (item.mediaKind.lowercase()) {
                                "manga" -> Icons.Default.MenuBook
                                "music" -> Icons.Default.MusicNote
                                else -> Icons.Default.Movie
                            },
                        contentDescription = null,
                        tint = MediaLibraryAccent,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = MediaLibraryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = MediaLibraryMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text =
                        item.mediaKind.replaceFirstChar { it.uppercase() } +
                            " • " + item.extensionId +
                            " • " + trailingLabel,
                    color = MediaLibraryMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove from " + trailingLabel,
                    tint = MediaLibraryMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyMediaCollection(
    text: String,
) {
    Text(
        text = text,
        color = MediaLibraryMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(
            horizontal = 4.dp,
            vertical = 10.dp,
        ),
    )
}
