package com.example.whatsapp.data.browser

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject

data class NightBrowserVerificationOutcome(
    val verified: Boolean,
    val message: String,
)

object NightBrowserVerification {
    fun buildActionPayload(
        context: Context,
        spec: NightBrowserSpec,
        extensionPayload: JSONObject,
    ): JSONObject {
        val safe = spec.sanitized()
        val currentUrl = NightBrowserSessionStore.currentUrl(
            context = context,
            spec = safe,
        )
        val cookieHeader = CookieManager.getInstance()
            .getCookie(currentUrl)
            ?.trim()
            .orEmpty()
        val cookieNames = cookieHeader
            .split(';')
            .asSequence()
            .map { it.trim().substringBefore('=').trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(64)
            .toList()

        return JSONObject()
            .put(
                "browserSession",
                JSONObject()
                    .put("sessionId", safe.sessionId)
                    .put("currentUrl", currentUrl)
                    .put(
                        "host",
                        runCatching { Uri.parse(currentUrl).host.orEmpty() }
                            .getOrDefault("")
                    )
                    .put("hasCookies", cookieHeader.isNotBlank())
                    .put("cookieHeader", cookieHeader)
                    .put("cookieNames", JSONArray(cookieNames))
                    .put("allowedHosts", JSONArray(safe.allowedHosts))
            )
            .put("extensionPayload", extensionPayload)
    }

    fun interpretResult(result: JSONObject?): NightBrowserVerificationOutcome {
        if (result == null) {
            return NightBrowserVerificationOutcome(
                verified = false,
                message = "The extension is not available to verify this session.",
            )
        }

        val explicitVerified =
            if (result.has("verified")) result.optBoolean("verified", false) else null
        val status = result.optString("status").trim().lowercase()
        val verified = explicitVerified ?: (
            status == "verified" ||
                status == "success" ||
                status == "ok" ||
                status == "valid"
            )

        val message = sequenceOf(
            result.optString("message"),
            result.optString("detail"),
            result.optString("error"),
        )
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            ?: if (verified) "Session verified." else "Verification failed."

        return NightBrowserVerificationOutcome(
            verified = verified,
            message = message.take(240),
        )
    }

    fun verifying(spec: NightBrowserSpec): NightBrowserSpec =
        spec.sanitized().copy(
            verificationState = NightBrowserVerificationState.Verifying,
            verificationMessage = "Checking session…",
            verifiedAt = null,
        )

    fun applyOutcome(
        spec: NightBrowserSpec,
        outcome: NightBrowserVerificationOutcome,
        now: Long = System.currentTimeMillis(),
    ): NightBrowserSpec =
        spec.sanitized().copy(
            verificationState = if (outcome.verified) {
                NightBrowserVerificationState.Verified
            } else {
                NightBrowserVerificationState.Failed
            },
            verificationMessage = outcome.message,
            verifiedAt = now.takeIf { outcome.verified },
        )
}
