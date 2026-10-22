package com.night.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Gecko allows exactly one runtime per Android process. MainActivity can be
 * recreated without the process dying (density/theme changes, multi-window,
 * or a system Activity relaunch). Keep Gecko itself at process scope while
 * each BrowserController still owns and closes only its own tab sessions.
 */
private object BrowserRuntimeHolder {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun obtain(context: Context): GeckoRuntime = runtime
        ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
}

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
    var journeyTracker: JourneyVisitTracker? = null
    var crashed by mutableStateOf(false)
    var failedUrl by mutableStateOf<String?>(null)
    var secureConnection by mutableStateOf(false)
    var mixedContent by mutableStateOf(false)
    var knownSitePermissions by mutableStateOf<List<GeckoSession.PermissionDelegate.ContentPermission>>(emptyList())
    // In-memory reduced page preview. Never written to the browsing-history store.
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var sessionState: String? = null
    // A Gecko HistoryList entry can precede or follow its location callback.
    // Hold only the latest tab-local entry; never write private history to disk.
    var latestHistoryUrl: String? = null
    var latestHistoryTitle: String? = null
}

class BrowserController(
    private val appContext: Context,
    private val platform: BrowserPlatformHost
) {
    val store = JourneyStore(appContext)
    private val runtime: GeckoRuntime = BrowserRuntimeHolder.obtain(appContext)
    val siteStudioStore = SiteStudioStore(appContext)
    val siteStudio = SiteStudioBridge(runtime, siteStudioStore)
    private val tabStore = TabSessionStore(appContext)
    private val writes = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingSave = Runnable { persistNow() }
    private val pendingJourneyUpdates = mutableMapOf<String, Runnable>()
    private fun cancelJourneyUpdate(tabId: String) {
        pendingJourneyUpdates.remove(tabId)?.let(handler::removeCallbacks)
    }

    private fun attachJourneyTracker(tab: BrowserTab, seedNodeId: String? = null) {
        if (tab.isPrivate) return
        tab.journeyTracker = JourneyVisitTracker(
            lookup = { nodeId ->
                if (store.journeyRecordingEnabled) store.getNode(tab.journeyId, nodeId) else null
            },
            saveVisit = { url, title, parentId ->
                store.record(tab.journeyId, url, title, parentId)
            },
            seedNodeId = seedNodeId
        )
        tab.lastNode = tab.journeyTracker?.nodeId
    }

    // In-document navigation has no network onPageStop. Debounce it so redirects
    // are not recorded as a chain of transient Journey nodes.
    private fun queueJourneyUpdate(tab: BrowserTab, url: String) {
        if (tab.isPrivate) return
        cancelJourneyUpdate(tab.id)
        val callback = object : Runnable {
            override fun run() {
                pendingJourneyUpdates.remove(tab.id)
                if (!isClosed && tabs.any { it.id == tab.id } &&
                    tab.session.isOpen && !tab.isLoading && tab.url == url) {
                    tab.lastNode = tab.journeyTracker?.visited(url, tab.title)
                    schedulePersist()
                }
            }
        }
        pendingJourneyUpdates[tab.id] = callback
        handler.postDelayed(callback, 400L)
    }
    private var isClosed = false
    private val recentlyClosedNormal = mutableListOf<TabSessionStore.Entry>()
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableStateOf("")
        private set
    var contextLink by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    /** Agent 6 can bind its download library here without replacing Gecko delegates. */
    var downloadHandler: ((BrowserTab, WebResponse) -> Unit)? = null

    val active: BrowserTab get() = tabs.first { it.id == activeId }

    init {
        val saved = tabStore.read()
        if (saved == null || saved.tabs.isEmpty()) {
            newTab()
        } else {
            saved.tabs.forEach { entry ->
                val journey = store.journeys.find { it.id == entry.journeyId }?.id
                    ?: store.journeys.first().id
                createTab(journeyId = journey, restored = entry)
            }
            select(saved.activeId?.takeIf { id -> tabs.any { it.id == id } }
                ?: tabs.last().id)
        }
    }

    fun newTab(
        url: String? = null,
        journeyId: String = store.journeys.first().id,
        privateMode: Boolean = false,
        seedNodeId: String? = null
    ): BrowserTab = createTab(url, journeyId, privateMode, seedNodeId = seedNodeId)

    /** New link tabs are descendants of the source tab, including popup links. */
    fun newChildTab(url: String, sourceTab: BrowserTab = active): BrowserTab =
        newTab(url, sourceTab.journeyId, sourceTab.isPrivate, sourceTab.lastNode)

    private fun createTab(
        url: String? = null,
        journeyId: String = store.journeys.first().id,
        privateMode: Boolean = false,
        restored: TabSessionStore.Entry? = null,
        seedNodeId: String? = null
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
        attachJourneyTracker(tab, restored?.lastNode ?: seedNodeId)
        installDelegates(tab, session)
        session.open(runtime)
        siteStudio.attach(session, tab.isPrivate)
        tabs.add(tab)
        select(tab.id)
        if (restored != null) {
            val prior = restored.sessionState?.let { encoded ->
                runCatching { GeckoSession.SessionState.fromString(encoded) }.getOrNull()
            }
            val recovered = prior?.let { runCatching { session.restoreState(it) }.isSuccess } == true
            if (!recovered && restored.url.isNotBlank()) session.loadUri(restored.url)
        } else {
            url?.takeIf { it.isNotBlank() }?.let(session::loadUri)
        }
        return tab
    }

    /** Gecko expects an unopened popup. Inherit the initiating tab's private mode. */
    private fun newPopupTab(parent: BrowserTab): BrowserTab {
        val popupSession = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(parent.isPrivate).build()
        )
        val popup = BrowserTab(UUID.randomUUID().toString(),
            popupSession, parent.journeyId, parent.isPrivate)
        attachJourneyTracker(popup, parent.lastNode)
        installDelegates(popup, popup.session)
        siteStudio.attach(popup.session, popup.isPrivate)
        tabs.add(popup)
        select(popup.id)
        return popup
    }

    private fun installDelegates(tab: BrowserTab, session: GeckoSession) {
        session.permissionDelegate = platform
        session.promptDelegate = platform
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadError(
                session: GeckoSession, uri: String?, error: WebRequestError
            ): GeckoResult<String> {
                tab.isLoading = false
                // Certificate failures must never be bypassed automatically.
                // Keep the original HTTP(S) address visible; internal error
                // documents are never recorded in Journeys.
                if (!uri.isNullOrBlank() && (
                    uri.startsWith("https://", ignoreCase = true) ||
                    uri.startsWith("http://", ignoreCase = true)
                )) {
                    tab.failedUrl = uri
                    // Keep the actual failed destination in the omnibar. The
                    // internal Gecko error document is not a successful visit.
                    tab.url = uri
                    tab.title = "Page unavailable"
                }
                val kind = when (error.category) {
                    WebRequestError.ERROR_CATEGORY_SECURITY ->
                        BrowserErrorPage.Kind.SECURITY
                    WebRequestError.ERROR_CATEGORY_SAFEBROWSING ->
                        BrowserErrorPage.Kind.BLOCKED
                    WebRequestError.ERROR_CATEGORY_NETWORK,
                    WebRequestError.ERROR_CATEGORY_PROXY ->
                        BrowserErrorPage.Kind.CONNECTION
                    else -> BrowserErrorPage.Kind.UNKNOWN
                }
                return GeckoResult.fromValue(BrowserErrorPage.dataUri(kind))
            }
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                return when (ExternalNavigation.decide(
                    request.uri, request.hasUserGesture,
                    request.isDirectNavigation, request.isRedirect
                )) {
                    ExternalNavigation.Decision.ENGINE -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    ExternalNavigation.Decision.CONFIRM_EXTERNAL -> {
                        platform.confirmExternalLaunch(request.uri) {
                            notice = "No app can open this link."
                        }
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    ExternalNavigation.Decision.BLOCK -> {
                        // Do not dispatch script-created intent/file/custom scheme URLs.
                        notice = "This link type isn't supported."
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank()) return
                // Local Gecko error data pages have no place in navigation
                // history; keep the failed HTTPS URL as the address instead.
                if (url.startsWith("data:", true) || url.startsWith("about:", true)) return
                tab.knownSitePermissions = perms
                if (tab.url != url) {
                    tab.url = url
                    if (!tab.isPrivate) {
                        if (!tab.isLoading) queueJourneyUpdate(tab, url)
                        schedulePersist()
                    }
                }
                // bfcache/history traversal may restore a page without a new
                // network onPageStop or onTitleChange. Trust Gecko's matched
                // HistoryItem title only for this exact committed URL.
                BrowserHistoryTitle.atCommittedUrl(
                    url, tab.latestHistoryUrl, tab.latestHistoryTitle
                )?.let { tab.title = it }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val popup = newPopupTab(tab)
                return GeckoResult.fromValue(popup.session)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    if (!tab.isPrivate && store.journeyRecordingEnabled &&
                        store.getNode(tab.journeyId, tab.lastNode)?.url ==
                            JourneyGraph.safeUrl(tab.url)) {
                        store.updatePageTitle(tab.journeyId, tab.lastNode, title)
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

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val sink = downloadHandler
                if (sink == null) {
                    // Gecko's WebResponse owns an open HTTP body stream. Ignoring
                    // it without closing would leak a socket and keep the download
                    // request alive even though Library has no handler yet.
                    runCatching { response.body?.close() }
                    notice = "Download manager is not connected yet."
                    return
                }
                try {
                    // Contract with Agent 6: successful callback assumes ownership
                    // of response.body and MUST close it after copying/cancellation.
                    sink(tab, response)
                } catch (_: Exception) {
                    runCatching { response.body?.close() }
                    notice = "Couldn't start this download."
                }
            }

            override fun onKill(session: GeckoSession) {
                tab.isLoading = false
                tab.crashed = true
                tab.canGoBack = false
                tab.canGoForward = false
                notice = "Tab was stopped. Tap Reload to restore the page."
            }

            override fun onCrash(session: GeckoSession) {
                tab.isLoading = false
                tab.crashed = true
                tab.canGoBack = false
                tab.canGoForward = false
                notice = "Tab crashed. Tap Reload to restore the page."
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(
                session: GeckoSession, state: GeckoSession.SessionState
            ) {
                state.getOrNull(state.currentIndex)?.let { item ->
                    tab.latestHistoryUrl = item.uri
                    tab.latestHistoryTitle = item.title
                    BrowserHistoryTitle.atCommittedUrl(
                        tab.url, item.uri, item.title
                    )?.let { tab.title = it }
                }
                if (!tab.isPrivate) {
                    tab.sessionState = state.toString()
                    schedulePersist()
                }
            }
            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                tab.secureConnection = securityInfo.isSecure && !securityInfo.isException
                tab.mixedContent = securityInfo.mixedModeActive ==
                    GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED
            }
            override fun onPageStart(session: GeckoSession, url: String) {
                // Gecko opens popup sessions after onNewSession returns. At that
                // point the already selected tab can safely become active.
                if (session.isOpen) session.setActive(tab.id == activeId)
                cancelJourneyUpdate(tab.id)
                tab.isLoading = true
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    tab.failedUrl = null
                }
                // The prior origin's TLS indicator must not appear on a new page.
                tab.secureConnection = false
                tab.mixedContent = false
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                cancelJourneyUpdate(tab.id)
                tab.isLoading = false
                if (success && !tab.isPrivate && tab.failedUrl == null) {
                    // Error-page data URIs may finish successfully even when the
                    // requested HTTPS page failed. Never record those as visits.
                    tab.lastNode = tab.journeyTracker?.visited(tab.url, tab.title)
                    schedulePersist()
                }
                // A local error document already shows a usable explanation.
                if (!success && tab.failedUrl == null) notice = "This page couldn't finish loading."
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }
        }
    }

    fun select(tabId: String) {
        if (tabs.none { it.id == tabId }) return
        activeId = tabId
        // onNewSession MUST return an unopened GeckoSession. Do not activate
        // Gecko's unopened popup here; activate it once Gecko starts loading.
        tabs.forEach { tab ->
            if (tab.session.isOpen) tab.session.setActive(tab.id == activeId)
        }
        schedulePersist()
    }

    fun closeTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        cancelJourneyUpdate(tab.id)
        if (!tab.isPrivate) {
            recentlyClosedNormal.add(toSavedTab(tab))
            if (recentlyClosedNormal.size > 5) recentlyClosedNormal.removeAt(0)
        }
        if (tabs.size == 1) newTab(journeyId = tab.journeyId)
        else if (tabId == activeId) select(tabs.first { it.id != tabId }.id)
        tabs.remove(tab)
        tab.thumbnail = null
        if (!tab.isPrivate) siteStudio.detach(tab.session)
        if (tab.session.isOpen) tab.session.close()
        schedulePersist()
    }

    fun undoCloseTab(tabId: String): Boolean {
        val index = recentlyClosedNormal.indexOfLast { it.id == tabId }
        if (index < 0) return false
        val entry = recentlyClosedNormal.removeAt(index)
        val journey = store.journeys.find { it.id == entry.journeyId }?.id
            ?: store.journeys.first().id
        createTab(journeyId = journey, restored = entry)
        return true
    }

    private fun toSavedTab(tab: BrowserTab): TabSessionStore.Entry =
        TabSessionStore.Entry(tab.id, tab.journeyId, tab.url,
            tab.title, tab.lastNode, tab.sessionState)

    private fun schedulePersist() {
        if (isClosed) return
        handler.removeCallbacks(pendingSave)
        handler.postDelayed(pendingSave, 950L)
    }

    /** Only regular tabs and encrypted Gecko SessionState reach disk. */
    fun persistNow() {
        if (isClosed) return
        handler.removeCallbacks(pendingSave)
        val normal = tabs.filterNot { it.isPrivate }.map(::toSavedTab)
        val activeNormalId = activeId.takeIf { id -> normal.any { it.id == id } }
            ?: normal.lastOrNull()?.id
        val snapshot = TabSessionStore.Snapshot(activeNormalId, normal)
        writes.execute { runCatching { tabStore.write(snapshot) } }
    }

    /**
     * Lifecycle-only bounded flush. Never writes private Gecko tab metadata.
     * Normal event writes stay debounced to avoid redundant storage work.
     * If disk/Keystore hangs, stop after 350ms rather than freezing Android.
     */
    fun flushTabsForLifecycle() {
        if (isClosed) return
        persistNow()
        runCatching {
            writes.submit {}.get(350, TimeUnit.MILLISECONDS)
        }
    }

    fun close() {
        if (isClosed) return
        persistNow()
        isClosed = true
        handler.removeCallbacks(pendingSave)
        pendingJourneyUpdates.keys.toList().forEach(::cancelJourneyUpdate)
        tabs.forEach {
            if (!it.isPrivate) siteStudio.detach(it.session)
            if (it.session.isOpen) it.session.close()
        }
        writes.shutdown()
    }

    fun navigate(input: String) {
        val address = AddressInput.resolve(input)
        if (address == null) {
            notice = "Enter a valid website address or search query."
        } else {
            val tab = active
            tab.failedUrl = null
            if (tab.crashed) {
                recover(tab, address)
            } else {
                tab.session.loadUri(address)
            }
        }
    }

    fun goBack() {
        if (active.canGoBack) {
            active.journeyTracker?.expectBack()
            active.session.goBack()
        }
    }
    fun goForward() {
        if (active.canGoForward) {
            active.journeyTracker?.expectForward()
            active.session.goForward()
        }
    }
    fun reload() {
        val tab = active
        tab.journeyTracker?.expectReload()
        val failedUrl = tab.failedUrl
        if (failedUrl != null) {
            tab.failedUrl = null
            if (tab.crashed) recover(tab, failedUrl)
            else tab.session.loadUri(failedUrl)
        } else if (tab.crashed) recover(tab, tab.url)
        else tab.session.reload()
    }

    private fun recover(tab: BrowserTab, url: String) {
        tab.crashed = false
        try {
            // Crashed Gecko sessions can still report isOpen; reopening without
            // first closing that broken handle raises IllegalStateException.
            // Unsaved form data cannot be reconstructed from the URL.
            if (tab.session.isOpen) tab.session.close()
            tab.session.open(runtime)
            if (url.isNotBlank()) tab.session.loadUri(url)
        } catch (_: RuntimeException) {
            tab.crashed = true
            notice = "Couldn't restore this tab. Open a new tab instead."
        }
    }

    /**
     * Clear only saved content permissions for the active first-party origin.
     * Agent 2 can surface this in the site-information panel; this is not a
     * history/cookie-clearing action and must not alter other origins.
     */
    fun resetSitePermissions() {
        val tab = active
        val current = runCatching { Uri.parse(tab.url) }.getOrNull()
        val origin = current?.let { it.scheme + "://" + it.encodedAuthority }
        if (origin.isNullOrBlank() || current?.host.isNullOrBlank()) {
            notice = "Open a website to manage permissions."
            return
        }
        val entries = tab.knownSitePermissions.filter { item ->
            val uri = runCatching { Uri.parse(item.uri) }.getOrNull()
            uri != null && (uri.scheme + "://" + uri.encodedAuthority).equals(origin, true)
        }
        if (entries.isEmpty()) {
            notice = "No saved site permissions are available to reset."
            return
        }
        entries.forEach { permission ->
            runtime.storageController.setPermission(
                permission, GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
            )
        }
        tab.knownSitePermissions = emptyList()
        notice = "Site content permissions reset. Reload to apply changes."
    }

    fun bookmark() {
        if (active.isPrivate) { notice = "Private tabs do not save bookmarks"; return }
        store.bookmark(active.url, active.title)
        notice = "Added to Library"
    }

    fun newPeekSession(url: String, configure: (GeckoSession) -> Unit = {}): GeckoSession =
        GeckoSession(GeckoSessionSettings.Builder()
            .usePrivateMode(active.isPrivate).build()).also { peek ->
            // Disposable previews are denied all powerful permissions. A Peek
            // must not become a second permission-owning browser profile.
            val denyPermissions = object : GeckoSession.PermissionDelegate {
                override fun onAndroidPermissionsRequest(
                    session: GeckoSession, permissions: Array<out String>?,
                    callback: GeckoSession.PermissionDelegate.Callback
                ) { callback.reject() }

                override fun onContentPermissionRequest(
                    session: GeckoSession,
                    perm: GeckoSession.PermissionDelegate.ContentPermission
                ): GeckoResult<Int> = GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                )

                override fun onMediaPermissionRequest(
                    session: GeckoSession, uri: String,
                    video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                    audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                    callback: GeckoSession.PermissionDelegate.MediaCallback
                ) { callback.reject() }
            }
            peek.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    return GeckoResult.fromValue(
                        if (ExternalNavigation.decide(
                            request.uri, request.hasUserGesture,
                            request.isDirectNavigation, request.isRedirect
                        ) == ExternalNavigation.Decision.ENGINE) AllowOrDeny.ALLOW
                        else AllowOrDeny.DENY
                    )
                }
            }
            // Let PeekOverlay register its delegates before the first request.
            configure(peek)
            // Do not allow a caller to replace Peek's permanent deny-permissions policy.
            peek.permissionDelegate = denyPermissions
            peek.open(runtime)
            if (PeekNavigationPolicy.allows(url)) peek.loadUri(url)
        }

}
