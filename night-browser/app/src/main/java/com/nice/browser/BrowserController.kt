package com.nice.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.io.File
import java.util.UUID

class BrowserTab(
    val id: String,
    val session: GeckoSession,
    val journeyId: String
) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var lastNode by mutableStateOf<String?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
}

class BrowserController(private val appContext: Context) {
    val store = JourneyStore(appContext)
    private val runtime: GeckoRuntime = GeckoRuntime.create(appContext)
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableStateOf("")
        private set
    var contextLink by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var attachedView: GeckoView? = null

    val active: BrowserTab get() = tabs.first { it.id == activeId }

    init {
        newTab()
    }

    fun newTab(
        url: String? = null,
        journeyId: String = store.journeys.first().id,
        parentNodeId: String? = null,
    ): BrowserTab {
        val session = GeckoSession()
        val tab = BrowserTab(UUID.randomUUID().toString(), session, journeyId)
        tab.lastNode = parentNodeId
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank()) return
                if (tab.url != url) {
                    tab.url = url
                    store.record(tab.journeyId, url, tab.title, tab.lastNode)?.let { tab.lastNode = it }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val popup = newTab(journeyId = tab.journeyId)
                return GeckoResult.fromValue(popup.session)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    store.renameNode(tab.journeyId, tab.lastNode, title)
                }
            }

            override fun onContextMenu(
                session: GeckoSession, screenX: Int, screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                element.linkUri?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let {
                    contextLink = it
                }
            }

            override fun onCrash(session: GeckoSession) {
                notice = "This tab crashed. Close it and open a new tab."
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.isLoading = true
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.isLoading = false
                if (!success) notice = "This page could not finish loading."
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }
        }
        session.open(runtime)
        tabs.add(tab)
        select(tab.id)
        url?.takeIf { it.isNotBlank() }?.let(session::loadUri)
        return tab
    }

    /** Capture actual rendered pixels, never a fabricated webpage preview. */
    fun captureActiveTab(onFinished: () -> Unit) {
        val tab = tabs.firstOrNull { it.id == activeId }
        val surface = attachedView
        if (tab == null || surface?.session !== tab.session || !tab.url.startsWith("http")) {
            onFinished()
            return
        }
        runCatching {
            surface.capturePixels().accept({ bitmap ->
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    val width = 360
                    val height = (bitmap.height.toFloat() * width / bitmap.width)
                        .toInt().coerceIn(1, 800)
                    tab.thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)
                }
                onFinished()
            }, {
                onFinished()
            })
        }.onFailure { onFinished() }
    }

    fun select(tabId: String) {
        if (tabs.none { it.id == tabId }) return
        activeId = tabId
        tabs.forEach { it.session.setActive(it.id == activeId) }
    }

    fun closeTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tabs.size == 1) newTab(journeyId = tab.journeyId)
        else if (tabId == activeId) select(tabs.first { it.id != tabId }.id)
        tabs.remove(tab)
        tab.thumbnail = null
        tab.session.close()
    }

    fun navigate(input: String) {
        val address = AddressInput.resolve(input)
        if (address == null) {
            notice = "Enter a valid website address or search query."
        } else {
            active.session.loadUri(address)
        }
    }

    fun goBack() { if (active.canGoBack) active.session.goBack() }
    fun goForward() { if (active.canGoForward) active.session.goForward() }
    fun reload() { active.session.reload() }
    fun bookmark() { store.bookmark(active.url, active.title); notice = "Added to Library" }

    fun newPeekSession(url: String): GeckoSession =
        GeckoSession().also { it.open(runtime); it.loadUri(url) }

    fun savePdfSnapshot() {
        val tab = active
        if (!tab.url.startsWith("http")) {
            notice = "Open a webpage before saving a copy."
            return
        }
        val snapshotUrl = tab.url
        val snapshotTitle = tab.title
        tab.session.saveAsPdf().accept({ input ->
            Thread {
                try {
                    val folder = File(appContext.filesDir, "saved-pages").apply { mkdirs() }
                    val fileName = "page-" + System.currentTimeMillis() + "-" +
                        UUID.randomUUID().toString().take(8) + ".pdf"
                    requireNotNull(input) { "PDF stream was empty" }.use { stream ->
                        File(folder, fileName).outputStream().use { stream.copyTo(it) }
                    }
                    Handler(Looper.getMainLooper()).post {
                        store.savePage(fileName, snapshotUrl, snapshotTitle)
                        notice = "PDF copy saved in Time Machine"
                    }
                } catch (error: Exception) {
                    Handler(Looper.getMainLooper()).post { notice = "Couldn't save this page." }
                }
            }.start()
        }, { error -> notice = "Couldn't save this page as PDF." })
    }
}
