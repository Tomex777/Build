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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(
    post: MemePost,
    comments: List<RedditComment>,
    totalLoaded: Int,
    loading: Boolean,
    onNearEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, comments.size, totalLoaded) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (comments.size < totalLoaded && last >= comments.lastIndex - 5) onNearEnd() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AetherSurface,
        contentColor = AetherText,
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(38.dp).height(4.dp).clip(CircleShape).background(Color(0xFF4B4B4B))) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(compactNumber(post.comments), color = AetherMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider(color = Color(0xFF242424))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
            comments.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("No comments yet", color = AetherMuted) }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.82f),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp),
            ) {
                items(comments, key = { it.id }) { comment -> CommentRow(comment) }
                if (comments.size < totalLoaded) item {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.4.dp, color = AetherMuted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommentRow(comment: RedditComment) {
    Row(
        Modifier.fillMaxWidth().padding(start = (14 + comment.depth * 13).dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
    ) {
        if (comment.depth > 0) Box(Modifier.width(2.dp).height(44.dp).background(Color(0xFF303030)))
        Column(Modifier.padding(start = if (comment.depth > 0) 9.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("u/${comment.author}", color = AetherMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(7.dp))
                Text(compactNumber(comment.score), color = Color(0xFF666666), fontSize = 9.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.body, color = AetherText, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiSheet(
    post: MemePost,
    loading: Boolean,
    result: AiResult?,
    onAction: (AiAction) -> Unit,
    onSearchTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AetherSurface, contentColor = AetherText) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Text("Aether AI", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(post.title, fontSize = 12.sp, color = AetherMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiChoice("Caption", AiAction.CAPTION, Modifier.weight(1f), onAction)
                AiChoice("Explain", AiAction.EXPLAIN, Modifier.weight(1f), onAction)
                AiChoice("Tags", AiAction.TAGS, Modifier.weight(1f), onAction)
                AiChoice("Similar", AiAction.SIMILAR, Modifier.weight(1f), onAction)
            }
            Spacer(Modifier.height(18.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
                result != null -> {
                    if (result.text.isNotBlank()) Text(result.text, fontSize = 14.sp, lineHeight = 20.sp)
                    if (result.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            result.tags.forEach { tag -> AssistChip(onClick = { onSearchTag(tag) }, label = { Text(tag) }) }
                        }
                    }
                }
                else -> Text("Pick what you want Aether to do with this meme.", color = AetherMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun AiChoice(label: String, action: AiAction, modifier: Modifier = Modifier, onAction: (AiAction) -> Unit) {
    Button(
        onClick = { onAction(action) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AetherRaised, contentColor = AetherText),
    ) { Text(label, fontSize = 10.sp, maxLines = 1) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    settings: AppSettings,
    onIncludeVideos: (Boolean) -> Unit,
    onAutoplay: (Boolean) -> Unit,
    onSort: (SortMode) -> Unit,
    onAiBaseUrl: (String) -> Unit,
    onRedditClientId: (String) -> Unit,
    onClearSeen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var aiUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var redditClientId by remember(settings.redditClientId) { mutableStateOf(settings.redditClientId) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AetherSurface, contentColor = AetherText) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Text("Reddit access", color = AetherMuted, fontSize = 11.sp)
            OutlinedTextField(
                value = redditClientId,
                onValueChange = { redditClientId = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Installed-app Client ID") },
                trailingIcon = { TextButton(onClick = { onRedditClientId(redditClientId) }) { Text("Save") } },
            )
            Text("Required because Reddit no longer serves anonymous JSON feeds. No Reddit client secret is stored in Aether.", color = Color(0xFF777777), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(18.dp))
            Text("Sort", color = AetherMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { mode ->
                    FilterChip(selected = settings.sortMode == mode, onClick = { onSort(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingSwitch("Include videos", "Off by default. Images and GIFs stay in the normal feed.", settings.includeVideos, onIncludeVideos)
            if (settings.includeVideos) SettingSwitch("Autoplay videos", "Videos start muted.", settings.autoplayVideos, onAutoplay)
            Spacer(Modifier.height(14.dp))
            Text("AI server", color = AetherMuted, fontSize = 11.sp)
            OutlinedTextField(
                value = aiUrl,
                onValueChange = { aiUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://your-aether-server.example") },
                trailingIcon = { TextButton(onClick = { onAiBaseUrl(aiUrl) }) { Text("Save") } },
            )
            Text("Reddit browsing, comments and downloads work without the AI server.", color = Color(0xFF777777), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onClearSeen) { Text("Clear seen history") }
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, color = AetherMuted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SavedSheet(posts: List<MemePost>, imageLoader: ImageLoader, onToggleSave: (MemePost) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AetherSurface, contentColor = AetherText) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saved", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${posts.size}", color = AetherMuted)
            }
            Spacer(Modifier.height(10.dp))
            if (posts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing saved yet", color = AetherMuted) }
            else LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(posts, key = { it.id }) { post ->
                    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(AetherBlack).combinedClickable(onClick = {}, onLongClick = { onToggleSave(post) })) {
                        AsyncImage(post.posterUrl ?: post.mediaUrl, null, imageLoader = imageLoader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(190.dp))
                        IconButton(onClick = { onToggleSave(post) }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Bookmark, "Remove", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
