package com.night.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0C1017)
private val Raised = Color(0xFF171D27)
private val Border = Color(0xFF2B3441)
private val Bright = Color(0xFFF3F5F9)
private val Muted = Color(0xFF8F9BAC)
private val Accent = Color(0xFFB9BEFF)

private enum class Page { HOME, WEB, TABS, JOURNEYS, LIBRARY, TIME_MACHINE }

class MainActivity : ComponentActivity() {
    private lateinit var browser: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browser = BrowserController(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Raised, primary = Accent,
                onSurface = Bright, onBackground = Bright
            )) {
                NightBrowser(browser) { saved ->
                    val file = File(File(filesDir, "saved-pages"), saved.fileName)
                    if (!file.exists()) {
                        browser.notice = "The saved copy is missing."
                    } else {
                        val uri = FileProvider.getUriForFile(
                            this, "com.night.browser.files", file)
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { startActivity(intent) }
                        catch (e: Exception) { browser.notice = "Install a PDF viewer to open this copy." }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) browser.tabs.forEach { if (it.session.isOpen) it.session.close() }
        super.onDestroy()
    }
}

@Composable
private fun NightBrowser(browser: BrowserController, openSaved: (SavedPage) -> Unit) {
    var page by remember { mutableStateOf(Page.HOME) }
    var address by remember { mutableStateOf("") }
    var addressFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var peekUrl by remember { mutableStateOf<String?>(null) }
    var newJourneyDialog by remember { mutableStateOf(false) }
    val current = browser.active
    val context = LocalContext.current
    val archive = remember(browser) { TimeMachineArchive(context.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(current.url, current.id) {
        if (!addressFocused) address = current.url
    }
    LaunchedEffect(browser.notice) {
        val message = browser.notice
        if (!message.isNullOrBlank()) {
            browser.notice = null
            snack.showSnackbar(message)
        }
    }

    fun openAddress() {
        browser.navigate(address)
        page = Page.WEB
        keyboard?.hide()
        focusManager.clearFocus()
    }
    fun openTab(tab: BrowserTab) {
        browser.select(tab.id)
        page = if (tab.url.isBlank()) Page.HOME else Page.WEB
    }
    BackHandler(enabled = page != Page.HOME || peekUrl != null) {
        when {
            peekUrl != null -> peekUrl = null
            page == Page.WEB && current.canGoBack -> browser.goBack()
            page != Page.HOME -> page = Page.HOME
            else -> Unit
        }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snack) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    Page.HOME -> NightStartPage(browser,
                        onOpen = { raw -> browser.navigate(raw); page = Page.WEB },
                        onJourneys = { page = Page.JOURNEYS })
                    Page.WEB -> WebSurface(current)
                    Page.TABS -> TabPanel(browser,
                        onOpen = { openTab(it) },
                        onClose = { browser.closeTab(it.id) },
                        onNew = { browser.newTab(); page = Page.HOME })
                    Page.JOURNEYS -> JourneyPanel(browser,
                        onNew = { newJourneyDialog = true },
                        onOpen = { j, url ->
                            browser.newTab(url, j.id)
                            page = Page.WEB
                        },
                        onSelect = { j ->
                            browser.newTab(journeyId = j.id)
                            page = Page.HOME
                        })
                    Page.LIBRARY -> LibraryPanel(browser,
                        onOpen = { browser.newTab(it); page = Page.WEB })
                    Page.TIME_MACHINE -> TimeMachineArchivePanel(
                        archive = archive,
                        openSaved = { item ->
                            openSaved(SavedPage(item.fileName, item.url, item.title, item.capturedAt))
                        },
                        onNotice = { browser.notice = it }
                    )
                }
            }

            if (current.isLoading && page == Page.WEB) {
                LinearProgressIndicator(
                    progress = { (current.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent, trackColor = Border
                )
            }

            // The custom start page owns the sole omnibar on Home.
            // Website address entry belongs to real web browsing only.
            if (page == Page.WEB) {
            Row(
                Modifier.fillMaxWidth().background(Ink)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .background(Raised, RoundedCornerShape(15.dp))
                        .padding(horizontal = 15.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { addressFocused = it.isFocused },
                        singleLine = true,
                        textStyle = TextStyle(color = Bright, fontSize = 15.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { openAddress() }),
                        decorationBox = { inner ->
                            if (address.isBlank()) {
                                Text("Search or enter address", color = Muted, fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                }
                ChromeIcon(Icons.Rounded.ArrowUpward, "Go", enabled = address.isNotBlank()) {
                    openAddress()
                }
            }
            }
            HorizontalDivider(color = Border, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().background(Ink)
                    .navigationBarsPadding().height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", current.canGoBack) {
                    browser.goBack(); page = Page.WEB
                }
                ChromeIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", current.canGoForward) {
                    browser.goForward(); page = Page.WEB
                }
                ChromeIcon(Icons.Rounded.Home, "Home") { page = Page.HOME }
                ChromeIcon(Icons.Rounded.Tab, "Tabs") { page = Page.TABS }
                Box {
                    ChromeIcon(Icons.Rounded.MoreHoriz, "More") { menuExpanded = true }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = Raised
                    ) {
                        fun dismiss(action: () -> Unit) { menuExpanded = false; action() }
                        DropdownMenuItem(
                            text = { Text("New tab") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { dismiss { browser.newTab(); page = Page.HOME } })
                        DropdownMenuItem(
                            text = { Text("Journeys") },
                            leadingIcon = { Icon(Icons.Rounded.AccountTree, null) },
                            onClick = { dismiss { page = Page.JOURNEYS } })
                        DropdownMenuItem(
                            text = { Text("Library") },
                            leadingIcon = { Icon(Icons.Rounded.Bookmarks, null) },
                            onClick = { dismiss { page = Page.LIBRARY } })
                        DropdownMenuItem(
                            text = { Text("Time Machine") },
                            leadingIcon = { Icon(Icons.Rounded.History, null) },
                            onClick = { dismiss { page = Page.TIME_MACHINE } })
                        DropdownMenuItem(
                            text = { Text("Bookmark page") },
                            leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) },
                            enabled = current.url.startsWith("http"),
                            onClick = { dismiss { browser.bookmark() } })
                        DropdownMenuItem(
                            text = { Text("Save PDF copy") },
                            leadingIcon = { Icon(Icons.Rounded.SaveAlt, null) },
                            enabled = current.url.startsWith("http"),
                            onClick = { dismiss {
                                archive.savePdf(current.session, current.url, current.title) {
                                    browser.notice = it
                                }
                            } })
                        DropdownMenuItem(
                            text = { Text("Reload") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            enabled = current.url.isNotBlank(),
                            onClick = { dismiss { browser.reload(); page = Page.WEB } })
                    }
                }
            }
        }
    }

    browser.contextLink?.let { link ->
        AlertDialog(
            onDismissRequest = { browser.contextLink = null },
            title = { Text("Open link") },
            text = { Text(link, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { browser.contextLink = null; peekUrl = link }) {
                    Text("Peek")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    browser.contextLink = null
                    browser.newTab(link)
                    page = Page.WEB
                }) { Text("New tab") }
            },
            containerColor = Raised
        )
    }

    peekUrl?.let { url ->
        PeekDialog(browser, url,
            onDismiss = { peekUrl = null },
            onOpen = {
                browser.newTab(url)
                peekUrl = null
                page = Page.WEB
            })
    }

    if (newJourneyDialog) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newJourneyDialog = false },
            title = { Text("New Journey") },
            text = {
                Box(Modifier.fillMaxWidth()
                    .background(Ink, RoundedCornerShape(12.dp))
                    .padding(12.dp)) {
                    BasicTextField(
                        value = label, onValueChange = { label = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Bright, fontSize = 16.sp),
                        cursorBrush = SolidColor(Accent),
                        decorationBox = { inner ->
                            if (label.isBlank()) Text("Journey name", color = Muted)
                            inner()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val journey = browser.store.newJourney(label)
                    browser.newTab(journeyId = journey.id)
                    newJourneyDialog = false
                    page = Page.HOME
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newJourneyDialog = false }) { Text("Cancel") }
            },
            containerColor = Raised
        )
    }
}

