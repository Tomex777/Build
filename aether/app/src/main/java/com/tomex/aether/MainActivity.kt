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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AetherTheme { AetherApp() } }
    }
}

internal val AetherBlack = Color.Black
internal val AetherSurface = Color(0xFF101010)
internal val AetherRaised = Color(0xFF181818)
internal val AetherText = Color(0xFFF2F2F2)
internal val AetherMuted = Color(0xFF999999)
internal val AetherAccent = Color(0xFF8B7CFF)

@Composable
internal fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AetherBlack,
            surface = AetherSurface,
            surfaceVariant = AetherRaised,
            primary = AetherAccent,
            onBackground = AetherText,
            onSurface = AetherText,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AetherApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSaved by rememberSaveable { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<FeedCategory?>(null) }
    var aiPost by remember { mutableStateOf<MemePost?>(null) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    LaunchedEffect(listState, state.posts) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { infos ->
                infos.mapNotNull { info ->
                    val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    val visibleStart = maxOf(info.offset, listState.layoutInfo.viewportStartOffset)
                    val visibleEnd = minOf(info.offset + info.size, listState.layoutInfo.viewportEndOffset)
                    val fraction = if (info.size == 0) 0f else (visibleEnd - visibleStart).coerceAtLeast(0).toFloat() / info.size
                    info.index.takeIf { fraction >= .60f && viewport > 0 }?.let { state.posts.getOrNull(it)?.id }
                }
            }
            .distinctUntilChanged()
            .collect { ids -> ids.forEach(vm::markSeen) }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= state.posts.lastIndex - 5) vm.loadMore() }
    }

    Scaffold(
        containerColor = AetherBlack,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .background(AetherBlack)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            AetherHeader(
                seenCount = state.seenIds.size,
                savedCount = state.savedPosts.size,
                onSaved = { showSaved = true },
                onSettings = { showSettings = true },
            )
            CategoryPills(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = vm::selectCategory,
                onEdit = { editingCategory = it },
                onAdd = { editingCategory = vm.newCategory() },
            )

            when {
                state.loading && state.posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AetherAccent, strokeWidth = 2.dp)
                }
                state.error != null && state.posts.isEmpty() -> EmptyState(state.error!!, vm::reload)
                else -> MemeFeed(
                    state = state,
                    listState = listState,
                    imageLoader = imageLoader,
                    onToggle = vm::togglePostActions,
                    onSave = vm::toggleSave,
                    onDismiss = vm::dismiss,
                    onComments = vm::openComments,
                    onAi = { aiPost = it },
                    onDownload = { post ->
                        runCatching { DownloadHelper.enqueue(context, post) }
                            .onSuccess { vm.postMessage("Download started") }
                            .onFailure { vm.postMessage(it.message ?: "Download failed") }
                    },
                    onShare = { post -> sharePost(context, post) },
                    onOpenSource = { post -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.permalink))) },
                )
            }
        }
    }

    if (state.commentsPost != null) {
        CommentsSheet(
            post = state.commentsPost!!,
            comments = state.comments.take(state.visibleCommentCount),
            totalLoaded = state.comments.size,
            loading = state.commentsLoading,
            onNearEnd = vm::revealMoreComments,
            onDismiss = vm::closeComments,
        )
    }

    if (aiPost != null) {
        AiSheet(
            post = aiPost!!,
            loading = state.aiLoading,
            result = state.aiResult,
            onAction = { vm.runAi(aiPost!!, it) },
            onDismiss = { aiPost = null; vm.clearAiResult() },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onIncludeVideos = vm::setIncludeVideos,
            onAutoplay = vm::setAutoplayVideos,
            onSort = vm::setSortMode,
            onAiBaseUrl = vm::setAiBaseUrl,
            onClearSeen = vm::clearSeen,
            onDismiss = { showSettings = false },
        )
    }

    if (showSaved) {
        SavedSheet(
            posts = state.savedPosts,
            imageLoader = imageLoader,
            onToggleSave = vm::toggleSave,
            onDismiss = { showSaved = false },
        )
    }

    editingCategory?.let { category ->
        CategoryEditorSheet(
            initial = category,
            discovery = state.discoveryCandidates,
            discovering = state.discovering,
            onValidate = vm::validateSubreddit,
            onDiscover = vm::discoverSubreddits,
            onSave = { vm.saveCategory(it); editingCategory = null; vm.clearDiscovery() },
            onDelete = {
                vm.deleteCategory(category)
                editingCategory = null
                vm.clearDiscovery()
            },
            onDismiss = { editingCategory = null; vm.clearDiscovery() },
        )
    }
}
