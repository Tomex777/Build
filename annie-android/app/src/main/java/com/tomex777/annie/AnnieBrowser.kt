package com.tomex777.annie

import android.content.Context
import android.webkit.CookieManager
import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

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
)

/** Current URL and verification metadata persist; CookieManager remains the cookie authority. */
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

    /** Registers metadata without resetting a session's last visited URL. */
    fun register(context: Context, spec: AnnieBrowserSpec) {
        val safe = spec.sanitized()
        val saved = prefs(context).getString(key(safe.sessionId, "url"), null)
        val current = saved?.takeIf(safe::allows) ?: safe.url
        save(context, safe, current)
    }

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
        if (clearCookies && spec != null) {
            val cookies = CookieManager.getInstance()
            spec.allowedHosts.forEach { host ->
                val origin = "https://$host/"
                cookies.getCookie(origin).orEmpty().split(';').map(String::trim).forEach { cookie ->
                    val name = cookie.substringBefore('=').trim()
                    if (name.isNotBlank()) {
                        cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$host")
                    }
                }
            }
            cookies.flush()
        }
        p.edit().remove(key(sessionId, "url")).remove(key(sessionId, "spec"))
            .remove(key(sessionId, "state")).remove(key(sessionId, "verifiedAt")).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(sessionId: String, field: String) = "$sessionId::$field"
}
