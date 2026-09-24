/*
 * Nami host implementation for the Aniyomi extension network contract.
 * Package/class names intentionally match extensions-lib ABI.
 */
package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext
    private val defaultUserAgent = WebSettings.getDefaultUserAgent(appContext)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Deprecated("Use client")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = defaultUserAgent
}

private class WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { cookie ->
            manager.setCookie(url.toString(), cookie.toString())
        }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { pair ->
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            Cookie.parse(url, trimmed)
        }
    }
}
