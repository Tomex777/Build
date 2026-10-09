package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    fun page(): String {
        // Do not let inline JS or CSS close their containing tag accidentally.
        val safeCss = css.replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\/style")
        val safeJs = javascript.replace(Regex("</script", RegexOption.IGNORE_CASE), "<\\/script")
        return """
            <!doctype html>
            <html lang="en"><head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <meta http-equiv="Content-Security-Policy"
                    content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'">
              <style>
                :root { color-scheme: dark; }
                html, body { box-sizing:border-box; padding:0; margin:0; background:#0b1828; color:#eef5ff; font-family:system-ui,sans-serif; }
                *, *::before, *::after { box-sizing:inherit; }
                body { min-height:100vh; overscroll-behavior:contain; }
                button, input { font:inherit; }
                $safeCss
              </style>
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

internal class AnnieCanvasController(private val id: String) {
    private var web: WebView? = null
    private var inlineHost: FrameLayout? = null
    private var document: AnnieCanvasDocument? = null
    var expanded: Boolean = false
        private set

    internal val webView: WebView? get() = web

    private fun prepare(context: Context, next: AnnieCanvasDocument): WebView {
        val current = web ?: WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(11, 24, 40))
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
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                    callback?.invoke(origin, false, false)
                }
            }
        }.also { web = it }
        if (document != next) {
            document = next
            // null base URL is about:blank, not a network origin; CSP disables fetch and embeds.
            current.loadDataWithBaseURL(null, next.page(), "text/html", "UTF-8", null)
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
        inlineHost = host
        val view = prepare(context, next)
        if (!expanded) reparent(view, host)
    }

    fun beginFullscreen() {
        expanded = true
        web?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    fun attachFullscreen(host: FrameLayout) {
        web?.let { reparent(it, host) }
    }

    fun restart() {
        document?.let { web?.loadDataWithBaseURL(null, it.page(), "text/html", "UTF-8", null) }
    }

    fun returnToInline() {
        expanded = false
        val host = inlineHost
        if (host != null && host.isAttachedToWindow) {
            web?.let { reparent(it, host) }
        } else {
            destroy()
        }
    }

    fun inlineDisposed() {
        inlineHost = null
        if (!expanded) destroy()
    }

    fun destroy() {
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        web = null
        inlineHost = null
        document = null
        expanded = false
        AnnieCanvasSessions.remove(id, this)
    }

    val label: String get() = document?.title ?: "Canvas"
}

internal object AnnieCanvasSessions {
    private val sessions = ConcurrentHashMap<String, AnnieCanvasController>()
    fun get(id: String): AnnieCanvasController = sessions.getOrPut(id) { AnnieCanvasController(id) }
    fun find(id: String): AnnieCanvasController? = sessions[id]
    fun remove(id: String, controller: AnnieCanvasController) { sessions.remove(id, controller) }
}

@Composable
internal fun AnnieCanvasMessage(payload: JSONObject) {
    val doc = remember(payload.toString()) { AnnieCanvasDocument.from(payload) }
    if (doc == null) {
        Text("Canvas content is missing or exceeds the message size limit.", color = Color(0xFFFF9B91))
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val id = rememberAnnieBrowserInstanceId()
    val controller = remember(id) { AnnieCanvasSessions.get(id) }
    DisposableEffect(controller) { onDispose { controller.inlineDisposed() } }

    Surface(
        color = BrowserBubble,
        shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp),
        modifier = Modifier.fillMaxWidth().testTag("annie_canvas_message"),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.title, color = BrowserBrightText, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 7.dp))
                TextButton(onClick = controller::restart, modifier = Modifier.testTag("annie_canvas_restart")) {
                    Text("Restart", color = BrowserSoftText, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    controller.beginFullscreen()
                    context.startActivity(AnnieCanvasActivity.intent(context, id))
                }, modifier = Modifier.testTag("annie_canvas_fullscreen")) {
                    Text("Expand ⛶", color = BrowserTeal, fontSize = 12.sp)
                }
            }
            AndroidView(
                factory = { FrameLayout(it).also { host -> controller.attachInline(it, host, doc) } },
                update = { host -> controller.attachInline(host.context, host, doc) },
                modifier = Modifier.fillMaxWidth().height(doc.heightDp.dp)
                    .testTag("annie_canvas_webview"),
            )
            Text("Interactive • offline sandbox", fontSize = 10.sp,
                color = BrowserSoftText, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        }
    }
}

internal class AnnieCanvasActivity : ComponentActivity() {
    private var controller: AnnieCanvasController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = intent.getStringExtra(EXTRA_ID)?.let(AnnieCanvasSessions::find)
        if (controller == null) { finish(); return }
        setContent {
            AnnieTheme {
                Column(
                    Modifier.fillMaxSize().background(BrowserNight)
                        .statusBarsPadding().navigationBarsPadding()
                        .testTag("annie_canvas_fullscreen_screen"),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(controller?.label ?: "Canvas", color = BrowserBrightText,
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { controller?.restart() }) { Text("Restart") }
                        TextButton(onClick = ::finish, modifier = Modifier.testTag("annie_canvas_close")) {
                            Text("Close")
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

    override fun onDestroy() {
        controller?.returnToInline()
        controller = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "annie.canvas.instance"
        fun intent(context: Context, id: String): Intent =
            Intent(context, AnnieCanvasActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
