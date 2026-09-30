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

    val schemeSeparator = trimmed.indexOf(':')
    return "https" + trimmed.substring(schemeSeparator)
}

internal fun isValidHttpsEndpoint(value: String): Boolean =
    runCatching { normalizeHttpsEndpoint(value) }.isSuccess

/**
 * A saved bearer token may only be reused for the exact HTTPS endpoint it was
 * saved with. Changing hosts requires the user to supply a token explicitly so
 * Cortex never sends one server's credential to another server.
 */
internal fun canSaveHttpsConnection(
    savedEndpoint: String,
    candidateEndpoint: String,
    hasSavedToken: Boolean,
    enteredToken: String,
): Boolean {
    val candidate = runCatching { normalizeHttpsEndpoint(candidateEndpoint) }.getOrNull() ?: return false
    if (enteredToken.isNotBlank()) return true
    if (!hasSavedToken) return false
    val saved = runCatching { normalizeHttpsEndpoint(savedEndpoint) }.getOrNull() ?: return false
    return saved == candidate
}

