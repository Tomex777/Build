package com.veya.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veya.app.VeyaApplication
import com.veya.app.ui.VeyaTheme
import com.veya.app.downloads.VeyaDownloadState
import com.veya.app.youtube.VeyaBridgeSession
import com.veya.app.youtube.VeyaPlaybackSelection
import dev.tomex.youtube.api.Chapter
import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.ResolverFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Locale

class VeyaPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Video" }
        if (videoId.isBlank()) {
            finish()
            return
        }

        setContent {
            VeyaTheme {
                VeyaPlayerScreen(
                    videoId = videoId,
                    fallbackTitle = title,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isFinishing) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "veya.video.id"
        private const val EXTRA_TITLE = "veya.video.title"

        fun intent(context: Context, videoId: String, title: String): Intent =
            Intent(context, VeyaPlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_TITLE, title)
    }
}

@Composable
private fun VeyaPlayerScreen(
    videoId: String,
    fallbackTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as VeyaApplication
    val repository = app.youtubeRepository
    val bridge = app.playbackBridge
    val history = app.history
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val preferredQuality = remember(videoId) {
        context.getSharedPreferences("veya_settings", Context.MODE_PRIVATE)
            .getInt("default_quality", 720)
    }
    val offlineDownload = remember(videoId) {
        app.downloads.entry(videoId)?.takeIf {
            it.state == VeyaDownloadState.COMPLETE &&
                !it.videoPath.isNullOrBlank() &&
                !it.audioPath.isNullOrBlank() &&
                File(requireNotNull(it.videoPath)).exists() &&
                File(requireNotNull(it.audioPath)).exists()
        }
    }
    val persistedResume = remember(videoId) { history.position(videoId) }

    var title by remember(videoId) { mutableStateOf(fallbackTitle) }
    var thumbnail by remember(videoId) { mutableStateOf<String?>(null) }
    var chapters by remember(videoId) { mutableStateOf<List<Chapter>>(emptyList()) }
    var selection by remember(videoId) { mutableStateOf<VeyaPlaybackSelection?>(null) }
    var bridgeSession by remember(videoId) { mutableStateOf<VeyaBridgeSession?>(null) }
    var loading by remember(videoId) { mutableStateOf(true) }
    var failure by remember(videoId) { mutableStateOf<String?>(null) }
    var resumeAfterReload by remember(videoId) { mutableLongStateOf(0L) }

    LaunchedEffect(videoId) {
        loading = true
        failure = null
        if (offlineDownload != null) {
            title = offlineDownload.title
            thumbnail = offlineDownload.thumbnail
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            val details = repository.videoDetails(videoId)
            title = details.title
            thumbnail = details.thumbnails.lastOrNull()
            chapters = details.chapters
            runCatching {
                repository.preparePlayback(videoId, preferredHeight = preferredQuality)
            }.getOrElse {
                if (preferredQuality > 360) {
                    repository.preparePlayback(videoId, preferredHeight = 360)
                } else {
                    throw it
                }
            }
        }.onSuccess { prepared ->
            selection = prepared
            bridgeSession = bridge.register(prepared)
            loading = false
        }.onFailure {
            failure = friendlyPlaybackError(it)
            loading = false
        }
    }

    DisposableEffect(bridgeSession?.id) {
        val id = bridgeSession?.id
        onDispose {
            if (id != null) bridge.unregister(id)
        }
    }

    val libVlc = remember {
        LibVLC(
            context.applicationContext,
            arrayListOf(
                "--audio-time-stretch",
                "--network-caching=1200",
                "--no-video-title-show"
            )
        )
    }
    val player = remember(libVlc) { MediaPlayer(libVlc) }

    var attached by remember { mutableStateOf(false) }
    var surfaceReady by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    var wasPlayingBeforeStop by remember { mutableStateOf(false) }
    var lastPictures by remember { mutableLongStateOf(0L) }
    var lastAudioBuffers by remember { mutableLongStateOf(0L) }

    BackHandler(onBack = onBack)

    fun persistResume() {
        if (positionMs >= 3_000L || durationMs > 0L) {
            history.update(
                videoId = videoId,
                title = title,
                thumbnail = thumbnail,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    DisposableEffect(player, libVlc) {
        val callback = object : IVLCVout.Callback {
            override fun onSurfacesCreated(vlcVout: IVLCVout) {
                surfaceReady = true
                Log.i("VeyaVLC", "surfacesCreated")
            }

            override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
                surfaceReady = false
                Log.i("VeyaVLC", "surfacesDestroyed")
            }
        }

        player.vlcVout.addCallback(callback)

        onDispose {
            persistResume()
            runCatching { player.vlcVout.removeCallback(callback) }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(
        bridgeSession?.id,
        offlineDownload?.videoPath,
        surfaceReady,
        attached
    ) {
        if (!surfaceReady || !attached) return@LaunchedEffect
        val session = bridgeSession
        if (offlineDownload == null && session == null) return@LaunchedEffect

        runCatching { player.stop() }

        val videoUri = if (offlineDownload != null) {
            Uri.fromFile(File(requireNotNull(offlineDownload.videoPath)))
        } else {
            Uri.parse(requireNotNull(session).videoUrl)
        }
        val audioUri = if (offlineDownload != null) {
            Uri.fromFile(File(requireNotNull(offlineDownload.audioPath)))
        } else {
            Uri.parse(requireNotNull(session).audioUrl)
        }

        val media = Media(libVlc, videoUri).apply {
            setHWDecoderEnabled(true, false)
            if (offlineDownload == null) addOption(":network-caching=1200")
        }
        try {
            player.media = media
        } finally {
            media.release()
        }

        val audioAdded = runCatching {
            player.addSlave(
                IMedia.Slave.Type.Audio,
                audioUri,
                true
            )
        }.getOrDefault(false)
        Log.i(
            "VeyaVLC",
            "audioSlaveAdded=$audioAdded offline=${offlineDownload != null}"
        )

        player.play()

        val resume = resumeAfterReload.takeIf { it > 0L } ?: persistedResume
        resumeAfterReload = 0L
        if (resume > 0L) {
            delay(650)
            runCatching { player.setTime(resume) }
        }
        runCatching { player.setRate(speed) }
        userPaused = false
    }

    LaunchedEffect(player) {
        var persistTicks = 0
        var proofTicks = 0

        while (isActive) {
            positionMs = runCatching {
                player.time.coerceAtLeast(0L)
            }.getOrDefault(positionMs)

            durationMs = runCatching {
                player.length.coerceAtLeast(0L)
            }.getOrDefault(durationMs)

            playing = !userPaused && runCatching {
                player.isPlaying
            }.getOrDefault(false)

            persistTicks++
            if (persistTicks >= 20) {
                persistResume()
                persistTicks = 0
            }

            proofTicks++
            if (proofTicks >= 8) {
                val currentMedia = runCatching { player.media }.getOrNull()
                val stats = runCatching { currentMedia?.stats }.getOrNull()
                val pictures = stats?.displayedPictures?.toLong() ?: 0L
                val audioBuffers = stats?.playedAbuffers?.toLong() ?: 0L
                if (pictures > lastPictures || audioBuffers > lastAudioBuffers) {
                    Log.i(
                        "VeyaVLC",
                        "frameProof pictures=$pictures audioBuffers=$audioBuffers positionMs=$positionMs"
                    )
                    lastPictures = pictures
                    lastAudioBuffers = audioBuffers
                }
                runCatching { currentMedia?.release() }
                proofTicks = 0
            }

            delay(250)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        persistResume()
                        wasPlayingBeforeStop = runCatching {
                            player.isPlaying
                        }.getOrDefault(false)
                        if (wasPlayingBeforeStop) runCatching { player.pause() }
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (wasPlayingBeforeStop && !userPaused) {
                        runCatching { player.play() }
                        wasPlayingBeforeStop = false
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun togglePlayback() {
        if (playing) {
            userPaused = true
            runCatching { player.pause() }
            playing = false
        } else {
            userPaused = false
            runCatching { player.play() }
            playing = true
        }
        controlsVisible = true
    }

    fun switchQuality(format: MediaFormat) {
        qualityMenu = false
        val current = selection ?: return
        val resumeAt = runCatching {
            player.time.coerceAtLeast(0L)
        }.getOrDefault(positionMs)

        scope.launch {
            runCatching {
                repository.prepareQuality(
                    videoId = videoId,
                    descriptor = current.descriptor,
                    requestedVideo = format
                )
            }.onSuccess { prepared ->
                val oldSession = bridgeSession
                val newSession = bridge.register(prepared)
                selection = prepared
                resumeAfterReload = resumeAt
                bridgeSession = newSession
                oldSession?.id?.let(bridge::unregister)
            }.onFailure {
                failure = friendlyPlaybackError(it)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                }
            }
    ) {
        AndroidView(
            factory = { VLCVideoLayout(it) },
            update = { layout ->
                if (!attached) {
                    layout.post {
                        if (!attached) {
                            val success = runCatching {
                                player.attachViews(layout, null, true, true)
                            }.isSuccess
                            if (success) {
                                attached = true
                                if (layout.width > 0 && layout.height > 0) {
                                    runCatching {
                                        player.vlcVout.setWindowSize(
                                            layout.width,
                                            layout.height
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        failure?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                color = Color(0xDD17171B)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Can't play this video",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = message,
                        color = Color(0xFFD3D3DA)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && failure == null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x52000000))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    if (offlineDownload == null && selection != null) {
                        Box {
                            IconButton(onClick = { qualityMenu = true }) {
                                Text(
                                    text = selection?.video?.format?.height
                                        ?.let { "${it}p" }
                                        ?: "Quality",
                                    color = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = qualityMenu,
                                onDismissRequest = { qualityMenu = false }
                            ) {
                                selection?.qualityOptions.orEmpty().forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text("${format.height}p") },
                                        onClick = { switchQuality(format) }
                                    )
                                }
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { speedMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Playback options",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = speedMenu,
                            onDismissRequest = { speedMenu = false }
                        ) {
                            listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text("${rate}×") },
                                    onClick = {
                                        speed = rate
                                        runCatching { player.setRate(rate) }
                                        speedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = ::togglePlayback,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                ) {
                    Icon(
                        imageVector = if (playing) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VlcSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { target ->
                            positionMs = target
                            runCatching { player.setTime(target) }
                        }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${clock(positionMs)} / ${clock(durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))

                        if (chapters.isNotEmpty()) {
                            Box {
                                Text(
                                    text = "Chapters",
                                    color = Color.White,
                                    modifier = Modifier
                                        .clickable { chapterMenu = true }
                                        .padding(8.dp)
                                )
                                DropdownMenu(
                                    expanded = chapterMenu,
                                    onDismissRequest = {
                                        chapterMenu = false
                                    }
                                ) {
                                    chapters.forEach { chapter ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${clock(chapter.startMs)}  ${chapter.title}"
                                                )
                                            },
                                            onClick = {
                                                runCatching {
                                                    player.setTime(chapter.startMs)
                                                }
                                                chapterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (
                                    configuration.orientation ==
                                    Configuration.ORIENTATION_LANDSCAPE
                                ) {
                                    activity?.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                } else {
                                    activity?.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VlcSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = Color.White.copy(alpha = 0.35f)
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0L) return@pointerInput
                detectTapGestures { point ->
                    onSeek(
                        ((point.x / size.width).coerceIn(0f, 1f) * durationMs)
                            .toLong()
                    )
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0L) return@pointerInput
                detectDragGestures(
                    onDragStart = { point ->
                        onSeek(
                            ((point.x / size.width).coerceIn(0f, 1f) * durationMs)
                                .toLong()
                        )
                    },
                    onDrag = { change, _ ->
                        onSeek(
                            ((change.position.x / size.width)
                                .coerceIn(0f, 1f) * durationMs)
                                .toLong()
                        )
                        change.consume()
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = 4.dp.toPx()

        drawRoundRect(
            color = track,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(
                size.width,
                trackHeight
            ),
            cornerRadius = CornerRadius(trackHeight, trackHeight)
        )

        drawRoundRect(
            color = primary,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(
                size.width * progress,
                trackHeight
            ),
            cornerRadius = CornerRadius(trackHeight, trackHeight)
        )

        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(size.width * progress, centerY)
        )
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0L) return "0:00"

    val total = ms / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L

    return if (hours > 0L) {
        String.format(
            Locale.US,
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    } else {
        String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            seconds
        )
    }
}

private fun friendlyPlaybackError(t: Throwable): String = when (t) {
    is ResolverFailure.ChallengeRequired ->
        "YouTube asked for additional verification. Try again later."
    is ResolverFailure.SignInRequired ->
        "This video requires an account session."
    is ResolverFailure.VideoUnavailable ->
        "This video is unavailable."
    is ResolverFailure.RateLimited ->
        "YouTube is temporarily limiting requests. Try again shortly."
    is ResolverFailure.NoPlayableFormats ->
        "No compatible video quality is available right now."
    is ResolverFailure.SabrOnly,
    is ResolverFailure.DashManifestOnly,
    is ResolverFailure.Ciphered,
    is ResolverFailure.NParameterTransformRequired,
    is ResolverFailure.UnsupportedDelivery ->
        "This video can't be played by this version of Veya yet."
    else ->
        "Playback couldn't start. Check your connection and try again."
}
