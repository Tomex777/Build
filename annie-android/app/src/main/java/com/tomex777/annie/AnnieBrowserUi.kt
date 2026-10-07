package com.tomex777.annie

import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.graphics.Outline
import android.net.Uri
import java.net.URLEncoder
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private val BrowserNight = Color(0xFF07111E)
private val BrowserBubble = Color(0xFF13243A)
private val BrowserBlue = Color(0xFF168EEA)
private val BrowserSoftText = Color(0xFF9CB2CC)
private val BrowserBrightText = Color(0xFFEEF5FF)
private val BrowserTeal = Color(0xFF54D6AE)
private val BrowserBubbleShape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp)

internal class AnnieBrowserController(
    internal val sessionId: String,
) {
    internal var webView: WebView? = null
    internal var inlineParent: ViewGroup? = null
    internal var inFullscreen = false
    var currentUrl by mutableStateOf("")
        internal set
    var pageTitle by mutableStateOf("")
        internal set
    var progress by mutableIntStateOf(0)
        internal set
    var scrollY by mutableIntStateOf(0)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set
    var message by mutableStateOf<String?>(null)
        internal set
    internal var cookieManager: CookieManager? = null

    fun goBack() { webView?.takeIf { it.canGoBack() }?.goBack() }
    fun goForward() { webView?.takeIf { it.canGoForward() }?.goForward() }
    fun reload() { webView?.reload() }
    fun enterFullscreen() {
        inFullscreen = true
        webView?.let { web -> (web.parent as? ViewGroup)?.removeView(web) }
    }
    fun returnToInline() {
        webView?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            inlineParent?.let { parent ->
                if (web.parent == null) parent.addView(web)
            }
        }
        inFullscreen = false
    }
    fun destroy() {
        webView?.let { web ->
            runCatching { (web.parent as? ViewGroup)?.removeView(web) }
            runCatching { web.stopLoading() }
            runCatching { web.loadUrl("about:blank") }
            runCatching { web.removeAllViews() }
            runCatching { web.destroy() }
        }
        webView = null
        cookieManager = null
        inlineParent = null
        AnnieBrowserControllers.remove(this)
    }
    fun load(spec: AnnieBrowserSpec, address: String): Boolean {
        val safe = spec.sanitized()
        if (!AnnieBrowserProfiles.isSupported()) {
            message = AnnieBrowserProfiles.unavailableMessage()
            return false
        }
        if (!safe.allows(address)) {
            message = "That address is outside this browser session's allowed sites."
            return false
        }
        message = null
        webView?.loadUrl(address)
        return true
    }

    internal fun updateHistory(view: WebView?) {
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }
}

@Composable
internal fun rememberAnnieBrowserController(sessionId: String): AnnieBrowserController =
    remember(sessionId) { AnnieBrowserControllers.get(sessionId) }

internal object AnnieBrowserControllers {
    private val controllers = java.util.concurrent.ConcurrentHashMap<String, AnnieBrowserController>()
    fun get(sessionId: String): AnnieBrowserController =
        controllers.getOrPut(sessionId) { AnnieBrowserController(sessionId) }
    fun remove(controller: AnnieBrowserController) { controllers.entries.removeAll { it.value === controller } }
}

