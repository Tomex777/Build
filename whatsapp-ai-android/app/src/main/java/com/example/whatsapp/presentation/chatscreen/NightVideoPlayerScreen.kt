package com.example.whatsapp.presentation.chatscreen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun NightVideoPlayerScreen(
    localPath: String,
    title: String = "Video",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val libVlc = remember(localPath) {
        LibVLC(
            context.applicationContext,
            arrayListOf(
                "--no-video-title-show",
                "--no-snapshot-preview",
            ),
        )
    }
    val player = remember(localPath) { MediaPlayer(libVlc) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var draggingPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var qualityOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var audioOpen by remember { mutableStateOf(false) }
    var subtitlesOpen by remember { mutableStateOf(false) }
    var fitOpen by remember { mutableStateOf(false) }
    var fillVideo by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            durationMs = runCatching { player.length.coerceAtLeast(0L) }.getOrDefault(0L)
            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(0L)

            if (audioTracks.isEmpty()) {
                audioTracks = runCatching {
                    player.audioTracks
                        ?.map { it.id to (it.name ?: "Audio") }
                        .orEmpty()
                }.getOrDefault(emptyList())
            }
            if (subtitleTracks.isEmpty()) {
                subtitleTracks = runCatching {
                    player.spuTracks
                        ?.map { it.id to (it.name ?: "Subtitle") }
                        .orEmpty()
                }.getOrDefault(emptyList())
            }

            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(3200L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                controlsVisible = if (isLocked) true else !controlsVisible
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                VLCVideoLayout(viewContext).also { layout ->
                    player.attachViews(layout, null, true, false)
                    player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                    val media = Media(libVlc, Uri.fromFile(File(localPath)))
                    player.media = media
                    media.release()
                    player.play()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
            ) {
                if (isLocked) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 18.dp),
                    ) {
                        IconButton(onClick = { isLocked = false }) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Unlock controls",
                                tint = Color.White,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )

                        Box {
                            IconButton(onClick = { subtitlesOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = subtitlesOpen,
                                onDismissRequest = { subtitlesOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        runCatching { player.setSpuTrack(-1) }
                                        subtitlesOpen = false
                                    },
                                )
                                subtitleTracks.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            runCatching { player.setSpuTrack(id) }
                                            subtitlesOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { audioOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Audio track",
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = audioOpen,
                                onDismissRequest = { audioOpen = false },
                            ) {
                                audioTracks.ifEmpty { listOf(-1 to "Default") }.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            if (id >= 0) runCatching { player.setAudioTrack(id) }
                                            audioOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            TextButton(onClick = { qualityOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Auto", color = Color.White, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = qualityOpen,
                                onDismissRequest = { qualityOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto · source quality") },
                                    onClick = { qualityOpen = false },
                                )
                            }
                        }

                        IconButton(onClick = { controlsVisible = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                val target = (player.time - 10_000L).coerceAtLeast(0L)
                                player.setTime(target)
                            },
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Back 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = CircleShape,
                        ) {
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                    controlsVisible = true
                                },
                                modifier = Modifier.size(68.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val length = player.length.coerceAtLeast(0L)
                                val target = (player.time + 10_000L)
                                    .coerceAtMost(if (length > 0L) length else Long.MAX_VALUE)
                                player.setTime(target)
                            },
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val max = durationMs.coerceAtLeast(1L).toFloat()
                        val sliderValue = if (isDragging) {
                            draggingPosition.coerceIn(0f, max)
                        } else {
                            positionMs.toFloat().coerceIn(0f, max)
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                isDragging = true
                                draggingPosition = it
                            },
                            onValueChangeFinished = {
                                player.setTime(draggingPosition.toLong())
                                isDragging = false
                            },
                            valueRange = 0f..max,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatPlayerTime(if (isDragging) draggingPosition.toLong() else positionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = " / " + formatPlayerTime(durationMs),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(onClick = { isLocked = true }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock controls",
                                    tint = Color.White,
                                )
                            }

                            IconButton(
                                onClick = {
                                    val landscape =
                                        activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    activity?.requestedOrientation =
                                        if (landscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Rotate",
                                    tint = Color.White,
                                )
                            }

                            Box {
                                IconButton(onClick = { speedOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Playback speed",
                                        tint = Color.White,
                                    )
                                }
                                DropdownMenu(
                                    expanded = speedOpen,
                                    onDismissRequest = { speedOpen = false },
                                ) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                        DropdownMenuItem(
                                            text = { Text("${trimRate(rate)}×") },
                                            onClick = {
                                                speed = rate
                                                player.setRate(rate)
                                                speedOpen = false
                                            },
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(onClick = { fitOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.FitScreen,
                                        contentDescription = "View mode",
                                        tint = Color.White,
                                    )
                                }
                                DropdownMenu(
                                    expanded = fitOpen,
                                    onDismissRequest = { fitOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Fit") },
                                        onClick = {
                                            fillVideo = false
                                            player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                                            fitOpen = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fill") },
                                        onClick = {
                                            fillVideo = true
                                            player.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
                                            fitOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun trimRate(rate: Float): String =
    if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()
