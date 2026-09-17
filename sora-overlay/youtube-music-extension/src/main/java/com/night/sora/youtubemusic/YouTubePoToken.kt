package com.night.sora.youtubemusic

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, extension-local BotGuard/PoToken adapter derived from Spotui's pinned
 * PoToken implementation (GPL-3.0). YouTube-specific anti-bot/session logic stays
 * in the YouTube Music extension; Sora Core only receives ordinary stream URLs
 * and request headers.
 */
internal class YouTubePoTokenProvider(
    context: Context,
) {
    data class Tokens(
        val playerRequestPoToken: String,
        val streamingDataPoToken: String,
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var generator: Generator? = null
    private var sessionId: String? = null
    private var streamingToken: String? = null

    suspend fun tokens(videoId: String, visitorData: String): Result<Tokens> = runCatching {
        require(visitorData.isNotBlank()) { "VISITOR_DATA is required for a web PoToken" }
        mutex.withLock {
            var active = generator
            if (active == null || active.isExpired || sessionId != visitorData) {
                active?.let { old -> withContext(Dispatchers.Main.immediate) { old.close() } }
                active = Generator(appContext)
                withTimeout(INIT_TIMEOUT_MS) { active.initialize() }
                generator = active
                sessionId = visitorData
                streamingToken = withTimeout(TOKEN_TIMEOUT_MS) { active.generate(visitorData) }
                Log.i(TAG, "PoToken generator ready visitor=true")
            }

            val playerToken = withTimeout(TOKEN_TIMEOUT_MS) { active.generate(videoId) }
            val requestToken = streamingToken ?: error("Missing visitor-bound PoToken")
            Tokens(
                playerRequestPoToken = requestToken,
                streamingDataPoToken = playerToken,
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private class Generator(
        private val context: Context,
    ) {
        private val scope: CoroutineScope = MainScope()
        private val initialized = CompletableDeferred<Unit>()
        private val pending = ConcurrentHashMap<String, CancellableContinuation<String>>()
        private val webView = WebView(context)
        private var expirationInstant: Instant = Instant.EPOCH

        val isExpired: Boolean
            get() = Instant.now().isAfter(expirationInstant)

        init {
            webView.settings.javaScriptEnabled = true
            webView.settings.userAgentString = USER_AGENT
            webView.settings.blockNetworkLoads = true
            webView.addJavascriptInterface(this, JS_INTERFACE)
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val text = message.message().orEmpty()
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, "PoToken JS: $text")
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, "PoToken JS: $text")
                        else -> Log.d(TAG, "PoToken JS: $text")
                    }
                    if (text.contains("Uncaught", ignoreCase = true)) {
                        fail(IllegalStateException(text))
                    }
                    return true
                }
            }
        }

        suspend fun initialize() {
            val html = withContext(Dispatchers.IO) {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }
            withContext(Dispatchers.Main.immediate) {
                val data = html.replaceFirst(
                    "</script>",
                    "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
                )
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    data,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            initialized.await()
        }

        @JavascriptInterface
        fun downloadAndRunBotguard() {
            request(
                url = "https://www.youtube.com/api/jnn/v1/Create",
                body = "[ \"$REQUEST_KEY\" ]",
            ) { raw ->
                val challenge = parseChallengeData(raw)
                webView.evaluateJavascript(
                    """try {
                        data = $challenge;
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput;
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse);
                        }, function (error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\\n" + (error.stack || ''));
                        });
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\\n" + (error.stack || ''));
                    }""".trimIndent(),
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onRunBotguardResult(botguardResponse: String) {
            request(
                url = "https://www.youtube.com/api/jnn/v1/GenerateIT",
                body = "[ \"$REQUEST_KEY\", ${JSONObject.quote(botguardResponse)} ]",
            ) { raw ->
                val (integrityToken, expirationSeconds) = parseIntegrityTokenData(raw)
                expirationInstant = Instant.now()
                    .plusSeconds(expirationSeconds)
                    .minus(10, ChronoUnit.MINUTES)
                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityToken;
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $JS_INTERFACE.onMinterCreated();
                        }).catch(function(error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\\n" + (error.stack || ''));
                        });
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\\n" + (error.stack || ''));
                    }""".trimIndent(),
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onMinterCreated() {
            Log.i(TAG, "PoToken minter initialized")
            initialized.complete(Unit)
        }

        @JavascriptInterface
        fun onJsInitializationError(error: String) {
            fail(IllegalStateException("PoToken JavaScript initialization failed: $error"))
        }

        suspend fun generate(identifier: String): String = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                pending[identifier] = continuation
                continuation.invokeOnCancellation { pending.remove(identifier, continuation) }
                val literal = JSONObject.quote(identifier)
                webView.evaluateJavascript(
                    """try {
                        identifier = $literal;
                        u8Identifier = ${stringToU8(identifier)};
                        obtainPoToken(u8Identifier).then(function(poTokenU8) {
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8.join(","));
                        }).catch(function(error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\\n" + (error.stack || ''));
                        });
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\\n" + (error.stack || ''));
                    }""".trimIndent(),
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onObtainPoTokenResult(identifier: String, bytes: String) {
            pending.remove(identifier)?.resume(u8ToBase64(bytes))
        }

        @JavascriptInterface
        fun onObtainPoTokenError(identifier: String, error: String) {
            pending.remove(identifier)?.resumeWithException(
                IllegalStateException("PoToken mint failed: $error"),
            )
        }

        private fun request(url: String, body: String, onSuccess: (String) -> Unit) {
            scope.launchSafely {
                val response = withContext(Dispatchers.IO) { post(url, body) }
                onSuccess(response)
            }
        }

        private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
            kotlinx.coroutines.launch {
                try {
                    block()
                } catch (error: Throwable) {
                    fail(error)
                }
            }
        }

        private fun fail(error: Throwable) {
            Log.e(TAG, "PoToken failure: ${error.message}", error)
            initialized.completeExceptionally(error)
            pending.values.toList().forEach { continuation ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            pending.clear()
        }

        fun close() {
            pending.values.toList().forEach { continuation -> continuation.cancel() }
            pending.clear()
            scope.cancel()
            webView.removeJavascriptInterface(JS_INTERFACE)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    companion object {
        private const val TAG = "SoraYouTubePoToken"
        private const val ASSET_NAME = "po_token.html"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val JS_INTERFACE = "PoTokenBridge"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val INIT_TIMEOUT_MS = 30_000L
        private const val TOKEN_TIMEOUT_MS = 15_000L

        private fun post(url: String, body: String): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json+protobuf")
                setRequestProperty("x-goog-api-key", GOOGLE_API_KEY)
                setRequestProperty("x-user-agent", "grpc-web-javascript/0.1")
            }
            return try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code != 200) error("BotGuard HTTP $code: ${text.take(200)}")
                text
            } finally {
                connection.disconnect()
            }
        }

        private fun parseChallengeData(raw: String): String {
            val scrambled = JSONArray(raw)
            val challenge = if (
                scrambled.length() > 1 && scrambled.opt(1) is String
            ) {
                JSONArray(descramble(scrambled.getString(1)))
            } else {
                scrambled.getJSONArray(0)
            }

            val interpreter = JSONObject().apply {
                put(
                    "privateDoNotAccessOrElseSafeScriptWrappedValue",
                    firstString(challenge.optJSONArray(1)) ?: JSONObject.NULL,
                )
                put(
                    "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                    firstString(challenge.optJSONArray(2)) ?: JSONObject.NULL,
                )
            }
            return JSONObject().apply {
                put("messageId", challenge.getString(0))
                put("interpreterJavascript", interpreter)
                put("interpreterHash", challenge.getString(3))
                put("program", challenge.getString(4))
                put("globalName", challenge.getString(5))
                put("clientExperimentsStateBlob", challenge.getString(7))
            }.toString()
        }

        private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
            val data = JSONArray(raw)
            val bytes = decodeYouTubeBase64(data.getString(0))
            return newUint8Array(bytes) to data.getLong(1)
        }

        private fun firstString(array: JSONArray?): String? {
            if (array == null) return null
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value is String) return value
            }
            return null
        }

        private fun stringToU8(value: String): String = newUint8Array(value.toByteArray(Charsets.UTF_8))

        private fun newUint8Array(bytes: ByteArray): String =
            "new Uint8Array([${bytes.joinToString(",") { it.toUByte().toString() }}])"

        private fun descramble(value: String): String = decodeYouTubeBase64(value)
            .map { (it + 97).toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)

        private fun decodeYouTubeBase64(value: String): ByteArray {
            var normalized = value
                .replace('-', '+')
                .replace('_', '/')
                .replace('.', '=')
            val remainder = normalized.length % 4
            if (remainder != 0) normalized += "=".repeat(4 - remainder)
            return Base64.decode(normalized, Base64.DEFAULT)
        }

        private fun u8ToBase64(value: String): String {
            val bytes = value
                .split(',')
                .filter(String::isNotBlank)
                .map { it.trim().toInt().toByte() }
                .toByteArray()
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
        }
    }
}
