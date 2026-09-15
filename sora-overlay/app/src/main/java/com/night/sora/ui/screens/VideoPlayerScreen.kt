@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.night.sora.model.PlaybackSession
import com.night.sora.model.PlaybackStream
import com.night.sora.ui.theme.SoraAccent
import com.night.sora.ui.theme.SoraMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

@Composable
fun VideoPlayerScreen(
    session: PlaybackSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val streams = session.streams
    var selectedStream by remember { mutableIntStateOf(session.initialStream.coerceIn(0, (streams.size - 1).coerceAtLeast(0))) }
    var controlsVisible by remember { mutableStateOf(true) }
    var streamMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPercent by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }

    BackHandler(onBack = onBack)

    DisposableEffect(view, player) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            player.release()
        }
    }

    LaunchedEffect(selectedStream, streams) {
        if (streams.isEmpty()) return@LaunchedEffect
        val stream = streams[selectedStream.coerceIn(0, streams.lastIndex)]
        playbackError = null
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .apply { stream.mimeType?.takeIf(String::isNotBlank)?.let(::setMimeType) }
            .build()
        val resumeAt = positionMs.coerceAtLeast(0L)
        player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
        player.prepare()
        if (resumeAt > 0L) player.seekTo(resumeAt)
        player.playWhenReady = true
        player.setPlaybackSpeed(speed)
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            bufferedPercent = player.bufferedPercentage.coerceIn(0, 100)
            isPlaying = player.isPlaying
            playbackState = player.playbackState
            playbackError = player.playerError?.message ?: playbackError
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !streamMenuOpen && !speedMenuOpen) {
            delay(3_200)
            controlsVisible = false
        }
    }

    fun seekBy(deltaMs: Long) {
        val end = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, end))
        controlsVisible = true
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        controlsVisible = true
    }

    if (streams.isEmpty()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(session.episodeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
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
        Modifier.fillMaxSize().background(Color.Black).pointerInput(player) {
            detectTapGestures(
                onTap = { controlsVisible = !controlsVisible },
                onDoubleTap = { offset ->
                    if (offset.x < size.width / 2f) seekBy(-10_000L) else seekBy(10_000L)
                },
            )
        },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                color = SoraAccent,
                modifier = Modifier.align(Alignment.Center).size(42.dp),
            )
        }

        playbackError?.let { message ->
            Surface(
                color = Color.Black.copy(alpha = .82f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Playback error", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(message, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { playbackError = null; player.prepare(); player.play() }) { Text("Retry") }
                }
            }
        }

        if (controlsVisible) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .28f)))

            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(session.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(session.episodeTitle, color = Color.White.copy(alpha = .68f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(session.sourceName, color = Color.White.copy(alpha = .68f), fontSize = 10.sp, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
            }

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = { seekBy(-10_000L) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .50f), contentColor = Color.White),
                ) { Icon(Icons.Rounded.Replay10, "Back 10 seconds") }
                FilledIconButton(
                    onClick = ::togglePlayback,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .94f), contentColor = Color.Black),
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { seekBy(10_000L) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .50f), contentColor = Color.White),
                ) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds") }
            }

            Surface(
                color = Color.Black.copy(alpha = .72f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progress,
                        onValueChange = { fraction ->
                            if (durationMs > 0L) player.seekTo((durationMs * fraction.coerceIn(0f, 1f)).roundToLong())
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
                        Text("${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}", color = Color.White, fontSize = 11.sp)
                        Text("  ·  $bufferedPercent% buffered", color = SoraMuted, fontSize = 9.sp)
                        Spacer(Modifier.weight(1f))

                        Box {
                            TextButton(onClick = { speedMenuOpen = true; controlsVisible = true }) {
                                Text("${trimSpeed(speed)}×", color = Color.White, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text("${trimSpeed(value)}×") },
                                        trailingIcon = { if (speed == value) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                                        onClick = {
                                            speed = value
                                            player.setPlaybackSpeed(value)
                                            speedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            TextButton(onClick = { streamMenuOpen = true; controlsVisible = true }) {
                                Text(streams[selectedStream].label.ifBlank { "Source" }, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                            DropdownMenu(expanded = streamMenuOpen, onDismissRequest = { streamMenuOpen = false }) {
                                streams.forEachIndexed { index, stream ->
                                    DropdownMenuItem(
                                        text = { Text(stream.label.ifBlank { "Stream ${index + 1}" }) },
                                        trailingIcon = { if (selectedStream == index) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                                        onClick = {
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

private fun formatPlayerTime(valueMs: Long): String {
    if (valueMs <= 0L) return "0:00"
    val totalSeconds = valueMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun trimSpeed(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
