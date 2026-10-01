#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "later")
media_dir = root / "app/src/main/java/com/night/later/ui/media"
viewer = media_dir / "LaterMediaViewer.kt"
if not viewer.exists():
    raise SystemExit("LaterMediaViewer.kt not found")

text = viewer.read_text()

old_comment = """/**
 * Inline Media3 player with one player instance that moves into the fullscreen
 * surface instead of restarting playback.
 */"""
new_comment = """/**
 * Later video playback keeps one player instance while presenting custom,
 * VLC-style controls instead of the stock Media3 controller.
 */"""
if old_comment in text:
    text = text.replace(old_comment, new_comment, 1)

start_marker = "@Composable\nprivate fun LaterVideoPlayerSurface("
end_marker = "\nprivate fun shareVideoSource"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("LaterVideoPlayerSurface block not found")

replacement = """@Suppress("UNUSED_PARAMETER")
@Composable
private fun LaterVideoPlayerSurface(
    player: ExoPlayer,
    displayName: String?,
    modifier: Modifier,
    onFullscreen: (() -> Unit)?
) {
    LaterVlcStyleVideoSurface(
        player = player,
        modifier = modifier,
        onFullscreen = onFullscreen
    )
}
"""

text = text[:start] + replacement + text[end:]
for unused_import in (
    "import androidx.compose.material.icons.rounded.Fullscreen\n",
    "import androidx.compose.ui.viewinterop.AndroidView\n",
    "import androidx.media3.ui.PlayerView\n",
):
    text = text.replace(unused_import, "")

viewer.write_text(text)

surface = media_dir / "LaterVlcStyleVideoSurface.kt"
surface.write_text(r'''package com.night.later.ui.media

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Video rendering stays on the proven Media3 pipeline, while playback controls
 * are owned by Later. The seek bar is custom drawn so the viewer never exposes
 * a generic Material slider or the stock ExoPlayer controller.
 */
@Composable
internal fun LaterVlcStyleVideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null
) {
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var bufferedMs by remember(player) { mutableLongStateOf(0L) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var controlsVisible by remember(player) { mutableStateOf(true) }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs =
                player.duration
                    .takeIf { it != C.TIME_UNSET && it > 0L }
                    ?: 0L
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(160)
        }
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .semantics { contentDescription = "Later video player" }
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(player) {
                        detectTapGestures {
                            controlsVisible = !controlsVisible
                        }
                    }
        )

        if (controlsVisible) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VlcTextControl(
                        label = "−10",
                        description = "Replay 10 seconds",
                        onClick = {
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            controlsVisible = true
                        }
                    )
                    IconButton(
                        onClick = {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0L)
                                }
                                player.play()
                            }
                            controlsVisible = true
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    VlcTextControl(
                        label = "+10",
                        description = "Forward 10 seconds",
                        onClick = {
                            val upper =
                                durationMs.takeIf { it > 0L }
                                    ?: (player.currentPosition + 10_000L)
                            player.seekTo(
                                (player.currentPosition + 10_000L)
                                    .coerceAtMost(upper)
                                    .coerceAtLeast(0L)
                            )
                            controlsVisible = true
                        }
                    )
                    if (onFullscreen != null) {
                        IconButton(
                            onClick = onFullscreen,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }

                LaterVideoSeekBar(
                    positionMs = positionMs,
                    bufferedMs = bufferedMs,
                    durationMs = durationMs,
                    onSeek = { target ->
                        player.seekTo(target)
                        positionMs = target
                        controlsVisible = true
                    }
                )

                Text(
                    text = formatVideoClock(positionMs) + " / " + formatVideoClock(durationMs),
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun VlcTextControl(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .size(42.dp)
                .semantics { contentDescription = description }
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.46f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LaterVideoSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var dragFraction by remember(durationMs) { mutableFloatStateOf(-1f) }
    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction =
        if (dragFraction >= 0f) {
            dragFraction
        } else {
            (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    val bufferedFraction =
        (bufferedMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .semantics { contentDescription = "Video seek bar" }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            val fraction =
                                (offset.x / size.width.toFloat())
                                    .coerceIn(0f, 1f)
                            onSeek((durationMs * fraction).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                dragFraction =
                                    (offset.x / size.width.toFloat())
                                        .coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (dragFraction >= 0f) {
                                onSeek((durationMs * dragFraction).toLong())
                            }
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f },
                        onDrag = { change, _ ->
                            if (size.width > 0) {
                                dragFraction =
                                    (change.position.x / size.width.toFloat())
                                        .coerceIn(0f, 1f)
                            }
                            change.consume()
                        }
                    )
                }
    ) {
        val y = size.height / 2f
        val trackStart = Offset(0f, y)
        val trackEnd = Offset(size.width, y)
        drawLine(
            color = Color.White.copy(alpha = 0.24f),
            start = trackStart,
            end = trackEnd,
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = trackStart,
            end = Offset(size.width * bufferedFraction, y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = accent,
            start = trackStart,
            end = Offset(size.width * playedFraction, y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accent,
            radius = 6.dp.toPx(),
            center = Offset(size.width * playedFraction, y)
        )
    }
}

private fun formatVideoClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
''')
