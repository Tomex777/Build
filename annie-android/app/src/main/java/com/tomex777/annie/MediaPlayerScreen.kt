package com.tomex777.annie

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import org.json.JSONObject

class AnniePlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank) ?: "Video"
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        val videoConfig = intent.getStringExtra(EXTRA_VIDEO_CONFIG)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val sources = parsePlayerSources(videoConfig, mediaUri)
        val mode = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { PlayerMode.valueOf(it) }.getOrNull() }
            ?: PlayerMode.STREAMING
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
                    sourceAvailable = mediaUri != null || sources.isNotEmpty(),
                    mediaUri = mediaUri,
                    sources = sources,
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
        const val EXTRA_VIDEO_CONFIG = "annie.player.video_config"
        const val EXTRA_MODE = "annie.player.mode"
    }
}

internal enum class PlayerMode { STREAMING, OFFLINE }

internal data class PlayerSource(
    val label: String,
    val uri: Uri,
    val headers: Map<String, String> = emptyMap(),
)

private enum class AnnieVideoScale(val label: String, val scale: MediaPlayer.ScaleType) {
    FIT("Fit", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    FILL("Fill", MediaPlayer.ScaleType.SURFACE_FILL),
    STRETCH("Stretch", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
}

private fun View.installPlayerTap(onTap: () -> Unit) {
    isClickable = true
    setOnClickListener { onTap() }
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).installPlayerTap(onTap)
    }
}

