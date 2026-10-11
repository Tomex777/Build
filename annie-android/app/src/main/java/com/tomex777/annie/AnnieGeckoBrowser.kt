package com.tomex777.annie

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.SideEffect
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
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.mozilla.geckoview.GeckoResult
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

/**
 * File input belongs to the browser's owning Android Activity, not the page.
 * Site JavaScript only receives a URI after the user has selected a document.
 */
internal object AnnieGeckoFilePicker {
    fun intent(types: Array<String>?, multiple: Boolean): Intent {
        val allowed = types.orEmpty().map(String::trim).filter { mime ->
            mime.matches(Regex("[A-Za-z0-9!#internal data class AnnieBrowserVisit(val title: String, val url: String)^_.+-]+/[A-Za-z0-9!#internal data class AnnieBrowserVisit(val title: String, val url: String)^_.+*-]+"))
        }.distinct()
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(allowed.singleOrNull() ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply {
                if (allowed.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, allowed.toTypedArray())
                if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
    }

    fun uris(data: Intent?): Array<Uri> {
        if (data == null) return emptyArray()
        val all = mutableListOf<Uri>()
        data.data?.let(all::add)
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let(all::add)
            }
        }
        return all.distinct().toTypedArray()
    }
}

private data class PendingGeckoFilePrompt(
    val prompt: GeckoSession.PromptDelegate.FilePrompt,
    val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
)

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

/**
 * GeckoView's onExternalResponse supplies the *existing* response body. Do not
 * issue a second, unauthenticated DownloadManager request: that breaks cookies,
 * one-time links, POST downloads and blob: URLs.
 *
 * Stream handling is delegated to a long-lived IO scope, so leaving the browser
 * screen does not cancel a download already accepted by Gecko.
 */
internal object AnnieGeckoDownloadStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun filename(uri: String, disposition: String?, mimeType: String?): String {
        val extended = Regex(
            """filename\*\s*=\s*(?:UTF-8'')?["]?([^;"]+)""",
            RegexOption.IGNORE_CASE,
        ).find(disposition.orEmpty())?.groupValues?.getOrNull(1)
            ?.let { raw -> runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() }
        val basic = Regex("""filename\s*=\s*"?([^;"]+)""", RegexOption.IGNORE_CASE)
            .find(disposition.orEmpty())?.groupValues?.getOrNull(1)
        val pathName = runCatching { URI(uri).path?.substringAfterLast('/') }.getOrNull()
        val candidate = (extended ?: basic ?: pathName).orEmpty().trim()
            .substringAfterLast('/').substringAfterLast('\\')
        val cleaned = candidate.map { char ->
            if (char.code < 32 || char.code == 127 ||
                char in charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')) '_'
            else char
        }.joinToString("").trim().trim('.').take(120).ifBlank { "download" }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
            mimeType.orEmpty().substringBefore(';').trim().lowercase()
        )
        return if ('.' !in cleaned && !extension.isNullOrBlank())
            "$cleaned.$extension" else cleaned
    }

    fun accept(context: Context, response: WebResponse) {
        // A Gecko response body may be a non-replayable blob: stream.
        val body = response.body ?: run {
            toast(context, "This page provided no downloadable data")
            return
        }
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", ignoreCase = true)
        }?.value
        val type = response.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
            ?: "application/octet-stream"
        val name = filename(response.uri, disposition, type)
        val app = context.applicationContext
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToDownloads(app, name, type, body)
                    toast(app, "Saved to Downloads/Annie: $name")
                } else {
                    saveToAppDownloads(app, name, body)
                    toast(app, "Saved in Annie app downloads: $name")
                }
            } catch (problem: Exception) {
                toast(app, "Download failed: " + (problem.message ?: "storage unavailable"))
            } finally {
                runCatching { body.close() }
            }
        }
    }

    private suspend fun copyBody(source: InputStream, destination: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val count = source.read(buffer)
            if (count < 0) break
            if (count > 0) destination.write(buffer, 0, count)
        }
        destination.flush()
    }

    private suspend fun saveToDownloads(
        context: Context, name: String, mime: String, body: InputStream,
    ) {
        val provider = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Annie")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Can't create the download destination")
        try {
            provider.openOutputStream(uri, "w")?.use { stream ->
                copyBody(body, stream)
            } ?: throw IllegalStateException("Can't write the download")
            provider.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (failure: Throwable) {
            runCatching { provider.delete(uri, null, null) }
            throw failure
        }
    }

    private suspend fun saveToAppDownloads(context: Context, name: String, body: InputStream) {
        val folder = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")).apply { mkdirs() }
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { '.' in name }
            ?.let { ".$it" }.orEmpty()
        val destination = (0..200).map { index ->
            File(folder, if (index == 0) name else "$base ($index)$extension")
        }.firstOrNull { !it.exists() }
            ?: throw IllegalStateException("Too many files with the same name")
        val temporary = File.createTempFile("annie-gecko-", ".part", folder)
        try {
            temporary.outputStream().buffered().use { copyBody(body, it) }
            check(temporary.renameTo(destination)) { "Couldn't finish the download" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message.take(150), Toast.LENGTH_LONG).show()
        }
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
    private val prefs = context.applicationContext.getSharedPreferences("annie_gecko_tabs_v1", Context.MODE_PRIVATE)
    val tabs = mutableStateListOf<AnnieGeckoTab>()
    var selectedId by mutableStateOf("")
        private set
    val bookmarks get() = library.bookmarks
    val history get() = library.history
    val active: AnnieGeckoTab? get() = tabs.firstOrNull { it.id == selectedId }

    // Installed by the Compose screen that owns ActivityResult's document picker.
    // No file chooser can run silently or be triggered from a closed browser.
    var requestFilePicker: ((
        GeckoSession.PromptDelegate.FilePrompt,
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    ) -> Unit)? = null

    init {
        val saved = runCatching { JSONArray(prefs.getString("open", "[]")) }.getOrNull()
        if (saved != null) {
            for (i in 0 until minOf(saved.length(), 12)) {
                val url = saved.optString(i)
                if (AnnieBrowserAddress.resolve(url) == url) newTab(address = url)
            }
        }
        if (tabs.isEmpty()) newTab()
    }

    private fun persistTabs() {
        val urls = JSONArray()
        tabs.filterNot { it.privateMode }.take(12).forEach { urls.put(it.url) }
        prefs.edit().putString("open", urls.toString()).apply()
    }


    fun select(tab: AnnieGeckoTab) { selectedId = tab.id }

    fun newTab(
        privateMode: Boolean = false,
        address: String = "https://www.google.com/",
        autoLoad: Boolean = true,
    ): AnnieGeckoTab {
        val url = AnnieBrowserAddress.resolve(address) ?: "https://www.google.com/"
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()
        )
        val tab = AnnieGeckoTab(UUID.randomUUID().toString(), privateMode, session, url)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): org.mozilla.geckoview.GeckoResult<GeckoSession>? {
                if (tabs.size >= 20 || AnnieBrowserAddress.resolve(uri) != uri) return null
                return org.mozilla.geckoview.GeckoResult.fromValue(
                    newTab(privateMode = tab.privateMode, address = uri, autoLoad = false).session
                )
            }
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!url.isNullOrBlank()) {
                    tab.url = url
                    if (!tab.privateMode) { library.visit(tab.title, url); persistTabs() }
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
                AnnieGeckoDownloadStore.accept(context, response)
            }
            override fun onCrash(session: GeckoSession) {
                tab.error = "Browser tab crashed. Reload to recover."
                tab.loading = false
            }
        }
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val answer = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val picker = requestFilePicker
                if (picker == null) answer.complete(prompt.dismiss())
                else picker(prompt, answer)
                return answer
            }
        }
        session.open(runtime)
        tabs.add(tab)
        selectedId = tab.id
        if (autoLoad) session.loadUri(url)
        persistTabs()
        return tab
    }

    fun close(tab: AnnieGeckoTab) {
        tab.session.close()
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        if (tabs.isEmpty()) newTab()
        else if (selectedId == tab.id) selectedId = tabs[(index - 1).coerceIn(0, tabs.lastIndex)].id
        persistTabs()
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
    var pendingFilePicker by remember(model) { mutableStateOf<PendingGeckoFilePrompt?>(null) }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingFilePicker
        pendingFilePicker = null
        if (pending != null) {
            val chosen = if (result.resultCode == Activity.RESULT_OK)
                AnnieGeckoFilePicker.uris(result.data) else emptyArray()
            val response = if (chosen.isEmpty()) pending.prompt.dismiss()
                else if (pending.prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)
                    pending.prompt.confirm(context.applicationContext, chosen)
                else pending.prompt.confirm(context.applicationContext, chosen.first())
            pending.result.complete(response)
        }
    }
    SideEffect {
        model.requestFilePicker = { prompt, answer ->
            if (pendingFilePicker != null) {
                // Only one Android picker can be active at a time.
                answer.complete(prompt.dismiss())
            } else if (prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER) {
                // Directory access must not be approximated by disclosing a single file.
                answer.complete(prompt.dismiss())
            } else {
                pendingFilePicker = PendingGeckoFilePrompt(prompt, answer)
                runCatching {
                    documentPicker.launch(
                        AnnieGeckoFilePicker.intent(
                            prompt.mimeTypes,
                            prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE,
                        )
                    )
                }.onFailure {
                    pendingFilePicker = null
                    answer.complete(prompt.dismiss())
                }
            }
        }
    }
    DisposableEffect(model) {
        onDispose {
            pendingFilePicker?.let { pending ->
                runCatching { pending.result.complete(pending.prompt.dismiss()) }
                pendingFilePicker = null
            }
            model.requestFilePicker = null
            model.destroy()
        }
    }
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
