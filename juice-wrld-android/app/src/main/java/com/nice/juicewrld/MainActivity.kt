package com.nice.juicewrld

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

private val Ink = Color(0xFF08080D)
private val Surface = Color(0xFF12121B)
private val Line = Color(0xFF282634)
private val Light = Color(0xFFF4F1F9)
private val Muted = Color(0xFF9994AA)
private val Purple = Color(0xFFB886F5)
private val Pink = Color(0xFFFF91C2)
private val juiceColorScheme = darkColorScheme(
    primary = Purple, onPrimary = Ink, surface = Surface, onSurface = Light,
    background = Ink, onBackground = Light, outline = Line,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = juiceColorScheme) { JuiceScreen() } }
    }
}

@Composable
fun JuiceScreen(model: JuiceViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val context = LocalContext.current
    val releases = remember(state.items, state.favorites, state.savedTab, query, filter) {
        CatalogParser.search(
            if (state.savedTab) state.items.filter { it.id in state.favorites } else state.items,
            query, filter
        )
    }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        LazyVerticalGrid(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 8.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppHeader(onOpenArtist = { openArtist(context) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            if (state.savedTab) "Your collection" else "The discography",
                            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.1).sp, color = Light,
                        )
                        Text(
                            if (state.savedTab) state.favorites.size.toString() + " saved releases" else "Juice WRLD",
                            fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (!state.savedTab) {
                        IconButton(
                            onClick = { model.fetchPage(refresh = true) },
                            enabled = !state.loading && !state.loadingMore,
                            modifier = Modifier.semantics { contentDescription = "Refresh catalog" }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Muted)
                        }
                    }
                }
            }
            if (!state.savedTab && state.items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeaturedRelease(
                        item = state.items.first(),
                        onClick = { model.showRelease(state.items.first()) }
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogSearch(value = query, onChange = { query = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Album", "EP", "Single").forEach { type ->
                        FilterPill(
                            text = when (type) {
                                "Album" -> "Albums"
                                "Single" -> "Singles"
                                "EP" -> "EPs"
                                else -> "All"
                            },
                            selected = filter == type,
                            modifier = Modifier.weight(1f),
                            onClick = { filter = type },
                        )
                    }
                }
            }
            if (state.fromCache && state.items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Saved catalog · Connect to refresh", color = Muted, fontSize = 12.sp)
                }
            }
            if (state.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatusCard(state.error!!, onClick = { model.fetchPage(refresh = true) })
                }
            }
            if (state.loading && state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple, strokeWidth = 2.dp)
                    }
                }
            } else if (releases.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyContent(isSaved = state.savedTab, hasQuery = query.isNotBlank(), filter = filter)
                }
            } else {
                items(releases, key = { it.id }) { release ->
                    ReleaseCard(
                        release = release,
                        isSaved = release.id in state.favorites,
                        onOpen = { model.showRelease(release) },
                        onSave = { model.toggleFavorite(release.id) },
                    )
                }
            }
            if (state.hasMore && !state.savedTab) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { model.fetchPage(refresh = false) },
                        enabled = !state.loadingMore,
                        colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Light),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Purple
                            )
                        } else Text("Load more releases", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        NavigationBar(
            containerColor = Color(0xFF0F0E16), tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding().height(66.dp),
        ) {
            NavigationBarItem(
                selected = !state.savedTab,
                onClick = { model.setSavedTab(false) },
                icon = { Icon(Icons.Filled.Headphones, contentDescription = null) },
                label = { Text("Discover", fontSize = 12.sp) },
                alwaysShowLabel = true,
                colors = navColors()
            )
            NavigationBarItem(
                selected = state.savedTab,
                onClick = { model.setSavedTab(true) },
                icon = {
                    Icon(
                        if (state.savedTab) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null
                    )
                },
                label = { Text("Saved", fontSize = 12.sp) },
                alwaysShowLabel = true,
                colors = navColors()
            )
        }
    }
    state.selected?.let { release ->
        ReleaseDetails(
            item = release, isSaved = release.id in state.favorites,
            tracks = state.tracks, busy = state.loadingTracks, error = state.trackError,
            onDismiss = { model.closeRelease() },
            onSave = { model.toggleFavorite(release.id) },
            onListen = { openArtist(context) }
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Purple, selectedTextColor = Light,
    indicatorColor = Color(0xFF292036),
    unselectedIconColor = Muted, unselectedTextColor = Muted
)

@Composable
private fun AppHeader(onOpenArtist: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(37.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Purple, Color(0xFF7354B2)))),
            contentAlignment = Alignment.Center
        ) {
            Text("9", color = Ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                "JUICE WRLD", fontSize = 16.sp, fontWeight = FontWeight.Black,
                color = Light, letterSpacing = 2.sp, lineHeight = 17.sp
            )
            Text("999  /  ARCHIVE", fontSize = 9.sp, color = Purple,
                letterSpacing = 2.2.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onOpenArtist,
            modifier = Modifier.semantics { contentDescription = "Listen via Apple Music" }
        ) {
            Icon(Icons.Filled.NorthEast, contentDescription = null, tint = Light)
        }
    }
}

