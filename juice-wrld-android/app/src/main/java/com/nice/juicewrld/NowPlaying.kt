package com.nice.juicewrld

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val NightPlayer = Color(0xFF0B0A11)
private val LightPlayer = Color(0xFFF8F4FF)
private val AccentPlayer = Color(0xFFB997F6)
private val MutedPlayer = Color(0xFFB2AABF)

private fun elapsedTime(ms: Int): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * Single full-screen Now Playing surface. Does not own MediaPlayer: navigating
 * back keeps listening uninterrupted. Media playback state is sourced from the
 * existing shared player (not a simulated progress timer).
 */
@Composable
fun NowPlayingScreen(
    song: FanSong,
    state: FanPlayerState,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF261D38), NightPlayer, NightPlayer))
            )
        ) {
            val compact = maxHeight < 660.dp
            Column(
                Modifier.fillMaxSize().navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 23.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ArrowBack, "Back to songs", tint = LightPlayer)
                    }
                    Text(
                        "NOW PLAYING",
                        Modifier.weight(1f),
                        color = MutedPlayer, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp, textAlign = TextAlign.Center
                    )
                    IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (song.id in state.favorites) Icons.Filled.Favorite
                            else Icons.Filled.FavoriteBorder,
                            if (song.id in state.favorites) "Remove favorite" else "Add favorite",
                            tint = if (song.id in state.favorites) Color(0xFFFA9BBB) else LightPlayer
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
                AlbumCover(
                    song.album,
                    Modifier.size(if (compact) 190.dp else 294.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    song.title
                )
                Spacer(Modifier.height(if (compact) 18.dp else 32.dp))
                Text(
                    song.title, fontWeight = FontWeight.Black, fontSize = 26.sp,
                    letterSpacing = (-0.7).sp, color = LightPlayer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    song.album, fontSize = 13.sp, color = MutedPlayer,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(if (compact) 16.dp else 29.dp))
                var dragging by remember(song.id) { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragging ?: if (state.durationMs > 0) {
                        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    } else 0f,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let(onSeek)
                        dragging = null
                    },
                    enabled = state.durationMs > 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPlayer, activeTrackColor = AccentPlayer,
                        inactiveTrackColor = Color(0xFF51455E)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(elapsedTime(state.positionMs), color = MutedPlayer, fontSize = 12.sp)
                    Text(elapsedTime(state.durationMs), color = MutedPlayer, fontSize = 12.sp)
                }
                Spacer(Modifier.height(if (compact) 12.dp else 25.dp))
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Shuffle, contentDescription = "Shuffle",
                            tint = if (state.shuffle) AccentPlayer else MutedPlayer
                        )
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous song",
                            Modifier.size(33.dp), tint = LightPlayer)
                    }
                    IconButton(
                        onClick = onTogglePlay,
                        enabled = !state.isPreparing,
                        modifier = Modifier.size(74.dp).clip(CircleShape)
                            .background(AccentPlayer)
                    ) {
                        if (state.isPreparing) {
                            CircularProgressIndicator(
                                Modifier.size(25.dp), strokeWidth = 2.dp, color = NightPlayer
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                Modifier.size(37.dp), tint = NightPlayer
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Filled.SkipNext, "Next song",
                            Modifier.size(33.dp), tint = LightPlayer)
                    }
                    IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "Repeat: ${state.repeat.label}",
                            tint = if (state.repeat == RepeatMode.OFF) MutedPlayer else AccentPlayer
                        )
                    }
                }
                Spacer(Modifier.height(17.dp))
                Text(
                    "Juice WRLD · ${song.year}",
                    color = MutedPlayer.copy(alpha = 0.78f), fontSize = 11.sp
                )
            }
        }
    }
}
