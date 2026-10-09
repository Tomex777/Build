package com.tomex777.annie

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import org.json.JSONArray
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/**
 * Annie's independent, Firefox/Gecko-powered browser. Script-request browser cards deliberately
 * continue using their isolated WebView profiles; a normal browser must never join those profiles.
 * No source code is copied from Omni Browser; it is an architectural reference only.
 */
internal object AnnieGeckoRuntime {
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        return runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
    }
}

internal object AnnieBrowserAddress {
    fun resolve(input: String): String? {
        val text = input.trim().take(4096)
        if (text.isEmpty()) return null
        val candidate = when {
            text.startsWith("https://", true) || text.startsWith("http://", true) -> text
            // Never execute javascript:, file:, data:, intent: etc. entered in the omnibox.
            "://" in text || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(text) ->
                return null
            !text.contains(' ') && (text.contains('.') || text.startsWith("localhost")) ->
                "https://$text"
            else -> "https://www.google.com/search?q=" +
                URLEncoder.encode(text, Charsets.UTF_8.name())
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null) return null
        return uri.toASCIIString()
    }
}

internal data class AnnieBrowserVisit(val title: String, val url: String)

private class AnnieBrowserLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("annie_gecko_library_v1", Context.MODE_PRIVATE)
    var bookmarks by mutableStateOf(read("bookmarks"))
        private set
    var history by mutableStateOf(read("history"))
        private set

    private fun read(name: String): List<AnnieBrowserVisit> = runCatching {
        val array = JSONArray(prefs.getString(name, "[]"))
        buildList {
            for (i in 0 until minOf(array.length(), 300)) {
                val entry = array.optJSONObject(i) ?: continue
                val url = entry.optString("url")
                if (AnnieBrowserAddress.resolve(url) == url) {
                    add(AnnieBrowserVisit(entry.optString("title").take(150), url))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun save(name: String, entries: List<AnnieBrowserVisit>) {
        val array = JSONArray()
        entries.forEach { array.put(org.json.JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString(name, array.toString()).apply()
    }

    fun visit(title: String, url: String) {
        if (AnnieBrowserAddress.resolve(url) != url) return
        history = (listOf(AnnieBrowserVisit(title.take(150), url)) + history.filterNot { it.url == url }).take(250)
        save("history", history)
    }

    fun bookmark(title: String, url: String) {
        if (AnnieBrowserAddress.resolve(url) != url) return
        bookmarks = if (bookmarks.any { it.url == url }) bookmarks.filterNot { it.url == url }
        else listOf(AnnieBrowserVisit(title.take(150), url)) + bookmarks
        save("bookmarks", bookmarks)
    }
}

internal class AnnieGeckoTab(
    val id: String,
    val privateMode: Boolean,
    val session: GeckoSession,
    startUrl: String,
) {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("New tab")
    var loading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktopMode by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

internal class AnnieGeckoBrowserModel(private val context: Context) {
    private val runtime = AnnieGeckoRuntime.get(context)
    private val library = AnnieBrowserLibrary(context)
    val tabs = mutableStateListOf<AnnieGeckoTab>()
    var selectedId by mutableStateOf("")
        private set
    val bookmarks get() = library.bookmarks
    val history get() = library.history
    val active: AnnieGeckoTab? get() = tabs.firstOrNull { it.id == selectedId }

    init { newTab() }

    fun select(tab: AnnieGeckoTab) { selectedId = tab.id }

    fun newTab(privateMode: Boolean = false, address: String = "https://www.google.com/") {
        val url = AnnieBrowserAddress.resolve(address) ?: "https://www.google.com/"
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()
        )
        val tab = AnnieGeckoTab(UUID.randomUUID().toString(), privateMode, session, url)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!url.isNullOrBlank()) {
                    tab.url = url
                    if (!tab.privateMode) library.visit(tab.title, url)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canBack = canGoBack
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canForward = canGoForward
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 0
                tab.error = null
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress.coerceIn(0, 100)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                if (!success) tab.error = "Couldn't load this page. Check your connection."
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title?.take(150)?.ifBlank { "New tab" } ?: "New tab"
                if (!tab.privateMode) library.visit(tab.title, tab.url)
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                download(response.uri, tab.privateMode)
            }
            override fun onCrash(session: GeckoSession) {
                tab.error = "Browser tab crashed. Reload to recover."
                tab.loading = false
            }
        }
        session.open(runtime)
        tabs.add(tab)
        selectedId = tab.id
        session.loadUri(url)
    }

    fun close(tab: AnnieGeckoTab) {
        tab.session.close()
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        if (tabs.isEmpty()) newTab()
        else if (selectedId == tab.id) selectedId = tabs[(index - 1).coerceIn(0, tabs.lastIndex)].id
    }

    fun load(tab: AnnieGeckoTab, address: String): Boolean {
        val url = AnnieBrowserAddress.resolve(address) ?: return false
        tab.error = null
        tab.session.loadUri(url)
        return true
    }

    fun reload(tab: AnnieGeckoTab) {
        if (tab.session.isOpen) tab.session.reload()
        else { tab.session.open(runtime); tab.session.loadUri(tab.url) }
    }

    fun toggleDesktop(tab: AnnieGeckoTab) {
        tab.desktopMode = !tab.desktopMode
        tab.session.settings.userAgentMode = if (tab.desktopMode)
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode = if (tab.desktopMode)
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        tab.session.reload()
    }

    fun toggleBookmark(tab: AnnieGeckoTab) {
        if (!tab.privateMode) library.bookmark(tab.title, tab.url)
    }
    fun isBookmarked(tab: AnnieGeckoTab): Boolean = bookmarks.any { it.url == tab.url }

    private fun download(raw: String, privateMode: Boolean) {
        // Downloads are intentionally explicit, and never logged to browser history.
        val url = AnnieBrowserAddress.resolve(raw)
        if (url == null) {
            Toast.makeText(context, "Unsupported download link", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setTitle(uri.lastPathSegment?.take(90) ?: "Download")
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Couldn't start download", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(tab: AnnieGeckoTab) {
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, tab.url), "Share page"
        ))
    }

    fun destroy() {
        tabs.toList().forEach { it.session.close() }
        tabs.clear()
    }
}

/** Gecko browser surface is isolated from script-automation WebView sessions. */
@Composable
internal fun AnnieGeckoStandaloneBrowser(
    onExit: () -> Unit,
    onOwn: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    val context = LocalContext.current
    val state = remember(context) { runCatching { AnnieGeckoBrowserModel(context) } }
    val model = state.getOrNull()
    if (model == null) {
        // Gecko can't initialize on this device: keep existing WebView browser usable.
        AnnieStandaloneTabs(onExit = onExit, onOwn = onOwn, onRelease = onRelease)
        return
    }
    DisposableEffect(model) { onDispose { model.destroy() } }
    val tab = model.active ?: return
    var address by remember(tab.id) { mutableStateOf(tab.url) }
    var editing by remember(tab.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab.url) { if (!editing) address = tab.url }
    BackHandler(enabled = panel != null || tab.canBack) {
        if (panel != null) panel = null else tab.session.goBack()
    }

    Column(
        Modifier.fillMaxSize().background(BrowserNight)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .testTag("annie_gecko_browser")
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = onExit) { Text("✕", color = BrowserBrightText) }
            model.tabs.forEach { item ->
                TextButton(onClick = { model.select(item); panel = null },
                    modifier = Modifier.testTag("annie_browser_tab_" + item.id)) {
                    Text((if (item.privateMode) "◈ " else "") +
                            (if (item.id == tab.id) "● " else "") +
                            item.title.take(15),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (item.id == tab.id) BrowserTeal else BrowserSoftText)
                }
                TextButton(onClick = { model.close(item) }) { Text("×", color = BrowserSoftText) }
            }
            TextButton(onClick = { model.newTab(); panel = null },
                modifier = Modifier.testTag("annie_browser_new_tab")) { Text("+", color = BrowserTeal) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { editing = true; address = it.take(4096) },
                modifier = Modifier.weight(1f).testTag("annie_browser_tabs_address"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (model.load(tab, address)) editing = false
                }),
                placeholder = { Text("Search or enter address") },
            )
            Button(onClick = { if (model.load(tab, address)) editing = false }) { Text("Go") }
        }
        if (tab.loading) LinearProgressIndicator(
            progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = BrowserTeal)
        tab.error?.let { Text(it, Modifier.padding(12.dp), color = BrowserSoftText) }
        if (panel != null) {
            val entries = if (panel == "Bookmarks") model.bookmarks else model.history
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(panel.orEmpty(), Modifier.padding(16.dp), color = BrowserBrightText)
                TextButton(onClick = { panel = null }) { Text("Close") }
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                items(entries.size) { i ->
                    val entry = entries[i]
                    TextButton(onClick = { model.load(tab, entry.url); panel = null }) {
                        Column {
                            Text(entry.title.ifBlank { entry.url }, color = BrowserBrightText, maxLines = 1)
                            Text(entry.url, color = BrowserSoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else key(tab.id) {
            AndroidView(
                factory = { GeckoView(it).apply { setSession(tab.session) } },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("annie_browser_tabs_webview"),
                onRelease = { view -> view.releaseSession() },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { tab.session.goBack() }, enabled = tab.canBack) { Text("‹", color = BrowserBrightText) }
            TextButton(onClick = { tab.session.goForward() }, enabled = tab.canForward) { Text("›", color = BrowserBrightText) }
            TextButton(onClick = { model.reload(tab) }) { Text("⟳", color = BrowserBrightText) }
            TextButton(onClick = { model.toggleBookmark(tab) }, enabled = !tab.privateMode) {
                Text(if (model.isBookmarked(tab)) "★" else "☆", color = BrowserTeal)
            }
            TextButton(onClick = { menuOpen = true }) { Text("⋮", color = BrowserBrightText) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("New private tab") }, onClick = {
                    menuOpen = false; model.newTab(privateMode = true)
                })
                DropdownMenuItem(text = { Text("Bookmarks") }, onClick = {
                    menuOpen = false; panel = "Bookmarks"
                })
                DropdownMenuItem(text = { Text("History") }, onClick = {
                    menuOpen = false; panel = "History"
                })
                DropdownMenuItem(text = { Text(if (tab.desktopMode) "Mobile site" else "Desktop site") }, onClick = {
                    menuOpen = false; model.toggleDesktop(tab)
                })
                DropdownMenuItem(text = { Text("Share link") }, onClick = {
                    menuOpen = false; model.share(tab)
                })
            }
        }
    }
}
