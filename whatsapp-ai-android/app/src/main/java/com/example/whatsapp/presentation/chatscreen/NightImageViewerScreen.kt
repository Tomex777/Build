package com.example.whatsapp.presentation.chatscreen

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

data class NightChatMediaItem(
    val id: String,
    val localPath: String,
    val mimeType: String,
    val caption: String = "",
    val time: String = "",
    val sender: String = "",
    val thumbnailPath: String? = null,
    val duration: String = "",
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

@Composable
fun NightMediaViewerScreen(
    items: List<NightChatMediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onEdit: (NightChatMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        onBack()
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var imageMoreMenu by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            if (oldOrientation != null) {
                activity.requestedOrientation = oldOrientation
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        zoomed = false
        controlsVisible = true
        imageMoreMenu = false
        if (!items[pagerState.currentPage].isVideo) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                NightAniyomiVlcPlayer(
                    item = item,
                    active = page == pagerState.currentPage,
                    hasPrevious = page > 0,
                    hasNext = page < items.lastIndex,
                    onPrevious = {
                        if (page > 0) {
                            scope.launch { pagerState.animateScrollToPage(page - 1) }
                        }
                    },
                    onNext = {
                        if (page < items.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    },
                    onBack = onBack,
                    onShare = { shareNightMedia(context, item) },
                    onEdit = { onEdit(item) },
                    onSave = { saveNightMedia(context, item) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                NightZoomableImage(
                    path = item.localPath,
                    active = page == pagerState.currentPage,
                    onZoomedChange = { isZoomed ->
                        if (page == pagerState.currentPage) zoomed = isZoomed
                    },
                    onToggleControls = { controlsVisible = !controlsVisible },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (controlsVisible && !items[pagerState.currentPage].isVideo) {
            val current = items[pagerState.currentPage]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.sender.ifBlank { "Media" },
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (current.time.isNotBlank()) {
                            Text(current.time, color = Color(0xFFBEC3C6), fontSize = 11.sp)
                        }
                        Text(
                            text = (pagerState.currentPage + 1).toString() + " / " + items.size,
                            color = Color(0xFFBEC3C6),
                            fontSize = 11.sp,
                        )
                    }
                }

                IconButton(onClick = { shareNightMedia(context, current) }) {
                    Icon(Icons.Default.Share, "Share", tint = Color.White)
                }

                IconButton(onClick = { onEdit(current) }) {
                    Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                }

                Box {
                    IconButton(onClick = { imageMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = imageMoreMenu,
                        onDismissRequest = { imageMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            text = { Text("Save") },
                            onClick = {
                                imageMoreMenu = false
                                saveNightMedia(context, current)
                            },
                        )
                    }
                }
            }

            if (current.caption.isNotBlank()) {
                Text(
                    text = current.caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color(0xFFE3E5E6), fontSize = 11.sp)
    }
}

@Composable
private fun NightZoomableImage(
    path: String,
    active: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        scale = next
        offset = if (next <= 1.01f) Offset.Zero else offset + panChange
        if (active) onZoomedChange(next > 1.01f)
    }

    Box(
        modifier = modifier
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        scale = if (scale > 1.05f) 1f else 2.4f
                        offset = Offset.Zero
                        if (active) onZoomedChange(scale > 1.01f)
                    },
                )
            }
            .transformable(transform),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = nightMediaModel(path),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private fun View.installNightEditorTapHandler(onTap: () -> Unit) {
    isClickable = true
    setOnClickListener { onTap() }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).installNightEditorTapHandler(onTap)
        }
    }
}

@Composable
internal fun NightVlcVideoSurface(
    path: String,
    active: Boolean = true,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var softwareDecode by remember(path) { mutableStateOf(false) }
    var userPaused by remember(path) { mutableStateOf(false) }
    var fallbackResumePosition by remember(path) { mutableLongStateOf(0L) }
    val mediaUri = remember(path) {
        when {
            path.startsWith("http://") || path.startsWith("https://") ||
                path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
            else -> Uri.fromFile(File(path))
        }
    }

    val libVlc = remember(path, softwareDecode) {
        val options = arrayListOf(
            "--audio-time-stretch",
            "--network-caching=1500",
        )
        if (softwareDecode) {
            options += "--avcodec-hw=none"
        }
        LibVLC(context, options)
    }
    val player = remember(path, softwareDecode) { MediaPlayer(libVlc) }
    var attachedPlayer by remember(path) {
        mutableStateOf<MediaPlayer?>(null)
    }
    var playing by remember(path) { mutableStateOf(false) }
    var length by remember(path) { mutableLongStateOf(0L) }
    var position by remember(path) { mutableLongStateOf(0L) }

    DisposableEffect(player, libVlc, path) {
        val media = Media(libVlc, mediaUri).apply {
            if (softwareDecode) {
                setHWDecoderEnabled(false, false)
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
        if (active) {
            if (attachedPlayer !== player) {
                playing = false
                return@LaunchedEffect
            }
            player.play()
            if (softwareDecode && fallbackResumePosition > 0L) {
                runCatching { player.setTime(fallbackResumePosition) }
            }
            playing = true
            var startedAt = SystemClock.elapsedRealtime()
            var lastAdvanceAt = startedAt
            var lastObservedPosition = -1L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                length = player.length.coerceAtLeast(0L)
                position = player.time.coerceAtLeast(0L)
                playing = player.isPlaying
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
                    // Do not mistake normal surface/decoder startup for a 0ms stall.
                    fallbackResumePosition = position
                    Log.w(
                        "NightVideo",
                        "Editor playback stalled at ${position}ms; recreating VLC with software decoding.",
                    )
                    softwareDecode = true
                    return@LaunchedEffect
                }
                delay(250)
            }
        } else {
            runCatching { player.pause() }
            playing = false
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    layout.installNightEditorTapHandler(onToggleControls)
                }
            },
            update = { layout ->
                layout.installNightEditorTapHandler(onToggleControls)
                if (attachedPlayer !== player) {
                    layout.post {
                        if (attachedPlayer !== player) {
                            runCatching { attachedPlayer?.detachViews() }
                            val attached = runCatching {
                                player.attachViews(layout, null, false, false)
                            }.isSuccess
                            if (attached) {
                                attachedPlayer = player
                                layout.installNightEditorTapHandler(onToggleControls)
                            } else {
                                Log.e("NightVideo", "Could not attach editor VLC player to video surface.")
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showControls) {
            IconButton(
                onClick = {
                    if (player.isPlaying) {
                        userPaused = true
                        player.pause()
                        playing = false
                    } else {
                        userPaused = false
                        player.play()
                        playing = true
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp),
                )
            }

            if (length > 0L) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 18.dp, end = 18.dp, bottom = 94.dp),
                ) {
                    Slider(
                        value = (position.toFloat() / length.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { value ->
                            val target = (length * value.coerceIn(0f, 1f)).toLong()
                            player.setTime(target)
                            position = target
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE94B72),
                            activeTrackColor = Color(0xFFE94B72),
                            inactiveTrackColor = Color.White.copy(alpha = 0.42f),
                        ),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(formatViewerTime(position), color = Color.White, fontSize = 11.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(formatViewerTime(length), color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun nightMediaModel(path: String): Any =
    if (
        path.startsWith("http://") ||
        path.startsWith("https://") ||
        path.startsWith("content://") ||
        path.startsWith("android.resource://") ||
        path.startsWith("file://")
    ) {
        path
    } else {
        File(path)
    }

private fun shareNightMedia(context: Context, item: NightChatMediaItem) {
    val file = File(item.localPath)
    if (!file.exists()) {
        Toast.makeText(context, "This media is not available locally.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".files",
            file,
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(item.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share media",
            )
        )
    }.onFailure {
        Toast.makeText(context, "Could not share this media.", Toast.LENGTH_SHORT).show()
    }
}

private const val NIGHT_LEGACY_MEDIA_WRITE_REQUEST = 4821

private fun saveNightMedia(context: Context, item: NightChatMediaItem) {
    val source = File(item.localPath)
    if (!source.exists()) {
        Toast.makeText(context, "This media is not available locally.", Toast.LENGTH_SHORT).show()
        return
    }

    if (
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        val activity = context as? Activity
        if (activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                NIGHT_LEGACY_MEDIA_WRITE_REQUEST,
            )
            Toast.makeText(
                context,
                "Allow storage access, then tap Save again.",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(context, "Storage permission is required to save.", Toast.LENGTH_SHORT).show()
        }
        return
    }

    runCatching {
        val resolver = context.contentResolver
        val displayName = source.name.ifBlank {
            (if (item.isVideo) "Night video " else "Night image ") + System.currentTimeMillis()
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.TITLE, displayName.substringBeforeLast("."))
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (item.isVideo) "Movies/Night" else "Pictures/Night",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (item.isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val uri = checkNotNull(resolver.insert(collection, values))
        try {
            resolver.openOutputStream(uri).use { output ->
                requireNotNull(output)
                source.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (failure: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }

        Toast.makeText(context, "Saved.", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Could not save this media.", Toast.LENGTH_SHORT).show()
    }
}

private fun formatViewerTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) {
        hours.toString() + ":" + minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }
}
