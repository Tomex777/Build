package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Android WebView-backed cookie jar matching Aniyomi's extension-facing network ABI.
 */
class AndroidCookieJar : CookieJar {
    private val manager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> {
        val raw = manager.getCookie(url.toString()) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    fun remove(
        url: HttpUrl,
        cookieNames: List<String>? = null,
        maxAge: Int = -1,
    ): Int {
        val urlString = url.toString()
        val raw = manager.getCookie(urlString) ?: return 0
        val names = raw.split(';')
            .map { it.substringBefore('=').trim() }
            .filter { cookieNames == null || it in cookieNames }

        names.forEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
        manager.flush()
        return names.size
    }

    fun removeAll() {
        manager.removeAllCookies {}
        manager.flush()
    }
}
