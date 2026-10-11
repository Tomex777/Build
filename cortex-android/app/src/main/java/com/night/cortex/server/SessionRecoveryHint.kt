package com.night.cortex.server

/** Safe operator guidance from sanitized MSCC account state; never modifies auth. */
internal fun sessionRecoveryHint(status: String, reason: String): String? {
    val normalized = status.trim().lowercase()
    val description = reason.lowercase()
    return when {
        "bad mac" in description || "invalid mac" in description ->
            "Message decryption failed (Bad MAC). This does not prove that the saved login is revoked. Check session diagnostics and reconnect first; do not delete authentication automatically."
        normalized in setOf("auth-invalid", "logged-out", "revoked", "session-expired", "expired") ->
            "MSCC reports that this WhatsApp sign-in needs attention. If a normal reconnect fails, use Re-pair; saved authentication must not be deleted without confirmation."
        normalized in setOf("reconnecting", "connecting") ->
            "MSCC is attempting to reconnect. Wait for the next attempt before requesting a new pairing session."
        else -> null
    }
}
