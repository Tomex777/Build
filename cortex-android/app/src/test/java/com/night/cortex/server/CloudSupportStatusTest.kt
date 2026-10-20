package com.night.cortex.server

import org.junit.Assert.*
import org.junit.Test

class CloudSupportStatusTest {
    @Test fun missingEnvEndpointIsDistinguishedFromWrongCredentials() {
        val message = cloudEnvironmentIssue(CortexHttpException(404, "Not found"))
        assertTrue(message.contains("Agent is too old"))
        assertFalse(message.contains("password", ignoreCase = true))
    }

    @Test fun cloudGatewayFailureIsNotAssumedToMeanOutdatedNight() {
        val message = cloudDashboardIssue(CortexHttpException(502, "a sensitive upstream detail"))
        assertTrue(message.contains("MEGA check"))
        assertTrue(message.contains("restart NIGHT"))
        assertFalse(message.contains("Update NIGHT"))
        assertFalse(message.contains("sensitive upstream detail"))
    }

    @Test fun tokenRejectionIsDistinguishedFromMegaSignIn() {
        val message = cloudDashboardIssue(CortexHttpException(401, "private response"))
        assertTrue(message.contains("Cortex access token"))
        assertTrue(message.contains("MEGA sign-in was not tested"))
        assertFalse(message.contains("private response"))
    }

    @Test fun forbiddenEnvDoesNotInventFilePermissionFailure() {
        val message = cloudEnvironmentIssue(CortexHttpException(403, "Forbidden"))
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("access token"))
        assertFalse(message.contains("permissions"))
    }
}
