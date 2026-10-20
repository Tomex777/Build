package com.night.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
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
    var crashed by mutableStateOf(false)
    var secureConnection by mutableStateOf(false)
    var mixedContent by mutableStateOf(false)
    // In-memory reduced page preview. Never written to the browsing-history store.
    var thumbnail by mutableStateOf<Bitmap?>(null)
}

class BrowserController(
    private val appContext: Context,
    private val platform: BrowserPlatformHost
) {
    val store = JourneyStore(appContext)
    private val runtime: GeckoRuntime = GeckoRuntime.create(appContext)
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableStateOf("")
        private set
    var contextLink by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    /** Agent 6 can bind its download library here without replacing Gecko delegates. */
    var downloadHandler: ((WebResponse) -> Unit)? = null

    val active: BrowserTab get() = tabs.first { it.id == activeId }

    init {
        newTab()
    }

    fun newTab(url: String? = null, journeyId: String = store.journeys.first().id): BrowserTab {
        val session = GeckoSession()
        val tab = BrowserTab(UUID.randomUUID().toString(), session, journeyId)
        installDelegates(tab, session)
        session.open(runtime)
        tabs.add(tab)
        select(tab.id)
        url?.takeIf { it.isNotBlank() }?.let(session::loadUri)
        return tab
    }

    private fun installDelegates(tab: BrowserTab, session: GeckoSession) {
        session.permissionDelegate = platform
        session.promptDelegate = platform
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
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

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val sink = downloadHandler
                if (sink != null) sink(response)
                else notice = "Download manager is not connected yet."
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
            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                tab.secureConnection = securityInfo.isSecure && !securityInfo.isException
                tab.mixedContent = securityInfo.mixedModeActive ==
                    GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED
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
        if (tab.session.isOpen) tab.session.close()
    }

    fun navigate(input: String) {
        val address = AddressInput.resolve(input)
        if (address == null) {
            notice = "Enter a valid website address or search query."
        } else {
            val tab = active
            if (tab.crashed) {
                recover(tab, address)
            } else {
                tab.session.loadUri(address)
            }
        }
    }

    fun goBack() { if (active.canGoBack) active.session.goBack() }
    fun goForward() { if (active.canGoForward) active.session.goForward() }
    fun reload() {
        val tab = active
        if (tab.crashed) recover(tab, tab.url)
        else tab.session.reload()
    }

    private fun recover(tab: BrowserTab, url: String) {
        tab.crashed = false
        try {
            // GeckoSession is closed after onCrash/onKill; reopen before load.
            // The page's unsaved form state cannot be recovered by a plain URL.
            tab.session.open(runtime)
            if (url.isNotBlank()) tab.session.loadUri(url)
        } catch (_: RuntimeException) {
            tab.crashed = true
            notice = "Couldn't restore this tab. Open a new tab instead."
        }
    }

    fun bookmark() { store.bookmark(active.url, active.title); notice = "Added to Library" }

    fun newPeekSession(url: String): GeckoSession =
        GeckoSession().also { peek ->
            // Disposable previews are denied all powerful permissions. A Peek
            // must not become a second permission-owning browser profile.
            peek.permissionDelegate = object : GeckoSession.PermissionDelegate {
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
            peek.open(runtime)
            peek.loadUri(url)
        }

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
