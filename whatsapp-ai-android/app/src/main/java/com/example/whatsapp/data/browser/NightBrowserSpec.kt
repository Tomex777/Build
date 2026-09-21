package com.example.whatsapp.data.browser

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class NightBrowserSpec(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val initialUrl: String,
    val allowedHosts: List<String> = emptyList(),
    val title: String = "Browser",
    val verifyActionId: String? = null,
    val verifyLabel: String = "Verify",
    val javaScriptEnabled: Boolean = true,
    val thirdPartyCookies: Boolean = true,
    val userAgent: String? = null,
) {
    fun initialUri(): URI? = safeUri(initialUrl)

    fun normalizedAllowedHosts(): Set<String> {
        val declared = allowedHosts
            .asSequence()
            .map(::normalizeHost)
            .filter { it.isNotBlank() }
            .take(16)
            .toMutableSet()

        initialUri()?.host
            ?.let(::normalizeHost)
            ?.takeIf { it.isNotBlank() }
            ?.let(declared::add)

        return declared
    }

    fun isAllowedUrl(rawUrl: String): Boolean {
        val uri = safeUri(rawUrl) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme != "https" && scheme != "http") return false
        if (!uri.userInfo.isNullOrBlank()) return false

        val host = normalizeHost(uri.host ?: return false)
        val allowed = normalizedAllowedHosts()
        if (allowed.isEmpty()) return false

        return allowed.any { root ->
            host == root || host.endsWith("." + root)
        }
    }

    fun sanitized(): NightBrowserSpec {
        val safeSession = sessionId.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .take(96)
            .ifBlank { "browser" }

        val safeUrl = initialUrl.trim().take(4096)
        require(isAllowedUrl(safeUrl)) {
            "Browser initialUrl must be an allowed http/https URL."
        }

        return copy(
            schemaVersion = schemaVersion.coerceAtLeast(1),
            sessionId = safeSession,
            initialUrl = safeUrl,
            allowedHosts = normalizedAllowedHosts().take(16),
            title = title.trim().take(120).ifBlank { "Browser" },
            verifyActionId = verifyActionId
                ?.trim()
                ?.take(120)
                ?.takeIf { it.isNotBlank() },
            verifyLabel = verifyLabel.trim().take(48).ifBlank { "Verify" },
            userAgent = userAgent
                ?.trim()
                ?.take(512)
                ?.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        private fun safeUri(raw: String): URI? =
            runCatching { URI(raw.trim()) }
                .getOrNull()
                ?.takeIf { !it.host.isNullOrBlank() }

        private fun normalizeHost(raw: String): String =
            raw.trim()
                .trimEnd('.')
                .lowercase(Locale.US)
    }
}

object NightBrowserSpecCodec {
    fun encode(spec: NightBrowserSpec): JSONObject {
        val safe = spec.sanitized()
        return JSONObject()
            .put("schemaVersion", safe.schemaVersion)
            .put("sessionId", safe.sessionId)
            .put("initialUrl", safe.initialUrl)
            .put("allowedHosts", JSONArray(safe.allowedHosts))
            .put("title", safe.title)
            .put("verifyActionId", safe.verifyActionId ?: "")
            .put("verifyLabel", safe.verifyLabel)
            .put("javaScriptEnabled", safe.javaScriptEnabled)
            .put("thirdPartyCookies", safe.thirdPartyCookies)
            .put("userAgent", safe.userAgent ?: "")
    }

    fun decode(json: JSONObject?): NightBrowserSpec? {
        json ?: return null

        return runCatching {
            val allowedHosts = buildList {
                val array = json.optJSONArray("allowedHosts")
                if (array != null) {
                    for (index in 0 until minOf(array.length(), 16)) {
                        val host = array.optString(index).trim()
                        if (host.isNotBlank()) add(host)
                    }
                }
            }

            NightBrowserSpec(
                schemaVersion = json.optInt("schemaVersion", 1),
                sessionId = json.optString("sessionId"),
                initialUrl = json.optString("initialUrl"),
                allowedHosts = allowedHosts,
                title = json.optString("title").ifBlank { "Browser" },
                verifyActionId = json.optString("verifyActionId")
                    .trim()
                    .takeIf { it.isNotBlank() },
                verifyLabel = json.optString("verifyLabel").ifBlank { "Verify" },
                javaScriptEnabled = json.optBoolean("javaScriptEnabled", true),
                thirdPartyCookies = json.optBoolean("thirdPartyCookies", true),
                userAgent = json.optString("userAgent")
                    .trim()
                    .takeIf { it.isNotBlank() },
            ).sanitized()
        }.getOrNull()
    }

    fun decode(raw: String): NightBrowserSpec? =
        runCatching { JSONObject(raw) }
            .getOrNull()
            ?.let(::decode)
}
