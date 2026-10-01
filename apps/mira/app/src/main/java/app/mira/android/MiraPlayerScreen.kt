@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.mira.android

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mira.domain.ResolvedMedia

@Composable
internal fun MiraPlayerScreen(
    title: String,
    media: ResolvedMedia,
    identity: MiraPlaybackIdentity,
    watchProgressStore: MiraWatchProgressStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { MiraVlcPlayer(context) }
    val state by player.state.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = remember(context) { context.findMiraActivity() }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var sheet by remember { mutableStateOf<String?>(null) }
    var resumeAfterBackground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    resumeAfterBackground = player.state.value.isPlaying
                    player.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (resumeAfterBackground) {
                    resumeAfterBackground = false
                    player.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(activity, landscape) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (landscape) controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    fun saveProgress(
        positionMs: Long = player.state.value.positionMs,
        durationMs: Long = player.state.value.durationMs,
    ) {
        watchProgressStore.save(
            identity = identity,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    BackHandler {
        saveProgress()
        onBack()
    }

    DisposableEffect(player, identity.stableKey) {
        onDispose {
            saveProgress()
            player.release()
        }
    }
    LaunchedEffect(media.url, identity.stableKey) {
        val saved = watchProgressStore.get(identity)
        val startPosition = saved
            ?.takeUnless { it.completed }
            ?.positionMs
            ?: 0L
        player.play(media, startPosition)
    }
    LaunchedEffect(state.positionMs / 5_000L) {
        if (state.positionMs > 0L) saveProgress()
    }
    LaunchedEffect(state.ended) {
        if (state.ended && state.durationMs > 0L) {
            saveProgress(state.durationMs, state.durationMs)
        }
    }

    sheet?.let { selected ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(selected, style = MaterialTheme.typography.titleMedium)
                when (selected) {
                    "Speed" -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                        TextButton(onClick = { player.setRate(rate); sheet = null }) { Text("${rate}×") }
                    }
                    "Audio" -> state.audioTracks.forEach { track ->
                        TextButton(onClick = { player.selectAudioTrack(track.id); sheet = null }) { Text(track.name) }
                    }
                    "Subtitles" -> {
                        TextButton(onClick = { player.selectSubtitleTrack(-1); sheet = null }) { Text("Off") }
                        state.subtitleTracks.forEach { track ->
                            TextButton(onClick = { player.selectSubtitleTrack(track.id); sheet = null }) { Text(track.name) }
                        }
                        media.subtitles.forEach { track ->
                            TextButton(onClick = { player.addExternalSubtitle(track.url); sheet = null }) { Text(track.language ?: "External subtitle") }
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                title,
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it).also(player::attach) },
                onRelease = { player.detach(it) },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.isBuffering) CircularProgressIndicator()
            state.error?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { player.play(media, state.positionMs) }) { Text("Retry") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall)
            MiraVlcSeekBar(
                value = if (state.durationMs > 0L) state.positionMs.toFloat() / state.durationMs else 0f,
                enabled = state.seekable && state.durationMs > 0L,
                onSeekFraction = { player.seekTo((state.durationMs * it).toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekBy(-10_000L) }, enabled = state.seekable) {
                Icon(Icons.Filled.FastRewind, "Seek back 10 seconds")
            }
            IconButton(onClick = player::togglePlayPause) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = { player.seekBy(10_000L) }, enabled = state.seekable) {
                Icon(Icons.Filled.FastForward, "Seek forward 10 seconds")
            }
            IconButton(onClick = { sheet = "Subtitles" }) { Icon(Icons.Outlined.Subtitles, "Subtitles") }
            IconButton(onClick = { sheet = "Audio" }) { Icon(Icons.Outlined.Audiotrack, "Audio tracks") }
            IconButton(onClick = { sheet = "Speed" }) { Icon(Icons.Outlined.Speed, "Playback speed") }
            IconButton(onClick = {
                activity?.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }) { Icon(Icons.Outlined.Fullscreen, "Fullscreen") }
        }
    }
}
@Composable
private fun MiraVlcSeekBar(
    value: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = value.coerceIn(0f, 1f)
    val trackColor = Color.White.copy(alpha = if (enabled) 0.42f else 0.24f)
    val progressColor = MaterialTheme.colorScheme.primary
    val thumbColor = Color.White

    Canvas(
        modifier = modifier
            .height(28.dp)
            .testTag("vlc-seek-bar")
            .semantics {
                contentDescription = "Playback position"
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(onSeekFraction) {
                            detectTapGestures { offset ->
                                if (size.width > 0) {
                                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                                }
                            }
                        }
                        .pointerInput(onSeekFraction) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0) {
                                        onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0) {
                                        onSeekFraction(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                        )
                                    }
                                    change.consume()
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * progress
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (progressX > 0f) {
            drawLine(
                color = progressColor,
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(progressX, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = thumbColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(progressX, centerY),
        )
    }
}

private fun android.content.Context.findMiraActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findMiraActivity()
    else -> null
}
private fun formatTime(value: Long): String {
    val seconds = value.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
