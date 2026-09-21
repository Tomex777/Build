package com.example.whatsapp.presentation.browser

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.example.whatsapp.data.browser.NightBrowserSessionStore
import com.example.whatsapp.data.browser.NightBrowserSpec

class NightBrowserController {
    internal var webView: WebView? = null

    var currentUrl by mutableStateOf("")
        internal set
    var pageTitle by mutableStateOf("")
        internal set
    var progress by mutableIntStateOf(0)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var errorText by mutableStateOf<String?>(null)
        internal set
    var blockedUrl by mutableStateOf<String?>(null)
        internal set

    fun canGoBack(): Boolean = webView?.canGoBack() == true
    fun canGoForward(): Boolean = webView?.canGoForward() == true

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun loadUrl(
        spec: NightBrowserSpec,
        rawUrl: String,
    ): Boolean {
        val safe = spec.sanitized()
        if (!safe.isAllowedUrl(rawUrl)) {
            blockedUrl = rawUrl
            return false
        }

        blockedUrl = null
        webView?.loadUrl(rawUrl)
        return true
    }
}

@Composable
fun rememberNightBrowserController(): NightBrowserController =
    remember { NightBrowserController() }

@Composable
fun NightBrowserWebView(
    spec: NightBrowserSpec,
    controller: NightBrowserController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeSpec = remember(spec) { spec.sanitized() }

    DisposableEffect(controller) {
        onDispose {
            controller.webView?.let { webView ->
                runCatching { webView.stopLoading() }
                runCatching { webView.loadUrl("about:blank") }
                runCatching { webView.removeAllViews() }
                runCatching { webView.destroy() }
            }
            controller.webView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                controller.webView = this

                settings.javaScriptEnabled = safeSpec.javaScriptEnabled
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.safeBrowsingEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeSpec.userAgent?.let { settings.userAgentString = it }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(
                    this,
                    safeSpec.thirdPartyCookies,
                )

                webViewClient = object : WebViewClient() {
                    private fun shouldBlock(url: String): Boolean {
                        if (safeSpec.isAllowedUrl(url)) {
                            controller.blockedUrl = null
                            return false
                        }

                        controller.blockedUrl = url
                        return true
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean =
                        request?.url?.toString()?.let(::shouldBlock) ?: true

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?,
                    ): Boolean =
                        url?.let(::shouldBlock) ?: true

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        controller.isLoading = true
                        controller.errorText = null
                        url?.let { controller.currentUrl = it }
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        controller.isLoading = false
                        url?.let { finishedUrl ->
                            controller.currentUrl = finishedUrl
                            NightBrowserSessionStore.saveCurrentUrl(
                                context = context,
                                spec = safeSpec,
                                url = finishedUrl,
                            )
                        }
                        CookieManager.getInstance().flush()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            controller.errorText =
                                error?.description?.toString()?.take(180)
                                    ?: "This page could not be loaded."
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(
                        view: WebView?,
                        newProgress: Int,
                    ) {
                        controller.progress = newProgress.coerceIn(0, 100)
                    }

                    override fun onReceivedTitle(
                        view: WebView?,
                        title: String?,
                    ) {
                        controller.pageTitle = title.orEmpty().take(160)
                    }
                }

                val startUrl = NightBrowserSessionStore.currentUrl(
                    context = viewContext,
                    spec = safeSpec,
                )
                controller.currentUrl = startUrl
                loadUrl(startUrl)
            }
        },
        update = { webView ->
            if (controller.webView !== webView) {
                controller.webView = webView
            }
        },
    )
}
