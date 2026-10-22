package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.view.MotionEvent
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * A Canvas message is an OFFLINE interactive HTML/CSS/JS mini-app.
 * It runs inside a separate WebView, without host bridges, file/content access or networking.
 * It does not share the profile or cookies of Annie's browser or script sessions.
 */
internal data class AnnieCanvasDocument(
    val title: String,
    val html: String,
    val css: String,
    val javascript: String,
    val heightDp: Int,
) {
    fun page(initiallyPaused: Boolean = false): String {
        // Do not let inline JS or CSS close their containing tag accidentally.
        val safeCss = css.replace(Regex("</style", RegexOption.IGNORE_CASE)) { "<\\/" + it.value.substring(2) }
        val safeJs = javascript.replace(Regex("</script", RegexOption.IGNORE_CASE)) { "<\\/" + it.value.substring(2) }
        return """
            <!doctype html>
            <html lang="en"><head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <meta http-equiv="Content-Security-Policy"
                    content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'">
              <style>
                html.annie-host-paused *, html.annie-host-paused *::before,
                html.annie-host-paused *::after { animation-play-state: paused !important; }
                :root { color-scheme: dark; }
                html, body { box-sizing:border-box; padding:0; margin:0; background:#0b1828; color:#eef5ff; font-family:system-ui,sans-serif; }
                *, *::before, *::after { box-sizing:inherit; }
                body { min-height:100vh; overscroll-behavior:contain; }
                button, input { font:inherit; }
                $safeCss
              </style>
              <script>window.__annieCanvasInitiallyPaused = ${if (initiallyPaused) "true" else "false"}; 
              $CANVAS_LIFECYCLE_BOOTSTRAP</script>
            </head><body>$html<script>$safeJs</script></body></html>
        """.trimIndent()
    }

    companion object {
        fun from(payload: JSONObject): AnnieCanvasDocument? {
            val html = payload.optString("html")
            val css = payload.optString("css")
            val js = payload.optString("javascript").ifBlank { payload.optString("js") }
            if (html.isBlank() && css.isBlank() && js.isBlank()) return null
            // Match the existing message boundary. Do not silently truncate executable code.
            if (payload.toString().toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return null
            return AnnieCanvasDocument(
                title = payload.optString("title").trim().take(100).ifBlank { "Canvas" },
                html = html, css = css, javascript = js,
                heightDp = payload.optInt("height", 300).coerceIn(180, 500),
            )
        }
    }
}

/**
 * Compose's LazyColumn scroll is not a View.scrollTo() operation, so Android
 * getGlobalVisibleRect alone cannot see the conversation viewport clipping.
 * Intersect every layout-ancestor viewport on layout/placement changes.
 */
internal fun canvasHasVisibleComposeArea(coordinates: LayoutCoordinates): Boolean {
    val own = coordinates.boundsInWindow()
    if (own.width <= 0f || own.height <= 0f) return false
    var left = own.left
    var top = own.top
    var right = own.right
    var bottom = own.bottom
    var ancestor = coordinates.parentLayoutCoordinates
    while (ancestor != null) {
        val box = ancestor.boundsInWindow()
        left = maxOf(left, box.left)
        top = maxOf(top, box.top)
        right = minOf(right, box.right)
        bottom = minOf(bottom, box.bottom)
        if (right <= left || bottom <= top) return false
        ancestor = ancestor.parentLayoutCoordinates
    }
    val visibleArea = (right - left).toDouble() * (bottom - top).toDouble()
    val totalArea = own.width.toDouble() * own.height.toDouble()
    return visibleArea * 4.0 >= totalArea
}

internal class AnnieCanvasController(
    private val id: String,
    initiallyClosed: Boolean = false,
) {
    private var web: WebView? = null
    private var inlineHost: FrameLayout? = null
    private var fullscreenHost: FrameLayout? = null
    private var document: AnnieCanvasDocument? = null
    private var inlineForeground = false
    private var inlineComposeVisible = false
    private val fullscreenForegroundOwners = mutableSetOf<Any>()
    private var suspended: Boolean? = null
    private var observedHost: FrameLayout? = null
    private var observedTree: ViewTreeObserver? = null
    var pausedByUser by mutableStateOf(false)
        private set
    var isClosed by mutableStateOf(initiallyClosed)
        private set
    var expanded: Boolean = false
        private set

    internal val webView: WebView? get() = web

    private val onScroll = ViewTreeObserver.OnScrollChangedListener { syncSuspension() }
    private val onLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncSuspension() }
    private val onAttach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            // A ViewTreeObserver may be replaced when an AndroidView is attached.
            bindTree(v.viewTreeObserver)
            syncSuspension()
        }
        override fun onViewDetachedFromWindow(v: View) { syncSuspension() }
    }

    private fun bindTree(next: ViewTreeObserver?) {
        observedTree?.takeIf { it.isAlive }?.removeOnScrollChangedListener(onScroll)
        observedTree = next?.takeIf { it.isAlive }
        observedTree?.addOnScrollChangedListener(onScroll)
    }

    private fun observe(host: FrameLayout?) {
        if (observedHost === host) {
            syncSuspension()
            return
        }
        observedHost?.removeOnLayoutChangeListener(onLayout)
        observedHost?.removeOnAttachStateChangeListener(onAttach)
        bindTree(null)
        observedHost = host
        if (host != null) {
            host.addOnLayoutChangeListener(onLayout)
            host.addOnAttachStateChangeListener(onAttach)
            bindTree(host.viewTreeObserver)
        }
        syncSuspension()
    }

    private fun surfaceVisible(): Boolean {
        val host = if (expanded) fullscreenHost else inlineHost
        if (host == null || !host.isAttachedToWindow || !host.isShown) return false
        val visible = Rect()
        if (!host.getGlobalVisibleRect(visible) || host.width <= 0 || host.height <= 0) return false
        // Near-offscreen cards must not spin simply because a few pixels are
        // still visible below the conversation header. Re-enable on scroll.
        return visible.width().toLong() * visible.height().toLong() * 4L >=
            host.width.toLong() * host.height.toLong()
    }

    private fun shouldSuspend() = pausedByUser ||
        !(if (expanded) fullscreenForegroundOwners.isNotEmpty() else
            inlineForeground && inlineComposeVisible) || !surfaceVisible()

    private fun syncSuspension(force: Boolean = false) {
        val view = web ?: return
        val stop = shouldSuspend()
        if (!force && suspended == stop) return
        suspended = stop
        if (stop) {
            // evaluateJavascript is scoped to this WebView. Never call the static
            // WebView.pauseTimers(): it pauses unrelated WebViews and Gecko tabs.
            view.evaluateJavascript("window.__annieCanvasHost && window.__annieCanvasHost.pause()", null)
            view.evaluateJavascript("true") {
                if (web === view && shouldSuspend()) view.onPause()
            }
        } else {
            view.onResume()
            view.evaluateJavascript("window.__annieCanvasHost && window.__annieCanvasHost.resume()", null)
        }
    }

    fun setInlineForeground(value: Boolean) {
        inlineForeground = value
        if (!expanded) syncSuspension()
    }

    fun onInlineGeometryChanged(visibleInCompose: Boolean) {
        // Compose lazy scrolling clips children without changing Android view
        // scroll offsets; use ancestor placement bounds as the extra signal.
        inlineComposeVisible = visibleInCompose
        if (!expanded) syncSuspension()
    }

    fun setFullscreenForeground(owner: Any, value: Boolean) {
        // During rotation the old Activity may stop after a new fullscreen
        // Activity starts. A Boolean would let that stale STOP pause the new UI.
        if (value) fullscreenForegroundOwners.add(owner)
        else fullscreenForegroundOwners.remove(owner)
        if (expanded) syncSuspension()
    }

    fun togglePause() {
        if (isClosed) return
        pausedByUser = !pausedByUser
        syncSuspension()
    }

    private fun prepare(context: Context, next: AnnieCanvasDocument): WebView {
        val current = web ?: WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(11, 24, 40))
            setOnTouchListener { view, event ->
                // Host suspension also disables gestures; pausing Snake should
                // never queue hidden turns while the chat is in the background.
                if (shouldSuspend()) {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    return@setOnTouchListener true
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                blockNetworkLoads = true
                mediaPlaybackRequiresUserGesture = true
                setGeolocationEnabled(false)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view === web) syncSuspension(force = true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                    callback?.invoke(origin, false, false)
                }
            }
        }.also { web = it; suspended = null }
        if (document != next) {
            document = next
            current.loadDataWithBaseURL(null, next.page(shouldSuspend()), "text/html", "UTF-8", null)
        }
        return current
    }

    private fun reparent(view: WebView, target: FrameLayout) {
        if (view.parent !== target) {
            (view.parent as? ViewGroup)?.removeView(view)
            target.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
    }

    fun attachInline(context: Context, host: FrameLayout, next: AnnieCanvasDocument) {
        if (isClosed) return
        inlineHost = host
        val view = prepare(context, next)
        if (!expanded) {
            reparent(view, host)
            observe(host)
        }
    }

    fun beginFullscreen() {
        if (isClosed || expanded) return
        expanded = true
        observe(null)
        web?.let { (it.parent as? ViewGroup)?.removeView(it) }
        syncSuspension()
    }

    fun attachFullscreen(host: FrameLayout) {
        if (isClosed || !expanded) return
        fullscreenHost = host
        web?.let { reparent(it, host) }
        observe(host)
    }

    fun restart() {
        if (isClosed) return
        document?.let {
            web?.loadDataWithBaseURL(null, it.page(shouldSuspend()), "text/html", "UTF-8", null)
        }
    }

    fun close() {
        isClosed = true
        AnnieCanvasSessions.rememberClosed(id, true)
        releaseWebView()
        // Preserve the message and controller: the chat card offers Open again.
    }

    fun reopen() {
        if (!isClosed) return
        isClosed = false
        AnnieCanvasSessions.rememberClosed(id, false)
        pausedByUser = false
        // AndroidView is mounted again by Compose and loads the same document.
    }

    fun returnToInline() {
        expanded = false
        fullscreenHost = null
        fullscreenForegroundOwners.clear()
        val host = inlineHost
        if (host != null && host.isAttachedToWindow) {
            if (!isClosed) web?.let { reparent(it, host) }
            observe(host)
        } else {
            destroy()
        }
    }

    fun inlineDisposed() {
        inlineHost = null
        inlineForeground = false
        inlineComposeVisible = false
        if (!expanded) destroy()
    }

    private fun releaseWebView() {
        observe(null)
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.onPause()
            it.destroy()
        }
        web = null
        suspended = null
        document = null
    }

    fun destroy() {
        releaseWebView()
        inlineHost = null
        fullscreenHost = null
        fullscreenForegroundOwners.clear()
        expanded = false
        AnnieCanvasSessions.remove(id, this)
    }

    val label: String get() = document?.title ?: "Canvas"
}

