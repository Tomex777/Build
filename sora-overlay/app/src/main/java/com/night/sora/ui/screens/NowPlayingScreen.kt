package com.night.sora.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.playback.MusicPlaybackController
import com.night.sora.playback.MusicRepeatMode
import com.night.sora.ui.theme.*
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    player: MusicPlaybackController,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onBack: () -> Unit,
) {
    val track = player.currentTrack
    var queueOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (track == null) {
        Column(
            Modifier.fillMaxSize().background(SoraBg).statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.MusicOff, null, tint = SoraMuted, modifier = Modifier.size(48.dp))
            Text("Nothing playing", color = SoraText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = onBack) { Text("Back", color = SoraText) }
        }
        return
    }

    val progress = if (player.durationMs > 0L) {
        (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val saved = isSaved(track)

    Column(
        Modifier.fillMaxSize().background(SoraBg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = SoraText) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM SORA", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = SoraMuted)
                if (player.sourceName.isNotBlank()) {
                    Text(player.sourceName, color = SoraFaint, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { optionsOpen = true }) { Icon(Icons.Rounded.MoreVert, "Track options", tint = SoraText) }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Share track") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            optionsOpen = false
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${track.title} · ${track.subtitle}")
                                }, "Share track"))
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (saved) "Remove from Library" else "Add to Library") },
                        leadingIcon = { Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                        onClick = { optionsOpen = false; onToggleSaved(track) },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(SoraSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(track.artworkUrl, track.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(88.dp), tint = SoraMuted)
            }
            if (player.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = SoraText, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                Text(track.subtitle, color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = { onToggleSaved(track) }) {
                Icon(
                    if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Like",
                    tint = if (saved) SoraAccent else SoraText,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Slider(
            value = progress,
            onValueChange = player::seekToFraction,
            enabled = player.durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = SoraText,
                activeTrackColor = SoraText,
                inactiveTrackColor = SoraSurfaceRaised,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMusicTime(player.positionMs), color = SoraMuted, fontSize = 9.sp)
            Text(formatMusicTime(player.durationMs), color = SoraMuted, fontSize = 9.sp)
        }

        player.errorMessage?.let { message ->
            Surface(
                color = Color(0xFF2A201D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                    Text(message, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = player::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (player.shuffleEnabled) SoraAccent else SoraMuted)
            }
            IconButton(onClick = player::skipPrevious) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = SoraText, modifier = Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = player::togglePlayPause,
                modifier = Modifier.size(66.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoraText, contentColor = Color.Black),
            ) {
                if (player.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Color.Black)
                } else {
                    Icon(
                        if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            IconButton(onClick = player::skipNext) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = SoraText, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = player::cycleRepeatMode) {
                Icon(
                    if (player.repeatMode == MusicRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (player.repeatMode == MusicRepeatMode.OFF) SoraMuted else SoraAccent,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerSecondary(Icons.Rounded.Lyrics, "Lyrics")
            PlayerSecondary(Icons.Rounded.QueueMusic, "Queue", onClick = { queueOpen = true })
            PlayerSecondary(Icons.Rounded.Download, "Download")
        }

        Spacer(Modifier.height(22.dp))
        Surface(color = Color(0xFF26241D), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("LYRICS", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                when {
                    player.lyricsLoading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                        Text("Loading lyrics…", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    !player.lyricsText.isNullOrBlank() -> Text(
                        player.lyricsText.orEmpty(),
                        color = SoraText,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    else -> Text(
                        "Lyrics are not available from this source yet.",
                        color = SoraMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speaker, null, tint = SoraMuted)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("This phone", color = SoraText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (player.streamLabel.isNotBlank()) Text(player.streamLabel, color = SoraFaint, fontSize = 9.sp)
            }
            Text("Output", color = SoraMuted, fontSize = 10.sp)
        }
    }

    if (queueOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false }, containerColor = Color(0xFF161614)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Queue", color = SoraText, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                if (player.queue.isEmpty()) {
                    Text("Nothing queued.", color = SoraMuted, modifier = Modifier.padding(18.dp))
                } else {
                    player.queue.forEachIndexed { index, item ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                player.selectQueueIndex(index)
                                queueOpen = false
                            }.padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(SoraSurfaceHigh)) {
                                if (!item.artworkUrl.isNullOrBlank()) AsyncImage(item.artworkUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, color = SoraText, fontSize = 12.sp, fontWeight = if (index == player.currentIndex) FontWeight.Black else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (index == player.currentIndex) Icon(Icons.Rounded.GraphicEq, "Playing", tint = SoraAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSecondary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = SoraMuted)
        Text(label, color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatMusicTime(durationMillis: Long): String {
    if (durationMillis <= 0L) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
