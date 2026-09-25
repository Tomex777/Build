package com.tomex777.annie

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

internal class AnnieBrowserController {
    internal var webView: WebView? = null
    var currentUrl by mutableStateOf("")
        internal set
    var pageTitle by mutableStateOf("")
        internal set
    var progress by mutableIntStateOf(0)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set
    var message by mutableStateOf<String?>(null)
        internal set

    fun goBack() { webView?.takeIf { it.canGoBack() }?.goBack() }
    fun goForward() { webView?.takeIf { it.canGoForward() }?.goForward() }
    fun reload() { webView?.reload() }
    fun load(spec: AnnieBrowserSpec, address: String): Boolean {
        val safe = spec.sanitized()
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
internal fun rememberAnnieBrowserController(): AnnieBrowserController = remember { AnnieBrowserController() }

@Composable
internal fun AnnieBrowserWebView(
    spec: AnnieBrowserSpec,
    controller: AnnieBrowserController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safe = remember(spec) { spec.sanitized() }
    DisposableEffect(controller) {
        onDispose {
            controller.webView?.let { web ->
                runCatching { web.stopLoading() }
                runCatching { web.loadUrl("about:blank") }
                runCatching { web.removeAllViews() }
                runCatching { web.destroy() }
            }
            controller.webView = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                controller.webView = this
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
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, safe.thirdPartyCookies)
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
                        CookieManager.getInstance().flush()
                        controller.updateHistory(view)
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            controller.loading = false
                            controller.message = error?.description?.toString()?.take(180) ?: "This page could not be loaded."
                        }
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
        update = { if (controller.webView !== it) controller.webView = it },
    )
}

@Composable
internal fun AnnieBrowserMessage(
    spec: AnnieBrowserSpec,
    onVerify: (String, String, (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val safe = remember(spec) { spec.sanitized() }
    val controller = rememberAnnieBrowserController()
    var status by remember(safe.sessionId) {
        mutableStateOf(AnnieBrowserSessionStore.get(context, safe.sessionId)?.verificationState ?: safe.verificationState)
    }
    var verifyMessage by remember(safe.sessionId) { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().testTag("annie_browser_message")
            .background(BrowserBubble, RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp)).padding(10.dp),
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
        AnnieBrowserWebView(safe, controller, Modifier.fillMaxWidth().height(230.dp))
        controller.message?.let { Text(it, color = Color(0xFFFF9B91), fontSize = 12.sp) }
        if (verifyMessage.isNotBlank()) Text(verifyMessage, color = BrowserSoftText, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = controller::goBack, enabled = controller.canGoBack) { Text("Back") }
            TextButton(onClick = controller::reload) { Text("Reload") }
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
                    modifier = Modifier.testTag("annie_browser_verify"),
                ) { Text(if (status == AnnieBrowserVerificationState.Verifying) "Verifying…" else safe.verifyLabel) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                context.startActivity(AnnieBrowserActivity.intent(context, safe))
            }, modifier = Modifier.testTag("annie_browser_fullscreen")) { Text("Full screen") }
        }
    }
}

internal object AnnieBrowserVerification {
    fun payload(context: android.content.Context, spec: AnnieBrowserSpec, currentUrl: String): String {
        val safe = spec.sanitized()
        val url = currentUrl.takeIf(safe::allows) ?: safe.url
        val host = safe.safeUrl(url)?.host.orEmpty()
        val cookieLine = CookieManager.getInstance().getCookie(url).orEmpty()
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec = intent.getStringExtra(EXTRA_SPEC)?.let(AnnieBrowserSpec::decode)
        if (spec == null) { finish(); return }
        setContent { AnnieTheme { AnnieFullBrowser(spec) { finish() } } }
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
    val controller = rememberAnnieBrowserController()
    var address by remember { mutableStateOf(safe.url) }
    LaunchedEffect(controller.currentUrl) {
        if (controller.currentUrl.isNotBlank()) address = controller.currentUrl
    }
    BackHandler(controller.canGoBack) { controller.goBack() }
    Column(Modifier.fillMaxSize().testTag("annie_full_browser").background(BrowserNight).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Close") }
            Text(safe.title, Modifier.weight(1f), color = BrowserBrightText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(AnnieBrowserSpec.MAX_URL_CHARS) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { loadAddress(controller, safe, address) }),
            )
            Button(onClick = { loadAddress(controller, safe, address) }) { Text("Go") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = controller::goBack, enabled = controller.canGoBack) { Text("Back") }
            TextButton(onClick = controller::goForward, enabled = controller.canGoForward) { Text("Forward") }
            TextButton(onClick = controller::reload) { Text("Reload") }
            TextButton(onClick = {
                controller.currentUrl.takeIf(String::isNotBlank)?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }) { Text("Open external") }
            TextButton(onClick = {
                val url = controller.currentUrl.takeIf(String::isNotBlank) ?: safe.url
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share page"))
            }) { Text("Share") }
        }
        if (controller.loading) LinearProgressIndicator(
            progress = { controller.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = BrowserBlue,
        )
        controller.message?.let { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Color(0xFFFF9B91), fontSize = 12.sp) }
        AnnieBrowserWebView(safe, controller, Modifier.fillMaxSize())
    }
}

private fun loadAddress(controller: AnnieBrowserController, spec: AnnieBrowserSpec, raw: String) {
    val address = raw.trim().let { if ("://" in it) it else "https://$it" }
    if (controller.load(spec, address)) controller.currentUrl = address
}
