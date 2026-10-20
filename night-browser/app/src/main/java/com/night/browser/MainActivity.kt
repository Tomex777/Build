package com.night.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.SolidColor
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(current.url, current.id) {
        if (!addressFocused) address = current.url.takeUnless { it == "about:blank" }.orEmpty()
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
        page = if (tab.url.isBlank() || tab.url == "about:blank") Page.HOME else Page.WEB
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
                    Page.HOME -> NightHomePanel(
                        browser,
                        onSubmit = { browser.navigate(it); page = Page.WEB },
                        onOpen = { browser.navigate(it); page = Page.WEB },
                        onJourneys = { page = Page.JOURNEYS },
                        onLibrary = { page = Page.LIBRARY }
                    )
                    Page.WEB -> WebSurface(browser, current)
                    Page.TABS -> NightTabGrid(browser,
                        onOpen = { openTab(it) },
                        onClose = { browser.closeTab(it.id) },
                        onNew = { browser.newTab(); page = Page.HOME })
                    Page.JOURNEYS -> NightJourneysPanel(browser,
                        onNew = { newJourneyDialog = true },
                        onOpen = { j, node ->
                            browser.newTab(node.url, j.id, node.id)
                            page = Page.WEB
                        },
                        onSelect = { j ->
                            browser.newTab(journeyId = j.id)
                            page = Page.HOME
                        })
                    Page.LIBRARY -> LibraryPanel(browser,
                        onOpen = { browser.newTab(it); page = Page.WEB })
                    Page.TIME_MACHINE -> TimeMachinePanel(browser, openSaved)
                }
            }

            if (current.isLoading && page == Page.WEB) {
                LinearProgressIndicator(
                    progress = { (current.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent, trackColor = Border
                )
            }

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
                NightTabCountIcon(browser.tabs.size) {
                    if (page == Page.WEB) {
                        browser.captureActiveTab { page = Page.TABS }
                    } else {
                        page = Page.TABS
                    }
                }
                ChromeIcon(Icons.Rounded.MoreHoriz, "More") { menuExpanded = true }
            }
        }
    }

    if (menuExpanded) {
        NightMenuSheet(
            canSave = current.url.startsWith("http"),
            onDismiss = { menuExpanded = false },
            onNew = { browser.newTab(); page = Page.HOME },
            onJourneys = { page = Page.JOURNEYS },
            onLibrary = { page = Page.LIBRARY },
            onTimeMachine = { page = Page.TIME_MACHINE },
            onBookmark = { browser.bookmark() },
            onSave = { browser.savePdfSnapshot() },
            onReload = { browser.reload(); page = Page.WEB }
        )
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
private fun WebSurface(browser: BrowserController, tab: BrowserTab) {
    val context = LocalContext.current
    val view = remember(tab.id) { GeckoView(context) }
    DisposableEffect(view) {
        browser.attachedView = view
        onDispose {
            if (browser.attachedView === view) browser.attachedView = null
            view.releaseSession()
        }
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
private fun TimeMachinePanel(browser: BrowserController, openSaved: (SavedPage) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Time Machine")
        Text("Saved PDF copies · not interactive webpage archives",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontSize = 12.sp, color = Muted)
        if (browser.store.savedPages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Save a PDF copy from the page menu.", color = Muted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(browser.store.savedPages, key = { it.fileName }) { item ->
                    RowCard(
                        item.title, DateFormat.getDateTimeInstance().format(Date(item.timestamp)),
                        { Icon(Icons.Rounded.PictureAsPdf, null, tint = Accent) },
                        { openSaved(item) }
                    )
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
