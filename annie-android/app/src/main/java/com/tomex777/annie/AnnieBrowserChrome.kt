/*
 * Annie Browser user-interface integration.
 * Address-field select-all-on-focus behavior adapted from Omni Browser
 * app/src/main/java/com/rebelroot/omni/browser/AdaptiveBrowserChrome.kt
 * Copyright (C) 2026 RebelRoot Ltd, licensed GPL-3.0-or-later.
 * Upstream commit 9de84cc0d4c7cce32c7052e6d7a8ceab4bdc6d16.
 * This is a modified, Annie-branded integration; it is not endorsed by RebelRoot.
 */
package com.tomex777.annie

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/**
 * Annie-facing browser chrome. The engine is supplied by the real Omni-derived browser
 * integration, not reimplemented here. No Gecko runtime, persistence or history is created
 * by this UI. Agent 1 supplies the state and callbacks after porting upstream services.
 *
 * Keep this in a separate file: the existing AnnieGeckoBrowser.kt is Agent 1's integration
 * boundary. The legacy-model bridge below permits incremental integration without racing
 * edits to Agent 1's engine or duplicating a browser.
 */
internal data class AnnieBrowserChromeTab(
    val id: String,
    val title: String,
    val url: String,
    val privateMode: Boolean,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canBack: Boolean = false,
    val canForward: Boolean = false,
    val bookmarked: Boolean = false,
    val error: String? = null,
)

internal data class AnnieBrowserChromeEntry(val title: String, val url: String)

internal data class AnnieBrowserChromeFind(
 val visible: Boolean, val query: String, val current: Int, val total: Int, val found: Boolean,
 val open: () -> Unit, val update: (String) -> Unit, val next: () -> Unit,
 val previous: () -> Unit, val close: () -> Unit,
)

internal data class AnnieBrowserChromeState(
    val tabs: List<AnnieBrowserChromeTab>,
    val activeId: String,
    val bookmarks: List<AnnieBrowserChromeEntry> = emptyList(),
    val history: List<AnnieBrowserChromeEntry> = emptyList(),
    val desktopMode: Boolean = false,
) {
    val active: AnnieBrowserChromeTab? get() = tabs.firstOrNull { it.id == activeId }
    val isNewTab: Boolean get() = active?.url?.let { it.isBlank() || it.equals("about:blank", true) } == true
}

internal class AnnieBrowserChromeActions(
    val exit: () -> Unit,
    val navigate: (String) -> Boolean,
    val back: () -> Unit,
    val forward: () -> Unit,
    val reload: () -> Unit,
    val newTab: (privateMode: Boolean) -> Unit,
    val selectTab: (String) -> Unit,
    val closeTab: (String) -> Unit,
    val toggleBookmark: () -> Unit,
    val openSaved: (String) -> Unit,
    val toggleDesktop: () -> Unit,
    val share: () -> Unit,
    /** Absent until Agent 1's GPLv3 Omni WebExtension engine is actually bound. */
    val universalCopy: ((Boolean) -> Unit)? = null,
)

private val ChromeBorder = Color(0xFF263C52)
private val ChromeSurface = Color(0xFF132338)

/**
 * Pure Compose chrome: functional tabs, persisted library data from the supplied engine,
 * the real download panel slot and a Gecko page slot. Never invents browsing history
 * or fake shortcut destinations. The start page exists only for an un-navigated blank tab.
 */