internal object AnnieCanvasSessions {
    private val sessions = ConcurrentHashMap<String, AnnieCanvasController>()
    // The WebView is destroyed when LazyColumn evicts a message. An explicit
    // user Close must NOT silently reopen on scrolling back. Keep only tiny
    // bounded flags, never live WebViews, for closed chat message instances.
    private val closedIds = LinkedHashSet<String>()
    private const val MAX_CLOSED_IDS = 384

    fun get(id: String, restoredClosed: Boolean = false): AnnieCanvasController = sessions.getOrPut(id) {
        AnnieCanvasController(id, restoredClosed || synchronized(closedIds) { id in closedIds })
    }
    fun find(id: String): AnnieCanvasController? = sessions[id]
    fun remove(id: String, controller: AnnieCanvasController) {
        sessions.remove(id, controller)
    }
    fun rememberClosed(id: String, closed: Boolean) {
        synchronized(closedIds) {
            closedIds.remove(id)
            if (closed) {
                closedIds.add(id)
                while (closedIds.size > MAX_CLOSED_IDS) {
                    closedIds.remove(closedIds.first())
                }
            }
        }
    }
}

@Composable
internal fun AnnieCanvasMessage(payload: JSONObject) {
    val doc = remember(payload.toString()) { AnnieCanvasDocument.from(payload) }
    if (doc == null) {
        Text("Canvas content is missing or exceeds the message size limit.", color = Color(0xFFFF9B91))
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val id = rememberAnnieBrowserInstanceId()
    // Compose saves this boolean with the chat item's stable LazyColumn key.
    // Restoring a closed message must not allocate/render its WebView first.
    var closedAcrossRestore by rememberSaveable(id) { mutableStateOf(false) }
    val controller = remember(id) { AnnieCanvasSessions.get(id, closedAcrossRestore) }
    LaunchedEffect(controller.isClosed) {
        closedAcrossRestore = controller.isClosed
    }
    DisposableEffect(controller, owner) {
        controller.setInlineForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.setInlineForeground(true)
                Lifecycle.Event.ON_STOP -> controller.setInlineForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller.inlineDisposed()
        }
    }

    Surface(
        color = BrowserBubble,
        shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp),
        modifier = Modifier.fillMaxWidth().testTag("annie_canvas_message"),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.title, color = BrowserBrightText, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 7.dp))
                if (controller.isClosed) {
                    TextButton(onClick = {
                        controller.reopen()
                        closedAcrossRestore = false
                    }, modifier = Modifier.testTag("annie_canvas_reopen")) {
                        Text("Open", color = BrowserTeal)
                    }
                } else {
                    IconButton(onClick = controller::restart,
                        modifier = Modifier.size(48.dp).testTag("annie_canvas_restart")) {
                        Icon(AnnieIcons.Rotate, contentDescription = "Restart Canvas",
                            tint = BrowserSoftText, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = controller::togglePause,
                        modifier = Modifier.size(48.dp).testTag("annie_canvas_pause")) {
                        Icon(if (controller.pausedByUser) AnnieIcons.Play else AnnieIcons.Pause,
                            contentDescription = if (controller.pausedByUser) "Resume Canvas" else "Pause Canvas",
                            tint = BrowserTeal, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = {
                        controller.close()
                        closedAcrossRestore = true
                    }, modifier = Modifier.size(48.dp).testTag("annie_canvas_close")) {
                        Icon(AnnieCanvasIcons.Close, contentDescription = "Close Canvas",
                            tint = BrowserSoftText, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = {
                        controller.beginFullscreen()
                        context.startActivity(AnnieCanvasActivity.intent(context, id))
                    }, modifier = Modifier.size(48.dp).testTag("annie_canvas_fullscreen")) {
                        Icon(AnnieCanvasIcons.Expand, contentDescription = "Expand Canvas",
                            tint = BrowserTeal, modifier = Modifier.size(21.dp))
                    }
                }
            }
            if (!controller.isClosed) {
                AndroidView(
                    factory = { FrameLayout(it).also { host -> controller.attachInline(it, host, doc) } },
                    update = { host -> controller.attachInline(host.context, host, doc) },
                    modifier = Modifier.fillMaxWidth().height(doc.heightDp.dp)
                        .onGloballyPositioned {
                            controller.onInlineGeometryChanged(canvasHasVisibleComposeArea(it))
                        }
                        .testTag("annie_canvas_webview"),
                )
            } else {
                Text("Canvas closed", color = BrowserSoftText, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp).testTag("annie_canvas_closed"))
            }
        }
    }
}

