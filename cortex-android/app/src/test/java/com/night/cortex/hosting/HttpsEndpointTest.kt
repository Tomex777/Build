package com.night.cortex.hosting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsEndpointTest {
    @Test fun agentUrlIsAlwaysAnOrigin() {
        assertEquals("https://198.51.100.4", normalizeHttpsEndpoint("https://198.51.100.4/"))
        assertEquals(
            "https://198.51.100.4:443",
            normalizeHttpsEndpoint("https://198.51.100.4:443/api/cortex/host/cloud"),
        )
        assertEquals(
            "https://example.org",
            normalizeHttpsEndpoint("https://example.org/api/cortex/host/status"),
        )
    }

    @Test fun savedTokenMayOnlyBeReusedOnTheSameOrigin() {
        assertTrue(
            canSaveHttpsConnection(
                "https://example.org/api/cortex/host/status",
                "https://example.org",
                true,
                "",
            )
        )
        assertFalse(
            canSaveHttpsConnection("https://example.org", "https://different.example.org", true, "")
        )
        assertFalse(canSaveHttpsConnection("https://example.org", "http://example.org", true, ""))
    }

    @Test fun credentialsAndQueryStringsAreNeverAllowed() {
        for (invalid in listOf(
            "http://example.org",
            "https://person:password@example.org",
            "https://example.org?token=secret",
            "https://example.org/#private",
        )) {
            assertFalse(isValidHttpsEndpoint(invalid))
        }
    }
}
