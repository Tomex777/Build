package com.example.whatsapp.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightBrowserSpecTest {
    @Test
    fun browserAllowsInitialHostAndSubdomains() {
        val spec = NightBrowserSpec(
            sessionId = "anime.verify",
            initialUrl = "https://anime.example.com/login",
            allowedHosts = listOf("example.com"),
        )

        assertTrue(spec.isAllowedUrl("https://anime.example.com/login"))
        assertTrue(spec.isAllowedUrl("https://accounts.example.com/oauth"))
        assertTrue(spec.isAllowedUrl("http://example.com/callback"))
    }

    @Test
    fun browserRejectsUnsafeSchemesUserInfoAndForeignHosts() {
        val spec = NightBrowserSpec(
            sessionId = "verify",
            initialUrl = "https://example.com/start",
            allowedHosts = listOf("example.com"),
        )

        assertFalse(spec.isAllowedUrl("javascript:alert(1)"))
        assertFalse(spec.isAllowedUrl("file:///sdcard/test.html"))
        assertFalse(spec.isAllowedUrl("content://media/test"))
        assertFalse(spec.isAllowedUrl("https://example.com@evil.test/"))
        assertFalse(spec.isAllowedUrl("https://evil-example.com/"))
        assertFalse(spec.isAllowedUrl("https://evil.test/"))
    }

    @Test
    fun browserCodecRoundTripsSessionAndVerificationAction() {
        val original = NightBrowserSpec(
            sessionId = "notion.auth",
            initialUrl = "https://notion.example.com/start",
            allowedHosts = listOf("notion.example.com", "accounts.example.com"),
            title = "Connect Notion",
            verifyActionId = "verify_connection",
            verifyLabel = "Done",
            javaScriptEnabled = true,
            thirdPartyCookies = true,
            userAgent = "NightTest/1.0",
            verificationState = NightBrowserVerificationState.Verified,
            verificationMessage = "Connected.",
            verifiedAt = 99L,
        )

        val decoded = NightBrowserSpecCodec.decode(
            NightBrowserSpecCodec.encode(original)
        )

        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals("notion.auth", decoded.sessionId)
        assertEquals("Connect Notion", decoded.title)
        assertEquals("verify_connection", decoded.verifyActionId)
        assertEquals("Done", decoded.verifyLabel)
        assertEquals("NightTest/1.0", decoded.userAgent)
        assertEquals(NightBrowserVerificationState.Verified, decoded.verificationState)
        assertEquals("Connected.", decoded.verificationMessage)
        assertEquals(99L, decoded.verifiedAt)
        assertTrue(decoded.isAllowedUrl("https://accounts.example.com/callback"))
    }

    @Test
    fun generalBrowserAllowsAnyHttpHostButStillRejectsUnsafeSchemes() {
        val spec = NightBrowserSpec.general(
            initialUrl = "https://example.com/",
        )

        assertFalse(spec.restrictedToAllowedHosts)
        assertTrue(spec.isAllowedUrl("https://openai.com/"))
        assertTrue(spec.isAllowedUrl("http://example.org/path"))
        assertFalse(spec.isAllowedUrl("javascript:alert(1)"))
        assertFalse(spec.isAllowedUrl("file:///sdcard/test.html"))
        assertFalse(spec.isAllowedUrl("https://user@example.com/private"))
    }

    @Test
    fun verificationBrowserRemainsRestrictedByDefault() {
        val spec = NightBrowserSpec(
            sessionId = "verification",
            initialUrl = "https://anime.example.com/",
            allowedHosts = listOf("anime.example.com"),
        )

        assertTrue(spec.restrictedToAllowedHosts)
        assertTrue(spec.isAllowedUrl("https://anime.example.com/login"))
        assertFalse(spec.isAllowedUrl("https://example.org/"))
    }

    @Test
    fun invalidInitialUrlCannotBeSanitized() {
        val invalid = NightBrowserSpec(
            sessionId = "bad",
            initialUrl = "file:///tmp/bad.html",
        )

        val failed = runCatching { invalid.sanitized() }
        assertTrue(failed.isFailure)
    }
}
