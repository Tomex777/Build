package com.tomex777.annie

import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class AnniePlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank) ?: "Video"
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        val mode = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { PlayerMode.valueOf(it) }.getOrNull() } ?: PlayerMode.STREAMING
        val item = CatalogItem(
            id = intent.getIntExtra(EXTRA_ID, 0),
            mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE).orEmpty(),
            title = title,
            image = intent.getStringExtra(EXTRA_IMAGE).orEmpty(),
            year = intent.getIntExtra(EXTRA_YEAR, -1).takeIf { it >= 0 },
            status = "",
            episodes = null,
            chapters = null,
        )
        setContent {
            AnnieTheme {
                MediaPlayerScreen(
                    item = item,
                    mode = mode,
                    sourceAvailable = mediaUri != null,
                    mediaUri = mediaUri,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "annie.player.id"
        const val EXTRA_MEDIA_TYPE = "annie.player.media_type"
        const val EXTRA_TITLE = "annie.player.title"
        const val EXTRA_IMAGE = "annie.player.image"
        const val EXTRA_YEAR = "annie.player.year"
        const val EXTRA_MEDIA_URI = "annie.player.media_uri"
        const val EXTRA_MODE = "annie.player.mode"
    }
}

internal enum class PlayerMode { STREAMING, OFFLINE }

@Composable
internal fun MediaPlayerScreen(
    item: CatalogItem,
    mode: PlayerMode,
    sourceAvailable: Boolean,
    mediaUri: Uri? = null,
    onBack: () -> Unit,
    immersive: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    BackHandler(onBack = onBack)
    DisposableEffect(activity, view, immersive) {
        val controller = if (immersive) activity?.window?.let { WindowCompat.getInsetsController(it, view) } else null
        if (immersive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (immersive) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }
    val isOffline = mode == PlayerMode.OFFLINE
    val playable = sourceAvailable

    LaunchedEffect(mediaUri, videoView) {
        while (mediaUri != null && videoView != null) {
            val player = videoView
            if (player != null) {
                durationMs = player.duration.coerceAtLeast(0)
                positionMs = player.currentPosition.coerceAtLeast(0)
                playing = player.isPlaying
                progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            }
            delay(400)
        }
    }
    val activeVideoView = videoView
    DisposableEffect(activeVideoView) {
        onDispose { activeVideoView?.stopPlayback() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF030811)).testTag("media_player")) {
        if (mediaUri != null) {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).also { video ->
                        videoView = video
                        loadedUri = mediaUri
                        video.setOnPreparedListener { player ->
                            durationMs = player.duration.coerceAtLeast(0)
                            player.start()
                        }
                        video.setOnCompletionListener { playing = false }
                        video.setOnErrorListener { _, _, _ -> playing = false; true }
                        video.setVideoURI(mediaUri)
                    }
                },
                update = { video ->
                    if (loadedUri != mediaUri) {
                        loadedUri = mediaUri
                        video.setVideoURI(mediaUri)
                    }
                },
                modifier = Modifier.fillMaxSize().testTag("player_video_surface"),
            )
        } else if (item.image.isNotBlank()) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.58f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xA8000813), Color(0x26000813), Color(0x50000813), Color(0xE6000813))
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                PlayerTextButton("‹", "player_back", true, onBack, fontSize = 34)
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("player_title"))
                    Text(
                        if (isOffline) "Offline video" else if (playable) "Streaming" else "Streaming · Source unavailable",
                        color = Color(0xFFB9C9DD), fontSize = 13.sp,
                    )
                }
                PlayerTextButton("▣", "player_subtitles", playable, {}, label = "Subtitles")
                PlayerTextButton("◖", "player_audio", playable, {}, label = "Audio")
                PlayerTextButton(if (isOffline) "1080p" else "1080p⌄", "player_quality",
                    playable && !isOffline, {}, label = if (isOffline) "Quality unavailable offline" else "Quality")
                PlayerTextButton("⋮", "player_more", playable, {}, label = "More options")
            }
            Spacer(Modifier.weight(1f))
            if (!playable) {
                Surface(color = Color(0xCC07111E), modifier = Modifier.align(Alignment.CenterHorizontally)
                    .testTag("player_source_unavailable")) {
                    Text(
                        if (isOffline) "No offline video file is available for this title."
                        else "No streaming source is connected for this title.",
                        color = Color(0xFFE2EAF4), fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Slider(
                value = progress,
                onValueChange = {
                    progress = it
                    if (durationMs > 0) videoView?.seekTo((durationMs * it).toInt())
                },
                enabled = playable,
                modifier = Modifier.fillMaxWidth().testTag("player_seek"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (playable && durationMs > 0) formatPlayerTime(positionMs) else if (playable) "00:00" else "--:--", color = Color(0xFFE5ECF5), fontSize = 13.sp)
                Text(if (playable && durationMs > 0) formatPlayerTime(durationMs) else "—:—", color = Color(0xFFE5ECF5), fontSize = 13.sp)
            }
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerTextButton("▱", "player_cast", playable && !isOffline, {}, label = "Cast")
                PlayerTextButton("◂", "player_previous", playable, {}, label = "Previous")
                PlayerTextButton("↶ 10", "player_rewind", playable, {}, label = "Rewind 10 seconds")
                Button(
                    onClick = {
                        val player = videoView
                        if (player != null) {
                            if (player.isPlaying) player.pause() else player.start()
                            playing = player.isPlaying
                        } else {
                            playing = !playing
                        }
                    },
                    enabled = playable,
                    modifier = Modifier.size(64.dp).testTag("player_play_pause"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF08A8E8)),
                ) {
                    Text(if (playing) "Ⅱ" else "▶", color = Color.White, fontSize = 21.sp)
                }
                PlayerTextButton("↷ 10", "player_forward", playable, {}, label = "Forward 10 seconds")
                PlayerTextButton("▸", "player_next", playable, {}, label = "Next")
                PlayerTextButton("☷", "player_tracks", playable, {}, label = "Tracks")
                PlayerTextButton("⚙", "player_settings", playable, {}, label = "Settings")
            }
        }
        Text(
            if (isOffline) "OFFLINE" else "STREAMING",
            color = Color(0xFFB7D3EF), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).testTag("player_mode"),
        )
    }
}

@Composable
private fun PlayerTextButton(
    text: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String = text,
    fontSize: Int = 18,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(tag),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x3315263B),
            disabledContainerColor = Color(0x2215263B),
            contentColor = Color.White,
            disabledContentColor = Color(0xFF758397),
        ),
    ) {
        Text(text, color = if (enabled) Color.White else Color(0xFF758397), fontSize = fontSize.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatPlayerTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
