package com.nice.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import java.net.URI

private val CanvasBlack = Color(0xFF0B0E14)
private val Sheet = Color(0xFF181D28)
private val SurfaceEdge = Color(0xFF30384A)
private val White = Color(0xFFF5F5FA)
private val Gray = Color(0xFF9A9EAF)
private val Lilac = Color(0xFFC1B8FF)

/** Own implementation inspired by familiar browser conventions, not copied Omni source. */
private data class Destination(
    val name: String, val url: String, val tint: Color, val symbol: ImageVector
)
private val shortcuts = listOf(
    Destination("Google", "https://www.google.com/", Color(0xFF8AB4F8), Icons.Rounded.Search),
    Destination("YouTube", "https://www.youtube.com/", Color(0xFFFF6F73), Icons.Rounded.PlayArrow),
    Destination("GitHub", "https://github.com/", Color(0xFFC4C7D0), Icons.Rounded.Code),
    Destination("Wikipedia", "https://www.wikipedia.org/", Color(0xFFE2DBB7), Icons.Rounded.MenuBook),
    Destination("Reddit", "https://www.reddit.com/", Color(0xFFFF9869), Icons.Rounded.Forum),
    Destination("WhatsApp", "https://web.whatsapp.com/", Color(0xFF69D6A7), Icons.Rounded.Chat),
)

private val backgrounds = listOf(
    listOf(Color(0xFF191527), Color(0xFF0C1019), CanvasBlack),
    listOf(Color(0xFF0C2930), Color(0xFF0A171D), CanvasBlack),
    listOf(Color(0xFF1D2230), Color(0xFF10141D), CanvasBlack)
)