@Composable
private fun WebSurface(tab: BrowserTab) {
    val context = LocalContext.current
    val view = remember { GeckoView(context) }
    DisposableEffect(view) {
        onDispose { view.releaseSession() }
    }
    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize().background(Color.White),
        update = { target ->
            if (target.session !== tab.session) {
                target.releaseSession()
                target.setSession(tab.session)
            }
        }
    )

    // Capture genuine rendered page pixels, not a fabricated tab mockup.
    // Scaled previews are kept in memory only; do not persist private page pixels.
    LaunchedEffect(tab.id, tab.url, tab.isLoading) {
        if (!tab.isLoading && tab.url.startsWith("http")) {
            delay(900)
            if (view.session === tab.session && view.width > 0 && view.height > 0) {
                runCatching {
                    view.capturePixels().accept({ bitmap ->
                        bitmap?.let { pixels ->
                            if (pixels.width > 0 && pixels.height > 0) {
                                val width = 260
                                val height = (pixels.height.toLong() * width / pixels.width)
                                    .toInt().coerceIn(1, 560)
                                tab.thumbnail = android.graphics.Bitmap.createScaledBitmap(
                                    pixels, width, height, true
                                )
                            }
                        }
                    }, { _ -> /* no preview yet if the compositor isn't ready */ })
                }
            }
        }
    }
}

