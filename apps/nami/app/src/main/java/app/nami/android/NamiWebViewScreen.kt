@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Focused Aniyomi-style source browser: close, back/forward, progress, refresh, share,
 * open externally, and cookie clearing. It intentionally lives outside source implementations.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NamiWebViewScreen(
    title: String,
    url: String,
    onClose: () -> Unit,
    headers: Map<String, String> = emptyMap(),
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun updateNavigation(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
        currentUrl = view.url ?: currentUrl
        pageTitle = view.title
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(pageTitle ?: title, maxLines = 1)
                            Text(currentUrl, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { webView?.goBack() },
                            enabled = canGoBack,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(
                            onClick = { webView?.goForward() },
                            enabled = canGoForward,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        menuOpen = false
                                        webView?.reload()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        menuOpen = false
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        context.startActivity(Intent.createChooser(share, null))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    onClick = {
                                        menuOpen = false
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear cookies") },
                                    onClick = {
                                        menuOpen = false
                                        CookieManager.getInstance().removeAllCookies {
                                            CookieManager.getInstance().flush()
                                            webView?.reload()
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webView = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    headers.entries
                        .firstOrNull { it.key.equals("user-agent", ignoreCase = true) }
                        ?.value
                        ?.takeIf { it.isNotBlank() }
                        ?.let { settings.userAgentString = it }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            view?.let(::updateNavigation)
                        }

                        override fun onReceivedTitle(view: WebView?, newTitle: String?) {
                            pageTitle = newTitle
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            url?.let { currentUrl = it }
                            updateNavigation(view)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            url?.let { currentUrl = it }
                            updateNavigation(view)
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            url?.let { currentUrl = it }
                            updateNavigation(view)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val next = request.url.toString()
                            if (next.startsWith("intent://")) return true
                            if (next.startsWith("blob:http")) return false
                            view.loadUrl(next, headers)
                            return true
                        }
                    }

                    loadUrl(url, headers)
                }
            },
        )
    }
}
