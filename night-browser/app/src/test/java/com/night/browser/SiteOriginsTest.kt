package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteOriginsTest {
    @Test fun `normalizes hosts, protocols and default ports`() {
        assertEquals("https://example.com", SiteOrigins.fromUrl("HTTPS://EXAMPLE.COM:443/a?q=1"))
        assertEquals("http://example.com", SiteOrigins.fromUrl("http://EXAMPLE.com:80"))
        assertEquals("https://example.com:8443", SiteOrigins.fromUrl("https://example.com:8443/"))
        assertEquals("https://xn--bcher-kva.example", SiteOrigins.fromUrl("https://xn--bcher-kva.example"))
    }

    @Test fun `enforces origin separation`() {
        val allowed = "https://example.com"
        assertTrue(SiteOrigins.matches("https://example.com/profile", allowed))
        assertFalse(SiteOrigins.matches("http://example.com", allowed))
        assertFalse(SiteOrigins.matches("https://example.com.evil.test", allowed))
        assertFalse(SiteOrigins.matches("https://sub.example.com", allowed))
        assertFalse(SiteOrigins.matches("https://example.com:8443", allowed))
        assertFalse(SiteOrigins.matches("https://evil.test/?to=https://example.com", allowed))
        assertFalse(SiteOrigins.matches("https://example.com@evil.test", allowed))
    }

    @Test fun `rejects credentials and browser internal protocols`() {
        assertNull(SiteOrigins.fromUrl("https://user:pass@example.com"))
        assertNull(SiteOrigins.fromUrl("file:///sdcard/private.html"))
        assertNull(SiteOrigins.fromUrl("javascript:alert(1)"))
        assertNull(SiteOrigins.fromUrl("about:config"))
        assertNull(SiteOrigins.fromUrl("data:text/html,test"))
        assertNull(SiteOrigins.fromUrl("https://"))
        assertNull(SiteOrigins.fromUrl("https://example.com:0/path"))
        assertNull(SiteOrigins.fromUrl("https://example.com:99999/path"))
    }

    @Test fun `IPv6 origins retain their bracketed host and custom port`() {
        assertEquals("https://[::1]:8443", SiteOrigins.fromUrl("https://[::1]:8443/a"))
    }
}
