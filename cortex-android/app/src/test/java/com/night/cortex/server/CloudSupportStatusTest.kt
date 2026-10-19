package com.night.cortex.server

import org.junit.Assert.*
import org.junit.Test

class CloudSupportStatusTest {
    @Test fun missingEnvEndpointIsDistinguishedFromWrongCredentials() {
        val message = cloudEnvironmentIssue(CortexHttpException(404, "Not found"))
        assertTrue(message.contains("Agent is too old"))
        assertFalse(message.contains("password", ignoreCase = true))
    }

    @Test fun cloudUpstreamMissingIsNotCredentialFailure() {
        val message = cloudDashboardIssue(CortexHttpException(502, "Upstream unavailable"))
        assertTrue(message.contains("Update NIGHT"))
        assertFalse(message.contains("invalid"))
    }

    @Test fun forbiddenEnvWritesGetPermissionDiagnosis() {
        assertTrue(cloudEnvironmentIssue(CortexHttpException(403, "Forbidden")).contains("permissions"))
    }
}