@Composable
internal fun AnnieBrowserChrome(
    state: AnnieBrowserChromeState,
    actions: AnnieBrowserChromeActions,
    modifier: Modifier = Modifier,
    webContent: @Composable (Modifier) -> Unit,
    downloadsContent: @Composable (Modifier) -> Unit,
    find: AnnieBrowserChromeFind? = null,
) {
    val active = state.active ?: return
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var address by rememberSaveable(active.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(active.url.takeUnless { it == "about:blank" }.orEmpty()))
    }
    var editing by remember(active.id) { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var tabsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var universalCopyEnabled by remember { mutableStateOf(false) }
    val canCreateTab = state.tabs.size < 20
    var navigationSerial by remember { mutableStateOf(0) }
    val pageFocus = remember(active.id) { FocusRequester() }

    LaunchedEffect(active.id) {
        // The extension is global rather than tab-scoped. Switching *any* tab
        // disables it so the menu can never falsely say On for the new tab.
        // This also prevents private tabs from inheriting injected tools.
        if (universalCopyEnabled) {
            universalCopyEnabled = false
            actions.universalCopy?.invoke(false)
        }
    }
    LaunchedEffect(active.id, active.url) {
        if (!editing) address = TextFieldValue(active.url.takeUnless { it == "about:blank" }.orEmpty())
    }
    LaunchedEffect(navigationSerial, state.isNewTab) {
        // The initial Home state has no Gecko page focus target. Wait until a
        // real navigation creates the page; otherwise Go from a blank tab could
        // throw "FocusRequester is not initialized" before onLocationChange.
        if (navigationSerial > 0 && !state.isNewTab) {
            androidx.compose.runtime.withFrameNanos { }
            pageFocus.requestFocus()
            keyboard?.hide()
        }
    }

    fun openUrl(value: String) {
        if (actions.navigate(value)) {
            editing = false
            panel = null
            tabsOpen = false
            focus.clearFocus(force = true)
            keyboard?.hide()
            navigationSerial++
        }
    }
    // The page tool injects a WebExtension into sites. It is explicitly opt-in
    // and must be disabled when the user leaves this browser UI.
    DisposableEffect(actions.universalCopy) {
        // Do not instantiate/install an extension just because the browser UI
        // was first shown or disposed. Only disable one explicitly enabled.
        onDispose {
            if (universalCopyEnabled) actions.universalCopy?.invoke(false)
        }
    }
    BackHandler(enabled = menuOpen || find?.visible == true ||
        panel != null || tabsOpen || editing || active.canBack) {
        when {
            menuOpen -> menuOpen = false
            find?.visible == true -> find.close()
            tabsOpen -> tabsOpen = false
            panel != null -> panel = null
            editing -> { editing = false; focus.clearFocus(force = true); keyboard?.hide() }
            active.canBack -> actions.back()
            else -> actions.exit()
        }
    }

    Column(
        modifier.fillMaxSize().background(BrowserNight)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .testTag("annie_gecko_browser"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.exit, modifier = Modifier.size(48.dp)) {
                Icon(AnnieIcons.Close, contentDescription = "Close Annie Browser", tint = BrowserBrightText)
            }
            Row(
                Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChromeSurface)
                    .border(1.dp, ChromeBorder, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = address,
                    onValueChange = {
                        editing = true
                        address = it.copy(text = it.text.take(4096))
                    },
                    textStyle = TextStyle(fontSize = 14.sp, color = BrowserBrightText),
                    singleLine = true,
                    cursorBrush = SolidColor(BrowserTeal),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { openUrl(address.text) }),
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                        .onFocusChanged { focusState ->
                            // The URL is one editable field; select it all on entry,
                            // as in upstream Omni's responsive address bar.
                            if (focusState.isFocused && !editing) {
                                address = address.copy(selection = TextRange(0, address.text.length))
                                editing = true
                            }
                        }
                        .testTag("annie_browser_tabs_address")
                        .semantics { contentDescription = "Search or enter address" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (address.text.isBlank()) {
                                Text("Search or enter address", fontSize = 13.sp, color = BrowserSoftText,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    },
                )
                // The same action handles IME Go and this accessible in-field button.
                IconButton(
                    onClick = { openUrl(address.text) },
                    modifier = Modifier.size(48.dp).testTag("annie_browser_go"),
                ) {
                    Icon(AnnieIcons.ArrowForward, contentDescription = "Go to address",
                        tint = BrowserTeal)
                }
            }
            Box(
                Modifier.size(48.dp).testTag("annie_browser_tab_switcher")
                    .semantics {
                        role = Role.Button
                        contentDescription = "${state.tabs.size} tabs, show tab switcher"
                    }
                    .clickable { tabsOpen = !tabsOpen; panel = null },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).border(2.dp, BrowserBrightText, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.tabs.size.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = BrowserBrightText)
                }
            }
        }

        if (find?.visible == true) {
            Row(
                Modifier.fillMaxWidth().background(ChromeSurface)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("annie_browser_find_bar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = find.query,
                    onValueChange = { find.update(it.take(1024)) },
                    modifier = Modifier.weight(1f).height(48.dp)
                        .padding(horizontal = 10.dp).testTag("annie_browser_find_input"),
                    textStyle = TextStyle(color = BrowserBrightText, fontSize = 14.sp),
                    cursorBrush = SolidColor(BrowserTeal),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { find.next() }),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (find.query.isEmpty()) Text(
                                "Find in page", color = BrowserSoftText, fontSize = 13.sp)
                            inner()
                        }
                    },
                )
                if (find.query.isNotBlank()) {
                    Text(if (!find.found) "0" else "${find.current}/${find.total}",
                        color = BrowserSoftText, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
                IconButton(onClick = find.previous, modifier = Modifier.size(48.dp)) {
                    Icon(AnnieIcons.ArrowBack, contentDescription = "Previous match",
                        tint = BrowserBrightText)
                }
                IconButton(onClick = find.next, modifier = Modifier.size(48.dp)) {
                    Icon(AnnieIcons.ArrowForward, contentDescription = "Next match",
                        tint = BrowserBrightText)
                }
                IconButton(onClick = find.close, modifier = Modifier.size(48.dp)) {
                    Icon(AnnieIcons.Close, contentDescription = "Close find in page",
                        tint = BrowserBrightText)
                }
            }
        }

        if (active.loading) {
            LinearProgressIndicator(
                progress = { (active.progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BrowserTeal,
                trackColor = ChromeSurface,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            when {
                tabsOpen -> {
                    val columns = when {
                        maxWidth >= 840.dp -> 4
                        maxWidth >= 600.dp -> 3
                        else -> 2
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Tabs", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                color = BrowserBrightText)
                            IconButton(
                                onClick = { actions.newTab(false); tabsOpen = false },
                                enabled = canCreateTab,
                                modifier = Modifier.testTag("annie_browser_new_tab"),
                            ) {
                                Icon(AnnieIcons.Add, contentDescription = "New tab", tint = BrowserTeal)
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize().testTag("annie_browser_tabs_grid"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        ) {
                            items(state.tabs, key = { it.id }) { candidate ->
                                Column(
                                    Modifier.fillMaxWidth().height(140.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(if (candidate.id == active.id) BrowserBubble else ChromeSurface)
                                        .border(1.dp,
                                            if (candidate.id == active.id) BrowserTeal else ChromeBorder,
                                            RoundedCornerShape(15.dp))
                                        .clickable {
                                            actions.selectTab(candidate.id)
                                            tabsOpen = false
                                        }
                                        .testTag("annie_browser_tab_" + candidate.id),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (candidate.privateMode) "Private" else "Tab",
                                            fontSize = 12.sp, color = BrowserSoftText,
                                            modifier = Modifier.weight(1f))
                                        IconButton(onClick = { actions.closeTab(candidate.id) },
                                            modifier = Modifier.size(48.dp)) {
                                            Icon(AnnieIcons.Close,
                                                contentDescription = "Close " + candidate.title,
                                                tint = BrowserSoftText)
                                        }
                                    }
                                    Text(candidate.title.ifBlank { "New tab" },
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        color = BrowserBrightText, fontSize = 14.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.url.takeUnless { it == "about:blank" }.orEmpty(),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = BrowserSoftText, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                panel != null -> {
                    val title = panel.orEmpty()
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                color = BrowserBrightText)
                            IconButton(onClick = { panel = null }) {
                                Icon(AnnieIcons.Close, contentDescription = "Close " + title,
                                    tint = BrowserBrightText)
                            }
                        }
                        if (title == "Downloads") {
                            downloadsContent(Modifier.fillMaxSize())
                        } else {
                            val savedEntries = if (title == "Bookmarks") state.bookmarks else state.history
                            if (savedEntries.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (title == "Bookmarks") "No saved bookmarks" else "No browsing history",
                                        color = BrowserSoftText)
                                }
                            } else {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(savedEntries, key = { it.url }) { entry ->
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .clickable { actions.openSaved(entry.url); panel = null }
                                                .padding(horizontal = 20.dp, vertical = 14.dp),
                                        ) {
                                            Text(entry.title.ifBlank { entry.url }, color = BrowserBrightText,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.url, color = BrowserSoftText, fontSize = 12.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state.isNewTab -> {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 38.dp)
                            .testTag("annie_browser_home"),
                    ) {
                        Text("Annie Browser", color = BrowserBrightText,
                            fontSize = if (maxWidth >= 600.dp) 32.sp else 26.sp,
                            fontWeight = FontWeight.SemiBold)
                        if (active.privateMode) {
                            Text("Private tab", color = BrowserSoftText,
                                fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (state.bookmarks.isNotEmpty()) {
                            Text("Bookmarks", color = BrowserSoftText,
                                modifier = Modifier.padding(top = 32.dp, bottom = 10.dp),
                                fontSize = 12.sp)
                            LazyColumn {
                                items(state.bookmarks.take(12), key = { it.url }) { entry ->
                                    Text(
                                        entry.title.ifBlank { entry.url },
                                        color = BrowserBrightText,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            actions.openSaved(entry.url)
                                        }.padding(vertical = 13.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        active.error?.let { error ->
                            Text(error, modifier = Modifier.fillMaxWidth()
                                .background(ChromeSurface).padding(10.dp),
                                color = BrowserBrightText, fontSize = 12.sp)
                        }
                        Box(Modifier.fillMaxSize().focusRequester(pageFocus).focusable()) {
                            webContent(Modifier.fillMaxSize().testTag("annie_browser_tabs_webview"))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ChromeSurface).height(54.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.back, enabled = active.canBack,
                modifier = Modifier.size(48.dp).testTag("annie_browser_back")) {
                Icon(AnnieIcons.ArrowBack, contentDescription = "Go back",
                    tint = if (active.canBack) BrowserBrightText else BrowserSoftText.copy(alpha = 0.45f))
            }
            IconButton(onClick = actions.forward, enabled = active.canForward,
                modifier = Modifier.size(48.dp)) {
                Icon(AnnieIcons.ArrowForward, contentDescription = "Go forward",
                    tint = if (active.canForward) BrowserBrightText else BrowserSoftText.copy(alpha = 0.45f))
            }
            IconButton(onClick = actions.reload, enabled = !state.isNewTab,
                modifier = Modifier.size(48.dp)) {
                Icon(AnnieIcons.Refresh, contentDescription = "Reload page",
                    tint = if (state.isNewTab) BrowserSoftText.copy(alpha = 0.45f) else BrowserBrightText)
            }
            IconButton(onClick = actions.toggleBookmark,
                enabled = !active.privateMode && !state.isNewTab,
                modifier = Modifier.size(48.dp)) {
                Icon(AnnieIcons.Bookmark,
                    contentDescription = if (active.bookmarked) "Remove bookmark" else "Bookmark page",
                    tint = if (active.bookmarked) BrowserTeal else BrowserSoftText)
            }
            Box {
                IconButton(onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp)) {
                    Icon(AnnieIcons.More, contentDescription = "Browser menu", tint = BrowserBrightText)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("New tab") }, enabled = canCreateTab, onClick = {
                        menuOpen = false; actions.newTab(false); panel = null; tabsOpen = false
                    })
                    DropdownMenuItem(text = { Text("New private tab") }, enabled = canCreateTab, onClick = {
                        menuOpen = false; actions.newTab(true); panel = null; tabsOpen = false
                    })
                    DropdownMenuItem(text = { Text("Bookmarks") }, onClick = {
                        menuOpen = false; panel = "Bookmarks"; tabsOpen = false
                    })
                    DropdownMenuItem(text = { Text("History") }, onClick = {
                        menuOpen = false; panel = "History"; tabsOpen = false
                    })
                    DropdownMenuItem(text = { Text("Downloads") }, onClick = {
                        menuOpen = false; panel = "Downloads"; tabsOpen = false
                    })
                    DropdownMenuItem(text = { Text(if (state.desktopMode) "Mobile site" else "Desktop site") },
                        onClick = { menuOpen = false; actions.toggleDesktop() })
                    if (actions.universalCopy != null && !active.privateMode) {
                        DropdownMenuItem(
                            text = { Text(if (universalCopyEnabled) "Universal Copy: On" else "Universal Copy: Off") },
                            onClick = {
                                menuOpen = false
                                universalCopyEnabled = !universalCopyEnabled
                                actions.universalCopy.invoke(universalCopyEnabled)
                            },
                        )
                    }
                    DropdownMenuItem(text = { Text("Share link") },
                        onClick = { menuOpen = false; actions.share() })
                }
            }
        }
    }
}

/**
 * Temporary adapter for the existing Annie Gecko tab model. It is intentionally kept
 * outside AnnieGeckoBrowser.kt so Agent 1 can replace this bridge with upstream Omni
 * BrowserViewModel state without discarding UX or changing Gecko profile ownership.
 * File pickers remain controlled by the existing owning browser screen.
 */
@Composable
internal fun AnnieLegacyGeckoBrowserChrome(
    model: AnnieGeckoBrowserModel,
    onExit: () -> Unit,
    /** Pass model::setUniversalCopy only after the actual Omni engine PR is integrated. */
    universalCopy: ((Boolean) -> Unit)? = null,
    find: AnnieBrowserChromeFind? = null,
) {
    val tab = model.active ?: return
    val state = AnnieBrowserChromeState(
        tabs = model.tabs.map { item ->
            AnnieBrowserChromeTab(
                id = item.id, title = item.title, url = item.url, privateMode = item.privateMode,
                loading = item.loading, progress = item.progress, canBack = item.canBack,
                canForward = item.canForward, bookmarked = model.isBookmarked(item),
                error = item.error,
            )
        },
        activeId = tab.id,
        bookmarks = model.bookmarks.map { AnnieBrowserChromeEntry(it.title, it.url) },
        history = model.history.map { AnnieBrowserChromeEntry(it.title, it.url) },
        desktopMode = tab.desktopMode,
    )
    val actions = remember(model, tab.id, universalCopy) {
        AnnieBrowserChromeActions(
            exit = onExit,
            navigate = { input -> model.load(tab, input) },
            back = { tab.session.goBack() },
            forward = { tab.session.goForward() },
            reload = { model.reload(tab) },
            newTab = { isPrivate -> model.newTab(privateMode = isPrivate) },
            selectTab = { id -> model.tabs.firstOrNull { it.id == id }?.let(model::select) },
            closeTab = { id -> model.tabs.firstOrNull { it.id == id }?.let(model::close) },
            toggleBookmark = { model.toggleBookmark(tab) },
            openSaved = { address -> model.load(tab, address) },
            toggleDesktop = { model.toggleDesktop(tab) },
            share = { model.share(tab) },
            universalCopy = universalCopy,
        )
    }
    AnnieBrowserChrome(
        state = state,
        actions = actions,
        webContent = { modifier ->
            key(tab.id) {
                AndroidView(
                    factory = { context -> GeckoView(context).apply { setSession(tab.session) } },
                    modifier = modifier,
                    onRelease = { view -> view.releaseSession() },
                )
            }
        },
        downloadsContent = { modifier -> AnnieGeckoDownloadPanel(modifier) },
        find = find,
    )
}
