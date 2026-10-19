package com.night.cortex.server

/** Precise, non-secret diagnostics for Cloud setup and Agent compatibility. */
internal fun cloudEnvironmentIssue(error: Throwable): String = when (error) {
    is CortexHttpException -> when (error.statusCode) {
        401 -> "Cortex authorization expired. Reconnect the server in Settings."
        403 -> "The Agent cannot access protected MSCC settings. Update its environment permissions."
        404 -> "The installed Cortex Agent is too old for secure ENV management. Update the Agent; MEGA credentials were not checked."
        502, 503 -> "Cortex Agent cannot reach NIGHT. Check that both services are running."
        else -> "Secure ENV endpoint returned HTTP ${error.statusCode}. No credentials were saved."
    }
    else -> "Secure ENV endpoint could not be reached. Check the Cortex Agent connection."
}

internal fun cloudDashboardIssue(error: Throwable): String = when (error) {
    is CortexHttpException -> when (error.statusCode) {
        404 -> "The Cloud API is unavailable on this Agent. Update Cortex Agent."
        502, 503 -> "Cloud service is unavailable in the running NIGHT version. Update NIGHT before checking credentials."
        401 -> "Cortex authorization expired. Reconnect the server in Settings."
        else -> "Cloud service returned HTTP ${error.statusCode}; this does not establish invalid MEGA credentials."
    }
    else -> "Could not check Cloud service health. Verify the server connection."
}