@Composable
private fun FeaturedRelease(item: ReleaseGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 158.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2F174D), Color(0xFF21172F), Color(0xFF13121D))
                )
            )
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Artwork(item, Modifier.size(117.dp).clip(RoundedCornerShape(15.dp)), false)
        Column(Modifier.weight(1f)) {
            Text(
                "LATEST RELEASE", fontSize = 9.sp, color = Pink,
                fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                item.title, maxLines = 3, overflow = TextOverflow.Ellipsis,
                fontSize = 19.sp, lineHeight = 22.sp,
                color = Light, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                item.year + "  ·  " + item.type,
                fontSize = 11.sp, color = Color(0xFFC1B5D3)
            )
            Spacer(Modifier.height(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Purple,
                modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CatalogSearch(value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp)).background(Surface)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Muted,
            modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Light, fontSize = 15.sp),
            cursorBrush = SolidColor(Purple),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search releases" },
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text("Search music or year", fontSize = 14.sp, color = Muted)
                    inner()
                }
            }
        )
        if (value.isNotBlank()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Muted,
                    modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun FilterPill(
    text: String, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Light else Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Ink else Muted)
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseGroup, isSaved: Boolean,
    onOpen: () -> Unit, onSave: () -> Unit
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(Surface).clickable(onClick = onOpen).padding(9.dp)
    ) {
        Box {
            Artwork(
                release, Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)), true
            )
            IconButton(
                onClick = onSave,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(35.dp)
                    .clip(CircleShape).background(Color(0xD2100E18))
                    .semantics {
                        contentDescription = if (isSaved)
                            "Remove " + release.title + " from saved"
                        else "Save " + release.title
                    }
            ) {
                Icon(
                    if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null, tint = if (isSaved) Pink else Light,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            release.title, color = Light, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
            modifier = Modifier.heightIn(min = 36.dp).padding(horizontal = 3.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            release.year + "  ·  " + release.type.uppercase(),
            color = Muted, fontSize = 10.sp,
            modifier = Modifier.padding(start = 3.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun Artwork(release: ReleaseGroup, modifier: Modifier, compact: Boolean) {
    SubcomposeAsyncImage(
        model = release.coverUrl,
        contentDescription = "Cover art for " + release.title,
        modifier = modifier, contentScale = ContentScale.Crop,
        loading = { ArtworkFallback(release, compact) },
        error = { ArtworkFallback(release, compact) },
        success = { SubcomposeAsyncImageContent(contentScale = ContentScale.Crop) }
    )
}

@Composable
private fun ArtworkFallback(release: ReleaseGroup, compact: Boolean) {
    val palettes = listOf(
        listOf(Color(0xFF5D376E), Color(0xFF201B32)),
        listOf(Color(0xFF743C61), Color(0xFF292033)),
        listOf(Color(0xFF344F76), Color(0xFF1C223E)),
        listOf(Color(0xFF4E3F7A), Color(0xFF1D1D35))
    )
    val tones = palettes[(release.id.hashCode() and Int.MAX_VALUE) % palettes.size]
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(tones)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "999", color = Color.White.copy(alpha = 0.13f),
            fontSize = if (compact) 45.sp else 55.sp, fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp
        )
        Icon(
            Icons.Filled.MusicNote, contentDescription = null,
            tint = Color.White.copy(alpha = 0.87f),
            modifier = Modifier.size(if (compact) 30.dp else 42.dp)
        )
    }
}

@Composable
private fun EmptyContent(isSaved: Boolean, hasQuery: Boolean, filter: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 50.dp, bottom = 45.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (isSaved) Icons.Filled.LibraryMusic else Icons.Filled.Search,
            contentDescription = null, tint = Purple.copy(alpha = 0.8f),
            modifier = Modifier.size(35.dp)
        )
        Spacer(Modifier.height(15.dp))
        Text(
            when {
                hasQuery -> "Nothing matches"
                isSaved -> "No saved music yet"
                filter != "All" -> "No releases in this category"
                else -> "No releases to show"
            },
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Light
        )
        Spacer(Modifier.height(9.dp))
        Text(
            when {
                hasQuery -> "Try a different title or year."
                isSaved -> "Tap the heart on a release to keep it here."
                filter != "All" -> "Try another category."
                else -> "Use refresh to load the catalog."
            },
            fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusCard(message: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF241C2E))
            .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Light, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text("Retry", color = Purple, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseDetails(
    item: ReleaseGroup, isSaved: Boolean,
    tracks: List<Track>, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: () -> Unit, onListen: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15131D), contentColor = Light,
        dragHandle = {
            Box(Modifier.padding(13.dp).width(45.dp).height(4.dp)
                .clip(CircleShape).background(Color(0xFF766F80)))
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp).padding(bottom = 35.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Artwork(item, Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)), false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.type.uppercase() + "  /  " + item.year,
                        color = Purple, fontSize = 11.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.title, fontSize = 21.sp, color = Light, lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("Juice WRLD", fontSize = 13.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(19.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onListen, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(47.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)
                ) {
                    Icon(Icons.Default.NorthEast, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Artist on Apple Music", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedIconButton(
                    onClick = onSave, modifier = Modifier.size(47.dp),
                    border = BorderStroke(1.dp, Line),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = if (isSaved) Pink else Light
                    )
                ) {
                    Icon(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove from saved" else "Save release"
                    )
                }
            }
            Spacer(Modifier.height(25.dp))
            Text("TRACKLIST", fontSize = 11.sp, color = Muted,
                letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(13.dp))
            when {
                busy -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Purple, strokeWidth = 2.dp,
                        modifier = Modifier.size(25.dp))
                }
                error != null -> Text(error, fontSize = 13.sp, color = Muted,
                    modifier = Modifier.padding(vertical = 18.dp))
                else -> tracks.forEach { track ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 55.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(track.position, color = Muted, fontSize = 12.sp,
                            modifier = Modifier.width(35.dp))
                        Text(
                            track.title, color = Light, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2, modifier = Modifier.weight(1f)
                        )
                        track.formattedLength()?.let {
                            Text(it, color = Muted, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Line)
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(
                "Metadata: MusicBrainz. This is an unofficial fan-made catalog.",
                color = Muted, fontSize = 11.sp, lineHeight = 17.sp
            )
        }
    }
}

private fun openArtist(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APPLE_MUSIC_ARTIST)))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No browser available",
            android.widget.Toast.LENGTH_SHORT).show()
    }
}