@Composable
internal fun MediaPlayerScreen(
    item: CatalogItem,
    mode: PlayerMode,
    sourceAvailable: Boolean,
    mediaUri: Uri? = null,
    sources: List<PlayerSource> = emptyList(),
    onBack: () -> Unit,
    immersive: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler(onBack = onBack)

    DisposableEffect(activity, view, immersive) {
        val controller = if (immersive) activity?.window?.let { WindowCompat.getInsetsController(it, view) } else null
        if (immersive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (immersive) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val sourceChoices = remember(mediaUri, sources) {
        if (sources.isNotEmpty()) sources else mediaUri?.let { listOf(PlayerSource("Source", it)) }.orEmpty()
    }
    var sourceIndex by remember(sourceChoices) { mutableStateOf(0) }
    var switchResumePosition by remember { mutableLongStateOf(0L) }
    val activeSource = sourceChoices.getOrNull(sourceIndex)
    val activeUri = activeSource?.uri
    val playable = sourceAvailable || activeUri != null
    val isOffline = mode == PlayerMode.OFFLINE
    val resumePrefs = remember { appContext.getSharedPreferences("annie_video_resume", Context.MODE_PRIVATE) }
    val resumeKey = remember(item.id, item.title) { "${item.id}:${item.title}" }

    val libVlc = remember(activeUri) {
        activeUri?.let {
            LibVLC(
                appContext,
                arrayListOf(
                    "--audio-time-stretch",
                    "--network-caching=1500",
                    "--no-video-title-show",
                ),
            )
        }
    }
    val player = remember(libVlc) { libVlc?.let(::MediaPlayer) }
    var attachedPlayer by remember(activeUri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(activeUri) { mutableStateOf(false) }
    var positionMs by remember(activeUri) { mutableLongStateOf(0L) }
    var durationMs by remember(activeUri) { mutableLongStateOf(0L) }
    var userPaused by remember(activeUri) { mutableStateOf(false) }
    var controlsVisible by remember(activeUri) { mutableStateOf(true) }
    var wasPlayingBeforeBackground by remember(activeUri) { mutableStateOf(false) }
    var scaleMode by remember(activeUri) { mutableStateOf(AnnieVideoScale.FIT) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var audioTracks by remember(activeUri) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var subtitleTracks by remember(activeUri) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var speed by remember(mediaUri) { mutableFloatStateOf(1f) }

    DisposableEffect(player, libVlc, activeUri) {
        if (player != null && libVlc != null && activeUri != null) {
            val media = Media(libVlc, activeUri).apply {
                val emulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                    Build.MODEL.contains("Emulator", ignoreCase = true)
                // Emulator codec surfaces often cannot hand VLC an opaque output surface.
                // Decode in software there; keep hardware decoding on physical phones.
                setHWDecoderEnabled(!emulator, false)
                addOption(":network-caching=1500")
                activeSource?.headers?.forEach { (name, value) ->
                    when (name.lowercase()) {
                        "user-agent" -> addOption(":http-user-agent=$value")
                        "referer", "referrer" -> addOption(":http-referrer=$value")
                        "cookie" -> addOption(":http-cookie=$value")
                    }
                }
            }
            player.media = media
            media.release()
        }
        onDispose {
            if (player != null) {
                val last = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(positionMs)
                if (last > 2_000L) resumePrefs.edit().putLong(resumeKey, last).apply()
                runCatching { player.stop() }
                runCatching { player.detachViews() }
                runCatching { player.release() }
            }
            runCatching { libVlc?.release() }
        }
    }

    LaunchedEffect(player, attachedPlayer, activeUri) {
        if (player == null || activeUri == null) return@LaunchedEffect
        var checks = 0
        while (isActive && attachedPlayer !== player && checks < 120) {
            delay(50)
            checks++
        }
        if (!isActive || attachedPlayer !== player) return@LaunchedEffect
        player.play()
        val persistedResume = resumePrefs.getLong(resumeKey, 0L)
        val resume = switchResumePosition.takeIf { it > 0L } ?: persistedResume
        switchResumePosition = 0L
        if (resume > 0L) runCatching { player.setTime(resume) }
        runCatching { player.setRate(speed) }
        playing = true
        while (isActive) {
            durationMs = runCatching { player.length.coerceAtLeast(0L) }.getOrDefault(0L)
            positionMs = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(0L)
            playing = !userPaused && runCatching { player.isPlaying }.getOrDefault(false)
            delay(250)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (player != null) {
                        wasPlayingBeforeBackground = runCatching { player.isPlaying }.getOrDefault(false)
                        if (wasPlayingBeforeBackground) runCatching { player.pause() }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (player != null && wasPlayingBeforeBackground && !userPaused) {
                        runCatching { player.play() }
                        wasPlayingBeforeBackground = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controlsVisible, playing, subtitleMenu, audioMenu, speedMenu, qualityMenu) {
        if (controlsVisible && playing && !subtitleMenu && !audioMenu && !speedMenu && !qualityMenu) {
            delay(3_200)
            controlsVisible = false
        }
    }

    fun refreshTracks() {
        if (player == null) return
        audioTracks = runCatching {
            player.audioTracks?.filter { it.id >= 0 }?.map { it.id to it.name.orEmpty().ifBlank { "Audio track" } }
        }.getOrNull().orEmpty()
        subtitleTracks = runCatching {
            player.spuTracks?.filter { it.id >= 0 }?.map { it.id to it.name.orEmpty().ifBlank { "Subtitle track" } }
        }.getOrNull().orEmpty()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { controlsVisible = !controlsVisible }
            .testTag("media_player"),
    ) {
        if (player != null && activeUri != null) {
            AndroidView(
                factory = { viewContext ->
                    VLCVideoLayout(viewContext).also { layout ->
                        layout.installPlayerTap { controlsVisible = !controlsVisible }
                    }
                },
                update = { layout ->
                    layout.installPlayerTap { controlsVisible = !controlsVisible }
                    if (attachedPlayer !== player) {
                        layout.post {
                            if (attachedPlayer !== player) {
                                runCatching { attachedPlayer?.detachViews() }
                                // TextureView composes inside Annie's full-screen controls layer and
                                // avoids SurfaceView's separate-window ordering with Compose overlays.
                                val attached = runCatching { player.attachViews(layout, null, true, true) }.isSuccess
                                if (attached) {
                                    attachedPlayer = player
                                    runCatching { player.setVideoScale(scaleMode.scale) }
                                }
                            }
                        }
                    } else {
                        runCatching { player.setVideoScale(scaleMode.scale) }
                    }
                },
                modifier = Modifier.fillMaxSize().testTag("player_video_surface"),
            )
        } else if (item.image.isNotBlank()) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(0.42f),
            )
        }

        if (!playable) {
            Surface(
                color = Color(0xDD07111E),
                modifier = Modifier.align(Alignment.Center).testTag("player_source_unavailable"),
            ) {
                Text(
                    if (isOffline) "No offline video file is available for this title."
                    else "No streaming source is connected for this title.",
                    color = Color(0xFFE2EAF4),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        if (activeUri != null && durationMs == 0L && positionMs == 0L && !userPaused) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(42.dp).testTag("player_buffering"),
                color = Color.White,
            )
        }

        if (controlsVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xB8000000), Color(0x18000000), Color(0x42000000), Color(0xD9000000))
                    )
                )
            ) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerTextButton("‹", "player_back", true, onBack, fontSize = 34)
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player_title"),
                        )
                        Text(
                            if (isOffline) "Offline video" else if (playable) "Streaming" else "Source unavailable",
                            color = Color(0xFFB9C9DD),
                            fontSize = 12.sp,
                        )
                    }

                    Box {
                        PlayerTextButton("CC", "player_subtitles", playable, {
                            refreshTracks()
                            subtitleMenu = true
                        }, label = "Subtitles")
                        DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = {
                                    runCatching { player?.setSpuTrack(-1) }
                                    subtitleMenu = false
                                },
                            )
                            subtitleTracks.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        runCatching { player?.setSpuTrack(id) }
                                        subtitleMenu = false
                                    },
                                )
                            }
                        }
                    }

                    Box {
                        PlayerTextButton("♪", "player_audio", playable, {
                            refreshTracks()
                            audioMenu = true
                        }, label = "Audio")
                        DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                            if (audioTracks.isEmpty()) {
                                DropdownMenuItem(text = { Text("Default audio") }, onClick = { audioMenu = false })
                            }
                            audioTracks.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        runCatching { player?.setAudioTrack(id) }
                                        audioMenu = false
                                    },
                                )
                            }
                        }
                    }

                    Box {
                        val qualityLabel = when {
                            isOffline -> "Source"
                            sourceChoices.size > 1 -> activeSource?.label?.ifBlank { "Auto" } ?: "Auto"
                            else -> "Auto⌄"
                        }
                        PlayerTextButton(
                            qualityLabel,
                            "player_quality",
                            playable && !isOffline,
                            { qualityMenu = true },
                            label = "Quality",
                        )
                        DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                            if (sourceChoices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Source quality") },
                                    onClick = { qualityMenu = false },
                                )
                            } else {
                                sourceChoices.forEachIndexed { index, source ->
                                    DropdownMenuItem(
                                        text = { Text(source.label.ifBlank { "Source ${index + 1}" }) },
                                        onClick = {
                                            if (index != sourceIndex) {
                                                switchResumePosition = positionMs
                                                sourceIndex = index
                                                controlsVisible = true
                                            }
                                            qualityMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PlayerTextButton("↶ 10", "player_rewind", playable, {
                        val p = player
                        if (p != null) {
                            val target = (p.time - 10_000L).coerceAtLeast(0L)
                            p.setTime(target)
                            positionMs = target
                        }
                        controlsVisible = true
                    }, label = "Rewind 10 seconds")
                    Button(
                        onClick = {
                            val p = player
                            if (p != null) {
                                if (playing) {
                                    userPaused = true
                                    p.pause()
                                    playing = false
                                } else {
                                    userPaused = false
                                    p.play()
                                    playing = true
                                }
                            } else {
                                playing = !playing
                            }
                            controlsVisible = true
                        },
                        enabled = playable,
                        modifier = Modifier.size(72.dp).testTag("player_play_pause"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC168EEA)),
                    ) {
                        Text(if (playing) "Ⅱ" else "▶", color = Color.White, fontSize = 22.sp)
                    }
                    PlayerTextButton("↷ 10", "player_forward", playable, {
                        val p = player
                        if (p != null) {
                            val end = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                            val target = (p.time + 10_000L).coerceAtMost(end)
                            p.setTime(target)
                            positionMs = target
                        }
                        controlsVisible = true
                    }, label = "Forward 10 seconds")
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = positionMs.toFloat().coerceIn(0f, sliderMax),
                        onValueChange = { value ->
                            positionMs = value.toLong()
                            if (durationMs > 0L) runCatching { player?.setTime(positionMs) }
                            controlsVisible = true
                        },
                        valueRange = 0f..sliderMax,
                        enabled = playable,
                        modifier = Modifier.fillMaxWidth().testTag("player_seek"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (playable && durationMs > 0L) formatPlayerTime(positionMs) else if (playable) "00:00" else "--:--",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("player_position"),
                        )
                        Text(
                            if (playable && durationMs > 0L) formatPlayerTime(durationMs) else "—:—",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("player_duration"),
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PlayerTextButton("▱", "player_cast", playable && !isOffline, {}, label = "Cast")
                        PlayerTextButton("◂", "player_previous", playable, {}, label = "Previous")
                        PlayerTextButton("▸", "player_next", playable, {}, label = "Next")

                        Box {
                            PlayerTextButton("${formatRate(speed)}×", "player_speed", playable, { speedMenu = true }, label = "Speed")
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                    DropdownMenuItem(
                                        text = { Text("${formatRate(rate)}×") },
                                        onClick = {
                                            speed = rate
                                            runCatching { player?.setRate(rate) }
                                            speedMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        PlayerTextButton(scaleMode.label, "player_aspect", playable, {
                            scaleMode = when (scaleMode) {
                                AnnieVideoScale.FIT -> AnnieVideoScale.FILL
                                AnnieVideoScale.FILL -> AnnieVideoScale.STRETCH
                                AnnieVideoScale.STRETCH -> AnnieVideoScale.FIT
                            }
                            runCatching { player?.setVideoScale(scaleMode.scale) }
                            controlsVisible = true
                        }, label = "Aspect")
                        PlayerTextButton("↻", "player_rotate", true, {
                            val current = activity?.requestedOrientation
                            activity?.requestedOrientation =
                                if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                        }, label = "Rotate")
                        PlayerTextButton("⋮", "player_more", playable, {}, label = "More options")
                        PlayerTextButton("☷", "player_tracks", playable, {
                            refreshTracks()
                            audioMenu = true
                        }, label = "Tracks")
                        PlayerTextButton("⚙", "player_settings", playable, { speedMenu = true }, label = "Settings")
                    }
                }
            }
        }

        Text(
            if (isOffline) "OFFLINE" else "STREAMING",
            color = Color(0xFFB7D3EF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("player_mode"),
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
    fontSize: Int = 16,
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
        Text(
            text,
            color = if (enabled) Color.White else Color(0xFF758397),
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatRate(rate: Float): String =
    if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()

private fun parsePlayerSources(config: JSONObject?, fallback: Uri?): List<PlayerSource> {
    if (config == null) return fallback?.let { listOf(PlayerSource("Source", it)) }.orEmpty()
    val topHeaders = config.optJSONObject("headers").stringMap()
    val qualities = config.optJSONArray("qualities")
    val parsed = buildList {
        if (qualities != null) {
            for (index in 0 until qualities.length()) {
                val row = qualities.optJSONObject(index) ?: continue
                val address = listOf(
                    row.optString("uri"),
                    row.optString("url"),
                    row.optString("streamUrl"),
                ).firstOrNull(String::isNotBlank) ?: continue
                val label = row.optString("label")
                    .ifBlank { row.optString("quality") }
                    .ifBlank { "Source ${index + 1}" }
                val headers = topHeaders + row.optJSONObject("headers").stringMap()
                add(PlayerSource(label, Uri.parse(address), headers))
            }
        }
    }
    if (parsed.isNotEmpty()) return parsed
    val direct = config.optString("uri").takeIf(String::isNotBlank)?.let(Uri::parse) ?: fallback
    return direct?.let { listOf(PlayerSource(config.optString("quality", "Source"), it, topHeaders)) }.orEmpty()
}

private fun JSONObject?.stringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val objectValue = this
    return buildMap {
        val iterator = objectValue.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = objectValue.optString(key)
            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
