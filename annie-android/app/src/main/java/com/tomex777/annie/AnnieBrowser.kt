package com.tomex777.annie

import android.content.Context
import android.webkit.CookieManager
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object AnnieBrowserProfiles {
    private const val PROFILE_PREFIX = "annie-session-"
    private val cookieManagers = ConcurrentHashMap<String, CookieManager>()
    private val hex = "0123456789abcdef"

    fun isSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun unavailableMessage(): String =
        "This WebView provider cannot provide isolated browser sessions."

    fun requireSupported() {
        check(isSupported()) { unavailableMessage() }
    }

    fun profileName(sessionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sessionId.toByteArray(Charsets.UTF_8))
        return buildString(PROFILE_PREFIX.length + digest.size * 2) {
            append(PROFILE_PREFIX)
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    fun attach(webView: android.webkit.WebView, sessionId: String): CookieManager {
        requireSupported()
        WebViewCompat.setProfile(webView, profileName(sessionId))
        return WebViewCompat.getProfile(webView).getCookieManager().also {
            cookieManagers[sessionId] = it
        }
    }

    fun cookieManager(webView: android.webkit.WebView): CookieManager {
        requireSupported()
        return WebViewCompat.getProfile(webView).getCookieManager()
    }

    suspend fun cookieManager(sessionId: String): CookieManager =
        withContext(Dispatchers.Main.immediate) {
            requireSupported()
            cookieManagers[sessionId] ?: ProfileStore.getInstance()
                .getOrCreateProfile(profileName(sessionId))
                .getCookieManager()
                .also { cookieManagers[sessionId] = it }
        }

    fun cookieManagerOnMain(sessionId: String): CookieManager {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Browser profile creation must run on the main thread."
        }
        requireSupported()
        return cookieManagers[sessionId] ?: ProfileStore.getInstance()
            .getOrCreateProfile(profileName(sessionId))
            .getCookieManager()
            .also { cookieManagers[sessionId] = it }
    }
}

internal enum class AnnieBrowserVerificationState(val wireName: String) {
    Idle("idle"), Verifying("verifying"), Verified("verified"), Failed("failed");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.wireName == value } ?: Idle
    }
}

