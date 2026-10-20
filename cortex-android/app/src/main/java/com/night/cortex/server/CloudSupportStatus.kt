package com.night.cortex.server

/** Precise, non-secret diagnostics for Cloud setup and Agent compatibility. */
internal fun cloudEnvironmentIssue(error: Throwable): String = when (error) {
    is CortexHttpException -> when (error.statusCode) {
        401 -> "The saved Cortex access token was rejected (HTTP 401). Update your server connection token; MEGA sign-in was not tested."
        403 -> "Cortex access was denied (HTTP 403). Check the server URL and Cortex access token; MEGA credentials were not checked."
        404 -> "The installed Cortex Agent is too old for secure ENV management. Update the Agent; MEGA credentials were not checked."
        502, 503 -> "Cortex Agent cannot reach NIGHT. Check that both services are running."
        else -> "Secure ENV endpoint returned HTTP ${error.statusCode}. No credentials were saved."
    }
    else -> "Secure ENV endpoint could not be reached. Check the Cortex Agent connection."
}

internal fun cloudDashboardIssue(error: Throwable): String = when (error) {
    is CortexHttpException -> when (error.statusCode) {
        404 -> "The Cloud API is unavailable on this Agent. Update Cortex Agent."
        502, 503 -> "Cortex authentication succeeded, but NIGHT Cloud could not complete the MEGA check (HTTP ${error.statusCode}). Check whether the MEGA credentials were saved, restart NIGHT to apply them, and refresh. MEGA sign-in or the upstream service may have failed."
        401 -> "Cortex authorization expired. Reconnect the server in Settings."
        403 -> "Cortex access was denied (HTTP 403). Check your server URL and access token; MEGA sign-in was not tested."
        else -> "Cloud service returned HTTP ${error.statusCode}; this does not establish invalid MEGA credentials."
    }
    else -> "Could not check Cloud service health. Verify the server connection."
}
