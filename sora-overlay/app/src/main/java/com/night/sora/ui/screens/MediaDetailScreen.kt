@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.isCatalogProvider
import com.night.sora.extension.isDiagnosticProvider
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionSessionContract
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.PlaybackSession
import com.night.sora.model.PlaybackStream
import com.night.sora.model.ReaderPage
import com.night.sora.model.ReaderSession
import com.night.sora.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

private data class DetailRow(val id: String, val title: String, val subtitle: String)

private data class DetailMetadata(
    val description: String,
    val status: String = "",
    val score: String = "",
    val genres: List<String> = emptyList(),
)

@Composable
fun MediaDetailScreen(
    selection: ExtensionMediaSelection,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpenReader: (ReaderSession) -> Unit,
    onOpenPlayer: (PlaybackSession) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sourcePrefs = remember(context) { context.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE) }
    val listState = rememberLazyListState()

    var active by remember(selection) { mutableStateOf(selection) }
    var consumption by remember(selection) { mutableStateOf<ExtensionMediaSelection?>(null) }
    var metadata by remember(selection) { mutableStateOf(DetailMetadata(selection.subtitle)) }
    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var movieStreams by remember { mutableStateOf<List<PlaybackStream>>(emptyList()) }
    var secondaryRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }
    var secondaryTitle by remember { mutableStateOf("") }
    var rowsLoading by remember { mutableStateOf(false) }
    var counterpart by remember { mutableStateOf<ExtensionMediaSelection?>(null) }
    var sourcePickerOpen by remember { mutableStateOf(false) }
    var sourceSearchBusy by remember { mutableStateOf(false) }
    var sourceSearchError by remember { mutableStateOf<String?>(null) }
    var browserSession by remember { mutableStateOf<SourceBrowserSession?>(null) }
    var browserBusy by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    var descending by remember(active.id, active.type) { mutableStateOf(active.type == ContentType.MANGA) }
    var descriptionExpanded by remember(active.id, active.type) { mutableStateOf(false) }

    val toolbarOpaque by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28 }
    }
    val sourceOptions = remember(extensions, active.type) { consumptionSourcesFor(extensions, active.type) }
    val activeDisplayExtension = extensions.firstOrNull { it.packageName == active.extensionPackage }
    val activeDisplaySource = activeDisplayExtension?.descriptor?.sources?.firstOrNull { it.id == active.sourceId }
    val consumptionExtension = consumption?.let { chosen -> extensions.firstOrNull { it.packageName == chosen.extensionPackage } }
    val consumptionSource = consumptionExtension?.descriptor?.sources?.firstOrNull { it.id == consumption?.sourceId }
    val displayedSourceName = consumptionSource?.name ?: activeDisplaySource?.name ?: activeDisplayExtension?.declaredName ?: "Source unavailable"
    val webViewAvailable = consumption != null && consumptionExtension != null && consumptionSource?.capabilities?.contains(ExtensionSessionContract.CAPABILITY_WEBVIEW) == true

    val openSession = browserSession
    if (openSession != null) {
        val browserExtension = extensions.firstOrNull { it.packageName == openSession.extensionPackage }
        if (browserExtension != null) {
            SourceWebViewScreen(
                session = openSession,
                extension = browserExtension,
                manager = manager,
                onBack = { browserSession = null; refreshEpoch++ },
            )
        } else {
            MissingExtensionScreen(onBack = { browserSession = null })
        }
        return
    }

    fun effectiveBack() {
        if (secondaryRows.isNotEmpty()) {
            secondaryRows = emptyList()
            secondaryTitle = ""
        } else {
            onBack()
        }
    }

    fun loadConsumptionRows(target: ExtensionMediaSelection?) {
        childRows = emptyList()
        movieStreams = emptyList()
        if (target == null) {
            rowsLoading = false
            return
        }
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage }
        if (ext == null) {
            rowsLoading = false
            return
        }
        val method = childMethod(target.type)
        rowsLoading = true
        manager.call(
            ext,
            method,
            JSONObject().put("sourceId", target.sourceId).put("id", target.id).toString(),
        ) { result ->
            if (target.type == ContentType.MOVIE) {
                movieStreams = result.getOrNull()?.let(::parsePlaybackStreams).orEmpty()
                childRows = movieStreams.mapIndexed { index, stream ->
                    DetailRow(
                        id = "movie-stream-$index",
                        title = stream.label.ifBlank { "Stream ${index + 1}" },
                        subtitle = stream.mimeType.orEmpty(),
                    )
                }
                if (movieStreams.isEmpty() && result.isFailure) {
                    playbackError = result.exceptionOrNull()?.message ?: "This source could not load movie streams."
                }
            } else {
                childRows = result.getOrNull()?.let { parseRows(target.type, it) }.orEmpty()
            }
            rowsLoading = false
        }
    }

    fun chooseSource(ext: InstalledExtension, source: SourceDescriptor, dismissOnSuccess: Boolean = true) {
        sourceSearchBusy = true
        sourceSearchError = null
        browserError = null
        readerError = null
        playbackError = null
        searchSourceSelection(active, ext, source, manager) { found ->
            sourceSearchBusy = false
            if (found == null) {
                sourceSearchError = "${source.name} did not return a match for ${active.title}."
            } else {
                consumption = found
                sourcePrefs.edit().putString(preferredSourceKey(active.type), "${ext.packageName}|${source.id}").apply()
                if (dismissOnSuccess) sourcePickerOpen = false
            }
        }
    }

    fun openBrowser() {
        val target = consumption ?: return
        val ext = consumptionExtension ?: return
        val source = consumptionSource ?: return
        if (ExtensionSessionContract.CAPABILITY_WEBVIEW !in source.capabilities) return
        browserBusy = true
        browserError = null
        val payload = JSONObject()
            .put("sourceId", target.sourceId)
            .put("id", target.id)
            .toString()
        manager.call(ext, ExtensionSessionContract.METHOD_BROWSER_SESSION, payload) { result ->
            browserBusy = false
            val session = result.getOrNull()?.let { parseBrowserSession(it, target, ext, source) }
            if (session != null) browserSession = session
            else browserError = result.exceptionOrNull()?.message ?: "This source could not open its browser session."
        }
    }

    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
        val index = requested.coerceIn(0, movieStreams.lastIndex)
        onOpenPlayer(
            PlaybackSession(
                title = active.title,
                episodeTitle = active.title,
                sourceName = consumptionSource?.name ?: consumptionExtension?.declaredName ?: "Movie source",
                streams = movieStreams,
                initialStream = index,
            )
        )
    }

    fun openChild(row: DetailRow) {
        val target = consumption ?: active.takeIf { selectionCanConsume(it, extensions) } ?: return
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage } ?: return
        readerError = null

        if (active.type == ContentType.MANGA) {
            rowsLoading = true
            manager.call(
                ext,
                ExtensionContract.Method.PAGES,
                JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
            ) { result ->
                rowsLoading = false
                val pages = result.getOrNull()?.let(::parseReaderPages).orEmpty()
                if (pages.isNotEmpty()) {
                    onOpenReader(
                        ReaderSession(
                            title = active.title,
                            chapterTitle = row.title,
                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
                        )
                    )
                } else {
                    readerError = result.exceptionOrNull()?.message
                        ?: "${ext.declaredName} returned no readable pages for ${row.title}."
                }
            }
            return
        }

        if (active.type == ContentType.ANIME || active.type == ContentType.TV) {
            rowsLoading = true
            manager.call(
                ext,
                ExtensionContract.Method.STREAMS,
                JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
            ) { result ->
                rowsLoading = false
                val streams = result.getOrNull()?.let(::parsePlaybackStreams).orEmpty()
                if (streams.isNotEmpty()) {
                    onOpenPlayer(
                        PlaybackSession(
                            title = active.title,
                            episodeTitle = row.title,
                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
                        )
                    )
                } else {
                    playbackError = result.exceptionOrNull()?.message
                        ?: "${ext.declaredName} returned no playable streams for ${row.title}."
                }
            }
        }
    }

    BackHandler { effectiveBack() }

    LaunchedEffect(active.id, active.sourceId, active.extensionPackage, refreshEpoch, extensions) {
        val requested = active
        metadata = DetailMetadata(active.subtitle)
        counterpart = null
        secondaryRows = emptyList()
        secondaryTitle = ""
        browserError = null
        readerError = null
        playbackError = null

        activeDisplayExtension?.let { ext ->
            manager.call(
                ext,
                ExtensionContract.Method.DETAILS,
                JSONObject().put("sourceId", requested.sourceId).put("id", requested.id).toString(),
            ) { result ->
                if (active.id == requested.id && active.type == requested.type) {
                    result.getOrNull()?.let { metadata = parseMetadata(it, requested.subtitle) }
                }
            }
        }

        if (requested.type == ContentType.ANIME || requested.type == ContentType.MANGA) {
            findCounterpart(requested, extensions, manager) { found ->
                if (active.id == requested.id && active.type == requested.type) counterpart = found
            }
        }

        if (selectionCanConsume(requested, extensions)) {
            consumption = requested
        } else {
            consumption = null
            val pref = sourcePrefs.getString(preferredSourceKey(requested.type), null)
            val option = pref?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { parts ->
                sourceOptions.firstOrNull { (ext, source) -> ext.packageName == parts[0] && source.id == parts[1] }
            }
            if (option != null) {
                searchSourceSelection(requested, option.first, option.second, manager) { found ->
                    if (found != null && active.id == requested.id && active.type == requested.type) consumption = found
                }
            }
        }
    }

    LaunchedEffect(consumption?.id, consumption?.sourceId, consumption?.extensionPackage, active.type) {
        loadConsumptionRows(consumption)
    }

    val visibleRows = if (descending) childRows.reversed() else childRows

    Box(Modifier.fillMaxSize().background(SoraBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item(key = "info") {
                DetailInfoHeader(
                    selection = active,
                    metadata = metadata,
                    sourceName = displayedSourceName,
                )
            }

            item(key = "actions") {
                DetailActionRow(
                    saved = isSaved(active),
                    browserEnabled = webViewAvailable && !browserBusy,
                    browserBusy = browserBusy,
                    type = active.type,
                    counterpart = counterpart,
                    onLibrary = { onToggleSaved(active) },
                    onAdaptation = { counterpart?.let { active = it } },
                    onWebView = ::openBrowser,
                )
            }

            browserError?.let { message ->
                item(key = "browserError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            readerError?.let { message ->
                item(key = "readerError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            playbackError?.let { message ->
                item(key = "playbackError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            item(key = "description") {
                DetailDescription(
                    metadata = metadata,
                    fallback = active.subtitle,
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded },
                )
            }

            if (secondaryRows.isNotEmpty()) {
                item(key = "secondaryHeader") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = ::effectiveBack) { Icon(Icons.Rounded.ArrowBack, "Back to ${sectionTitle(active.type)}") }
                        Text(secondaryTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(secondaryRows, key = { "secondary-${it.id}" }) { row ->
                    SecondaryListRow(row, active.type)
                }
            } else {
                item(key = "itemHeader") {
                    DetailItemsHeader(
                        type = active.type,
                        count = childRows.size,
                        descending = descending,
                        onSort = { descending = !descending },
                    )
                }

                if (rowsLoading) {
                    item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
                } else if (consumption == null && requiresConsumptionSource(active.type)) {
                    item(key = "noSource") {
                        NoConsumptionSource(
                            type = active.type,
                            hasOptions = sourceOptions.isNotEmpty(),
                            onChooseSource = { sourcePickerOpen = true },
                        )
                    }
                } else if (childRows.isEmpty()) {
                    item(key = "emptyRows") {
                        Text(
                            "No ${sectionTitle(active.type).lowercase()} were returned by this source.",
                            color = SoraMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        )
                    }
                } else {
                    items(visibleRows, key = { "row-${it.id}" }) { row ->
                        DetailMediaListRow(
                            row = row,
                            type = active.type,
                            onClick = when (active.type) {
                                ContentType.ANIME, ContentType.TV, ContentType.MANGA -> ({ openChild(row) })
                                ContentType.MOVIE -> ({ openMovie(row) })
                                else -> null
                            },
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = {
                if (toolbarOpaque) Text(active.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
            },
            navigationIcon = { IconButton(onClick = ::effectiveBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Title options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Change source") },
                            leadingIcon = { Icon(Icons.Rounded.Source, null) },
                            onClick = { menuOpen = false; sourcePickerOpen = true },
                        )
                        if (webViewAvailable) {
                            DropdownMenuItem(
                                text = { Text("Open web view") },
                                leadingIcon = { Icon(Icons.Rounded.Public, null) },
                                onClick = { menuOpen = false; openBrowser() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menuOpen = false; refreshEpoch++ },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (toolbarOpaque) SoraBg else Color.Transparent,
                scrolledContainerColor = SoraBg,
            ),
        )

        if (secondaryRows.isEmpty() && visibleRows.isNotEmpty() && (active.type == ContentType.ANIME || active.type == ContentType.MANGA || active.type == ContentType.TV || active.type == ContentType.MOVIE)) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (active.type == ContentType.MOVIE) openMovie(visibleRows.firstOrNull())
                    else visibleRows.firstOrNull()?.let(::openChild)
                },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
                containerColor = SoraAccent,
                contentColor = SoraAccentInk,
                icon = { Icon(if (active.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null) },
                text = { Text(if (active.type == ContentType.MANGA) "Start" else "Play") },
            )
        }
    }

    if (sourcePickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { sourcePickerOpen = false; sourceSearchError = null },
            containerColor = SoraSurface,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                Text("Choose source", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                Text(
                    "Sora keeps catalog metadata separate from the extension used to ${consumptionVerb(active.type)}.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (sourceSearchBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                sourceSearchError?.let { Text(it, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }

                if (sourceOptions.isEmpty()) {
                    Text(
                        "No compatible ${active.type.label.lowercase()} source extension is installed yet.",
                        color = SoraMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                } else {
                    sourceOptions.forEach { (ext, source) ->
                        val selected = consumption?.extensionPackage == ext.packageName && consumption?.sourceId == source.id
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !sourceSearchBusy) { chooseSource(ext, source) }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Public, null, tint = if (selected) SoraAccent else SoraMuted)
                            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                                Text(source.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(ext.declaredName, color = SoraMuted, fontSize = 10.sp)
                            }
                            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = SoraAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoHeader(selection: ExtensionMediaSelection, metadata: DetailMetadata, sourceName: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 250.dp)) {
        if (!selection.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                selection.artworkUrl,
                null,
                Modifier.matchParentSize().blur(4.dp).alpha(.20f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, SoraBg))))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 94.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(SoraSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (!selection.artworkUrl.isNullOrBlank()) {
                    AsyncImage(selection.artworkUrl, selection.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(selection.title.take(1), color = SoraMuted, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(selection.title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (selection.subtitle.isNotBlank()) Text(selection.subtitle, color = SoraMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (metadata.score.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, tint = SoraAccent, modifier = Modifier.size(16.dp))
                        Text(metadata.score, color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon(metadata.status), null, tint = SoraMuted, modifier = Modifier.size(16.dp))
                    Text(metadata.status.ifBlank { "Unknown status" }, color = SoraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, null, tint = SoraMuted, modifier = Modifier.size(16.dp))
                    Text(sourceName, color = SoraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailActionRow(
    saved: Boolean,
    browserEnabled: Boolean,
    browserBusy: Boolean,
    type: ContentType,
    counterpart: ExtensionMediaSelection?,
    onLibrary: () -> Unit,
    onAdaptation: () -> Unit,
    onWebView: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        DetailActionButton(
            title = if (saved) "In library" else "Add to library",
            icon = if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            highlighted = saved,
            onClick = onLibrary,
        )
        if (type == ContentType.ANIME || type == ContentType.MANGA) {
            DetailActionButton(
                title = if (type == ContentType.ANIME) "Manga" else "Anime",
                icon = Icons.Rounded.SwapHoriz,
                highlighted = counterpart != null,
                enabled = counterpart != null,
                onClick = onAdaptation,
            )
        }
        DetailActionButton(
            title = if (browserBusy) "Opening…" else "Web view",
            icon = Icons.Rounded.Public,
            highlighted = browserEnabled,
            enabled = browserEnabled,
            onClick = onWebView,
        )
    }
}

@Composable
private fun RowScope.DetailActionButton(
    title: String,
    icon: ImageVector,
    highlighted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = when {
        highlighted -> SoraAccent
        enabled -> SoraMuted
        else -> SoraFaint
    }
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, color = color, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun DetailDescription(metadata: DetailMetadata, fallback: String, expanded: Boolean, onToggle: () -> Unit) {
    val description = metadata.description.ifBlank { fallback }.ifBlank { "No description available." }
    Column(Modifier.fillMaxWidth()) {
        Text(
            description,
            color = SoraText.copy(alpha = .74f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp),
        )
        if (metadata.genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(metadata.genres, key = { it }) { genre ->
                    SuggestionChip(onClick = {}, label = { Text(genre, fontSize = 11.sp) })
                }
            }
        }
    }
}

@Composable
private fun DetailItemsHeader(type: ContentType, count: Int, descending: Boolean, onSort: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(sectionTitle(type), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (count > 0) Text("$count item${if (count == 1) "" else "s"}", color = SoraMuted, fontSize = 11.sp)
        }
        IconButton(onClick = onSort) { Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, "Change sort order") }
    }
}

@Composable
private fun DetailMediaListRow(row: DetailRow, type: ContentType, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, color = SoraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) {
            Icon(
                if (type == ContentType.MANGA) Icons.Rounded.ChevronRight else Icons.Rounded.PlayArrow,
                if (type == ContentType.MANGA) "Open chapter" else "Open episode",
                tint = SoraMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SecondaryListRow(row: DetailRow, type: ContentType) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (type == ContentType.MANGA) Icons.Rounded.Image else Icons.Rounded.Public, null, tint = SoraMuted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NoConsumptionSource(type: ContentType, hasOptions: Boolean, onChooseSource: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text("No ${consumptionNoun(type)} source selected", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (hasOptions) "The catalog supplies the title metadata. Choose an installed source for the actual ${consumptionVerb(type)} data."
            else "The catalog supplies the title metadata. Install a compatible source extension to load actual ${consumptionVerb(type)} data.",
            color = SoraMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (hasOptions) TextButton(onClick = onChooseSource, contentPadding = PaddingValues(vertical = 8.dp)) { Text("Choose source") }
    }
}

@Composable
fun MissingExtensionScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Source unavailable") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { Text("The source that supplied this title is no longer installed.") }
    }
}

private fun parseMetadata(raw: String, fallback: String): DetailMetadata = runCatching {
    val obj = JSONObject(raw)
    val genres = buildList {
        val array = obj.optJSONArray("genres") ?: JSONArray()
        for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add)
    }
    val score = obj.optDouble("score").takeUnless { it.isNaN() || it <= 0.0 }?.let { String.format("%.1f", it) }.orEmpty()
    DetailMetadata(
        description = obj.optString("description", fallback),
        status = obj.optString("status"),
        score = score,
        genres = genres,
    )
}.getOrElse { DetailMetadata(fallback) }

private fun parseBrowserSession(
    raw: String,
    active: ExtensionMediaSelection,
    ext: InstalledExtension,
    source: SourceDescriptor,
): SourceBrowserSession? = runCatching {
    val obj = JSONObject(raw)
    val url = obj.optString("url")
    if (!url.startsWith("http://") && !url.startsWith("https://")) return@runCatching null
    val headersObject = obj.optJSONObject("headers")
    val headers = buildMap {
        if (headersObject != null) {
            val keys = headersObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headersObject.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
            }
        }
    }
    SourceBrowserSession(
        sourceId = source.id,
        extensionPackage = ext.packageName,
        url = url,
        title = obj.optString("title", active.title).ifBlank { active.title },
        headers = headers,
    )
}.getOrNull()

private fun consumptionSourcesFor(extensions: List<InstalledExtension>, type: ContentType): List<Pair<InstalledExtension, SourceDescriptor>> {
    val key = detailTypeKey(type)
    val capability = requiredCapability(type)
    return extensions.flatMap { ext ->
        if (ext.error != null || ext.isDiagnosticProvider()) emptyList()
        else ext.descriptor?.sources.orEmpty()
            .filter { source ->
                key in source.contentTypes && (
                    capability in source.capabilities ||
                        (!ext.isCatalogProvider() && "search" in source.capabilities)
                    )
            }
            .map { source -> ext to source }
    }
}

private fun selectionCanConsume(selection: ExtensionMediaSelection, extensions: List<InstalledExtension>): Boolean {
    val ext = extensions.firstOrNull { it.packageName == selection.extensionPackage } ?: return false
    if (ext.isDiagnosticProvider()) return false
    val source = ext.descriptor?.sources?.firstOrNull { it.id == selection.sourceId } ?: return !ext.isCatalogProvider()
    return requiredCapability(selection.type) in source.capabilities || !ext.isCatalogProvider()
}

private fun searchSourceSelection(
    active: ExtensionMediaSelection,
    ext: InstalledExtension,
    source: SourceDescriptor,
    manager: ExtensionManager,
    callback: (ExtensionMediaSelection?) -> Unit,
) {
    val payload = JSONObject()
        .put("sourceId", source.id)
        .put("type", detailTypeKey(active.type))
        .put("query", active.title)
        .toString()
    manager.call(ext, ExtensionContract.Method.SEARCH, payload) { result ->
        callback(result.getOrNull()?.let { raw -> parseBestSourceSelection(raw, source.id, ext.packageName, active) })
    }
}

private fun parseBestSourceSelection(raw: String, sourceId: String, packageName: String, active: ExtensionMediaSelection): ExtensionMediaSelection? = runCatching {
    val array = JSONArray(raw)
    if (array.length() == 0) return@runCatching null
    val target = normalizeTitle(active.title)
    var best: JSONObject? = null
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val candidate = normalizeTitle(item.optString("title"))
        if (candidate == target || candidate.contains(target) || target.contains(candidate)) {
            best = item
            break
        }
    }
    val item = best ?: array.optJSONObject(0) ?: return@runCatching null
    ExtensionMediaSelection(
        id = item.optString("id"),
        sourceId = sourceId,
        extensionPackage = packageName,
        type = active.type,
        title = item.optString("title", active.title),
        subtitle = item.optString("subtitle", active.subtitle),
        artworkUrl = detailArtwork(item) ?: active.artworkUrl,
    )
}.getOrNull()

private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {
    val opposite = if (active.type == ContentType.ANIME) ContentType.MANGA else if (active.type == ContentType.MANGA) ContentType.ANIME else return callback(null)
    val key = opposite.name.lowercase()
    val providers = extensions.flatMap { ext ->
        ext.descriptor?.sources.orEmpty()
            .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
            .map { source -> ext to source }
    }
    if (providers.isEmpty()) return callback(null)

    val normalized = normalizeTitle(active.title)
    fun tryProvider(index: Int) {
        if (index >= providers.size) return callback(null)
        val (ext, source) = providers[index]
        val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", active.title).toString()
        manager.call(ext, ExtensionContract.Method.SEARCH, payload) { result ->
            val found = result.getOrNull()?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    var best: JSONObject? = null
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val name = normalizeTitle(item.optString("title"))
                        if (name == normalized || name.contains(normalized) || normalized.contains(name)) { best = item; break }
                    }
                    (best ?: arr.optJSONObject(0))?.let {
                        ExtensionMediaSelection(it.optString("id"), source.id, ext.packageName, opposite, it.optString("title"), it.optString("subtitle"), detailArtwork(it))
                    }
                }.getOrNull()
            }
            if (found != null) callback(found) else tryProvider(index + 1)
        }
    }
    tryProvider(0)
}

private fun parseRows(type: ContentType, raw: String): List<DetailRow> = runCatching {
    when (type) {
        ContentType.MUSIC -> {
            val obj = JSONObject(raw)
            listOf(DetailRow(obj.optString("trackId", "lyrics"), "Lyrics", obj.optString("text", "No lyrics returned")))
        }
        else -> {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "item-$i")
                    val number = item.optString("number").takeIf(String::isNotBlank)
                    val fallbackTitle = when (type) {
                        ContentType.ANIME, ContentType.TV -> number?.let { "Episode $it" }
                        ContentType.MANGA -> number?.let { "Chapter $it" }
                        else -> null
                    } ?: "Item ${i + 1}"
                    val title = item.optString("title").ifBlank { item.optString("label").ifBlank { fallbackTitle } }
                    val subtitle = buildList {
                        item.optString("date").takeIf(String::isNotBlank)?.let(::add)
                        item.optString("scanlator").takeIf(String::isNotBlank)?.let(::add)
                        item.optString("duration").takeIf(String::isNotBlank)?.let(::add)
                        if (isEmpty()) number?.let { add("#$it") }
                        if (isEmpty()) item.optString("url").takeIf(String::isNotBlank)?.let(::add)
                    }.joinToString(" · ")
                    add(DetailRow(id, title, subtitle))
                }
            }
        }
    }
}.getOrDefault(emptyList())

private fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            val stream = when (value) {
                is JSONObject -> {
                    val url = value.optString("url").trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                    val headers = buildMap {
                        value.optJSONObject("headers")?.let { obj ->
                            obj.keys().forEach { key ->
                                obj.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                            }
                        }
                        value.optString("referer").takeIf(String::isNotBlank)?.let { putIfAbsent("Referer", it) }
                        value.optString("userAgent").takeIf(String::isNotBlank)?.let { putIfAbsent("User-Agent", it) }
                    }
                    val label = value.optString("label").ifBlank {
                        value.optString("quality").ifBlank { value.optString("name").ifBlank { "Stream ${i + 1}" } }
                    }
                    val mime = value.optString("mimeType").ifBlank { value.optString("contentType") }.takeIf(String::isNotBlank)
                    PlaybackStream(label = label, url = url, headers = headers, mimeType = mime)
                }
                is String -> value.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { PlaybackStream(label = "Stream ${i + 1}", url = it) }
                else -> null
            }
            if (stream != null) add(stream)
        }
    }.distinctBy { it.url }
}.getOrDefault(emptyList())

private fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            val page = when (value) {
                is JSONObject -> {
                    val url = value.optString("url").trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                    val headers = buildMap {
                        value.optJSONObject("headers")?.let { obj ->
                            obj.keys().forEach { key ->
                                obj.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                            }
                        }
                        value.optString("referer").takeIf(String::isNotBlank)?.let { putIfAbsent("Referer", it) }
                        value.optString("userAgent").takeIf(String::isNotBlank)?.let { putIfAbsent("User-Agent", it) }
                    }
                    ReaderPage(url = url, headers = headers)
                }
                is String -> value.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { ReaderPage(it) }
                else -> null
            }
            if (page != null) add(page)
        }
    }
}.getOrDefault(emptyList())

