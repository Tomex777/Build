@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionSessionContract
import com.night.sora.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

/** A source-owned page opened in Sora's manual login/challenge browser. */
data class SourceBrowserSession(
    val sourceId: String,
    val extensionPackage: String,
    val url: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    val sessionScripts: Map<String, String> = emptyMap(),
)

@Composable
fun SourceWebViewScreen(
    session: SourceBrowserSession,
    extension: InstalledExtension,
    manager: ExtensionManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager.getInstance() }
    val sessionValues = remember(session.url, session.sourceId) { mutableStateMapOf<String, String>() }
    var currentUrl by remember(session.url) { mutableStateOf(session.url) }
    var pageTitle by remember(session.title) { mutableStateOf(session.title) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var cloudflarePage by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun saveSession() {
        val webView = webViewRef
        val url = webView?.url ?: currentUrl
        if (url.isBlank()) return
        cookieManager.flush()
        val payload = JSONObject()
            .put("sourceId", session.sourceId)
            .put("url", url)
            .put("cookieHeader", cookieManager.getCookie(url).orEmpty())
            .put("userAgent", webView?.settings?.userAgentString.orEmpty())
        sessionValues.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) payload.put(key, value)
        }
        manager.call(extension, ExtensionSessionContract.METHOD_STORE_SESSION, payload.toString()) { }
    }

    fun closeBrowser() {
        saveSession()
        onBack()
    }

    BackHandler {
        val webView = webViewRef
        if (webView?.canGoBack() == true) webView.goBack() else closeBrowser()
    }

    DisposableEffect(session.sourceId, session.extensionPackage) {
        onDispose {
            saveSession()
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                pageTitle.ifBlank { session.title.ifBlank { "Web view" } },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                currentUrl,
                                color = SoraMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = ::closeBrowser) { Icon(Icons.Rounded.Close, "Close web view") }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                        IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
                            Icon(Icons.Rounded.ArrowForward, "Forward")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Web view options") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = { menuOpen = false; webViewRef?.reload() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                    onClick = {
                                        menuOpen = false
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                                    onClick = {
                                        menuOpen = false
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear cookies") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                    onClick = {
                                        menuOpen = false
                                        clearCookiesForUrl(cookieManager, currentUrl)
                                        cookieManager.flush()
                                        sessionValues.clear()
                                        webViewRef?.reload()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SoraBg),
                )

                if (cloudflarePage) {
                    Surface(
                        color = SoraSurfaceHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = SoraAccent, modifier = Modifier.size(19.dp))
                            Text(
                                "Complete the browser challenge manually. Sora will keep this source session for the extension.",
                                color = SoraMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 9.dp),
                            )
                        }
                    }
                }

                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = SoraAccent,
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.loadsImagesAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    session.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value?.takeIf(String::isNotBlank)?.let {
                        settings.userAgentString = it
                    }
                    WebView.setWebContentsDebuggingEnabled(false)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress.coerceIn(0, 100)
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title.orEmpty().ifBlank { session.title }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            url?.let { currentUrl = it }
                            cloudflarePage = false
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            view?.evaluateJavascript(
                                "(function(){try{return !!window._cf_chl_opt || (document.body && document.body.innerText.indexOf('Ray ID') >= 0);}catch(e){return false;}})()"
                            ) { raw -> cloudflarePage = raw == "true" }
                            session.sessionScripts.forEach { (key, script) ->
                                if (key.isNotBlank() && script.isNotBlank()) {
                                    view?.evaluateJavascript(script) { raw ->
                                        decodeJavascriptString(raw).takeIf(String::isNotBlank)?.let { value ->
                                            sessionValues[key] = value
                                        }
                                    }
                                }
                            }
                            cookieManager.flush()
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            if (url.startsWith("blob:http")) return false
                            if (url.startsWith("intent://")) return true
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                view?.loadUrl(url, session.headers)
                                return true
                            }
                            return false
                        }
                    }

                    loadUrl(session.url, session.headers)
                }
            },
            update = { webViewRef = it },
        )
    }
}

private fun decodeJavascriptString(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank() || value == "null" || value == "undefined") return ""
    return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
}

private fun clearCookiesForUrl(cookieManager: CookieManager, url: String) {
    val cookieHeader = cookieManager.getCookie(url).orEmpty()
    if (cookieHeader.isBlank()) return
    cookieHeader.split(';').forEach { token ->
        val name = token.substringBefore('=').trim()
        if (name.isNotBlank()) cookieManager.setCookie(url, "$name=; Max-Age=0; Path=/")
    }
}
