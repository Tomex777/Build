package com.night.browser

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
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.concurrent.Executors
import java.io.File
import java.util.UUID

class BrowserTab(
    val id: String,
    val session: GeckoSession,
    val journeyId: String,
    val isPrivate: Boolean = false
) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var lastNode by mutableStateOf<String?>(null)
    // In-memory reduced page preview. Never written to the browsing-history store.
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var sessionState: String? = null
}

class BrowserController(private val appContext: Context) {
    val store = JourneyStore(appContext)
    private val runtime: GeckoRuntime = GeckoRuntime.create(appContext)
    private val tabStore = TabSessionStore(appContext)
    private val writes = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingSave = Runnable { persistNow() }
    private var isClosed = false
    private val recentlyClosedNormal = mutableListOf<TabSessionStore.Entry>()
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableStateOf("")
        private set
    var contextLink by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    val active: BrowserTab get() = tabs.first { it.id == activeId }

    init {
        val saved = tabStore.read()
        if (saved == null || saved.tabs.isEmpty()) {
            newTab()
        } else {
            // Restore every accepted manifest entry; never silently discard older tabs.
            saved.tabs.forEach { entry ->
                val validJourney = store.journeys.find { it.id == entry.journeyId }?.id
                    ?: store.journeys.first().id
                createTab(journeyId = validJourney, restored = entry)
            }
            select(saved.activeId?.takeIf { id -> tabs.any { it.id == id } }
                ?: tabs.last().id)
        }
    }

    fun newTab(
        url: String? = null,
        journeyId: String = store.journeys.first().id,
        privateMode: Boolean = false
    ): BrowserTab = createTab(url, journeyId, privateMode, null)

    private fun createTab(
        url: String? = null,
        journeyId: String = store.journeys.first().id,
        privateMode: Boolean = false,
        restored: TabSessionStore.Entry? = null
    ): BrowserTab {
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(privateMode).build()
        )
        val tab = BrowserTab(restored?.id ?: UUID.randomUUID().toString(),
            session, journeyId, privateMode)
        if (restored != null) {
            tab.title = restored.title.ifBlank { "New tab" }
            tab.url = restored.url
            tab.lastNode = restored.lastNode
            tab.sessionState = restored.sessionState
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                // Do not show Gecko's internal blank document as a visited website.
                if (url.isNullOrBlank() || url == "about:blank") return
                if (tab.url != url) {
                    tab.url = url
                    if (!tab.isPrivate) {
                        store.record(tab.journeyId, url, tab.title, tab.lastNode)?.let { tab.lastNode = it }
                        schedulePersist()
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val popup = newTab(journeyId = tab.journeyId, privateMode = tab.isPrivate)
                return GeckoResult.fromValue(popup.session)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    if (!tab.isPrivate) {
                        store.renameNode(tab.journeyId, tab.lastNode, title)
                        schedulePersist()
                    }
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
            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
                if (!tab.isPrivate) {
                    tab.sessionState = state.toString()
                    schedulePersist()
                }
            }
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
        if (restored != null) {
            val prior = restored.sessionState?.let { encoded ->
                runCatching { GeckoSession.SessionState.fromString(encoded) }.getOrNull()
            }
            val didRestore = prior?.let { runCatching { session.restoreState(it) }.isSuccess } == true
            if (!didRestore && restored.url.isNotBlank()) session.loadUri(restored.url)
        } else {
            url?.takeIf { it.isNotBlank() }?.let(session::loadUri)
        }
        return tab
    }

    fun select(tabId: String) {
        if (tabs.none { it.id == tabId }) return
        activeId = tabId
        tabs.forEach { it.session.setActive(it.id == activeId) }
        schedulePersist()
    }

    fun closeTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        // Private-tab closure is final: no private URLs, thumbnails or state in undo storage.
        if (!tab.isPrivate) {
            recentlyClosedNormal.add(toSavedTab(tab))
            if (recentlyClosedNormal.size > 5) recentlyClosedNormal.removeAt(0)
        }
        if (tabs.size == 1) newTab(journeyId = tab.journeyId)
        else if (tabId == activeId) select(tabs.first { it.id != tabId }.id)
        tabs.remove(tab)
        tab.thumbnail = null
        tab.session.close()
        schedulePersist()
    }

    fun undoCloseTab(tabId: String): Boolean {
        val index = recentlyClosedNormal.indexOfLast { it.id == tabId }
        if (index == -1) return false
        val closed = recentlyClosedNormal.removeAt(index)
        val journey = store.journeys.find { it.id == closed.journeyId }?.id
            ?: store.journeys.first().id
        createTab(journeyId = journey, restored = closed)
        return true
    }

    private fun toSavedTab(tab: BrowserTab): TabSessionStore.Entry =
        TabSessionStore.Entry(tab.id, tab.journeyId, tab.url, tab.title,
            tab.lastNode, tab.sessionState)

    private fun schedulePersist() {
        if (isClosed) return
        handler.removeCallbacks(pendingSave)
        handler.postDelayed(pendingSave, 950L)
    }

    /** Persists only NORMAL tabs. Content/form state is encrypted before disk write. */
    fun persistNow() {
        if (isClosed) return
        handler.removeCallbacks(pendingSave)
        val normal = tabs.filterNot { it.isPrivate }.map(::toSavedTab)
        val activeNormalId = activeId.takeIf { id -> normal.any { it.id == id } }
            ?: normal.lastOrNull()?.id
        val snapshot = TabSessionStore.Snapshot(activeNormalId, normal)
        writes.execute { runCatching { tabStore.write(snapshot) } }
    }

    fun trimPreviews() { tabs.forEach { it.thumbnail = null } }

    fun close() {
        if (isClosed) return
        persistNow()
        isClosed = true
        handler.removeCallbacks(pendingSave)
        tabs.forEach { if (it.session.isOpen) it.session.close() }
        writes.shutdown()
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
    fun bookmark() {
        if (active.isPrivate) { notice = "Private tabs do not save bookmarks"; return }
        store.bookmark(active.url, active.title)
        notice = "Added to Library"
    }

    fun newPeekSession(url: String): GeckoSession =
        GeckoSession(GeckoSessionSettings.Builder()
            .usePrivateMode(active.isPrivate).build())
            .also { it.open(runtime); it.loadUri(url) }

    fun savePdfSnapshot() {
        val tab = active
        if (tab.isPrivate) { notice = "Unavailable in private tabs"; return }
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