@Composable
internal fun AnnieBrowserWebView(
    spec: AnnieBrowserSpec,
    controller: AnnieBrowserController,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
) {
    val context = LocalContext.current
    val safe = remember(spec) { spec.sanitized() }
    if (!AnnieBrowserProfiles.isSupported()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BrowserBubble),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                AnnieBrowserProfiles.unavailableMessage(),
                color = BrowserSoftText,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    check(controller.sessionId == safe.sessionId) {
        "Browser controller/session mismatch: " + controller.sessionId + " != " + safe.sessionId
    }
    DisposableEffect(controller, fullscreen) {
        onDispose {
            if (!fullscreen && !controller.inFullscreen) controller.destroy()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            controller.webView?.let { existing ->
                (existing.parent as? ViewGroup)?.removeView(existing)
                existing
            } ?: WebView(viewContext).apply {
                controller.cookieManager = AnnieBrowserProfiles.attach(this, safe.sessionId)
                controller.webView = this
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radius = 12f * view.resources.displayMetrics.density
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
                controller.scrollY = AnnieBrowserSessionStore.scrollY(context, safe)
                setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                    controller.scrollY = scrollY.coerceAtLeast(0)
                    AnnieBrowserSessionStore.saveScroll(context, safe, controller.scrollY)
                }
                // The browser lives inside a vertically scrolling chat list. Keep the
                // gesture with WebView so page swipes scroll the page instead of the chat.
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                settings.apply {
                    javaScriptEnabled = safe.javaScriptEnabled
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture = true
                    safeBrowsingEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    safe.userAgent?.let { userAgentString = it }
                }
                controller.cookieManager?.setAcceptCookie(true)
                controller.cookieManager?.setAcceptThirdPartyCookies(this, safe.thirdPartyCookies)
                webViewClient = object : WebViewClient() {
                    private fun allow(raw: String?): Boolean {
                        if (raw != null && safe.allows(raw)) {
                            controller.message = null
                            return true
                        }
                        controller.message = "Navigation blocked: this session is limited to its allowed sites."
                        return false
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                        !allow(request?.url?.toString())
                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = !allow(url)
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        controller.loading = true
                        controller.message = null
                        url?.let { controller.currentUrl = it }
                        controller.updateHistory(view)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.loading = false
                        url?.takeIf(safe::allows)?.let {
                            controller.currentUrl = it
                            AnnieBrowserSessionStore.save(context, safe, it)
                        }
                        val savedScroll = AnnieBrowserSessionStore.scrollY(context, safe)
                        controller.scrollY = savedScroll
                        if (savedScroll > 0) view?.post { view.scrollTo(0, savedScroll) }
                        controller.cookieManager?.flush()
                        controller.updateHistory(view)
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            controller.loading = false
                            controller.message = error?.description?.toString()?.take(180) ?: "This page could not be loaded."
                        }
                    }
                }
                setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                    if (!safe.allows(url)) {
                        controller.message = "This download is outside the allowed sites."
                    } else {
                        AnnieBrowserSessionStore.save(context, safe, controller.currentUrl)
                        val filename = DownloadFileMetadata.filename(contentDisposition, null, url, mimeType)
                        val id = "browser-${java.util.UUID.randomUUID()}"
                        val item = DownloadItem(
                            id = id, canonicalTitleId = id, sourceId = safe.sessionId, sourceName = safe.title,
                            kind = DownloadMediaKind.FILE, title = filename, unitTitle = filename,
                            state = DownloadState.QUEUED, bytesTotal = contentLength.coerceAtLeast(0), sourceUrl = url,
                            filename = filename, sourceMimeType = DownloadFileMetadata.mime(mimeType, null, filename),
                            browserSessionId = safe.sessionId,
                            headersJson = org.json.JSONObject().put("User-Agent", userAgent ?: settings.userAgentString)
                                .put("Referer", controller.currentUrl).toString(),
                        )
                        DownloadTransferService.enqueue(context, item)
                        android.widget.Toast.makeText(context, "Downloading $filename", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        controller.progress = newProgress.coerceIn(0, 100)
                        controller.updateHistory(view)
                    }
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        controller.pageTitle = title.orEmpty().take(160)
                    }
                }
                val start = AnnieBrowserSessionStore.currentUrl(context, safe)
                controller.currentUrl = start
                loadUrl(start)
            }
        },
        update = {
            if (controller.webView !== it) controller.webView = it
            if (!fullscreen && !controller.inFullscreen) controller.inlineParent = it.parent as? ViewGroup
        },
    )
}

