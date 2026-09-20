package com.example.whatsapp.presentation.chatscreen

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private fun View.installNightVideoTapHandler(onTap: () -> Unit) {
    isClickable = true
    setOnClickListener { onTap() }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).installNightVideoTapHandler(onTap)
        }
    }
}

private fun shouldNightStartWithSoftwareVideoDecode(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val brand = Build.BRAND.orEmpty()
    val device = Build.DEVICE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()

    return fingerprint.startsWith("generic", ignoreCase = true) ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        manufacturer.contains("Genymotion", ignoreCase = true) ||
        (brand.startsWith("generic", ignoreCase = true) &&
            device.startsWith("generic", ignoreCase = true)) ||
        product.contains("sdk_gphone", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        hardware.contains("ranchu", ignoreCase = true)
}

private enum class NightVideoAspect(
    val label: String,
    val scale: MediaPlayer.ScaleType,
) {
    Fit("Fit", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    Stretch("Stretch", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
    Crop("Crop", MediaPlayer.ScaleType.SURFACE_FILL),
}

@Composable
internal fun NightAniyomiVlcPlayer(
    item: NightChatMediaItem,
    active: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findNightActivity() }
    val appContext = context.applicationContext
    val startWithSoftwareDecode = remember { shouldNightStartWithSoftwareVideoDecode() }
    var softwareDecode by remember(item.localPath) { mutableStateOf(startWithSoftwareDecode) }
    var userPaused by remember(item.localPath) { mutableStateOf(false) }
    var fallbackResumePosition by remember(item.localPath) { mutableLongStateOf(0L) }

    val mediaUri = remember(item.localPath) {
        when {
            item.localPath.startsWith("http://") ||
                item.localPath.startsWith("https://") ||
                item.localPath.startsWith("content://") ||
                item.localPath.startsWith("file://") -> Uri.parse(item.localPath)
            else -> Uri.fromFile(File(item.localPath))
        }
    }

    val libVlc = remember(item.localPath, softwareDecode) {
        val options = arrayListOf(
            "--audio-time-stretch",
            "--network-caching=1500",
            "--no-video-title-show",
        )
        if (softwareDecode) {
        }
        LibVLC(appContext, options)
    }
    val player = remember(item.localPath, softwareDecode) { MediaPlayer(libVlc) }
    var attachedPlayer by remember(item.localPath) {
        mutableStateOf<MediaPlayer?>(null)
    }

    var controlsVisible by remember(item.localPath) { mutableStateOf(true) }
    var controlsLocked by remember(item.localPath) { mutableStateOf(false) }
    var playing by remember(item.localPath) { mutableStateOf(false) }
    var length by remember(item.localPath) { mutableLongStateOf(0L) }
    var position by remember(item.localPath) { mutableLongStateOf(0L) }
    var dragging by remember(item.localPath) { mutableStateOf(false) }
    var dragPosition by remember(item.localPath) { mutableFloatStateOf(0f) }

    var autoplayNext by remember(item.localPath) { mutableStateOf(false) }
    var playbackSpeed by remember(item.localPath) { mutableFloatStateOf(1f) }
    var aspect by remember(item.localPath) { mutableStateOf(NightVideoAspect.Fit) }
    var landscape by remember(item.localPath) { mutableStateOf(false) }
    var completionHandled by remember(item.localPath) { mutableStateOf(false) }

    var subtitleMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    var audioTracks by remember(item.localPath) {
        mutableStateOf<List<Pair<Int, String>>>(emptyList())
    }
    var subtitleTracks by remember(item.localPath) {
        mutableStateOf<List<Pair<Int, String>>>(emptyList())
    }

    val menuOpen = subtitleMenu || audioMenu || speedMenu || moreMenu

    DisposableEffect(player, libVlc, item.localPath) {
        val media = Media(libVlc, mediaUri).apply {
            if (softwareDecode) {
                // Do not call setHWDecoderEnabled(false, false) here: in this
                // LibVLC generation that can still leave Android MediaCodec in
                // the decoder candidate list. Force the software decoder path.
                addOption(":codec=avcodec")
                addOption(":avcodec-hw=none")
            } else {
                setHWDecoderEnabled(true, false)
            }
            addOption(":network-caching=1500")
        }
        player.media = media
        media.release()

        onDispose {
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(active, player, softwareDecode, attachedPlayer) {
        if (!active) {
            runCatching { player.pause() }
            playing = false
            if (landscape) {
                landscape = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            return@LaunchedEffect
        }
        if (attachedPlayer !== player) {
            playing = false
            return@LaunchedEffect
        }

        player.play()
        if (softwareDecode && fallbackResumePosition > 0L) {
            runCatching { player.setTime(fallbackResumePosition) }
        }
        runCatching { player.setRate(playbackSpeed) }
        playing = true
        var startedAt = SystemClock.elapsedRealtime()
        var lastAdvanceAt = startedAt
        var lastObservedPosition = -1L

        while (isActive && active) {
            val now = SystemClock.elapsedRealtime()
            length = runCatching { player.length.coerceAtLeast(0L) }.getOrDefault(0L)
            position = runCatching { player.time.coerceAtLeast(0L) }.getOrDefault(0L)
            playing = runCatching { player.isPlaying }.getOrDefault(false)

            if (position > lastObservedPosition + 180L) {
                lastObservedPosition = position
                lastAdvanceAt = now
            }

            if (
                !softwareDecode &&
                !userPaused &&
                length > 0L &&
                position < (length - 1500L).coerceAtLeast(0L) &&
                (
                    (lastObservedPosition >= 500L && now - lastAdvanceAt >= 2500L) ||
                        (lastObservedPosition < 500L && now - startedAt >= 9000L)
                    )
            ) {
                // Treat startup latency differently from a decoder that truly stalled.
                // Once playback has advanced, 2.5s without movement is suspicious.
                // Before the first 500ms, allow a longer startup window so slow
                // surface/codec initialization does not trigger a false fallback at 0ms.
                fallbackResumePosition = position
                Log.w(
                    "NightVideo",
                    "Hardware playback stalled at ${position}ms; recreating VLC with software decoding.",
                )
                softwareDecode = true
                return@LaunchedEffect
            }

            audioTracks = runCatching {
                player.audioTracks
                    ?.filter { it.id >= 0 }
                    ?.map { it.id to it.name.orEmpty().ifBlank { "Audio" } }
                    .orEmpty()
            }.getOrDefault(emptyList())

            subtitleTracks = runCatching {
                player.spuTracks
                    ?.filter { it.id >= 0 }
                    ?.map { it.id to it.name.orEmpty().ifBlank { "Subtitle" } }
                    .orEmpty()
            }.getOrDefault(emptyList())

            if (length > 0L && position >= (length - 450L).coerceAtLeast(0L)) {
                if (!completionHandled) {
                    completionHandled = true
                    if (autoplayNext && hasNext) onNext()
                }
            } else if (length <= 0L || position < (length - 1500L).coerceAtLeast(0L)) {
                completionHandled = false
            }

            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, playing, controlsLocked, menuOpen) {
        if (controlsVisible && playing && !controlsLocked && !menuOpen) {
            delay(3200L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .semantics {
                contentDescription =
                    "Night video player: " + item.sender.ifBlank { "Video" }
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                VLCVideoLayout(viewContext).also { layout ->
                    layout.installNightVideoTapHandler {
                        controlsVisible = !controlsVisible
                    }
                }
            },
            update = { layout ->
                layout.installNightVideoTapHandler {
                    controlsVisible = !controlsVisible
                }
                if (attachedPlayer !== player) {
                    // Avoid starting VLC against a zero-sized or stale surface. The
                    // same VLCVideoLayout stays mounted while the player/engine swaps.
                    layout.post {
                        if (attachedPlayer !== player) {
                            runCatching { attachedPlayer?.detachViews() }
                            val attached = runCatching {
                                player.attachViews(layout, null, true, false)
                            }.isSuccess
                            if (attached) {
                                attachedPlayer = player
                                layout.installNightVideoTapHandler {
                                    controlsVisible = !controlsVisible
                                }
                                runCatching { player.setVideoScale(aspect.scale) }
                            } else {
                                Log.e("NightVideo", "Could not attach VLC player to video surface.")
                            }
                        }
                    }
                } else {
                    runCatching { player.setVideoScale(aspect.scale) }
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
                    .background(Color.Black.copy(alpha = 0.20f)),
            ) {
                if (controlsLocked) {
                    IconButton(
                        onClick = {
                            controlsLocked = false
                            controlsVisible = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.48f), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Unlock controls",
                            tint = Color.White,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.sender.ifBlank { "Video" },
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subline = listOf(item.time, item.duration)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (subline.isNotBlank()) {
                                Text(
                                    text = subline,
                                    color = Color(0xFFBEC3C6),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }

                        Box {
                            CompactPlayerButton(
                                onClick = { subtitleMenu = true },
                            ) {
                                Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = subtitleMenu,
                                onDismissRequest = { subtitleMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        runCatching { player.setSpuTrack(-1) }
                                        subtitleMenu = false
                                    },
                                )
                                subtitleTracks.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            runCatching { player.setSpuTrack(id) }
                                            subtitleMenu = false
                                        },
                                    )
                                }
                                if (subtitleTracks.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No embedded subtitles") },
                                        onClick = { subtitleMenu = false },
                                        enabled = false,
                                    )
                                }
                            }
                        }

                        Box {
                            CompactPlayerButton(
                                onClick = { moreMenu = true },
                            ) {
                                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = moreMenu,
                                onDismissRequest = { moreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (autoplayNext) "Autoplay next · On"
                                            else "Autoplay next · Off"
                                        )
                                    },
                                    onClick = {
                                        autoplayNext = !autoplayNext
                                        moreMenu = false
                                        controlsVisible = true
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.HighQuality, null) },
                                    text = { Text("Quality · source") },
                                    onClick = { moreMenu = false },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    text = { Text("Share") },
                                    onClick = {
                                        moreMenu = false
                                        onShare()
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    text = { Text("Edit") },
                                    onClick = {
                                        moreMenu = false
                                        onEdit()
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                    text = { Text("Save") },
                                    onClick = {
                                        moreMenu = false
                                        onSave()
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (hasPrevious) onPrevious()
                                controlsVisible = true
                            },
                            enabled = hasPrevious,
                            modifier = Modifier.size(66.dp),
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                "Previous media",
                                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.30f),
                                modifier = Modifier.size(46.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                if (player.isPlaying) {
                                    userPaused = true
                                    player.pause()
                                } else {
                                    userPaused = false
                                    player.play()
                                }
                                playing = player.isPlaying
                                controlsVisible = true
                            },
                            modifier = Modifier
                                .size(92.dp)
                                .background(Color.Black.copy(alpha = 0.30f), CircleShape),
                        ) {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(58.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                if (hasNext) onNext()
                                controlsVisible = true
                            },
                            enabled = hasNext,
                            modifier = Modifier.size(66.dp),
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                "Next media",
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.30f),
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompactPlayerButton(
                                onClick = {
                                    controlsLocked = true
                                    controlsVisible = true
                                },
                            ) {
                                Icon(Icons.Default.LockOpen, "Lock controls", tint = Color.White)
                            }

                            CompactPlayerButton(
                                onClick = {
                                    landscape = !landscape
                                    activity?.requestedOrientation =
                                        if (landscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    controlsVisible = true
                                },
                            ) {
                                Icon(Icons.Default.ScreenRotation, "Rotate", tint = Color.White)
                            }

                            Box {
                                CompactPlayerButton(
                                    onClick = { audioMenu = true },
                                ) {
                                    Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = audioMenu,
                                    onDismissRequest = { audioMenu = false },
                                ) {
                                    audioTracks.forEach { (id, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                runCatching { player.setAudioTrack(id) }
                                                audioMenu = false
                                            },
                                        )
                                    }
                                    if (audioTracks.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Default audio") },
                                            onClick = { audioMenu = false },
                                        )
                                    }
                                }
                            }

                            Box {
                                TextButton(
                                    onClick = { speedMenu = true },
                                    modifier = Modifier.padding(horizontal = 0.dp),
                                ) {
                                    Text(
                                        text = formatNightRate(playbackSpeed) + "×",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                    )
                                }
                                DropdownMenu(
                                    expanded = speedMenu,
                                    onDismissRequest = { speedMenu = false },
                                ) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                        DropdownMenuItem(
                                            text = { Text(formatNightRate(rate) + "×") },
                                            onClick = {
                                                playbackSpeed = rate
                                                runCatching { player.setRate(rate) }
                                                speedMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                CompactPlayerButton(
                                    onClick = {
                                        controlsVisible = false
                                        runCatching {
                                            activity?.enterPictureInPictureMode(
                                                PictureInPictureParams.Builder().build()
                                            )
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.PictureInPictureAlt,
                                        "Picture in picture",
                                        tint = Color.White,
                                    )
                                }
                            }

                            CompactPlayerButton(
                                onClick = {
                                    aspect = when (aspect) {
                                        NightVideoAspect.Fit -> NightVideoAspect.Stretch
                                        NightVideoAspect.Stretch -> NightVideoAspect.Crop
                                        NightVideoAspect.Crop -> NightVideoAspect.Fit
                                    }
                                    runCatching { player.setVideoScale(aspect.scale) }
                                    controlsVisible = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.AspectRatio,
                                    aspect.label,
                                    tint = Color.White,
                                )
                            }
                        }

                        if (length > 0L) {
                            val max = length.coerceAtLeast(1L).toFloat()
                            val shownPosition =
                                if (dragging) dragPosition.coerceIn(0f, max)
                                else position.toFloat().coerceIn(0f, max)

                            Slider(
                                value = shownPosition,
                                onValueChange = {
                                    dragging = true
                                    dragPosition = it
                                    controlsVisible = true
                                },
                                onValueChangeFinished = {
                                    val target = dragPosition.toLong().coerceIn(0L, length)
                                    player.setTime(target)
                                    position = target
                                    dragging = false
                                    controlsVisible = true
                                },
                                valueRange = 0f..max,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFE94B72),
                                    activeTrackColor = Color(0xFFE94B72),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.42f),
                                ),
                            )

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    formatNightPlayerTime(
                                        if (dragging) dragPosition.toLong() else position
                                    ),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    formatNightPlayerTime(length),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPlayerButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
    ) {
        content()
    }
}

private tailrec fun Context.findNightActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findNightActivity()
    else -> null
}

private fun formatNightPlayerTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        hours.toString() + ":" +
            minutes.toString().padStart(2, '0') + ":" +
            seconds.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }
}

private fun formatNightRate(rate: Float): String =
    if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()
