#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "later")
media_dir = root / "app/src/main/java/com/night/later/ui/media"
viewer = media_dir / "LaterMediaViewer.kt"
build_file = root / "app/build.gradle.kts"
settings = root / "app/src/main/java/com/night/later/ui/settings/SettingsScreen.kt"
if not viewer.exists() or not build_file.exists():
    raise SystemExit("Later media viewer/build file not found")

build = build_file.read_text()
dependency = 'implementation("org.videolan.android:libvlc-all:3.7.6")'
if dependency not in build:
    marker = "dependencies {"
    if marker not in build:
        raise SystemExit("dependencies block not found")
    build = build.replace(marker, marker + "\n    " + dependency, 1)
build_file.write_text(build)

text = viewer.read_text()
for imp in (
    "import androidx.media3.common.MediaItem\n",
    "import androidx.media3.common.PlaybackParameters\n",
    "import androidx.media3.common.Player\n",
    "import androidx.media3.exoplayer.ExoPlayer\n",
    "import androidx.media3.ui.PlayerView\n",
):
    text = text.replace(imp, "")

start = text.find("@Composable\nfun LaterVideoPlayer(")
end = text.find("\nprivate fun shareVideoSource", start)
if start < 0 or end < 0:
    raise SystemExit("Later video player section not found")

replacement = r'''@Composable
fun LaterVideoPlayer(
    source: String,
    displayName: String?,
    modifier: Modifier = Modifier,
    mimeType: String? = null
) {
    val playback = rememberLaterVlcPlayback(source)
    var fullscreen by remember(source) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!fullscreen) {
            LaterVideoPlayerSurface(
                playback = playback,
                displayName = displayName,
                modifier = Modifier.fillMaxSize(),
                onFullscreen = { fullscreen = true }
            )
        }
    }

    if (fullscreen) {
        FullscreenVideoDialog(
            playback = playback,
            displayName = displayName,
            onDismiss = { fullscreen = false }
        )
    }
}

/** Fullscreen route used when an editor thumbnail is tapped. */
@Composable
fun LaterFullscreenVideoViewer(
    source: String,
    displayName: String?,
    mimeType: String? = null,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    versionActionLabel: String? = null,
    onSwitchVersion: (() -> Unit)? = null
) {
    val playback = rememberLaterVlcPlayback(source)
    val context = LocalContext.current
    FullscreenVideoDialog(
        playback = playback,
        displayName = displayName,
        onDismiss = onDismiss,
        onEdit = onEdit,
        onShare = onShare ?: { shareVideoSource(context, source, mimeType) },
        versionActionLabel = versionActionLabel,
        onSwitchVersion = onSwitchVersion
    )
}

@Composable
private fun FullscreenVideoDialog(
    playback: LaterVlcPlayback,
    displayName: String?,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    versionActionLabel: String? = null,
    onSwitchVersion: (() -> Unit)? = null
) {
    var playbackSpeed by remember(playback) { mutableFloatStateOf(1f) }
    var muted by remember(playback) { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LaterVideoPlayerSurface(
                playback = playback,
                displayName = displayName,
                modifier = Modifier.fillMaxSize(),
                onFullscreen = null
            )

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.50f)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit fullscreen", tint = Color.White)
                    }
                }
                onShare?.let { share ->
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.50f)) {
                        IconButton(onClick = share) { Icon(Icons.Rounded.Share, contentDescription = "Share video", tint = Color.White) }
                    }
                }
                onEdit?.let { edit ->
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.62f)) {
                        TextButtonCompat(label = "Edit", enabled = true, onClick = edit)
                    }
                }
            }
            if (versionActionLabel != null && onSwitchVersion != null) {
                Surface(modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 68.dp), shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.62f)) {
                    TextButtonCompat(label = versionActionLabel, enabled = true, onClick = onSwitchVersion)
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    TextButtonCompat(
                        label = playbackSpeed.toString() + "×",
                        enabled = true,
                        onClick = {
                            val speeds = listOf(0.5f, 1f, 1.5f, 2f)
                            val next = speeds[(speeds.indexOf(playbackSpeed).coerceAtLeast(0) + 1) % speeds.size]
                            playbackSpeed = next
                            playback.player.setRate(next)
                        }
                    )
                }
                Surface(shape = RoundedCornerShape(22.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    TextButtonCompat(
                        label = if (muted) "Unmute" else "Mute",
                        enabled = true,
                        onClick = {
                            muted = !muted
                            playback.player.setVolume(if (muted) 0 else 100)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberLaterVlcPlayback(source: String): LaterVlcPlayback {
    val context = LocalContext.current.applicationContext
    val playback = remember(source) { LaterVlcPlayback(context, source) }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
    return playback
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun LaterVideoPlayerSurface(
    playback: LaterVlcPlayback,
    displayName: String?,
    modifier: Modifier,
    onFullscreen: (() -> Unit)?
) {
    LaterVlcStyleVideoSurface(
        playback = playback,
        modifier = modifier,
        onFullscreen = onFullscreen
    )
}
'''

