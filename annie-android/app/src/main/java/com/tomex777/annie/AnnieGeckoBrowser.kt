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
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.tomex777.annie.browser.engine.AnnieBrowserEngineBridge
import com.tomex777.annie.browser.engine.useragent.UserAgentManager
import com.tomex777.annie.browser.engine.useragent.UserAgentPreset
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/**
 * Annie's independent, Firefox/Gecko-powered browser. Script-request browser cards deliberately
 * continue using their isolated WebView profiles; a normal browser must never join those profiles.
 * This host now integrates source-derived Omni session persistence and WebExtension services
 * via AnnieBrowserEngineBridge. See third_party/omni-browser/README.md for source/terms.
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
        val explicitHttp = Regex("^https?://", RegexOption.IGNORE_CASE).find(text)
        // Host:port is not a custom URI scheme. Recognise local development
        // and domain ports BEFORE the dangerous-scheme rejection.
        val hostPort = Regex(
            """^(?:localhost|127(?:\.\d{1,3}){3}|\[[0-9a-fA-F:]+\]|[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+):\d{1,5}(?:[/#?][^\s]*)?$""",
            RegexOption.IGNORE_CASE,
        ).matches(text)
        val loopback = Regex(
            """^(?:localhost|127(?:\.\d{1,3}){3}|\[[0-9a-fA-F:]+\])(?::\d{1,5})?(?:[/#?][^\s]*)?$""",
            RegexOption.IGNORE_CASE,
        ).matches(text)
        val candidate = when {
            explicitHttp != null -> explicitHttp.value.lowercase(java.util.Locale.ROOT) +
                text.substring(explicitHttp.value.length)
            loopback -> "http://$text"
            hostPort -> "https://$text"
            // Do not execute javascript:, file:, data:, intent:, about: or custom schemes.
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
            mime.matches(Regex("[A-Za-z0-9!#^_.+-]+/[A-Za-z0-9!#^_.+*-]+"))
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
                if (url == "about:blank" || AnnieBrowserAddress.resolve(url) == url) {
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
        val total = response.headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.toLongOrNull()?.takeIf { it > 0 }
        val id = UUID.randomUUID().toString()
        val app = context.applicationContext
        AnnieGeckoDownloads.initialize(app)
        AnnieGeckoDownloads.started(id, name, type, total)
        // Register cancellation before starting IO to prevent a race with the UI.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val onProgress: (Long) -> Unit = { bytes ->
                    AnnieGeckoDownloads.progress(id, bytes)
                }
                val savedUri = saveResponse(app, name, type, body, onProgress)
                // Save commits are cancellation-aware; in the rare case that a
                // user cancels just as the commit finishes, remove the output.
                if (AnnieGeckoDownloads.isCancelled(id)) {
                    runCatching {
                        app.contentResolver.delete(savedUri, null, null)
                    }
                    AnnieGeckoDownloads.cancelled(id)
                } else {
                    AnnieGeckoDownloads.completed(id, savedUri)
                    toast(app, "Download complete: $name")
                }
            } catch (_: CancellationException) {
                AnnieGeckoDownloads.cancelled(id)
            } catch (problem: Exception) {
                if (AnnieGeckoDownloads.isCancelled(id)) {
                    AnnieGeckoDownloads.cancelled(id)
                } else {
                    AnnieGeckoDownloads.failed(id, problem.message ?: "Storage unavailable")
                    toast(app, "Download failed: " + (problem.message ?: "storage unavailable"))
                }
            } finally {
                runCatching { body.close() }
                AnnieGeckoDownloads.untrack(id)
            }
        }
        AnnieGeckoDownloads.track(id) {
            job.cancel()
            scope.launch { runCatching { body.close() } }
        }
        job.start()
    }

    /**
     * Actual storage entrypoint; using it from Android tests exercises MediaStore
     * on API 29+ and our FileProvider on API 26-28 with real copied bytes.
     */
    internal suspend fun saveResponse(
        context: Context,
        name: String,
        mime: String,
        body: InputStream,
        onProgress: (Long) -> Unit = {},
    ): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveToDownloads(context, name, mime, body, onProgress)
    } else {
        saveToAppDownloads(context, name, body, onProgress)
    }

    private suspend fun copyBody(
        source: InputStream,
        destination: java.io.OutputStream,
        onProgress: (Long) -> Unit,
    ) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var lastNotification = android.os.SystemClock.elapsedRealtime()
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val count = source.read(buffer)
            if (count < 0) break
            if (count > 0) {
                destination.write(buffer, 0, count)
                copied += count
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastNotification >= 250) {
                    onProgress(copied)
                    lastNotification = now
                }
            }
        }
        destination.flush()
        onProgress(copied)
    }

    private suspend fun saveToDownloads(
        context: Context,
        name: String,
        mime: String,
        body: InputStream,
        onProgress: (Long) -> Unit,
    ): Uri {
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
                copyBody(body, stream, onProgress)
            } ?: throw IllegalStateException("Can't write the download")
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            provider.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (failure: Throwable) {
            runCatching { provider.delete(uri, null, null) }
            throw failure
        }
    }

    private suspend fun saveToAppDownloads(
        context: Context, name: String, body: InputStream, onProgress: (Long) -> Unit,
    ): Uri {
        // Must match the existing FileProvider's Download/Annie/ path on API 26-28.
        val folder = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, "Annie") }
            ?: File(context.filesDir, "downloads/Annie")).apply { mkdirs() }
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { '.' in name }
            ?.let { ".$it" }.orEmpty()
        val destination = (0..200).map { index ->
            File(folder, if (index == 0) name else "$base ($index)$extension")
        }.firstOrNull { !it.exists() }
            ?: throw IllegalStateException("Too many files with the same name")
        val temporary = File.createTempFile("annie-gecko-", ".part", folder)
        try {
            temporary.outputStream().buffered().use { copyBody(body, it, onProgress) }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            check(temporary.renameTo(destination)) { "Couldn't finish the download" }
            return androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".download-files",
                destination,
            )
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
    // Engine services ported from actual Omni Browser source (GPLv3).
    private val upstreamEngine = AnnieBrowserEngineBridge(context, runtime)
    // Original Omni per-site UA settings, separated from Gecko session identities.
    private val userAgentManager = UserAgentManager(context.applicationContext)
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

    private var restoringTabs = true
    private var lastNormalSelectedId = ""

    init {
        AnnieGeckoDownloads.initialize(context)
        val selectedIndex = prefs.getInt("selected", 0)
        val saved = runCatching { JSONArray(prefs.getString("open", "[]")) }.getOrNull()
        if (saved != null) {
            for (i in 0 until saved.length()) {
                // Accept the original URL-only format without losing existing tabs.
                val entry = saved.optJSONObject(i)
                val url = entry?.optString("url") ?: saved.optString(i)
                if (AnnieBrowserAddress.resolve(url) == url) {
                    val savedId = entry?.optString("id").orEmpty()
                        .takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
                    newTab(address = url, desktopMode = entry?.optBoolean("desktop", false) == true,
                        persistedId = savedId)
                }
            }
        }
        if (tabs.isEmpty()) newTab()
        selectedId = tabs[selectedIndex.coerceIn(0, tabs.lastIndex)].id
        lastNormalSelectedId = selectedId
        restoringTabs = false
        persistTabs()
    }

    private fun persistTabs() {
        if (restoringTabs) return
        // Persist every user-created normal tab; truncation silently lost tabs
        // and reset selection when the active tab was beyond the twelfth.
        val normalTabs = tabs.filterNot { it.privateMode }
        val urls = JSONArray()
        normalTabs.forEach { urls.put(JSONObject().put("id", it.id).put("url", it.url).put("desktop", it.desktopMode)) }
        // A private tab never writes its URL, mode, identity or selection to disk.
        val selected = normalTabs.indexOfFirst { it.id == lastNormalSelectedId }.coerceAtLeast(0)
        prefs.edit().putString("open", urls.toString()).putInt("selected", selected).apply()
    }


    fun select(tab: AnnieGeckoTab) {
        if (tab !in tabs) return
        selectedId = tab.id
        if (!tab.privateMode) lastNormalSelectedId = tab.id
        persistTabs()
    }

    fun newTab(
        privateMode: Boolean = false,
        address: String = "about:blank",
        autoLoad: Boolean = true,
        desktopMode: Boolean = false,
        persistedId: String? = null,
    ): AnnieGeckoTab {
        val url = if (address == "about:blank") "about:blank"
            else AnnieBrowserAddress.resolve(address) ?: "about:blank"
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()
        )
        // Preserve existing users' URL-only saved tabs, while granting all new tabs
        // a stable identifier for upstream Gecko SessionState recovery.
        val stableId = persistedId?.takeIf { candidate -> tabs.none { it.id == candidate } }
            ?: UUID.randomUUID().toString()
        val tab = AnnieGeckoTab(stableId, privateMode, session, url)
        tab.desktopMode = desktopMode
        applyDesktopSettings(tab)
        applyUserAgentSettings(tab, url)
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
                    applyUserAgentSettings(tab, url)
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
            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState,
            ) {
                // Real Omni-derived persistence: private and non-web URLs are skipped.
                upstreamEngine.checkpoint(tab.id, sessionState, tab.title, tab.url,
                    tab.privateMode, tab.canBack, tab.canForward)
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
        select(tab)
        if (autoLoad && url != "about:blank") {
            // A failure or Gecko version mismatch safely falls back to the saved URL.
            val recovered = if (!privateMode && persistedId != null)
                upstreamEngine.restoreNormalTab(tab.id, url) else null
            if (recovered == null) session.loadUri(url)
            else runCatching { session.restoreState(recovered) }
                .onFailure { session.loadUri(url) }
        }
        persistTabs()
        return tab
    }

    fun close(tab: AnnieGeckoTab) {
        if (!tab.privateMode) upstreamEngine.forgetNormalTab(tab.id)
        tab.session.close()
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        if (tabs.isEmpty()) newTab()
        else if (selectedId == tab.id) select(tabs[(index - 1).coerceIn(0, tabs.lastIndex)])
        persistTabs()
    }

    fun load(tab: AnnieGeckoTab, address: String): Boolean {
        val url = AnnieBrowserAddress.resolve(address) ?: return false
        tab.error = null
        tab.loading = true
        tab.progress = 0
        // Address-bar navigation carries the original input into Gecko's load
        // handling, including URI fixup and the user-entered navigation source.
        applyUserAgentSettings(tab, url)
        // Switch the Compose viewport immediately from internal Home to Gecko.
        // History remains driven by actual Gecko navigation callbacks.
        return runCatching {
            tab.session.load(GeckoSession.Loader().uri(url).originalInput(address))
            tab.url = url
            if (!tab.privateMode) persistTabs()
            true
        }.getOrElse {
            tab.loading = false
            tab.error = "Couldn't open this address"
            false
        }
    }

    fun reload(tab: AnnieGeckoTab) {
        if (tab.url == "about:blank") return
        if (tab.session.isOpen) tab.session.reload()
        else { tab.session.open(runtime); tab.session.loadUri(tab.url) }
    }

    fun toggleDesktop(tab: AnnieGeckoTab) {
        tab.desktopMode = !tab.desktopMode
        applyDesktopSettings(tab)
        applyUserAgentSettings(tab, tab.url)
        persistTabs()
        tab.session.reload()
    }

    private fun applyDesktopSettings(tab: AnnieGeckoTab) {
        tab.session.settings.userAgentMode = if (tab.desktopMode)
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode = if (tab.desktopMode)
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    /** Hook for Agent 2's opt-in page tool; never enable content injection by default. */
    fun setUniversalCopy(enabled: Boolean, onFinished: () -> Unit = {}) =
        upstreamEngine.setUniversalCopy(enabled, onFinished)

    // These model APIs are the single canonical engine contract for Agent 2's
    // eventual custom Annie Browser settings UI. No duplicate UA controls.
    fun setGlobalUserAgent(preset: UserAgentPreset, custom: String = "") {
        userAgentManager.setGlobalPreset(preset, custom)
        tabs.forEach { applyUserAgentSettings(it, it.url) }
    }

    fun setSiteUserAgent(domain: String, preset: UserAgentPreset, custom: String = "") {
        userAgentManager.addOrUpdateSiteRule(domain, preset, custom)
        tabs.forEach { applyUserAgentSettings(it, it.url) }
    }

    fun removeSiteUserAgent(domain: String) {
        userAgentManager.removeSiteRulesForDomain(domain)
        tabs.forEach { applyUserAgentSettings(it, it.url) }
    }

    private fun applyUserAgentSettings(tab: AnnieGeckoTab, url: String) {
        tab.session.settings.userAgentOverride = userAgentManager.resolveUserAgent(url)
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
        upstreamEngine.close()
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
    // Annie-integrated Omni-derived runtime owns Gecko sessions and file prompts.
    // Agent 2 owns only this user-facing Compose toolbar/tab/surface.
    val universalCopyToggle = remember(model) {
        { enabled: Boolean -> model.setUniversalCopy(enabled) }
    }
    AnnieLegacyGeckoBrowserChrome(
        model = model,
        onExit = onExit,
        universalCopy = universalCopyToggle,
    )
}