@Composable
internal fun AnnieBrowserMessage(
    spec: AnnieBrowserSpec,
    onVerify: (String, String, (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val safe = remember(spec) { spec.sanitized() }
    val controller = rememberAnnieBrowserController(safe.sessionId)
    var status by remember(safe.sessionId) {
        mutableStateOf(AnnieBrowserSessionStore.get(context, safe.sessionId)?.verificationState ?: safe.verificationState)
    }
    var verifyMessage by remember(safe.sessionId) { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().testTag("annie_browser_message")
            .clip(BrowserBubbleShape)
            .background(BrowserBubble)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(safe.title, color = BrowserBrightText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Uri.parse(controller.currentUrl.ifBlank { safe.url }).host.orEmpty(), color = BrowserSoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (status) {
                    AnnieBrowserVerificationState.Verified -> "Verified"
                    AnnieBrowserVerificationState.Verifying -> "Verifying…"
                    AnnieBrowserVerificationState.Failed -> "Verification failed"
                    else -> if (safe.verifyAction.isNullOrBlank()) "Session ready" else "Verification needed"
                },
                color = if (status == AnnieBrowserVerificationState.Verified) BrowserTeal else BrowserSoftText,
                fontSize = 11.sp,
            )
        }
        if (controller.loading) LinearProgressIndicator(
            progress = { controller.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = BrowserBlue,
        )
        AnnieBrowserWebView(
            safe,
            controller,
            Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp))
                .testTag("annie_browser_inline_webview"),
        )
        controller.message?.let { Text(it, color = Color(0xFFFF9B91), fontSize = 12.sp) }
        if (verifyMessage.isNotBlank()) Text(verifyMessage, color = BrowserSoftText, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = controller::goBack,
                enabled = controller.canGoBack,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
            ) { Text("Back", fontSize = 11.sp, maxLines = 1) }
            TextButton(
                onClick = controller::reload,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
            ) { Text("Reload", fontSize = 11.sp, maxLines = 1) }
            if (!safe.verifyAction.isNullOrBlank()) {
                Button(
                    onClick = {
                        val current = controller.currentUrl.ifBlank { AnnieBrowserSessionStore.currentUrl(context, safe) }
                        status = AnnieBrowserVerificationState.Verifying
                        verifyMessage = ""
                        AnnieBrowserSessionStore.setVerification(context, safe.sessionId, status)
                        val payload = AnnieBrowserVerification.payload(context, safe, current)
                        onVerify(safe.verifyAction, payload) { rawResult ->
                            val result = runCatching { JSONObject(rawResult.orEmpty()) }.getOrNull()
                            val outcome = result?.optJSONObject("verification")?.optString("status")
                                ?: result?.optString("verificationStatus")
                            status = if (outcome.equals("verified", true)) AnnieBrowserVerificationState.Verified
                                else AnnieBrowserVerificationState.Failed
                            verifyMessage = result?.optJSONObject("verification")?.optString("message").orEmpty()
                                .ifBlank { if (status == AnnieBrowserVerificationState.Verified) "Session confirmed." else "The source did not confirm this session." }
                            AnnieBrowserSessionStore.setVerification(
                                context, safe.sessionId, status,
                                if (status == AnnieBrowserVerificationState.Verified) System.currentTimeMillis() else 0L,
                            )
                        }
                    },
                    enabled = status != AnnieBrowserVerificationState.Verifying,
                    colors = ButtonDefaults.buttonColors(containerColor = BrowserBlue),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("annie_browser_verify"),
                ) {
                    Text(
                        if (status == AnnieBrowserVerificationState.Verifying) "Verifying…" else safe.verifyLabel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    controller.enterFullscreen()
                    context.startActivity(AnnieBrowserActivity.intent(context, safe))
                },
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                modifier = Modifier.testTag("annie_browser_fullscreen"),
            ) {
                Text("Full screen", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

internal object AnnieBrowserVerification {
    fun payload(context: android.content.Context, spec: AnnieBrowserSpec, currentUrl: String): String {
        val safe = spec.sanitized()
        val url = currentUrl.takeIf(safe::allows) ?: safe.url
        val host = safe.safeUrl(url)?.host.orEmpty()
        val cookieLine = AnnieBrowserControllers.get(safe.sessionId).cookieManager?.getCookie(url).orEmpty()
        val names = cookieLine.split(';').map { it.trim().substringBefore('=').trim() }.filter(String::isNotBlank).distinct()
        return JSONObject()
            .put("sessionId", safe.sessionId)
            .put("currentUrl", url)
            .put("currentHost", host)
            .put("allowedHosts", JSONArray(safe.allowedHosts))
            .put("hasCookies", names.isNotEmpty())
            .put("cookieNames", JSONArray(names))
            .put("verificationState", AnnieBrowserSessionStore.get(context, safe.sessionId)?.verificationState?.wireName ?: "idle")
            .toString()
    }
}

internal class AnnieBrowserActivity : ComponentActivity() {
    private var browserController: AnnieBrowserController? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec = intent.getStringExtra(EXTRA_SPEC)?.let(AnnieBrowserSpec::decode)
        if (spec == null) { finish(); return }
        browserController = AnnieBrowserControllers.get(spec.sessionId)
        setContent { AnnieTheme { AnnieFullBrowser(spec) { finish() } } }
    }

    override fun onDestroy() {
        browserController?.returnToInline()
        browserController = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SPEC = "annie.browser.spec"
        fun intent(context: android.content.Context, spec: AnnieBrowserSpec) =
            Intent(context, AnnieBrowserActivity::class.java).putExtra(EXTRA_SPEC, spec.encode().toString())
    }
}

@Composable
private fun AnnieFullBrowser(spec: AnnieBrowserSpec, onClose: () -> Unit) {
    val context = LocalContext.current
    val safe = remember(spec) { spec.sanitized() }
    val controller = rememberAnnieBrowserController(safe.sessionId)
    var address by remember { mutableStateOf(safe.url) }
    LaunchedEffect(controller.currentUrl) {
        if (controller.currentUrl.isNotBlank()) address = controller.currentUrl
    }
    BackHandler(controller.canGoBack) { controller.goBack() }

    Column(
        Modifier.fillMaxSize().testTag("annie_full_browser")
            .background(BrowserNight).statusBarsPadding().navigationBarsPadding()
    ) {
        Surface(color = BrowserBubble, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("annie_browser_close")) {
                        Icon(
                            AnnieIcons.Close,
                            contentDescription = "Close browser",
                            tint = BrowserBrightText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            controller.pageTitle.ifBlank { safe.title },
                            color = BrowserBrightText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Uri.parse(controller.currentUrl.ifBlank { safe.url }).host.orEmpty(),
                            color = BrowserSoftText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (safe.restricted) "SESSION" else "BROWSER",
                        color = if (safe.restricted) BrowserTeal else BrowserSoftText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it.take(AnnieBrowserSpec.MAX_URL_CHARS) },
                        modifier = Modifier.weight(1f).testTag("annie_browser_address"),
                        singleLine = true,
                        placeholder = { Text("Search or enter address", color = BrowserSoftText, fontSize = 12.sp) },
                        shape = RoundedCornerShape(15.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { loadAddress(controller, safe, address) }),
                    )
                    Button(
                        onClick = { loadAddress(controller, safe, address) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrowserBlue),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Go") }
                }
            }
        }

        if (controller.loading) {
            LinearProgressIndicator(
                progress = { controller.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = BrowserBlue,
            )
        }

        controller.message?.let {
            Surface(color = Color(0xFF3A2026), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFFFFB0A9), fontSize = 12.sp)
            }
        }

        AnnieBrowserWebView(
            safe,
            controller,
            Modifier.fillMaxWidth().weight(1f).testTag("annie_full_browser_webview"),
            fullscreen = true,
        )

        Surface(color = BrowserBubble, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = controller::goBack, enabled = controller.canGoBack) {
                    Icon(AnnieIcons.ArrowBack, contentDescription = "Back")
                }
                TextButton(onClick = controller::goForward, enabled = controller.canGoForward) {
                    Icon(AnnieIcons.ArrowForward, contentDescription = "Forward")
                }
                TextButton(onClick = controller::reload) {
                    Icon(AnnieIcons.Refresh, contentDescription = "Reload")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    controller.currentUrl.takeIf(String::isNotBlank)?.let { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                }, modifier = Modifier.testTag("annie_browser_external")) {
                    Text("Open", fontSize = 12.sp)
                }
                TextButton(onClick = {
                    val url = controller.currentUrl.takeIf(String::isNotBlank) ?: safe.url
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                            "Share page",
                        )
                    )
                }, modifier = Modifier.testTag("annie_browser_share")) {
                    Text("Share", fontSize = 12.sp)
                }
            }
        }
    }
}
private fun loadAddress(controller: AnnieBrowserController, spec: AnnieBrowserSpec, raw: String) {
    val text = raw.trim()
    if (text.isBlank()) return
    val address = when {
        "://" in text -> text
        Regex("""^[A-Za-z0-9.-]+(?::\d+)?(?:/.*)?$""").matches(text) -> "https://$text"
        else -> "https://www.google.com/search?q=" + URLEncoder.encode(text, Charsets.UTF_8.name())
    }
    if (controller.load(spec, address)) controller.currentUrl = address
}

