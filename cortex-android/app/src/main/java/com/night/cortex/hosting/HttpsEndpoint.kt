package com.night.cortex.hosting

import java.net.URI

/**
 * Normalizes and validates a Cortex Agent endpoint before a bearer credential
 * can ever be attached to a request.
 *
 * User-info, query strings and fragments are deliberately rejected: Cortex
 * stores the endpoint outside the encrypted secret vault, so credentials and
 * signed URLs must never be smuggled into that value.
 */
internal fun normalizeHttpsEndpoint(value: String): String {
    val trimmed = value.trim().removeSuffix("/")
    require(trimmed.isNotBlank()) { "Server URL is required." }

    val uri = runCatching { URI(trimmed) }
        .getOrElse { throw IllegalArgumentException("Enter a valid HTTPS server URL.") }

    require(uri.scheme.equals("https", ignoreCase = true)) {
        "Server URL must use HTTPS."
    }
    require(!uri.host.isNullOrBlank()) {
        "Server URL must include a valid host."
    }
    require(uri.rawUserInfo == null) {
        "Server URL must not contain embedded credentials."
    }
    require(uri.rawQuery == null && uri.rawFragment == null) {
        "Server URL must not contain a query string or fragment."
    }
    require(uri.port == -1 || uri.port in 1..65535) {
        "Server URL contains an invalid port."
    }

    return trimmed
}

internal fun isValidHttpsEndpoint(value: String): Boolean =
    runCatching { normalizeHttpsEndpoint(value) }.isSuccess

internal fun safeRemoteError(service: String, statusCode: Int): String = when (statusCode) {
    401, 403 -> "$service authentication failed. Check the saved credential."
    408, 504 -> "$service request timed out."
    429 -> "$service is rate limiting requests. Try again shortly."
    in 500..599 -> "$service is temporarily unavailable (HTTP $statusCode)."
    else -> "$service request failed (HTTP $statusCode)."
}