/** Structured, script-safe browser request. It never contains Android WebView objects. */
internal data class AnnieBrowserSpec(
    val sessionId: String,
    val url: String,
    val title: String = "Browser",
    val allowedHosts: List<String> = emptyList(),
    val restricted: Boolean = true,
    val verifyAction: String? = null,
    val verifyLabel: String = "Verify",
    val userAgent: String? = null,
    val javaScriptEnabled: Boolean = true,
    val thirdPartyCookies: Boolean = true,
    val verificationState: AnnieBrowserVerificationState = AnnieBrowserVerificationState.Idle,
) {
    fun safeUrl(raw: String): URI? = runCatching { URI(raw.trim()) }.getOrNull()
        ?.takeIf { uri ->
            (uri.scheme?.equals("http", true) == true || uri.scheme?.equals("https", true) == true) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo.isNullOrBlank()
        }

    fun normalizedHosts(): List<String> = (allowedHosts + listOfNotNull(safeUrl(url)?.host))
        .asSequence()
        .map(::normalizeHost)
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_HOSTS)
        .toList()

    fun allows(raw: String): Boolean {
        val uri = safeUrl(raw) ?: return false
        if (!restricted) return true
        val host = normalizeHost(uri.host.orEmpty())
        return normalizedHosts().any { root -> host == root || host.endsWith(".$root") }
    }

    fun sanitized(): AnnieBrowserSpec {
        val safeSessionId = sessionId.trim()
        require(safeSessionId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,95}"))) {
            "Browser sessionId must use letters, numbers, dot, underscore, colon or hyphen."
        }
        val initial = url.trim().take(MAX_URL_CHARS)
        require(safeUrl(initial) != null && allows(initial)) {
            "Browser URL must be an allowed HTTP or HTTPS URL without user info."
        }
        val hosts = normalizedHosts()
        require(!restricted || hosts.isNotEmpty()) { "Restricted browser requires an allowed host." }
        return copy(
            sessionId = safeSessionId,
            url = initial,
            title = title.trim().take(120).ifBlank { "Browser" },
            allowedHosts = hosts,
            verifyAction = verifyAction?.trim()?.take(120)?.takeIf(String::isNotBlank),
            verifyLabel = verifyLabel.trim().take(48).ifBlank { "Verify" },
            userAgent = userAgent?.trim()?.take(512)?.takeIf(String::isNotBlank),
        )
    }

    fun encode(): JSONObject = sanitized().let { safe ->
        JSONObject()
            .put("type", "browser")
            .put("sessionId", safe.sessionId)
            .put("url", safe.url)
            .put("title", safe.title)
            .put("allowedHosts", JSONArray(safe.allowedHosts))
            .put("restricted", safe.restricted)
            .put("verifyAction", safe.verifyAction ?: JSONObject.NULL)
            .put("verifyLabel", safe.verifyLabel)
            .put("userAgent", safe.userAgent ?: JSONObject.NULL)
            .put("javaScriptEnabled", safe.javaScriptEnabled)
            .put("thirdPartyCookies", safe.thirdPartyCookies)
            .put("verificationState", safe.verificationState.wireName)
    }

    companion object {
        const val MAX_HOSTS = 16
        const val MAX_URL_CHARS = 4096

        fun decode(json: JSONObject?): AnnieBrowserSpec? = runCatching {
            json ?: return null
            val hosts = buildList {
                val array = json.optJSONArray("allowedHosts") ?: JSONArray()
                for (index in 0 until minOf(array.length(), MAX_HOSTS)) {
                    array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
            AnnieBrowserSpec(
                sessionId = json.optString("sessionId"),
                url = json.optString("url"),
                title = json.optString("title", "Browser"),
                allowedHosts = hosts,
                restricted = json.optBoolean("restricted", true),
                verifyAction = json.optString("verifyAction").takeIf { it.isNotBlank() && it != "null" },
                verifyLabel = json.optString("verifyLabel", "Verify"),
                userAgent = json.optString("userAgent").takeIf { it.isNotBlank() && it != "null" },
                javaScriptEnabled = json.optBoolean("javaScriptEnabled", true),
                thirdPartyCookies = json.optBoolean("thirdPartyCookies", true),
                verificationState = AnnieBrowserVerificationState.from(json.optString("verificationState")),
            ).sanitized()
        }.getOrNull()

        fun decode(raw: String): AnnieBrowserSpec? = runCatching { decode(JSONObject(raw)) }.getOrNull()

        fun normalizeHost(value: String): String = value.trim().trimEnd('.').lowercase(Locale.US)
    }
}

internal data class AnnieBrowserSession(
    val sessionId: String,
    val currentUrl: String,
    val userAgent: String?,
    val allowedHosts: List<String>,
    val restricted: Boolean,
    val verificationState: AnnieBrowserVerificationState,
    val verifiedAtMillis: Long,
    val scrollY: Int,
)

/** Current URL and verification metadata persist; browser cookies live in the session profile. */
internal object AnnieBrowserSessionStore {
    private const val PREFS = "annie_browser_sessions_v1"

    fun currentUrl(context: Context, spec: AnnieBrowserSpec): String {
        val safe = spec.sanitized()
        val saved = prefs(context).getString(key(safe.sessionId, "url"), null).orEmpty()
        return saved.takeIf(safe::allows) ?: safe.url
    }

    fun save(context: Context, spec: AnnieBrowserSpec, url: String) {
        val safe = spec.sanitized()
        if (!safe.allows(url)) return
        prefs(context).edit()
            .putString(key(safe.sessionId, "url"), url.take(AnnieBrowserSpec.MAX_URL_CHARS))
            .putString(key(safe.sessionId, "spec"), safe.encode().toString())
            .apply()
    }

    /** Registers a session without resetting its mutable URL/scroll state or stealing an existing identity. */
    fun register(context: Context, spec: AnnieBrowserSpec) {
        val safe = spec.sanitized()
        val existing = prefs(context).getString(key(safe.sessionId, "spec"), null)
            ?.let(AnnieBrowserSpec::decode)
        if (existing != null && immutableIdentity(existing) != immutableIdentity(safe)) {
            throw IllegalArgumentException(
                "Browser sessionId is already bound to a different browser session configuration."
            )
        }
        val saved = prefs(context).getString(key(safe.sessionId, "url"), null)
        val current = saved?.takeIf(safe::allows) ?: safe.url
        save(context, safe, current)
    }

    private fun immutableIdentity(spec: AnnieBrowserSpec): String = JSONObject()
        .put("allowedHosts", JSONArray(spec.allowedHosts))
        .put("restricted", spec.restricted)
        .put("verifyAction", spec.verifyAction ?: JSONObject.NULL)
        .put("verifyLabel", spec.verifyLabel)
        .put("userAgent", spec.userAgent ?: JSONObject.NULL)
        .put("javaScriptEnabled", spec.javaScriptEnabled)
        .put("thirdPartyCookies", spec.thirdPartyCookies)
        .toString()

    fun saveScroll(context: Context, spec: AnnieBrowserSpec, scrollY: Int) {
        val safe = spec.sanitized()
        prefs(context).edit().putInt(key(safe.sessionId, "scrollY"), scrollY.coerceAtLeast(0)).apply()
    }

    fun scrollY(context: Context, spec: AnnieBrowserSpec): Int =
        prefs(context).getInt(key(spec.sanitized().sessionId, "scrollY"), 0).coerceAtLeast(0)

    fun setVerification(
        context: Context,
        sessionId: String,
        state: AnnieBrowserVerificationState,
        verifiedAtMillis: Long = 0L,
    ) {
        require(sessionId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,95}")))
        prefs(context).edit()
            .putString(key(sessionId, "state"), state.wireName)
            .putLong(key(sessionId, "verifiedAt"), verifiedAtMillis)
            .apply()
    }

    fun get(context: Context, sessionId: String): AnnieBrowserSession? {
        val p = prefs(context)
        val spec = AnnieBrowserSpec.decode(p.getString(key(sessionId, "spec"), null) ?: return null) ?: return null
        return AnnieBrowserSession(
            sessionId = spec.sessionId,
            currentUrl = currentUrl(context, spec),
            userAgent = spec.userAgent,
            allowedHosts = spec.allowedHosts,
            restricted = spec.restricted,
            verificationState = AnnieBrowserVerificationState.from(p.getString(key(sessionId, "state"), "idle").orEmpty()),
            verifiedAtMillis = p.getLong(key(sessionId, "verifiedAt"), 0L),
            scrollY = p.getInt(key(sessionId, "scrollY"), 0).coerceAtLeast(0),
        )
    }

    fun allows(session: AnnieBrowserSession, rawUrl: String): Boolean {
        val uri = runCatching { java.net.URI(rawUrl) }.getOrNull() ?: return false
        if ((uri.scheme?.equals("http", true) != true && uri.scheme?.equals("https", true) != true) ||
            uri.host.isNullOrBlank() || !uri.rawUserInfo.isNullOrBlank()) return false
        if (!session.restricted) return true
        val host = AnnieBrowserSpec.normalizeHost(uri.host)
        return session.allowedHosts.any { root -> host == root || host.endsWith(".$root") }
    }

    fun clear(context: Context, sessionId: String, clearCookies: Boolean = true) {
        val p = prefs(context)
        val spec = p.getString(key(sessionId, "spec"), null)?.let(AnnieBrowserSpec::decode)
        if (clearCookies) {
            val clear = Runnable {
                runCatching {
                    AnnieBrowserProfiles.cookieManagerOnMain(sessionId).apply {
                        removeAllCookies(null)
                        flush()
                    }
                }
            }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) clear.run()
            else android.os.Handler(android.os.Looper.getMainLooper()).post(clear)
        }
        p.edit().remove(key(sessionId, "url")).remove(key(sessionId, "spec"))
            .remove(key(sessionId, "state")).remove(key(sessionId, "verifiedAt")).remove(key(sessionId, "scrollY")).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(sessionId: String, field: String) = "$sessionId::$field"
}