private fun parseSecondary(method: String, raw: String): List<DetailRow> = runCatching {
    val arr = JSONArray(raw)
    buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (method == ExtensionContract.Method.PAGES) {
                add(DetailRow("page-$i", "Page ${i + 1}", item.optString("url")))
            } else {
                add(DetailRow("stream-$i", item.optString("label", "Source ${i + 1}"), item.optString("url")))
            }
        }
    }
}.getOrDefault(emptyList())

private fun childMethod(type: ContentType): String = when (type) {
    ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.EPISODES
    ContentType.MANGA -> ExtensionContract.Method.CHAPTERS
    ContentType.MOVIE -> ExtensionContract.Method.STREAMS
    ContentType.MUSIC -> ExtensionContract.Method.LYRICS
    ContentType.MEME -> ExtensionContract.Method.FEED
}

private fun requiredCapability(type: ContentType): String = when (type) {
    ContentType.ANIME, ContentType.TV -> "episodes"
    ContentType.MANGA -> "chapters"
    ContentType.MOVIE, ContentType.MUSIC -> "streams"
    ContentType.MEME -> "feed"
}

private fun requiresConsumptionSource(type: ContentType): Boolean = type == ContentType.ANIME || type == ContentType.MANGA || type == ContentType.MOVIE || type == ContentType.TV

