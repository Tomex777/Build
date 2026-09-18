package com.night.pahebatcher.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.night.pahebatcher.data.AnimeDetails
import com.night.pahebatcher.data.AnimeSearchResult
import com.night.pahebatcher.data.EpisodeInfo
import com.night.pahebatcher.data.SessionSnapshot
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF0B0B0D)
private val Elevated = Color(0xFF151518)
private val Elevated2 = Color(0xFF1C1C20)
private val TextMain = Color(0xFFF4F1EE)
private val TextMuted = Color(0xFF9C9997)
private val Accent = Color(0xFFFF6B57)
private val AccentSoft = Color(0xFF2B1816)
private val Success = Color(0xFF78D39B)
private val Error = Color(0xFFFF8E80)
private val Divider = Color(0xFF28282D)

private val PaheColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF250603),
    background = Bg,
    onBackground = TextMain,
    surface = Elevated,
    onSurface = TextMain,
    surfaceVariant = Elevated2,
    onSurfaceVariant = TextMuted,
    error = Error,
)

@Composable
fun PaheApp(vm: PaheViewModel) {
    MaterialTheme(colorScheme = PaheColors) {
        when {
            vm.verificationActive -> VerificationScreen(vm)
            vm.details != null || vm.detailsLoading || vm.detailsError != null -> DetailHost(vm)
            else -> RootTabs(vm)
        }
    }
}

