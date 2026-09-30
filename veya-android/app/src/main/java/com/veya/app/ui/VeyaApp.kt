package com.veya.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veya.app.BuildConfig
import com.veya.app.VeyaApplication
import com.veya.app.player.VeyaPlayerActivity
import com.veya.app.downloads.VeyaDownload
import com.veya.app.downloads.VeyaDownloadState
import com.veya.app.youtube.YouTubeUrlParser
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.api.VideoDetails
import kotlinx.coroutines.launch
import java.util.Locale

private enum class MainTab { Home, Search, Library, Downloads, Settings }

@Composable
fun VeyaApp(initialUrl: String) {
    VeyaTheme {
        val context = LocalContext.current
        var tab by remember { mutableStateOf(MainTab.Home) }
        var detailsVideoId by remember { mutableStateOf<String?>(null) }
        var aboutOpen by remember { mutableStateOf(false) }
        val incomingId = remember(initialUrl) { YouTubeUrlParser.videoId(initialUrl) }

        LaunchedEffect(incomingId) {
            if (incomingId != null) {
                aboutOpen = false
                detailsVideoId = incomingId
            }
        }

        if (aboutOpen) {
            AboutScreen(onBack = { aboutOpen = false })
            return@VeyaTheme
        }

        val selectedVideo = detailsVideoId
        if (selectedVideo != null) {
            VideoDetailsScreen(
                videoId = selectedVideo,
                onBack = { detailsVideoId = null },
                onPlay = { id, title ->
                    context.startActivity(VeyaPlayerActivity.intent(context, id, title))
                }
            )
            return@VeyaTheme
        }

        Scaffold(
            bottomBar = {
                NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                    NavigationBarItem(
                        selected = tab == MainTab.Home,
                        onClick = { tab = MainTab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Search,
                        onClick = { tab = MainTab.Search },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Library,
                        onClick = { tab = MainTab.Library },
                        icon = {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null
                            )
                        },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Downloads,
                        onClick = { tab = MainTab.Downloads },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Settings,
                        onClick = { tab = MainTab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            when (tab) {
                MainTab.Home -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    openSearch = { tab = MainTab.Search },
                    openVideo = { detailsVideoId = it }
                )
                MainTab.Search -> SearchScreen(
                    modifier = Modifier.padding(padding),
                    openVideo = { detailsVideoId = it }
                )
                MainTab.Library -> LibraryScreen(
                    modifier = Modifier.padding(padding),
                    openVideo = { detailsVideoId = it }
                )
                MainTab.Downloads -> DownloadsScreen(Modifier.padding(padding))
                MainTab.Settings -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    openAbout = { aboutOpen = true }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    openSearch: () -> Unit,
    openVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VeyaApplication
    val history by app.history.items.collectAsState()
    var query by remember { mutableStateOf("") }
    val continueWatching = history.filter { !it.completed && it.positionMs > 0L }.take(8)
    val watchAgain = history.filter { it.completed }.take(6)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Veya",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search videos") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (query.isNotBlank()) {
                                app.getSharedPreferences(
                                    "veya_ui",
                                    Context.MODE_PRIVATE
                                )
                                    .edit()
                                    .putString("pending_search", query.trim())
                                    .apply()
                                openSearch()
                            }
                        },
                        enabled = query.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "Continue watching",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(continueWatching, key = { "continue-${it.videoId}" }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openVideo(entry.videoId) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column {
                        entry.thumbnail?.let { image ->
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            )
                        }
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entry.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.durationMs > 0L) {
                                LinearProgressIndicator(
                                    progress = {
                                        (entry.positionMs.toFloat() / entry.durationMs)
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (watchAgain.isNotEmpty()) {
            item {
                Text(
                    text = "Watch again",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(watchAgain, key = { "again-${it.videoId}" }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openVideo(entry.videoId) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    openVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VeyaApplication
    val repository = app.youtubeRepository
    val prefs = remember { context.getSharedPreferences("veya_ui", Context.MODE_PRIVATE) }
    val initial = remember { prefs.getString("pending_search", "").orEmpty() }
    var query by remember { mutableStateOf(initial) }
    val results = remember { mutableStateListOf<SearchResult.Video>() }
    var continuation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch(loadMore: Boolean = false) {
        val term = query.trim()
        if (term.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            runCatching {
                repository.searchVideos(term, if (loadMore) continuation else null)
            }.onSuccess { page ->
                if (!loadMore) results.clear()
                page.videos.forEach { video ->
                    if (results.none { it.id == video.id }) results.add(video)
                }
                continuation = page.continuation
                prefs.edit().remove("pending_search").apply()
            }.onFailure {
                error = friendlyError(it)
            }
            loading = false
        }
    }

    LaunchedEffect(initial) {
        if (initial.isNotBlank()) runSearch()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search videos") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = { runSearch() },
                    enabled = query.isNotBlank() && !loading
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        }

        error?.let { message ->
            item { MessageCard(message) }
        }

        items(results, key = { it.id }) { video ->
            VideoResultCard(video = video, onClick = { openVideo(video.id) })
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (continuation != null && results.isNotEmpty()) {
            item {
                FilledTonalButton(
                    onClick = { runSearch(loadMore = true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun VideoResultCard(
    video: SearchResult.Video,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            video.thumbnail?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        video.channel,
                        video.durationSeconds?.let(::formatSeconds)
                    ).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun VideoDetailsScreen(
    videoId: String,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VeyaApplication
    val repository = app.youtubeRepository
    val downloads by app.downloads.items.collectAsState()
    val download = downloads.firstOrNull { it.videoId == videoId }
    var details by remember(videoId) { mutableStateOf<VideoDetails?>(null) }
    var error by remember(videoId) { mutableStateOf<String?>(null) }
    var loading by remember(videoId) { mutableStateOf(true) }

    LaunchedEffect(videoId) {
        loading = true
        error = null
        runCatching {
            repository.videoDetails(videoId)
        }.onSuccess {
            details = it
        }.onFailure {
            error = friendlyError(it)
        }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        error?.let { message ->
            item {
                Box(Modifier.padding(horizontal = 18.dp)) {
                    MessageCard(message)
                }
            }
        }

        details?.let { video ->
            item {
                video.thumbnails.lastOrNull()?.let { image ->
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listOfNotNull(
                            video.channel,
                            video.durationSeconds?.let { formatSeconds(it.toInt()) }
                        ).joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onPlay(video.id, video.title) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (download?.state == VeyaDownloadState.COMPLETE) "Play offline" else "Play")
                    }

                    when (download?.state) {
                        null -> FilledTonalButton(
                            onClick = {
                                val height = context
                                    .getSharedPreferences("veya_settings", Context.MODE_PRIVATE)
                                    .getInt("default_quality", 720)
                                app.downloadController.enqueue(
                                    videoId = video.id,
                                    title = video.title,
                                    thumbnail = video.thumbnails.lastOrNull(),
                                    preferredHeight = height
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Download")
                        }

                        VeyaDownloadState.QUEUED,
                        VeyaDownloadState.DOWNLOADING -> {
                            if ((download?.totalBytes ?: 0L) > 0L) {
                                LinearProgressIndicator(
                                    progress = { download?.progress ?: 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            FilledTonalButton(
                                onClick = { app.downloadController.pause(video.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pause download")
                            }
                        }

                        VeyaDownloadState.PAUSED -> FilledTonalButton(
                            onClick = { app.downloadController.resume(video.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resume download")
                        }

                        VeyaDownloadState.FAILED -> FilledTonalButton(
                            onClick = { app.downloadController.retry(video.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry download")
                        }

                        VeyaDownloadState.COMPLETE -> Text(
                            text = "Available offline",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (video.chapters.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        video.chapters.take(8).forEach { chapter ->
                            Text("${clock(chapter.startMs)}  ${chapter.title}")
                        }
                    }
                }
            }
            video.description
                ?.takeIf { it.isNotBlank() }
                ?.let { description ->
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = description,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    openVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VeyaApplication
    val history by app.history.items.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "Videos you watch will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(history, key = { it.videoId }) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openVideo(entry.videoId) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    entry.thumbnail?.let { image ->
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            modifier = Modifier.size(132.dp, 74.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (entry.completed) {
                                "Watched"
                            } else if (entry.durationMs > 0L) {
                                "${clock(entry.positionMs)} / ${clock(entry.durationMs)}"
                            } else {
                                "Continue watching"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!entry.completed && entry.durationMs > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (entry.positionMs.toFloat() / entry.durationMs)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as VeyaApplication
    val downloads by app.downloads.items.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        if (downloads.isEmpty()) {
            item {
                Text(
                    text = "Videos you download will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(downloads, key = { it.videoId }) { item ->
            DownloadCard(
                item = item,
                onOpen = {
                    if (
                        item.state == VeyaDownloadState.COMPLETE &&
                        item.videoPath != null &&
                        item.audioPath != null
                    ) {
                        context.startActivity(
                            VeyaPlayerActivity.intent(context, item.videoId, item.title)
                        )
                    }
                },
                onPause = { app.downloadController.pause(item.videoId) },
                onResume = { app.downloadController.resume(item.videoId) },
                onRetry = { app.downloadController.retry(item.videoId) },
                onDelete = { app.downloadController.delete(item.videoId) }
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: VeyaDownload,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            when (item.state) {
                VeyaDownloadState.QUEUED -> Text("Waiting to download")
                VeyaDownloadState.DOWNLOADING -> {
                    Text("Downloading")
                    if ((item.totalBytes ?: 0L) > 0L) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                VeyaDownloadState.PAUSED -> Text("Paused")
                VeyaDownloadState.COMPLETE -> Text("Downloaded")
                VeyaDownloadState.FAILED -> Text("Download failed")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.state) {
                    VeyaDownloadState.QUEUED,
                    VeyaDownloadState.DOWNLOADING -> FilledTonalButton(onClick = onPause) {
                        Text("Pause")
                    }
                    VeyaDownloadState.PAUSED -> FilledTonalButton(onClick = onResume) {
                        Text("Resume")
                    }
                    VeyaDownloadState.FAILED -> FilledTonalButton(onClick = onRetry) {
                        Text("Retry")
                    }
                    VeyaDownloadState.COMPLETE -> FilledTonalButton(onClick = onOpen) {
                        Text("Play")
                    }
                }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, openAbout: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("veya_settings", Context.MODE_PRIVATE)
    }
    var defaultQuality by remember {
        mutableStateOf(prefs.getInt("default_quality", 720))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Default quality", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(360, 720, 1080).forEach { height ->
                            FilledTonalButton(
                                onClick = {
                                    defaultQuality = height
                                    prefs.edit()
                                        .putInt("default_quality", height)
                                        .apply()
                                }
                            ) {
                                Text(
                                    if (defaultQuality == height) "✓ ${height}p"
                                    else "${height}p"
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            FilledTonalButton(
                onClick = openAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("About Veya")
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "About Veya",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Veya",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Playback",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Veya uses VLC/libVLC for video playback.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun friendlyError(t: Throwable): String = when (t) {
    is ResolverFailure.ChallengeRequired ->
        "YouTube asked for additional verification. Try again later."
    is ResolverFailure.SignInRequired ->
        "This video requires an account session."
    is ResolverFailure.VideoUnavailable ->
        "This video is unavailable."
    is ResolverFailure.RateLimited ->
        "Too many requests right now. Try again shortly."
    is ResolverFailure.NetworkFailure,
    is ResolverFailure.TransientNetworkFailure ->
        "Couldn't connect. Check your connection and try again."
    else ->
        "Something went wrong. Try again."
}

private fun formatSeconds(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secondsOnly = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secondsOnly)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secondsOnly)
    }
}

private fun clock(ms: Long): String =
    formatSeconds((ms / 1000L).toInt())