internal class AnnieCanvasActivity : ComponentActivity() {
    private var controller: AnnieCanvasController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        controller = intent.getStringExtra(EXTRA_ID)?.let(AnnieCanvasSessions::find)
        if (controller == null || controller?.isClosed == true) { finish(); return }
        setContent {
            AnnieTheme {
                Column(
                    Modifier.fillMaxSize().background(BrowserNight)
                        .navigationBarsPadding()
                        .testTag("annie_canvas_fullscreen_screen"),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(controller?.label ?: "Canvas", color = BrowserBrightText,
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { controller?.togglePause() },
                            modifier = Modifier.size(48.dp).testTag("annie_canvas_fullscreen_pause")) {
                            Icon(if (controller?.pausedByUser == true) AnnieIcons.Play else AnnieIcons.Pause,
                                contentDescription = if (controller?.pausedByUser == true) "Resume Canvas" else "Pause Canvas",
                                tint = BrowserTeal)
                        }
                        IconButton(onClick = { controller?.restart() },
                            modifier = Modifier.size(48.dp).testTag("annie_canvas_fullscreen_restart")) {
                            Icon(AnnieIcons.Rotate, contentDescription = "Restart Canvas",
                                tint = BrowserSoftText)
                        }
                        IconButton(onClick = {
                            controller?.close()
                            finish()
                        }, modifier = Modifier.size(48.dp).testTag("annie_canvas_fullscreen_close")) {
                            Icon(AnnieCanvasIcons.Close, contentDescription = "Close Canvas",
                                tint = BrowserSoftText)
                        }
                        IconButton(onClick = ::finish,
                            modifier = Modifier.size(48.dp).testTag("annie_canvas_collapse")) {
                            Icon(AnnieIcons.ArrowBack, contentDescription = "Return to chat",
                                tint = BrowserTeal)
                        }
                    }
                    AndroidView(
                        factory = { FrameLayout(it).also { host -> controller?.attachFullscreen(host) } },
                        update = { host -> controller?.attachFullscreen(host) },
                        modifier = Modifier.fillMaxSize().testTag("annie_canvas_fullscreen_webview"),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller?.setFullscreenForeground(this, true)
    }

    override fun onStop() {
        controller?.setFullscreenForeground(this, false)
        super.onStop()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) controller?.returnToInline()
        controller = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "annie.canvas.instance"
        fun intent(context: Context, id: String): Intent =
            Intent(context, AnnieCanvasActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
