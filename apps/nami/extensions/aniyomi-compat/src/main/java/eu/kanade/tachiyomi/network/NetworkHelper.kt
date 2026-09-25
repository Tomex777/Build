package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebSettings
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext
    private val defaultUserAgent = WebSettings.getDefaultUserAgent(appContext)

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    @Deprecated("The regular client handles shared WebView cookies")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = defaultUserAgent
}
