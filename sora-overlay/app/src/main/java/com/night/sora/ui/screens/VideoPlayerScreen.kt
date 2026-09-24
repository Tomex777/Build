@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.night.sora.R
import com.night.sora.model.PlaybackSession
import com.night.sora.ui.player.AniyomiPlayerGestureLayer
import com.night.sora.ui.player.AniyomiPlayerView
import com.night.sora.ui.theme.SoraAccent
import com.night.sora.ui.theme.SoraMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Sora's Anime/TV video screen backed by Aniyomi's libmpv stack.
 *
 * Sora owns source/session/progress state. Playback is delegated to the
 * Aniyomi-derived [AniyomiPlayerView].
 */
@Composable
fun VideoPlayerScreen(
    session: PlaybackSession,
    onBack: () -> Unit,
    onProgress: (PlaybackSession, Long, Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val streams = session.streams

    var selectedStream by remember(session) {
        mutableIntStateOf(session.initialStream.coerceIn(0, (streams.size - 1).coerceAtLeast(0)))
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var streamMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var positionMs by remember(session) { mutableLongStateOf(session.initialPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember(session) { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var didStartPlayback by remember(session) { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var controlsLocked by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var player by remember { mutableStateOf<AniyomiPlayerView?>(null) }
    var didInitialSeek by remember(session) { mutableStateOf(session.initialPositionMs <= 0L) }
    var exitRequested by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestExit() {
        if (exitRequested) return
        exitRequested = true
        controlsVisible = false

        val currentPlayer = player
        if (currentPlayer == null) {
            onBack()
            return
        }

        currentPlayer.requestStopForExit()
        scope.launch {
            repeat(30) {
                if (currentPlayer.isIdleForExit()) {
                    currentPlayer.releaseAsync(onReleased = onBack)
                    return@launch
                }
                delay(50)
            }

            // Some mpv builds may not expose idle-active promptly. Release
            // asynchronously anyway; never perform native destroy on the UI
            // thread and never pop this route until the global mpv instance is
            // actually gone.
            currentPlayer.releaseAsync(onReleased = onBack)
        }
    }

    BackHandler(onBack = ::requestExit)

    DisposableEffect(rootView, activity) {
        val previousKeepScreenOn = rootView.keepScreenOn
        rootView.keepScreenOn = true
        val insetsController = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, rootView).also { controller ->
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            rootView.keepScreenOn = previousKeepScreenOn
            player?.destroyPlayer()
            player = null
        }
    }

    LaunchedEffect(player, selectedStream, streams) {
        val currentPlayer = player ?: return@LaunchedEffect
        if (streams.isEmpty()) return@LaunchedEffect
        val stream = streams[selectedStream.coerceIn(0, streams.lastIndex)]
        val resumeAt = if (didInitialSeek) positionMs else session.initialPositionMs
        currentPlayer.play(
            url = stream.url,
            headers = stream.headers,
            positionMs = resumeAt.coerceAtLeast(0L),
        )
        currentPlayer.setSpeed(speed)
        // A newly loaded mpv file can retain the paused state from initialization
        // on Android 16. Resume here and again once duration confirms the file is
        // ready; the one-time guard below keeps this from overriding user pauses.
        currentPlayer.resume()
    }

    LaunchedEffect(player, session) {
        val currentPlayer = player ?: return@LaunchedEffect
        while (isActive) {
            val current = currentPlayer.positionMs().coerceAtLeast(0L)
            val total = currentPlayer.durationMs().coerceAtLeast(0L)
            positionMs = current
            durationMs = total
            isPlaying = !currentPlayer.isPaused()
            isBuffering = currentPlayer.isBuffering()

            if (!didStartPlayback && total > 0L) {
                if (currentPlayer.isPaused()) currentPlayer.resume()
                didStartPlayback = true
            }

            if (!didInitialSeek && total > 0L && session.initialPositionMs > 0L) {
                currentPlayer.seekTo(session.initialPositionMs)
                didInitialSeek = true
            }
            delay(250)
        }
    }

    LaunchedEffect(player, session) {
        val currentPlayer = player ?: return@LaunchedEffect
        while (isActive) {
            delay(1_000)
            val current = currentPlayer.positionMs().coerceAtLeast(0L)
            val total = currentPlayer.durationMs().coerceAtLeast(0L)
            if (current > 0L && total > 0L) onProgress(session, current, total)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, streamMenuOpen, speedMenuOpen) {
        if (controlsVisible && isPlaying && !streamMenuOpen && !speedMenuOpen) {
            delay(3_200)
            controlsVisible = false
        }
    }

    fun seekBy(deltaMs: Long) {
        player?.seekBy(deltaMs)
        controlsVisible = true
    }

    fun togglePlayback() {
        player?.togglePause()
        controlsVisible = true
    }

    if (streams.isEmpty()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(session.episodeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = ::requestExit) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This source returned no playable streams.", color = SoraMuted)
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(
                    R.layout.sora_aniyomi_player_view,
                    null,
                    false,
                ) as AniyomiPlayerView).also { view ->
                    view.initialize()
                    player = view
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AniyomiPlayerGestureLayer(
            player = player,
            durationMs = durationMs,
            positionMs = positionMs,
            locked = controlsLocked,
            playbackSpeed = speed,
            onToggleControls = { controlsVisible = !controlsVisible },
            onSeekPreview = { seekPreviewMs = it },
        )

        seekPreviewMs?.let { preview ->
            Surface(
                color = Color.Black.copy(alpha = .72f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    formatPlayerTime(preview),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = SoraAccent,
                modifier = Modifier.align(Alignment.Center).size(42.dp),
            )
        }

        if (controlsVisible) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .28f)))

            if (controlsLocked) {
                FilledIconButton(
                    onClick = {
                        controlsLocked = false
                        controlsVisible = true
                    },
                    modifier = Modifier.align(Alignment.Center),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = .62f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.LockOpen, "Unlock controls")
                }
            } else {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::requestExit) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        session.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        session.episodeTitle,
                        color = Color.White.copy(alpha = .68f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    session.sourceName,
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                IconButton(onClick = {
                    controlsLocked = true
                    controlsVisible = true
                }) {
                    Icon(Icons.Rounded.Lock, "Lock controls", tint = Color.White)
                }
            }

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = { seekBy(-10_000L) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = .50f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Replay10, "Back 10 seconds")
                }
                FilledIconButton(
                    onClick = ::togglePlayback,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = .94f),
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(
                    onClick = { seekBy(10_000L) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = .50f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Forward10, "Forward 10 seconds")
                }
            }

            Surface(
                color = Color.Black.copy(alpha = .72f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    val progress = if (durationMs > 0L) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Slider(
                        value = progress,
                        onValueChange = { fraction ->
                            if (durationMs > 0L) {
                                player?.seekTo((durationMs * fraction.coerceIn(0f, 1f)).roundToLong())
                            }
                            controlsVisible = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = SoraAccent,
                            activeTrackColor = SoraAccent,
                            inactiveTrackColor = Color.White.copy(alpha = .28f),
                        ),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.weight(1f))

                        Box {
                            TextButton(
                                onClick = {
                                    speedMenuOpen = true
                                    controlsVisible = true
                                },
                            ) {
                                Text("${trimSpeed(speed)}×", color = Color.White, fontSize = 11.sp)
                            }
                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                            ) {
                                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text("${trimSpeed(value)}×") },
                                        trailingIcon = {
                                            if (speed == value) {
                                                Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                                            }
                                        },
                                        onClick = {
                                            speed = value
                                            player?.setSpeed(value)
                                            speedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            TextButton(
                                onClick = {
                                    streamMenuOpen = true
                                    controlsVisible = true
                                },
                            ) {
                                Text(
                                    streams[selectedStream].label.ifBlank { "Source" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                            DropdownMenu(
                                expanded = streamMenuOpen,
                                onDismissRequest = { streamMenuOpen = false },
                            ) {
                                streams.forEachIndexed { index, stream ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(stream.label.ifBlank { "Stream ${index + 1}" })
                                        },
                                        trailingIcon = {
                                            if (selectedStream == index) {
                                                Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                                            }
                                        },
                                        onClick = {
                                            positionMs = player?.positionMs() ?: positionMs
                                            didInitialSeek = true
                                            selectedStream = index
                                            streamMenuOpen = false
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
}

private fun formatPlayerTime(valueMs: Long): String {
    if (valueMs <= 0L) return "0:00"
    val totalSeconds = valueMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun trimSpeed(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