private fun sectionTitle(type: ContentType): String = when (type) {
    ContentType.ANIME, ContentType.TV -> "Episodes"
    ContentType.MANGA -> "Chapters"
    ContentType.MOVIE -> "Sources"
    ContentType.MUSIC -> "Track"
    ContentType.MEME -> "Post"
}

private fun consumptionVerb(type: ContentType): String = when (type) {
    ContentType.MANGA -> "read"
    ContentType.ANIME, ContentType.TV, ContentType.MOVIE -> "watch"
    ContentType.MUSIC -> "play"
    ContentType.MEME -> "load"
}

private fun consumptionNoun(type: ContentType): String = when (type) {
    ContentType.MANGA -> "reading"
    ContentType.ANIME, ContentType.TV, ContentType.MOVIE -> "watch"
    ContentType.MUSIC -> "playback"
    ContentType.MEME -> "feed"
}

private fun statusIcon(status: String): ImageVector = when {
    status.contains("finished", true) || status.contains("complete", true) -> Icons.Rounded.DoneAll
    status.contains("airing", true) || status.contains("publishing", true) || status.contains("ongoing", true) -> Icons.Rounded.Schedule
    status.contains("hiatus", true) -> Icons.Rounded.Pause
    status.contains("cancel", true) -> Icons.Rounded.Close
    else -> Icons.Rounded.Info
}

private fun preferredSourceKey(type: ContentType): String = "preferred_${detailTypeKey(type)}"

private fun detailArtwork(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl")
    .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }

private fun detailTypeKey(type: ContentType): String = when (type) {
    ContentType.MOVIE -> "movie"
    ContentType.MEME -> "memes"
    else -> type.name.lowercase()
}

private fun normalizeTitle(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