@Composable
private fun PeekDialog(
    browser: BrowserController, url: String, onDismiss: () -> Unit, onOpen: () -> Unit
) {
    val context = LocalContext.current
    val peek = remember(url) { browser.newPeekSession(url) }
    val view = remember(url) { GeckoView(context) }
    DisposableEffect(peek) {
        onDispose {
            view.releaseSession()
            if (peek.isOpen) peek.close()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth(.94f).fillMaxHeight(.84f)
                .background(Raised, RoundedCornerShape(20.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PEEK", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(url, color = Bright, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                ChromeIcon(Icons.Rounded.OpenInNew, "Open as tab") { onOpen() }
                ChromeIcon(Icons.Rounded.Close, "Close preview") { onDismiss() }
            }
            HorizontalDivider(color = Border)
            AndroidView(
                factory = { view.apply { setSession(peek) } },
                modifier = Modifier.fillMaxSize().background(Color.White)
            )
        }
    }
}

private data class StartShortcut(val title: String, val url: String)

@Composable
private fun HomePanel(
    browser: BrowserController, onJourney: () -> Unit, onOpen: (String) -> Unit
) {
    val bookmarks = browser.store.bookmarks
    val quickSites = if (bookmarks.isNotEmpty()) {
        bookmarks.take(8).map { StartShortcut(it.title, it.url) }
    } else listOf(
        StartShortcut("Google", "https://www.google.com"),
        StartShortcut("YouTube", "https://www.youtube.com"),
        StartShortcut("Wikipedia", "https://www.wikipedia.org"),
        StartShortcut("GitHub", "https://github.com")
    )
    val recent = browser.store.journeys.asSequence()
        .flatMap { it.nodes.asSequence() }.sortedByDescending { it.timestamp }
        .distinctBy { it.url }.take(4).toList()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NIGHT", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp, color = Bright)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.width(3.dp).height(18.dp)
                .background(Accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(9.dp))
            Text("Browser", fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(36.dp))
        Text("Quick access", fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
            color = Bright)
        Spacer(Modifier.height(16.dp))
        quickSites.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { site ->
                    Column(
                        Modifier.weight(1f).clickable { onOpen(site.url) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .background(Raised, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(site.title.take(1).uppercase(), color = Bright,
                                fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(site.title, color = Muted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Journeys", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = Bright, modifier = Modifier.weight(1f))
            TextButton(onClick = onJourney) { Text("See all") }
        }
        browser.store.journeys.firstOrNull { it.nodes.isNotEmpty() }?.let { journey ->
            RowCard(
                title = journey.name,
                subtitle = journey.nodes.last().title.ifBlank { journey.nodes.last().url },
                icon = { Icon(Icons.Rounded.AccountTree, null, tint = Accent) },
                onClick = { onOpen(journey.nodes.last().url) }
            )
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recently visited", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = Bright)
            Spacer(Modifier.height(14.dp))
            recent.forEach { node ->
                RowCard(
                    title = node.title.ifBlank { node.url },
                    subtitle = node.url,
                    icon = { Icon(Icons.Rounded.History, null, tint = Accent) },
                    onClick = { onOpen(node.url) }
                )
            }
        }
        Spacer(Modifier.height(35.dp))
    }
}

@Composable
private fun TabPanel(
    browser: BrowserController,
    onOpen: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNew: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 15.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tabs", fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
                color = Bright)
            Text("  ${browser.tabs.size}", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            ChromeIcon(Icons.Rounded.Add, "New tab", onClick = onNew)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(browser.tabs.size, key = { browser.tabs[it].id }) { index ->
                val tab = browser.tabs[index]
                val selected = tab.id == browser.activeId
                Surface(
                    modifier = Modifier.fillMaxWidth().height(248.dp)
                        .clickable { onOpen(tab) },
                    shape = RoundedCornerShape(16.dp),
                    color = Raised,
                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) Accent else Border)
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().height(51.dp)
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = Bright, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            ChromeIcon(Icons.Rounded.Close, "Close ${tab.title}") {
                                onClose(tab)
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().weight(1f).background(Ink),
                            contentAlignment = Alignment.Center
                        ) {
                            val preview = tab.thumbnail
                            if (preview != null) {
                                Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = "Preview of ${tab.title}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Rounded.Public, null, tint = Muted,
                                    modifier = Modifier.size(36.dp))
                            }
                        }
                        Text(
                            tab.url.ifBlank { "New tab" },
                            modifier = Modifier.fillMaxWidth().padding(9.dp),
                            color = Muted, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyPanel(
    browser: BrowserController,
    onNew: () -> Unit,
    onOpen: (Journey, String) -> Unit,
    onSelect: (Journey) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Journeys", action = {
            ChromeIcon(Icons.Rounded.Add, "New Journey", onClick = onNew)
        })
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(browser.store.journeys, key = { it.id }) { journey ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(journey.name, color = Bright, fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSelect(journey) }) { Text("Use") }
                    }
                    if (journey.nodes.isEmpty()) {
                        Text("Open a page in this Journey to grow its tree.",
                            color = Muted, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 14.dp))
                    } else {
                        val map = journey.nodes.associateBy { it.id }
                        journey.nodes.takeLast(50).forEach { node ->
                            var depth = 0
                            var parent = node.parent
                            val seen = mutableSetOf<String>()
                            while (parent != null && depth < 5 && seen.add(parent)) {
                                depth++
                                parent = map[parent]?.parent
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(journey, node.url) }
                                    .padding(start = (depth * 12).dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (depth == 0) "●" else "↳",
                                    color = if (depth == 0) Accent else Muted,
                                    modifier = Modifier.width(24.dp))
                                Column {
                                    Text(node.title.ifBlank { node.url }, color = Bright,
                                        fontSize = 14.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(node.url, color = Muted, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Border, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryPanel(browser: BrowserController, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Library")
        if (browser.store.bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bookmark pages to keep them here.", color = Muted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(browser.store.bookmarks, key = { it.url }) {
                    RowCard(it.title, it.url,
                        { Icon(Icons.Rounded.BookmarkBorder, null, tint = Accent) },
                        { onOpen(it.url) })
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Bright, fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun PanelCaption(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp, color = Muted,
        modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun RowCard(
    title: String, subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .background(Raised, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Bright, fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun ChromeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label,
            tint = if (enabled) Bright else Color(0xFF4A5360),
            modifier = Modifier.size(21.dp))
    }
}
