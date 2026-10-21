package com.night.offlinegames

import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/** Temporary offline-only compatibility view until the remaining native ports land. */
@Composable
internal fun OfflineBundledGameView(gameId: String, directory: String, onExit: () -> Unit) {
    if (WebView.getCurrentWebViewPackage() == null) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Native version in progress")
            Spacer(Modifier.height(12.dp))
            Text("This device has no WebView provider. This game is being rebuilt in Kotlin.")
            TextButton(onClick = onExit) { Text("Back to games") }
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context)).build()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.setSupportZoom(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest
                    ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url
                        if (uri.host == "appassets.androidplatform.net" &&
                            uri.path?.startsWith("/" + directory + "/") == true) return false
                        onExit()
                        return true
                    }
                }
                loadUrl("https://appassets.androidplatform.net/" + directory + "/index.html?game=" + gameId)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() }
    )
}