@Composable
private fun RootTabs(vm: PaheViewModel) {
    BackHandler(enabled = vm.tab != MainTab.EXPLORE) {
        vm.navigateToTab(MainTab.EXPLORE)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Surface(
                color = Color(0xFF101012),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(
                        selected = vm.tab == MainTab.EXPLORE,
                        label = "Explore",
                        icon = { Icon(Icons.Rounded.Explore, null) },
                        modifier = Modifier.weight(1f),
                        onClick = { vm.navigateToTab(MainTab.EXPLORE) },
                    )
                    NavItem(
                        selected = vm.tab == MainTab.DOWNLOADS,
                        label = "Downloads",
                        icon = { Icon(Icons.Rounded.Download, null) },
                        modifier = Modifier.weight(1f),
                        onClick = { vm.navigateToTab(MainTab.DOWNLOADS) },
                    )
                    NavItem(
                        selected = vm.tab == MainTab.SETTINGS,
                        label = "Settings",
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        modifier = Modifier.weight(1f),
                        onClick = { vm.navigateToTab(MainTab.SETTINGS) },
                    )
                }
            }
        },
    ) { padding ->
        when (vm.tab) {
            MainTab.EXPLORE -> ExploreScreen(vm, padding)
            MainTab.DOWNLOADS -> DownloadsScreen(vm, padding)
            MainTab.SETTINGS -> SettingsScreen(vm, padding)
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = if (selected) AccentSoft else Color.Transparent,
            contentColor = if (selected) Accent else TextMuted,
            shape = CircleShape,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) TextMain else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ExploreScreen(vm: PaheViewModel, padding: PaddingValues) {
    val focus = LocalFocusManager.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PaheBatcher",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Find it. Keep it.",
                        color = TextMain,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    color = if (vm.sessions.animeCookieSaved) Color(0xFF142019) else Elevated2,
                    shape = CircleShape,
                ) {
                    Text(
                        if (vm.sessions.animeCookieSaved) "SESSION READY" else "VERIFY",
                        color = if (vm.sessions.animeCookieSaved) Success else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search AnimePahe", color = TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (vm.searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Accent,
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focus.clearFocus()
                        vm.submitSearch()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Divider,
                    focusedContainerColor = Elevated,
                    unfocusedContainerColor = Elevated,
                    cursorColor = Accent,
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                ),
            )
        }

        vm.searchError?.let { message ->
            item {
                MessageCard(
                    message = message,
                    action = if (message.contains("verification", true)) "Open verification" else null,
                    onAction = vm::startVerification,
                )
            }
        }

        if (!vm.sessions.animeCookieSaved && vm.results.isEmpty() && vm.query.isBlank()) {
            item {
                Surface(
                    color = Elevated,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Browser verification", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "AnimePahe and Kwik keep separate browser cookies. Run the two-step check whenever either one expires.",
                            color = TextMuted,
                            lineHeight = 20.sp,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = vm::startVerification,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Open verification browser", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (vm.results.isNotEmpty()) {
            item {
                Text(
                    "Results",
                    color = TextMain,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(vm.results, key = { it.session }) { anime ->
                AnimeResultRow(anime, onClick = { vm.openAnime(anime) })
            }
        } else if (vm.query.isBlank() && vm.sessions.animeCookieSaved) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Search the live catalog", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No placeholder shows. What you see here comes from AnimePahe.",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeResultRow(anime: AnimeSearchResult, onClick: () -> Unit) {
    Surface(
        color = Elevated,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(
                url = anime.poster,
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    anime.title,
                    color = TextMain,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                val meta = buildList {
                    anime.type.takeIf { it.isNotBlank() }?.let(::add)
                    if (anime.episodes > 0) add("${anime.episodes} eps")
                    anime.status.takeIf { it.isNotBlank() }?.let(::add)
                }.joinToString("  ·  ")
                Text(meta.ifBlank { "Anime" }, color = TextMuted, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
private fun Poster(url: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) {
        Box(modifier.background(Elevated2), contentAlignment = Alignment.Center) {
            Text("PAHE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Elevated2),
        )
    }
}

@Composable
private fun DetailHost(vm: PaheViewModel) {
    val current = vm.details
    if (current != null) {
        DetailScreen(vm, current)
        return
    }

    BackHandler(onBack = vm::closeDetails)
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        if (vm.detailsLoading) {
            CircularProgressIndicator(color = Accent)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(20.dp),
            ) {
                IconButton(onClick = vm::closeDetails) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = TextMain)
                }
                Spacer(Modifier.height(40.dp))
                MessageCard(vm.detailsError ?: "Could not open this anime.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(vm: PaheViewModel, details: AnimeDetails) {
    var sheetEpisode by remember { mutableStateOf<EpisodeInfo?>(null) }

    BackHandler(onBack = vm::closeDetails)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .background(Elevated),
                ) {
                    Poster(
                        details.result.poster,
                        Modifier
                            .fillMaxSize()
                            .background(Elevated),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.48f)),
                    )
                    IconButton(
                        onClick = vm::closeDetails,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = TextMain)
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                    ) {
                        Text(
                            details.result.title,
                            color = Color.White,
                            fontSize = 27.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            buildList {
                                details.result.type.takeIf { it.isNotBlank() }?.let(::add)
                                if (details.episodes.isNotEmpty()) {
                                    add("${details.episodes.map { it.number }.distinct().size} episodes")
                                } else if (details.result.episodes > 0) {
                                    add("${details.result.episodes} episodes")
                                }
                                details.result.status.takeIf { it.isNotBlank() }?.let(::add)
                            }.joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Episodes", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (vm.detailsLoading) "Loading…" else "Tap to download",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            if (vm.detailsLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = Accent,
                        trackColor = Elevated2,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            vm.detailsError?.let { message ->
                item {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        MessageCard(
                            message = message,
                            action = "Retry",
                            onAction = vm::retryDetails,
                        )
                    }
                }
            }

            if (!vm.detailsLoading && vm.detailsError == null && details.episodes.isEmpty()) {
                item {
                    Text(
                        "No episodes were returned for this title.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            items(
                details.episodes,
                key = { "${it.session}_${it.audio}" },
            ) { episode ->
                EpisodeRow(episode, onClick = { sheetEpisode = episode })
            }
        }
    }

    sheetEpisode?.let { episode ->
        EpisodeDownloadSheet(
            episode = episode,
            onDismiss = { sheetEpisode = null },
            onDownload = { quality, audio ->
                vm.downloadEpisode(episode, quality, audio)
                sheetEpisode = null
                vm.navigateToTab(MainTab.DOWNLOADS)
            },
        )
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Elevated2,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(episode.epLabel, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.title.ifBlank { "Episode ${episode.epLabel}" },
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                listOfNotNull(
                    episode.fansub.takeIf { it.isNotBlank() },
                    if (episode.audio == "eng") "DUB" else "SUB",
                ).joinToString("  ·  "),
                color = if (episode.audio == "eng") Accent else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Icon(Icons.Rounded.Download, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 82.dp)
            .height(1.dp)
            .background(Divider),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDownloadSheet(
    episode: EpisodeInfo,
    onDismiss: () -> Unit,
    onDownload: (Int, String) -> Unit,
) {
    var quality by remember(episode.session) { mutableStateOf(1080) }
    var audio by remember(episode.session) { mutableStateOf(episode.audio) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Elevated,
        contentColor = TextMain,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 22.dp),
        ) {
            Text("Episode ${episode.epLabel}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                episode.title.ifBlank { "Choose a release" },
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(22.dp))
            Text("Quality", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1080, 720, 360).forEach { option ->
                    FilterChip(
                        selected = quality == option,
                        onClick = { quality = option },
                        label = { Text("${option}p") },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Audio", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = audio == "jpn",
                    onClick = { audio = "jpn" },
                    label = { Text("SUB") },
                )
                FilterChip(
                    selected = audio == "eng",
                    onClick = { audio = "eng" },
                    label = { Text("DUB") },
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onDownload(quality, audio) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Download episode", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadsScreen(vm: PaheViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Downloads", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Saved files go to Downloads/PaheBatcher.", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
        }

        if (vm.downloads.isEmpty()) {
            item {
                Surface(color = Elevated, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Nothing queued", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("Open an anime and tap an episode to choose quality and audio.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(vm.downloads, key = { it.id }) { item ->
                Surface(color = Elevated, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.animeTitle,
                                    color = TextMain,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Episode ${item.episode}  ·  ${item.quality}p  ·  ${if (item.audio == "eng") "DUB" else "SUB"}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            if (item.progress >= 1f && !item.failed) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = Success)
                            } else if (item.failed) {
                                IconButton(onClick = { vm.removeDownload(item.id) }) {
                                    Icon(Icons.Rounded.Close, null, tint = Error)
                                }
                            }
                        }
                        Spacer(Modifier.height(13.dp))
                        LinearProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = if (item.failed) Error else Accent,
                            trackColor = Elevated2,
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            item.status,
                            color = if (item.failed) Error else TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: PaheViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Settings", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Browser sessions are manual by design.", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
        }
        item {
            VerificationCard(vm.sessions, onVerify = vm::startVerification)
        }
        item {
            Surface(color = Elevated, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("About this build", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Native Android port of PaheBatcher 3.4.0's AnimePahe → Kwik → HLS pipeline. Finished streams are remuxed to MP4 with Android's media stack when possible, with a transport-stream fallback.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
        if (vm.sessions.animeCookieSaved || vm.sessions.kwikCookieSaved) {
            item {
                TextButton(onClick = vm::clearVerificationSessions) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Error)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear saved browser sessions", color = Error)
                }
            }
        }
    }
}

@Composable
private fun VerificationCard(
    sessions: SessionSnapshot,
    onVerify: () -> Unit,
) {
    Surface(color = Elevated, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Web verification", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(
                "Run both stages whenever either Cloudflare cookie expires.",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            SessionRow(
                title = "AnimePahe",
                host = sessions.animeHost.ifBlank { "Not saved" },
                saved = sessions.animeCookieSaved,
                updatedAt = sessions.animeUpdatedAt,
            )
            Spacer(Modifier.height(13.dp))
            SessionRow(
                title = "Kwik",
                host = sessions.kwikHost.ifBlank { "Not saved" },
                saved = sessions.kwikCookieSaved,
                updatedAt = sessions.kwikUpdatedAt,
            )
            Spacer(Modifier.height(19.dp))
            Button(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Text("Open verification browser", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SessionRow(title: String, host: String, saved: Boolean, updatedAt: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (saved) Color(0xFF142019) else Elevated2,
            shape = CircleShape,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (saved) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Success, modifier = Modifier.size(19.dp))
                } else {
                    Text("—", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(host, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (saved) formatSaved(updatedAt) else "Not verified",
            color = if (saved) Success else TextMuted,
            fontSize = 10.sp,
        )
    }
}

private fun formatSaved(time: Long): String {
    if (time <= 0L) return "Saved"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VerificationScreen(vm: PaheViewModel) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    BackHandler {
        val browser = webView
        if (vm.verifyStage != VerifyStage.PREPARING_SECOND && browser?.canGoBack() == true) {
            browser.goBack()
        } else {
            vm.closeVerification()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeVerification) {
                Icon(Icons.Rounded.Close, null, tint = TextMain)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (vm.verifyStage) {
                        VerifyStage.ANIMEPAHE -> "Step 1 of 2 · AnimePahe"
                        VerifyStage.PREPARING_SECOND -> "Preparing step 2"
                        VerifyStage.KWIK -> "Step 2 of 2 · Kwik"
                    },
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (vm.verifyStage) {
                        VerifyStage.ANIMEPAHE -> "Complete the browser check, then confirm below."
                        VerifyStage.PREPARING_SECOND -> "Using the AnimePahe session to open a real episode."
                        VerifyStage.KWIK -> "Complete the second browser check, then confirm."
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            IconButton(
                enabled = vm.verifyStage != VerifyStage.PREPARING_SECOND,
                onClick = { webView?.reload() },
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = TextMuted)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White),
        ) {
            AndroidView(
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = settings.userAgentString
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webView = this
                    }
                },
                update = { view ->
                    val target = vm.verifyUrl
                    if (
                        target.isNotBlank() &&
                        vm.verifyStage != VerifyStage.PREPARING_SECOND &&
                        view.tag != target
                    ) {
                        view.tag = target
                        if (vm.verifyStage == VerifyStage.KWIK) {
                            val animeHost = vm.sessions.animeHost.ifBlank { "animepahe.pw" }
                            view.loadUrl(target, mapOf("Referer" to "https://$animeHost/"))
                        } else {
                            view.loadUrl(target)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (vm.verifyStage == VerifyStage.PREPARING_SECOND) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Bg.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(14.dp))
                        Text("Opening Kwik…", color = TextMain, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        vm.verifyError?.let {
            Text(
                it,
                color = Error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF241311))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        Surface(color = Elevated, tonalElevation = 0.dp) {
            Button(
                enabled = vm.verifyStage != VerifyStage.PREPARING_SECOND,
                onClick = {
                    val page = webView?.url.orEmpty().ifBlank { vm.verifyUrl }
                    val cookie = CookieManager.getInstance().getCookie(page).orEmpty()
                    val ua = webView?.settings?.userAgentString.orEmpty()
                    CookieManager.getInstance().flush()
                    vm.completeVerificationStep(page, cookie, ua)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(14.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Text(
                    when (vm.verifyStage) {
                        VerifyStage.ANIMEPAHE -> "I’ve completed AnimePahe"
                        VerifyStage.PREPARING_SECOND -> "Opening second verification…"
                        VerifyStage.KWIK -> "I’ve completed Kwik"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        color = Color(0xFF211513),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = Error, fontSize = 13.sp, lineHeight = 19.sp)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(action, color = Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