@Composable
internal fun NightHomePanel(
    browser: BrowserController,
    onSubmit: (String) -> Unit,
    onOpen: (String) -> Unit,
    onJourneys: () -> Unit,
    onLibrary: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("night_home_appearance", 0) }
    var backgroundChoice by remember { mutableIntStateOf(prefs.getInt("background", 0).coerceIn(0, 2)) }
    var chooseBackground by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val lastJourney = browser.store.journeys.firstOrNull()
    val newestVisit = lastJourney?.nodes?.lastOrNull()
    val savedDestinations = browser.store.bookmarks
        .take(2)
        .filterNot { mark -> shortcuts.any { it.url == mark.url } }
        .map { mark -> Destination(mark.title.take(12), mark.url, Color(0xFFBDB4FF), Icons.Rounded.Bookmark) }
    val items = (shortcuts.take(6) + savedDestinations).take(8)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(backgrounds[backgroundChoice]))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(32.dp).background(Lilac, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", color = CanvasBlack, fontWeight = FontWeight.Black, fontSize = 19.sp)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { chooseBackground = true }) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Change background", tint = White)
                    }
                    DropdownMenu(
                        expanded = chooseBackground,
                        onDismissRequest = { chooseBackground = false },
                        containerColor = Sheet
                    ) {
                        listOf("Dusk", "Deep sea", "Graphite").forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title, color = White) },
                                leadingIcon = {
                                    Box(Modifier.size(20.dp)
                                        .background(backgrounds[index][0], CircleShape)
                                        .border(1.dp, Gray, CircleShape))
                                },
                                trailingIcon = {
                                    if (index == backgroundChoice)
                                        Icon(Icons.Rounded.Check, null, tint = Lilac)
                                },
                                onClick = {
                                    backgroundChoice = index
                                    prefs.edit().putInt("background", index).apply()
                                    chooseBackground = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NIGHT", color = White, fontSize = 38.sp, letterSpacing = 7.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("B R O W S E R", color = Lilac, fontSize = 10.sp,
                    letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .background(Color(0xFF222632), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFF41445C), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = Lilac,
                    modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Lilac),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) {
                            onSubmit(query)
                            keyboard?.hide()
                            focus.clearFocus()
                        }
                    }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search or enter address", color = Gray, fontSize = 14.sp)
                        inner()
                    }
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = {
                        onSubmit(query)
                        keyboard?.hide()
                        focus.clearFocus()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Search", tint = Lilac)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick access", fontSize = 17.sp, color = White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLibrary) {
                    Text("Library", color = Lilac, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Lilac,
                        modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(5.dp))
            items.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpen(item.url) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(48.dp)
                                    .background(Color(0xFF272B37), RoundedCornerShape(19.dp))
                                    .border(1.dp, Color(0xFF383C50), RoundedCornerShape(19.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.symbol, contentDescription = null,
                                    tint = item.tint, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.name, color = Color(0xFFDEE0EC), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Journeys", color = White, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onJourneys) {
                    Text("Explore", color = Lilac, fontSize = 13.sp)
                    Icon(Icons.Rounded.ChevronRight, null,
                        modifier = Modifier.size(17.dp), tint = Lilac)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF252438).copy(alpha = 0.88f))
                    .border(1.dp, Color(0xFF47425F), RoundedCornerShape(20.dp))
                    .clickable {
                        if (newestVisit != null) onOpen(newestVisit.url)
                        else onJourneys()
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).background(Color(0xFF39334F),
                    RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AccountTree, null, tint = Lilac, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(lastJourney?.name ?: "My browsing", color = White, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(newestVisit?.title?.takeIf { it.isNotBlank() }
                            ?: "Your browsing path starts here",
                        color = Gray, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Lilac)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
internal fun NightTabGrid(
    browser: BrowserController,
    onOpen: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNew: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(CanvasBlack)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 17.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tabs", color = White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(11.dp))
            Box(Modifier.background(Sheet, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("${browser.tabs.size}", color = Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNew) {
                Icon(Icons.Rounded.Add, contentDescription = "New tab", tint = White)
            }
        }
        HorizontalDivider(color = SurfaceEdge.copy(alpha = .6f))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(browser.tabs.toList(), key = { it.id }) { tab ->
                TabThumbnailCard(tab, selected = tab.id == browser.activeId,
                    onOpen = { onOpen(tab) },
                    onClose = { onClose(tab) })
            }
        }
    }
}

@Composable
private fun TabThumbnailCard(
    tab: BrowserTab, selected: Boolean, onOpen: () -> Unit, onClose: () -> Unit
) {
    var drag by remember(tab.id) { mutableFloatStateOf(0f) }
    val swipeLimit = with(LocalDensity.current) { 115.dp.toPx() }
    Column(
        Modifier.fillMaxWidth()
            .offset { IntOffset(drag.roundToInt(), 0) }
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > swipeLimit) onClose()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { change, distance ->
                        change.consume()
                        drag = (drag + distance).coerceIn(-swipeLimit * 1.3f, swipeLimit * 1.3f)
                    }
                )
            }
            .clip(RoundedCornerShape(19.dp))
            .background(Sheet)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Lilac else SurfaceEdge,
                RoundedCornerShape(19.dp)
            )
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = 9.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(22.dp).background(Color(0xFF323748), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.url.takeIf { it.startsWith("http") }
                        ?.let { runCatching { URI(it).host.orEmpty().removePrefix("www.").take(1).uppercase() }.getOrDefault("•") }
                        ?: "N",
                    color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                tab.title.ifBlank { "New tab" },
                color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose, modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close ${tab.title}",
                    tint = Color(0xFFC8CAD5), modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(.80f)
                .background(Color(0xFF111725))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            val thumbnail = tab.thumbnail
            if (thumbnail != null && !thumbnail.isRecycled) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Preview of ${tab.title}",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)) {
                    Icon(
                        if (tab.url.startsWith("http")) Icons.Rounded.Language else Icons.Rounded.Explore,
                        contentDescription = null, tint = Lilac.copy(alpha = .7f),
                        modifier = Modifier.size(43.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        if (tab.url.startsWith("http")) {
                            runCatching { URI(tab.url).host.orEmpty().removePrefix("www.") }
                                .getOrDefault("Website")
                        } else "New tab",
                        color = Gray, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** One native bottom sheet; no second menu or duplicated actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NightMenuSheet(
    canSave: Boolean,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onJourneys: () -> Unit,
    onLibrary: () -> Unit,
    onTimeMachine: () -> Unit,
    onBookmark: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1D27),
        contentColor = White,
        dragHandle = {
            Box(Modifier.padding(top = 11.dp, bottom = 13.dp).size(40.dp, 4.dp)
                .background(Color(0xFF555969), CircleShape))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 25.dp)) {
            Text("NIGHT", color = White, fontSize = 16.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            val entries = listOf(
                Triple("New tab", Icons.Rounded.Add, onNew),
                Triple("Journeys", Icons.Rounded.AccountTree, onJourneys),
                Triple("Library", Icons.Rounded.Bookmarks, onLibrary),
                Triple("Time Machine", Icons.Rounded.History, onTimeMachine),
                Triple("Bookmark", Icons.Rounded.BookmarkAdd, onBookmark),
                Triple("Save PDF", Icons.Rounded.SaveAlt, onSave),
                Triple("Reload", Icons.Rounded.Refresh, onReload)
            )
            entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { (label, icon, action) ->
                        val enabled = canSave || (label !in listOf("Bookmark", "Save PDF", "Reload"))
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable(enabled = enabled) { onDismiss(); action() }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(52.dp).background(
                                    if (enabled) Color(0xFF2B2F3D) else Color(0xFF21232A),
                                    RoundedCornerShape(17.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null,
                                    tint = if (enabled) Lilac else Color(0xFF636676),
                                    modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(label, color = if (enabled) White else Gray,
                                fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(13.dp))
            }
        }
    }
}

/** Familiar Chrome-style outlined tab counter, exactly one switcher control. */
@Composable
internal fun NightTabCountIcon(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = "Tabs" }) {
        Box(
            Modifier.size(25.dp).border(2.dp, White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(count.toString().take(2), color = White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
        }
    }
}
