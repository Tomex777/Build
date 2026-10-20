package com.night.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class BrowserErrorPageTest {
    @Test fun errorPagesAreLocalNonHttpAndOffline() {
        for (kind in BrowserErrorPage.Kind.values()) {
            val uri = BrowserErrorPage.dataUri(kind)
            assertTrue(uri.startsWith("data:text/html;charset=utf-8;base64,"))
            assertFalse(uri.contains("https://"))
            val html = String(
                Base64.getDecoder().decode(uri.substringAfter("base64,")),
                StandardCharsets.UTF_8
            )
            assertTrue(html.contains("Content-Security-Policy"))
            assertTrue(html.contains("default-src 'none'"))
            assertTrue(html.contains("Use Reload to try again"))
            assertFalse(html.contains("<script"))
        }
    }

    @Test fun tlsErrorsDoNotOfferABypass() {
        val html = String(Base64.getDecoder().decode(
            BrowserErrorPage.dataUri(BrowserErrorPage.Kind.SECURITY)
                .substringAfter("base64,")), StandardCharsets.UTF_8)
        assertTrue(html.contains("couldn't verify"))
        assertFalse(html.contains("proceed anyway", ignoreCase = true))
        assertFalse(html.contains("addCertException"))
    }
}
