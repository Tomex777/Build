package com.tomex.aether

import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
internal fun AetherHeader(seenCount: Int, savedCount: Int, onSaved: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Aether", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = AetherText)
        Spacer(Modifier.weight(1f))
        if (seenCount > 0) Text("$seenCount seen", fontSize = 11.sp, color = AetherMuted, modifier = Modifier.padding(end = 6.dp))
        Box {
            IconButton(onClick = onSaved) { Icon(Icons.Default.BookmarkBorder, "Saved", tint = AetherText) }
            if (savedCount > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 4.dp).size(15.dp).clip(CircleShape).background(AetherAccent),
                contentAlignment = Alignment.Center,
            ) { Text(savedCount.coerceAtMost(99).toString(), fontSize = 8.sp, color = Color.White) }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.MoreVert, "Settings", tint = AetherText) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryPills(
    categories: List<FeedCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onEdit: (FeedCategory) -> Unit,
    onAdd: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (selected) AetherText else AetherRaised)
                    .combinedClickable(onClick = { onSelect(category.id) }, onLongClick = { onEdit(category) })
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(category.name, color = if (selected) Color.Black else AetherText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Box(
                Modifier.size(33.dp).clip(CircleShape).background(AetherRaised).combinedClickable(onClick = onAdd, onLongClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, "Add category", tint = AetherText, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
internal fun MemeFeed(
    state: AetherUiState,
    listState: LazyListState,
    imageLoader: ImageLoader,
    onToggle: (String) -> Unit,
    onSave: (MemePost) -> Unit,
    onDismiss: (MemePost) -> Unit,
    onComments: (MemePost) -> Unit,
    onAi: (MemePost) -> Unit,
    onDownload: (MemePost) -> Unit,
    onShare: (MemePost) -> Unit,
    onOpenSource: (MemePost) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(AetherBlack),
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(state.posts, key = { it.id }) { post ->
            MemeItem(
                post = post,
                active = state.activePostId == post.id,
                saved = state.savedPosts.any { it.id == post.id },
                autoplayVideos = state.settings.autoplayVideos,
                imageLoader = imageLoader,
                onToggle = { onToggle(post.id) },
                onSave = { onSave(post) },
                onDismiss = { onDismiss(post) },
                onComments = { onComments(post) },
                onAi = { onAi(post) },
                onDownload = { onDownload(post) },
                onShare = { onShare(post) },
                onOpenSource = { onOpenSource(post) },
            )
        }
        if (state.loadingMore) item {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AetherMuted, strokeWidth = 1.5.dp, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun MemeItem(
    post: MemePost,
    active: Boolean,
    saved: Boolean,
    autoplayVideos: Boolean,
    imageLoader: ImageLoader,
    onToggle: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onComments: () -> Unit,
    onAi: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpenSource: () -> Unit,
) {
    var dragX by remember(post.id) { mutableFloatStateOf(0f) }
    var videoPlaying by remember(post.id) { mutableStateOf(autoplayVideos) }
    val threshold = 92f

    Box(
        Modifier
            .fillMaxWidth()
            .background(AetherBlack)
            .graphicsLayer { translationX = dragX * .16f }
            .pointerInput(post.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragX = (dragX + amount).coerceIn(-220f, 220f) },
                    onDragEnd = {
                        when {
                            dragX > threshold -> onSave()
                            dragX < -threshold -> onDismiss()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            }
    ) {
        when (post.kind) {
            MediaKind.IMAGE, MediaKind.GIF -> AsyncImage(
                model = post.mediaUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clickableNoIndication(onToggle),
            )
            MediaKind.VIDEO -> VideoMedia(post, videoPlaying, autoplayVideos, onToggle) { videoPlaying = it }
        }

        AnimatedVisibility(
            visible = active,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .68f), Color.Transparent, Color.Black.copy(alpha = .82f))))
                    .clickableNoIndication(onToggle)
            ) {
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(14.dp)) {
                    Text(post.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text("r/${post.subreddit}  •  ${compactNumber(post.score)} points", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayAction(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", onSave)
                    OverlayAction(Icons.Default.Download, "Download", onDownload)
                    OverlayAction(Icons.Default.ChatBubbleOutline, "Comments", onComments)
                    OverlayAction(Icons.Default.AutoAwesome, "AI", onAi)
                    if (post.kind == MediaKind.VIDEO) {
                        OverlayAction(if (videoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (videoPlaying) "Pause" else "Play") { videoPlaying = !videoPlaying }
                    } else {
                        OverlayAction(Icons.Default.Share, "Share", onShare)
                    }
                    OverlayAction(Icons.Default.OpenInNew, "Source", onOpenSource)
                }
            }
        }
    }
}

@Composable
internal fun OverlayAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .46f))) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Text(label, color = Color.White.copy(alpha = .86f), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
internal fun VideoMedia(post: MemePost, playing: Boolean, autoplay: Boolean, onToggle: () -> Unit, onPlaying: (Boolean) -> Unit) {
    val context = LocalContext.current
    val player = remember(post.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(post.mediaUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = autoplay
        }
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxWidth().height(520.dp)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.matchParentSize().clickableNoIndication {
            onToggle()
            onPlaying(player.isPlaying)
        })
    }
}
