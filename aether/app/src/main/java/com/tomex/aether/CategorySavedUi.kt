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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditorSheet(
    initial: FeedCategory,
    discovery: List<SubredditCandidate>,
    discovering: Boolean,
    onValidate: (String, (SubredditCandidate?) -> Unit) -> Unit,
    onDiscover: (String) -> Unit,
    onSave: (FeedCategory) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var subs by remember(initial.id) { mutableStateOf(initial.subreddits) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags) }
    var subInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var discoveryPrompt by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AetherSurface, contentColor = AetherText) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(.90f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (initial.subreddits.isEmpty() && initial.name == "New") "New category" else "Edit ${initial.name}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                Text("Subreddits", color = AetherMuted, fontSize = 11.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subs.forEach { sub -> AssistChip(onClick = { subs = subs.filterNot { it.equals(sub, true) } }, label = { Text("r/$sub  ×") }) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subInput,
                        onValueChange = { subInput = it.removePrefix("r/") },
                        modifier = Modifier.weight(1f),
                        label = { Text("Add subreddit") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (subInput.isNotBlank() && !validating) {
                            validating = true; validationMessage = null
                            onValidate(subInput) { candidate ->
                                validating = false
                                if (candidate == null) validationMessage = "Could not verify r/${subInput.trim()}"
                                else {
                                    subs = (subs + candidate.name).distinctBy { it.lowercase() }
                                    validationMessage = "Verified r/${candidate.name}"
                                    subInput = ""
                                }
                            }
                        }
                    }) { if (validating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Verify") }
                }
                validationMessage?.let { Text(it, color = if (it.startsWith("Verified")) Color(0xFF7ED8A1) else Color(0xFFFF8A80), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(18.dp))
                Text("Tags / keywords", color = AetherMuted, fontSize = 11.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> AssistChip(onClick = { tags = tags - tag }, label = { Text("$tag  ×") }) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, modifier = Modifier.weight(1f), label = { Text("Add tag") }, singleLine = true)
                    IconButton(onClick = {
                        val clean = tagInput.trim()
                        if (clean.isNotBlank()) { tags = (tags + clean).distinct(); tagInput = "" }
                    }) { Icon(Icons.Default.Add, "Add tag") }
                }
                Spacer(Modifier.height(22.dp))
                Text("Find with AI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Aether discovers candidates, verifies that they exist, samples recent posts, then ranks the useful ones.", color = AetherMuted, fontSize = 10.sp)
                OutlinedTextField(
                    value = discoveryPrompt,
                    onValueChange = { discoveryPrompt = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    placeholder = { Text("e.g. stupid cat memes") },
                    minLines = 2,
                )
                Button(onClick = { onDiscover(discoveryPrompt) }, enabled = discoveryPrompt.isNotBlank() && !discovering, modifier = Modifier.padding(top = 8.dp)) {
                    if (discovering) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (discovering) "Checking Reddit…" else "Discover & verify")
                }
                Spacer(Modifier.height(8.dp))
            }

            items(discovery, key = { it.name.lowercase() }) { candidate ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(10.dp)).background(AetherRaised).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("r/${candidate.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(candidate.title.ifBlank { candidate.description }, color = AetherMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("verified • ${(candidate.mediaFit * 100).roundToInt()}% image/GIF fit • ${candidate.recentPosts} sampled", color = Color(0xFF777777), fontSize = 9.sp)
                    }
                    Button(onClick = { subs = (subs + candidate.name).distinctBy { it.lowercase() } }, enabled = subs.none { it.equals(candidate.name, true) }) {
                        Text(if (subs.any { it.equals(candidate.name, true) }) "Added" else "Add")
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!initial.id.startsWith("custom-") || initial.subreddits.isNotEmpty()) {
                        TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Delete") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSave(initial.copy(name = name.trim().ifBlank { "Untitled" }, subreddits = subs, tags = tags)) },
                        enabled = subs.isNotEmpty(),
                    ) { Text("Save category") }
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = AetherMuted, fontSize = 13.sp)
            TextButton(onClick = retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Retry") }
        }
    }
}

internal fun compactNumber(value: Int): String = when {
    value >= 1_000_000 -> "%.1fm".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

internal fun sharePost(context: android.content.Context, post: MemePost) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${post.title}\n${post.mediaUrl}\n${post.permalink}")
    }
    context.startActivity(Intent.createChooser(intent, "Share meme"))
}

internal fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
