package eu.kanade.tachiyomi.ui.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.nami.compat.aniyomi.AniyomiBrowserSessionRegistry

class WebViewActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var titleView: TextView
    private lateinit var progressBar: ProgressBar
    private val cookieManager by lazy { CookieManager.getInstance() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(URL_KEY)
        if (initialUrl.isNullOrBlank()) {
            finish()
            return
        }
        val sourceId = intent.extras?.takeIf { it.containsKey(SOURCE_KEY) }?.getLong(SOURCE_KEY)
        val headers = AniyomiBrowserSessionRegistry.headers(sourceId)

        titleView = TextView(this).apply {
            text = intent.getStringExtra(TITLE_KEY).orEmpty()
            maxLines = 1
            setPadding(dp(12), 0, dp(8), 0)
        }
        val close = Button(this).apply { text = "×"; setOnClickListener { finish() } }
        val back = Button(this).apply { text = "‹"; setOnClickListener { if (webView.canGoBack()) webView.goBack() } }
        val forward = Button(this).apply { text = "›"; setOnClickListener { if (webView.canGoForward()) webView.goForward() } }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(close, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(back, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(forward, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(titleView, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
                ?.takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) titleView.text = title
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    url?.let { titleView.contentDescription = it }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    cookieManager.flush()
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val next = request.url.toString()
                    return !next.startsWith("http://") && !next.startsWith("https://") && !next.startsWith("blob:http")
                }
            }
            loadUrl(initialUrl, headers)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    @Deprecated("Deprecated in Android framework; retained for minSdk 26 compatibility.")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            cookieManager.flush()
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val URL_KEY = "url_key"
        private const val SOURCE_KEY = "source_key"
        private const val TITLE_KEY = "title_key"
        private const val ANIME_KEY = "anime_key"

        fun newIntent(
            context: Context,
            url: String,
            sourceId: Long? = null,
            title: String? = null,
            isAnime: Boolean = false,
        ): Intent = Intent(context, WebViewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(URL_KEY, url)
            putExtra(SOURCE_KEY, sourceId)
            putExtra(TITLE_KEY, title)
            putExtra(ANIME_KEY, isAnime)
        }
    }
}