text = text[:start] + replacement + text[end:]
viewer.write_text(text)

surface = media_dir / "LaterVlcStyleVideoSurface.kt"
surface.write_text(r'''package com.night.later.ui.media

import android.content.Context
import android.net.Uri
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
import java.io.File
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

internal class LaterVlcPlayback(
    context: Context,
    source: String
) {
    private val libVlc =
        LibVLC(
            context,
            arrayListOf(
                "--audio-time-stretch",
                "--no-video-title-show"
            )
        )

    val player: MediaPlayer = MediaPlayer(libVlc)

    init {
        val media = Media(libVlc, vlcMediaUri(source))
        media.setHWDecoderEnabled(true, false)
        player.media = media
        media.release()
    }

    fun release() {
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }
}

/**
 * libVLC owns video decoding/rendering. Later owns the chrome so playback feels
 * native to the app without exposing VLC branding or a generic Material slider.
 */
@Composable
internal fun LaterVlcStyleVideoSurface(
    playback: LaterVlcPlayback,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null
) {
    val player = playback.player
    var positionMs by remember(playback) { mutableLongStateOf(0L) }
    var durationMs by remember(playback) { mutableLongStateOf(0L) }
    var isPlaying by remember(playback) { mutableStateOf(player.isPlaying) }
    var controlsVisible by remember(playback) { mutableStateOf(true) }

    LaunchedEffect(playback) {
        while (true) {
            positionMs = player.time.coerceAtLeast(0L)
            durationMs = player.length.coerceAtLeast(0L)
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
                VLCVideoLayout(viewContext).also { layout ->
                    runCatching { player.detachViews() }
                    player.attachViews(layout, null, true, false)
                }
            },
            update = { layout ->
                if (!player.vlcVout.areViewsAttached()) {
                    player.attachViews(layout, null, true, false)
                }
            },
            onRelease = {
                runCatching { player.detachViews() }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(playback) {
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
                            player.setTime((player.time - 10_000L).coerceAtLeast(0L))
                            controlsVisible = true
                        }
                    )
                    IconButton(
                        onClick = {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                val length = player.length.coerceAtLeast(0L)
                                if (length > 0L && player.time >= length - 250L) {
                                    player.setTime(0L)
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
                            val length = player.length.coerceAtLeast(0L)
                            val target = player.time.coerceAtLeast(0L) + 10_000L
                            player.setTime(if (length > 0L) target.coerceAtMost(length) else target)
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
                    durationMs = durationMs,
                    onSeek = { target ->
                        player.setTime(target)
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

private fun vlcMediaUri(source: String): Uri {
    val parsed = Uri.parse(source)
    return if (!parsed.scheme.isNullOrBlank()) parsed else Uri.fromFile(File(source))
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

if settings.exists():
    s = settings.read_text()
    s = s.replace(
        '"Version 2.2.4"',
        '"Version 2.2.4 · LibVLC playback"',
        1
    )
    settings.write_text(s)
