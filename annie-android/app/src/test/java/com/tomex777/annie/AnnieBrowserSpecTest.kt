package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieBrowserSpecTest {
    @Test fun browserMessageRoundTripsStructuredSessionSettings() {
        val encoded = AnnieBrowserSpec(
            sessionId = "animeonsen.main",
            url = "https://animeonsen.example/verify",
            title = "Verify AnimeOnsen",
            allowedHosts = listOf("animeonsen.example", "challenges.example"),
            verifyAction = "verifySession",
            userAgent = "Mozilla/5.0 AnnieTest",
            thirdPartyCookies = false,
        ).encode()

        assertEquals("browser", encoded.getString("type"))
        val decoded = AnnieBrowserSpec.decode(encoded)
        assertNotNull(decoded)
        assertEquals("animeonsen.main", decoded?.sessionId)
        assertEquals("verifySession", decoded?.verifyAction)
        assertFalse(decoded?.thirdPartyCookies ?: true)
        assertEquals("Mozilla/5.0 AnnieTest", decoded?.userAgent)
    }

    @Test fun restrictedSessionsAllowRootAndSubdomainsButRejectOtherSites() {
        val spec = AnnieBrowserSpec(
            sessionId = "source.main",
            url = "https://example.com/start",
            allowedHosts = listOf("related.example"),
        )

        assertTrue(spec.allows("https://example.com/next"))
        assertTrue(spec.allows("https://cdn.example.com/media"))
        assertTrue(spec.allows("https://related.example/login"))
        assertTrue(spec.allows("https://img.related.example/thumb"))
        assertFalse(spec.allows("https://notexample.com/"))
        assertFalse(spec.allows("https://elsewhere.test/"))
    }

    @Test fun browserUrlsRejectUnsafeSchemesAndEmbeddedCredentials() {
        val valid = JSONObject()
            .put("sessionId", "safe")
            .put("url", "https://example.com/")
            .put("allowedHosts", listOf("example.com"))
        assertNotNull(AnnieBrowserSpec.decode(valid))

        assertNull(AnnieBrowserSpec.decode(JSONObject(valid.toString()).put("url", "file:///etc/passwd")))
        assertNull(AnnieBrowserSpec.decode(JSONObject(valid.toString()).put("url", "intent://example.com/#Intent;end")))
        assertNull(AnnieBrowserSpec.decode(JSONObject(valid.toString()).put("url", "https://user:password@example.com/")))
    }

    @Test fun unrestrictedBrowserStillAcceptsOnlySafeWebUrls() {
        val spec = AnnieBrowserSpec(
            sessionId = "general",
            url = "https://example.com/",
            restricted = false,
        )
        assertTrue(spec.allows("https://unrelated.example/path"))
        assertTrue(spec.allows("http://localhost:8080/"))
        assertFalse(spec.allows("javascript:alert(1)"))
    }
}
